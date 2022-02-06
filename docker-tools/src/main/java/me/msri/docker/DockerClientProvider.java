package me.msri.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import me.msri.docker.exception.DockerRunnerException;

@Slf4j
public class DockerClientProvider {
static {
  //System.setProperty("java.util.logging.level", "INFO");
}
  private static DockerClientProvider dockerClientProvider;

  private final DockerClient dockerClient;

  private DockerClientProvider() {
    dockerClient =
        DockerClientBuilder.getInstance(
                DefaultDockerClientConfig.createDefaultConfigBuilder().build())
            .build();

    // Shutdown hook to close docker connection
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    log.info("Closing docker client before exiting runtime.");
                    dockerClient.close();
                  } catch (IOException e) {
                    throw new DockerRunnerException(e);
                  }
                }));
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
