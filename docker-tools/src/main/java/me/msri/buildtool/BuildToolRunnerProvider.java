package me.msri.buildtool;

import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import me.msri.buildtool.exception.UnresolvedBuildToolException;
import me.msri.buildtool.gradle.GradleClientProvider;
import me.msri.buildtool.gradle.GradleRunner;
import me.msri.buildtool.gradle.GradleSetupInformationRepository;
import me.msri.buildtool.maven.MavenRunner;
import me.msri.buildtool.maven.MavenWrapperRunner;
import me.msri.console.ConsoleCommandExecutor;

@Slf4j
@UtilityClass
public class BuildToolRunnerProvider {

  public static BuildToolRunner getBuildToolRunner(final String projectBasePath) {

    final String command = "ls " + projectBasePath;

    var result = ConsoleCommandExecutor.runCommandAndWait(command);
    final var errorResult = result.errorResult();
    if (!result.isSuccessful()) {
      throw new UnresolvedBuildToolException(errorResult);
    }

    final String successResult = result.getSuccessResultStream().collect(Collectors.joining(" "));

    if (successResult.contains("gradlew") || successResult.contains("build.gradle")) {
      final var gradleClientProvider = GradleClientProvider.getInstance();
      final var repository = new GradleSetupInformationRepository(gradleClientProvider);
      repository.initialise(projectBasePath);
      return new GradleRunner(gradleClientProvider, repository);
    }

    if (successResult.contains("mvnw")) {
      return new MavenWrapperRunner();
    }

    if (successResult.contains("pom.xml")) {
      return new MavenRunner();
    }

    throw new UnresolvedBuildToolException("Gradle or Maven build configuration not found.");
  }
}
