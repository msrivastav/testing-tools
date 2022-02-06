package me.msri.docker;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import me.msri.docker.exception.DockerRunnerException;

@UtilityClass
@Slf4j
public class DockerfileUtil {
  private static final String JAR_FILE_NAME_PLACEHOLDER_IN_TEMPLATE = "<<JARFILENAME>>";

  private static final String DOCKERFILE_TEMPLATE = """
      FROM eclipse-temurin:17.0.2_8-jdk-alpine as builder
      WORKDIR application
      COPY <<JARFILENAME>> application.jar
      RUN jar -fx application.jar
      
      FROM eclipse-temurin:17.0.2_8-jre-alpine
      WORKDIR application
      COPY --from=builder application/BOOT-INF/lib ./BOOT-INF/lib
      COPY --from=builder application/org ./org
      COPY --from=builder application/META-INF ./META-INF
      COPY --from=builder application/BOOT-INF/classes ./BOOT-INF/classes
      ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]
      """;

  /**
   * Creates an input-stream representing a docker-file based on java 17 jre and
   * spring boot layered archive.
   */
  public static File createDockerFileFromTemplate(final String jarFilePath, final String jarName) {
    final String dockerfileAsString = DOCKERFILE_TEMPLATE.replace(JAR_FILE_NAME_PLACEHOLDER_IN_TEMPLATE, jarName);

    if(log.isDebugEnabled()) {
      log.debug("Dockerfile: {}", dockerfileAsString);
    }

    try {
      final var dockerfile = Files.createTempFile(Path.of(jarFilePath), "Dockerfile", "").toFile();
      dockerfile.deleteOnExit();

      try(final var fileWriter = new FileWriter(dockerfile)){
        fileWriter.write(dockerfileAsString);
        return dockerfile;
      }
    } catch (IOException e) {
      throw new DockerRunnerException(e);
    }
  }
}
