package io.github.gatlingcommunity.mcp.detect;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SbtBuildModelResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesSbtModelFromBuildFile() throws Exception {
        Files.writeString(tempDir.resolve("build.sbt"), """
                scalaVersion := "2.13.16"
                libraryDependencies += "io.gatling" % "gatling-app" % "3.11.5"
                libraryDependencies += "io.gatling" % "gatling-charts-highcharts" % "3.11.5"
                """);
        Files.createDirectories(tempDir.resolve("src/gatling/scala"));
        Files.createDirectories(tempDir.resolve("src/test/scala"));

        var model = new SbtBuildModelResolver().resolve(tempDir);

        assertThat(model.buildTool()).isEqualTo(BuildTool.SBT);
        assertThat(model.properties()).containsEntry("gatling.version", "3.11.5");
        assertThat(model.properties()).containsEntry("scala.version", "2.13.16");
        assertThat(model.dependencies()).contains("gatling-app", "gatling-charts-highcharts");
        assertThat(model.sourceRoots()).contains("src/gatling/scala");
        assertThat(model.testRoots()).contains("src/test/scala");
        assertThat(model.warnings()).contains("build_model.sbt.static_parse");
    }

    @Test
    void projectContextUsesSbtResolver() throws Exception {
        Files.writeString(tempDir.resolve("build.sbt"), """
                libraryDependencies += "io.gatling" % "gatling-app" % "3.10.5"
                """);

        var context = new ProjectContextService(List.of(new SbtBuildModelResolver(), new ConventionOnlyBuildModelResolver()))
                .inspect(tempDir);

        assertThat(context.buildTool()).isEqualTo(BuildTool.SBT);
        assertThat(context.detectedGatlingVersion()).isEqualTo("3.10.5");
        assertThat(context.dependencyState().toString()).contains("gatling-app");
    }
}
