package io.github.gatlingcommunity.mcp.mcp.schema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.github.gatlingcommunity.mcp.mcp.schema.JsonSchemaBuilder.array;
import static io.github.gatlingcommunity.mcp.mcp.schema.JsonSchemaBuilder.bool;
import static io.github.gatlingcommunity.mcp.mcp.schema.JsonSchemaBuilder.integer;
import static io.github.gatlingcommunity.mcp.mcp.schema.JsonSchemaBuilder.number;
import static io.github.gatlingcommunity.mcp.mcp.schema.JsonSchemaBuilder.object;
import static io.github.gatlingcommunity.mcp.mcp.schema.JsonSchemaBuilder.objectWithAdditionalProperties;
import static io.github.gatlingcommunity.mcp.mcp.schema.JsonSchemaBuilder.string;
import static io.github.gatlingcommunity.mcp.mcp.schema.JsonSchemaBuilder.stringArray;

public final class ToolOutputSchemas {
    private static final List<String> ERROR_REQUIRED_FIELDS = List.of(
            "code", "severity", "path", "message", "suggestion");
    private static final Set<String> OPTIONAL_SUCCESS_FIELDS = Set.of("error");

    private ToolOutputSchemas() {
    }

    public static Map<String, Object> interactionStatus() {
        return output(Map.of(
                "clientName", string("MCP client name reported to the server"),
                "clientVersion", string("MCP client version reported to the server"),
                "elicitation", bool("Whether the client supports elicitation requests"),
                "sampling", bool("Whether the client supports sampling requests"),
                "progress", bool("Whether the client supports progress notifications"),
                "progressTokenPresent", bool("Whether progress can be correlated with a progress token")
        ));
    }

    public static Map<String, Object> capabilities() {
        return output(Map.ofEntries(
                Map.entry("target", targetContext()),
                Map.entry("supported", bool("Whether the requested target is supported")),
                Map.entry("generationMode", string("Generation depth such as deep, deep-plugin, or capability-only")),
                Map.entry("features", stringArray("Supported feature identifiers")),
                Map.entry("warnings", array("Compatibility warnings", warningMessage())),
                Map.entry("metadata", capabilityMetadata())
        ));
    }

    public static Map<String, Object> detectedProject() {
        return output(Map.of(
                "buildTool", string("Detected build tool"),
                "language", string("Detected Gatling DSL language"),
                "gatlingVersion", string("Detected Gatling version or unknown"),
                "confidence", string("Detection confidence")
        ));
    }

    public static Map<String, Object> projectContext() {
        return output(projectContextProperties());
    }

    public static Map<String, Object> effectiveContext() {
        return output(Map.ofEntries(
                Map.entry("root", string("Resolved project root")),
                Map.entry("gatlingVersion", string("Effective Gatling version")),
                Map.entry("language", string("Effective Gatling DSL language")),
                Map.entry("buildTool", string("Effective build tool")),
                Map.entry("protocol", string("Effective protocol")),
                Map.entry("javaVersion", string("Effective Java version")),
                Map.entry("nodeVersion", string("Effective Node.js version")),
                Map.entry("plugin", string("Selected community plugin or empty string")),
                Map.entry("sources", object(List.of(), Map.of(
                        "gatlingVersion", string("Source of the Gatling version value"),
                        "language", string("Source of the language value"),
                        "buildTool", string("Source of the build-tool value"),
                        "protocol", string("Source of the protocol value"),
                        "javaVersion", string("Source of the Java version value"),
                        "nodeVersion", string("Source of the Node.js version value")
                ))),
                Map.entry("warnings", stringArray("Effective-context warnings")),
                Map.entry("projectContext", object(List.of(), projectContextProperties()))
        ));
    }

    public static Map<String, Object> generatedSimulation() {
        return output(Map.ofEntries(
                Map.entry("target", targetContext()),
                Map.entry("code", string("Generated simulation source; empty for unsupported or capability-only targets")),
                Map.entry("generationMode", string("Resolved generation mode")),
                Map.entry("warnings", array("Generation and compatibility warnings", warningMessage())),
                Map.entry("plugin", pluginIdentity())
        ));
    }

    public static Map<String, Object> simulationAnalysis() {
        return output(Map.ofEntries(
                Map.entry("target", targetContext()),
                Map.entry("scenarioNames", stringArray("Scenario names extracted from source")),
                Map.entry("protocolConfigurations", stringArray("Protocol configuration expressions extracted from source")),
                Map.entry("requestNames", stringArray("Request names extracted from source")),
                Map.entry("checks", stringArray("Checks extracted from source")),
                Map.entry("feeders", stringArray("Feeder declarations and feed steps extracted from source")),
                Map.entry("correlations", stringArray("Session variables captured through saveAs")),
                Map.entry("injectionProfiles", stringArray("Injection profile expressions extracted from source")),
                Map.entry("assertions", stringArray("Simulation assertion expressions extracted from source")),
                Map.entry("validationFindings", array("Version, layout, and security findings", validationFinding()))
        ));
    }

    public static Map<String, Object> featureUsageValidation() {
        return output(Map.of(
                "target", targetContext(),
                "valid", bool("Whether no error-severity finding was detected"),
                "findingCount", integer("Number of feature-usage findings"),
                "findings", array("Feature-usage findings", validationFinding())
        ));
    }

    public static Map<String, Object> dslMethods() {
        return output(Map.of(
                "gatlingVersion", string("Requested Gatling version"),
                "matchedGatlingLine", string("Matched compatibility line"),
                "language", string("Requested DSL language"),
                "protocol", string("Requested protocol"),
                "category", string("Applied category filter or ALL"),
                "count", integer("Number of returned methods"),
                "methods", array("Matching DSL methods", dslMethod())
        ));
    }

    public static Map<String, Object> methodChainValidation() {
        return output(Map.ofEntries(
                Map.entry("valid", bool("Whether the method chain passed semantic validation")),
                Map.entry("gatlingVersion", string("Requested Gatling version")),
                Map.entry("matchedGatlingLine", string("Matched compatibility line")),
                Map.entry("language", string("Requested DSL language")),
                Map.entry("protocol", string("Requested protocol")),
                Map.entry("methodCount", integer("Number of methods in the checked chain")),
                Map.entry("findings", array("Method-chain validation findings", finding())),
                Map.entry("transitions", array("Per-method transition summaries", transition())),
                Map.entry("methodRequirements", array("Semantic requirements for known methods", methodSemantic()))
        ));
    }

    public static Map<String, Object> dslMethodExplanation() {
        return output(Map.ofEntries(
                Map.entry("found", bool("Whether the method was found")),
                Map.entry("gatlingVersion", string("Requested Gatling version")),
                Map.entry("matchedGatlingLine", string("Matched compatibility line")),
                Map.entry("language", string("Requested DSL language")),
                Map.entry("protocol", string("Requested protocol")),
                Map.entry("methodName", string("Requested method name")),
                Map.entry("method", dslMethod()),
                Map.entry("semantic", methodSemantic()),
                Map.entry("replacements", array("Replacement candidates", replacement()))
        ));
    }

    public static Map<String, Object> replacementMethods() {
        return output(Map.of(
                "found", bool("Whether replacement candidates were found"),
                "gatlingVersion", string("Requested Gatling version"),
                "matchedGatlingLine", string("Matched compatibility line"),
                "language", string("Requested DSL language"),
                "protocol", string("Requested protocol"),
                "methodName", string("Deprecated or unavailable method name"),
                "replacements", array("Replacement candidates", replacement())
        ));
    }

    public static Map<String, Object> versionConstraints() {
        return output(Map.ofEntries(
                Map.entry("gatlingVersion", string("Requested Gatling version")),
                Map.entry("matchedGatlingLine", string("Matched compatibility line")),
                Map.entry("language", string("Requested DSL language")),
                Map.entry("buildTool", string("Requested build tool")),
                Map.entry("protocol", string("Requested protocol")),
                Map.entry("features", array("Feature gates", featureGate())),
                Map.entry("breakingChanges", array("Breaking changes and replacements", replacement())),
                Map.entry("dslMethodCount", integer("Number of matching DSL methods")),
                Map.entry("dslMethodCategories", stringArray("Method categories present for this target"))
        ));
    }

    public static Map<String, Object> simulationPlan(boolean includeInteraction) {
        var properties = new LinkedHashMap<>(planResponseProperties());
        if (includeInteraction) {
            properties.put("interaction", interactionSummary());
        }
        return output(properties);
    }

    public static Map<String, Object> correlations() {
        return output(Map.of(
                "correlations", array("Planned correlation checks", checkPlan())
        ));
    }

    public static Map<String, Object> checks() {
        return output(Map.of(
                "checks", array("Planned checks", checkPlan())
        ));
    }

    public static Map<String, Object> injectionProfile() {
        return output(Map.of(
                "injectionProfile", injectionProfileSchema()
        ));
    }

    public static Map<String, Object> assertions() {
        return output(Map.of(
                "assertions", array("Planned assertions", assertionSchema())
        ));
    }

    public static Map<String, Object> generatedFromPlan() {
        return output(Map.of(
                "valid", bool("Whether the input plan was valid"),
                "matchedGatlingLine", string("Matched compatibility line"),
                "language", string("Generated DSL language"),
                "methodRequirements", array("DSL methods required by the generated plan", planMethodRequirement())
        ));
    }

    public static Map<String, Object> generationDecisions() {
        return output(Map.of(
                "decisionCount", integer("Number of generation decisions"),
                "decisions", array("Generation decisions with assumptions and verification", generationDecision())
        ));
    }

    public static Map<String, Object> generatedPatch() {
        return output(Map.of(
                "targetPath", string("Target path represented in the diff"),
                "unifiedDiff", string("Unified diff that the caller may apply manually"),
                "writesFiles", bool("Whether the tool wrote files to disk"),
                "warnings", stringArray("Patch-generation warnings")
        ));
    }

    public static Map<String, Object> generatedCodeValidation() {
        return output(Map.of(
                "valid", bool("Whether generated code passed static validation"),
                "language", string("Validated DSL language"),
                "findings", array("Generated-code validation findings", finding()),
                "compileCheck", object(List.of(), Map.of(
                        "executed", bool("Whether compile check was executed"),
                        "status", string("Compile-check status")
                )),
                "compileReadiness", object(List.of(), Map.of(
                        "status", string("Static compile-readiness status"),
                        "canRunCompileCheck", bool("Whether compile check should be attempted"),
                        "reason", string("Reason for compile-readiness status")
                )),
                "recommendedCompileCommand", stringArray("Recommended compile-only command tokens")
        ));
    }

    public static Map<String, Object> compileCheck() {
        return output(Map.ofEntries(
                Map.entry("executed", bool("Whether a compile command was executed")),
                Map.entry("success", bool("Whether planning or execution succeeded")),
                Map.entry("status", string("Compile-check status")),
                Map.entry("buildTool", string("Selected build tool")),
                Map.entry("command", stringArray("Whitelisted command tokens")),
                Map.entry("commandText", string("Whitelisted command as text")),
                Map.entry("workingDirectory", string("Original workspace directory")),
                Map.entry("isolatedWorkingDirectory", string("Temporary isolated directory used for execution")),
                Map.entry("isolated", bool("Whether the check used an isolated project copy")),
                Map.entry("exitCode", integer("Process exit code, or -1 when unavailable")),
                Map.entry("durationMillis", integer("Compile-check duration in milliseconds")),
                Map.entry("timedOut", bool("Whether the command timed out")),
                Map.entry("outputTail", string("Masked compile output tail")),
                Map.entry("findings", array("Compile findings", finding())),
                Map.entry("warnings", array("Compile warnings", finding()))
        ));
    }

    public static Map<String, Object> importedPlan() {
        var properties = new LinkedHashMap<String, Object>();
        properties.put("sourceType", string("Import source type"));
        properties.put("sourceVersion", string("Detected source format version"));
        properties.put("requestCount", integer("Number of imported HTTP requests"));
        properties.put("endpointInventory", endpointInventory());
        properties.put("feederCandidates", stringArray(
                "Request path and request-body representation placeholders that can become feeder columns"));
        properties.put("correlationCandidates", stringArray("Response fields that likely need check/saveAs correlation"));
        properties.put("correlationUsages", stringArray("Detected later references to correlation candidates"));
        properties.put("warnings", array("Import warnings", importWarning()));
        properties.putAll(planResponseProperties());
        properties.put("error", string("Import error message when import failed"));
        return output(properties);
    }

    public static Map<String, Object> endpointExtraction() {
        return output(Map.of(
                "endpointInventory", endpointInventory(),
                "warnings", array("Codebase extraction warnings", importWarning()),
                "scannedFiles", integer("Number of candidate files inspected"),
                "matchedFiles", integer("Number of files that produced at least one endpoint")
        ));
    }

    public static Map<String, Object> reportAnalysis() {
        return output(Map.ofEntries(
                Map.entry("sourceType", string("Report or log source type")),
                Map.entry("requestCount", integer("Number of request-level stats entries")),
                Map.entry("globalStats", requestStats()),
                Map.entry("requestStats", array("Per-request stats", requestStats())),
                Map.entry("topSlowRequests", array("Slowest requests by p95", requestStats())),
                Map.entry("topFailedRequests", array("Requests with failures", requestStats())),
                Map.entry("findings", array("Report or log findings", finding())),
                Map.entry("warnings", array("Report or log warnings", importWarning())),
                Map.entry("interaction", interactionSummary())
        ));
    }

    public static Map<String, Object> troubleshooting() {
        return output(Map.ofEntries(
                Map.entry("findings", array("Focused troubleshooting findings", troubleshootingFinding())),
                Map.entry("rootCauseHypotheses", array("Root-cause hypotheses with evidence and verification", rootCauseHypothesis())),
                Map.entry("verificationSteps", stringArray("Concrete verification steps")),
                Map.entry("nextActions", stringArray("Recommended next actions")),
                Map.entry("confidence", string("Overall confidence level")),
                Map.entry("suggestedModel", suggestedLoadModel()),
                Map.entry("injectionProfile", troubleshootingInjectionProfile()),
                Map.entry("assumptions", stringArray("Assumptions made during analysis")),
                Map.entry("risks", stringArray("Residual risks or detected quality risks"))
        ));
    }

    private static Map<String, Object> output(Map<String, Object> properties) {
        var successRequiredFields = properties.keySet().stream()
                .filter(field -> !OPTIONAL_SUCCESS_FIELDS.contains(field))
                .toList();
        var merged = new LinkedHashMap<String, Object>(properties);
        merged.put("code", string("Error code when the tool returns isError=true"));
        merged.put("severity", string("Error severity when the tool returns isError=true"));
        merged.put("path", string("Input path or JSON pointer related to an error"));
        merged.put("message", string("Human-readable error message"));
        merged.put("suggestion", string("Suggested remediation for an error"));
        merged.put("details", objectWithAdditionalProperties("Machine-readable error details", string("Detail value")));
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", Map.copyOf(merged));
        schema.put("additionalProperties", false);
        schema.put("anyOf", List.of(
                Map.of("required", successRequiredFields),
                Map.of("required", ERROR_REQUIRED_FIELDS)
        ));
        return Map.copyOf(schema);
    }

    private static Map<String, Object> projectContextProperties() {
        return Map.ofEntries(
                Map.entry("root", string("Resolved project root")),
                Map.entry("buildTool", string("Detected build tool")),
                Map.entry("language", string("Detected Gatling DSL language")),
                Map.entry("detectedGatlingVersion", string("Detected Gatling version or unknown")),
                Map.entry("javaVersion", string("Detected Java version or unknown")),
                Map.entry("nodeVersion", string("Detected Node.js version or unknown")),
                Map.entry("sourceRoots", stringArray("Detected source roots")),
                Map.entry("testRoots", stringArray("Detected test roots")),
                Map.entry("existingSimulationFiles", stringArray("Existing simulation files")),
                Map.entry("dependencyState", dependencyState()),
                Map.entry("pluginState", pluginState()),
                Map.entry("packageNaming", packageNaming()),
                Map.entry("styleConventions", stringArray("Detected style conventions")),
                Map.entry("warnings", stringArray("Project-context warnings"))
        );
    }

    private static Map<String, Object> dependencyState() {
        return object(List.of(), Map.of(
                "buildTool", string("Build tool used for dependency detection"),
                "dependencies", stringArray("Detected dependencies"),
                "gatlingArtifacts", stringArray("Dependencies whose artifact name mentions Gatling"),
                "hasGatlingDependency", bool("Whether a Gatling dependency was detected"),
                "resolvedProperties", objectWithAdditionalProperties("Resolved build properties", string("Property value"))
        ));
    }

    private static Map<String, Object> pluginState() {
        var pluginInfo = object(List.of(), Map.of(
                "detected", bool("Whether the plugin marker was detected"),
                "marker", string("Dependency marker used for detection")
        ));
        return object(List.of(), Map.of(
                "KAFKA", pluginInfo,
                "JDBC", pluginInfo,
                "AMQP", pluginInfo,
                "PICATINNY", pluginInfo
        ));
    }

    private static Map<String, Object> packageNaming() {
        return object(List.of(), Map.of(
                "primaryPackage", string("Primary package used by existing simulations"),
                "packages", stringArray("Packages found in existing simulations"),
                "usesDefaultPackage", bool("Whether simulations use the default package")
        ));
    }

    private static Map<String, Object> dslMethod() {
        return object(List.of(), Map.of(
                "name", string("DSL method name"),
                "category", string("DSL method category"),
                "since", string("First supported Gatling version line"),
                "until", string("Last supported Gatling version line or empty"),
                "callTemplate", string("DSL-specific call template"),
                "sourceUrl", string("Primary source URL"),
                "confidenceLevel", string("Verification confidence level")
        ));
    }

    private static Map<String, Object> methodSemantic() {
        return object(List.of(), Map.ofEntries(
                Map.entry("name", string("DSL method name")),
                Map.entry("category", string("DSL method category")),
                Map.entry("allowedParentContext", string("Allowed parent builder or semantic context")),
                Map.entry("returnType", string("Expected return type or builder family")),
                Map.entry("chainType", string("Semantic chain family")),
                Map.entry("requiredPrecedingMethod", string("Required predecessor method, or empty string")),
                Map.entry("incompatibleMethods", stringArray("Known incompatible methods")),
                Map.entry("dslSpecificSyntax", string("Language-specific call syntax")),
                Map.entry("exampleSnippet", string("Example DSL snippet")),
                Map.entry("compileRiskNotes", string("Compile-risk notes for LLM authoring"))
        ));
    }

    private static Map<String, Object> transition() {
        return object(List.of(), Map.of(
                "index", integer("Method index in the submitted chain"),
                "method", string("Method name"),
                "category", string("Method category"),
                "allowedParentContext", string("Allowed parent context"),
                "returnType", string("Return type or builder family"),
                "known", bool("Whether the method exists in the catalog")
        ));
    }

    private static Map<String, Object> replacement() {
        return object(List.of(), Map.of(
                "since", string("Version line introducing the change"),
                "invalidPattern", string("Deprecated or removed method pattern"),
                "replacement", string("Replacement method or pattern"),
                "message", string("Human-readable replacement explanation")
        ));
    }

    private static Map<String, Object> featureGate() {
        return object(List.of(), Map.of(
                "since", string("Version line introducing the feature"),
                "feature", string("Feature identifier"),
                "message", string("Feature-gate explanation")
        ));
    }

    private static Map<String, Object> planResponseProperties() {
        return Map.of(
                "valid", bool("Whether the simulation plan passed validation"),
                "matchedGatlingLine", string("Matched compatibility line"),
                "plan", httpSimulationPlan(),
                "findings", array("Plan validation findings", importWarning()),
                "methodRequirements", array("DSL methods required by the plan", planMethodRequirement())
        );
    }

    private static Map<String, Object> planMethodRequirement() {
        return object(List.of(), Map.of(
                "name", string("Required DSL method name"),
                "missing", bool("Whether the required method is missing from the matched catalog"),
                "category", string("DSL method category"),
                "since", string("First supported Gatling version line"),
                "until", string("Last supported Gatling version line or empty"),
                "callTemplate", string("DSL-specific call template"),
                "sourceUrl", string("Primary source URL"),
                "confidenceLevel", string("Verification confidence level")
        ));
    }

    private static Map<String, Object> generationDecision() {
        return object(List.of(), Map.of(
                "subject", string("Decision subject"),
                "selectedMethod", string("Selected Gatling DSL method or renderer choice"),
                "why", string("Why this choice was made"),
                "source", string("Input source behind the decision"),
                "assumption", string("Assumption made by the generator"),
                "risk", string("Residual risk"),
                "verification", string("Recommended verification step")
        ));
    }

    private static Map<String, Object> troubleshootingFinding() {
        return object(List.of(), Map.of(
                "severity", string("Finding severity"),
                "code", string("Stable finding code"),
                "path", string("Input JSON path, line, or plan path"),
                "message", string("Human-readable finding"),
                "evidence", string("Evidence extracted from input"),
                "suggestion", string("Suggested remediation")
        ));
    }

    private static Map<String, Object> rootCauseHypothesis() {
        return object(List.of(), Map.of(
                "code", string("Stable hypothesis code"),
                "cause", string("Likely root cause"),
                "evidence", string("Evidence backing the hypothesis"),
                "confidence", string("Hypothesis confidence"),
                "verification", string("How to verify or falsify this hypothesis")
        ));
    }

    private static Map<String, Object> suggestedLoadModel() {
        return object(List.of(), Map.of(
                "workloadType", string("Suggested workload model type"),
                "estimatedConcurrentUsers", integer("Estimated concurrent users"),
                "targetRps", number("Target requests per second"),
                "durationSeconds", integer("Suggested duration in seconds"),
                "rampSeconds", integer("Suggested ramp in seconds"),
                "p95Ms", integer("p95 value used for estimation")
        ));
    }

    private static Map<String, Object> troubleshootingInjectionProfile() {
        return object(List.of(), Map.of(
                "type", string("Suggested injection profile type"),
                "rampFromUsersPerSec", integer("Initial users per second for open workload"),
                "rampToUsersPerSec", integer("Target users per second for open workload"),
                "constantUsersPerSec", integer("Steady users per second for open workload"),
                "rampToConcurrentUsers", integer("Target concurrent users for closed workload"),
                "holdConcurrentUsers", integer("Steady concurrent users for closed workload"),
                "rampDurationSeconds", integer("Ramp duration in seconds"),
                "constantDurationSeconds", integer("Steady duration in seconds")
        ));
    }

    private static Map<String, Object> httpSimulationPlan() {
        return object(List.of(), Map.ofEntries(
                Map.entry("simulationClassName", string("Simulation class name")),
                Map.entry("scenarioName", string("Scenario name")),
                Map.entry("baseUrl", string("HTTP base URL")),
                Map.entry("requests", array("Backward-compatible flat HTTP request list", httpRequest())),
                Map.entry("steps", array("First-class scenario steps", scenarioStep())),
                Map.entry("feeders", array("Gatling feeder definitions", feeder())),
                Map.entry("protocolOptions", protocolOptions()),
                Map.entry("injectionProfile", injectionProfileSchema()),
                Map.entry("assertions", array("Gatling assertions", assertionSchema()))
        ));
    }

    private static Map<String, Object> endpointInventory() {
        return object(List.of(), Map.of(
                "endpoints", array("Imported endpoint inventory entries", endpointInventoryEntry()),
                "groups", objectWithAdditionalProperties("Endpoint keys grouped by tags or inferred path groups",
                        stringArray("Endpoint keys in the group")),
                "authSchemes", stringArray("Detected authentication scheme names"),
                "warnings", stringArray("Endpoint inventory warnings")
        ));
    }

    private static Map<String, Object> endpointInventoryEntry() {
        return object(List.of(), Map.of(
                "name", string("Endpoint operation or request name"),
                "method", string("HTTP method"),
                "path", string("HTTP path including query string when present"),
                "key", string("Stable endpoint key in METHOD path format"),
                "tags", stringArray("OpenAPI tags or inferred endpoint groups"),
                "sourcePath", string("Source JSON pointer or import location"),
                "hasRequestBody", bool("Whether the imported endpoint has a request body"),
                "headerNames", stringArray("Header names used by the endpoint")
        ));
    }

    private static Map<String, Object> httpRequest() {
        return object(List.of(), Map.ofEntries(
                Map.entry("name", string("Request name")),
                Map.entry("method", string("HTTP method")),
                Map.entry("path", string("Request path without query string when queryParams is used")),
                Map.entry("headers", objectWithAdditionalProperties("HTTP headers", string("Header value"))),
                Map.entry("queryParams", objectWithAdditionalProperties("HTTP query parameters", string("Query value"))),
                Map.entry("formParams", objectWithAdditionalProperties("HTTP form parameters", string("Form value"))),
                Map.entry("multipartParts", array("Multipart request parts", multipartPart())),
                Map.entry("body", string("Request body")),
                Map.entry("resources", array("Secondary HTTP resources", httpResource())),
                Map.entry("auth", auth()),
                Map.entry("cookies", array("HTTP cookies", cookie())),
                Map.entry("options", requestOptions()),
                Map.entry("checks", array("Request checks", checkPlan()))
        ));
    }

    private static Map<String, Object> feeder() {
        return object(List.of(), Map.of(
                "name", string("Feeder name"),
                "type", string("Feeder type such as csv or json"),
                "source", string("Feeder source path"),
                "strategy", string("Feeder strategy such as circular or random"),
                "columns", stringArray("Expected feeder columns")
        ));
    }

    private static Map<String, Object> protocolOptions() {
        return object(List.of(), Map.of(
                "headers", objectWithAdditionalProperties("Protocol-level HTTP headers", string("Header value")),
                "followRedirects", bool("Whether redirects are followed at protocol level"),
                "http2", bool("Whether HTTP/2 is enabled"),
                "proxyHost", string("Proxy host"),
                "proxyPort", integer("Proxy port")
        ));
    }

    private static Map<String, Object> scenarioStep() {
        return object(List.of(), Map.ofEntries(
                Map.entry("type", string("Scenario step type")),
                Map.entry("feederName", string("Feeder name for feed steps")),
                Map.entry("request", httpRequest()),
                Map.entry("pause", pause()),
                Map.entry("loop", loop()),
                Map.entry("group", group()),
                Map.entry("conditional", conditional())
        ));
    }

    private static Map<String, Object> pause() {
        return object(List.of(), Map.of("durationSeconds", integer("Pause duration in seconds")));
    }

    private static Map<String, Object> loop() {
        return object(List.of(), Map.of(
                "type", string("Loop type"),
                "count", integer("Repeat count"),
                "durationSeconds", integer("During duration in seconds"),
                "steps", array("Nested loop steps", shallowStep())
        ));
    }

    private static Map<String, Object> group() {
        return object(List.of(), Map.of(
                "name", string("Gatling group name"),
                "steps", array("Nested group steps", shallowStep())
        ));
    }

    private static Map<String, Object> conditional() {
        return object(List.of(), Map.of(
                "expression", string("Raw condition expression"),
                "left", string("Left expression"),
                "operator", string("Condition operator"),
                "right", string("Right expected value"),
                "steps", array("Nested conditional steps", shallowStep())
        ));
    }

    private static Map<String, Object> shallowStep() {
        return object(List.of(), Map.of(
                "type", string("Nested step type"),
                "request", httpRequest(),
                "pause", pause()
        ));
    }

    private static Map<String, Object> multipartPart() {
        return object(List.of(), Map.of(
                "name", string("Part name"),
                "value", string("Inline part value"),
                "fileName", string("Uploaded file name"),
                "contentType", string("Part content type"),
                "filePath", string("Workspace-relative file path")
        ));
    }

    private static Map<String, Object> httpResource() {
        return object(List.of(), Map.of(
                "name", string("Resource request name"),
                "method", string("Resource HTTP method"),
                "path", string("Resource path"),
                "headers", objectWithAdditionalProperties("Resource headers", string("Header value"))
        ));
    }

    private static Map<String, Object> auth() {
        return object(List.of(), Map.of(
                "type", string("Authentication type"),
                "username", string("Basic auth username expression"),
                "password", string("Basic auth password expression"),
                "token", string("Bearer token expression"),
                "headerName", string("Auth header name")
        ));
    }

    private static Map<String, Object> cookie() {
        return object(List.of(), Map.of(
                "name", string("Cookie name"),
                "value", string("Cookie value"),
                "domain", string("Cookie domain"),
                "path", string("Cookie path")
        ));
    }

    private static Map<String, Object> requestOptions() {
        return object(List.of(), Map.of(
                "followRedirects", bool("Whether this request follows redirects"),
                "silent", bool("Whether this request is marked silent")
        ));
    }

    private static Map<String, Object> checkPlan() {
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

    private static Map<String, Object> requestStats() {
        return object(List.of(), Map.ofEntries(
                Map.entry("name", string("Request or group name")),
                Map.entry("total", integer("Total request count")),
                Map.entry("ok", integer("Successful request count")),
                Map.entry("ko", integer("Failed request count")),
                Map.entry("errorRate", number("Error rate in percent")),
                Map.entry("minMs", integer("Minimum response time in milliseconds")),
                Map.entry("maxMs", integer("Maximum response time in milliseconds")),
                Map.entry("meanMs", integer("Mean response time in milliseconds")),
                Map.entry("p50Ms", integer("50th percentile response time in milliseconds")),
                Map.entry("p75Ms", integer("75th percentile response time in milliseconds")),
                Map.entry("p95Ms", integer("95th percentile response time in milliseconds")),
                Map.entry("p99Ms", integer("99th percentile response time in milliseconds")),
                Map.entry("meanRps", number("Mean requests per second"))
        ));
    }

    private static Map<String, Object> interactionSummary() {
        return object(List.of(), Map.of(
                "clientName", string("MCP client name reported to the server"),
                "clientVersion", string("MCP client version reported to the server"),
                "elicitation", string("Elicitation status or support flag"),
                "elicitedFields", stringArray("Fields returned by elicitation"),
                "sampling", string("Sampling status"),
                "sampledText", string("Sampled advisory text when sampling completed"),
                "progress", bool("Whether the client supports progress notifications"),
                "progressTokenPresent", bool("Whether progress can be correlated with a progress token")
        ));
    }

    private static Map<String, Object> finding() {
        return object(List.of(), Map.of(
                "severity", string("Finding severity"),
                "code", string("Stable finding code"),
                "path", string("Input path or JSON pointer"),
                "message", string("Human-readable finding message"),
                "suggestion", string("Suggested remediation")
        ));
    }

    private static Map<String, Object> importWarning() {
        return object(List.of(), Map.of(
                "severity", string("Warning severity"),
                "code", string("Stable warning code"),
                "path", string("Input path or JSON pointer"),
                "message", string("Human-readable warning message")
        ));
    }

    private static Map<String, Object> warningMessage() {
        return object(List.of("code", "message", "source"), Map.of(
                "code", string("Stable warning code"),
                "message", string("Human-readable warning message"),
                "source", string("Warning source")
        ));
    }

    private static Map<String, Object> validationFinding() {
        return object(List.of("code", "severity", "message", "evidence"), Map.of(
                "code", string("Stable validation finding code"),
                "severity", string("Finding severity"),
                "message", string("Human-readable validation message"),
                "evidence", string("Masked source evidence")
        ));
    }

    private static Map<String, Object> targetContext() {
        return object(
                List.of("gatlingVersion", "edition", "language", "buildTool", "javaVersion", "nodeVersion", "plugin", "pluginVersion"),
                Map.of(
                        "gatlingVersion", string("Requested Gatling version"),
                        "edition", string("Edition context"),
                        "language", string("Requested DSL language"),
                        "buildTool", string("Requested build tool"),
                        "javaVersion", string("Effective Java version or empty string"),
                        "nodeVersion", string("Effective Node.js version or empty string"),
                        "plugin", string("Selected community plugin or empty string"),
                        "pluginVersion", string("Selected or requested plugin version or empty string")
                )
        );
    }

    private static Map<String, Object> pluginIdentity() {
        return object(
                List.of("selected", "name", "version", "verifiedAgainstGatlingVersion",
                        "minimumJavaVersion", "confidence", "sourceUrl"),
                Map.of(
                        "selected", bool("Whether strict-verified plugin generation was selected"),
                        "name", string("Community plugin name or empty string"),
                        "version", string("Verified plugin version or empty string"),
                        "verifiedAgainstGatlingVersion", string("Exact Gatling version used for verification"),
                        "minimumJavaVersion", string("Minimum verified Java version"),
                        "confidence", string("Verification confidence source"),
                        "sourceUrl", string("Plugin release verification URL")
                )
        );
    }

    private static Map<String, Object> capabilityMetadata() {
        return object(
                List.of("protocol", "matchedGatlingLine", "compatibilityPolicy", "dslMethodCount",
                        "dslMethodCategories", "plugin", "pluginVersion", "verifiedAgainstGatlingVersion",
                        "minimumJavaVersion", "confidence", "repositoryUrl", "sourceUrl"),
                Map.ofEntries(
                        Map.entry("protocol", string("Requested protocol")),
                        Map.entry("matchedGatlingLine", string("Matched Gatling compatibility line")),
                        Map.entry("compatibilityPolicy", string("Compatibility policy")),
                        Map.entry("dslMethodCount", integer("Matching DSL method count")),
                        Map.entry("dslMethodCategories", stringArray("Matching DSL method categories")),
                        Map.entry("plugin", string("Verified plugin name or empty string")),
                        Map.entry("pluginVersion", string("Verified plugin version or empty string")),
                        Map.entry("verifiedAgainstGatlingVersion", string("Exact Gatling verification version")),
                        Map.entry("minimumJavaVersion", string("Minimum verified Java version")),
                        Map.entry("confidence", string("Verification confidence")),
                        Map.entry("repositoryUrl", string("Plugin repository URL")),
                        Map.entry("sourceUrl", string("Plugin release verification URL"))
                )
        );
    }
}
