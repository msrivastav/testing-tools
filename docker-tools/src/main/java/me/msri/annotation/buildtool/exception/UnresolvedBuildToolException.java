package me.msri.annotation.buildtool.exception;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class UnresolvedBuildToolException extends RuntimeException {

  private final String cause;

  public UnresolvedBuildToolException(final String cause) {
    log.error("Failed to resolve build tool with error: {}", cause);
    this.cause = cause;
  }
}
