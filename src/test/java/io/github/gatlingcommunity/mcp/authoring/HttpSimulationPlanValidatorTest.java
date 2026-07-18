package io.github.gatlingcommunity.mcp.authoring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import io.github.gatlingcommunity.mcp.core.model.DslLanguage;
import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import io.github.gatlingcommunity.mcp.data.SourceDataRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HttpSimulationPlanValidatorTest {
    private final HttpSimulationPlanValidator validator =
            new HttpSimulationPlanValidator(SourceDataRepository.loadDefault());

    @Test
    void validatesStepsOnlyPlanAndReturnsRichMethodRequirements() {
        var result = validator.validate(target(), HttpPlanTraversalTest.stepsOnlyPlan());

        assertThat(result.valid()).isTrue();
        assertThat(result.findings())
                .extracting(PlanFinding::code)
                .doesNotContain("plan.requests.empty", "plan.correlation.missing", "plan.method.unsupported");
        assertThat(result.methodRequirements().toString())
                .contains(
                        "feed",
                        "csv",
                        "circular",
                        "group",
                        "pause",
                        "repeat",
                        "doIf",
                        "queryParam",
                        "formParam",
                        "resources",
                        "basicAuth",
                        "enableHttp2",
                        "disableFollowRedirect",
                        "proxy",
                        "jsonPath.saveAs"
                );
    }

    @Test
    void reportsCorrelationMissingInsideNestedStepWithPrecisePath() {
        var broken = new HttpSimulationPlan(
                "BrokenStepsSimulation",
                "Broken",
                "https://api.example.test",
                java.util.List.of(),
                java.util.List.of(HttpScenarioStepPlan.group(new GroupPlan(
                        "orders",
                        java.util.List.of(HttpScenarioStepPlan.request(new HttpRequestPlan(
                                "GET /api/orders",
                                "GET",
                                "/api/orders",
                                java.util.Map.of("Authorization", "Bearer #{jwtToken}"),
                                "",
                                java.util.List.of(new CheckPlan("status", "", "is", "200", ""))
                        )))
                ))),
                java.util.List.of(),
                HttpProtocolOptionsPlan.defaults(),
                InjectionProfilePlan.defaultOpenModel(),
                java.util.List.of()
        );

        var result = validator.validate(target(), broken);

        assertThat(result.valid()).isFalse();
        assertThat(result.findings()).anySatisfy(finding -> {
            assertThat(finding.code()).isEqualTo("plan.correlation.missing");
            assertThat(finding.path()).isEqualTo("$.plan.steps[0].group.steps[0].request.headers.Authorization");
            assertThat(finding.message()).contains("jwtToken");
        });
    }

    private static TargetContext target() {
        return new TargetContext("3.9.5", "community", DslLanguage.JAVA, BuildTool.MAVEN,
                Optional.of("25"), Optional.empty(), Optional.empty());
    }
}
