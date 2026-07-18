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
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpToolRegistrySafetyTest {
    @Test
    void emitsMaskedAuditEventsAndDoesNotEchoSecretsFromTroubleshootingEvidence() {
        var stderr = new ByteArrayOutputStream();
        var original = System.err;
        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
        try {
            var result = registry().call("gatling_explain_errors", Map.of(
                    "errorText", "ReadTimeoutException Authorization: Bearer super-secret-token"
            ));

            assertThat(result.error()).isFalse();
            assertThat(result.structured().toString())
                    .doesNotContain("super-secret-token")
                    .contains("Authorization: Bearer <redacted>", "log.timeout");
        } finally {
            System.setErr(original);
        }

        var audit = stderr.toString(StandardCharsets.UTF_8);
        assertThat(audit)
                .contains("\"event\":\"tool.started\"")
                .contains("\"event\":\"tool.finished\"")
                .contains("\"tool\":\"gatling_explain_errors\"")
                .contains("\"argumentKeys\":[\"errorText\"]")
                .doesNotContain("super-secret-token");
    }

    @Test
    void rejectsDeepInputBeforeToolDispatchWithStructuredErrorAndAuditFailure() {
        var stderr = new ByteArrayOutputStream();
        var original = System.err;
        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
        try {
            var result = registry().call("gatling_interaction_status", nestedInput(30));

            assertThat(result.error()).isTrue();
            assertThat(result.structured())
                    .containsEntry("code", "input.depth_exceeded")
                    .containsEntry("severity", "error");
        } finally {
            System.setErr(original);
        }

        var audit = stderr.toString(StandardCharsets.UTF_8);
        assertThat(audit)
                .contains("\"event\":\"tool.started\"")
                .contains("\"event\":\"tool.failed\"")
                .contains("\"tool\":\"gatling_interaction_status\"")
                .contains("input.depth_exceeded");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedInput(int depth) {
        Map<String, Object> value = Map.of("leaf", "value");
        for (int i = 0; i < depth; i++) {
            value = Map.of("level" + i, value);
        }
        return value;
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
}
