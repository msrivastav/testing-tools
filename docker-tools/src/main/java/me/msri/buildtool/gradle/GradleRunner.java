package me.msri.buildtool.gradle;

import static me.msri.buildtool.gradle.GradleRunnerUtil.executeTaskAndProcessError;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.msri.buildtool.BuildToolInformationRepository;
import me.msri.buildtool.BuildToolRunner;

@Slf4j
@RequiredArgsConstructor
public class GradleRunner implements BuildToolRunner {

  private final GradleClientProvider gradleClientProvider;
  private final BuildToolInformationRepository repository;

  @Override
  public boolean isValidService(String serviceName) {
    return repository.isProjectConfigured(serviceName);
  }

  @Override
  public Optional<Map.Entry<String, String>> createSpringBootImage(final String projectName) {
    final String bootBuildImage = "bootBuildImage";
    return executeTaskAndProcessError(gradleClientProvider, repository, projectName, bootBuildImage)
        .map(Map.Entry::getKey)
        .stream()
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
    var jarCreationResult =
        executeTaskAndProcessError(gradleClientProvider, repository, projectName, bootJarTaskName);
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
    final var propertyReadResult =
        executeTaskAndProcessError(
            gradleClientProvider, repository, projectName, propertiesTaskName);
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
}
