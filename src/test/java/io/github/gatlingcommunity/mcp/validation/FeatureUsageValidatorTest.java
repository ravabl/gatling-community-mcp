package io.github.gatlingcommunity.mcp.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import io.github.gatlingcommunity.mcp.core.model.DslLanguage;
import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import io.github.gatlingcommunity.mcp.data.SourceDataRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FeatureUsageValidatorTest {
    private final FeatureUsageValidator validator = new FeatureUsageValidator(
            SourceDataRepository.loadDefault(),
            new SecretMasker()
    );
    private final TargetContext target311 = new TargetContext("3.11", "community", DslLanguage.SCALA, BuildTool.SBT,
            Optional.of("17"), Optional.empty(), Optional.empty());

    @Test
    void detectsRemovedExpressionLanguageAndRenamedInjection() {
        var result = validator.validate(target311, "http(\"x\").get(\"/${id}\"); heavisideUsers(100)");

        assertThat(result.findings()).anySatisfy(finding -> assertThat(finding.message()).contains("#{"));
        assertThat(result.findings()).anySatisfy(finding -> assertThat(finding.message()).contains("stressPeakUsers"));
    }

    @Test
    void appliesPatchVersionFeatureRules() {
        var target315Patch = new TargetContext("3.15.1", "community", DslLanguage.JAVA, BuildTool.MAVEN,
                Optional.of("25"), Optional.empty(), Optional.empty());

        var result = validator.validate(target315Patch,
                "feeder(\"users.csv\").eager().queue(); http(\"x\").polling(); wait(5)");

        assertThat(result.findings()).anySatisfy(finding ->
                assertThat(finding.message()).contains("feeder loading mode control"));
        assertThat(result.findings()).anySatisfy(finding ->
                assertThat(finding.message()).contains("HTTP polling was renamed to poll"));
        assertThat(result.findings()).anySatisfy(finding ->
                assertThat(finding.message()).contains("MQTT wait was renamed to await"));
    }

    @Test
    void detectsGatlingAuthoringAntiPatterns() {
        var code = "Thread.sleep(1000); println(\"debug\"); scenario(\"x\").inject(atOnceUsers(1));";

        var result = validator.validate(target311, code);

        assertThat(result.findings()).extracting(ValidationFinding::code)
                .contains("anti-pattern.thread-sleep", "anti-pattern.println", "anti-pattern.injection-in-scenario");
    }

    @Test
    void acceptsInjectionInsideSetUp() {
        var code = """
                val scn = scenario("valid").exec(http("request").get("/"))
                setUp(scn.inject(atOnceUsers(1))).protocols(http.baseUrl("https://example.test"))
                """;

        var result = validator.validate(target311, code);

        assertThat(result.findings()).extracting(ValidationFinding::code)
                .doesNotContain("anti-pattern.injection-in-scenario", "anti-pattern.request-in-simulation");
    }

    @Test
    void detectsRequestDeclaredDirectlyInsideSimulationSetup() {
        var code = """
                class BrokenSimulation extends Simulation {
                  setUp(scenario("inline")
                    .exec(http("request in setup").get("/"))
                    .inject(atOnceUsers(1)))
                }
                """;

        var result = validator.validate(target311, code);

        assertThat(result.findings()).extracting(ValidationFinding::code)
                .contains("anti-pattern.request-in-simulation");
    }

    @Test
    void detectsMixedCaseScenarioAndSimulationResponsibilities() {
        var code = """
                class LoginCase { val request = http("login").post("/login") }
                class LoginScenario { val scn = scenario("login") }
                class LoginSimulation extends Simulation { }
                """;

        var result = validator.validate(target311, code);

        assertThat(result.findings()).extracting(ValidationFinding::code)
                .contains("anti-pattern.mixed-layout-responsibilities");
    }

    @Test
    void masksSecretsInFindings() {
        var result = validator.validate(target311, "val secret = \"abcdef1234567890\"");

        assertThat(result.findings()).anySatisfy(finding -> {
            assertThat(finding.code()).isEqualTo("security.hardcoded-secret");
            assertThat(finding.evidence()).doesNotContain("abcdef1234567890");
            assertThat(finding.evidence()).contains("***");
        });
    }

    @Test
    void warnsWhenStatus200IsTheOnlyResponseCheck() {
        var result = validator.validate(target311,
                "scenario(\"x\").exec(http(\"request\").get(\"/\").check(status.is(200)))");

        assertThat(result.findings()).extracting(ValidationFinding::code)
                .contains("anti-pattern.status-only-check");

        var withBusinessCheck = validator.validate(target311,
                "scenario(\"x\").exec(http(\"request\").get(\"/\")"
                        + ".check(status.is(200), jsonPath(\"$.result\").is(\"ok\")))");
        assertThat(withBusinessCheck.findings()).extracting(ValidationFinding::code)
                .doesNotContain("anti-pattern.status-only-check");
    }
}
