package io.github.gatlingcommunity.mcp.detect;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenBuildModelResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesPropertyIndirectionFromEffectivePom() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        var runner = new CapturingRunner(BuildToolCommandRunner.CommandResult.success("""
                WARNING: Java runtime emitted diagnostic text before Maven output
                <project>
                  <properties>
                    <gatling.version>3.9.5</gatling.version>
                    <maven.compiler.release>25</maven.compiler.release>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>io.gatling</groupId>
                      <artifactId>gatling-app</artifactId>
                      <version>3.9.5</version>
                    </dependency>
                  </dependencies>
                  <build>
                    <sourceDirectory>%s/src/main/java</sourceDirectory>
                    <testSourceDirectory>%s/src/test/java</testSourceDirectory>
                    <plugins>
                      <plugin>
                        <groupId>io.gatling</groupId>
                        <artifactId>gatling-maven-plugin</artifactId>
                      </plugin>
                    </plugins>
                  </build>
                </project>
                """.formatted(tempDir, tempDir)));

        var model = new MavenBuildModelResolver(runner).resolve(tempDir);

        assertThat(model.buildTool()).isEqualTo(BuildTool.MAVEN);
        assertThat(model.properties()).containsEntry("gatling.version", "3.9.5");
        assertThat(model.properties()).containsEntry("maven.compiler.release", "25");
        assertThat(model.dependencies()).contains("gatling-app");
        assertThat(model.plugins()).contains("gatling-maven-plugin");
        assertThat(model.sourceRoots()).contains("src/main/java");
        assertThat(model.testRoots()).contains("src/test/java");
        assertThat(model.warnings()).doesNotContain("build_model.maven.effective_pom.unavailable");
        assertThat(model.warnings()).doesNotContain("build_model.maven.effective_pom.parse_failed");
    }

    @Test
    void fallsBackToRawPomWhenMavenIsUnavailable() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <properties>
                    <gatling.version>3.10.5</gatling.version>
                  </properties>
                  <dependencies>
                    <dependency>
                      <artifactId>gatling-charts-highcharts</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);
        var runner = new CapturingRunner(new BuildToolCommandRunner.CommandResult(127, "", "mvn: command not found", true));

        var model = new MavenBuildModelResolver(runner).resolve(tempDir);

        assertThat(model.properties()).containsEntry("gatling.version", "3.10.5");
        assertThat(model.dependencies()).contains("gatling-charts-highcharts");
        assertThat(model.warnings()).contains("build_model.maven.effective_pom.unavailable");
    }

    @Test
    void doesNotRunArbitraryCommands() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        var runner = new CapturingRunner(BuildToolCommandRunner.CommandResult.success("<project/>"));

        new MavenBuildModelResolver(runner).resolve(tempDir);

        assertThat(runner.commands).containsExactly(List.of("mvn", "-q", "help:effective-pom", "-DskipTests"));
        assertThat(runner.commands.getFirst()).doesNotContain("sh", "-c", ";", "&&", "|");
    }

    @Test
    void prefersProjectMavenWrapperWithoutShell() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        Files.writeString(tempDir.resolve("mvnw"), "#!/usr/bin/env sh\n");
        var runner = new CapturingRunner(BuildToolCommandRunner.CommandResult.success("<project/>"));

        new MavenBuildModelResolver(runner).resolve(tempDir);

        assertThat(runner.commands).containsExactly(List.of("./mvnw", "-q", "help:effective-pom", "-DskipTests"));
    }

    @Test
    void systemRunnerEnforcesTimeoutWithoutWaitingForBlockedOutputReader() throws Exception {
        var wrapper = tempDir.resolve("mvnw");
        var childPid = tempDir.resolve("child.pid");
        Files.writeString(wrapper, "#!/bin/sh\nsleep 30 &\necho $! > child.pid\nwait\n");
        assertThat(wrapper.toFile().setExecutable(true)).isTrue();
        var command = List.of("./mvnw", "-q", "help:effective-pom", "-DskipTests");

        var started = System.nanoTime();
        var result = BuildToolCommandRunner.system().run(tempDir, command, Duration.ofMillis(500));
        var elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(result.timedOut()).isTrue();
        assertThat(result.exitCode()).isEqualTo(-1);
        assertThat(elapsedMillis).isLessThan(2_000);
        var child = ProcessHandle.of(Long.parseLong(Files.readString(childPid).trim()));
        assertThat(child.map(ProcessHandle::isAlive).orElse(false)).isFalse();
    }

    private static final class CapturingRunner implements BuildToolCommandRunner {
        private final BuildToolCommandRunner.CommandResult result;
        private final List<List<String>> commands = new ArrayList<>();

        private CapturingRunner(BuildToolCommandRunner.CommandResult result) {
            this.result = result;
        }

        @Override
        public CommandResult run(Path workingDirectory, List<String> command, Duration timeout) {
            commands.add(command);
            return result;
        }
    }
}
