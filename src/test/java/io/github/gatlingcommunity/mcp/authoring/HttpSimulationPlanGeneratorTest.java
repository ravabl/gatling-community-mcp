package io.github.gatlingcommunity.mcp.authoring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import io.github.gatlingcommunity.mcp.core.model.DslLanguage;
import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HttpSimulationPlanGeneratorTest {
    private final HttpSimulationPlanGenerator generator = new HttpSimulationPlanGenerator();

    @Test
    void rendersRichJavaHttpPlanWithFirstClassDslFeatures() {
        var code = generator.generate(target(DslLanguage.JAVA), richPlan());

        assertThat(code)
                .contains("class OrdersSimulation extends Simulation")
                .contains("var users = csv(\"users.csv\").circular();")
                .contains(".feed(users)")
                .contains(".group(\"checkout\").on(")
                .contains("pause(Duration.ofSeconds(2))")
                .contains("repeat(3).on(")
                .contains(".queryParam(\"state\", \"open\")")
                .contains(".formParam(\"username\", \"#{username}\")")
                .contains(".bodyPart(StringBodyPart(\"metadata\", \"#{metadata}\"))")
                .contains(".resources(")
                .contains(".basicAuth(\"#{username}\", \"#{password}\")")
                .contains(".header(\"Cookie\", \"tenant=acme\")")
                .contains(".disableFollowRedirect()")
                .contains(".enableHttp2()")
                .contains(".proxy(Proxy(\"proxy.local\", 8080))")
                .contains(".during(Duration.ofSeconds(5)).on(")
                .contains(".doIf(session -> session.contains(\"jwtToken\")).then(")
                .doesNotContain("doIf(session -> true)");
    }

    @Test
    void rendersKotlinWithoutUsingJavaStringReplacement() {
        var code = generator.generate(target(DslLanguage.KOTLIN), richPlan());

        assertThat(code)
                .doesNotContain("import static")
                .contains("class OrdersSimulation : Simulation()")
                .contains("val users = csv(\"users.csv\").circular()")
                .contains("status().shouldBe(200)")
                .contains(".queryParam(\"state\", \"open\")")
                .contains(".formParam(\"username\", \"#{username}\")")
                .contains(".bodyPart(StringBodyPart(\"metadata\", \"#{metadata}\"))")
                .contains(".resources(")
                .contains(".basicAuth(\"#{username}\", \"#{password}\")")
                .contains(".header(\"Cookie\", \"tenant=acme\")")
                .contains(".disableFollowRedirect()")
                .contains(".enableHttp2()")
                .contains(".proxy(Proxy(\"proxy.local\", 8080))")
                .contains(".during(Duration.ofSeconds(5)).on(")
                .contains(".doIf({ session -> session.contains(\"jwtToken\") }).then(");
    }

    @Test
    void rendersScalaRichHttpPlanWithScalaSyntax() {
        var code = generator.generate(target(DslLanguage.SCALA), richPlan());

        assertThat(code)
                .contains("class OrdersSimulation extends Simulation")
                .contains("val users = csv(\"users.csv\").circular")
                .contains("pause(2.seconds)")
                .contains("repeat(3)")
                .contains("during(5.seconds)")
                .contains("doIf(session => session.contains(\"jwtToken\"))")
                .contains(".queryParam(\"state\", \"open\")")
                .contains(".formParam(\"username\", \"#{username}\")")
                .contains(".bodyPart(StringBodyPart(\"metadata\", \"#{metadata}\"))")
                .contains(".resources(")
                .contains(".basicAuth(\"#{username}\", \"#{password}\")")
                .contains(".header(\"Cookie\", \"tenant=acme\")")
                .contains(".proxy(Proxy(\"proxy.local\", 8080))")
                .contains("jsonPath(\"$.token\").saveAs(\"jwtToken\")");
    }

    @Test
    void rendersJavascriptAndTypescriptRichHttpPlan() {
        var javascript = generator.generate(target(DslLanguage.JAVASCRIPT), richPlan());
        var typescript = generator.generate(target(DslLanguage.TYPESCRIPT), richPlan());

        assertThat(javascript)
                .contains("const users = csv(\"users.csv\").circular();")
                .contains(".queryParam(\"state\", \"open\")")
                .contains(".formParam(\"username\", \"#{username}\")")
                .contains(".bodyPart(StringBodyPart(\"metadata\", \"#{metadata}\"))")
                .contains(".resources(")
                .contains(".basicAuth(\"#{username}\", \"#{password}\")")
                .contains(".header(\"Cookie\", \"tenant=acme\")")
                .contains(".proxy({ host: \"proxy.local\", port: 8080 })")
                .contains("during(5).on(")
                .contains(".doIf((session) => session.contains(\"jwtToken\")).then(")
                .contains("status().is(200)");
        assertThat(typescript)
                .contains("export default simulation")
                .contains("repeat(3)")
                .contains("during(5)")
                .contains(".resources(")
                .contains("jsonPath(\"$.token\").saveAs(\"jwtToken\")");
    }

    private static TargetContext target(DslLanguage language) {
        return new TargetContext(
                language == DslLanguage.JAVASCRIPT || language == DslLanguage.TYPESCRIPT ? "3.11.2" : "3.9.5",
                "community",
                language,
                language == DslLanguage.SCALA ? BuildTool.SBT
                        : language == DslLanguage.JAVASCRIPT || language == DslLanguage.TYPESCRIPT
                                ? BuildTool.NPM : BuildTool.MAVEN,
                language == DslLanguage.JAVASCRIPT || language == DslLanguage.TYPESCRIPT
                        ? Optional.empty() : Optional.of("25"),
                language == DslLanguage.JAVASCRIPT || language == DslLanguage.TYPESCRIPT
                        ? Optional.of("24") : Optional.empty(),
                Optional.empty()
        );
    }

    private static HttpSimulationPlan richPlan() {
        var login = new HttpRequestPlan(
                "POST /login",
                "POST",
                "/login",
                Map.of("X-Tenant", "acme"),
                Map.of(),
                Map.of("username", "#{username}", "password", "#{password}"),
                List.of(new MultipartPartPlan("metadata", "#{metadata}", "", "text/plain", "")),
                "",
                List.of(),
                new AuthPlan("basic", "#{username}", "#{password}", "", "Authorization"),
                List.of(new CookiePlan("tenant", "acme", "", "/")),
                new HttpRequestOptionsPlan(false, false),
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
                HttpRequestOptionsPlan.defaults(),
                List.of(
                        new CheckPlan("status", "", "is", "200", ""),
                        new CheckPlan("jsonPath", "$.orders[0]", "exists", "", "")
                )
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
                "OrdersSimulation",
                "Orders API",
                "https://api.example.test",
                List.of(login, orders, upload),
                List.of(
                        HttpScenarioStepPlan.feed("users"),
                        HttpScenarioStepPlan.group(new GroupPlan("checkout", List.of(
                                HttpScenarioStepPlan.request(login),
                                HttpScenarioStepPlan.pause(new PausePlan(2)),
                                HttpScenarioStepPlan.loop("repeat", new LoopPlan("repeat", 3, 0,
                                        List.of(HttpScenarioStepPlan.request(orders))))
                        ))),
                        HttpScenarioStepPlan.loop("during", new LoopPlan("during", 0, 5,
                                List.of(HttpScenarioStepPlan.pause(new PausePlan(1))))),
                        HttpScenarioStepPlan.conditional("ifEquals", new ConditionalPlan(
                                "",
                                "#{jwtToken}",
                                "exists",
                                "",
                                List.of(HttpScenarioStepPlan.request(upload))
                        ))
                ),
                List.of(new HttpFeederPlan("users", "csv", "users.csv", "circular", List.of("username", "password"))),
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
