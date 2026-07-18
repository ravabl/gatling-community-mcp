package io.github.gatlingcommunity.mcp.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import io.github.gatlingcommunity.mcp.core.model.DslLanguage;
import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import io.github.gatlingcommunity.mcp.data.SourceDataRepository;
import io.github.gatlingcommunity.mcp.validation.FeatureUsageValidator;
import io.github.gatlingcommunity.mcp.validation.SecretMasker;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SimulationAnalyzerTest {
    @Test
    void extractsTargetProtocolFeedersCorrelationsInjectionAssertionsAndFindings() {
        var analyzer = new SimulationAnalyzer(new FeatureUsageValidator(SourceDataRepository.loadDefault(), new SecretMasker()));
        var target = new TargetContext("3.11", "community", DslLanguage.SCALA, BuildTool.SBT,
                Optional.of("17"), Optional.empty(), Optional.empty());

        var result = analyzer.analyze(target, """
                class ApiSimulation extends Simulation {
                  val httpProtocol = http.baseUrl("https://example.test").http2Enabled
                  val users = csv("users.csv").circular
                  val scn = scenario("API")
                    .feed(users)
                    .exec(http("GET session").get("/session")
                      .check(status.is(200), jsonPath("$.token").saveAs("token")))
                  setUp(scn.inject(rampUsers(10).during(10)))
                    .protocols(httpProtocol)
                    .assertions(global.failedRequests.percent.lt(1.0))
                }
                """);

        assertThat(result.target()).isEqualTo(target);
        assertThat(result.scenarioNames()).contains("API");
        assertThat(result.requestNames()).contains("GET session");
        assertThat(result.protocolConfigurations()).anyMatch(value -> value.contains("baseUrl"));
        assertThat(result.feeders()).anyMatch(value -> value.contains("users.csv"));
        assertThat(result.correlations()).contains("token");
        assertThat(result.injectionProfiles()).anyMatch(value -> value.contains("rampUsers"));
        assertThat(result.assertions()).anyMatch(value -> value.contains("failedRequests"));
        assertThat(result.checks()).singleElement().asString()
                .contains("status.is(200)", "jsonPath", "saveAs");
        assertThat(result.validationFindings()).extracting("code")
                .doesNotContain("anti-pattern.injection-in-scenario");
    }
}
