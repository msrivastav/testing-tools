package me.msri.buildtool.gradle;

import org.gradle.tooling.model.GradleProject;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import me.msri.buildtool.BuildToolRunner;
import me.msri.buildtool.exception.BuildToolRunnerException;

@Slf4j
public class GradleRunner implements BuildToolRunner {

  private static final String PROJECT_ROOT = System.getProperty("user.dir");
  private final GradleClientProvider gradleClientProvider;
  // = GradleRunnerUtil.getProjectNames(GRADLE_WRAPPER_COMMAND);
  private final Map<String, ProjectPathAndTasks> projectToTasks;

  public GradleRunner(final String projectBasePath) {
    gradleClientProvider = GradleClientProvider.newInstance();
    projectToTasks = getAllProjectAndTasks(projectBasePath);
  }

  public static void main(String[] args) {
    final var b = new ByteArrayOutputStream();
    b.toString();
    b.toString();
  }

  @Override
  public boolean isValidService(String serviceName) {
    return projectToTasks.containsKey(serviceName.toLowerCase());
  }

  @Override
  public Optional<Map.Entry<String, String>> createSpringBootImage(String projectName) {
    final var pathAndTasks = projectToTasks.get(projectName.toLowerCase());

    if (pathAndTasks == null) {
      throw new BuildToolRunnerException(projectName + " not configured as gradle project.");
    }

    final String bootBuildImageTask = "bootBuildImage";
    if (!pathAndTasks.tasks().contains(bootBuildImageTask)) {
        log.error("Spring boot image from build-pack can not be created for service: {}", projectName);
      return Optional.empty();
    }

    final String projectPath = pathAndTasks.path();
    final var result =
        GradleRunnerUtil.executeTask(gradleClientProvider, projectPath, bootBuildImageTask);

    final var error = result.getValue();
    if (error != null && !error.isBlank()) {
      if (log.isDebugEnabled()) {
        log.debug(error);
      }
      return Optional.empty();
    }

      if (log.isDebugEnabled()) {
          log.debug(result.getKey());
      }

    return result
        .getKey()
        .lines()
        .filter(line -> line.contains("Successfully built image"))
        .map(line -> line.split("'"))
        .map(arr -> arr[1])
        .map(imageName -> imageName.split(":"))
        .map(imageAndTag -> Map.entry(imageAndTag[0], imageAndTag[1]))
        .findFirst();
  }

  @Override
  public Optional<Map.Entry<String, String>> createSpringBootJar(String projectName) {
    return null;
  }

  private Map<String, ProjectPathAndTasks> getAllProjectAndTasks(final String projectBasePath) {
    final Map<String, ProjectPathAndTasks> prjToTsks = new HashMap<>();
    try (final var projConn = gradleClientProvider.getConnectionForProject(projectBasePath)) {
      final var project = projConn.getModel(GradleProject.class);
      GradleRunnerUtil.addProjectAndSubProjectsToMap(prjToTsks, project);
    }
    return prjToTsks.isEmpty() ? Collections.emptyMap() : prjToTsks;
  }
}
