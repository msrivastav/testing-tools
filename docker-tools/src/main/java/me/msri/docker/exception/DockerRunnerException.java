package me.msri.docker.exception;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Slf4j
public class DockerRunnerException extends RuntimeException {
  final Exception cause;
  final String desc;

  public DockerRunnerException(final Exception cause) {
    this(cause, null);
    log.error("Docker runner failed.", cause);
  }

  public DockerRunnerException(final String desc) {
    this(null, desc);
    log.error("Docker runner failed for reason: {}", desc);
  }
}
