package io.github.gatlingcommunity.mcp.resources;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gatlingcommunity.mcp.core.model.Protocol;
import io.github.gatlingcommunity.mcp.data.SourceDataRepository;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GatlingCatalogTest {
    @Test
    void exposesRequiredResources() {
        var catalog = new GatlingResourceCatalog();

        assertThat(catalog.listUris()).contains(
                "gatling://versions/latest",
                "gatling://versions/history",
                "gatling://versions/3.9/features",
                "gatling://versions/3.11/breaking-changes",
                "gatling://methods/http/java/3.9",
                "gatling://methods/http/typescript/3.15",
                "gatling://compatibility/java",
                "gatling://compatibility/node",
                "gatling://community-plugins/kafka",
                "gatling://community-plugins/index",
                "gatling://authoring/http/golden-flows",
                "gatling://imports/http",
                "gatling://analysis/reports",
                "gatling://mcp/interactions",
                "gatling://recipes/http",
                "gatling://recipes/feeders",
                "gatling://recipes/checks",
                "gatling://recipes/injection",
                "gatling://recipes/assertions",
                "gatling://project-templates/java/maven",
                "gatling://project-templates/typescript/npm",
                "gatling://limitations",
                "gatling://upgrade-guides/index",
                "gatling://patterns/anti-patterns"
        );
        assertThat(catalog.read("gatling://versions/history")).contains("3.0", "3.9", "3.15");
        assertThat(catalog.read("gatling://versions/3.9/features")).contains("recordsCount");
        assertThat(catalog.read("gatling://versions/3.11/breaking-changes")).contains("stressPeakUsers");
        assertThat(catalog.read("gatling://methods/http/java/3.9")).contains("get", "jsonPath.saveAs", "rampUsersPerSec");
        assertThat(catalog.read("gatling://community-plugins/kafka"))
                .contains("strict-verified", "1.0.6", "3.13.5", "request-reply");
        assertThat(catalog.read("gatling://authoring/http/golden-flows"))
                .contains("gatling_plan_simulation", "gatling_validate_simulation_plan", "jwtToken");
        assertThat(catalog.read("gatling://imports/http"))
                .contains("gatling_import_openapi", "OpenAPI 3.x", "HAR 1.2",
                        "Postman Collection 2.0", "sourceVersion", "redacted");
        assertThat(catalog.read("gatling://analysis/reports"))
                .contains("gatling_analyze_report", "topSlowRequests", "simulation.log", "best-effort");
        assertThat(catalog.read("gatling://mcp/interactions"))
                .contains("gatling_interaction_status", "useElicitation=true", "useSampling=true", "progressToken");
        assertThat(catalog.read("gatling://recipes/feeders")).contains("queue", "circular", "gatling_check_feeder_risk");
        assertThat(catalog.read("gatling://project-templates/java/maven")).contains("src/test/java", "pom.xml");
        assertThat(catalog.read("gatling://limitations")).contains("3.7", "3.15", "capability-only");
        assertThat(catalog.read("gatling://upgrade-guides/index")).contains("gatling_find_replacement_method");
    }

    @Test
    void exposesAndReadsResourceTemplates() {
        var catalog = new GatlingResourceCatalog();

        assertThat(catalog.listTemplates())
                .extracting("uriTemplate")
                .contains(
                        "gatling://methods/http/{language}/{version}",
                        "gatling://versions/{version}/features",
                        "gatling://versions/{version}/breaking-changes",
                        "gatling://examples/{language}/{protocol}/{pattern}"
                );
        assertThat(catalog.read("gatling://methods/http/java/3.9.5"))
                .contains("HTTP DSL methods for JAVA Gatling 3.9.5", "jsonPath.saveAs");
        assertThat(catalog.read("gatling://versions/3.11.2/breaking-changes"))
                .contains("stressPeakUsers");
        assertThat(catalog.read("gatling://examples/java/http/login-token-orders"))
                .contains("class OrdersSimulation extends Simulation",
                        "jsonPath(\"$.token\").saveAs(\"jwtToken\")",
                        "gatling_generate_from_plan");
        assertThat(catalog.read("gatling://examples/typescript/http/login-token-orders"))
                .contains("export default simulation", "jwtToken");
    }

    @Test
    void exposesProjectLayoutResourcesForEverySupportedDslBuildToolPair() {
        var catalog = new GatlingResourceCatalog();

        assertThat(catalog.listUris()).contains(
                "gatling://project-templates/java/maven",
                "gatling://project-templates/java/gradle",
                "gatling://project-templates/kotlin/maven",
                "gatling://project-templates/kotlin/gradle",
                "gatling://project-templates/scala/sbt",
                "gatling://project-templates/scala/maven",
                "gatling://project-templates/scala/gradle",
                "gatling://project-templates/javascript/npm",
                "gatling://project-templates/typescript/npm");
        for (var uri : List.of(
                "gatling://project-templates/java/maven",
                "gatling://project-templates/java/gradle",
                "gatling://project-templates/kotlin/maven",
                "gatling://project-templates/kotlin/gradle",
                "gatling://project-templates/scala/sbt",
                "gatling://project-templates/scala/maven",
                "gatling://project-templates/scala/gradle",
                "gatling://project-templates/javascript/npm",
                "gatling://project-templates/typescript/npm")) {
            assertThat(catalog.read(uri))
                    .describedAs(uri)
                    .contains("Directory layout:", "ExampleSimulation", "users.csv", "README.md", ".gitignore",
                            "Dependencies:", "Run command:", "gatling_compile_check");
        }
        assertThat(catalog.read("gatling://project-templates/kotlin/gradle"))
                .contains("src/test/kotlin", "build.gradle", "CI=true", "--simulation", "--non-interactive",
                        "Do not propose the standalone Gatling bundle");
        assertThat(catalog.read("gatling://project-templates/scala/sbt"))
                .contains("src/test/scala", "build.sbt", "Gatling/testOnly");
        assertThat(catalog.read("gatling://project-templates/scala/maven"))
                .contains("scala-maven-plugin", "gatling-maven-plugin", "Maven 3.6.3+");
        assertThat(catalog.read("gatling://project-templates/javascript/npm"))
                .contains("src/gatling/javascript", "package.json", "@gatling.io/core", "npm run gatling:test");
    }

    @Test
    void protocolMatrixDistinguishesHttpDeepGenerationFromCapabilityOnlyProtocols() {
        var catalog = new GatlingResourceCatalog();

        assertThat(catalog.read("gatling://compatibility/protocol-matrix"))
                .contains("HTTP", "deep generation", "WEBSOCKET", "SSE", "JMS", "MQTT", "GRPC", "capability-only");
    }

    @Test
    void protocolMatrixUsesHttpGenerationModeFromSourceData() throws Exception {
        var catalog = new GatlingResourceCatalog(sourceDataWithHttpGenerationMode("source-defined-deep"));

        assertThat(catalog.read("gatling://compatibility/protocol-matrix"))
                .contains("HTTP: generationMode=source-defined-deep");
    }

    @ParameterizedTest
    @MethodSource("capabilityOnlyProtocols")
    void exposesProtocolSpecificCapabilityNotesWithoutGeneratedCode(Protocol protocol) {
        var catalog = new GatlingResourceCatalog();
        var uri = "gatling://examples/java/%s/capability-notes".formatted(protocol.name().toLowerCase());

        assertThat(catalog.listUris()).contains(uri);
        assertThat(catalog.read(uri)).contains(
                protocol.name(),
                "capability metadata",
                "context",
                "version",
                "dependency",
                "Full code generation is disabled"
        );
        assertThat(catalog.read(uri)).doesNotContain("class ", "extends Simulation", "verified code");
    }

    private static Stream<Protocol> capabilityOnlyProtocols() {
        return Stream.of(Protocol.WEBSOCKET, Protocol.SSE, Protocol.JMS, Protocol.MQTT, Protocol.GRPC);
    }

    private static SourceDataRepository sourceDataWithHttpGenerationMode(String httpGenerationMode) throws Exception {
        var mapper = new ObjectMapper();
        var featureMatrix = mapper.readTree("""
                {
                  "knownVersions": [],
                  "supportedVersions": [],
                  "rules": [],
                  "protocols": [
                    {"protocol": "HTTP", "generationMode": "%s"},
                    {"protocol": "WEBSOCKET", "generationMode": "capability-only"},
                    {"protocol": "SSE", "generationMode": "capability-only"},
                    {"protocol": "JMS", "generationMode": "capability-only"},
                    {"protocol": "MQTT", "generationMode": "capability-only"},
                    {"protocol": "GRPC", "generationMode": "capability-only"}
                  ]
                }
                """.formatted(httpGenerationMode));
        var constructor = SourceDataRepository.class.getDeclaredConstructor(
                JsonNode.class, JsonNode.class, JsonNode.class, JsonNode.class);
        constructor.setAccessible(true);
        return (SourceDataRepository) constructor.newInstance(
                featureMatrix,
                mapper.createObjectNode(),
                mapper.createObjectNode(),
                mapper.createObjectNode()
        );
    }

    @Test
    void exposesRequiredPrompts() {
        var catalog = new GatlingPromptCatalog();

        assertThat(catalog.listNames()).contains(
                "create_simulation",
                "import_http_plan",
                "analyze_report",
                "find_simulation_issues",
                "explain_community_limitations"
        );
        assertThat(catalog.get("create_simulation")).contains("Generate a Gatling Community simulation");
        assertThat(catalog.get("import_http_plan")).contains("gatling_import_openapi", "gatling_import_curl");
        assertThat(catalog.get("analyze_report")).contains("gatling_analyze_report", "gatling_analyze_log");
    }
}
