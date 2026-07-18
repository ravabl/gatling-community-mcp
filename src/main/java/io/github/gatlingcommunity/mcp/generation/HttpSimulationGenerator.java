package io.github.gatlingcommunity.mcp.generation;

public final class HttpSimulationGenerator {
    public String generate(GenerationRequest request) {
        var name = request.simulationClassName();
        return switch (request.target().language()) {
            case JAVA -> javaTemplate(name);
            case KOTLIN -> kotlinTemplate(name);
            case SCALA -> scalaTemplate(name);
            case JAVASCRIPT -> javascriptTemplate(name);
            case TYPESCRIPT -> typescriptTemplate(name);
        };
    }

    private static String javaTemplate(String name) {
        return """
                import static io.gatling.javaapi.core.CoreDsl.*;
                import static io.gatling.javaapi.http.HttpDsl.*;

                import io.gatling.javaapi.core.*;
                import io.gatling.javaapi.http.*;
                import java.time.Duration;

                public class %s extends Simulation {
                  private final HttpProtocolBuilder httpProtocol = http
                      .baseUrl(System.getProperty("baseUrl", "https://example.test"))
                      .acceptHeader("application/json")
                      .contentTypeHeader("application/json")
                      .disableFollowRedirect();

                  private final ScenarioBuilder scn = scenario("HTTP API")
                      .exec(http("GET session")
                          .get("/session")
                          .check(status().is(200))
                          .check(jsonPath("$.sessionId").saveAs("sessionId")))
                      .exec(http("GET resource")
                          .get("/resource")
                          .header("X-Session-Id", "#{sessionId}")
                          .check(status().is(200))
                          .check(jsonPath("$.id").exists()));

                  {
                    setUp(
                        scn.injectOpen(
                            rampUsersPerSec(1).to(5).during(Duration.ofSeconds(30)),
                            constantUsersPerSec(5).during(Duration.ofMinutes(1))
                        )
                    ).protocols(httpProtocol)
                     .assertions(global().failedRequests().percent().lt(1.0));
                  }
                }
                """.formatted(name);
    }

    private static String kotlinTemplate(String name) {
        return """
                import io.gatling.javaapi.core.CoreDsl.*
                import io.gatling.javaapi.http.HttpDsl.*
                import io.gatling.javaapi.core.*
                import io.gatling.javaapi.http.*
                import java.time.Duration

                class %s : Simulation() {
                    private val httpProtocol: HttpProtocolBuilder = http
                        .baseUrl(System.getProperty("baseUrl", "https://example.test"))
                        .acceptHeader("application/json")
                        .contentTypeHeader("application/json")
                        .disableFollowRedirect()

                    private val scn: ScenarioBuilder = scenario("HTTP API")
                        .exec(http("GET session")
                            .get("/session")
                            .check(status().shouldBe(200))
                            .check(jsonPath("$.sessionId").saveAs("sessionId")))
                        .exec(http("GET resource")
                            .get("/resource")
                            .header("X-Session-Id", "#{sessionId}")
                            .check(status().shouldBe(200))
                            .check(jsonPath("$.id").exists()))

                    init {
                        setUp(
                            scn.injectOpen(
                                rampUsersPerSec(1.0).to(5.0).during(Duration.ofSeconds(30)),
                                constantUsersPerSec(5.0).during(Duration.ofMinutes(1))
                            )
                        ).protocols(httpProtocol)
                         .assertions(global().failedRequests().percent().lt(1.0))
                    }
                }
                """.formatted(name);
    }

    private static String scalaTemplate(String name) {
        return """
                import io.gatling.core.Predef._
                import io.gatling.http.Predef._
                import scala.concurrent.duration._

                class %s extends Simulation {
                  val httpProtocol = http
                    .baseUrl(System.getProperty("baseUrl", "https://example.test"))
                    .acceptHeader("application/json")
                    .contentTypeHeader("application/json")
                    .disableFollowRedirect

                  val scn = scenario("HTTP API")
                    .exec(http("GET session")
                      .get("/session")
                      .check(status.is(200))
                      .check(jsonPath("$.sessionId").saveAs("sessionId")))
                    .exec(http("GET resource")
                      .get("/resource")
                      .header("X-Session-Id", "#{sessionId}")
                      .check(status.is(200))
                      .check(jsonPath("$.id").exists))

                  setUp(
                    scn.inject(
                      rampUsersPerSec(1).to(5).during(30.seconds),
                      constantUsersPerSec(5).during(1.minute)
                    )
                  ).protocols(httpProtocol)
                    .assertions(global.failedRequests.percent.lt(1.0))
                }
                """.formatted(name);
    }

    private static String javascriptTemplate(String name) {
        return """
                import { scenario, simulation, jsonPath, global, constantUsersPerSec, rampUsersPerSec } from "@gatling.io/core";
                import { http, status } from "@gatling.io/http";

                export default simulation((setUp) => {
                  const httpProtocol = http
                    .baseUrl(process.env.BASE_URL ?? "https://example.test")
                    .acceptHeader("application/json")
                    .contentTypeHeader("application/json");

                  const scn = scenario("%s")
                    .exec(http("GET session")
                      .get("/session")
                      .check(status().is(200), jsonPath("$.sessionId").saveAs("sessionId")))
                    .exec(http("GET resource")
                      .get("/resource")
                      .header("X-Session-Id", "#{sessionId}")
                      .check(status().is(200), jsonPath("$.id").exists()));

                  setUp(
                    scn.injectOpen(
                      rampUsersPerSec(1).to(5).during(30),
                      constantUsersPerSec(5).during(60)
                    )
                  ).protocols(httpProtocol)
                   .assertions(global().failedRequests().percent().lt(1.0));
                });
                """.formatted(name);
    }

    private static String typescriptTemplate(String name) {
        return javascriptTemplate(name);
    }
}
