package me.msri.console;

import java.util.Arrays;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class ConsoleCommandExecutor {

    private static final Runtime RUNTIME = Runtime.getRuntime();
    public static final String DELIMITER = "<<DEL>>";

    public static CommandExecutionResult runCommandAndWait(final String command) {
        final long startTime = System.currentTimeMillis();
        final String threadName = Thread.currentThread().getName();

        try {
            final var process = RUNTIME.exec(command);
            final int result = process.waitFor();

            try (final Scanner inputScanner = new Scanner(process.getInputStream());
                 final Scanner errorScanner = new Scanner(process.getErrorStream())) {
                return new CommandExecutionResult(
                    result == 0,
                    inputScanner.tokens().collect(Collectors.joining(DELIMITER)),
                    errorScanner.tokens().collect(Collectors.joining(DELIMITER)));
            }

        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConsoleCommandExecutionFailedException(e);
        } catch (final Exception e) {
            throw new ConsoleCommandExecutionFailedException(e);
        } finally {
            log.info(
                "Command: {} :: Execution Time: {}ms :: Thread: {}",
                command,
                System.currentTimeMillis() - startTime,
                threadName);
        }
    }

    public static final record CommandExecutionResult(
        boolean isSuccessful, String successResult, String errorResult) {

        public Stream<String> getSuccessResultStream() {
            return Arrays.stream(successResult.split(DELIMITER));
        }

        public Stream<String> getErrorResultStream() {
            return Arrays.stream(errorResult.split(DELIMITER));
        }
    }
}
