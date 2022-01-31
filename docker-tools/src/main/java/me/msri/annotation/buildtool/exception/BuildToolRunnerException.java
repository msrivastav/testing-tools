package me.msri.annotation.buildtool.exception;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BuildToolRunnerException extends RuntimeException {
    final Exception cause;

    public BuildToolRunnerException(final Exception cause) {
        log.error("Build tool runner failed.", cause);
        this.cause = cause;
    }
}
