package io.github.gatlingcommunity.mcp.detect;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NpmBuildModelResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesNpmGatlingDependenciesAndNodeEngine() throws Exception {
        Files.writeString(tempDir.resolve("package.json"), """
                {
                  "type": "module",
                  "engines": { "node": ">=24" },
                  "dependencies": {
                    "@gatling.io/core": "3.15.1",
                    "@gatling.io/http": "3.15.1"
                  },
                  "devDependencies": {
                    "typescript": "^5.8.0"
                  },
                  "scripts": {
                    "build": "tsc --noEmit"
                  }
                }
                """);
        Files.createDirectories(tempDir.resolve("src/gatling/typescript"));
        Files.createDirectories(tempDir.resolve("src/test/typescript"));

        var model = new NpmBuildModelResolver().resolve(tempDir);

        assertThat(model.buildTool()).isEqualTo(BuildTool.NPM);
        assertThat(model.properties()).containsEntry("gatling.version", "3.15.1");
        assertThat(model.properties()).containsEntry("node.version", ">=24");
        assertThat(model.dependencies()).contains("@gatling.io/core", "@gatling.io/http");
        assertThat(model.plugins()).contains("typescript");
        assertThat(model.sourceRoots()).contains("src/gatling/typescript");
        assertThat(model.testRoots()).contains("src/test/typescript");
        assertThat(model.warnings()).contains("build_model.npm.static_parse");
    }

    @Test
    void projectContextUsesNpmResolver() throws Exception {
        Files.writeString(tempDir.resolve("package.json"), """
                {
                  "dependencies": {
                    "@gatling.io/core": "3.14.0",
                    "@gatling.io/http": "3.14.0"
                  }
                }
                """);

        var context = new ProjectContextService(List.of(new NpmBuildModelResolver(), new ConventionOnlyBuildModelResolver()))
                .inspect(tempDir);

        assertThat(context.buildTool()).isEqualTo(BuildTool.NPM);
        assertThat(context.detectedGatlingVersion()).isEqualTo("3.14.0");
        assertThat(context.dependencyState().toString()).contains("@gatling.io/core", "@gatling.io/http");
    }
}
