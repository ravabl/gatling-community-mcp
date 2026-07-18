package io.github.gatlingcommunity.mcp.detect;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

interface BuildToolCommandRunner {
    CommandResult run(Path workingDirectory, List<String> command, Duration timeout);

    static BuildToolCommandRunner system() {
        return new SystemBuildToolCommandRunner();
    }

    record CommandResult(int exitCode, String stdout, String stderr, boolean timedOut) {
        public CommandResult {
            stdout = stdout == null ? "" : stdout;
            stderr = stderr == null ? "" : stderr;
        }

        static CommandResult success(String stdout) {
            return new CommandResult(0, stdout, "", false);
        }
    }

    final class SystemBuildToolCommandRunner implements BuildToolCommandRunner {
        private static final int MAX_CAPTURED_OUTPUT_BYTES = 4 * 1024 * 1024;
        private static final List<String> MAVEN = List.of("mvn", "-q", "help:effective-pom", "-DskipTests");
        private static final List<String> MAVEN_WRAPPER = List.of("./mvnw", "-q", "help:effective-pom", "-DskipTests");

        @Override
        public CommandResult run(Path workingDirectory, List<String> command, Duration timeout) {
            if (!MAVEN.equals(command) && !MAVEN_WRAPPER.equals(command)) {
                return new CommandResult(126, "", "Blocked non-whitelisted build-tool command", false);
            }
            var processBuilder = new ProcessBuilder(command);
            processBuilder.directory(workingDirectory.toFile());
            processBuilder.redirectErrorStream(true);
            Path outputFile = null;
            try {
                outputFile = Files.createTempFile("gatling-mcp-build-model-", ".log");
                processBuilder.redirectOutput(outputFile.toFile());
                var process = processBuilder.start();
                var completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!completed) {
                    terminateProcessTree(process);
                }
                var output = readBoundedOutput(outputFile);
                return new CommandResult(completed ? process.exitValue() : -1, output, "", !completed);
            } catch (IOException exc) {
                return new CommandResult(127, "", exc.getMessage(), false);
            } catch (InterruptedException exc) {
                Thread.currentThread().interrupt();
                return new CommandResult(130, "", "Interrupted while running build-tool command", false);
            } finally {
                if (outputFile != null) {
                    try {
                        Files.deleteIfExists(outputFile);
                    } catch (IOException ignored) {
                        // A temporary diagnostic file must not make project detection fail.
                    }
                }
            }
        }

        private static String readBoundedOutput(Path outputFile) throws IOException {
            try (var input = Files.newInputStream(outputFile)) {
                return new String(input.readNBytes(MAX_CAPTURED_OUTPUT_BYTES), StandardCharsets.UTF_8);
            }
        }

        private static void terminateProcessTree(Process process) throws InterruptedException {
            var descendants = process.descendants().toList().reversed();
            descendants.forEach(ProcessHandle::destroy);
            process.destroy();
            process.waitFor(200, TimeUnit.MILLISECONDS);
            descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(500, TimeUnit.MILLISECONDS);
            }
        }
    }
}
