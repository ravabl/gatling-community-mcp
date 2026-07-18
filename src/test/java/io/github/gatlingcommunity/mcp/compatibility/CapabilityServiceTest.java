package io.github.gatlingcommunity.mcp.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import io.github.gatlingcommunity.mcp.core.model.CommunityPlugin;
import io.github.gatlingcommunity.mcp.core.model.ConfidenceLevel;
import io.github.gatlingcommunity.mcp.core.model.DslLanguage;
import io.github.gatlingcommunity.mcp.core.model.PluginContext;
import io.github.gatlingcommunity.mcp.core.model.Protocol;
import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import io.github.gatlingcommunity.mcp.data.SourceDataRepository;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class CapabilityServiceTest {
    private final CapabilityService service = new CapabilityService(SourceDataRepository.loadDefault());

    @Test
    void supportsHttpDeepGenerationForJavaGatling315() {
        var report = service.resolve(new TargetContext("3.15", "community", DslLanguage.JAVA, BuildTool.MAVEN,
                Optional.of("25"), Optional.empty(), Optional.empty()), Protocol.HTTP);

        assertThat(report.supported()).isTrue();
        assertThat(report.generationMode()).isEqualTo("deep");
        assertThat(report.features()).contains("http", "checks", "feeders", "correlation", "assertions");
        assertThat((Integer) report.metadata().get("dslMethodCount")).isGreaterThanOrEqualTo(32);
        assertThat(report.metadata()).containsKey("dslMethodCategories");
        assertThat(report.warnings()).isEmpty();
    }

    @Test
    void supportsPatchVersionsWithinTheVerifiedGatlingLine() {
        var report = service.resolve(new TargetContext("3.9.5", "community", DslLanguage.JAVA, BuildTool.GRADLE,
                Optional.of("21"), Optional.empty(), Optional.empty()), Protocol.HTTP);

        assertThat(report.supported()).isTrue();
        assertThat(report.generationMode()).isEqualTo("deep");
        assertThat(report.metadata()).containsEntry("matchedGatlingLine", "3.9");
        assertThat(report.metadata()).containsEntry("compatibilityPolicy", "version-line");
        assertThat(report.warnings()).isEmpty();
    }

    @Test
    void rejectsVersionsOutsideTheV1AuthoringRange() {
        var report = service.resolve(new TargetContext("3.16.0", "community", DslLanguage.JAVA, BuildTool.MAVEN,
                Optional.of("25"), Optional.empty(), Optional.empty()), Protocol.HTTP);

        assertThat(report.supported()).isFalse();
        assertThat(report.warnings()).anySatisfy(warning ->
                assertThat(warning.code()).isEqualTo("gatling.version.unsupported.v1"));
    }

    @Test
    void rejectsJavaDslBeforeGatling37() {
        var report = service.resolve(new TargetContext("3.6", "community", DslLanguage.JAVA, BuildTool.MAVEN,
                Optional.of("17"), Optional.empty(), Optional.empty()), Protocol.HTTP);

        assertThat(report.supported()).isFalse();
        assertThat(report.warnings()).anySatisfy(warning ->
                assertThat(warning.code()).isEqualTo("gatling.jvm.dsl.requires.3.7"));
    }

    @Test
    void rejectsJavascriptAndTypescriptBeforeGatling3112() {
        for (var language : java.util.List.of(DslLanguage.JAVASCRIPT, DslLanguage.TYPESCRIPT)) {
            for (var version : java.util.List.of("3.9.5", "3.11.1")) {
                var report = service.resolve(new TargetContext(version, "community", language, BuildTool.NPM,
                        Optional.empty(), Optional.of("24"), Optional.empty()), Protocol.HTTP);

                assertThat(report.supported()).isFalse();
                assertThat(report.warnings()).anySatisfy(warning -> {
                    assertThat(warning.code()).isEqualTo("gatling.javascript.typescript.dsl.requires.3.11.2");
                    assertThat(warning.source()).isEqualTo("https://github.com/gatling/gatling-js/releases/tag/v3.11.2");
                });
            }
        }
    }

    @Test
    void supportsJavascriptAndTypescriptFromGatling3112() {
        for (var language : java.util.List.of(DslLanguage.JAVASCRIPT, DslLanguage.TYPESCRIPT)) {
            var report = service.resolve(new TargetContext("3.11.2", "community", language, BuildTool.NPM,
                    Optional.empty(), Optional.of("24"), Optional.empty()), Protocol.HTTP);

            assertThat(report.supported()).isTrue();
            assertThat(report.generationMode()).isEqualTo("deep");
            assertThat((Integer) report.metadata().get("dslMethodCount")).isGreaterThan(0);
            assertThat(report.warnings()).isEmpty();
        }
    }

    @ParameterizedTest
    @MethodSource("capabilityOnlyProtocols")
    void reportsCoreNonHttpProtocolsAsCapabilityOnlyInCommunityV1(Protocol protocol) {
        var report = service.resolve(new TargetContext("3.15", "community", DslLanguage.JAVA, BuildTool.MAVEN,
                Optional.of("25"), Optional.empty(), Optional.empty()), protocol);

        assertThat(report.supported()).isTrue();
        assertThat(report.generationMode()).isEqualTo("capability-only");
        assertThat(report.features()).contains(protocol.name().toLowerCase());
        assertThat(report.metadata()).containsEntry("protocol", protocol.name());
        assertThat(report.warnings()).anySatisfy(warning ->
                assertThat(warning.code()).isEqualTo("protocol.generation.disabled.v1"));
    }

    private static Stream<Protocol> capabilityOnlyProtocols() {
        return Stream.of(Protocol.WEBSOCKET, Protocol.SSE, Protocol.JMS, Protocol.MQTT, Protocol.GRPC);
    }

    @Test
    void supportsExactVerifiedKafkaPluginForJvmDslOnly() {
        var report = service.resolve(new TargetContext("3.13.5", "community", DslLanguage.SCALA, BuildTool.SBT,
                        Optional.of("17"), Optional.empty(),
                        Optional.of(new PluginContext(CommunityPlugin.KAFKA, "1.0.6", ConfidenceLevel.PLUGIN_RELEASE))),
                Protocol.KAFKA);

        assertThat(report.supported()).isTrue();
        assertThat(report.generationMode()).isEqualTo("deep-plugin");
        assertThat(report.metadata())
                .containsEntry("pluginVersion", "1.0.6")
                .containsEntry("verifiedAgainstGatlingVersion", "3.13.5")
                .containsEntry("confidence", "PLUGIN_RELEASE");
    }

    @Test
    void rejectsKafkaPluginForTypescript() {
        var report = service.resolve(new TargetContext("3.13.5", "community", DslLanguage.TYPESCRIPT, BuildTool.NPM,
                        Optional.empty(), Optional.of("24"),
                        Optional.of(new PluginContext(CommunityPlugin.KAFKA, "1.0.6", ConfidenceLevel.PLUGIN_RELEASE))),
                Protocol.KAFKA);

        assertThat(report.supported()).isFalse();
        assertThat(report.warnings()).anySatisfy(warning ->
                assertThat(warning.code()).isEqualTo("plugin.dsl.unsupported"));
    }

    @Test
    void rejectsUnverifiedPluginVersionWithoutGeneratingOptimisticCode() {
        var report = service.resolve(new TargetContext("3.13.5", "community", DslLanguage.JAVA, BuildTool.MAVEN,
                        Optional.of("25"), Optional.empty(),
                        Optional.of(new PluginContext(CommunityPlugin.KAFKA, "9.9.9", ConfidenceLevel.PLUGIN_RELEASE))),
                Protocol.KAFKA);

        assertThat(report.supported()).isFalse();
        assertThat(report.warnings()).anySatisfy(warning -> {
            assertThat(warning.code()).isEqualTo("plugin.compatibility.unverified");
            assertThat(warning.source()).endsWith("/releases/tag/v1.0.6");
        });
    }
}
