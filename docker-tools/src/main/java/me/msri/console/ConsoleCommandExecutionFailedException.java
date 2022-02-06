package me.msri.console;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ConsoleCommandExecutionFailedException extends RuntimeException {

  private final Exception cause;

  public ConsoleCommandExecutionFailedException(final Exception cause) {
    log.error("Failed to execute console command.", cause);
    this.cause = cause;
  }
}
