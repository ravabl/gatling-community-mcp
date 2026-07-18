package io.github.gatlingcommunity.mcp.compile;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import io.github.gatlingcommunity.mcp.core.observability.AuditLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class CompileCheckService {
    private static final int OUTPUT_TAIL_LIMIT = 12_000;
    public static final int DEFAULT_TIMEOUT_SECONDS = 120;
    public static final int MAX_TIMEOUT_SECONDS = 300;
    /** Reserve for temp-root creation, project copy, and process startup before the requested process timeout. */
    public static final Duration SERVICE_PREPARATION_RESERVE = Duration.ofSeconds(10);
    /** Grace reserved exclusively for descendant termination, output shutdown, and temp-root deletion. */
    public static final Duration SERVICE_CLEANUP_GRACE = Duration.ofSeconds(5);
    private final AuditLogger auditLogger;
    private final Duration preparationReserve;
    private final Duration cleanupGrace;
    private final ProjectCopier projectCopier;
    private final TempRootFactory tempRootFactory;

    public CompileCheckService() {
        this(new AuditLogger());
    }

    CompileCheckService(AuditLogger auditLogger) {
        this(auditLogger, SERVICE_PREPARATION_RESERVE, SERVICE_CLEANUP_GRACE,
                CompileCheckService::copyProject, () -> Files.createTempDirectory("gatling-mcp-compile-"));
    }

    CompileCheckService(
            AuditLogger auditLogger,
            Duration preparationReserve,
            Duration cleanupGrace,
            ProjectCopier projectCopier,
            TempRootFactory tempRootFactory
    ) {
        this.auditLogger = auditLogger == null ? new AuditLogger() : auditLogger;
        if (preparationReserve == null || preparationReserve.isNegative()) {
            throw new IllegalArgumentException("preparation reserve must not be negative");
        }
        if (cleanupGrace == null || cleanupGrace.isZero() || cleanupGrace.isNegative()) {
            throw new IllegalArgumentException("cleanup grace must be positive");
        }
        if (projectCopier == null) {
            throw new IllegalArgumentException("project copier must not be null");
        }
        if (tempRootFactory == null) {
            throw new IllegalArgumentException("temp root factory must not be null");
        }
        this.preparationReserve = preparationReserve;
        this.cleanupGrace = cleanupGrace;
        this.projectCopier = projectCopier;
        this.tempRootFactory = tempRootFactory;
    }

    public Map<String, Object> check(Path projectRoot,
                                     BuildTool requestedBuildTool,
                                     int requestedTimeoutSeconds,
                                     boolean execute) {
        var normalizedRoot = projectRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRoot)) {
            throw new IllegalArgumentException("Project path must be a directory: " + normalizedRoot);
        }
        var buildTool = requestedBuildTool == null || requestedBuildTool == BuildTool.UNKNOWN
                ? detectBuildTool(normalizedRoot)
                : requestedBuildTool;
        var command = compileCommand(normalizedRoot, buildTool);
        var timeoutSeconds = clampTimeout(requestedTimeoutSeconds);
        if (command.isEmpty()) {
            auditLogger.event("compile_check.command_unavailable", auditFields(
                    normalizedRoot, normalizedRoot, buildTool, command, "unsupported"
            ));
            return baseResult(normalizedRoot, normalizedRoot, buildTool, List.of(), false, false,
                    "unsupported", -1, 0, false, "",
                    List.of(finding(
                            "error",
                            "compile.build_tool.unsupported",
                            "$.buildTool",
                            "No compile-only command is available for build tool: " + buildTool,
                            "Use MAVEN, GRADLE, SBT, or NPM and make sure the project has the matching build file."
                    )),
                    List.of());
        }
        if (!execute) {
            auditLogger.event("compile_check.planned", auditFields(
                    normalizedRoot, normalizedRoot, buildTool, command, "planned"
            ));
            return baseResult(normalizedRoot, normalizedRoot, buildTool, command, false, true,
                    "planned", 0, 0, false, "",
                    List.of(),
                    List.of(warning(
                            "compile.execution.skipped",
                            "$.execute",
                            "Compile command was planned but not executed.",
                            "Call gatling_compile_check with execute=true or omit execute to run the compile check."
                    )));
        }

        return executeWithinResponseDeadline(normalizedRoot, buildTool, command, timeoutSeconds);
    }

    private Map<String, Object> executeWithinResponseDeadline(Path normalizedRoot,
                                                               BuildTool buildTool,
                                                               List<String> command,
                                                               int timeoutSeconds) {
        var operationBudget = Duration.ofSeconds(timeoutSeconds).plus(preparationReserve);
        var completion = new CountDownLatch(1);
        var result = new AtomicReference<Map<String, Object>>();
        var failure = new AtomicReference<Throwable>();
        var worker = Thread.startVirtualThread(() -> {
            try {
                result.set(executeIsolated(
                        normalizedRoot,
                        buildTool,
                        command,
                        timeoutSeconds,
                        CompileDeadline.after(operationBudget),
                        new CleanupBudget(cleanupGrace)
                ));
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                completion.countDown();
            }
        });
        try {
            if (!completion.await(operationBudget.toNanos(), TimeUnit.NANOSECONDS)) {
                worker.interrupt();
                awaitCleanup(completion);
                return timedOutResult(normalizedRoot, buildTool, command, responseDeadline(timeoutSeconds));
            }
        } catch (InterruptedException exception) {
            worker.interrupt();
            awaitCleanupUninterruptibly(completion);
            Thread.currentThread().interrupt();
            return interruptedResult(normalizedRoot, buildTool, command);
        }
        var cause = failure.get();
        if (cause instanceof CompileDeadlineExceededException) {
            return timedOutResult(normalizedRoot, buildTool, command, responseDeadline(timeoutSeconds));
        }
        if (cause instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return interruptedResult(normalizedRoot, buildTool, command);
        }
        if (cause instanceof UncheckedIOException uncheckedIOException) {
            throw uncheckedIOException;
        }
        if (cause instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (cause != null) {
            throw new IllegalStateException("Compile check failed", cause);
        }
        return result.get();
    }

    private Map<String, Object> executeIsolated(Path normalizedRoot,
                                                BuildTool buildTool,
                                                List<String> command,
                                                int timeoutSeconds,
                                                CompileDeadline deadline,
                                                CleanupBudget cleanupBudget)
            throws IOException, InterruptedException, CompileDeadlineExceededException {
        Path tempRoot = null;
        try {
            deadline.check();
            tempRoot = tempRootFactory.create();
            var isolatedRoot = tempRoot.resolve(normalizedRoot.getFileName().toString());
            projectCopier.copy(normalizedRoot, isolatedRoot, deadline);
            deadline.check();
            return executeCompile(normalizedRoot, isolatedRoot, buildTool, command, timeoutSeconds, auditLogger, deadline, cleanupBudget);
        } catch (IOException exception) {
            throw new UncheckedIOException("Compile check failed while preparing isolated project copy", exception);
        } finally {
            if (tempRoot != null) {
                cleanupTempRoot(tempRoot);
            }
        }
    }

    private static Map<String, Object> executeCompile(Path originalRoot,
                                                      Path isolatedRoot,
                                                      BuildTool buildTool,
                                                      List<String> command,
                                                      int timeoutSeconds,
                                                      AuditLogger auditLogger,
                                                      CompileDeadline deadline,
                                                      CleanupBudget cleanupBudget)
            throws InterruptedException, CompileDeadlineExceededException {
        var started = Instant.now();
        var processBuilder = new ProcessBuilder(command);
        processBuilder.directory(isolatedRoot.toFile());
        processBuilder.redirectErrorStream(true);
        Process process = null;
        auditLogger.event("compile_check.started", auditFields(
                originalRoot, isolatedRoot, buildTool, command, "started"
        ));
        try {
            deadline.check();
            process = processBuilder.start();
            deadline.check();
            var runningProcess = process;
            var executor = Executors.newSingleThreadExecutor();
            var outputFuture = executor.submit(() -> readOutputTail(runningProcess));
            boolean completed;
            try {
                var processBudget = Duration.ofSeconds(timeoutSeconds);
                var waitBudget = minimum(processBudget, deadline.remaining());
                completed = process.waitFor(waitBudget.toNanos(), TimeUnit.NANOSECONDS);
                if (!completed) {
                    terminateProcessTree(process, cleanupBudget.start());
                }
                var output = output(outputFuture, deadline);
                deadline.check();
                var durationMillis = Duration.between(started, Instant.now()).toMillis();
                var exitCode = completed ? process.exitValue() : -1;
                var success = completed && exitCode == 0;
                auditLogger.event(completed ? "compile_check.finished" : "compile_check.timeout", auditFields(
                        originalRoot,
                        isolatedRoot,
                        buildTool,
                        command,
                        success ? "passed" : completed ? "failed" : "timeout",
                        success,
                        exitCode,
                        durationMillis,
                        maskSecrets(output)
                ));
                return baseResult(originalRoot, isolatedRoot, buildTool, command, true, success,
                        success ? "passed" : completed ? "failed" : "timeout",
                        exitCode, durationMillis, !completed, maskSecrets(output),
                        success ? List.of() : List.of(finding(
                                completed ? "error" : "warning",
                                completed ? "compile.failed" : "compile.timeout",
                                "$.path",
                                completed ? "Compile command exited with code " + exitCode
                                        : "Compile command timed out after " + timeoutSeconds + " seconds.",
                                completed ? "Inspect outputTail and fix compiler or dependency errors before generation is accepted."
                                        : "Increase timeoutSeconds up to %d or reduce dependency resolution work."
                                        .formatted(MAX_TIMEOUT_SECONDS)
                        )),
                        List.of());
            } finally {
                executor.shutdownNow();
            }
        } catch (IOException exc) {
            var durationMillis = Duration.between(started, Instant.now()).toMillis();
            auditLogger.event("compile_check.command_unavailable", auditFields(
                    originalRoot,
                    isolatedRoot,
                    buildTool,
                    command,
                    "command-unavailable",
                    false,
                    -1,
                    durationMillis,
                    exc.getMessage()
            ));
            return baseResult(originalRoot, isolatedRoot, buildTool, command, true, false,
                    "command-unavailable", -1, durationMillis, false, maskSecrets(exc.getMessage()),
                    List.of(finding(
                            "error",
                            "compile.command.unavailable",
                            "$.buildTool",
                            "Compile command could not be started: " + exc.getMessage(),
                            "Install the selected build tool in the MCP server runtime or use a wrapper script committed to the project."
                    )),
                    List.of());
        } catch (InterruptedException | CompileDeadlineExceededException exc) {
            if (process != null) {
                terminateProcessTree(process, cleanupBudget.start());
            }
            throw exc;
        }
    }

    private static Map<String, Object> auditFields(Path originalRoot,
                                                   Path workingRoot,
                                                   BuildTool buildTool,
                                                   List<String> command,
                                                   String status) {
        return auditFields(originalRoot, workingRoot, buildTool, command, status, false, -1, 0, "");
    }

    private static Map<String, Object> auditFields(Path originalRoot,
                                                   Path workingRoot,
                                                   BuildTool buildTool,
                                                   List<String> command,
                                                   String status,
                                                   boolean success,
                                                   int exitCode,
                                                   long durationMillis,
                                                   String outputTail) {
        return Map.of(
                "buildTool", buildTool.name(),
                "command", command,
                "commandText", command.stream().collect(Collectors.joining(" ")),
                "workingDirectory", originalRoot.toString(),
                "isolatedWorkingDirectory", workingRoot.toString(),
                "status", status,
                "success", success,
                "exitCode", exitCode,
                "durationMillis", durationMillis,
                "outputTail", maskSecrets(outputTail)
        );
    }

    private static String readOutputTail(Process process) {
        try (var reader = process.inputReader()) {
            var builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
                if (builder.length() > OUTPUT_TAIL_LIMIT) {
                    builder.delete(0, builder.length() - OUTPUT_TAIL_LIMIT);
                }
            }
            return builder.toString();
        } catch (IOException exc) {
            return "Failed to read compile output: " + exc.getMessage();
        }
    }

    private static String output(java.util.concurrent.Future<String> outputFuture, CompileDeadline deadline)
            throws InterruptedException, CompileDeadlineExceededException {
        try {
            var remaining = deadline.remaining();
            var waitBudget = minimum(Duration.ofSeconds(2), remaining);
            return outputFuture.get(waitBudget.toNanos(), TimeUnit.NANOSECONDS);
        } catch (ExecutionException exc) {
            return exc.getCause() == null ? exc.getMessage() : exc.getCause().getMessage();
        } catch (TimeoutException exc) {
            outputFuture.cancel(true);
            deadline.check();
            return "";
        }
    }

    static void copyProject(Path sourceRoot, Path targetRoot, CompileDeadline deadline)
            throws IOException, InterruptedException, CompileDeadlineExceededException {
        try (var stream = Files.walk(sourceRoot)) {
            Iterator<Path> paths = stream.iterator();
            while (paths.hasNext()) {
                deadline.check();
                var source = paths.next();
                var relative = sourceRoot.relativize(source);
                if (shouldSkip(relative)) {
                    continue;
                }
                var target = targetRoot.resolve(relative);
                if (Files.isSymbolicLink(source)) {
                    continue;
                }
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
                deadline.check();
            }
        }
    }

    private static boolean shouldSkip(Path relative) {
        for (var part : relative) {
            var name = part.toString();
            if (List.of(".git", "target", "build", ".gradle", "node_modules", ".idea", ".DS_Store")
                    .contains(name)) {
                return true;
            }
        }
        return false;
    }

    private static void cleanupTempRoot(Path root) {
        var interrupted = Thread.interrupted();
        try (var stream = Files.walk(root)) {
            var paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (var path : paths) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Continue through the full tree; a later parent delete can still succeed.
                }
            }
        } catch (IOException ignored) {
            // Temp cleanup failure must not hide the compile-check result.
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void terminateProcessTree(Process process, CleanupDeadline deadline) {
        var handles = new ArrayList<ProcessHandle>();
        handles.addAll(process.toHandle().descendants().toList());
        handles.add(process.toHandle());
        handles.forEach(ProcessHandle::destroy);
        awaitTermination(handles, deadline.gracefulTerminationDeadline());
        handles.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        awaitTermination(handles, deadline);
    }

    private static void awaitTermination(List<ProcessHandle> handles, CleanupDeadline deadline) {
        while (handles.stream().anyMatch(ProcessHandle::isAlive) && !deadline.expired()) {
            try {
                Thread.sleep(Math.min(10, Math.max(1, deadline.remaining().toMillis())));
            } catch (InterruptedException ignored) {
                // Cleanup owns the interrupt boundary; keep terminating descendants before returning.
            }
        }
    }

    private void awaitCleanup(CountDownLatch completion) {
        try {
            if (!awaitCleanupUntil(completion, System.nanoTime() + cleanupGrace.toNanos())) {
                throw new IllegalStateException("Compile check cleanup did not finish within " + cleanupGrace);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while awaiting compile check cleanup", exception);
        }
    }

    private void awaitCleanupUninterruptibly(CountDownLatch completion) {
        var interrupted = false;
        var cleanupExpiresAtNanos = System.nanoTime() + cleanupGrace.toNanos();
        while (true) {
            try {
                if (!awaitCleanupUntil(completion, cleanupExpiresAtNanos)) {
                    throw new IllegalStateException("Compile check cleanup did not finish within " + cleanupGrace);
                }
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean awaitCleanupUntil(CountDownLatch completion, long expiresAtNanos) throws InterruptedException {
        var remainingNanos = expiresAtNanos - System.nanoTime();
        return remainingNanos > 0 && completion.await(remainingNanos, TimeUnit.NANOSECONDS);
    }

    private Map<String, Object> timedOutResult(Path root,
                                               BuildTool buildTool,
                                               List<String> command,
                                               Duration budget) {
        auditLogger.event("compile_check.deadline_exceeded", auditFields(
                root, root, buildTool, command, "timeout", false, -1, budget.toMillis(), ""
        ));
        return baseResult(root, root, buildTool, command, true, false, "timeout", -1, budget.toMillis(), true, "",
                List.of(finding(
                        "warning",
                        "compile.timeout",
                        "$.timeoutSeconds",
                        "Compile check exceeded its end-to-end response deadline of " + budget.toSeconds() + " seconds.",
                        "Reduce project preparation work or increase timeoutSeconds up to %d."
                                .formatted(MAX_TIMEOUT_SECONDS)
                )),
                List.of());
    }

    private Map<String, Object> interruptedResult(Path root,
                                                  BuildTool buildTool,
                                                  List<String> command) {
        auditLogger.event("compile_check.interrupted", auditFields(root, root, buildTool, command, "interrupted"));
        return baseResult(root, root, buildTool, command, true, false, "interrupted", -1, 0, false, "",
                List.of(finding(
                        "warning",
                        "compile.interrupted",
                        "$.path",
                        "Compile check was interrupted.",
                        "Retry the MCP call if the client did not intentionally cancel it."
                )),
                List.of());
    }

    private static Duration minimum(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private static BuildTool detectBuildTool(Path root) {
        if (Files.exists(root.resolve("pom.xml"))) {
            return BuildTool.MAVEN;
        }
        if (Files.exists(root.resolve("build.gradle")) || Files.exists(root.resolve("build.gradle.kts"))) {
            return BuildTool.GRADLE;
        }
        if (Files.exists(root.resolve("build.sbt"))) {
            return BuildTool.SBT;
        }
        if (Files.exists(root.resolve("package.json"))) {
            return BuildTool.NPM;
        }
        return BuildTool.UNKNOWN;
    }

    private static List<String> compileCommand(Path root, BuildTool buildTool) {
        return switch (buildTool) {
            case MAVEN -> Files.exists(root.resolve("mvnw"))
                    ? List.of("./mvnw", "-q", "-DskipTests", "test-compile")
                    : List.of("mvn", "-q", "-DskipTests", "test-compile");
            case GRADLE -> Files.exists(root.resolve("gradlew"))
                    ? List.of("./gradlew", "testClasses")
                    : List.of("gradle", "testClasses");
            case SBT -> List.of("sbt", "Test/compile");
            case NPM -> npmCompileCommand(root);
            case UNKNOWN -> List.of();
        };
    }

    private static List<String> npmCompileCommand(Path root) {
        if (Files.exists(root.resolve("tsconfig.json"))) {
            return List.of("npm", "exec", "tsc", "--", "--noEmit");
        }
        return List.of("npm", "run", "build", "--if-present");
    }

    public static int clampTimeout(int requestedTimeoutSeconds) {
        if (requestedTimeoutSeconds <= 0) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        return Math.min(requestedTimeoutSeconds, MAX_TIMEOUT_SECONDS);
    }

    /**
     * Maximum service response time: process timeout plus preparation and cleanup phases.
     */
    public static Duration serviceResponseDeadline(int requestedTimeoutSeconds) {
        return Duration.ofSeconds(clampTimeout(requestedTimeoutSeconds))
                .plus(SERVICE_PREPARATION_RESERVE)
                .plus(SERVICE_CLEANUP_GRACE);
    }

    private Duration responseDeadline(int requestedTimeoutSeconds) {
        return Duration.ofSeconds(clampTimeout(requestedTimeoutSeconds))
                .plus(preparationReserve)
                .plus(cleanupGrace);
    }

    @FunctionalInterface
    interface ProjectCopier {
        void copy(Path sourceRoot, Path targetRoot, CompileDeadline deadline)
                throws IOException, InterruptedException, CompileDeadlineExceededException;
    }

    @FunctionalInterface
    interface TempRootFactory {
        Path create() throws IOException;
    }

    static final class CompileDeadline {
        private final long expiresAtNanos;

        private CompileDeadline(long expiresAtNanos) {
            this.expiresAtNanos = expiresAtNanos;
        }

        static CompileDeadline after(Duration timeout) {
            return new CompileDeadline(System.nanoTime() + timeout.toNanos());
        }

        Duration remaining() throws InterruptedException, CompileDeadlineExceededException {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Compile check interrupted");
            }
            var remaining = remainingOrZero();
            if (remaining.isZero()) {
                throw new CompileDeadlineExceededException();
            }
            return remaining;
        }

        Duration remainingOrZero() {
            return Duration.ofNanos(Math.max(0, expiresAtNanos - System.nanoTime()));
        }

        void check() throws InterruptedException, CompileDeadlineExceededException {
            remaining();
        }
    }

    static final class CompileDeadlineExceededException extends Exception {
        private CompileDeadlineExceededException() {
            super("Compile check end-to-end deadline elapsed");
        }
    }

    private static final class CleanupBudget {
        private final Duration grace;
        private CleanupDeadline deadline;

        private CleanupBudget(Duration grace) {
            this.grace = grace;
        }

        private synchronized CleanupDeadline start() {
            if (deadline == null) {
                deadline = CleanupDeadline.after(grace);
            }
            return deadline;
        }
    }

    private record CleanupDeadline(long expiresAtNanos) {
        static CleanupDeadline after(Duration grace) {
            return new CleanupDeadline(System.nanoTime() + grace.toNanos());
        }

        Duration remaining() {
            return Duration.ofNanos(Math.max(0, expiresAtNanos - System.nanoTime()));
        }

        boolean expired() {
            return remaining().isZero();
        }

        CleanupDeadline gracefulTerminationDeadline() {
            return CleanupDeadline.after(remaining().dividedBy(2));
        }
    }

    private static Map<String, Object> baseResult(Path originalRoot,
                                                  Path workingRoot,
                                                  BuildTool buildTool,
                                                  List<String> command,
                                                  boolean executed,
                                                  boolean success,
                                                  String status,
                                                  int exitCode,
                                                  long durationMillis,
                                                  boolean timedOut,
                                                  String outputTail,
                                                  List<Map<String, Object>> findings,
                                                  List<Map<String, Object>> warnings) {
        var values = new LinkedHashMap<String, Object>();
        values.put("executed", executed);
        values.put("success", success);
        values.put("status", status);
        values.put("buildTool", buildTool.name());
        values.put("command", command);
        values.put("commandText", command.stream().collect(Collectors.joining(" ")));
        values.put("workingDirectory", originalRoot.toString());
        values.put("isolatedWorkingDirectory", workingRoot.toString());
        values.put("isolated", true);
        values.put("exitCode", exitCode);
        values.put("durationMillis", durationMillis);
        values.put("timedOut", timedOut);
        values.put("outputTail", outputTail == null ? "" : outputTail);
        values.put("findings", findings);
        values.put("warnings", warnings);
        return Map.copyOf(values);
    }

    private static Map<String, Object> finding(String severity,
                                               String code,
                                               String path,
                                               String message,
                                               String suggestion) {
        return Map.of(
                "severity", severity,
                "code", code,
                "path", path,
                "message", message,
                "suggestion", suggestion
        );
    }

    private static Map<String, Object> warning(String code,
                                               String path,
                                               String message,
                                               String suggestion) {
        return Map.of(
                "severity", "warning",
                "code", code,
                "path", path,
                "message", message,
                "suggestion", suggestion
        );
    }

    private static String maskSecrets(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text
                .replaceAll("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s]+", "$1<redacted>")
                .replaceAll("(?i)(password\\s*[:=]\\s*)[^\\s]+", "$1<redacted>")
                .replaceAll("(?i)(token\\s*[:=]\\s*)[^\\s]+", "$1<redacted>")
                .replaceAll("(?i)(secret\\s*[:=]\\s*)[^\\s]+", "$1<redacted>");
    }
}
