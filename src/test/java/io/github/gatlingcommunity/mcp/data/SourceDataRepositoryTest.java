package io.github.gatlingcommunity.mcp.data;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gatlingcommunity.mcp.core.model.CommunityPlugin;
import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import io.github.gatlingcommunity.mcp.core.model.DslLanguage;
import io.github.gatlingcommunity.mcp.core.model.Protocol;
import org.junit.jupiter.api.Test;

class SourceDataRepositoryTest {
    @Test
    void loadsGatlingVersionRangeAndFeatureGates() {
        var repository = SourceDataRepository.loadDefault();

        assertThat(repository.supportedGatlingVersions())
                .containsExactly("3.7", "3.8", "3.9", "3.10", "3.11", "3.12", "3.13", "3.14", "3.15");
        assertThat(repository.knownGatlingVersions())
                .containsExactly("3.0", "3.1", "3.2", "3.3", "3.4", "3.5", "3.6",
                        "3.7", "3.8", "3.9", "3.10", "3.11", "3.12", "3.13", "3.14", "3.15");
        assertThat(repository.supportedVersionLineFor("3.9.5")).contains("3.9");
        assertThat(repository.supportedVersionLineFor("3.15.1")).contains("3.15");
        assertThat(repository.featureRulesFor("3.11"))
                .anySatisfy(rule -> {
                    assertThat(rule.invalidPattern()).contains("heavisideUsers");
                    assertThat(rule.replacement()).contains("stressPeakUsers");
                });
        assertThat(repository.featureRulesFor("3.9.5"))
                .anySatisfy(rule -> assertThat(rule.feature()).isEqualTo("recordsCount"));
        assertThat(repository.featureRulesFor("3.15.1"))
                .anySatisfy(rule -> assertThat(rule.invalidPattern()).isEqualTo("eager().queue()"));
    }

    @Test
    void loadsStrictVerifiedCommunityPluginMatrix() {
        var repository = SourceDataRepository.loadDefault();

        var kafka = repository.pluginSupport(
                CommunityPlugin.KAFKA, "3.13.5", DslLanguage.JAVA, BuildTool.MAVEN, "1.0.6");

        assertThat(kafka.supported()).isTrue();
        assertThat(kafka.plugin()).isEqualTo(CommunityPlugin.KAFKA);
        assertThat(kafka.pluginVersion()).isEqualTo("1.0.6");
        assertThat(kafka.verifiedAgainstGatlingVersion()).isEqualTo("3.13.5");
        assertThat(kafka.sourceUrl()).endsWith("/releases/tag/v1.0.6");
        assertThat(kafka.confidenceLevel().name()).isEqualTo("PLUGIN_RELEASE");
        assertThat(kafka.languages()).contains(DslLanguage.SCALA, DslLanguage.JAVA, DslLanguage.KOTLIN);
        assertThat(kafka.languages()).doesNotContain(DslLanguage.JAVASCRIPT, DslLanguage.TYPESCRIPT);

        assertThat(repository.pluginSupport(
                CommunityPlugin.KAFKA, "3.13.5", DslLanguage.JAVA, BuildTool.MAVEN, "0.20.3").supported())
                .isFalse();
        assertThat(repository.pluginSupport(
                CommunityPlugin.KAFKA, "3.15", DslLanguage.JAVA, BuildTool.MAVEN, "1.0.6").supported())
                .isFalse();
        assertThat(repository.pluginSupport(
                CommunityPlugin.KAFKA, "3.13", DslLanguage.TYPESCRIPT, BuildTool.NPM, "1.0.6").supported())
                .isFalse();
    }

    @Test
    void exposesHttpAsDeepProtocolAndCoreNonHttpAsCapabilityOnly() {
        var repository = SourceDataRepository.loadDefault();

        assertThat(repository.protocolSupport(Protocol.HTTP).generationMode()).isEqualTo("deep");
        assertThat(repository.protocolSupport(Protocol.GRPC).generationMode()).isEqualTo("capability-only");
    }
}
