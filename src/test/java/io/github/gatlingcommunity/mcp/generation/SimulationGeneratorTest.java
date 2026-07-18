package io.github.gatlingcommunity.mcp.generation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gatlingcommunity.mcp.compatibility.CapabilityService;
import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import io.github.gatlingcommunity.mcp.core.model.CommunityPlugin;
import io.github.gatlingcommunity.mcp.core.model.ConfidenceLevel;
import io.github.gatlingcommunity.mcp.core.model.DslLanguage;
import io.github.gatlingcommunity.mcp.core.model.PluginContext;
import io.github.gatlingcommunity.mcp.core.model.Protocol;
import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import io.github.gatlingcommunity.mcp.data.SourceDataRepository;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SimulationGeneratorTest {
    private final SimulationGenerator generator = new SimulationGenerator(
            new CapabilityService(SourceDataRepository.loadDefault()),
            new HttpSimulationGenerator(),
            new CommunityPluginSimulationGenerator()
    );

    @Test
    void generatesJavaHttpSimulationWithChecksCorrelationAndAssertions() {
        var generated = generator.generate(new GenerationRequest(
                new TargetContext("3.15", "community", DslLanguage.JAVA, BuildTool.MAVEN,
                        Optional.of("25"), Optional.empty(), Optional.empty()),
                Protocol.HTTP,
                "ApiSimulation",
                Map.of("baseUrl", "https://example.test")
        ));

        assertThat(generated.code()).contains("class ApiSimulation extends Simulation");
        assertThat(generated.code()).contains("jsonPath(\"$.sessionId\").saveAs(\"sessionId\")");
        assertThat(generated.code()).contains("constantUsersPerSec(5).during(Duration.ofMinutes(1))");
        assertThat(generated.code()).contains("global().failedRequests().percent().lt(1.0)");
        assertThat(generated.warnings()).isEmpty();
    }

    @Test
    void generatesScalaHttpSimulationWithGatling311ExpressionLanguage() {
        var generated = generator.generate(new GenerationRequest(
                new TargetContext("3.11", "community", DslLanguage.SCALA, BuildTool.SBT,
                        Optional.of("17"), Optional.empty(), Optional.empty()),
                Protocol.HTTP,
                "ApiSimulation",
                Map.of("baseUrl", "https://example.test")
        ));

        assertThat(generated.code()).contains("#{sessionId}");
        assertThat(generated.code()).doesNotContain("${sessionId}");
    }

    @ParameterizedTest
    @MethodSource("coreHttpDslCases")
    void generatesDistinctBaseHttpSimulationForEveryCoreDsl(
            DslLanguage language,
            BuildTool buildTool,
            String classMarker,
            String statusMarker,
            String assertionMarker
    ) {
        var generated = generator.generate(new GenerationRequest(
                new TargetContext("3.15", "community", language, buildTool,
                        language == DslLanguage.JAVASCRIPT || language == DslLanguage.TYPESCRIPT
                                ? Optional.empty() : Optional.of("25"),
                        language == DslLanguage.JAVASCRIPT || language == DslLanguage.TYPESCRIPT
                                ? Optional.of("24") : Optional.empty(),
                        Optional.empty()),
                Protocol.HTTP,
                "ApiSimulation",
                Map.of()
        ));

        assertThat(generated.generationMode()).isEqualTo("deep");
        assertThat(generated.warnings()).isEmpty();
        assertThat(generated.code())
                .contains(classMarker, statusMarker, assertionMarker, "sessionId", "GET resource")
                .doesNotContain("${sessionId}");
    }

    @Test
    void generatesTypescriptHttpSimulationButRejectsJvmPlugin() {
        var generated = generator.generate(new GenerationRequest(
                new TargetContext("3.11.2", "community", DslLanguage.TYPESCRIPT, BuildTool.NPM,
                        Optional.empty(), Optional.of("24"),
                        Optional.of(new PluginContext(CommunityPlugin.KAFKA, "1.0.6", ConfidenceLevel.PLUGIN_RELEASE))),
                Protocol.KAFKA,
                "KafkaSimulation",
                Map.of()
        ));

        assertThat(generated.code()).isBlank();
        assertThat(generated.warnings()).anySatisfy(warning ->
                assertThat(warning.code()).isEqualTo("plugin.dsl.unsupported"));
    }

    @ParameterizedTest
    @MethodSource("verifiedPluginDslCases")
    void generatesVerifiedPluginTemplateForEveryJvmDsl(
            CommunityPlugin plugin,
            Protocol protocol,
            String pluginVersion,
            DslLanguage language,
            BuildTool buildTool,
            String classDeclaration,
            String featureMarker
    ) {
        var generated = generator.generate(new GenerationRequest(
                new TargetContext("3.13.5", "community", language, buildTool,
                        Optional.of("17"), Optional.empty(),
                        Optional.of(new PluginContext(plugin, pluginVersion, ConfidenceLevel.PLUGIN_RELEASE))),
                protocol,
                "PluginSimulation",
                Map.of()
        ));

        assertThat(generated.generationMode()).isEqualTo("deep-plugin");
        assertThat(generated.metadata())
                .containsEntry("plugin", plugin.name())
                .containsEntry("pluginVersion", pluginVersion)
                .containsEntry("verifiedAgainstGatlingVersion", "3.13.5");
        assertThat(generated.code()).contains(classDeclaration, featureMarker);
        assertThat(generated.code()).contains(pluginVersion);
        assertThat(generated.code()).doesNotContain("private final var");
    }

    @Test
    void rejectsUnverifiedPluginVersionWithoutCode() {
        var generated = generator.generate(new GenerationRequest(
                new TargetContext("3.13.5", "community", DslLanguage.JAVA, BuildTool.MAVEN,
                        Optional.of("25"), Optional.empty(),
                        Optional.of(new PluginContext(CommunityPlugin.KAFKA, "9.9.9", ConfidenceLevel.PLUGIN_RELEASE))),
                Protocol.KAFKA,
                "KafkaSimulation",
                Map.of()
        ));

        assertThat(generated.code()).isEmpty();
        assertThat(generated.warnings()).extracting("code").contains("plugin.compatibility.unverified");
    }

    @ParameterizedTest
    @MethodSource("capabilityOnlyProtocols")
    void doesNotGenerateFabricatedCodeForCapabilityOnlyProtocol(Protocol protocol) {
        var generated = generator.generate(new GenerationRequest(
                new TargetContext("3.15", "community", DslLanguage.JAVA, BuildTool.MAVEN,
                        Optional.of("25"), Optional.empty(), Optional.empty()),
                protocol,
                "CapabilityOnlySimulation",
                Map.of("baseUrl", "https://example.test")
        ));

        assertThat(generated.code()).isEmpty();
        assertThat(generated.warnings()).anySatisfy(warning ->
                assertThat(warning.code()).isEqualTo("protocol.generation.disabled.v1"));
    }

    private static Stream<Protocol> capabilityOnlyProtocols() {
        return Stream.of(Protocol.WEBSOCKET, Protocol.SSE, Protocol.JMS, Protocol.MQTT, Protocol.GRPC);
    }

    private static Stream<Arguments> coreHttpDslCases() {
        return Stream.of(
                Arguments.of(DslLanguage.JAVA, BuildTool.MAVEN,
                        "class ApiSimulation extends Simulation", "status().is(200)",
                        "global().failedRequests().percent().lt(1.0)"),
                Arguments.of(DslLanguage.KOTLIN, BuildTool.MAVEN,
                        "class ApiSimulation : Simulation()", "status().shouldBe(200)",
                        "global().failedRequests().percent().lt(1.0)"),
                Arguments.of(DslLanguage.SCALA, BuildTool.SBT,
                        "class ApiSimulation extends Simulation", "status.is(200)",
                        "global.failedRequests.percent.lt(1.0)"),
                Arguments.of(DslLanguage.JAVASCRIPT, BuildTool.NPM,
                        "export default simulation", "status().is(200)",
                        "global().failedRequests().percent().lt(1.0)"),
                Arguments.of(DslLanguage.TYPESCRIPT, BuildTool.NPM,
                        "export default simulation", "status().is(200)",
                        "global().failedRequests().percent().lt(1.0)")
        );
    }

    private static Stream<Arguments> verifiedPluginDslCases() {
        return Stream.of(CommunityPlugin.values()).flatMap(plugin -> Stream.of(
                pluginCase(plugin, DslLanguage.JAVA, BuildTool.MAVEN, "public class PluginSimulation extends Simulation"),
                pluginCase(plugin, DslLanguage.KOTLIN, BuildTool.MAVEN, "class PluginSimulation : Simulation()"),
                pluginCase(plugin, DslLanguage.SCALA, BuildTool.SBT, "class PluginSimulation extends Simulation")
        ));
    }

    private static Arguments pluginCase(
            CommunityPlugin plugin,
            DslLanguage language,
            BuildTool buildTool,
            String classDeclaration
    ) {
        var protocol = switch (plugin) {
            case KAFKA -> Protocol.KAFKA;
            case JDBC -> Protocol.JDBC;
            case AMQP -> Protocol.AMQP;
            case PICATINNY -> Protocol.PICATINNY;
        };
        var version = switch (plugin) {
            case KAFKA -> "1.0.6";
            case JDBC -> "1.2.0";
            case AMQP -> "1.3.3";
            case PICATINNY -> "1.24.0";
        };
        var marker = switch (plugin) {
            case KAFKA -> "requestReply";
            case JDBC -> "queryP";
            case AMQP -> "replyExchange";
            case PICATINNY -> "RandomUUIDFeeder";
        };
        return Arguments.of(plugin, protocol, version, language, buildTool, classDeclaration, marker);
    }
}
