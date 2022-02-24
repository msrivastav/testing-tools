package me.msri.annotation.processor;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toUnmodifiableSet;

import com.sun.source.util.Trees;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import lombok.extern.slf4j.Slf4j;
import me.msri.annotation.BuildImage;
import me.msri.annotation.BuildMultipleImages;
import me.msri.buildtool.BuildToolRunner;
import me.msri.buildtool.BuildToolRunnerProvider;
import me.msri.docker.DockerClientProvider;
import me.msri.docker.DockerRunner;
import me.msri.docker.DockerfileUtil;

@SupportedAnnotationTypes({
  "me.msri.annotation.BuildImage",
  "me.msri.annotation.BuildMultipleImages"
})
@Slf4j
public class BuildImageProcessor extends AbstractProcessor {

  private static final String PROJECT_ROOT = System.getProperty("user.dir");
  private BuildToolRunner buildToolRunner;
  private DockerRunner dockerRunner;
  private Map<String, Map<String, Set<String>>> dockerImages;

  private Trees trees;

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    trees = Trees.instance(processingEnv);
    buildToolRunner = BuildToolRunnerProvider.getBuildToolRunner(PROJECT_ROOT);
    dockerRunner = new DockerRunner(DockerClientProvider.newInstance());
    loadDockerImages();
  }
  // 1. It is gradle project or maven
  // 2. List services managed by gradle
  // 3. Is it > SB 2.3+. Is it < SB 2.3. Is it executable jar. Non Exec jar
  // 3. Image already exists
  // 6. Create Image
  // 7. Create tags
  // 8. Delete image after test
  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (annotations.isEmpty()) {
      return false;
    }

    final long startTime = System.currentTimeMillis();
    final var imagesWithTagsToBeCreated =
        Stream.concat(getSingleAnnotations(roundEnv), getMultipleAnnotations(roundEnv))
            .flatMap(this::createTagEntryPerService)
            .filter(
                imageWithTag -> {
                  if (buildToolRunner.isValidService(imageWithTag.serviceName())) {
                    return true;
                  }
                  log.error("Service does not exist: {}", imageWithTag.serviceName());
                  return false;
                })
            .collect(
                groupingBy(
                    ImageWithTag::serviceName, mapping(ImageWithTag::tag, toUnmodifiableSet())));

    if (!imagesWithTagsToBeCreated.isEmpty()) {
      final var newImagesWithTag =
          imagesWithTagsToBeCreated.entrySet().stream()
              .map(imageAndTags -> createImage(imageAndTags.getKey(), imageAndTags.getValue()))
              .filter(Optional::isPresent)
              .map(Optional::get)
              .collect(Collectors.toUnmodifiableSet());

      // log images that could not be created
      imagesWithTagsToBeCreated.keySet().stream()
          .filter(Predicate.not(newImagesWithTag::contains))
          .forEach(
              image ->
                  log.info(
                      "Could not create image: {}, with tags: {}",
                      image,
                      imagesWithTagsToBeCreated.get(image)));
    }

    log.info("Total time to setup images: {}ms", System.currentTimeMillis() - startTime);
    /*    roundEnv.getElementsAnnotatedWith(BuildImage.class).stream()
    .filter(TypeElement.class::isInstance)
    .map(this::getAbsolutePathOfAnnotatedClass)
    .forEach(System.out::println);*/
    return true;
  }

  private Optional<String> createImage(final String serviceName, final Set<String> tags) {
    // Try to create spring boot application image using build pack (if boot version supports it)
    final var springBootOciImage = buildToolRunner.createSpringBootImage(serviceName);
    if (springBootOciImage.isPresent()) {
      final String sourceTag = springBootOciImage.get().getValue();
      final boolean isSourceTaPreExisting = dockerImages.get(serviceName).values().stream()
          .flatMap(Set::stream).anyMatch(tag -> tag.equals(sourceTag));
      // Re-load image list to have data for newly created images
      loadDockerImages();
      // Creating requested tags
      tags.forEach(targetTag -> buildImageWithTag(serviceName, sourceTag, targetTag));
      // Cleaning up source tag that if it was not request or did not exist before
      if (!isSourceTaPreExisting) {
        dockerRunner.deleteImageAndTag(serviceName, sourceTag);
      }
      return Optional.of(serviceName);
    }

    // Try to create spring boot fat jar and then manually create docker image from it
    final var springBootImageFromJarNameAndTag =
        buildToolRunner
            .createSpringBootJar(serviceName)
            .map(
                entry ->
                    DockerfileUtil.createDockerFileFromTemplate(entry.getKey(), entry.getValue()))
            .map(dockerFile -> dockerRunner.createNewImage(serviceName, tags, dockerFile));
    if (springBootImageFromJarNameAndTag.isPresent()) {
      return Optional.of(serviceName);
    }

    return Optional.empty();
  }

  private void buildImageWithTag(
      final String imageName, final String sourceTag, final String targetTag) {
    final String id = dockerImages.get(imageName).entrySet().stream()
        .filter(idAndTags -> idAndTags.getValue().contains(sourceTag))
        .map(Map.Entry::getKey).findFirst().orElseThrow();
    final String repoTag = imageName + ":" + sourceTag;

    log.info("""
            Adding new tag for existing image -
            Name: {}
            Id: {}
            Source Tag: {}
            Target Tag: {}
            """, imageName, id, sourceTag, targetTag);

    dockerRunner.createTagForImage(id, repoTag, targetTag);
  }

  private void loadDockerImages() {
    dockerImages = dockerRunner.getAllDockerImageWithTags();
  }

  private Stream<BuildImage> getSingleAnnotations(final RoundEnvironment roundEnv) {
    final var classesWithAnnotation = roundEnv.getElementsAnnotatedWith(BuildImage.class);
    if (classesWithAnnotation.isEmpty()) {
      return Stream.empty();
    }
    return classesWithAnnotation.stream().map(element -> element.getAnnotation(BuildImage.class));
  }

  private Stream<BuildImage> getMultipleAnnotations(final RoundEnvironment roundEnv) {
    final var classesWithAnnotation = roundEnv.getElementsAnnotatedWith(BuildMultipleImages.class);
    if (classesWithAnnotation.isEmpty()) {
      return Stream.empty();
    }
    return classesWithAnnotation.stream()
        .map(element -> element.getAnnotation(BuildMultipleImages.class))
        .map(BuildMultipleImages::value)
        .flatMap(Arrays::stream);
  }

  private Stream<ImageWithTag> createTagEntryPerService(final BuildImage annotation) {
    final String tag = annotation.tag().trim();
    final var services = annotation.services();
    return Arrays.stream(services)
        .filter(Predicate.not(String::isBlank))
        .map(String::trim)
        .map(String::toLowerCase)
        .map(serviceName -> new ImageWithTag(serviceName, tag));
  }

  private String getAbsolutePathOfAnnotatedClass(final Element classOrInterfaceType) {
    return trees
        .getPath(classOrInterfaceType)
        .getCompilationUnit()
        .getSourceFile()
        .toUri()
        .getPath();
  }

  private static final record ImageWithTag(String serviceName, String tag) {}

  /*      Path root = FileSystems.getDefault().getPath("").toAbsolutePath();
  Path filePath = Paths.get(root.toString(),"src", "main", "resources", "SpringBootAppTest");
  System.out.println(root);
  System.out.println(filePath);*/

  /*      System.out.println("---");
  roundEnv.getElementsAnnotatedWith(BuildImage.class).stream().map(this::getFileOrObject)
      .forEach(System.out::println);
  System.out.println("---");
  final var f = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
  System.out.println(">>>> " + f);
  System.out.println("---");*/
}
