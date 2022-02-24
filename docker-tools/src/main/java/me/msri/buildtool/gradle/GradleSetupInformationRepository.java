package me.msri.buildtool.gradle;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import me.msri.buildtool.BuildToolInformationRepository;
import me.msri.buildtool.exception.BuildToolRunnerException;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.Task;

/** Provides data related to gradle setup of a project. */
public class GradleSetupInformationRepository implements BuildToolInformationRepository {

  private final Map<String, ProjectPathAndTasks> projects;
  private final GradleClientProvider gradleClientProvider;

  public GradleSetupInformationRepository(final GradleClientProvider gradleClientProvider) {
    this.gradleClientProvider = gradleClientProvider;
    projects = new HashMap<>();
  }

  @Override
  public void initialise(String projectBasePath) {
    try (final var projConn = gradleClientProvider.getConnectionForProject(projectBasePath)) {
      final var project = projConn.getModel(GradleProject.class);
      addProjectAndSubProjectsToMap(project);
    }
  }

  /**
   * @param name Name of gradle project.
   * @param absolutePath Path of gradle project, relative to user root directory.
   * @param tasks A {@link Set} of all gradle tasks configured for this project.
   */
  @Override
  public void addProjectInformation(
      final String name, final String absolutePath, final Set<String> tasks) {
    projects.put(name.toLowerCase(), new ProjectPathAndTasks(absolutePath, tasks));
  }

  @Override
  public boolean isProjectConfigured(final String projectName) {
    return projects.containsKey(projectName.toLowerCase());
  }

  @Override
  public boolean isTaskConfiguredForProject(final String projectName, final String taskName) {
    if (!isProjectConfigured(projectName)) {
      throw new BuildToolRunnerException(projectName + " is not configured as gradle project.");
    }

    return projects.get(projectName.toLowerCase()).tasks().contains(taskName);
  }

  @Override
  public String getAbsolutePathOfProject(final String projectName) {
    if (!isProjectConfigured(projectName)) {
      throw new BuildToolRunnerException(projectName + " is not configured as gradle project.");
    }

    return projects.get(projectName.toLowerCase()).projectPath();
  }

  private void addProjectAndSubProjectsToMap(final GradleProject project) {
    putProjectAndTasksToMap(project);

    final var children = project.getChildren();
    if (children.isEmpty()) {
      return;
    }

    children.forEach(this::addProjectAndSubProjectsToMap);
  }

  private void putProjectAndTasksToMap(final GradleProject project) {
    addProjectInformation(
        project.getName(),
        project.getProjectDirectory().getAbsolutePath(),
        project.getTasks().stream().map(Task::getName).collect(Collectors.toUnmodifiableSet()));
  }

  /** Contains absolute path, and all tasks configured for one gradle project. */
  private static record ProjectPathAndTasks(String projectPath, Set<String> tasks) {}
}
