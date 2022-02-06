package me.msri.annotation.processor;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

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
  private Map<String, Map.Entry<String, Set<String>>> dockerImages;

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
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

    final var imagesThatMustBeCreatedNew =
        imagesWithTagsToBeCreated.entrySet().stream()
            .filter(serviceNameAndTags -> serviceNameAndTags.getValue().getKey())
            .map(
                serviceNameAndTags ->
                    Map.entry(
                        serviceNameAndTags.getKey(), serviceNameAndTags.getValue().getValue()))
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

    if (!imagesThatMustBeCreatedNew.isEmpty()) {
      final var newImageNames = imagesThatMustBeCreatedNew.keySet();
      log.info("Creating new images for: {}", newImageNames);
      final var newImagesWithTag =
          newImageNames.stream()
              .map(this::createImage)
              .filter(Optional::isPresent)
              .map(Optional::get)
              .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

      // log images that could not be created
      imagesThatMustBeCreatedNew.keySet().stream()
          .filter(Predicate.not(newImagesWithTag::containsKey))
          .forEach(
              image ->
                  log.info(
                      "Could not create image: {}, with tags: {}",
                      image,
                      imagesThatMustBeCreatedNew.get(image)));

      // Re-load image list to have data for newly created images
      loadDockerImages();

      // Creating tags for newly created images
      newImagesWithTag.forEach(
          (imageName, sourceTag) ->
              imagesThatMustBeCreatedNew
                  .get(imageName)
                  .forEach(targetTag -> buildImageWithTag(imageName, sourceTag, targetTag)));

      // Cleaning up intermediate images and tags
      newImagesWithTag.entrySet().stream()
          .filter(imageAndTag -> {
            // delete those temporary image and tags that were not requested in first place
            final String newlyCreatedImageName = imageAndTag.getKey();
            final String newlyCreatedImageTag = imageAndTag.getValue();
            final Set<String> allRequestedTags = imagesWithTagsToBeCreated
                .get(newlyCreatedImageName).getValue();
            return !allRequestedTags.contains(newlyCreatedImageTag);
          })
          .forEach(imageAndTag ->
              dockerRunner.deleteImageAndTag(imageAndTag.getKey(), imageAndTag.getValue()));
    }

    // Creating tags for images that already exist
    imagesWithTagsToBeCreated.entrySet().stream()
        .filter(Predicate.not(imageAndTags -> imageAndTags.getValue().getKey()))
        .map(
            serviceNameAndTags ->
                Map.entry(serviceNameAndTags.getKey(), serviceNameAndTags.getValue().getValue()))
        .forEach(
            imageAndTags -> {
              final String serviceName = imageAndTags.getKey();
              final String existingTag = dockerImages.get(serviceName).getValue().iterator().next();
              imageAndTags
                  .getValue()
                  .forEach(targetTag -> buildImageWithTag(serviceName, existingTag, targetTag));
            });

    log.info("Total time to setup images: {}ms", System.currentTimeMillis() - startTime);
    /*    roundEnv.getElementsAnnotatedWith(BuildImage.class).stream()
    .filter(TypeElement.class::isInstance)
    .map(this::getAbsolutePathOfAnnotatedClass)
    .forEach(System.out::println);*/
    return true;
  }

  private Optional<Map.Entry<String, String>> createImage(final String serviceName) {
    // Try to create spring boot application image using build pack (if boot version supports it)
    final var springBootOciImage = buildToolRunner.createSpringBootImage(serviceName);
    if (springBootOciImage.isPresent()) {
      return springBootOciImage;
    }

    // Try to create spring boot fat jar and then manually create docker image from it
    final var springBootImageFromJarNameAndTag =
        buildToolRunner
            .createSpringBootJar(serviceName)
            .map(
                entry ->
                    DockerfileUtil.createDockerFileFromTemplate(entry.getKey(), entry.getValue()))
            .map(dockerFile -> dockerRunner.createNewImage(serviceName, dockerFile));
    if (springBootImageFromJarNameAndTag.isEmpty()) {
      return Optional.empty();
    }

    return springBootImageFromJarNameAndTag;
  }

  private void buildImageWithTag(
      final String imageName, final String sourceTag, final String targetTag) {
    final String id = dockerImages.get(imageName).getKey();
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
    final boolean enforce = annotation.enforce();
    final var services = annotation.services();
    return Arrays.stream(services)
        .filter(Predicate.not(String::isBlank))
        .map(String::trim)
        .map(serviceName -> new ImageWithTag(serviceName, tag, enforce));
  }

  /*
   * For an image, if even one tag is marked as enforced, then a new image will be created, and
   * all tags for this image that are processed in this round will be created. If any of
   * the to be created tag already exists for that image, then it will be overwritten.
   * - Image Name
   *   - Is new image to be created
   *   - Set of tags to be created for new or existing image
   */
  private Map.Entry<String, Map.Entry<Boolean, Set<String>>> getBuildableImageWithTag(
      final Map.Entry<String, Map<String, Boolean>> serviceToTags) {
    final String serviceName = serviceToTags.getKey();
    final var tagsAndRestriction = serviceToTags.getValue();

    // For an image even if one tag is enforced, or if image does not exist,
    // then creating new image and tags
    final var isAnyTagEnforced =
        tagsAndRestriction.entrySet().stream().anyMatch(Map.Entry::getValue);
    if (isAnyTagEnforced || !dockerImages.containsKey(serviceName)) {
      final var allTags =
          tagsAndRestriction.keySet().stream().collect(Collectors.toUnmodifiableSet());

      log.info("Create new image: {}, with tags: {}", serviceName, allTags);
      return Map.entry(serviceName, Map.entry(true, allTags));
    }

    // If new image creation is not enforced and fiw of image name and tag combination
    // already exist then create tags of existing image that are not already created
    final var tagsOfExistingImage =
        dockerImages.getOrDefault(serviceName, Map.entry("", Collections.emptySet())).getValue();
    log.info("Found existing tags for image {}: {}", serviceName, tagsOfExistingImage);

    final var newTagsToBeCreatedOfExistingImage =
        tagsAndRestriction.keySet().stream()
            .filter(Predicate.not(tagsOfExistingImage::contains))
            .collect(Collectors.toUnmodifiableSet());
    log.info("Create new tags for image: {}: {}", serviceName, newTagsToBeCreatedOfExistingImage);

    if (newTagsToBeCreatedOfExistingImage.isEmpty()) {
      log.info("No need to create new image with name: {}", serviceName);
      return null;
    }

    return Map.entry(serviceName, Map.entry(false, newTagsToBeCreatedOfExistingImage));
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
