package io.github.gatlingcommunity.mcp.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gatlingcommunity.mcp.analysis.SimulationAnalyzer;
import io.github.gatlingcommunity.mcp.compatibility.CapabilityService;
import io.github.gatlingcommunity.mcp.data.SourceDataRepository;
import io.github.gatlingcommunity.mcp.detect.ProjectDetector;
import io.github.gatlingcommunity.mcp.generation.CommunityPluginSimulationGenerator;
import io.github.gatlingcommunity.mcp.generation.HttpSimulationGenerator;
import io.github.gatlingcommunity.mcp.generation.SimulationGenerator;
import io.github.gatlingcommunity.mcp.resources.GatlingPromptCatalog;
import io.github.gatlingcommunity.mcp.resources.GatlingResourceCatalog;
import io.github.gatlingcommunity.mcp.validation.FeatureUsageValidator;
import io.github.gatlingcommunity.mcp.validation.SecretMasker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpServerSmokeTest {
    @Test
    void toolRegistryListsAndCallsCapabilityTool() {
        var registry = registry();

        assertThat(registry.toolNames()).contains(
                "gatling_resolve_capabilities",
                "gatling_detect_project",
                "gatling_get_project_context",
                "gatling_explain_project_context",
                "gatling_resolve_effective_context",
                "gatling_generate_simulation",
                "gatling_analyze_simulation",
                "gatling_validate_feature_usage",
                "gatling_list_dsl_methods",
                "gatling_validate_method_chain",
                "gatling_explain_dsl_method",
                "gatling_find_replacement_method",
                "gatling_explain_version_constraints",
                "gatling_plan_simulation",
                "gatling_validate_simulation_plan",
                "gatling_generate_from_plan",
                "gatling_explain_generation_decisions",
                "gatling_generate_patch",
                "gatling_validate_generated_code",
                "gatling_compile_check",
                "gatling_import_openapi",
                "gatling_import_har",
                "gatling_import_curl",
                "gatling_import_postman_collection",
                "gatling_extract_endpoints_from_codebase",
                "gatling_analyze_report",
                "gatling_analyze_log",
                "gatling_explain_errors",
                "gatling_suggest_load_model",
                "gatling_check_feeder_risk",
                "gatling_check_correlation_risk",
                "gatling_check_assertion_quality",
                "gatling_interaction_status"
        );

        var result = registry.call("gatling_resolve_capabilities", Map.of(
                "gatlingVersion", "3.15",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP"
        ));

        assertThat(result.structured()).containsEntry("supported", true);
        assertThat(result.text()).contains("generationMode=deep");
    }

    @Test
    void resourceRegistryDiscoversAndReadsCapabilityOnlyProtocolNotes() {
        var registry = registry();

        assertThat(registry.resources().listUris()).contains(
                "gatling://compatibility/protocol-matrix",
                "gatling://examples/java/websocket/capability-notes",
                "gatling://examples/java/sse/capability-notes",
                "gatling://examples/java/jms/capability-notes",
                "gatling://examples/java/mqtt/capability-notes",
                "gatling://examples/java/grpc/capability-notes"
        );
        assertThat(registry.resources().read("gatling://compatibility/protocol-matrix"))
                .contains("HTTP", "deep generation", "capability-only");
        assertThat(registry.resources().read("gatling://examples/java/grpc/capability-notes"))
                .contains("GRPC", "Full code generation is disabled")
                .doesNotContain("extends Simulation");
    }

    @Test
    void projectContextToolsReturnDetailedContextAndEffectiveTarget() throws Exception {
        var project = projectFixture("target/project-context-smoke");
        var registry = registry();

        var context = registry.call("gatling_get_project_context", Map.of("path", project.toString()));

        assertThat(context.error()).isFalse();
        assertThat(context.structured())
                .containsEntry("buildTool", "MAVEN")
                .containsEntry("language", "JAVA")
                .containsEntry("detectedGatlingVersion", "3.9.5")
                .containsEntry("javaVersion", "25");
        assertThat(context.structured().get("sourceRoots").toString()).contains("src/gatling/java");
        assertThat(context.structured().get("testRoots").toString()).contains("src/test/java");
        assertThat(context.structured().get("existingSimulationFiles").toString()).contains("OrdersSimulation.java");
        assertThat(context.structured().get("dependencyState").toString())
                .contains("gatling-charts-highcharts", "gatling-maven-plugin");
        assertThat(context.structured().get("pluginState").toString()).contains("KAFKA");
        assertThat(context.structured().get("packageNaming").toString()).contains("com.acme.load");
        assertThat(context.structured().get("styleConventions").toString()).contains("static-imports");

        var explanation = registry.call("gatling_explain_project_context", Map.of("path", project.toString()));
        assertThat(explanation.error()).isFalse();
        assertThat(explanation.text()).contains("MAVEN", "JAVA", "3.9.5", "OrdersSimulation.java");

        var effective = registry.call("gatling_resolve_effective_context", Map.of(
                "path", project.toString(),
                "protocol", "HTTP"
        ));
        assertThat(effective.error()).isFalse();
        assertThat(effective.structured())
                .containsEntry("buildTool", "MAVEN")
                .containsEntry("language", "JAVA")
                .containsEntry("gatlingVersion", "3.9.5")
                .containsEntry("protocol", "HTTP");
        assertThat(effective.structured().get("sources").toString()).contains("project");
    }

    @Test
    void rejectsProjectPathsOutsideWorkspaceRoot() throws Exception {
        var outside = Files.createTempDirectory("gatling-mcp-outside");
        Files.writeString(outside.resolve("pom.xml"), """
                <project>
                  <properties><gatling.version>3.15.1</gatling.version></properties>
                </project>
                """);
        var registry = registry();

        var result = registry.call("gatling_detect_project", Map.of("path", outside.toString()));

        assertThat(result.error()).isTrue();
        assertThat(result.structured())
                .containsEntry("code", "path.outside_workspace")
                .containsEntry("severity", "error");
    }

    @Test
    void extractsEndpointsFromSpringAndNodeCodebase() throws Exception {
        var project = Path.of("target/codebase-endpoint-smoke").toAbsolutePath().normalize();
        Files.createDirectories(project.resolve("src/main/java/com/acme/api"));
        Files.createDirectories(project.resolve("src/main/js"));
        Files.writeString(project.resolve("src/main/java/com/acme/api/OrdersController.java"), """
                package com.acme.api;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping("/api")
                class OrdersController {
                    @GetMapping("/orders")
                    String listOrders() { return "ok"; }

                    @PostMapping(path = "/orders")
                    String createOrder() { return "ok"; }
                }
                """);
        Files.writeString(project.resolve("src/main/js/app.js"), """
                const express = require("express");
                const app = express();
                app.get("/health", (_req, res) => res.send("ok"));
                app.post("/login", (_req, res) => res.json({token: "abc"}));
                """);
        var registry = registry();

        var extracted = registry.call("gatling_extract_endpoints_from_codebase", Map.of(
                "path", project.toString(),
                "languageHint", "JAVA",
                "maxFiles", 50
        ));

        assertThat(extracted.error()).isFalse();
        assertThat(extracted.structured().get("endpointInventory").toString())
                .contains("GET /api/orders", "POST /api/orders", "GET /health", "POST /login");
        assertThat(extracted.structured().get("warnings").toString()).doesNotContain("codebase.file_limit_reached");
    }

    @Test
    void rejectsOversizedTextInputsWithStructuredToolError() {
        assertTooLarge("gatling_import_openapi", "document", largeString(2_000_001));
        assertTooLarge("gatling_analyze_log", "logText", largeString(5_000_001));
        assertTooLarge("gatling_validate_feature_usage", "code", largeString(1_000_001));
        assertTooLarge("gatling_analyze_report", "reportContent", largeString(2_000_001));
    }

    @Test
    void generateSimulationToolReturnsCode() {
        var registry = registry();

        var result = registry.call("gatling_generate_simulation", Map.of(
                "gatlingVersion", "3.15",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "simulationClassName", "ApiSimulation"
        ));

        assertThat(((Map<?, ?>) result.structured().get("target")).get("language")).isEqualTo("JAVA");
        assertThat(result.structured()).containsEntry("generationMode", "deep");
        assertThat(result.structured()).containsKey("code");
        assertThat(result.text()).contains("class ApiSimulation extends Simulation");
    }

    @Test
    void generateSimulationToolReturnsVerifiedJvmPluginCode() {
        var registry = registry();

        var result = registry.call("gatling_generate_simulation", Map.of(
                "gatlingVersion", "3.13.5",
                "language", "SCALA",
                "buildTool", "SBT",
                "protocol", "KAFKA",
                "plugin", "KAFKA",
                "pluginVersion", "1.0.6",
                "javaVersion", "17",
                "simulationClassName", "KafkaSimulation"
        ));

        assertThat(((Map<?, ?>) result.structured().get("target")).get("language")).isEqualTo("SCALA");
        assertThat(result.structured()).containsEntry("generationMode", "deep-plugin");
        var plugin = (Map<?, ?>) result.structured().get("plugin");
        assertThat(plugin.get("version")).isEqualTo("1.0.6");
        assertThat(plugin.get("verifiedAgainstGatlingVersion")).isEqualTo("3.13.5");
        assertThat(result.text()).contains("org.galaxio.gatling.kafka.Predef._");
    }

    @Test
    void listDslMethodsToolReturnsStructuredMethodCatalog() {
        var registry = registry();

        var result = registry.call("gatling_list_dsl_methods", Map.of(
                "gatlingVersion", "3.15.1",
                "language", "KOTLIN",
                "protocol", "HTTP",
                "category", "CHECK"
        ));

        assertThat(result.text()).contains("methods=");
        assertThat(result.structured()).containsEntry("language", "KOTLIN");
        assertThat(result.structured()).containsEntry("matchedGatlingLine", "3.15");
        assertThat((Integer) result.structured().get("count")).isGreaterThanOrEqualTo(10);
        assertThat(result.structured().get("methods").toString())
                .contains("status.is", "status().shouldBe(200)", "OFFICIAL_DOCS");
    }

    @Test
    void explainVersionConstraintsToolReturnsStructuredRules() {
        var registry = registry();

        var result = registry.call("gatling_explain_version_constraints", Map.of(
                "gatlingVersion", "3.11.2",
                "language", "JAVA",
                "protocol", "HTTP"
        ));

        assertThat(result.structured()).containsEntry("matchedGatlingLine", "3.11");
        assertThat(result.structured().get("breakingChanges").toString())
                .contains("heavisideUsers", "stressPeakUsers");
        assertThat(result.structured().get("features").toString()).contains("java-sdk");
        assertThat((Integer) result.structured().get("dslMethodCount")).isGreaterThanOrEqualTo(95);
    }

    @Test
    void validateMethodChainExplainsMissingPredecessorsAndValidChains() {
        var registry = registry();

        var broken = registry.call("gatling_validate_method_chain", Map.of(
                "gatlingVersion", "3.9.5",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "methods", java.util.List.of("scenario", "exec", "get", "check", "status.is")
        ));

        assertThat(broken.error()).isFalse();
        assertThat(broken.structured()).containsEntry("valid", false);
        assertThat(broken.structured()).containsEntry("matchedGatlingLine", "3.9");
        assertThat(broken.structured().get("findings").toString())
                .contains("method_chain.missing_predecessor", "get", "http");
        assertThat(broken.structured().get("methodRequirements").toString())
                .contains("allowedParentContext", "requiredPrecedingMethod", "compileRiskNotes");

        var incompatible = registry.call("gatling_validate_method_chain", Map.of(
                "gatlingVersion", "3.9.5",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "methods", java.util.List.of("Simulation", "http", "post", "body.StringBody", "formParam")
        ));

        assertThat(incompatible.error()).isFalse();
        assertThat(incompatible.structured()).containsEntry("valid", false);
        assertThat(incompatible.structured().get("findings").toString())
                .contains("method_chain.incompatible_method", "body.StringBody", "formParam");

        var valid = registry.call("gatling_validate_method_chain", Map.of(
                "gatlingVersion", "3.9.5",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "methods", java.util.List.of(
                        "Simulation", "http", "baseUrl", "scenario", "exec", "http",
                        "post", "body.StringBody", "asJson", "check", "jsonPath.saveAs",
                        "get", "header", "check", "status.is", "rampUsersPerSec",
                        "global.failedRequests.percent.lt")
        ));

        assertThat(valid.error()).isFalse();
        assertThat(valid.structured()).containsEntry("valid", true);
        assertThat(valid.structured().get("transitions").toString())
                .contains("jsonPath.saveAs", "CORRELATION");
    }

    @Test
    void explainDslMethodAndFindReplacementReturnStructuredMethodFacts() {
        var registry = registry();

        var explained = registry.call("gatling_explain_dsl_method", Map.of(
                "gatlingVersion", "3.9.5",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "method", "jsonPath.saveAs"
        ));

        assertThat(explained.error()).isFalse();
        assertThat(explained.structured()).containsEntry("found", true);
        assertThat(explained.structured()).containsEntry("matchedGatlingLine", "3.9");
        assertThat(explained.structured().get("method").toString())
                .contains("CORRELATION", "jsonPath(path).saveAs(name)", "OFFICIAL_DOCS");
        assertThat(explained.structured().get("semantic").toString())
                .contains("allowedParentContext", "check", "requiredPrecedingMethod");

        var replacement = registry.call("gatling_find_replacement_method", Map.of(
                "gatlingVersion", "3.11.2",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "method", "heavisideUsers"
        ));

        assertThat(replacement.error()).isFalse();
        assertThat(replacement.structured()).containsEntry("found", true);
        assertThat(replacement.structured().get("replacements").toString())
                .contains("stressPeakUsers", "Gatling 3.11 renamed heavisideUsers");
    }

    @Test
    void compileCheckRunsWhitelistedCompileCommandInIsolatedCopy() throws Exception {
        var project = compileProjectFixture("target/compile-check-smoke");
        var registry = registry();

        var planned = registry.call("gatling_compile_check", Map.of(
                "path", project.toString(),
                "buildTool", "MAVEN",
                "execute", false
        ));
        assertThat(planned.error()).isFalse();
        assertThat(planned.structured())
                .containsEntry("executed", false)
                .containsEntry("status", "planned")
                .containsEntry("success", true);
        assertThat(planned.structured().get("command").toString()).contains("test-compile");

        var executed = registry.call("gatling_compile_check", Map.of(
                "path", project.toString(),
                "buildTool", "MAVEN",
                "timeoutSeconds", 90
        ));

        assertThat(executed.error()).isFalse();
        assertThat(executed.structured())
                .containsEntry("executed", true)
                .containsEntry("success", true)
                .containsEntry("status", "passed")
                .containsEntry("buildTool", "MAVEN");
        assertThat(executed.structured().get("command").toString())
                .contains("mvn", "test-compile");
        assertThat(executed.structured().get("workingDirectory").toString())
                .contains("compile-check-smoke");
    }

    @Test
    void planSimulationToolTurnsIntentIntoStructuredHttpPlan() {
        var registry = registry();

        var result = registry.call("gatling_plan_simulation", Map.of(
                "gatlingVersion", "3.9.5",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "simulationClassName", "OrdersSimulation",
                "goal", "login, extract JWT token, call /api/orders with Authorization header, assert 200 and non-empty orders",
                "baseUrl", "https://api.example.test"
        ));

        assertThat(result.error()).isFalse();
        assertThat(result.structured()).containsEntry("matchedGatlingLine", "3.9");
        assertThat(result.structured()).containsEntry("valid", true);
        assertThat(result.structured().get("plan").toString())
                .contains("OrdersSimulation", "POST /login", "GET /api/orders", "Authorization", "jwtToken");
        assertThat(result.structured().get("methodRequirements").toString())
                .contains("jsonPath.saveAs", "status.is", "header");
    }

    @Test
    void planSimulationToolBuildsRichHttpFlowFromOperationalIntent() {
        var registry = registry();

        var result = registry.call("gatling_plan_simulation", Map.of(
                "gatlingVersion", "3.9.5",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "simulationClassName", "RichOrdersSimulation",
                "goal", "feed users.csv, group checkout, login with basic auth, pause 2 seconds, repeat 10 orders calls, if token exists call upload, use http2 and proxy proxy.local:8080",
                "baseUrl", "https://api.example.test"
        ));

        assertThat(result.error()).isFalse();
        assertThat(result.structured()).containsEntry("valid", true);
        assertThat(result.structured().get("plan").toString())
                .contains("users.csv", "feed", "group", "checkout", "pause", "durationSeconds=2",
                        "repeat", "count=10", "ifEquals", "basic", "http2=true", "proxy.local");
    }

    @Test
    void validateSimulationPlanAcceptsStepsOnlyRichHttpFlow() {
        var registry = registry();

        var result = registry.call("gatling_validate_simulation_plan", Map.of(
                "gatlingVersion", "3.9.5",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "plan", Map.of(
                        "simulationClassName", "StepsOnlySimulation",
                        "scenarioName", "Steps only API",
                        "baseUrl", "https://api.example.test",
                        "requests", java.util.List.of(),
                        "feeders", java.util.List.of(Map.of(
                                "name", "users",
                                "type", "csv",
                                "source", "users.csv",
                                "strategy", "circular",
                                "columns", java.util.List.of("username", "password")
                        )),
                        "protocolOptions", Map.of(
                                "http2", true,
                                "followRedirects", false,
                                "proxyHost", "proxy.local",
                                "proxyPort", 8080
                        ),
                        "steps", java.util.List.of(
                                Map.of("type", "feed", "feederName", "users"),
                                Map.of("type", "group", "group", Map.of(
                                        "name", "checkout",
                                        "steps", java.util.List.of(
                                                Map.of("type", "request", "request", Map.of(
                                                        "name", "POST /login",
                                                        "method", "POST",
                                                        "path", "/login",
                                                        "formParams", Map.of("username", "#{username}", "password", "#{password}"),
                                                        "auth", Map.of("type", "basic", "username", "#{username}", "password", "#{password}"),
                                                        "checks", java.util.List.of(
                                                                Map.of("type", "status", "operator", "is", "expected", "200"),
                                                                Map.of("type", "jsonPath", "expression", "$.token",
                                                                        "operator", "saveAs", "saveAs", "jwtToken")
                                                        )
                                                )),
                                                Map.of("type", "pause", "pause", Map.of("durationSeconds", 2)),
                                                Map.of("type", "repeat", "loop", Map.of(
                                                        "type", "repeat",
                                                        "count", 10,
                                                        "steps", java.util.List.of(Map.of("type", "request", "request", Map.of(
                                                                "name", "GET /api/orders",
                                                                "method", "GET",
                                                                "path", "/api/orders",
                                                                "queryParams", Map.of("state", "open"),
                                                                "headers", Map.of("Authorization", "Bearer #{jwtToken}"),
                                                                "resources", java.util.List.of(Map.of(
                                                                        "name", "GET /assets/app.js",
                                                                        "method", "GET",
                                                                        "path", "/assets/app.js"
                                                                )),
                                                                "options", Map.of("followRedirects", false),
                                                                "checks", java.util.List.of(Map.of(
                                                                        "type", "status",
                                                                        "operator", "is",
                                                                        "expected", "200"
                                                                ))
                                                        )))
                                                ))
                                        )
                                )),
                                Map.of("type", "ifEquals", "conditional", Map.of(
                                        "left", "#{jwtToken}",
                                        "operator", "exists",
                                        "steps", java.util.List.of(Map.of("type", "request", "request", Map.of(
                                                "name", "POST /upload",
                                                "method", "POST",
                                                "path", "/upload",
                                                "headers", Map.of("Authorization", "Bearer #{jwtToken}"),
                                                "checks", java.util.List.of(Map.of(
                                                        "type", "status",
                                                        "operator", "is",
                                                        "expected", "201"
                                                ))
                                        )))
                                ))
                        ),
                        "injectionProfile", Map.of("type", "rampAndConstant"),
                        "assertions", java.util.List.of(Map.of(
                                "metric", "global.failedRequests.percent",
                                "operator", "lt",
                                "value", 1.0
                        ))
                )
        ));

        assertThat(result.error()).isFalse();
        assertThat(result.structured()).containsEntry("valid", true);
        assertThat(result.structured().get("findings").toString()).doesNotContain("plan.requests.empty");
        assertThat(result.structured().get("methodRequirements").toString())
                .contains("feed", "csv", "circular", "group", "pause", "repeat", "doIf",
                        "queryParam", "formParam", "resources", "basicAuth", "jsonPath.saveAs");
    }

    @Test
    void validateSimulationPlanReportsMissingCorrelationVariables() {
        var registry = registry();

        var result = registry.call("gatling_validate_simulation_plan", Map.of(
                "gatlingVersion", "3.9.5",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "plan", Map.of(
                        "simulationClassName", "BrokenSimulation",
                        "scenarioName", "Broken API",
                        "baseUrl", "https://api.example.test",
                        "requests", java.util.List.of(Map.of(
                                "name", "GET orders",
                                "method", "GET",
                                "path", "/api/orders",
                                "headers", Map.of("Authorization", "Bearer #{jwtToken}"),
                                "checks", java.util.List.of(Map.of(
                                        "type", "status",
                                        "expected", 200
                                ))
                        )),
                        "injectionProfile", Map.of("type", "rampAndConstant"),
                        "assertions", java.util.List.of(Map.of(
                                "metric", "global.failedRequests.percent",
                                "operator", "lt",
                                "value", 1.0
                        ))
                )
        ));

        assertThat(result.error()).isFalse();
        assertThat(result.structured()).containsEntry("valid", false);
        assertThat(result.structured().get("findings").toString())
                .contains("plan.correlation.missing", "jwtToken");
    }

    @Test
    void generateFromPlanProducesCorrelatedJavaSimulation() {
        var registry = registry();
        var planned = registry.call("gatling_plan_simulation", Map.of(
                "gatlingVersion", "3.9.5",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "simulationClassName", "OrdersSimulation",
                "goal", "login, extract JWT token, call /api/orders with Authorization header, assert 200 and non-empty orders",
                "baseUrl", "https://api.example.test"
        ));

        var generated = registry.call("gatling_generate_from_plan", Map.of(
                "gatlingVersion", "3.9.5",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "plan", planned.structured().get("plan")
        ));

        assertThat(generated.error()).isFalse();
        assertThat(generated.structured()).containsEntry("valid", true);
        assertThat(generated.text())
                .contains("class OrdersSimulation extends Simulation")
                .contains(".post(\"/login\")")
                .contains("jsonPath(\"$.token\").saveAs(\"jwtToken\")")
                .contains(".header(\"Authorization\", \"Bearer #{jwtToken}\")")
                .contains("jsonPath(\"$.orders[0]\").exists()");
    }

    @Test
    void generateFromPlanProducesDslSpecificCorrelatedSimulations() {
        var registry = registry();
        var planned = registry.call("gatling_plan_simulation", Map.of(
                "gatlingVersion", "3.9.5",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "simulationClassName", "OrdersSimulation",
                "goal", "login, extract JWT token, call /api/orders with Authorization header, assert 200 and non-empty orders",
                "baseUrl", "https://api.example.test"
        ));

        var kotlin = registry.call("gatling_generate_from_plan", Map.of(
                "gatlingVersion", "3.9.5",
                "language", "KOTLIN",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "plan", planned.structured().get("plan")
        ));
        assertThat(kotlin.text())
                .contains("class OrdersSimulation : Simulation()")
                .contains("status().shouldBe(200)")
                .contains("StringBody(\"{\\\"username\\\":\\\"#{username}\\\",\\\"password\\\":\\\"#{password}\\\"}\")");

        var scala = registry.call("gatling_generate_from_plan", Map.of(
                "gatlingVersion", "3.9.5",
                "language", "SCALA",
                "buildTool", "SBT",
                "protocol", "HTTP",
                "plan", planned.structured().get("plan")
        ));
        assertThat(scala.text())
                .contains("class OrdersSimulation extends Simulation")
                .contains(".body(StringBody(\"{\\\"username\\\":\\\"#{username}\\\",\\\"password\\\":\\\"#{password}\\\"}\")).asJson")
                .contains("jsonPath(\"$.token\").saveAs(\"jwtToken\")");

        var typescript = registry.call("gatling_generate_from_plan", Map.of(
                "gatlingVersion", "3.11.2",
                "language", "TYPESCRIPT",
                "buildTool", "NPM",
                "protocol", "HTTP",
                "plan", planned.structured().get("plan")
        ));
        assertThat(typescript.text())
                .contains("export default simulation")
                .contains(".body(StringBody(\"{\\\"username\\\":\\\"#{username}\\\",\\\"password\\\":\\\"#{password}\\\"}\")).asJson()")
                .contains("jsonPath(\"$.orders[0]\").exists()");
    }

    @Test
    void generationDecisionPatchAndCodeValidationToolsReturnStructuredOutputs() {
        var registry = registry();
        var planned = registry.call("gatling_plan_simulation", Map.of(
                "gatlingVersion", "3.9.5",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "simulationClassName", "OrdersSimulation",
                "goal", "feed users.csv, login, extract JWT token, call /api/orders with Authorization header, assert 200",
                "baseUrl", "https://api.example.test"
        ));

        var targetArgs = Map.of(
                "gatlingVersion", "3.9.5",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "plan", planned.structured().get("plan")
        );

        var decisions = registry.call("gatling_explain_generation_decisions", targetArgs);
        assertThat(decisions.error()).isFalse();
        assertThat(decisions.structured().get("decisions").toString())
                .contains("feeder", "jsonPath.saveAs", "status.is", "why", "verification");

        var patch = registry.call("gatling_generate_patch", with(targetArgs,
                "targetPath", "src/gatling/java/com/acme/load/OrdersSimulation.java"));
        assertThat(patch.error()).isFalse();
        assertThat(patch.structured().get("unifiedDiff").toString())
                .contains("---", "+++", "@@", "OrdersSimulation.java", "class OrdersSimulation");
        assertThat(patch.structured()).containsEntry("writesFiles", false);

        var generated = registry.call("gatling_generate_from_plan", targetArgs);
        var validation = registry.call("gatling_validate_generated_code", Map.of(
                "gatlingVersion", "3.9.5",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "code", generated.text()
        ));
        assertThat(validation.error()).isFalse();
        assertThat(validation.structured())
                .containsEntry("valid", true)
                .containsEntry("language", "JAVA");
        assertThat(validation.structured().get("findings").toString()).doesNotContain("error");
        assertThat(validation.structured().get("compileReadiness").toString()).contains("ready", "canRunCompileCheck=true");
        assertThat(validation.structured().get("recommendedCompileCommand").toString()).contains("mvn", "test-compile");

        var unsafe = registry.call("gatling_validate_generated_code", Map.of(
                "gatlingVersion", "3.9.5",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "code", "class Broken extends Simulation { { setUp(scenario(\"s\").doIf(session -> true).then(exec(http(\"x\").get(\"/\").check(status().is(200))))); } }"
        ));
        assertThat(unsafe.error()).isFalse();
        assertThat(unsafe.structured()).containsEntry("valid", false);
        assertThat(unsafe.structured().get("findings").toString())
                .contains("generated_code.unsafe_condition_always_true");
    }

    @Test
    void importOpenApiToolReturnsPlanThatCanBeGenerated() {
        var registry = registry();

        var imported = registry.call("gatling_import_openapi", Map.of(
                "gatlingVersion", "3.9.5",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "simulationClassName", "ImportedOpenApiSimulation",
                "document", """
                        {
                          "openapi": "3.0.3",
                          "servers": [{"url": "https://api.example.test"}],
                          "paths": {
                            "/login": {
                              "post": {
                                "operationId": "login",
                                "requestBody": {
                                  "content": {
                                    "application/json": {
                                      "schema": {
                                        "type": "object",
                                        "required": ["username", "password"],
                                        "properties": {
                                          "username": {"type": "string"},
                                          "password": {"type": "string"}
                                        }
                                      }
                                    }
                                  }
                                },
                                "responses": {"200": {"description": "ok"}}
                              }
                            }
                          }
                        }
                        """
        ));

        assertThat(imported.error()).isFalse();
        assertThat(imported.structured()).containsEntry("sourceType", "OPENAPI");
        assertThat(imported.structured()).containsEntry("sourceVersion", "3.0.3");
        assertThat(imported.structured()).containsEntry("requestCount", 1);
        assertThat(imported.structured()).containsEntry("valid", true);
        assertThat(imported.structured().get("feederCandidates").toString())
                .contains("username", "password");
        assertThat(imported.structured().get("correlationCandidates").toString()).isEqualTo("[]");
        assertThat(imported.structured().get("endpointInventory").toString())
                .contains("POST /login", "endpoints", "groups");
        assertThat(imported.structured().get("plan").toString())
                .contains("ImportedOpenApiSimulation", "/login", "#{username}", "#{password}");

        var generated = registry.call("gatling_generate_from_plan", Map.of(
                "gatlingVersion", "3.9.5",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "plan", imported.structured().get("plan")
        ));

        assertThat(generated.error()).isFalse();
        assertThat(generated.text())
                .contains("class ImportedOpenApiSimulation extends Simulation")
                .contains(".post(\"/login\")")
                .contains("StringBody(\"{\\\"username\\\":\\\"#{username}\\\",\\\"password\\\":\\\"#{password}\\\"}\")");
    }

    @Test
    void importCurlToolKeepsSafeHeadersAndRedactsSecrets() {
        var registry = registry();

        var imported = registry.call("gatling_import_curl", Map.of(
                "gatlingVersion", "3.15",
                "language", "JAVA",
                "buildTool", "MAVEN",
                "protocol", "HTTP",
                "curl", """
                        curl 'https://api.example.test/api/orders?state=open' \
                          -H 'Accept: application/json' \
                          -H 'Authorization: Bearer real-token'
                        """
        ));

        assertThat(imported.error()).isFalse();
        assertThat(imported.structured()).containsEntry("sourceType", "CURL");
        assertThat(imported.structured()).containsEntry("sourceVersion", "command");
        assertThat(imported.structured()).containsEntry("requestCount", 1);
        assertThat(imported.structured().get("plan").toString())
                .contains("/api/orders", "queryParams={state=open}",
                        "Accept=application/json", "Authorization=Bearer <redacted>");
        assertThat(imported.structured().get("warnings").toString())
                .contains("import.secret.header.redacted");
    }

    @Test
    void analyzeReportToolReturnsStructuredReportFindings() {
        var registry = registry();

        var analyzed = registry.call("gatling_analyze_report", Map.of(
                "reportContent", """
                        var stats = {
                          "type": "GROUP",
                          "name": "Global Information",
                          "stats": {
                            "numberOfRequests": {"total": "10", "ok": "8", "ko": "2"},
                            "minResponseTime": {"total": "10"},
                            "maxResponseTime": {"total": "2500"},
                            "meanResponseTime": {"total": "320"},
                            "percentiles1": {"total": "100"},
                            "percentiles2": {"total": "250"},
                            "percentiles3": {"total": "1600"},
                            "percentiles4": {"total": "2500"},
                            "meanNumberOfRequestsPerSecond": {"total": "2.0"}
                          },
                          "contents": {
                            "req_orders": {
                              "type": "REQUEST",
                              "name": "GET /api/orders",
                              "stats": {
                                "numberOfRequests": {"total": "10", "ok": "8", "ko": "2"},
                                "minResponseTime": {"total": "10"},
                                "maxResponseTime": {"total": "2500"},
                                "meanResponseTime": {"total": "320"},
                                "percentiles1": {"total": "100"},
                                "percentiles2": {"total": "250"},
                                "percentiles3": {"total": "1600"},
                                "percentiles4": {"total": "2500"},
                                "meanNumberOfRequestsPerSecond": {"total": "2.0"}
                              }
                            }
                          }
                        };
                        """,
                "contentType", "AUTO",
                "errorRateThreshold", 1.0,
                "p95ThresholdMs", 1_000
        ));

        assertThat(analyzed.error()).isFalse();
        assertThat(analyzed.structured()).containsEntry("sourceType", "GATLING_STATS_JS");
        assertThat(analyzed.structured()).containsEntry("requestCount", 1);
        assertThat(analyzed.structured().get("globalStats").toString())
                .contains("errorRate=20.0", "p95Ms=1600");
        assertThat(analyzed.structured().get("findings").toString())
                .contains("report.error-rate.high", "report.p95.high", "GET /api/orders");
    }

    @Test
    void focusedTroubleshootingToolsReturnStructuredRcaContracts() {
        var registry = registry();

        var explained = registry.call("gatling_explain_errors", Map.of(
                "errorText", """
                        ERROR io.netty.handler.timeout.ReadTimeoutException
                        WARN status.find.is(200), but actually found 500
                        WARN Feeder is now empty
                        """
        ));
        assertThat(explained.error()).isFalse();
        assertThat(explained.structured().get("findings").toString())
                .contains("log.timeout", "log.server.error", "log.feeder.empty");
        assertThat(explained.structured().get("rootCauseHypotheses").toString())
                .contains("root_cause.timeout", "verification");

        var noPattern = registry.call("gatling_explain_errors", Map.of(
                "errorText", "all requests completed successfully"
        ));
        assertThat(noPattern.error()).isFalse();
        assertThat(noPattern.structured())
                .containsEntry("confidence", "low");
        assertThat(noPattern.structured().get("findings").toString())
                .contains("errors.no_known_patterns");
        assertThat(noPattern.structured().get("rootCauseHypotheses").toString())
                .isEqualTo("[]");

        var loadModel = registry.call("gatling_suggest_load_model", Map.of(
                "targetRps", 50,
                "durationSeconds", 300,
                "rampSeconds", 60,
                "p95Ms", 250,
                "workloadType", "open"
        ));
        assertThat(loadModel.error()).isFalse();
        assertThat(loadModel.structured().get("suggestedModel").toString())
                .contains("estimatedConcurrentUsers=13", "targetRps=50.0");
        assertThat(loadModel.structured().get("injectionProfile").toString())
                .contains("rampAndConstant", "constantUsersPerSec=50");

        var weakPlan = Map.of(
                "simulationClassName", "WeakSimulation",
                "scenarioName", "Weak",
                "baseUrl", "https://api.example.test",
                "requests", java.util.List.of(Map.of(
                        "name", "GET /api/orders",
                        "method", "GET",
                        "path", "/api/orders",
                        "headers", Map.of("Authorization", "Bearer #{jwtToken}")
                )),
                "feeders", java.util.List.of(Map.of(
                        "name", "users",
                        "type", "csv",
                        "source", "users.csv",
                        "strategy", "queue"
                )),
                "steps", java.util.List.of(Map.of("type", "feed", "feederName", "missing")),
                "injectionProfile", Map.of("type", "rampAndConstant"),
                "assertions", java.util.List.of()
        );

        var feederRisk = registry.call("gatling_check_feeder_risk", Map.of(
                "plan", weakPlan,
                "sampleCsv", "username,password\nu1,p1\n",
                "expectedUsers", 10
        ));
        assertThat(feederRisk.error()).isFalse();
        assertThat(feederRisk.structured().get("findings").toString())
                .contains("feeder.undefined", "feeder.finite_strategy");

        var correlationRisk = registry.call("gatling_check_correlation_risk", Map.of("plan", weakPlan));
        assertThat(correlationRisk.error()).isFalse();
        assertThat(correlationRisk.structured().get("findings").toString())
                .contains("correlation.reference_without_save", "jwtToken");

        var assertionQuality = registry.call("gatling_check_assertion_quality", Map.of("plan", weakPlan));
        assertThat(assertionQuality.error()).isFalse();
        assertThat(assertionQuality.structured().get("findings").toString())
                .contains(
                        "assertion.missing_global_assertions",
                        "assertion.missing_error_budget",
                        "assertion.latency_budget_missing",
                        "assertion.request_status_checks_missing"
                );
    }

    private static McpToolRegistry registry() {
        var sourceData = SourceDataRepository.loadDefault();
        return McpToolRegistry.createDefault(
                new CapabilityService(sourceData),
                new SimulationGenerator(
                        new CapabilityService(sourceData),
                        new HttpSimulationGenerator(),
                        new CommunityPluginSimulationGenerator()
                ),
                new ProjectDetector(),
                new SimulationAnalyzer(new FeatureUsageValidator(sourceData, new SecretMasker())),
                new FeatureUsageValidator(sourceData, new SecretMasker()),
                new GatlingResourceCatalog(sourceData),
                new GatlingPromptCatalog(),
                sourceData
        );
    }

    private static void assertTooLarge(String toolName, String field, String value) {
        var arguments = new java.util.LinkedHashMap<String, Object>();
        if (toolName.startsWith("gatling_import_") || toolName.equals("gatling_validate_feature_usage")) {
            arguments.put("gatlingVersion", "3.9.5");
            arguments.put("language", "JAVA");
            arguments.put("buildTool", "MAVEN");
            arguments.put("protocol", "HTTP");
        }
        arguments.put(field, value);

        var result = registry().call(toolName, arguments);

        assertThat(result.error()).as(toolName).isTrue();
        assertThat(result.structured()).as(toolName)
                .containsEntry("code", "input.too_large")
                .containsEntry("severity", "error")
                .containsEntry("path", "$." + field);
        assertThat(result.structured().get("suggestion").toString()).contains("Reduce");
    }

    private static String largeString(int length) {
        return "x".repeat(length);
    }

    private static Map<String, Object> with(Map<String, Object> base, String key, Object value) {
        var values = new java.util.LinkedHashMap<String, Object>(base);
        values.put(key, value);
        return Map.copyOf(values);
    }

    private static Path projectFixture(String relativePath) throws Exception {
        var project = Path.of(relativePath).toAbsolutePath().normalize();
        Files.createDirectories(project.resolve("src/gatling/java/com/acme/load"));
        Files.createDirectories(project.resolve("src/test/java/com/acme/load"));
        Files.writeString(project.resolve("pom.xml"), """
                <project>
                  <properties>
                    <gatling.version>3.9.5</gatling.version>
                    <maven.compiler.release>25</maven.compiler.release>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>io.gatling.highcharts</groupId>
                      <artifactId>gatling-charts-highcharts</artifactId>
                      <version>${gatling.version}</version>
                    </dependency>
                    <dependency>
                      <groupId>io.gatling</groupId>
                      <artifactId>gatling-app</artifactId>
                      <version>${gatling.version}</version>
                    </dependency>
                    <dependency>
                      <groupId>org.galaxio</groupId>
                      <artifactId>gatling-kafka-plugin</artifactId>
                      <version>verified</version>
                    </dependency>
                  </dependencies>
                  <build>
                    <plugins>
                      <plugin>
                        <groupId>io.gatling</groupId>
                        <artifactId>gatling-maven-plugin</artifactId>
                      </plugin>
                    </plugins>
                  </build>
                </project>
                """);
        Files.writeString(project.resolve("src/gatling/java/com/acme/load/OrdersSimulation.java"), """
                package com.acme.load;

                import static io.gatling.javaapi.core.CoreDsl.*;
                import static io.gatling.javaapi.http.HttpDsl.*;

                import io.gatling.javaapi.core.Simulation;

                public class OrdersSimulation extends Simulation {
                }
                """);
        return project;
    }

    private static Path compileProjectFixture(String relativePath) throws Exception {
        var project = Path.of(relativePath).toAbsolutePath().normalize();
        Files.createDirectories(project.resolve("src/test/java/com/acme/load"));
        Files.writeString(project.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.acme</groupId>
                  <artifactId>compile-check-smoke</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <maven.compiler.release>25</maven.compiler.release>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                  </properties>
                  <build>
                    <plugins>
                      <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-compiler-plugin</artifactId>
                        <version>3.14.0</version>
                        <configuration>
                          <release>${maven.compiler.release}</release>
                        </configuration>
                      </plugin>
                    </plugins>
                  </build>
                </project>
                """);
        Files.writeString(project.resolve("src/test/java/com/acme/load/CompileOnlyTest.java"), """
                package com.acme.load;

                import java.util.SequencedCollection;
                import java.util.List;

                public final class CompileOnlyTest {
                    public SequencedCollection<String> names() {
                        return List.of("orders", "login");
                    }
                }
                """);
        return project;
    }
}
