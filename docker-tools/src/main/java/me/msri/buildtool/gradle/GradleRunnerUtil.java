package me.msri.buildtool.gradle;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import me.msri.buildtool.exception.BuildToolRunnerException;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.Task;

@UtilityClass
@Slf4j
public class GradleRunnerUtil {

  public static Map.Entry<String, String> executeTask(
      final GradleClientProvider gradleClientProvider,
      final String fullProjectPath,
      final String task) {
    final var outputStream = new ByteArrayOutputStream();
    final var errorStream = new ByteArrayOutputStream();

    try (final var projConn = gradleClientProvider.getConnectionForProject(fullProjectPath)) {

      projConn
          .newBuild()
          .setStandardOutput(outputStream)
          .setStandardError(errorStream)
          .forTasks(task)
          .run(
              new ResultHandler<>() {
                @Override
                public void onComplete(Void result) {
                  log.info("Task: {} execution complete.", task);
                }

                @Override
                public void onFailure(GradleConnectionException failure) {
                  log.error("Task: {} execution failed.", task);
                  throw new BuildToolRunnerException(failure);
                }
              });
    }

    return Map.entry(outputStream.toString(), errorStream.toString());
  }

  public static void addProjectAndSubProjectsToMap(
      final Map<String, ProjectPathAndTasks> prjToTsks, final GradleProject project) {
    putProjectAndTasksToMap(prjToTsks, project);

    final var children = project.getChildren();
    if (children.isEmpty()) {
      return;
    }

    children.forEach(childProj -> addProjectAndSubProjectsToMap(prjToTsks, childProj));
  }

  private void putProjectAndTasksToMap(
      final Map<String, ProjectPathAndTasks> prjToTsks, final GradleProject project) {
    prjToTsks.put(
        project.getName().toLowerCase(),
        new ProjectPathAndTasks(
            project.getProjectDirectory().getAbsolutePath(),
            project.getTasks().stream()
                .map(Task::getName)
                .collect(Collectors.toUnmodifiableSet())));
  }
}
