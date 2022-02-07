package me.msri.buildtool.gradle;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import me.msri.buildtool.BuildToolRunner;
import me.msri.buildtool.exception.BuildToolRunnerException;
import org.gradle.tooling.model.GradleProject;

@Slf4j
public class GradleRunner implements BuildToolRunner {

  private final GradleClientProvider gradleClientProvider;
  private final Map<String, ProjectPathAndTasks> projectToTasks;

  public GradleRunner(final String projectBasePath) {
    gradleClientProvider = GradleClientProvider.newInstance();
    projectToTasks = getAllProjectAndTasks(projectBasePath);
  }

  @Override
  public boolean isValidService(String serviceName) {
    return projectToTasks.containsKey(serviceName.toLowerCase());
  }

  @Override
  public Optional<Map.Entry<String, String>> createSpringBootImage(final String projectName) {
    final String bootBuildImage = "bootBuildImage";
    return executeTaskAndProcessError(projectName, bootBuildImage).map(Map.Entry::getKey).stream()
        .flatMap(String::lines)
        .filter(line -> line.contains("Successfully built image"))
        .map(line -> line.split("'"))
        .map(arr -> arr[1])
        .map(imageName -> imageName.split(":"))
        .map(imageAndTag -> Map.entry(imageAndTag[0].split("/")[2], imageAndTag[1]))
        .findFirst();
  }

  @Override
  public Optional<Map.Entry<String, String>> createSpringBootJar(String projectName) {
    // create jar
    final String bootJarTaskName = "bootJar";
    var jarCreationResult = executeTaskAndProcessError(projectName, bootJarTaskName);
    if (jarCreationResult.isEmpty()) {
      return jarCreationResult;
    }

    final var jarCreationError = jarCreationResult.get().getValue();
    if (jarCreationError != null && !jarCreationError.isBlank()) {
      log.error("Error while creating spring boot fat jar: {}", jarCreationError);
      return Optional.empty();
    }

    final var jarResult = jarCreationResult.get().getKey();
    if (log.isDebugEnabled()) {
      log.debug("Sprig boot fat jar creation successful: {}", jarResult);
    }

    // obtain spring boot fat jar path and name
    final String propertiesTaskName = "properties";
    final var propertyReadResult = executeTaskAndProcessError(projectName, propertiesTaskName);
    if (propertyReadResult.isEmpty()) {
      return propertyReadResult;
    }

    final var propertyReadResultError = propertyReadResult.get().getValue();
    if (propertyReadResultError != null && !propertyReadResultError.isBlank()) {
      log.error(
          "Error while reading gradle properties of spring boot project : {}",
          propertyReadResultError);
      return Optional.empty();
    }

    final var propertyResult = propertyReadResult.get().getKey();
    if (log.isDebugEnabled()) {
      log.debug("Sprig boot gradle project property read successful: {}", propertyResult);
    }

    final var propertiesAndValues =
        propertyResult
            .lines()
            .filter(line -> line.contains(": "))
            .map(line -> line.split(":"))
            .collect(
                Collectors.toUnmodifiableMap(
                    propAndVal -> propAndVal[0], propAndVal -> propAndVal[1].strip()));

    final String fullBuildDirPath =
        propertiesAndValues
            .get("buildDir")
            .concat("/")
            .concat(propertiesAndValues.getOrDefault("libsDirName", ""));
    final String jarFileName =
        propertiesAndValues
            .get("archivesBaseName")
            .concat("-")
            .concat(propertiesAndValues.getOrDefault("version", ""))
            .concat(".jar");

    return Optional.of(Map.entry(fullBuildDirPath, jarFileName));
  }

  private Map<String, ProjectPathAndTasks> getAllProjectAndTasks(final String projectBasePath) {
    final Map<String, ProjectPathAndTasks> prjToTsks = new HashMap<>();
    try (final var projConn = gradleClientProvider.getConnectionForProject(projectBasePath)) {
      final var project = projConn.getModel(GradleProject.class);
      GradleRunnerUtil.addProjectAndSubProjectsToMap(prjToTsks, project);
    }
    return prjToTsks.isEmpty() ? Collections.emptyMap() : prjToTsks;
  }

  private Optional<Map.Entry<String, String>> executeTaskAndProcessError(
      final String projectName, final String gradleTask) {

    if (!isTaskConfiguredForProject(projectName, gradleTask)) {
      log.warn("Task: {}, not configured for project: {}", gradleTask, projectName);
      return Optional.empty();
    }

    final String projectPath = getPathAndTasks(projectName).projectPath();
    final var result = GradleRunnerUtil.executeTask(gradleClientProvider, projectPath, gradleTask);

    return Optional.of(result);
  }

  private boolean isTaskConfiguredForProject(final String projectName, final String gradleTask) {
    final var pathAndTasks = getPathAndTasks(projectName);
    return pathAndTasks.tasks().contains(gradleTask);
  }

  private ProjectPathAndTasks getPathAndTasks(final String projectName) {
    final var pathAndTasks = projectToTasks.get(projectName.toLowerCase());

    if (pathAndTasks == null) {
      throw new BuildToolRunnerException(projectName + " not configured as gradle project.");
    }

    return pathAndTasks;
  }
}
