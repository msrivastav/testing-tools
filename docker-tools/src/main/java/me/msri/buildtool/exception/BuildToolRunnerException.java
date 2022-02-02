package me.msri.buildtool.exception;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class BuildToolRunnerException extends RuntimeException {
    final Exception cause;
    final String desc;

    public BuildToolRunnerException(final Exception cause) {
        this(cause, null);
        log.error("Build tool runner failed.", cause);
    }

    public BuildToolRunnerException(final String desc) {
        this(null, desc);
        log.error("Build tool runner failed for reason: {}", desc);
    }
}
