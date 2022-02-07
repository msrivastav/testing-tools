package me.msri.docker;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toUnmodifiableSet;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DockerRunner {

  private static final Object LOCK = new Object();

  private final DockerClient dockerClient;

  public DockerRunner(final DockerClientProvider dockerClientProvider) {
    this.dockerClient = dockerClientProvider.getDockerClient();
  }

  /**
   * Provides a mapping between image name, its id and all tags.
   * Provides following map:
   * <pre>
   * Key: Name of the image
   * Value:
   *     Key: Full id of the image.
   *     Value: {@link Set} of tags of image that have same image id.
   * </pre>
   */
  public Map<String, Map<String, Set<String>>> getAllDockerImageWithTags() {
    synchronized (LOCK) {
      record ImageIdNameTag(String name, String id, String tag){}
      final long start = System.currentTimeMillis();
      final var imageNameIdTags = dockerClient
          .listImagesCmd()
          .exec()
          .stream()
          .filter(image -> image.getRepoTags() != null || image.getRepoTags().length == 0)
          .flatMap(image -> {
            final var repoTags = image.getRepoTags();

            final String name = repoTags[0].split(":")[0];
            if (name.contains("none")) {
              return Stream.empty();
            }
            return Arrays.stream(repoTags)
                    .map(tag -> tag.split(":"))
                    .map(it -> it[1])
                    .map(tag -> new ImageIdNameTag(name, image.getId(), tag));
                   })
              .collect(groupingBy(ImageIdNameTag::name, groupingBy(ImageIdNameTag::id, mapping(
                  ImageIdNameTag::tag, toUnmodifiableSet()))));

      log.info("Total time to get list of all images: {}ms", (System.currentTimeMillis() - start));
      return imageNameIdTags.isEmpty() ? Collections.emptyMap() : imageNameIdTags;
    }
  }

  /**
   * Create a new tag for existing docker image.
   *
   * @param id Identifier if existing image.
   * @param repoTag image_repo/image_name:image_tag for existing image.
   * @param targetTag target tag.
   */
  public void createTagForImage(final String id, final String repoTag, final String targetTag) {
    dockerClient.tagImageCmd(id, repoTag, targetTag).exec();
  }

  /**
   * Creates docker image based on given dockerfile, and tags it with supplied tags.
   * @return ID of newly crated image.
   */
  public String createNewImage(final String imageName, final Set<String> tags, final File dockerfile) {
    final long start = System.currentTimeMillis();

    final var repoTags = tags.stream()
        .map(tag -> imageName.concat(":").concat(tag)).collect(toUnmodifiableSet());
    final String imageId =
        dockerClient
            .buildImageCmd()
            .withDockerfile(dockerfile)
            .withTags(repoTags)
            .exec(new BuildImageResultCallback())
            .awaitImageId();

    log.info("""
            New image created -
            Tags: {}
            Id: {}
            Total time of creation: {}ms
            """, repoTags, imageId, (System.currentTimeMillis() - start));

    return imageId;
  }

  /**
   * Deletes a combination of image name and image tag.
   */
  public void deleteImageAndTag(final String imageName, final String imageTag) {
    final long start = System.currentTimeMillis();
    dockerClient.removeImageCmd(imageName.concat(":").concat(imageTag)).exec();
    log.info("""
            Image deleted -
            Name: {}
            Tag: {}
            Total time of deletion: {}ms
            """, imageName, imageTag, (System.currentTimeMillis() - start));
  }
}
