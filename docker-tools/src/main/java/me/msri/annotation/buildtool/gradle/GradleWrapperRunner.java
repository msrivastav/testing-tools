package me.msri.annotation.buildtool.gradle;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import me.msri.annotation.buildtool.BuildToolRunner;

@Slf4j
public class GradleWrapperRunner implements BuildToolRunner {

  private static final String PROJECT_ROOT = System.getProperty("user.dir");
  private static final String GRADLE_WRAPPER_COMMAND = "gradlew";

  private static final String FULL_GRADLE_COMMAND = PROJECT_ROOT + "/" + GRADLE_WRAPPER_COMMAND + " ";

  private static final Map<String, String> PROJECT_NAMES = GradleRunnerUtil.getProjectNames(GRADLE_WRAPPER_COMMAND);



  private boolean isCommandExecutable(final String projectName) {
    final String command = FULL_GRADLE_COMMAND + "| grep " + projectName;
    return false;
  }
}
