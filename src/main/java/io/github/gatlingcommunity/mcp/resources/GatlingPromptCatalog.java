package io.github.gatlingcommunity.mcp.resources;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GatlingPromptCatalog {
    private final Map<String, PromptDefinition> prompts = new LinkedHashMap<>();

    public GatlingPromptCatalog() {
        add(new PromptDefinition(
                "create_simulation",
                "Create Gatling Simulation",
                "Guide an LLM through the structured authoring flow for a Gatling Community simulation.",
                List.of(
                        required("gatlingVersion", "Target Gatling version, for example 3.9.5"),
                        required("language", "Target DSL: JAVA, KOTLIN, SCALA, JAVASCRIPT, or TYPESCRIPT"),
                        required("buildTool", "Build tool: MAVEN, GRADLE, SBT, or NPM"),
                        required("protocol", "Protocol, HTTP for deep v1 authoring"),
                        required("goal", "User intent for the scenario flow"),
                        optional("baseUrl", "Optional HTTP base URL"),
                        optional("simulationClassName", "Optional simulation class name")
                ),
                args -> """
                        Generate a Gatling Community simulation with the structured authoring flow.

                        Target:
                        - Gatling version: %s
                        - DSL language: %s
                        - Build tool: %s
                        - Protocol: %s
                        - Base URL: %s
                        - Simulation class: %s

                        User goal:
                        %s

                        Required MCP flow:
                        1. Call gatling_plan_simulation with the target context and goal.
                        2. Inspect the structured plan and methodRequirements.
                        3. Call gatling_validate_simulation_plan.
                        4. Call gatling_generate_from_plan only when valid=true.
                        5. Explain warnings and version constraints rather than fabricating unsupported Gatling APIs.
                        """.formatted(
                        value(args, "gatlingVersion", "unspecified"),
                        value(args, "language", "unspecified"),
                        value(args, "buildTool", "unspecified"),
                        value(args, "protocol", "HTTP"),
                        value(args, "baseUrl", "unspecified"),
                        value(args, "simulationClassName", "ApiSimulation"),
                        value(args, "goal", "unspecified")
                )
        ));
        add(new PromptDefinition(
                "create_project",
                "Create Gatling Project Layout",
                "Explain the minimal local project layout for Gatling Community authoring.",
                List.of(
                        required("language", "Target DSL"),
                        required("buildTool", "Build tool"),
                        optional("gatlingVersion", "Target Gatling version")
                ),
                args -> """
                        Explain a minimal Gatling Community project layout.
                        Language: %s
                        Build tool: %s
                        Gatling version: %s

                        Keep the answer focused on local source roots, dependencies, and how to run the generated simulation.
                        """.formatted(
                        value(args, "language", "unspecified"),
                        value(args, "buildTool", "unspecified"),
                        value(args, "gatlingVersion", "latest supported")
                )
        ));
        add(new PromptDefinition(
                "import_http_plan",
                "Import HTTP Plan",
                "Guide an LLM through importing OpenAPI, HAR, curl, or Postman input into a validated Gatling HTTP simulation plan.",
                List.of(
                        required("sourceType", "Import source type: OPENAPI, HAR, CURL, or POSTMAN"),
                        required("gatlingVersion", "Target Gatling version"),
                        required("language", "Target DSL: JAVA, KOTLIN, SCALA, JAVASCRIPT, or TYPESCRIPT"),
                        required("buildTool", "Build tool: MAVEN, GRADLE, SBT, or NPM"),
                        optional("simulationClassName", "Optional simulation class name"),
                        optional("baseUrl", "Optional base URL override")
                ),
                args -> """
                        Import external HTTP API material into a Gatling Community simulation plan.

                        Source type: %s
                        Target:
                        - Gatling version: %s
                        - DSL language: %s
                        - Build tool: %s
                        - Base URL override: %s
                        - Simulation class: %s

                        Required MCP flow:
                        1. For OPENAPI call gatling_import_openapi with document.
                        2. For HAR call gatling_import_har with document.
                        3. For CURL call gatling_import_curl with curl.
                        4. For POSTMAN call gatling_import_postman_collection with document.
                        5. Inspect warnings, especially redacted secrets and incomplete operations.
                        6. Call gatling_generate_from_plan only when valid=true.
                        """.formatted(
                        value(args, "sourceType", "unspecified"),
                        value(args, "gatlingVersion", "unspecified"),
                        value(args, "language", "unspecified"),
                        value(args, "buildTool", "unspecified"),
                        value(args, "baseUrl", "source-derived"),
                        value(args, "simulationClassName", "ApiSimulation")
                )
        ));
        add(new PromptDefinition(
                "analyze_report",
                "Analyze Gatling Report",
                "Guide an LLM through local Gatling report and log analysis.",
                List.of(
                        optional("reportPath", "Optional path to a Gatling report directory, stats.js, or stats.json"),
                        optional("logPath", "Optional path to a Gatling runtime log or simulation.log"),
                        optional("errorRateThreshold", "Error-rate threshold in percent"),
                        optional("p95ThresholdMs", "p95 threshold in milliseconds"),
                        optional("p99ThresholdMs", "p99 threshold in milliseconds")
                ),
                args -> """
                        Analyze local Gatling run output.

                        Inputs:
                        - Report path: %s
                        - Log path: %s
                        - Error-rate threshold: %s
                        - p95 threshold ms: %s
                        - p99 threshold ms: %s

                        Required MCP flow:
                        1. Prefer gatling_analyze_report when a report directory, stats.js, or stats.json is available.
                        2. Use gatling_analyze_log for runtime logs, startup failures, and best-effort simulation.log request events.
                        3. Explain topSlowRequests, topFailedRequests, threshold findings, and warnings separately.
                        4. Treat simulation.log results as diagnostic signals because the file format is not a stable public contract.
                        """.formatted(
                        value(args, "reportPath", "unspecified"),
                        value(args, "logPath", "unspecified"),
                        value(args, "errorRateThreshold", "1.0"),
                        value(args, "p95ThresholdMs", "1000"),
                        value(args, "p99ThresholdMs", "2000")
                )
        ));
        add(new PromptDefinition(
                "explain_simulation",
                "Explain Gatling Simulation",
                "Explain scenarios, requests, checks, feeders, injection profiles, assertions, and version risks.",
                List.of(
                        required("code", "Gatling simulation source code"),
                        optional("gatlingVersion", "Target Gatling version"),
                        optional("language", "DSL language")
                ),
                args -> """
                        Explain this Gatling Community simulation. Cover scenarios, requests, checks, feeders,
                        injection profiles, assertions, version risks, and practical improvement points.

                        Gatling version: %s
                        Language: %s

                        Code:
                        %s
                        """.formatted(
                        value(args, "gatlingVersion", "unspecified"),
                        value(args, "language", "unspecified"),
                        value(args, "code", "")
                )
        ));
        add(new PromptDefinition(
                "find_simulation_issues",
                "Find Gatling Simulation Issues",
                "Find deprecated APIs, unsupported usage, hardcoded secrets, feeder risks, and correlation issues.",
                List.of(
                        required("code", "Gatling simulation source code"),
                        optional("gatlingVersion", "Target Gatling version"),
                        optional("language", "DSL language")
                ),
                args -> """
                        Review this Gatling Community simulation for practical authoring issues.
                        Use gatling_validate_feature_usage when code is available.

                        Gatling version: %s
                        Language: %s

                        Code:
                        %s
                        """.formatted(
                        value(args, "gatlingVersion", "unspecified"),
                        value(args, "language", "unspecified"),
                        value(args, "code", "")
                )
        ));
        add(new PromptDefinition(
                "explain_community_limitations",
                "Explain Community Authoring Scope",
                "Explain local Gatling Community authoring capabilities and limitations for this MCP server.",
                List.of(optional("protocol", "Protocol to explain")),
                args -> """
                        Explain the local Gatling Community authoring scope for protocol %s.
                        Focus on what this MCP server can generate, validate, analyze, and where it returns warnings.
                        """.formatted(value(args, "protocol", "HTTP"))
        ));
    }

    public List<String> listNames() {
        return List.copyOf(prompts.keySet());
    }

    public List<PromptDefinition> list() {
        return List.copyOf(prompts.values());
    }

    public PromptDefinition getDefinition(String name) {
        var value = prompts.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Unknown prompt: " + name);
        }
        return value;
    }

    public String get(String name) {
        return render(name, Map.of());
    }

    public String render(String name, Map<String, Object> arguments) {
        return getDefinition(name).render(arguments == null ? Map.of() : arguments);
    }

    private void add(PromptDefinition definition) {
        prompts.put(definition.name(), definition);
    }

    private static McpSchema.PromptArgument required(String name, String description) {
        return McpSchema.PromptArgument.builder(name)
                .description(description)
                .required(true)
                .build();
    }

    private static McpSchema.PromptArgument optional(String name, String description) {
        return McpSchema.PromptArgument.builder(name)
                .description(description)
                .required(false)
                .build();
    }

    private static String value(Map<String, Object> args, String key, String fallback) {
        var value = args.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return String.valueOf(value);
    }

    public record PromptDefinition(
            String name,
            String title,
            String description,
            List<McpSchema.PromptArgument> arguments,
            Renderer renderer
    ) {
        public String render(Map<String, Object> values) {
            return renderer.render(values);
        }
    }

    @FunctionalInterface
    public interface Renderer {
        String render(Map<String, Object> values);
    }
}
