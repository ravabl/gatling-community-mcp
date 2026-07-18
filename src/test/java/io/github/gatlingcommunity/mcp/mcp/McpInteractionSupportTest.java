package io.github.gatlingcommunity.mcp.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gatlingcommunity.mcp.analysis.SimulationAnalyzer;
import io.github.gatlingcommunity.mcp.compatibility.CapabilityService;
import io.github.gatlingcommunity.mcp.data.SourceDataRepository;
import io.github.gatlingcommunity.mcp.detect.ProjectDetector;
import io.github.gatlingcommunity.mcp.generation.CommunityPluginSimulationGenerator;
import io.github.gatlingcommunity.mcp.generation.HttpSimulationGenerator;
import io.github.gatlingcommunity.mcp.generation.SimulationGenerator;
import io.github.gatlingcommunity.mcp.resources.GatlingPromptCatalog;
import io.github.gatlingcommunity.mcp.resources.GatlingResourceCatalog;
import io.github.gatlingcommunity.mcp.validation.FeatureUsageValidator;
import io.github.gatlingcommunity.mcp.validation.SecretMasker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class McpInteractionSupportTest {
    @Test
    void interactionStatusReportsClientCapabilitiesAndProgressToken() {
        var registry = registry();
        var interaction = new RecordingInteraction(true, true, "progress-1");

        var result = registry.call("gatling_interaction_status", Map.of(), interaction);

        assertThat(result.error()).isFalse();
        assertThat(result.structured()).containsEntry("elicitation", true);
        assertThat(result.structured()).containsEntry("sampling", true);
        assertThat(result.structured()).containsEntry("progress", true);
        assertThat(result.structured()).containsEntry("progressTokenPresent", true);
    }

    @Test
    void planSimulationUsesElicitationForBlankGoalAndEmitsProgress() {
        var registry = registry();
        var interaction = new RecordingInteraction(true, false, "progress-2");
        interaction.elicited = Map.of(
                "goal", "login, extract JWT token, call /api/orders with Authorization header",
                "baseUrl", "https://api.example.test",
                "simulationClassName", "ElicitedOrdersSimulation"
        );

        var result = registry.call("gatling_plan_simulation", Map.of(
                "gatlingVersion", "3.9.5",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "goal", "",
                "useElicitation", true
        ), interaction);

        assertThat(result.error()).isFalse();
        assertThat(result.structured()).containsEntry("valid", true);
        assertThat(result.structured().get("plan").toString())
                .contains("ElicitedOrdersSimulation", "POST /login", "GET /api/orders");
        assertThat(result.structured().get("interaction").toString())
                .contains("elicitation=completed", "goal", "baseUrl", "simulationClassName");
        assertThat(interaction.progressMessages)
                .contains("Planning simulation", "Validating simulation plan", "Simulation plan ready");
        assertThat(interaction.elicitationMessages)
                .singleElement()
                .satisfies(message -> assertThat(message).contains("Need missing Gatling HTTP simulation details"));
    }

    @Test
    void analyzeReportUsesSamplingWhenRequested() {
        var registry = registry();
        var interaction = new RecordingInteraction(false, true, "progress-3");
        interaction.sampledText = "Prioritize fixing GET /api/orders p95 and KO responses.";

        var result = registry.call("gatling_analyze_report", Map.of(
                "reportContent", """
                        var stats = {
                          "type": "GROUP",
                          "name": "Global Information",
                          "stats": {
                            "numberOfRequests": {"total": "10", "ok": "8", "ko": "2"},
                            "minResponseTime": {"total": "10"},
                            "maxResponseTime": {"total": "2500"},
                            "meanResponseTime": {"total": "320"},
                            "percentiles1": {"total": "100"},
                            "percentiles2": {"total": "250"},
                            "percentiles3": {"total": "1600"},
                            "percentiles4": {"total": "2500"},
                            "meanNumberOfRequestsPerSecond": {"total": "2.0"}
                          },
                          "contents": {}
                        };
                        """,
                "useSampling", true
        ), interaction);

        assertThat(result.error()).isFalse();
        assertThat(result.structured().get("interaction").toString())
                .contains("sampling=completed", "Prioritize fixing GET /api/orders");
        assertThat(interaction.samplingPrompts)
                .singleElement()
                .satisfies(prompt -> assertThat(prompt).contains("Review this Gatling report analysis"));
    }

    @Test
    void keyToolsEmitUniformProgressMessages() throws Exception {
        var registry = registry();
        var project = minimalProject("target/interaction-progress-project");

        var importInteraction = new RecordingInteraction(false, false, "progress-import");
        var imported = registry.call("gatling_import_openapi", Map.of(
                "gatlingVersion", "3.9.5",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "document", """
                        openapi: 3.0.3
                        paths:
                          /api/orders:
                            get:
                              responses:
                                '200': { description: OK }
                        """
        ), importInteraction);
        assertThat(imported.error()).isFalse();
        assertThat(importInteraction.progressMessages)
                .contains("Reading input", "Extracting endpoints", "Building plan", "Validating plan");

        var compileInteraction = new RecordingInteraction(false, false, "progress-compile");
        var compiled = registry.call("gatling_compile_check", Map.of(
                "path", project.toString(),
                "buildTool", "MAVEN",
                "execute", false
        ), compileInteraction);
        assertThat(compiled.error()).isFalse();
        assertThat(compileInteraction.progressMessages)
                .contains("Planning command", "Returning output");

        var logInteraction = new RecordingInteraction(false, false, "progress-log");
        var analyzed = registry.call("gatling_analyze_log", Map.of(
                "logText", "REQUEST\t\tGET /api/orders\t1000\t1700\tKO\tstatus 500",
                "logType", "SIMULATION_LOG"
        ), logInteraction);
        assertThat(analyzed.error()).isFalse();
        assertThat(logInteraction.progressMessages)
                .contains("Analyzing Gatling log", "Log analysis ready");

        var contextInteraction = new RecordingInteraction(false, false, "progress-context");
        var context = registry.call("gatling_get_project_context", Map.of("path", project.toString()),
                contextInteraction);
        assertThat(context.error()).isFalse();
        assertThat(contextInteraction.progressMessages)
                .contains("Scanning project", "Resolving build model", "Inspecting simulations", "Returning context");
    }

    @Test
    void unsupportedSamplingAndElicitationAreReportedInInteractionSummary() {
        var registry = registry();
        var interaction = new RecordingInteraction(false, false, "progress-unsupported");

        var result = registry.call("gatling_plan_simulation", Map.of(
                "gatlingVersion", "3.9.5",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "goal", "",
                "useElicitation", true,
                "useSampling", true
        ), interaction);

        assertThat(result.error()).isFalse();
        assertThat(result.structured().get("interaction").toString())
                .contains("elicitation=unsupported", "sampling=unsupported");
    }

    @Test
    void samplingPromptMasksSecretsBeforeCallingClient() {
        var registry = registry();
        var interaction = new RecordingInteraction(false, true, "progress-secret");
        interaction.sampledText = "Masked prompt looks safe.";

        var result = registry.call("gatling_analyze_report", Map.of(
                "reportContent", """
                        var stats = {
                          "type": "GROUP",
                          "name": "Global Information",
                          "stats": {"numberOfRequests": {"total": "1", "ok": "1", "ko": "0"}},
                          "contents": {
                            "req_secret": {
                              "type": "REQUEST",
                              "name": "Authorization: Bearer super-secret-token",
                              "stats": {"numberOfRequests": {"total": "1", "ok": "1", "ko": "0"}}
                            }
                          }
                        };
                        """,
                "contentType", "AUTO",
                "useSampling", true
        ), interaction);

        assertThat(result.error()).isFalse();
        assertThat(interaction.samplingPrompts)
                .singleElement()
                .satisfies(prompt -> assertThat(prompt)
                        .doesNotContain("super-secret-token")
                        .contains("Authorization: Bearer <redacted>"));
    }

    @Test
    void samplingBudgetCanDisableSamplingForAToolCall() {
        var registry = registry();
        var interaction = new RecordingInteraction(false, true, "progress-budget");
        interaction.policy = new InteractionPolicy(true, true, true, 0);
        interaction.sampledText = "This should not be requested.";

        var result = registry.call("gatling_analyze_report", Map.of(
                "reportContent", "var stats = {\"type\":\"GROUP\",\"name\":\"Global Information\",\"stats\":{}};",
                "contentType", "AUTO",
                "useSampling", true
        ), interaction);

        assertThat(result.error()).isFalse();
        assertThat(result.structured().get("interaction").toString()).contains("sampling=budget-exhausted");
        assertThat(interaction.samplingPrompts).isEmpty();
    }

    private static McpToolRegistry registry() {
        var sourceData = SourceDataRepository.loadDefault();
        return McpToolRegistry.createDefault(
                new CapabilityService(sourceData),
                new SimulationGenerator(
                        new CapabilityService(sourceData),
                        new HttpSimulationGenerator(),
                        new CommunityPluginSimulationGenerator()
                ),
                new ProjectDetector(),
                new SimulationAnalyzer(new FeatureUsageValidator(sourceData, new SecretMasker())),
                new FeatureUsageValidator(sourceData, new SecretMasker()),
                new GatlingResourceCatalog(sourceData),
                new GatlingPromptCatalog(),
                sourceData
        );
    }

    private static Path minimalProject(String relativePath) throws Exception {
        var project = Path.of(relativePath).toAbsolutePath().normalize();
        Files.createDirectories(project.resolve("src/gatling/java/com/acme/load"));
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
                </project>
                """);
        Files.writeString(project.resolve("src/gatling/java/com/acme/load/OrdersSimulation.java"), """
                package com.acme.load;

                import io.gatling.javaapi.core.Simulation;

                public final class OrdersSimulation extends Simulation {
                }
                """);
        return project;
    }

    private static final class RecordingInteraction implements McpClientInteraction {
        private final boolean elicitation;
        private final boolean sampling;
        private final Object progressToken;
        private final List<String> progressMessages = new ArrayList<>();
        private final List<String> elicitationMessages = new ArrayList<>();
        private final List<String> samplingPrompts = new ArrayList<>();
        private InteractionPolicy policy = InteractionPolicy.defaults();
        private Map<String, Object> elicited = Map.of();
        private String sampledText = "";

        private RecordingInteraction(boolean elicitation, boolean sampling, Object progressToken) {
            this.elicitation = elicitation;
            this.sampling = sampling;
            this.progressToken = progressToken;
        }

        @Override
        public ClientInteractionStatus status() {
            return new ClientInteractionStatus("test-client", "1.0", elicitation, sampling, progressToken != null);
        }

        @Override
        public InteractionPolicy policy() {
            return policy;
        }

        @Override
        public void progress(double progress, Double total, String message) {
            progressMessages.add(message);
        }

        @Override
        public Optional<Map<String, Object>> elicitForm(String message, Map<String, Object> requestedSchema) {
            elicitationMessages.add(message);
            return elicitation ? Optional.of(elicited) : Optional.empty();
        }

        @Override
        public Optional<String> sampleText(String systemPrompt, String userPrompt, int maxTokens) {
            samplingPrompts.add(userPrompt);
            return sampling ? Optional.of(sampledText) : Optional.empty();
        }
    }
}
