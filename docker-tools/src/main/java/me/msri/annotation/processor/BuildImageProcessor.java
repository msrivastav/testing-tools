package me.msri.annotation.processor;

import com.sun.source.util.Trees;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
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

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableSet;

@SupportedAnnotationTypes({"me.msri.annotation.BuildImage", "me.msri.annotation.BuildMultipleImages"})
@Slf4j
public class BuildImageProcessor extends AbstractProcessor {

  private static final String PROJECT_ROOT = System.getProperty("user.dir");
  private BuildToolRunner buildToolRunner;
  private DockerRunner dockerRunner;
  private Map<String, Set<String>> dockerImages;

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
      dockerImages = dockerRunner.getAllDockerImageWithTags();
  }
  // 1. It is gradle project or maven
  // 2. List services managed by gradle
  // 3. Is it > SB 2.3+. Is it < SB 2.3. Is it executable jar. Non Exec jar
  // 3. Image already exists
  // 5. Detect code change and create new image
  // 6. Create Image
  // 7. Delete image after test
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
                  log.error("Invalid service name: {}", imageWithTag.serviceName());
                  return false;
                })
            .collect(
                groupingBy(
                    ImageWithTag::serviceName,
                    toMap(ImageWithTag::tag, ImageWithTag::enforce, Boolean::logicalOr)))
            .entrySet()
            .stream()
            .map(this::getBuildableImageWithTag)
            .filter(Objects::nonNull)
            .collect(toUnmodifiableSet());

    if (!imagesWithTagsToBeCreated.isEmpty()) {
      log.info("Creating project jars ...");
      //runGradleCommandAndWait("jar");
      log.info("Creating images ...");
      imagesWithTagsToBeCreated.parallelStream().map(Map.Entry::getKey).map(buildToolRunner::createSpringBootImage).filter(
          Optional::isPresent).forEach(System.out::println);
    }
    System.out.println("<<>>");
    //buildToolRunner.createSpringBootImage("boot-app").ifPresent(System.out::println);
      System.out.println("<<>>");
    log.info("Total time to setup images: {}ms", System.currentTimeMillis() - startTime);
    roundEnv.getElementsAnnotatedWith(BuildImage.class).stream()
        .filter(TypeElement.class::isInstance)
        .map(this::getAbsolutePathOfAnnotatedClass)
        .forEach(System.out::println);
    return true;
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
    final boolean enforce = annotation.enforce();
    final var services = annotation.services();
    return Arrays.stream(services)
        .filter(Predicate.not(String::isBlank))
        .map(String::trim)
        .map(serviceName -> new ImageWithTag(serviceName, tag, enforce));
  }

  private Map.Entry<String, Set<String>> getBuildableImageWithTag(
      final Map.Entry<String, Map<String, Boolean>> serviceToTags) {
    final String serviceName = serviceToTags.getKey();
    final var tagsAndRestriction = serviceToTags.getValue();

    /// Check whether images for all tags must be created for the service
    final var tagsToBeCreated =
        tagsAndRestriction.entrySet().stream()
            .filter(Map.Entry::getValue)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

    if (tagsToBeCreated.size() == tagsAndRestriction.size()) {
      log.info("Images to be created : {}, with tags: {}", serviceName, tagsToBeCreated);
      return Map.entry(serviceName, tagsToBeCreated);
    }

    final var tagsOfExistingImage = dockerImages.getOrDefault(serviceName, Collections.emptySet());
    log.info("Found existing tags for image {}: {}", serviceName, tagsOfExistingImage);

    tagsAndRestriction.keySet().stream()
        // filtering out images that must be created
        .filter(Predicate.not(tagsToBeCreated::contains))
        // filtering out images that need not be created if there is an existing image
        .filter(Predicate.not(tagsOfExistingImage::contains))
        .forEach(tagsToBeCreated::add);

    if (tagsToBeCreated.isEmpty()) {
      log.info("No need to create new image for service: {}", serviceName);
      return null;
    }

    log.info("Images to be created : {}, with tags: {}", serviceName, tagsToBeCreated);
    return Map.entry(serviceName, tagsToBeCreated);
  }

  private String getAbsolutePathOfAnnotatedClass(final Element classOrInterfaceType) {
    return trees
        .getPath(classOrInterfaceType)
        .getCompilationUnit()
        .getSourceFile()
        .toUri()
        .getPath();
  }

  private static final record ImageWithTag(String serviceName, String tag, boolean enforce) {}

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
