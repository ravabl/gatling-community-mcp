package io.github.gatlingcommunity.mcp.authoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpPlanTraversalTest {
    private final HttpPlanTraversal traversal = new HttpPlanTraversal();

    @Test
    void flattensStepsOnlyPlanInExecutionOrderWithStablePaths() {
        var plan = stepsOnlyPlan();

        var requests = traversal.requestsInExecutionOrder(plan);
        var checks = traversal.checksInExecutionOrder(plan);

        assertThat(requests)
                .extracting(ref -> ref.request().name())
                .containsExactly("POST /login", "GET /api/orders", "POST /upload");
        assertThat(requests)
                .extracting(HttpPlanTraversal.RequestRef::path)
                .containsExactly(
                        "$.plan.steps[1].group.steps[0].request",
                        "$.plan.steps[1].group.steps[2].loop.steps[0].request",
                        "$.plan.steps[2].conditional.steps[0].request"
                );
        assertThat(checks)
                .extracting(ref -> ref.check().saveAs())
                .contains("jwtToken");
        assertThat(traversal.savedVariables(plan)).containsExactly("jwtToken");
        assertThat(traversal.sessionPlaceholders(plan))
                .contains("username", "password", "jwtToken");
        assertThat(traversal.feedStepNames(plan)).containsExactly("users");
        assertThat(traversal.declaredFeederNames(plan)).containsExactly("users");
        assertThat(traversal.stepsInExecutionOrder(plan))
                .extracting(HttpPlanTraversal.StepRef::type)
                .contains("feed", "group", "request", "pause", "repeat", "ifEquals");
    }

    static HttpSimulationPlan stepsOnlyPlan() {
        var login = new HttpRequestPlan(
                "POST /login",
                "POST",
                "/login",
                Map.of("X-Tenant", "acme"),
                Map.of(),
                Map.of("username", "#{username}", "password", "#{password}"),
                List.of(),
                "",
                List.of(),
                new AuthPlan("basic", "#{username}", "#{password}", "", "Authorization"),
                List.of(new CookiePlan("tenant", "acme", "", "/")),
                HttpRequestOptionsPlan.defaults(),
                List.of(
                        new CheckPlan("status", "", "is", "200", ""),
                        new CheckPlan("jsonPath", "$.token", "saveAs", "", "jwtToken")
                )
        );
        var orders = new HttpRequestPlan(
                "GET /api/orders",
                "GET",
                "/api/orders",
                Map.of("Authorization", "Bearer #{jwtToken}"),
                Map.of("state", "open"),
                Map.of(),
                List.of(),
                "",
                List.of(new HttpResourcePlan("GET /assets/app.js", "GET", "/assets/app.js", Map.of())),
                AuthPlan.none(),
                List.of(),
                new HttpRequestOptionsPlan(false, false),
                List.of(new CheckPlan("status", "", "is", "200", ""))
        );
        var upload = new HttpRequestPlan(
                "POST /upload",
                "POST",
                "/upload",
                Map.of("Authorization", "Bearer #{jwtToken}"),
                Map.of(),
                Map.of(),
                List.of(new MultipartPartPlan("file", "", "orders.csv", "text/csv", "data/orders.csv")),
                "",
                List.of(),
                AuthPlan.none(),
                List.of(),
                HttpRequestOptionsPlan.defaults(),
                List.of(new CheckPlan("status", "", "is", "201", ""))
        );
        return new HttpSimulationPlan(
                "RichStepsSimulation",
                "Rich steps",
                "https://api.example.test",
                List.of(),
                List.of(
                        HttpScenarioStepPlan.feed("users"),
                        HttpScenarioStepPlan.group(new GroupPlan("checkout", List.of(
                                HttpScenarioStepPlan.request(login),
                                HttpScenarioStepPlan.pause(new PausePlan(2)),
                                HttpScenarioStepPlan.loop("repeat", new LoopPlan(
                                        "repeat",
                                        10,
                                        0,
                                        List.of(HttpScenarioStepPlan.request(orders))
                                ))
                        ))),
                        HttpScenarioStepPlan.conditional("ifEquals", new ConditionalPlan(
                                "",
                                "#{jwtToken}",
                                "exists",
                                "",
                                List.of(HttpScenarioStepPlan.request(upload))
                        ))
                ),
                List.of(new HttpFeederPlan("users", "csv", "users.csv", "circular",
                        List.of("username", "password"))),
                new HttpProtocolOptionsPlan(
                        Map.of("Accept", "application/json", "Content-Type", "application/json"),
                        false,
                        true,
                        "proxy.local",
                        8080
                ),
                InjectionProfilePlan.defaultOpenModel(),
                List.of(AssertionPlan.defaultFailedRequests())
        );
    }
}
