package io.github.gatlingcommunity.mcp.generation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import io.github.gatlingcommunity.mcp.core.model.DslLanguage;
import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GeneratedCodeValidationServiceTest {
    private final GeneratedCodeValidationService service = new GeneratedCodeValidationService();

    @Test
    void rejectsJavaWithoutSetupAndMissingChecks() {
        var result = service.validate(target(DslLanguage.JAVA, BuildTool.MAVEN), """
                import io.gatling.javaapi.core.Simulation;
                class BrokenSimulation extends Simulation {
                  private final Object scn = http("GET").get("/");
                }
                """);

        assertThat(result).containsEntry("valid", false);
        assertThat(codes(result)).contains("generated_code.setup.missing", "generated_code.checks.missing");
        assertThat(result.get("compileReadiness").toString()).contains("blocked", "canRunCompileCheck=false");
    }

    @Test
    void rejectsDslImportMismatchesAcrossLanguages() {
        var kotlin = service.validate(target(DslLanguage.KOTLIN, BuildTool.MAVEN), """
                import static io.gatling.javaapi.core.CoreDsl.*;
                class BrokenSimulation : Simulation() { init { setUp(scenario("s")) } }
                """);
        var scala = service.validate(target(DslLanguage.SCALA, BuildTool.SBT), """
                import io.gatling.javaapi.core.CoreDsl.*;
                class BrokenSimulation extends Simulation { setUp(scenario("s")) }
                """);

        assertThat(codes(kotlin)).contains("generated_code.dsl_import_mismatch");
        assertThat(codes(scala)).contains("generated_code.dsl_import_mismatch");
    }

    @Test
    void rejectsJavascriptWithoutDefaultSimulationExport() {
        var result = service.validate(target(DslLanguage.TYPESCRIPT, BuildTool.NPM), """
                const scn = scenario("s").exec(http("GET").get("/").check(status().is(200)));
                setUp(scn.injectOpen(atOnceUsers(1)));
                """);

        assertThat(codes(result)).contains("generated_code.simulation.missing");
    }

    @Test
    void rejectsUnsafeAlwaysTrueConditionAndMissingCorrelationSave() {
        var result = service.validate(target(DslLanguage.JAVA, BuildTool.MAVEN), """
                import io.gatling.javaapi.core.Simulation;
                class BrokenSimulation extends Simulation {
                  {
                    setUp(scenario("s")
                      .exec(http("GET orders").get("/orders")
                        .header("Authorization", "Bearer #{jwtToken}")
                        .check(status().is(200)))
                      .doIf(session -> true).then(exec(http("upload").post("/upload").check(status().is(201)))));
                  }
                }
                """);

        assertThat(codes(result)).contains(
                "generated_code.unsafe_condition_always_true",
                "generated_code.correlation.reference_without_save"
        );
    }

    @Test
    void acceptsCorrelatedJavaCodeAndReturnsCompileGuidance() {
        var result = service.validate(target(DslLanguage.JAVA, BuildTool.MAVEN), """
                import io.gatling.javaapi.core.Simulation;
                class OrdersSimulation extends Simulation {
                  {
                    setUp(scenario("s")
                      .exec(http("login").post("/login").check(jsonPath("$.token").saveAs("jwtToken")))
                      .exec(http("orders").get("/orders")
                        .header("Authorization", "Bearer #{jwtToken}")
                        .check(status().is(200))));
                  }
                }
                """);

        assertThat(result).containsEntry("valid", true);
        assertThat(result.get("compileReadiness").toString()).contains("ready", "canRunCompileCheck=true");
        assertThat(result.get("recommendedCompileCommand").toString()).contains("mvn", "test-compile");
    }

    @SuppressWarnings("unchecked")
    private static List<String> codes(Map<String, Object> result) {
        return ((List<Map<String, Object>>) result.get("findings")).stream()
                .map(finding -> String.valueOf(finding.get("code")))
                .toList();
    }

    private static TargetContext target(DslLanguage language, BuildTool buildTool) {
        return new TargetContext("3.9.5", "community", language, buildTool,
                Optional.of("25"), Optional.empty(), Optional.empty());
    }
}
