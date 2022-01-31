package me.msri.buildtool.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import lombok.experimental.UtilityClass;
import me.msri.buildtool.exception.BuildToolRunnerException;

@UtilityClass
public class GradleRunnerUtil {

  private static final String PROJECT_ROOT = System.getProperty("user.dir");

  public static Map<String, String> getProjectNames(final String gradleCommand) {
    final Path settingsGradleFilePath = getSettingsGradleFilePath(gradleCommand);

    final Map<String, String> projects = new HashMap<>();

    // Get list of projects configured with gradle
    try (final var nameLine = Files.lines(settingsGradleFilePath);
        final var contentLines = Files.lines(settingsGradleFilePath)) {

      final String projectName =
          nameLine
              .filter(line -> line.contains("rootProject.name"))
              .map(line -> line.split("=")[1].strip())
              .map(String::toLowerCase)
              .findFirst()
              .orElse("");
      projects.put(projectName, "");

      contentLines
          .filter(line -> line.contains("include"))
          .map(
              line -> {
                if (line.contains("\"")) {
                  return line.split("\"")[1];
                }
                if (line.contains("'")) {
                  return line.split("'")[1];
                }
                return null;
              })
          .filter(Objects::nonNull)
          .map(String::toLowerCase)
          .forEach(
              name -> {
                if (name.contains(":")) {
                  var n = name.split(":");
                  int length = n.length;
                  projects.put(n[length - 1], ":" + name);
                } else {
                  projects.put(name, ":" + name);
                }
              });
    } catch (IOException e) {
      throw new BuildToolRunnerException(e);
    }
    return projects;
  }

  private static Path getSettingsGradleFilePath(final String gradleCommand) {
    final String groovyFilePath = PROJECT_ROOT + "/settings.gradle";
    if (new File(groovyFilePath).exists()) {
      return Path.of(groovyFilePath);
    }

    final String kotlinFilePath = PROJECT_ROOT + "/settings.gradle.kts";
    if (new File(kotlinFilePath).exists()) {
      return Path.of(kotlinFilePath);
    }

    throw new IllegalStateException(
        "'settings.gradle' or 'settings.gradle.kts' not found for gradle project.");
  }

  /*    final String command = FULL_GRADLE_COMMAND + "tasks --all";
  var result = ConsoleCommandExecutor.runCommandAndWait(command);

  result
      .getSuccessResultStream()
      .filter(line -> line.contains(":"))
      .filter(line -> !line.startsWith("'"))
      // .map(line -> line.split("-"))
      // .map(arr -> arr[0])
      .map(String::strip)
      .forEach(System.out::println);
  // log.error(result.toString());*/
}
