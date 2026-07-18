package io.github.gatlingcommunity.mcp.compile;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import io.github.gatlingcommunity.mcp.core.observability.AuditLogger;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompileCheckServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void emitsMaskedJsonlAuditEventsForCompileLifecycle() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        Files.writeString(tempDir.resolve("mvnw"), """
                #!/usr/bin/env sh
                echo "Authorization: Bearer super-secret-token"
                exit 0
                """);
        Files.setPosixFilePermissions(tempDir.resolve("mvnw"), Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE
        ));
        var stderr = new ByteArrayOutputStream();
        var original = System.err;
        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
        try {
            var result = new CompileCheckService().check(tempDir, BuildTool.MAVEN, 30, true);

            assertThat(result).containsEntry("success", true);
        } finally {
            System.setErr(original);
        }

        var audit = stderr.toString(StandardCharsets.UTF_8);
        assertThat(audit).contains("\"event\":\"compile_check.started\"");
        assertThat(audit).contains("\"event\":\"compile_check.finished\"");
        assertThat(audit).contains("\"buildTool\":\"MAVEN\"");
        assertThat(audit).doesNotContain("super-secret-token");
        assertThat(audit).contains("Bearer <redacted>");
    }

    @Test
    void emitsPlannedAuditEventWhenExecutionIsSkipped() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        var stderr = new ByteArrayOutputStream();
        var original = System.err;
        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
        try {
            var result = new CompileCheckService().check(tempDir, BuildTool.MAVEN, 30, false);

            assertThat(result).containsEntry("status", "planned");
        } finally {
            System.setErr(original);
        }

        assertThat(stderr.toString(StandardCharsets.UTF_8))
                .contains("\"event\":\"compile_check.planned\"")
                .contains("\"buildTool\":\"MAVEN\"");
    }

    @Test
    void plansJavaMavenCompileFixtureFromCopiedSource() throws Exception {
        var fixture = copyFixture("java-maven");

        var result = new CompileCheckService().check(fixture, BuildTool.MAVEN, 30, false);

        assertThat(result).containsEntry("executed", false);
        assertThat(result).containsEntry("success", true);
        assertThat(result).extractingByKey("command")
                .isEqualTo(List.of("mvn", "-q", "-DskipTests", "test-compile"));
        assertThat(result).extractingByKey("commandText").asString().contains("test-compile");
    }

    @Test
    void executesJavaMavenCompileFixture() throws Exception {
        var fixture = copyFixture("java-maven");

        var result = new CompileCheckService().check(fixture, BuildTool.MAVEN, 120, true);

        assertThat(result).containsEntry("executed", true);
        assertThat(result).containsEntry("success", true);
        assertThat(result).containsEntry("status", "passed");
    }

    @Test
    void plansNonMavenCompileFixturesWithoutStartingBuildTools() throws Exception {
        var service = new CompileCheckService();

        assertPlanned(service, copyFixture("kotlin-maven"), BuildTool.MAVEN,
                List.of("mvn", "-q", "-DskipTests", "test-compile"));
        assertPlanned(service, copyFixture("scala-sbt"), BuildTool.SBT,
                List.of("sbt", "Test/compile"));
        assertPlanned(service, copyFixture("js-npm"), BuildTool.NPM,
                List.of("npm", "run", "build", "--if-present"));
    }

    @Test
    void enforcesEndToEndDeadlineAndInterruptsPreparation() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        var preparationStarted = new CountDownLatch(1);
        var preparationInterrupted = new CountDownLatch(1);
        var ownedTempRoot = tempDir.resolve("gatling-mcp-compile-owned");
        var service = new CompileCheckService(
                new AuditLogger(),
                Duration.ofMillis(100),
                Duration.ofMillis(250),
                (source, target, deadline) -> {
            preparationStarted.countDown();
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException exception) {
                preparationInterrupted.countDown();
                throw exception;
            }
        }, () -> Files.createDirectories(ownedTempRoot));
        var started = System.nanoTime();

        var result = service.check(tempDir, BuildTool.MAVEN, 1, true);

        var elapsed = Duration.ofNanos(System.nanoTime() - started);
        assertThat(preparationStarted.await(100, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(preparationInterrupted.await(500, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(elapsed).isLessThan(Duration.ofMillis(1_500));
        assertThat(result).containsEntry("status", "timeout").containsEntry("timedOut", true);
        assertThat(ownedTempRoot).doesNotExist();
    }

    @Test
    void terminatesDescendantsAndDeletesIsolatedRootBeforeReturningTimeout() throws Exception {
        var project = tempDir.resolve("project");
        Files.createDirectories(project);
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        var childPid = tempDir.resolve("compile-child.pid");
        var grandchildPid = tempDir.resolve("compile-grandchild.pid");
        var wrapper = project.resolve("mvnw");
        Files.writeString(wrapper, """
                #!/usr/bin/env sh
                sh -c 'trap "" TERM; sleep 30 & grandchild=$!; printf "%%s" "$grandchild" > "%s"; wait "$grandchild"' &
                child=$!
                printf '%%s' "$child" > "%s"
                wait "$child"
                """.formatted(grandchildPid, childPid));
        Files.setPosixFilePermissions(wrapper, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE
        ));
        var isolatedRoot = tempDir.resolve("gatling-mcp-compile-owned");
        var service = new CompileCheckService(
                new AuditLogger(),
                Duration.ofMillis(50),
                Duration.ofMillis(500),
                CompileCheckService::copyProject,
                () -> Files.createDirectories(isolatedRoot)
        );

        var result = service.check(project, BuildTool.MAVEN, 1, true);

        assertThat(result).containsEntry("status", "timeout").containsEntry("timedOut", true);
        assertThat(childPid).exists();
        assertThat(grandchildPid).exists();
        var child = Long.parseLong(Files.readString(childPid));
        var grandchild = Long.parseLong(Files.readString(grandchildPid));
        assertThat(ProcessHandle.of(child).filter(ProcessHandle::isAlive)).isEmpty();
        assertThat(ProcessHandle.of(grandchild).filter(ProcessHandle::isAlive)).isEmpty();
        assertThat(isolatedRoot).doesNotExist();
    }

    private Path copyFixture(String name) throws Exception {
        var source = Path.of("src", "test", "fixtures", "compile", name).toAbsolutePath();
        assertThat(source).isDirectory();
        var tempRoot = tempDir.toAbsolutePath().normalize();
        var target = tempRoot.resolve("compile-fixtures").resolve(name).normalize();
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws java.io.IOException {
                Files.createDirectories(target.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws java.io.IOException {
                Files.copy(file, target.resolve(source.relativize(file)));
                return FileVisitResult.CONTINUE;
            }
        });
        assertThat(target).isDirectory();
        assertThat(target.startsWith(tempRoot)).isTrue();
        return target;
    }

    private static void assertPlanned(CompileCheckService service,
                                      Path fixture,
                                      BuildTool buildTool,
                                      List<String> expectedCommand) {
        var result = service.check(fixture, buildTool, 30, false);

        assertThat(result).containsEntry("executed", false);
        assertThat(result).containsEntry("status", "planned");
        assertThat(result).extractingByKey("command").isEqualTo(expectedCommand);
        assertThat(result).extractingByKey("commandText").asString()
                .isEqualTo(String.join(" ", expectedCommand));
    }
}
