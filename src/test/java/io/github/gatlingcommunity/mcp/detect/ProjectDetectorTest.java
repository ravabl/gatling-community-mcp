package io.github.gatlingcommunity.mcp.detect;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import io.github.gatlingcommunity.mcp.core.model.DslLanguage;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectDetectorTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsMavenJavaGatlingProject() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <properties><gatling.version>3.15.1</gatling.version></properties>
                  <dependencies><dependency><artifactId>gatling-charts-highcharts</artifactId></dependency></dependencies>
                </project>
                """);

        var result = new ProjectDetector().detect(tempDir);

        assertThat(result.buildTool()).isEqualTo(BuildTool.MAVEN);
        assertThat(result.language()).isEqualTo(DslLanguage.JAVA);
        assertThat(result.gatlingVersion()).contains("3.15.1");
    }
}
