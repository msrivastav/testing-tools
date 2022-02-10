package me.msri.buildtool;

import java.util.Set;

/**
 * Provides information related to set up and tasks carried out by build tool for a certain project.
 */
public interface BuildToolInformationRepository {

  /**
   * Initialises the repository with build tool setup information related to project available at
   * supplied path, and all sub-projects of this project.
   *
   * @param projectBasePath Absolute path of the project.
   */
  void initialise(String projectBasePath);

  /**
   * @param name Name of the project.
   * @param absolutePath Absolute path of the project root.
   * @param tasks A {@link Set} of all tasks configured for this project that can be carried out by
   *     build tool.
   */
  void addProjectInformation(String name, String absolutePath, Set<String> tasks);

  boolean isProjectConfigured(String projectName);

  boolean isTaskConfiguredForProject(String projectName, String taskName);

  String getAbsolutePathOfProject(String projectName);
}
