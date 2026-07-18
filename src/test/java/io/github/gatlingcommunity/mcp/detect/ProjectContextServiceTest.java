package io.github.gatlingcommunity.mcp.detect;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectContextServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesMavenPropertyIndirectionIntoProjectContext() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <properties>
                    <gatling.version>${loadtest.gatling.version}</gatling.version>
                    <loadtest.gatling.version>3.9.5</loadtest.gatling.version>
                    <maven.compiler.release>25</maven.compiler.release>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>io.gatling</groupId>
                      <artifactId>gatling-app</artifactId>
                      <version>${gatling.version}</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        var context = new ProjectContextService().inspect(tempDir);

        assertThat(context.detectedGatlingVersion()).isEqualTo("3.9.5");
        assertThat(context.javaVersion()).isEqualTo("25");
        assertThat(context.dependencyState().toString()).contains("resolvedProperties", "loadtest.gatling.version=3.9.5");
        assertThat(context.dependencyState().toString()).contains("gatling-app");
    }

    @Test
    void scansKnownSourceRootsAndPrunesGeneratedOrDependencyDirectories() throws Exception {
        Files.writeString(tempDir.resolve("RootSimulation.java"), "class RootSimulation extends Simulation {}");
        writeSimulation("src/test/java/ApiSimulation.java", "class ApiSimulation extends Simulation {}");
        writeSimulation("target/generated/TargetSimulation.java", "class TargetSimulation extends Simulation {}");
        writeSimulation("node_modules/pkg/DependencySimulation.js", "simulation(() => {})");
        writeSimulation(".git/objects/HiddenSimulation.java", "class HiddenSimulation extends Simulation {}");

        var context = new ProjectContextService(List.of(new ConventionOnlyBuildModelResolver())).inspect(tempDir);

        assertThat(context.existingSimulationFiles())
                .contains("RootSimulation.java", "src/test/java/ApiSimulation.java")
                .doesNotContain(
                        "target/generated/TargetSimulation.java",
                        "node_modules/pkg/DependencySimulation.js",
                        ".git/objects/HiddenSimulation.java"
                );
    }

    private void writeSimulation(String relativePath, String code) throws Exception {
        var path = tempDir.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, code);
    }
}
