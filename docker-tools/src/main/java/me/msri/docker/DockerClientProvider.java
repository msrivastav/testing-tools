package me.msri.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;

public class DockerClientProvider {

  private static DockerClientProvider dockerClientProvider;

  private final DockerClient dockerClient;

  private DockerClientProvider() {
    dockerClient =
        DockerClientBuilder.getInstance(
                DefaultDockerClientConfig.createDefaultConfigBuilder().build())
            .build();
  }

  public static DockerClientProvider newInstance() {
    if (dockerClientProvider == null) {
        dockerClientProvider = new DockerClientProvider();
    }
    return dockerClientProvider;
  }

  public DockerClient getDockerClient() {
      return dockerClient;
  }
}
