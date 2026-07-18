package io.github.gatlingcommunity.mcp.detect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConventionOnlyBuildModelResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesManyIndependentPropertiesWithoutCombinatorialRecursion() throws Exception {
        var properties = IntStream.range(0, 64)
                .mapToObj(index -> "<property." + index + ">value-" + index + "</property." + index + ">")
                .collect(Collectors.joining("\n"));
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <properties>
                    %s
                    <gatling.version>${property.63}</gatling.version>
                  </properties>
                </project>
                """.formatted(properties));

        var model = assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> new ConventionOnlyBuildModelResolver().resolve(tempDir));

        assertThat(model.properties())
                .hasSize(65)
                .containsEntry("property.0", "value-0")
                .containsEntry("property.63", "value-63")
                .containsEntry("gatling.version", "value-63");
    }
}
