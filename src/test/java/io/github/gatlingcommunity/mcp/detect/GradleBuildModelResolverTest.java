package io.github.gatlingcommunity.mcp.detect;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GradleBuildModelResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesGradleKotlinDslModelWithGatlingPlugin() throws Exception {
        Files.writeString(tempDir.resolve("build.gradle.kts"), """
                plugins {
                    id("io.gatling.gradle") version "3.13.5"
                    java
                }

                java {
                    toolchain {
                        languageVersion.set(JavaLanguageVersion.of(25))
                    }
                }

                dependencies {
                    gatling("io.gatling:gatling-app:3.13.5")
                    gatling("io.gatling:gatling-charts-highcharts:3.13.5")
                }
                """);
        Files.createDirectories(tempDir.resolve("src/gatling/java"));
        Files.createDirectories(tempDir.resolve("src/test/java"));

        var model = new GradleBuildModelResolver().resolve(tempDir);

        assertThat(model.buildTool()).isEqualTo(BuildTool.GRADLE);
        assertThat(model.properties()).containsEntry("gatling.version", "3.13.5");
        assertThat(model.properties()).containsEntry("java.version", "25");
        assertThat(model.dependencies()).contains("gatling-app", "gatling-charts-highcharts");
        assertThat(model.plugins()).contains("io.gatling.gradle");
        assertThat(model.sourceRoots()).contains("src/gatling/java");
        assertThat(model.testRoots()).contains("src/test/java");
        assertThat(model.warnings()).contains("build_model.gradle.static_parse");
    }

    @Test
    void projectContextUsesGradleResolver() throws Exception {
        Files.writeString(tempDir.resolve("build.gradle"), """
                plugins { id 'io.gatling.gradle' version '3.12.0' }
                dependencies { gatling 'io.gatling:gatling-app:3.12.0' }
                """);
        Files.createDirectories(tempDir.resolve("src/gatling/java"));

        var context = new ProjectContextService(List.of(new GradleBuildModelResolver(), new ConventionOnlyBuildModelResolver()))
                .inspect(tempDir);

        assertThat(context.buildTool()).isEqualTo(BuildTool.GRADLE);
        assertThat(context.detectedGatlingVersion()).isEqualTo("3.12.0");
        assertThat(context.dependencyState().toString()).contains("gatling-app", "resolvedProperties");
    }
}
