package me.msri.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Image;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toUnmodifiableSet;

@Slf4j
public class DockerRunner {

    private final DockerClient dockerClient;

    public DockerRunner(final DockerClientProvider dockerClientProvider) {
        this.dockerClient = dockerClientProvider.getDockerClient();
    }

    public Map<String, Set<String>> getAllDockerImageWithTags() {
        final long start = System.currentTimeMillis();
        final var images =
            dockerClient.listImagesCmd().exec().stream()
                .map(Image::getRepoTags)
                .filter(Objects::nonNull)
                .flatMap(Arrays::stream)
                .map(tag -> tag.split(":"))
                .collect(
                    groupingBy(
                        imageAndTag -> imageAndTag[0],
                        collectingAndThen(
                            toUnmodifiableSet(),
                            imageAndTag ->
                                imageAndTag.stream().map(it -> it[1]).collect(toUnmodifiableSet()))));
        log.info("Total time to get list of image names: {}", (System.currentTimeMillis() - start));
        return images;
    }
}
