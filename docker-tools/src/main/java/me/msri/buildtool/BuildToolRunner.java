package me.msri.buildtool;

import java.util.Map;
import java.util.Optional;

/** Provides functionalities related to build tools such as gradle, and maven. */
public interface BuildToolRunner {

  /** Returns true if the service name exists as a project handled by build tool. */
  boolean isValidService(String serviceName);

  /**
   * Creates image of the given project by invoking in-built functionality of build tool to create
   * image, and submits the image to docker client running on localhost.
   *
   * @param projectName Name of the project that should be same as a service as a service handled by
   *     build tool.
   * @return An {@link Optional<Map.Entry>} containing name and tag of newly created image. {@link
   *     Optional#empty()} if image could not be created, including if it is not possible for build
   *     tool to create such image.
   */
  Optional<Map.Entry<String, String>> createSpringBootImage(String projectName);

  /**
   * Uses build tool to create a layered spring boot Jar of the project with supplied name.
   *
   * @param projectName Name of the project that should be same as a service as a service handled by
   *     * build tool.
   * @return An {@link Optional<Map.Entry>} containing absolute path and name of newly created
   *     spring boot layered Jar. {@link Optional#empty()} if Jar could not be created including, if
   *     it is not possible for build tool to create such Jar.
   */
  Optional<Map.Entry<String, String>> createSpringBootJar(String projectName);
}
