package me.msri.buildtool.gradle;

import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

/**
 * Provides a gradle client to invoke tasks on local running gradle or gradle wrapper installation.
 */
@Slf4j
public class GradleClientProvider {
  private static GradleClientProvider gradleClientProvider;

  private final GradleConnector connector;

  private GradleClientProvider() {
    connector = GradleConnector.newConnector();
  }

  /**
   * Provides a singleton instance of GradleClientProvider class, which contains same gradle
   * connector for all gradle clients.
   */
  public static GradleClientProvider getInstance() {
    if (gradleClientProvider == null) {
      gradleClientProvider = new GradleClientProvider();
    }
    return gradleClientProvider;
  }

  /**
   * Provides a new {@link ProjectConnection} object that connects to globally running gradle
   * instance, not specific to any one project.
   */
  public ProjectConnection getConnection() {
    return connector.connect();
  }

  /**
   * Provides a new {@link ProjectConnection} object specific to a gradle project. If a gradle
   * wrapper is configured at supplied project base path, then it connects with wrapper installation
   * otherwise it connects to global gradle installation.
   *
   * @param projectBasePath Absolute path of the gradle project that we are trying to connect to.
   */
  public ProjectConnection getConnectionForProject(final String projectBasePath) {
    return connector.forProjectDirectory(new File(projectBasePath)).connect();
  }
}
