package io.github.gatlingcommunity.mcp.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ToolSchemas {
    private ToolSchemas() {
    }

    public static Map<String, Object> resolveCapabilitiesSchema() {
        return object(List.of("gatlingVersion", "language", "buildTool", "protocol"), Map.of(
                "gatlingVersion", string("Target Gatling version, for example 3.9.5 or 3.15"),
                "language", enumValues("Target DSL", "JAVA", "KOTLIN", "SCALA", "JAVASCRIPT", "TYPESCRIPT"),
                "buildTool", enumValues("Build tool", "MAVEN", "GRADLE", "SBT", "NPM"),
                "protocol", protocol(),
                "javaVersion", string("Optional Java runtime version"),
                "nodeVersion", string("Optional Node.js version"),
                "plugin", string("Optional verified community plugin"),
                "pluginVersion", string("Optional exact plugin version; when omitted the server selects a verified release for the target Gatling line"),
                "confidenceLevel", string("Optional plugin source confidence level")
        ));
    }

    public static Map<String, Object> interactionStatusSchema() {
        return emptyObjectSchema();
    }

    public static Map<String, Object> detectProjectSchema() {
        return object(List.of("path"), Map.of(
                "path", string("Project root path visible to the MCP server")
        ));
    }

    public static Map<String, Object> projectContextSchema() {
        return object(List.of("path"), Map.of(
                "path", string("Project root path inside a configured MCP workspace root")
        ));
    }

    public static Map<String, Object> effectiveContextSchema() {
        return object(List.of("path"), Map.of(
                "path", string("Project root path inside a configured MCP workspace root"),
                "protocol", protocol(),
                "gatlingVersion", string("Optional explicit Gatling version override"),
                "language", enumValues("Optional explicit target DSL override", "JAVA", "KOTLIN", "SCALA", "JAVASCRIPT", "TYPESCRIPT"),
                "buildTool", enumValues("Optional explicit build tool override", "MAVEN", "GRADLE", "SBT", "NPM"),
                "javaVersion", string("Optional explicit Java runtime version override"),
                "nodeVersion", string("Optional explicit Node.js version override"),
                "plugin", string("Optional verified community plugin"),
                "pluginVersion", string("Optional exact plugin version; when omitted the server selects a verified release for the target Gatling line"),
                "confidenceLevel", string("Optional plugin source confidence level")
        ));
    }

    public static Map<String, Object> generateSimulationSchema() {
        return object(List.of("gatlingVersion", "language", "buildTool", "protocol", "simulationClassName"), Map.of(
                "gatlingVersion", string("Target Gatling version"),
                "language", enumValues("Target DSL", "JAVA", "KOTLIN", "SCALA", "JAVASCRIPT", "TYPESCRIPT"),
                "buildTool", enumValues("Build tool", "MAVEN", "GRADLE", "SBT", "NPM"),
                "protocol", protocol(),
                "simulationClassName", string("Generated simulation class name"),
                "javaVersion", string("Optional Java runtime version"),
                "nodeVersion", string("Optional Node.js version"),
                "plugin", string("Optional verified community plugin"),
                "pluginVersion", string("Optional exact plugin version; when omitted the server selects a verified release for the target Gatling line"),
                "confidenceLevel", string("Optional plugin source confidence level")
        ));
    }

    public static Map<String, Object> analyzeSimulationSchema() {
        return object(List.of("gatlingVersion", "language", "buildTool", "protocol", "code"), targetWithCode());
    }

    public static Map<String, Object> validateFeatureUsageSchema() {
        return object(List.of("gatlingVersion", "language", "buildTool", "protocol", "code"), targetWithCode());
    }

    public static Map<String, Object> listDslMethodsSchema() {
        return object(List.of("gatlingVersion", "language", "protocol"), Map.of(
                "gatlingVersion", string("Target Gatling version"),
                "language", enumValues("Target DSL", "JAVA", "KOTLIN", "SCALA", "JAVASCRIPT", "TYPESCRIPT"),
                "protocol", protocol(),
                "category", string("Optional DSL method category filter"),
                "buildTool", enumValues("Optional build tool", "MAVEN", "GRADLE", "SBT", "NPM"),
                "javaVersion", string("Optional Java runtime version"),
                "nodeVersion", string("Optional Node.js version")
        ));
    }

    public static Map<String, Object> validateMethodChainSchema() {
        return object(List.of("gatlingVersion", "language", "protocol", "methods"), Map.of(
                "gatlingVersion", string("Target Gatling version"),
                "language", enumValues("Target DSL", "JAVA", "KOTLIN", "SCALA", "JAVASCRIPT", "TYPESCRIPT"),
                "protocol", protocol(),
                "methods", stringArray("Ordered Gatling DSL method names, for example scenario, exec, http, get, check, status.is"),
                "buildTool", enumValues("Optional build tool", "MAVEN", "GRADLE", "SBT", "NPM"),
                "javaVersion", string("Optional Java runtime version"),
                "nodeVersion", string("Optional Node.js version")
        ));
    }

    public static Map<String, Object> explainDslMethodSchema() {
        return object(List.of("gatlingVersion", "language", "protocol", "method"), Map.of(
                "gatlingVersion", string("Target Gatling version"),
                "language", enumValues("Target DSL", "JAVA", "KOTLIN", "SCALA", "JAVASCRIPT", "TYPESCRIPT"),
                "protocol", protocol(),
                "method", string("Gatling DSL method name to explain, for example jsonPath.saveAs"),
                "buildTool", enumValues("Optional build tool", "MAVEN", "GRADLE", "SBT", "NPM"),
                "javaVersion", string("Optional Java runtime version"),
                "nodeVersion", string("Optional Node.js version")
        ));
    }

    public static Map<String, Object> findReplacementMethodSchema() {
        return object(List.of("gatlingVersion", "language", "protocol", "method"), Map.of(
                "gatlingVersion", string("Target Gatling version"),
                "language", enumValues("Target DSL", "JAVA", "KOTLIN", "SCALA", "JAVASCRIPT", "TYPESCRIPT"),
                "protocol", protocol(),
                "method", string("Deprecated or unavailable Gatling DSL method name"),
                "buildTool", enumValues("Optional build tool", "MAVEN", "GRADLE", "SBT", "NPM"),
                "javaVersion", string("Optional Java runtime version"),
                "nodeVersion", string("Optional Node.js version")
        ));
    }

    public static Map<String, Object> explainVersionConstraintsSchema() {
        return object(List.of("gatlingVersion", "language", "protocol"), Map.of(
                "gatlingVersion", string("Target Gatling version"),
                "language", enumValues("Target DSL", "JAVA", "KOTLIN", "SCALA", "JAVASCRIPT", "TYPESCRIPT"),
                "protocol", protocol(),
                "buildTool", enumValues("Optional build tool", "MAVEN", "GRADLE", "SBT", "NPM"),
                "javaVersion", string("Optional Java runtime version"),
                "nodeVersion", string("Optional Node.js version")
        ));
    }

    public static Map<String, Object> planSimulationSchema() {
        var properties = new LinkedHashMap<String, Object>();
        properties.put("gatlingVersion", string("Target Gatling version"));
        properties.put("language", enumValues("Target DSL", "JAVA", "KOTLIN", "SCALA", "JAVASCRIPT", "TYPESCRIPT"));
        properties.put("buildTool", enumValues("Build tool", "MAVEN", "GRADLE", "SBT", "NPM"));
        properties.put("protocol", protocol());
        properties.put("goal", string("User intent for the simulation flow"));
        properties.put("simulationClassName", string("Optional simulation class name"));
        properties.put("baseUrl", string("Optional HTTP base URL"));
        properties.put("useElicitation", bool("Ask the MCP client for missing authoring details when supported"));
        properties.put("useSampling", bool("Ask the MCP client model for an advisory review when supported"));
        properties.put("javaVersion", string("Optional Java runtime version"));
        properties.put("nodeVersion", string("Optional Node.js version"));
        return object(List.of("gatlingVersion", "language", "buildTool", "protocol", "goal"), Map.copyOf(properties));
    }

    public static Map<String, Object> intentOnlySchema() {
        return object(List.of(), Map.of(
                "goal", string("User intent for the flow")
        ));
    }

    public static Map<String, Object> emptyObjectSchema() {
        return object(List.of(), Map.of());
    }

    public static Map<String, Object> simulationPlanSchema() {
        return object(List.of("gatlingVersion", "language", "buildTool", "protocol", "plan"), Map.of(
                "gatlingVersion", string("Target Gatling version"),
                "language", enumValues("Target DSL", "JAVA", "KOTLIN", "SCALA", "JAVASCRIPT", "TYPESCRIPT"),
                "buildTool", enumValues("Build tool", "MAVEN", "GRADLE", "SBT", "NPM"),
                "protocol", protocol(),
                "plan", httpSimulationPlanSchema(),
                "javaVersion", string("Optional Java runtime version"),
                "nodeVersion", string("Optional Node.js version")
        ));
    }

    public static Map<String, Object> patchGenerationSchema() {
        var properties = new LinkedHashMap<String, Object>();
        properties.putAll(simulationPlanSchemaProperties());
        properties.put("targetPath", string("Workspace-relative target file path used only in the returned unified diff"));
        return object(List.of("gatlingVersion", "language", "buildTool", "protocol", "plan"), Map.copyOf(properties));
    }

    public static Map<String, Object> generatedCodeValidationSchema() {
        return object(List.of("gatlingVersion", "language", "buildTool", "protocol", "code"), targetWithCode());
    }

    public static Map<String, Object> importDocumentSchema(String documentDescription) {
        return object(List.of("gatlingVersion", "language", "buildTool", "protocol", "document"),
                importBaseProperties("document", documentDescription));
    }

    private static Map<String, Object> simulationPlanSchemaProperties() {
        return Map.of(
                "gatlingVersion", string("Target Gatling version"),
                "language", enumValues("Target DSL", "JAVA", "KOTLIN", "SCALA", "JAVASCRIPT", "TYPESCRIPT"),
                "buildTool", enumValues("Build tool", "MAVEN", "GRADLE", "SBT", "NPM"),
                "protocol", protocol(),
                "plan", httpSimulationPlanSchema(),
                "javaVersion", string("Optional Java runtime version"),
                "nodeVersion", string("Optional Node.js version")
        );
    }

    public static Map<String, Object> importCurlSchema() {
        return object(List.of("gatlingVersion", "language", "buildTool", "protocol", "curl"),
                importBaseProperties("curl", "curl command text"));
    }

    public static Map<String, Object> extractCodebaseEndpointsSchema() {
        return object(List.of("path"), Map.of(
                "path", string("Codebase root path inside a configured MCP workspace root"),
                "languageHint", enumValues("Optional primary source language hint", "JAVA", "KOTLIN", "SCALA", "JAVASCRIPT", "TYPESCRIPT"),
                "maxFiles", integer("Maximum source files to inspect, default 500, hard-capped at 2000")
        ));
    }

    public static Map<String, Object> analyzeReportSchema() {
        return object(List.of(), Map.of(
                "reportPath", string("Optional path to a Gatling report directory, stats.js, or stats.json visible to the MCP server"),
                "reportContent", string("Optional Gatling stats.js or stats.json content"),
                "contentType", enumValues("Report content type", "AUTO", "STATS_JS", "STATS_JSON"),
                "errorRateThreshold", number("Error-rate threshold in percent, default 1.0"),
                "p95ThresholdMs", integer("p95 response-time threshold in milliseconds, default 1000"),
                "p99ThresholdMs", integer("p99 response-time threshold in milliseconds, default 2000"),
                "useSampling", bool("Ask the MCP client model for an advisory review when supported")
        ));
    }

    public static Map<String, Object> analyzeLogSchema() {
        return object(List.of(), Map.of(
                "logPath", string("Optional path to a Gatling runtime log or simulation.log visible to the MCP server"),
                "logText", string("Optional Gatling runtime log or simulation.log text"),
                "logType", enumValues("Log type", "AUTO", "SIMULATION_LOG", "RUNTIME_LOG"),
                "errorRateThreshold", number("Error-rate threshold in percent, default 1.0"),
                "p95ThresholdMs", integer("p95 response-time threshold in milliseconds, default 1000"),
                "p99ThresholdMs", integer("p99 response-time threshold in milliseconds, default 2000"),
                "useSampling", bool("Ask the MCP client model for an advisory review when supported")
        ));
    }

    public static Map<String, Object> explainErrorsSchema() {
        return object(List.of(), Map.of(
                "errorText", string("Raw Gatling error text, failed check text, stack trace, or excerpt"),
                "logText", string("Optional Gatling runtime log excerpt"),
                "reportContent", string("Optional Gatling report or stats excerpt")
        ));
    }

    public static Map<String, Object> suggestLoadModelSchema() {
        return object(List.of(), Map.of(
                "targetRps", number("Target requests per second"),
                "expectedUsers", integer("Expected active or business users"),
                "durationSeconds", integer("Total test duration in seconds"),
                "rampSeconds", integer("Ramp duration in seconds"),
                "p95Ms", integer("Expected or measured p95 response time in milliseconds"),
                "workloadType", enumValues("Workload model preference", "open", "closed")
        ));
    }

    public static Map<String, Object> feederRiskSchema() {
        return object(List.of(), Map.of(
                "plan", httpSimulationPlanSchema(),
                "sampleCsv", string("Optional feeder CSV sample including header row"),
                "expectedUsers", integer("Expected users or iterations that may consume feeder rows"),
                "durationSeconds", integer("Expected test duration in seconds")
        ));
    }

    public static Map<String, Object> correlationRiskSchema() {
        return object(List.of(), Map.of(
                "plan", httpSimulationPlanSchema(),
                "code", string("Optional Gatling simulation code to inspect for #{session} references and saveAs calls")
        ));
    }

    public static Map<String, Object> assertionQualitySchema() {
        return object(List.of(), Map.of(
                "plan", httpSimulationPlanSchema(),
                "code", string("Optional Gatling simulation code to inspect for checks and assertions")
        ));
    }

    public static Map<String, Object> compileCheckSchema() {
        return object(List.of("path"), Map.of(
                "path", string("Project root path inside a configured MCP workspace root"),
                "buildTool", enumValues("Optional build tool override; auto-detected when omitted", "MAVEN", "GRADLE", "SBT", "NPM"),
                "timeoutSeconds", integer("Compile process timeout in seconds, default 120, maximum 300. "
                        + "The service reserves 10 seconds for isolation preparation and 5 seconds for cleanup; "
                        + "the HTTP bridge separately reserves 3 seconds for transport and JSON serialization."),
                "execute", bool("When false, only return the whitelisted compile command without executing it")
        ));
    }

    private static Map<String, Object> httpSimulationPlanSchema() {
        return Map.of(
                "type", "object",
                "description", "Structured HTTP simulation plan",
                "additionalProperties", false,
                "properties", Map.ofEntries(
                        Map.entry("simulationClassName", string("Simulation class name")),
                        Map.entry("scenarioName", string("Scenario name")),
                        Map.entry("baseUrl", string("HTTP base URL")),
                        Map.entry("requests", arraySchema("Backward-compatible flat HTTP request list", httpRequestSchema())),
                        Map.entry("steps", arraySchema("First-class scenario steps: feed, request, pause, loop, group, conditional", scenarioStepSchema())),
                        Map.entry("feeders", arraySchema("Gatling feeder definitions", feederSchema())),
                        Map.entry("protocolOptions", protocolOptionsSchema()),
                        Map.entry("injectionProfile", injectionProfileSchema()),
                        Map.entry("assertions", arraySchema("Gatling assertions", assertionSchema()))
                )
        );
    }

    private static Map<String, Object> httpRequestSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.ofEntries(
                        Map.entry("name", string("Request name")),
                        Map.entry("method", string("HTTP method")),
                        Map.entry("path", string("Request path without query string when queryParams is used")),
                        Map.entry("headers", stringMapSchema("HTTP request headers")),
                        Map.entry("queryParams", stringMapSchema("HTTP query parameters")),
                        Map.entry("formParams", stringMapSchema("application/x-www-form-urlencoded parameters")),
                        Map.entry("multipartParts", arraySchema("Multipart request parts", multipartPartSchema())),
                        Map.entry("body", string("Request body")),
                        Map.entry("resources", arraySchema("Secondary HTTP resources for this request", resourceSchema())),
                        Map.entry("auth", authSchema()),
                        Map.entry("cookies", arraySchema("Request cookies", cookieSchema())),
                        Map.entry("options", requestOptionsSchema()),
                        Map.entry("checks", arraySchema("Request checks", checkSchema()))
                )
        );
    }

    private static Map<String, Object> scenarioStepSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.ofEntries(
                        Map.entry("type", enumValues("Scenario step type", "feed", "request", "pause", "repeat", "during", "forever", "group", "ifEquals", "doIf")),
                        Map.entry("feederName", string("Feeder name for feed steps")),
                        Map.entry("request", httpRequestSchema()),
                        Map.entry("pause", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "properties", Map.of("durationSeconds", integer("Pause duration in seconds"))
                        )),
                        Map.entry("loop", loopSchema()),
                        Map.entry("group", groupSchema()),
                        Map.entry("conditional", conditionalSchema())
                )
        );
    }

    private static Map<String, Object> feederSchema() {
        return object(List.of(), Map.of(
                "name", string("Feeder name"),
                "type", enumValues("Feeder type", "csv", "json", "tsv", "ssv"),
                "source", string("Feeder source path or resource"),
                "strategy", enumValues("Feeder strategy", "queue", "random", "shuffle", "circular"),
                "columns", stringArray("Expected feeder columns")
        ));
    }

    private static Map<String, Object> protocolOptionsSchema() {
        return object(List.of(), Map.of(
                "headers", stringMapSchema("Protocol-level headers"),
                "followRedirects", bool("Whether redirects are followed at protocol level"),
                "http2", bool("Whether HTTP/2 is enabled when supported by the DSL/runtime"),
                "proxyHost", string("Optional proxy host"),
                "proxyPort", integer("Optional proxy port")
        ));
    }

    private static Map<String, Object> loopSchema() {
        return object(List.of(), Map.of(
                "type", enumValues("Loop type", "repeat", "during", "forever"),
                "count", integer("Repeat count"),
                "durationSeconds", integer("Loop duration in seconds for during"),
                "steps", arraySchema("Nested loop steps", shallowStepSchema())
        ));
    }

    private static Map<String, Object> groupSchema() {
        return object(List.of(), Map.of(
                "name", string("Gatling group name"),
                "steps", arraySchema("Nested group steps", shallowStepSchema())
        ));
    }

    private static Map<String, Object> conditionalSchema() {
        return object(List.of(), Map.of(
                "expression", string("Raw DSL condition expression when known"),
                "left", string("Left session expression"),
                "operator", enumValues("Conditional operator", "equals", "exists", "matches"),
                "right", string("Right expected value"),
                "steps", arraySchema("Nested conditional steps", shallowStepSchema())
        ));
    }

    private static Map<String, Object> shallowStepSchema() {
        return object(List.of(), Map.of(
                "type", string("Nested step type"),
                "request", httpRequestSchema(),
                "pause", object(List.of(), Map.of("durationSeconds", integer("Pause duration in seconds")))
        ));
    }

    private static Map<String, Object> multipartPartSchema() {
        return object(List.of(), Map.of(
                "name", string("Part name"),
                "value", string("Inline part value"),
                "fileName", string("Uploaded file name"),
                "contentType", string("Part content type"),
                "filePath", string("Workspace-relative file path for file parts")
        ));
    }

    private static Map<String, Object> resourceSchema() {
        return object(List.of(), Map.of(
                "name", string("Resource request name"),
                "method", string("Resource HTTP method"),
                "path", string("Resource path"),
                "headers", stringMapSchema("Resource headers")
        ));
    }

    private static Map<String, Object> authSchema() {
        return object(List.of(), Map.of(
                "type", enumValues("Request authentication type", "", "basic", "bearer", "apiKey"),
                "username", string("Basic auth username expression"),
                "password", string("Basic auth password expression"),
                "token", string("Bearer token expression"),
                "headerName", string("Auth header name")
        ));
    }

    private static Map<String, Object> cookieSchema() {
        return object(List.of(), Map.of(
                "name", string("Cookie name"),
                "value", string("Cookie value"),
                "domain", string("Cookie domain"),
                "path", string("Cookie path")
        ));
    }

    private static Map<String, Object> requestOptionsSchema() {
        return object(List.of(), Map.of(
                "followRedirects", bool("Whether this request follows redirects"),
                "silent", bool("Whether this request is marked silent")
        ));
    }

    private static Map<String, Object> checkSchema() {
        return object(List.of(), Map.of(
                "type", string("Check type"),
                "expression", string("Check expression"),
                "operator", string("Check operator"),
                "expected", string("Expected value"),
                "saveAs", string("Session variable name")
        ));
    }

    private static Map<String, Object> injectionProfileSchema() {
        return object(List.of(), Map.of(
                "type", string("Injection profile type"),
                "rampFromUsersPerSec", integer("Initial users per second"),
                "rampToUsersPerSec", integer("Target users per second"),
                "rampDurationSeconds", integer("Ramp duration in seconds"),
                "constantUsersPerSec", integer("Constant users per second"),
                "constantDurationSeconds", integer("Constant duration in seconds")
        ));
    }

    private static Map<String, Object> assertionSchema() {
        return object(List.of(), Map.of(
                "metric", string("Assertion metric"),
                "operator", string("Assertion operator"),
                "value", number("Assertion value")
        ));
    }

    private static Map<String, Object> arraySchema(String description, Map<String, Object> itemSchema) {
        return Map.of(
                "type", "array",
                "description", description,
                "items", itemSchema
        );
    }

    private static Map<String, Object> stringMapSchema(String description) {
        return Map.of(
                "type", "object",
                "description", description,
                "additionalProperties", Map.of("type", "string")
        );
    }

    private static Map<String, Object> targetWithCode() {
        return Map.of(
                "gatlingVersion", string("Target Gatling version"),
                "language", enumValues("Target DSL", "JAVA", "KOTLIN", "SCALA", "JAVASCRIPT", "TYPESCRIPT"),
                "buildTool", enumValues("Build tool", "MAVEN", "GRADLE", "SBT", "NPM"),
                "protocol", protocol(),
                "code", string("Simulation source code"),
                "javaVersion", string("Optional Java runtime version"),
                "nodeVersion", string("Optional Node.js version")
        );
    }

    private static Map<String, Object> importBaseProperties(String sourceProperty, String sourceDescription) {
        var properties = new LinkedHashMap<String, Object>();
        properties.put("gatlingVersion", string("Target Gatling version"));
        properties.put("language", enumValues("Target DSL", "JAVA", "KOTLIN", "SCALA", "JAVASCRIPT", "TYPESCRIPT"));
        properties.put("buildTool", enumValues("Build tool", "MAVEN", "GRADLE", "SBT", "NPM"));
        properties.put("protocol", enumValues("Protocol; imports support HTTP in v1", "HTTP"));
        properties.put(sourceProperty, string(sourceDescription));
        properties.put("simulationClassName", string("Optional simulation class name for the imported plan"));
        properties.put("scenarioName", string("Optional scenario name for the imported plan"));
        properties.put("baseUrl", string("Optional base URL override; otherwise the importer derives it from the source"));
        properties.put("javaVersion", string("Optional Java runtime version"));
        properties.put("nodeVersion", string("Optional Node.js version"));
        return Map.copyOf(properties);
    }

    private static Map<String, Object> object(List<String> required, Map<String, Object> properties) {
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("required", required);
        schema.put("properties", properties);
        schema.put("additionalProperties", false);
        return Map.copyOf(schema);
    }

    private static Map<String, Object> string(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> number(String description) {
        return Map.of("type", "number", "description", description);
    }

    private static Map<String, Object> integer(String description) {
        return Map.of("type", "integer", "description", description);
    }

    private static Map<String, Object> bool(String description) {
        return Map.of("type", "boolean", "description", description);
    }

    private static Map<String, Object> stringArray(String description) {
        return Map.of(
                "type", "array",
                "description", description,
                "items", Map.of("type", "string")
        );
    }

    private static Map<String, Object> protocol() {
        return enumValues("Protocol", "HTTP", "WEBSOCKET", "SSE", "JMS", "MQTT", "GRPC", "KAFKA", "JDBC", "AMQP", "PICATINNY");
    }

    private static Map<String, Object> enumValues(String description, String... values) {
        return Map.of(
                "type", "string",
                "description", description,
                "enum", List.of(values)
        );
    }
}
