package io.github.gatlingcommunity.mcp.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gatlingcommunity.mcp.GatlingCommunityMcpApplication;
import io.github.gatlingcommunity.mcp.mcp.schema.ToolOutputSchemas;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolOutputSchemaContractTest {
    @Test
    @SuppressWarnings("unchecked")
    void importedPlanSchemaDescribesPathAndBodyFeederCandidates() {
        var schema = ToolOutputSchemas.importedPlan();
        var properties = (Map<String, Object>) schema.get("properties");
        var feederCandidates = (Map<String, Object>) properties.get("feederCandidates");

        assertThat(feederCandidates.get("description"))
                .isEqualTo("Request path and request-body representation placeholders that can become feeder columns");
    }

    @Test
    void allToolsAdvertiseStrictSuccessAndErrorOutputContracts() {
        var registry = GatlingCommunityMcpApplication.createDefaultRegistry();

        assertThat(registry.toolDefinitions()).allSatisfy(definition -> {
            var schema = definition.outputSchema();

            assertThat(schema).as(definition.name()).containsEntry("type", "object");
            assertThat(schema).as(definition.name()).containsEntry("additionalProperties", false);
            assertThat(schema).as(definition.name()).containsKey("anyOf");
            assertThat(schema.toString()).as(definition.name())
                    .doesNotContain("required=[]")
                    .doesNotContain("type=[string, number, boolean, object, array, integer]");
            assertThat(requiredBranch(schema, 0)).as(definition.name()).isNotEmpty();
            assertThat(requiredBranch(schema, 1)).as(definition.name())
                    .containsExactlyInAnyOrder("code", "severity", "path", "message", "suggestion");
        });
    }

    @Test
    void representativeToolOutputsMatchAdvertisedSchemas() throws Exception {
        var registry = GatlingCommunityMcpApplication.createDefaultRegistry();
        var project = projectFixture();

        for (var definition : registry.toolDefinitions()) {
            var args = exampleArguments(definition);
            if (needsWorkspacePath(definition.name())) {
                args = with(args, "path", project.toString());
            }
            if (definition.name().equals("gatling_compile_check")) {
                args = with(args, "execute", false);
            }

            var response = registry.call(definition.name(), args);

            SchemaAssertions.assertMatches(definition.outputSchema(), response.structured());
            assertThat(response.error()).as(definition.name()).isFalse();
        }

        var outside = Files.createTempDirectory("gatling-mcp-schema-outside");
        var errorResponse = registry.call("gatling_detect_project", Map.of("path", outside.toString()));

        assertThat(errorResponse.error()).isTrue();
        SchemaAssertions.assertMatches(
                registry.toolDefinition("gatling_detect_project").outputSchema(),
                errorResponse.structured()
        );
        assertThat(errorResponse.structured()).containsEntry("code", "path.outside_workspace");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> exampleArguments(ToolDefinition definition) {
        var examples = (List<Map<String, Object>>) definition.meta().get("examples");
        var firstExample = examples.getFirst();
        return new LinkedHashMap<>((Map<String, Object>) firstExample.get("arguments"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> requiredBranch(Map<String, Object> schema, int index) {
        var alternatives = (List<Map<String, Object>>) schema.get("anyOf");
        return (List<String>) alternatives.get(index).get("required");
    }

    private static boolean needsWorkspacePath(String toolName) {
        return switch (toolName) {
            case "gatling_detect_project",
                    "gatling_get_project_context",
                    "gatling_explain_project_context",
                    "gatling_resolve_effective_context",
                    "gatling_compile_check",
                    "gatling_extract_endpoints_from_codebase" -> true;
            default -> false;
        };
    }

    private static Map<String, Object> with(Map<String, Object> base, String key, Object value) {
        var values = new LinkedHashMap<String, Object>(base);
        values.put(key, value);
        return Map.copyOf(values);
    }

    private static Path projectFixture() throws Exception {
        var project = Path.of("target/output-schema-contract-project").toAbsolutePath().normalize();
        Files.createDirectories(project.resolve("src/gatling/java/com/acme/load"));
        Files.createDirectories(project.resolve("src/main/java/com/acme/api"));
        Files.createDirectories(project.resolve("src/test/java/com/acme/load"));
        Files.writeString(project.resolve("pom.xml"), """
                <project>
                  <properties>
                    <gatling.version>3.9.5</gatling.version>
                    <maven.compiler.release>25</maven.compiler.release>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>io.gatling.highcharts</groupId>
                      <artifactId>gatling-charts-highcharts</artifactId>
                      <version>${gatling.version}</version>
                    </dependency>
                  </dependencies>
                  <build>
                    <plugins>
                      <plugin>
                        <groupId>io.gatling</groupId>
                        <artifactId>gatling-maven-plugin</artifactId>
                      </plugin>
                    </plugins>
                  </build>
                </project>
                """);
        Files.writeString(project.resolve("src/gatling/java/com/acme/load/OrdersSimulation.java"), """
                package com.acme.load;

                import static io.gatling.javaapi.core.CoreDsl.*;
                import static io.gatling.javaapi.http.HttpDsl.*;

                import io.gatling.javaapi.core.Simulation;

                public class OrdersSimulation extends Simulation {
                  {
                    var httpProtocol = http.baseUrl("https://api.example.test");
                    var scn = scenario("Orders").exec(http("GET /api/orders").get("/api/orders"));
                    setUp(scn.injectOpen(atOnceUsers(1))).protocols(httpProtocol);
                  }
                }
                """);
        Files.writeString(project.resolve("src/main/java/com/acme/api/OrdersController.java"), """
                package com.acme.api;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;

                @RequestMapping("/api")
                class OrdersController {
                  @GetMapping("/orders")
                  String orders() { return "ok"; }
                }
                """);
        return project;
    }
}
