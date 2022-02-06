package me.msri.docker;

import static java.util.stream.Collectors.toUnmodifiableSet;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DockerRunner {

  private static final Object LOCK = new Object();

  private final DockerClient dockerClient;

  public DockerRunner(final DockerClientProvider dockerClientProvider) {
    this.dockerClient = dockerClientProvider.getDockerClient();
  }

  // <Image name, <image id, Set<Image tags>>>
  public Map<String, Map.Entry<String, Set<String>>> getAllDockerImageWithTags() {
    synchronized (LOCK) {
      final long start = System.currentTimeMillis();
      final Map<String, Map.Entry<String, Set<String>>> imageNameIdTags = new HashMap<>();
      dockerClient
          .listImagesCmd()
          .exec()
          .forEach(
              image -> {
                final var repoTags = image.getRepoTags();
                if (repoTags == null || repoTags.length == 0) {
                  return;
                }

                final String id = image.getId();
                final String name = repoTags[0].split(":")[0];
                final var tags =
                    Arrays.stream(repoTags)
                        .map(tag -> tag.split(":"))
                        .map(it -> it[1])
                        .collect(toUnmodifiableSet());

                imageNameIdTags.put(name, Map.entry(id, tags));
              });
      log.info("Total time to get list of image names: {}ms", (System.currentTimeMillis() - start));
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

  public Map.Entry<String, String> createNewImage(final String imageName, final File dockerfile) {
    final long start = System.currentTimeMillis();

    final String imageId =
        dockerClient
            .buildImageCmd()
            .withDockerfile(dockerfile)
            .withTags(Set.of(imageName))
            .exec(new BuildImageResultCallback())
            .awaitImageId();
    log.info(
        "Total time to create image: {}, id: {} - {}ms",
        imageName,
        imageId,
        (System.currentTimeMillis() - start));
    return Map.entry(imageName, "latest");
  }

  /**
   * Deletes a combination of image name and image tag.
   */
  public void deleteImageAndTag(final String imageName, final String imageTag) {
    dockerClient.removeImageCmd(imageName.concat(":").concat(imageTag)).exec();
  }
}
