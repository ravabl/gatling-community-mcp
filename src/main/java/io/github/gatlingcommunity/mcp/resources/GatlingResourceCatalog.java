package io.github.gatlingcommunity.mcp.resources;

import io.github.gatlingcommunity.mcp.data.SourceDataRepository;
import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import io.github.gatlingcommunity.mcp.core.model.DslLanguage;
import io.github.gatlingcommunity.mcp.core.model.Protocol;
import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class GatlingResourceCatalog {
    private static final Pattern HTTP_METHODS_URI = Pattern.compile("^gatling://methods/http/([^/]+)/([^/]+)$");
    private static final Pattern VERSION_FEATURES_URI = Pattern.compile("^gatling://versions/([^/]+)/features$");
    private static final Pattern VERSION_BREAKING_CHANGES_URI = Pattern.compile("^gatling://versions/([^/]+)/breaking-changes$");
    private static final Pattern EXAMPLE_URI = Pattern.compile("^gatling://examples/([^/]+)/([^/]+)/([^/]+)$");

    private final SourceDataRepository sourceData;
    private final Map<String, String> resources = new LinkedHashMap<>();
    private final List<GatlingResourceTemplate> templates = List.of(
            new GatlingResourceTemplate(
                    "gatling://methods/http/{language}/{version}",
                    "http_dsl_methods",
                    "HTTP DSL Methods",
                    "Gatling HTTP DSL method catalog for a selected language and Gatling version.",
                    "text/plain",
                    1.0
            ),
            new GatlingResourceTemplate(
                    "gatling://versions/{version}/features",
                    "version_features",
                    "Version Features",
                    "Gatling feature entries active for a selected version.",
                    "text/plain",
                    0.8
            ),
            new GatlingResourceTemplate(
                    "gatling://versions/{version}/breaking-changes",
                    "version_breaking_changes",
                    "Version Breaking Changes",
                    "Gatling breaking-change checks active for a selected version.",
                    "text/plain",
                    0.9
            ),
            new GatlingResourceTemplate(
                    "gatling://examples/{language}/{protocol}/{pattern}",
                    "gatling_examples",
                    "Gatling Authoring Examples",
                    "Concrete Gatling authoring examples by DSL, protocol, and pattern.",
                    "text/plain",
                    0.9
            )
    );

    public GatlingResourceCatalog() {
        this(SourceDataRepository.loadDefault());
    }

    public GatlingResourceCatalog(SourceDataRepository sourceData) {
        this.sourceData = sourceData;
        resources.put("gatling://versions/latest",
                "Latest v1-supported Gatling line: 3.15. Target range: 3.7 through 3.15. Patch releases such as 3.9.5 match their minor line.");
        resources.put("gatling://versions/history", versionHistory());
        sourceData.supportedGatlingVersions().forEach(version -> {
            resources.put("gatling://versions/%s/features".formatted(version), featuresFor(version));
            resources.put("gatling://versions/%s/breaking-changes".formatted(version), breakingChangesFor(version));
            for (var language : DslLanguage.values()) {
                resources.put("gatling://methods/http/%s/%s".formatted(language.name().toLowerCase(), version),
                        methodsFor(language, version));
            }
        });
        resources.put("gatling://compatibility/java",
                "Java runtime: 64-bit OpenJDK LTS 11, 17, 21, and 25. OpenJ9 and 32-bit JVMs are unsupported.");
        resources.put("gatling://compatibility/node",
                "JavaScript/TypeScript local authoring requires Node.js v24+ LTS and npm v11+.");
        resources.put("gatling://compatibility/dsl-matrix",
                "Core authoring DSLs: Java, Kotlin, Scala, JavaScript, TypeScript. Java DSL starts at Gatling 3.7; Kotlin uses the Java API.");
        resources.put("gatling://compatibility/protocol-matrix",
                protocolMatrix());
        resources.put("gatling://compatibility/build-tools",
                "Build tools: Maven, Gradle, sbt, npm, TypeScript npm. Gatling 3.11 Maven plugin requires Maven 3.6.3+.");
        resources.put("gatling://compatibility/community-edition",
                "Default edition is community. v1 generates local Community-compatible authoring code and reports limitations as warnings.");
        resources.put("gatling://community-plugins/index",
                "Opt-in strict-verified JVM targets: Kafka 1.0.6, JDBC 1.2.0, AMQP 1.3.3, and Picatinny 1.24.0, each verified against Gatling 3.13.5. Other combinations return warnings and no generated code.");
        resources.put("gatling://community-plugins/kafka",
                "gatling-kafka-plugin 1.0.6 is strict-verified against Gatling 3.13.5 for Java/Maven-or-Gradle, Kotlin/Maven-or-Gradle, and Scala/sbt. Generated pattern: request-reply with consumer settings, timeout, JSON check, and key correlation. Source: https://github.com/galax-io/gatling-kafka-plugin/releases/tag/v1.0.6");
        resources.put("gatling://community-plugins/jdbc",
                "gatling-jdbc-plugin 1.2.0 is strict-verified against Gatling 3.13.5 for Java/Maven-or-Gradle, Kotlin/Maven-or-Gradle, and Scala/sbt. Generated pattern: parameterized query with a non-empty result check and bounded pool. Add the vendor JDBC driver separately. Source: https://github.com/galax-io/gatling-jdbc-plugin/releases/tag/v1.2.0");
        resources.put("gatling://community-plugins/amqp",
                "gatling-amqp-plugin 1.3.3 is strict-verified against Gatling 3.13.5 for Java/Maven-or-Gradle, Kotlin/Maven-or-Gradle, and Scala/sbt. Generated pattern: request-reply with message-id correlation, reply timeout, and body check. Source: https://github.com/galax-io/gatling-amqp-plugin/releases/tag/v1.3.3");
        resources.put("gatling://community-plugins/picatinny",
                "gatling-picatinny 1.24.0 is strict-verified against Gatling 3.13.5 for Java/Maven-or-Gradle, Kotlin/Maven-or-Gradle, and Scala/sbt. Generated pattern: UUID feeder, transaction boundaries, HTTP check, and failed-request assertion. Redis requires explicit project configuration. Source: https://github.com/galax-io/gatling-picatinny/releases/tag/v1.24.0");
        resources.put("gatling://authoring/http/golden-flows", goldenFlows());
        for (var language : DslLanguage.values()) {
            resources.put("gatling://examples/%s/http/login-token-orders"
                            .formatted(language.name().toLowerCase()),
                    exampleFor(language, Protocol.HTTP, "login-token-orders"));
        }
        capabilityOnlyProtocols().forEach(protocol -> resources.put(
                "gatling://examples/java/%s/capability-notes".formatted(protocol.name().toLowerCase()),
                capabilityNotesFor(protocol)
        ));
        resources.put("gatling://imports/http", httpImports());
        resources.put("gatling://analysis/reports", reportAnalysis());
        resources.put("gatling://mcp/interactions", mcpInteractions());
        resources.put("gatling://recipes/http",
                "HTTP recipe: detect or resolve target context, plan the flow, validate the plan, generate code, validate generated code, then run a compile-only check. Use request-level checks and explicit correlation for every reused response value.");
        resources.put("gatling://recipes/feeders",
                "Feeder recipe: declare csv/json/custom data, choose queue only when rows are guaranteed for all users and iterations, otherwise use circular or random, then call gatling_check_feeder_risk before generation.");
        resources.put("gatling://recipes/checks",
                "Check recipe: combine a status check with a business body check. Use saveAs for correlation, validate every referenced session key, and avoid status.is(200) as the only quality signal.");
        resources.put("gatling://recipes/injection",
                "Injection recipe: start conservatively with a ramp and a stable open-workload stage. Keep injection in Simulation setup and use gatling_suggest_load_model when target RPS and latency are known.");
        resources.put("gatling://recipes/assertions",
                "Assertion recipe: set a global failed-request budget and at least one latency budget such as p95. Add request- or group-level assertions for critical operations.");
        resources.put("gatling://limitations",
                "Verified deep authoring covers Gatling 3.7 through 3.15 version lines and HTTP for five DSLs. WebSocket, SSE, JMS, MQTT, and gRPC are capability-only. Community plugins require an explicit strict-verified JVM target.");
        resources.put("gatling://upgrade-guides/index",
                "Upgrade flow: resolve both version lines, read breaking-change resources, call gatling_find_replacement_method for removed APIs, validate the method chain, regenerate or patch, and finish with a compile-only check. Official guides: https://docs.gatling.io/release-notes/gatling/upgrading/");
        for (var language : DslLanguage.values()) {
            for (var buildTool : projectBuildTools(language)) {
                resources.put("gatling://project-templates/%s/%s".formatted(
                                language.name().toLowerCase(), buildTool.name().toLowerCase()),
                        projectTemplate(language, buildTool));
            }
        }
        resources.put("gatling://patterns/layout-boundaries",
                "Keep cases atomic, feeders data-only, scenarios business-flow-only, and simulations focused on protocol plus injection setup.");
        resources.put("gatling://patterns/anti-patterns",
                "Warnings: Thread.sleep, println under load, hardcoded secrets, injection in scenarios, requests in simulations, feeder exhaustion.");
    }

    public List<String> listUris() {
        return List.copyOf(resources.keySet());
    }

    public List<GatlingResourceTemplate> listTemplates() {
        return templates;
    }

    public String read(String uri) {
        var value = resources.get(uri);
        if (value != null) {
            return value;
        }
        var methods = HTTP_METHODS_URI.matcher(uri);
        if (methods.matches()) {
            return methodsFor(DslLanguage.valueOf(methods.group(1).toUpperCase()), methods.group(2));
        }
        var features = VERSION_FEATURES_URI.matcher(uri);
        if (features.matches()) {
            return featuresFor(features.group(1));
        }
        var breakingChanges = VERSION_BREAKING_CHANGES_URI.matcher(uri);
        if (breakingChanges.matches()) {
            return breakingChangesFor(breakingChanges.group(1));
        }
        var example = EXAMPLE_URI.matcher(uri);
        if (example.matches()) {
            return exampleFor(
                    DslLanguage.valueOf(example.group(1).toUpperCase()),
                    Protocol.valueOf(example.group(2).toUpperCase()),
                    example.group(3)
            );
        }
        throw new IllegalArgumentException("Unknown resource URI: " + uri);
    }

    private String versionHistory() {
        return "Known Gatling lines: %s. v1 deep authoring support: %s."
                .formatted(sourceData.knownGatlingVersions(), sourceData.supportedGatlingVersions());
    }

    private static List<BuildTool> projectBuildTools(DslLanguage language) {
        return switch (language) {
            case JAVA, KOTLIN -> List.of(BuildTool.MAVEN, BuildTool.GRADLE);
            case SCALA -> List.of(BuildTool.SBT, BuildTool.MAVEN, BuildTool.GRADLE);
            case JAVASCRIPT, TYPESCRIPT -> List.of(BuildTool.NPM);
        };
    }

    private static String projectTemplate(DslLanguage language, BuildTool buildTool) {
        var sourceRoot = switch (language) {
            case JAVA -> "src/test/java";
            case KOTLIN -> "src/test/kotlin";
            case SCALA -> "src/test/scala";
            case JAVASCRIPT -> "src/gatling/javascript";
            case TYPESCRIPT -> "src/gatling/typescript";
        };
        var buildFile = switch (buildTool) {
            case MAVEN -> "pom.xml";
            case GRADLE -> "build.gradle or build.gradle.kts";
            case SBT -> "build.sbt";
            case NPM -> "package.json";
            case UNKNOWN -> "build file";
        };
        var resourceRoot = switch (language) {
            case JAVA, KOTLIN, SCALA -> "src/test/resources";
            case JAVASCRIPT, TYPESCRIPT -> "resources";
        };
        var simulationFile = switch (language) {
            case JAVA -> "ExampleSimulation.java";
            case KOTLIN -> "ExampleSimulation.kt";
            case SCALA -> "ExampleSimulation.scala";
            case JAVASCRIPT -> "example-simulation.js";
            case TYPESCRIPT -> "example-simulation.ts";
        };
        var dependencies = switch (language) {
            case JAVA, KOTLIN, SCALA ->
                    "Gatling core and HTTP modules matching the selected Gatling version";
            case JAVASCRIPT, TYPESCRIPT ->
                    "@gatling.io/core and @gatling.io/http at the same selected version";
        };
        var buildNotes = switch (buildTool) {
            case MAVEN -> switch (language) {
                case SCALA ->
                        "Configure scala-maven-plugin for Scala compilation and gatling-maven-plugin for execution. "
                                + "For Gatling 3.11 with gatling-maven-plugin 4.8.0, require Maven 3.6.3+.";
                case KOTLIN ->
                        "Configure kotlin-maven-plugin and gatling-maven-plugin; Kotlin authoring uses the Gatling Java API.";
                default -> "Configure gatling-maven-plugin and keep its version compatible with Gatling.";
            };
            case GRADLE ->
                    "Apply the io.gatling.gradle plugin. For Gatling 3.11+, document CI=true, --simulation, and --non-interactive command behavior.";
            case SBT -> "Add the Gatling sbt plugin and keep plugin, Scala, and Gatling versions compatible.";
            case NPM -> "Declare Gatling packages in package.json and expose a gatling:test npm script.";
            case UNKNOWN -> "Use a supported build tool before generating project files.";
        };
        var runCommand = switch (buildTool) {
            case MAVEN -> "mvn gatling:test -Dgatling.simulationClass=ExampleSimulation";
            case GRADLE -> "CI=true ./gradlew gatlingRun --simulation ExampleSimulation --non-interactive";
            case SBT -> "sbt 'Gatling/testOnly ExampleSimulation'";
            case NPM -> "npm run gatling:test -- --simulation ExampleSimulation";
            case UNKNOWN -> "configure a supported build tool";
        };
        var bundleNote = switch (language) {
            case KOTLIN, SCALA -> "Do not propose the standalone Gatling bundle for this DSL; use the selected build tool.";
            case JAVA -> "The standalone bundle is Java-only; this template still uses the selected reproducible build tool.";
            case JAVASCRIPT, TYPESCRIPT -> "Use npm packages and scripts, not the JVM standalone bundle.";
        };
        return """
                %s/%s Gatling project blueprint

                Directory layout:
                - %s/%s: example simulation using scenario, http protocol, checks, injection, and assertions.
                - %s/users.csv: example feeder with a header and one non-secret sample row.
                - %s: build definition.
                - README.md: prerequisites, exact Gatling version, build command, and MCP validation flow.
                - .gitignore: build output, IDE metadata, local reports, and secret-bearing local files.

                Build contract:
                - Dependencies: %s.
                - %s
                - Run command: %s
                - %s

                Authoring flow:
                1. Detect or resolve project context.
                2. Plan and validate the simulation.
                3. Generate %s and users.csv without embedded credentials.
                4. Run gatling_validate_generated_code and gatling_compile_check before a load run.
                """.formatted(
                language.name(), buildTool.name(), sourceRoot, simulationFile,
                resourceRoot, buildFile, dependencies, buildNotes, runCommand, bundleNote, simulationFile);
    }

    private String featuresFor(String version) {
        var features = sourceData.featureRulesFor(version).stream()
                .map(SourceDataRepository.FeatureRule::feature)
                .filter(feature -> !feature.isBlank())
                .toList();
        if (features.isEmpty()) {
            return "No feature-only entries recorded for Gatling %s; see breaking changes and protocol resources.".formatted(version);
        }
        return "Gatling %s feature entries: %s.".formatted(version, features);
    }

    private String breakingChangesFor(String version) {
        var breakingChanges = sourceData.featureRulesFor(version).stream()
                .filter(rule -> !rule.invalidPattern().isBlank())
                .map(rule -> "%s -> %s".formatted(rule.invalidPattern(), rule.replacement()))
                .toList();
        if (breakingChanges.isEmpty()) {
            return "No breaking API replacements recorded through Gatling %s.".formatted(version);
        }
        return "Gatling %s active breaking-change checks: %s.".formatted(version, breakingChanges);
    }

    private String protocolMatrix() {
        var httpGenerationMode = sourceData.protocolSupport(Protocol.HTTP).generationMode();
        var matrix = new StringBuilder("Protocol generation matrix (v1):\n")
                .append("- HTTP: generationMode=%s; HTTP %s generation is available after plan validation.\n"
                        .formatted(httpGenerationMode, httpGenerationMode));
        capabilityOnlyProtocols().forEach(protocol -> matrix.append(
                "- %s: generationMode=capability-only; capability metadata and warnings only, no full code generation.\n"
                        .formatted(protocol.name())
        ));
        return matrix.toString();
    }

    private List<Protocol> capabilityOnlyProtocols() {
        return java.util.Arrays.stream(Protocol.values())
                .filter(protocol -> "capability-only".equals(sourceData.protocolSupport(protocol).generationMode()))
                .toList();
    }

    private static String capabilityNotesFor(Protocol protocol) {
        return """
                %s capability notes:
                - The server reports capability metadata and the protocol.generation.disabled.v1 warning for this protocol.
                - Check the selected context: Gatling version, Java DSL, build tool, Community edition, and the protocol dependency assumptions in the target project.
                - %s
                - Full code generation is disabled in v1. These notes are safe guidance, not a generated code snippet.
                """.formatted(protocol.name(), protocolPrerequisite(protocol));
    }

    private static String protocolPrerequisite(Protocol protocol) {
        return switch (protocol) {
            case WEBSOCKET -> "Confirm endpoint upgrade/authentication behavior and a Gatling WebSocket module version aligned with the project.";
            case SSE -> "Confirm event endpoint, reconnect/proxy behavior, and the HTTP transport dependency version used by the project.";
            case JMS -> "Confirm broker/client dependency versions and whether the selected Gatling line expects javax.jms or jakarta.jms APIs.";
            case MQTT -> "Confirm broker protocol level, authentication/TLS settings, and the MQTT module dependency version.";
            case GRPC -> "Confirm generated stubs, protobuf/grpc-java dependency versions, TLS, and service descriptor assumptions.";
            default -> throw new IllegalArgumentException("Capability notes are not available for " + protocol);
        };
    }

    private String methodsFor(DslLanguage language, String version) {
        var target = new TargetContext(version, "community", language, buildTool(language),
                javaVersion(language), nodeVersion(language), Optional.empty());
        var methods = sourceData.dslMethods(target, Protocol.HTTP);
        if (methods.isEmpty()) {
            return "No HTTP DSL methods recorded for %s %s.".formatted(language, version);
        }
        return "HTTP DSL methods for %s Gatling %s: %s.".formatted(
                language,
                version,
                methods.stream().map(method -> method.name()).toList()
        );
    }

    private static String goldenFlows() {
        return """
                HTTP authoring golden flow:
                1. Call gatling_plan_simulation with gatlingVersion, language, buildTool, protocol=HTTP, goal, simulationClassName, and baseUrl.
                2. Inspect the structured plan before generating code.
                3. Call gatling_validate_simulation_plan with the returned plan.
                4. Generate code only with gatling_generate_from_plan when valid=true.

                Canonical login/token/orders intent:
                goal: login, extract JWT token, call /api/orders with Authorization header, assert 200 and non-empty orders.
                Expected plan shape:
                - POST /login
                - jsonPath $.token saveAs jwtToken
                - GET /api/orders
                - Authorization: Bearer #{jwtToken}
                - status 200 checks on both requests
                - jsonPath $.orders[0] exists
                - rampAndConstant open workload
                - global.failedRequests.percent lt 1.0 assertion.
                """;
    }

    private static String httpImports() {
        return """
                HTTP import workflow:
                - gatling_import_openapi accepts Swagger/OpenAPI 2.0 and OpenAPI 3.x JSON or YAML and creates a structured HTTP simulation plan.
                - gatling_import_har accepts HAR 1.1 and current HAR 1.2 JSON entries and preserves method, path, safe headers, body, and response status checks.
                - gatling_import_curl accepts one curl command, including classic -d/--data-* syntax and modern --json syntax.
                - gatling_import_postman_collection accepts Postman Collection 2.0 and 2.1 JSON, including nested items and simple pm.response.to.have.status checks.
                - Import tools return sourceVersion, plan, warnings, validation findings, and method requirements.
                - Sensitive headers such as Authorization, Cookie, and x-api-key are redacted before the plan is returned.
                - Generate code from imported plans with gatling_generate_from_plan only after valid=true.
                """;
    }

    private static String exampleFor(DslLanguage language, Protocol protocol, String pattern) {
        if (protocol != Protocol.HTTP) {
            return "No deep %s example is available in v1. Use capability matrix resources and warnings for this protocol."
                    .formatted(protocol);
        }
        if (!"login-token-orders".equals(pattern)) {
            return "Unknown example pattern: %s. Available patterns: login-token-orders.".formatted(pattern);
        }
        return switch (language) {
            case JAVA -> """
                    Example: JAVA HTTP login-token-orders

                    Recommended MCP workflow:
                    1. gatling_plan_simulation with protocol=HTTP and goal="login, extract JWT token, call /api/orders".
                    2. gatling_validate_simulation_plan.
                    3. gatling_generate_from_plan.
                    4. gatling_validate_method_chain for Simulation -> http -> baseUrl -> scenario -> exec -> http -> post -> check -> jsonPath.saveAs -> get -> header -> check -> status.is.
                    5. gatling_compile_check after writing code into the local project.

                    Code shape:
                    class OrdersSimulation extends Simulation {
                      HttpProtocolBuilder httpProtocol = http.baseUrl("https://api.example.test");

                      ScenarioBuilder scn = scenario("Orders API")
                        .exec(http("POST /login")
                          .post("/login")
                          .body(StringBody("{\\"username\\":\\"#{username}\\",\\"password\\":\\"#{password}\\"}")).asJson()
                          .check(status().is(200), jsonPath("$.token").saveAs("jwtToken")))
                        .exec(http("GET /api/orders")
                          .get("/api/orders")
                          .header("Authorization", "Bearer #{jwtToken}")
                          .check(status().is(200), jsonPath("$.orders[0]").exists()));
                    }
                    """;
            case KOTLIN -> """
                    Example: KOTLIN HTTP login-token-orders

                    Use gatling_plan_simulation -> gatling_validate_simulation_plan -> gatling_generate_from_plan -> gatling_compile_check.
                    The correlation check is jsonPath("$.token").saveAs("jwtToken"), and the follow-up request uses Authorization = Bearer #{jwtToken}.
                    Kotlin status checks should use status().shouldBe(200) in this MCP catalog.
                    """;
            case SCALA -> """
                    Example: SCALA HTTP login-token-orders

                    Use gatling_plan_simulation -> gatling_validate_simulation_plan -> gatling_generate_from_plan -> gatling_compile_check.
                    Code shape uses class OrdersSimulation extends Simulation, .post("/login"), jsonPath("$.token").saveAs("jwtToken"), and .header("Authorization", "Bearer #{jwtToken}").
                    """;
            case JAVASCRIPT, TYPESCRIPT -> """
                    Example: %s HTTP login-token-orders

                    Recommended MCP workflow:
                    gatling_plan_simulation -> gatling_validate_simulation_plan -> gatling_generate_from_plan -> gatling_validate_method_chain -> gatling_compile_check.

                    Code shape:
                    export default simulation((setUp) => {
                      const httpProtocol = http.baseUrl("https://api.example.test");
                      const scn = scenario("Orders API")
                        .exec(http("POST /login").post("/login")
                          .body(StringBody("{\\"username\\":\\"#{username}\\",\\"password\\":\\"#{password}\\"}")).asJson()
                          .check(status().is(200), jsonPath("$.token").saveAs("jwtToken")))
                        .exec(http("GET /api/orders").get("/api/orders")
                          .header("Authorization", "Bearer #{jwtToken}")
                          .check(status().is(200), jsonPath("$.orders[0]").exists()));
                    });
                    """.formatted(language);
        };
    }

    private static String reportAnalysis() {
        return """
                Gatling report and log analysis workflow:
                - Prefer gatling_analyze_report with a local report directory, stats.js, stats.json, or reportContent.
                - gatling_analyze_report returns globalStats, requestStats, topSlowRequests, topFailedRequests, findings, and warnings.
                - Use errorRateThreshold, p95ThresholdMs, and p99ThresholdMs to match project SLOs.
                - gatling_analyze_log detects common runtime failures such as connection refused, timeout, TLS handshake, DNS, OOM, and feeder exhaustion.
                - simulation.log parsing is best-effort because that file is an undocumented Gatling implementation detail.
                - For interrupted runs, generated reports can be incomplete; treat findings as diagnostic signals, not final RCA.
                """;
    }

    private static String mcpInteractions() {
        return """
                MCP interaction features:
                - gatling_interaction_status reports whether the current client supports elicitation, sampling, and progress notifications.
                - Progress notifications are sent only when the MCP call includes _meta.progressToken.
                - gatling_plan_simulation can use elicitation when useElicitation=true and required planning details are blank.
                - gatling_plan_simulation, gatling_analyze_report, and gatling_analyze_log can use sampling when useSampling=true.
                - Sampling output is advisory. Deterministic structured fields such as plan, validation findings, requestStats, and warnings remain authoritative.
                - When a client does not support a P4 feature, tools return a structured interaction status and continue with deterministic fallback behavior.
                """;
    }

    private static BuildTool buildTool(DslLanguage language) {
        return switch (language) {
            case JAVA, KOTLIN -> BuildTool.MAVEN;
            case SCALA -> BuildTool.SBT;
            case JAVASCRIPT, TYPESCRIPT -> BuildTool.NPM;
        };
    }

    private static Optional<String> javaVersion(DslLanguage language) {
        return switch (language) {
            case JAVA, KOTLIN, SCALA -> Optional.of("25");
            case JAVASCRIPT, TYPESCRIPT -> Optional.empty();
        };
    }

    private static Optional<String> nodeVersion(DslLanguage language) {
        return switch (language) {
            case JAVA, KOTLIN, SCALA -> Optional.empty();
            case JAVASCRIPT, TYPESCRIPT -> Optional.of("24");
        };
    }
}
