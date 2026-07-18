package io.github.gatlingcommunity.mcp.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import io.github.gatlingcommunity.mcp.mcp.schema.ToolOutputSchemas;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ToolDefinitionCatalog {
    private static final McpSchema.ToolAnnotations READ_ONLY_LOCAL = McpSchema.ToolAnnotations.builder()
            .readOnlyHint(true)
            .destructiveHint(false)
            .idempotentHint(true)
            .openWorldHint(false)
            .build();
    private static final McpSchema.ToolAnnotations COMPILE_CHECK_LOCAL = McpSchema.ToolAnnotations.builder()
            .readOnlyHint(false)
            .destructiveHint(false)
            .idempotentHint(true)
            .openWorldHint(true)
            .build();

    private final Map<String, ToolDefinition> definitions = new LinkedHashMap<>();

    public ToolDefinitionCatalog() {
        add("gatling_interaction_status",
                "Gatling MCP Interaction Status",
                "Report the current MCP client's elicitation, sampling, and progress support for Gatling interactive authoring flows.",
                ToolSchemas.interactionStatusSchema(),
                ToolOutputSchemas.interactionStatus());
        add("gatling_resolve_capabilities",
                "Resolve Gatling Capabilities",
                "Resolve Gatling Community authoring capabilities for a version, DSL, build tool, protocol, and optional verified plugin.",
                ToolSchemas.resolveCapabilitiesSchema(),
                ToolOutputSchemas.capabilities());
        add("gatling_detect_project",
                "Detect Gatling Project",
                "Inspect local Gatling Community project files and infer build tool, DSL language, version, and confidence.",
                ToolSchemas.detectProjectSchema(),
                ToolOutputSchemas.detectedProject());
        add("gatling_get_project_context",
                "Get Gatling Project Context",
                "Inspect a local Gatling project inside the configured workspace root and return build, source, dependency, plugin, package, and style context.",
                ToolSchemas.projectContextSchema(),
                ToolOutputSchemas.projectContext());
        add("gatling_explain_project_context",
                "Explain Gatling Project Context",
                "Explain detected local Gatling project context in a concise form suitable for an LLM before authoring or validation.",
                ToolSchemas.projectContextSchema(),
                ToolOutputSchemas.projectContext());
        add("gatling_resolve_effective_context",
                "Resolve Effective Gatling Context",
                "Merge explicit overrides with detected local project context so other Gatling tools can be called with a concrete version, DSL, build tool, protocol, and runtimes.",
                ToolSchemas.effectiveContextSchema(),
                ToolOutputSchemas.effectiveContext());
        add("gatling_generate_simulation",
                "Generate Gatling Simulation",
                "Generate a Gatling Community simulation for the selected target context. Prefer plan-based generation for rich HTTP flows.",
                ToolSchemas.generateSimulationSchema(),
                ToolOutputSchemas.generatedSimulation());
        add("gatling_analyze_simulation",
                "Analyze Gatling Simulation",
                "Analyze Gatling Community simulation source code and extract scenarios, requests, checks, and validation findings.",
                ToolSchemas.analyzeSimulationSchema(),
                ToolOutputSchemas.simulationAnalysis());
        add("gatling_validate_feature_usage",
                "Validate Gatling Feature Usage",
                "Validate Gatling Community source code for deprecated APIs, removed APIs, unsupported APIs, secrets, and authoring risks.",
                ToolSchemas.validateFeatureUsageSchema(),
                ToolOutputSchemas.featureUsageValidation());
        add("gatling_list_dsl_methods",
                "List Gatling DSL Methods",
                "List structured Gatling Community DSL method catalog rows filtered by version, language, protocol, and optional category.",
                ToolSchemas.listDslMethodsSchema(),
                ToolOutputSchemas.dslMethods());
        add("gatling_validate_method_chain",
                "Validate Gatling DSL Method Chain",
                "Validate ordered Gatling Community DSL method names against version availability, DSL syntax catalog, parent context, predecessor requirements, and compile-risk notes.",
                ToolSchemas.validateMethodChainSchema(),
                ToolOutputSchemas.methodChainValidation());
        add("gatling_explain_dsl_method",
                "Explain Gatling DSL Method",
                "Explain one Gatling Community DSL method with category, version gates, DSL-specific syntax, source URL, confidence, required predecessor, and compile-risk notes.",
                ToolSchemas.explainDslMethodSchema(),
                ToolOutputSchemas.dslMethodExplanation());
        add("gatling_find_replacement_method",
                "Find Gatling Replacement Method",
                "Find known Gatling Community DSL replacements for renamed, removed, or gated methods using the version feature matrix.",
                ToolSchemas.findReplacementMethodSchema(),
                ToolOutputSchemas.replacementMethods());
        add("gatling_explain_version_constraints",
                "Explain Gatling Version Constraints",
                "Explain Gatling Community feature gates, breaking changes, DSL method count, and method categories for a target context.",
                ToolSchemas.explainVersionConstraintsSchema(),
                ToolOutputSchemas.versionConstraints());
        add("gatling_plan_simulation",
                "Plan Gatling Simulation",
                "Create a structured HTTP simulation plan from user intent before validating and generating Gatling Community code.",
                ToolSchemas.planSimulationSchema(),
                ToolOutputSchemas.simulationPlan(true));
        add("gatling_plan_http_flow",
                "Plan Gatling HTTP Flow",
                "Create a structured Gatling HTTP simulation plan from user intent. Alias of gatling_plan_simulation for HTTP-first clients.",
                ToolSchemas.planSimulationSchema(),
                ToolOutputSchemas.simulationPlan(true));
        add("gatling_plan_correlation",
                "Plan Gatling Correlation",
                "Extract planned Gatling Community correlation checks such as jsonPath saveAs variables from user intent.",
                ToolSchemas.intentOnlySchema(),
                ToolOutputSchemas.correlations());
        add("gatling_plan_checks",
                "Plan Gatling Checks",
                "Extract planned Gatling Community status and body checks from user intent.",
                ToolSchemas.intentOnlySchema(),
                ToolOutputSchemas.checks());
        add("gatling_plan_injection",
                "Plan Gatling Injection",
                "Return a conservative Gatling Community open workload injection profile for first-pass HTTP simulations.",
                ToolSchemas.emptyObjectSchema(),
                ToolOutputSchemas.injectionProfile());
        add("gatling_plan_assertions",
                "Plan Gatling Assertions",
                "Return conservative Gatling Community assertions suitable for first-pass HTTP simulations.",
                ToolSchemas.emptyObjectSchema(),
                ToolOutputSchemas.assertions());
        add("gatling_validate_simulation_plan",
                "Validate Gatling Simulation Plan",
                "Validate a structured Gatling Community simulation plan against version, DSL, method catalog, and correlation rules.",
                ToolSchemas.simulationPlanSchema(),
                ToolOutputSchemas.simulationPlan(false));
        add("gatling_generate_from_plan",
                "Generate Gatling From Plan",
                "Generate Gatling Community code only from a validated structured simulation plan.",
                ToolSchemas.simulationPlanSchema(),
                ToolOutputSchemas.generatedFromPlan());
        add("gatling_explain_generation_decisions",
                "Explain Gatling Generation Decisions",
                "Explain why the plan-based Gatling generator selected specific DSL methods, assumptions, risks, and verification steps.",
                ToolSchemas.simulationPlanSchema(),
                ToolOutputSchemas.generationDecisions());
        add("gatling_generate_patch",
                "Generate Gatling Patch",
                "Return a unified diff for adding or replacing a generated Gatling simulation without writing files.",
                ToolSchemas.patchGenerationSchema(),
                ToolOutputSchemas.generatedPatch());
        add("gatling_validate_generated_code",
                "Validate Generated Gatling Code",
                "Validate generated Gatling source text for required DSL structure before optional compile checks.",
                ToolSchemas.generatedCodeValidationSchema(),
                ToolOutputSchemas.generatedCodeValidation());
        add("gatling_compile_check",
                "Compile Check Gatling Project",
                "Run or plan a local compile-only check for a Gatling project using a whitelisted build-tool command in an isolated temporary project copy.",
                ToolSchemas.compileCheckSchema(),
                ToolOutputSchemas.compileCheck(),
                COMPILE_CHECK_LOCAL);
        add("gatling_import_openapi",
                "Import OpenAPI To Gatling Plan",
                "Import an OpenAPI JSON or YAML document into a structured Gatling HTTP simulation plan, then validate it against the target context.",
                ToolSchemas.importDocumentSchema("OpenAPI JSON or YAML document"),
                ToolOutputSchemas.importedPlan());
        add("gatling_import_har",
                "Import HAR To Gatling Plan",
                "Import a HAR document into a structured Gatling HTTP simulation plan, redacting sensitive headers before returning the plan.",
                ToolSchemas.importDocumentSchema("HAR JSON document"),
                ToolOutputSchemas.importedPlan());
        add("gatling_import_curl",
                "Import curl To Gatling Plan",
                "Import a curl command into a structured Gatling HTTP simulation plan, preserving method, URL, safe headers, body, and default checks.",
                ToolSchemas.importCurlSchema(),
                ToolOutputSchemas.importedPlan());
        add("gatling_import_postman_collection",
                "Import Postman Collection To Gatling Plan",
                "Import a Postman Collection v2.x JSON document into a structured Gatling HTTP simulation plan, including nested request items and simple status tests.",
                ToolSchemas.importDocumentSchema("Postman Collection v2.x JSON document"),
                ToolOutputSchemas.importedPlan());
        add("gatling_extract_endpoints_from_codebase",
                "Extract HTTP Endpoints From Codebase",
                "Scan a local codebase inside the configured workspace root and extract conservative Gatling-ready HTTP endpoint inventory from Spring, Express/Fastify, and OpenAPI files.",
                ToolSchemas.extractCodebaseEndpointsSchema(),
                ToolOutputSchemas.endpointExtraction());
        add("gatling_analyze_report",
                "Analyze Gatling Report",
                "Analyze local Gatling report artifacts such as stats.js or stats.json and return request metrics, slow requests, failures, warnings, and threshold findings.",
                ToolSchemas.analyzeReportSchema(),
                ToolOutputSchemas.reportAnalysis());
        add("gatling_analyze_log",
                "Analyze Gatling Log",
                "Analyze local Gatling runtime logs and best-effort simulation.log request events for common load-test failure patterns and threshold findings.",
                ToolSchemas.analyzeLogSchema(),
                ToolOutputSchemas.reportAnalysis());
        add("gatling_explain_errors",
                "Explain Gatling Errors",
                "Explain Gatling runtime errors, failed checks, stack traces, and log excerpts as root-cause hypotheses with concrete verification steps.",
                ToolSchemas.explainErrorsSchema(),
                ToolOutputSchemas.troubleshooting());
        add("gatling_suggest_load_model",
                "Suggest Gatling Load Model",
                "Suggest a first-pass Gatling load model from target RPS, expected users, duration, ramp, and p95 latency, including assumptions and risks.",
                ToolSchemas.suggestLoadModelSchema(),
                ToolOutputSchemas.troubleshooting());
        add("gatling_check_feeder_risk",
                "Check Gatling Feeder Risk",
                "Inspect a structured Gatling HTTP plan and optional feeder CSV sample for undefined feeders, finite data exhaustion, and placeholder risks.",
                ToolSchemas.feederRiskSchema(),
                ToolOutputSchemas.troubleshooting());
        add("gatling_check_correlation_risk",
                "Check Gatling Correlation Risk",
                "Inspect a Gatling plan or source code for session variables used before check/saveAs correlation or feeder provisioning.",
                ToolSchemas.correlationRiskSchema(),
                ToolOutputSchemas.troubleshooting());
        add("gatling_check_assertion_quality",
                "Check Gatling Assertion Quality",
                "Inspect Gatling request checks and global assertions for missing status checks, error budgets, latency budgets, and weak quality gates.",
                ToolSchemas.assertionQualitySchema(),
                ToolOutputSchemas.troubleshooting());
    }

    public List<ToolDefinition> list() {
        return List.copyOf(definitions.values());
    }

    public ToolDefinition get(String name) {
        var definition = definitions.get(name);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown tool definition: " + name);
        }
        return definition;
    }

    private void add(String name,
                     String title,
                     String description,
                     Map<String, Object> inputSchema,
                     Map<String, Object> outputSchema) {
        add(name, title, description, inputSchema, outputSchema, READ_ONLY_LOCAL);
    }

    private void add(String name,
                     String title,
                     String description,
                     Map<String, Object> inputSchema,
                     Map<String, Object> outputSchema,
                     McpSchema.ToolAnnotations annotations) {
        definitions.put(name, new ToolDefinition(
                name,
                title,
                description,
                inputSchema,
                outputSchema,
                annotations,
                exampleMeta(name, title)
        ));
    }

    private static Map<String, Object> exampleMeta(String name, String title) {
        return Map.of("examples", List.of(Map.of(
                "title", title + " example",
                "arguments", exampleArguments(name)
        )));
    }

    private static Map<String, Object> exampleArguments(String name) {
        return switch (name) {
            case "gatling_interaction_status" -> Map.of();
            case "gatling_resolve_capabilities" -> target("3.15", "JAVA", "MAVEN", "HTTP");
            case "gatling_detect_project", "gatling_get_project_context", "gatling_explain_project_context" ->
                    Map.of("path", "/workspace");
            case "gatling_resolve_effective_context" -> Map.of(
                    "path", "/workspace",
                    "protocol", "HTTP"
            );
            case "gatling_generate_simulation" -> with(target("3.15", "JAVA", "MAVEN", "HTTP"),
                    "simulationClassName", "ApiSimulation");
            case "gatling_analyze_simulation" -> with(target("3.15", "JAVA", "MAVEN", "HTTP"),
                    "code", "scenario(\"API\").exec(http(\"GET orders\").get(\"/api/orders\"))");
            case "gatling_validate_feature_usage" -> with(target("3.9.5", "JAVA", "MAVEN", "HTTP"),
                    "code", "http(\"legacy\").get(\"/${id}\")");
            case "gatling_list_dsl_methods" -> with(target("3.9.5", "JAVA", "MAVEN", "HTTP"),
                    "category", "CHECK");
            case "gatling_validate_method_chain" -> with(target("3.9.5", "JAVA", "MAVEN", "HTTP"),
                    "methods", List.of("scenario", "exec", "http", "get", "check", "status.is"));
            case "gatling_explain_dsl_method" -> with(target("3.9.5", "JAVA", "MAVEN", "HTTP"),
                    "method", "jsonPath.saveAs");
            case "gatling_find_replacement_method" -> with(target("3.11.2", "JAVA", "MAVEN", "HTTP"),
                    "method", "heavisideUsers");
            case "gatling_explain_version_constraints" -> target("3.9.5", "JAVA", "MAVEN", "HTTP");
            case "gatling_plan_simulation", "gatling_plan_http_flow" ->
                    with(with(target("3.9.5", "JAVA", "MAVEN", "HTTP"),
                            "goal", "login, extract JWT token, call /api/orders, assert 200"),
                            "baseUrl", "https://api.example.test");
            case "gatling_plan_correlation" -> Map.of(
                    "goal", "extract $.token from login response and reuse it as Authorization header"
            );
            case "gatling_plan_checks" -> Map.of(
                    "goal", "assert status 200 and non-empty $.orders array"
            );
            case "gatling_plan_injection", "gatling_plan_assertions" -> Map.of();
            case "gatling_validate_simulation_plan", "gatling_generate_from_plan",
                    "gatling_explain_generation_decisions", "gatling_generate_patch" ->
                    with(target("3.9.5", "JAVA", "MAVEN", "HTTP"), "plan", planExample());
            case "gatling_validate_generated_code" -> with(target("3.9.5", "JAVA", "MAVEN", "HTTP"),
                    "code", "public class OrdersSimulation extends Simulation { { setUp(scenario(\"Orders\").exec(http(\"GET\").get(\"/\").check(status().is(200)))); } }");
            case "gatling_compile_check" -> Map.of(
                    "path", "/workspace",
                    "buildTool", "MAVEN",
                    "execute", false
            );
            case "gatling_import_openapi" -> with(target("3.9.5", "JAVA", "MAVEN", "HTTP"),
                    "document", """
                            openapi: 3.0.3
                            info: { title: Orders API, version: 1.0.0 }
                            paths:
                              /api/orders:
                                get:
                                  responses:
                                    '200': { description: OK }
                            """);
            case "gatling_import_har" -> with(target("3.9.5", "JAVA", "MAVEN", "HTTP"),
                    "document", "{\"log\":{\"version\":\"1.2\",\"entries\":[]}}");
            case "gatling_import_curl" -> with(target("3.9.5", "JAVA", "MAVEN", "HTTP"),
                    "curl", "curl 'https://api.example.test/api/orders' -H 'Accept: application/json'");
            case "gatling_import_postman_collection" -> with(target("3.9.5", "JAVA", "MAVEN", "HTTP"),
                    "document", """
                            {"info":{"schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},"item":[]}
                            """);
            case "gatling_extract_endpoints_from_codebase" -> Map.of(
                    "path", "/workspace",
                    "languageHint", "JAVA",
                    "maxFiles", 500
            );
            case "gatling_analyze_report" -> Map.of(
                    "reportContent", "var stats = {\"type\":\"GROUP\",\"name\":\"Global Information\",\"stats\":{}};",
                    "contentType", "STATS_JS"
            );
            case "gatling_analyze_log" -> Map.of(
                    "logText", "REQUEST\t\tGET /api/orders\t1000\t1700\tKO\tstatus 500",
                    "logType", "SIMULATION_LOG"
            );
            case "gatling_explain_errors" -> Map.of(
                    "errorText", "ReadTimeoutException while calling GET /api/orders, then status.find.is(200), but actually found 500"
            );
            case "gatling_suggest_load_model" -> Map.of(
                    "targetRps", 50,
                    "durationSeconds", 300,
                    "rampSeconds", 60,
                    "p95Ms", 250
            );
            case "gatling_check_feeder_risk" -> Map.of(
                    "plan", planExample(),
                    "sampleCsv", "username,password\nu1,p1\n",
                    "expectedUsers", 100
            );
            case "gatling_check_correlation_risk" -> Map.of(
                    "plan", planExample(),
                    "code", "http(\"orders\").get(\"/api/orders\").header(\"Authorization\", \"Bearer #{jwtToken}\")"
            );
            case "gatling_check_assertion_quality" -> Map.of(
                    "plan", planExample()
            );
            default -> Map.of();
        };
    }

    private static Map<String, Object> target(String gatlingVersion,
                                              String language,
                                              String buildTool,
                                              String protocol) {
        return Map.of(
                "gatlingVersion", gatlingVersion,
                "language", language,
                "buildTool", buildTool,
                "protocol", protocol
        );
    }

    private static Map<String, Object> with(Map<String, Object> base, String key, Object value) {
        var values = new LinkedHashMap<String, Object>(base);
        values.put(key, value);
        return Map.copyOf(values);
    }

    private static Map<String, Object> planExample() {
        return Map.of(
                "simulationClassName", "OrdersSimulation",
                "scenarioName", "Orders API",
                "baseUrl", "https://api.example.test",
                "requests", List.of(Map.of(
                        "name", "GET /api/orders",
                        "method", "GET",
                        "path", "/api/orders",
                        "checks", List.of(Map.of(
                                "type", "status",
                                "operator", "is",
                                "expected", "200"
                        ))
                )),
                "injectionProfile", Map.of(
                        "type", "rampAndConstant",
                        "rampFromUsersPerSec", 1,
                        "rampToUsersPerSec", 5,
                        "rampDurationSeconds", 30,
                        "constantUsersPerSec", 5,
                        "constantDurationSeconds", 60
                ),
                "assertions", List.of(Map.of(
                        "metric", "global.failedRequests.percent",
                        "operator", "lt",
                        "value", 1.0
                ))
        );
    }
}
