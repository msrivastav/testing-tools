package me.msri.annotation.buildtool;

import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import me.msri.annotation.buildtool.exception.UnresolvedBuildToolException;
import me.msri.annotation.buildtool.gradle.GradleRunner;
import me.msri.annotation.buildtool.gradle.GradleWrapperRunner;
import me.msri.annotation.buildtool.maven.MavenRunner;
import me.msri.annotation.buildtool.maven.MavenWrapperRunner;
import me.msri.annotation.util.ConsoleCommandExecutor;

@Slf4j
@UtilityClass
public class BuildToolRunnerProvider {

  private static final String PROJECT_ROOT = System.getProperty("user.dir");

  public static BuildToolRunner getBuildToolRunner() {

    final String command = "ls " + PROJECT_ROOT;

    var result = ConsoleCommandExecutor.runCommandAndWait(command);
    final var errorResult = result.errorResult();
    if (!result.isSuccessful()) {
      throw new UnresolvedBuildToolException(errorResult);
    }

    final String successResult = result.getSuccessResultStream().collect(Collectors.joining(" "));

    if (successResult.contains("gradlew")) {
      return new GradleWrapperRunner();
    }

    if (successResult.contains("build.gradle")) {
      return new GradleRunner();
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
