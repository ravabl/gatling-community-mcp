package io.github.gatlingcommunity.mcp.fixture;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.csv;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.FeederBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

public class GeneratedSimulation extends Simulation {
    private final HttpProtocolBuilder httpProtocol = http.baseUrl("https://example.test");
    private final FeederBuilder.Batchable<String> users = csv("users.csv").circular();
    private final ScenarioBuilder scenario = scenario("Generated compile fixture")
            .feed(users)
            .exec(http("create session")
                    .post("/sessions")
                    .body(StringBody("{\"username\":\"#{username}\"}"))
                    .asJson()
                    .check(status().is(201), jsonPath("$.token").saveAs("authToken")))
            .exec(http("read profile")
                    .get("/profiles/#{authToken}")
                    .check(status().is(200)));

    {
        setUp(scenario.injectOpen(atOnceUsers(1)))
                .protocols(httpProtocol)
                .assertions(global().successfulRequests().percent().gt(95.0));
    }
}
