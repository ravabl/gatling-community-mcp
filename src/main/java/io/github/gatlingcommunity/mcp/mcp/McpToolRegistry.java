package io.github.gatlingcommunity.mcp.mcp;

import io.github.gatlingcommunity.mcp.analysis.SimulationAnalyzer;
import io.github.gatlingcommunity.mcp.authoring.HttpSimulationPlan;
import io.github.gatlingcommunity.mcp.authoring.HttpSimulationPlanGenerator;
import io.github.gatlingcommunity.mcp.authoring.HttpSimulationPlanMapper;
import io.github.gatlingcommunity.mcp.authoring.HttpSimulationPlanValidator;
import io.github.gatlingcommunity.mcp.authoring.HttpSimulationPlanner;
import io.github.gatlingcommunity.mcp.authoring.PlanValidationResult;
import io.github.gatlingcommunity.mcp.compile.CompileCheckService;
import io.github.gatlingcommunity.mcp.compatibility.CapabilityService;
import io.github.gatlingcommunity.mcp.core.error.ToolException;
import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import io.github.gatlingcommunity.mcp.core.model.CommunityPlugin;
import io.github.gatlingcommunity.mcp.core.model.ConfidenceLevel;
import io.github.gatlingcommunity.mcp.core.model.DslLanguage;
import io.github.gatlingcommunity.mcp.core.model.DslMethodCategory;
import io.github.gatlingcommunity.mcp.core.model.GatlingDslMethod;
import io.github.gatlingcommunity.mcp.core.model.PluginContext;
import io.github.gatlingcommunity.mcp.core.model.Protocol;
import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import io.github.gatlingcommunity.mcp.core.model.WarningMessage;
import io.github.gatlingcommunity.mcp.core.observability.AuditLogger;
import io.github.gatlingcommunity.mcp.data.SourceDataRepository;
import io.github.gatlingcommunity.mcp.detect.ProjectContext;
import io.github.gatlingcommunity.mcp.detect.ProjectContextService;
import io.github.gatlingcommunity.mcp.detect.ProjectDetector;
import io.github.gatlingcommunity.mcp.dsl.DslMethodService;
import io.github.gatlingcommunity.mcp.filesystem.PathAccessException;
import io.github.gatlingcommunity.mcp.filesystem.WorkspacePathPolicy;
import io.github.gatlingcommunity.mcp.generation.GenerationRequest;
import io.github.gatlingcommunity.mcp.generation.GeneratedCodeValidationService;
import io.github.gatlingcommunity.mcp.generation.GenerationDecisionService;
import io.github.gatlingcommunity.mcp.generation.PatchGenerationService;
import io.github.gatlingcommunity.mcp.generation.SimulationGenerator;
import io.github.gatlingcommunity.mcp.importing.CodebaseEndpointExtractor;
import io.github.gatlingcommunity.mcp.importing.CodebaseEndpointExtractionResult;
import io.github.gatlingcommunity.mcp.importing.HttpImportOptions;
import io.github.gatlingcommunity.mcp.importing.HttpImportResult;
import io.github.gatlingcommunity.mcp.importing.HttpImportService;
import io.github.gatlingcommunity.mcp.reporting.GatlingReportAnalyzer;
import io.github.gatlingcommunity.mcp.reporting.LogAnalysisOptions;
import io.github.gatlingcommunity.mcp.reporting.ReportAnalysisOptions;
import io.github.gatlingcommunity.mcp.resources.GatlingPromptCatalog;
import io.github.gatlingcommunity.mcp.resources.GatlingResourceCatalog;
import io.github.gatlingcommunity.mcp.resources.GatlingResourceTemplate;
import io.github.gatlingcommunity.mcp.core.safety.InputLimits;
import io.github.gatlingcommunity.mcp.troubleshooting.TroubleshootingService;
import io.github.gatlingcommunity.mcp.validation.FeatureUsageValidator;
import io.github.gatlingcommunity.mcp.validation.SecretMasker;
import io.github.gatlingcommunity.mcp.validation.ValidationFinding;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public final class McpToolRegistry {
    private final SourceDataRepository sourceData;
    private final CapabilityService capabilityService;
    private final SimulationGenerator simulationGenerator;
    private final ProjectDetector projectDetector;
    private final ProjectContextService projectContextService;
    private final WorkspacePathPolicy pathPolicy;
    private final SimulationAnalyzer simulationAnalyzer;
    private final FeatureUsageValidator featureUsageValidator;
    private final GatlingResourceCatalog resourceCatalog;
    private final GatlingPromptCatalog promptCatalog;
    private final ToolDefinitionCatalog toolDefinitionCatalog = new ToolDefinitionCatalog();
    private final ToolResultMapper mapper = new ToolResultMapper();
    private static final SecretMasker SECRET_MASKER = new SecretMasker();
    private final AuditLogger auditLogger = new AuditLogger();
    private final HttpSimulationPlanner planPlanner = new HttpSimulationPlanner();
    private final HttpSimulationPlanMapper planMapper = new HttpSimulationPlanMapper();
    private final HttpSimulationPlanValidator planValidator;
    private final HttpSimulationPlanGenerator planGenerator = new HttpSimulationPlanGenerator();
    private final HttpImportService importService = new HttpImportService();
    private final CodebaseEndpointExtractor codebaseEndpointExtractor = new CodebaseEndpointExtractor();
    private final GatlingReportAnalyzer reportAnalyzer = new GatlingReportAnalyzer();
    private final GenerationDecisionService generationDecisionService = new GenerationDecisionService();
    private final PatchGenerationService patchGenerationService = new PatchGenerationService();
    private final GeneratedCodeValidationService generatedCodeValidationService = new GeneratedCodeValidationService();
    private final TroubleshootingService troubleshootingService = new TroubleshootingService();
    private final DslMethodService dslMethodService;
    private final CompileCheckService compileCheckService = new CompileCheckService();

    private McpToolRegistry(SourceDataRepository sourceData,
                            CapabilityService capabilityService,
                            SimulationGenerator simulationGenerator,
                            ProjectDetector projectDetector,
                            SimulationAnalyzer simulationAnalyzer,
                            FeatureUsageValidator featureUsageValidator,
                            GatlingResourceCatalog resourceCatalog,
                            GatlingPromptCatalog promptCatalog,
                            WorkspacePathPolicy pathPolicy) {
        this.sourceData = sourceData;
        this.capabilityService = capabilityService;
        this.simulationGenerator = simulationGenerator;
        this.projectDetector = projectDetector;
        this.projectContextService = new ProjectContextService();
        this.pathPolicy = pathPolicy == null ? WorkspacePathPolicy.defaults() : pathPolicy;
        this.simulationAnalyzer = simulationAnalyzer;
        this.featureUsageValidator = featureUsageValidator;
        this.resourceCatalog = resourceCatalog;
        this.promptCatalog = promptCatalog;
        this.planValidator = new HttpSimulationPlanValidator(sourceData);
        this.dslMethodService = new DslMethodService(sourceData);
    }

    public static McpToolRegistry createDefault(CapabilityService capabilityService,
                                                SimulationGenerator simulationGenerator,
                                                ProjectDetector projectDetector,
                                                SimulationAnalyzer simulationAnalyzer,
                                                FeatureUsageValidator featureUsageValidator,
                                                GatlingResourceCatalog resourceCatalog,
                                                GatlingPromptCatalog promptCatalog) {
        return createDefault(capabilityService, simulationGenerator, projectDetector, simulationAnalyzer,
                featureUsageValidator, resourceCatalog, promptCatalog, SourceDataRepository.loadDefault());
    }

    public static McpToolRegistry createDefault(CapabilityService capabilityService,
                                                SimulationGenerator simulationGenerator,
                                                ProjectDetector projectDetector,
                                                SimulationAnalyzer simulationAnalyzer,
                                                FeatureUsageValidator featureUsageValidator,
                                                GatlingResourceCatalog resourceCatalog,
                                                GatlingPromptCatalog promptCatalog,
                                                SourceDataRepository sourceData) {
        return createDefault(capabilityService, simulationGenerator, projectDetector, simulationAnalyzer,
                featureUsageValidator, resourceCatalog, promptCatalog, sourceData, WorkspacePathPolicy.defaults());
    }

    public static McpToolRegistry createDefault(CapabilityService capabilityService,
                                                SimulationGenerator simulationGenerator,
                                                ProjectDetector projectDetector,
                                                SimulationAnalyzer simulationAnalyzer,
                                                FeatureUsageValidator featureUsageValidator,
                                                GatlingResourceCatalog resourceCatalog,
                                                GatlingPromptCatalog promptCatalog,
                                                SourceDataRepository sourceData,
                                                WorkspacePathPolicy pathPolicy) {
        return new McpToolRegistry(sourceData, capabilityService, simulationGenerator, projectDetector,
                simulationAnalyzer, featureUsageValidator, resourceCatalog, promptCatalog, pathPolicy);
    }

    public List<String> toolNames() {
        return toolDefinitions().stream().map(ToolDefinition::name).toList();
    }

    public List<ToolDefinition> toolDefinitions() {
        return toolDefinitionCatalog.list();
    }

    public ToolDefinition toolDefinition(String name) {
        return toolDefinitionCatalog.get(name);
    }

    public List<GatlingResourceTemplate> resourceTemplates() {
        return resourceCatalog.listTemplates();
    }

    public List<McpSchema.CompleteReference> completionReferences() {
        return List.of(
                new McpSchema.PromptReference("create_simulation"),
                new McpSchema.PromptReference("create_project"),
                new McpSchema.PromptReference("import_http_plan"),
                new McpSchema.PromptReference("analyze_report"),
                new McpSchema.PromptReference("explain_simulation"),
                new McpSchema.PromptReference("find_simulation_issues"),
                new McpSchema.PromptReference("explain_community_limitations"),
                new McpSchema.ResourceReference("gatling://methods/http/{language}/{version}"),
                new McpSchema.ResourceReference("gatling://examples/{language}/{protocol}/{pattern}"),
                new McpSchema.ResourceReference("gatling://analysis/reports"),
                new McpSchema.ResourceReference("gatling://versions/{version}/features"),
                new McpSchema.ResourceReference("gatling://versions/{version}/breaking-changes")
        );
    }

    public McpSchema.CompleteResult complete(McpSchema.CompleteRequest request) {
        var argument = request.argument();
        var prefix = argument == null || argument.value() == null ? "" : argument.value();
        var name = argument == null ? "" : argument.name();
        var values = completionValues(name).stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                .limit(100)
                .toList();
        return new McpSchema.CompleteResult(
                new McpSchema.CompleteResult.CompleteCompletion(values, values.size(), false)
        );
    }

    public ToolResultMapper.ToolResponse call(String name, Map<String, Object> args) {
        return call(name, args, McpClientInteraction.NOOP);
    }

    public ToolResultMapper.ToolResponse call(String name, Map<String, Object> args, McpClientInteraction interaction) {
        var safeArgs = args == null ? Map.<String, Object>of() : args;
        var clientInteraction = new SamplingBudgetedInteraction(
                interaction == null ? McpClientInteraction.NOOP : interaction);
        auditLogger.event("tool.started", toolAuditFields(name, safeArgs, false, "", ""));
        try {
            InputLimits.requireToolInputWithinLimits(safeArgs);
            var response = switch (name) {
                case "gatling_interaction_status" -> interactionStatus(clientInteraction);
                case "gatling_resolve_capabilities" -> resolveCapabilities(safeArgs);
                case "gatling_detect_project" -> detectProject(safeArgs);
                case "gatling_get_project_context" -> getProjectContext(safeArgs, clientInteraction);
                case "gatling_explain_project_context" -> explainProjectContext(safeArgs);
                case "gatling_resolve_effective_context" -> resolveEffectiveContext(safeArgs);
                case "gatling_generate_simulation" -> generateSimulation(safeArgs);
                case "gatling_analyze_simulation" -> analyzeSimulation(safeArgs);
                case "gatling_validate_feature_usage" -> validateFeatureUsage(safeArgs);
                case "gatling_list_dsl_methods" -> listDslMethods(safeArgs);
                case "gatling_validate_method_chain" -> validateMethodChain(safeArgs);
                case "gatling_explain_dsl_method" -> explainDslMethod(safeArgs);
                case "gatling_find_replacement_method" -> findReplacementMethod(safeArgs);
                case "gatling_explain_version_constraints" -> explainVersionConstraints(safeArgs);
                case "gatling_plan_simulation", "gatling_plan_http_flow" -> planSimulation(safeArgs, clientInteraction);
                case "gatling_plan_correlation" -> planCorrelation(safeArgs);
                case "gatling_plan_checks" -> planChecks(safeArgs);
                case "gatling_plan_injection" -> planInjection();
                case "gatling_plan_assertions" -> planAssertions();
                case "gatling_validate_simulation_plan" -> validateSimulationPlan(safeArgs);
                case "gatling_generate_from_plan" -> generateFromPlan(safeArgs);
                case "gatling_explain_generation_decisions" -> explainGenerationDecisions(safeArgs);
                case "gatling_generate_patch" -> generatePatch(safeArgs);
                case "gatling_validate_generated_code" -> validateGeneratedCode(safeArgs);
                case "gatling_compile_check" -> compileCheck(safeArgs, clientInteraction);
                case "gatling_import_openapi" -> importOpenApi(safeArgs, clientInteraction);
                case "gatling_import_har" -> importHar(safeArgs, clientInteraction);
                case "gatling_import_curl" -> importCurl(safeArgs, clientInteraction);
                case "gatling_import_postman_collection" -> importPostmanCollection(safeArgs, clientInteraction);
                case "gatling_extract_endpoints_from_codebase" -> extractEndpointsFromCodebase(safeArgs);
                case "gatling_analyze_report" -> analyzeReport(safeArgs, clientInteraction);
                case "gatling_analyze_log" -> analyzeLog(safeArgs, clientInteraction);
                case "gatling_explain_errors" -> explainErrors(safeArgs);
                case "gatling_suggest_load_model" -> suggestLoadModel(safeArgs);
                case "gatling_check_feeder_risk" -> checkFeederRisk(safeArgs);
                case "gatling_check_correlation_risk" -> checkCorrelationRisk(safeArgs);
                case "gatling_check_assertion_quality" -> checkAssertionQuality(safeArgs);
                default -> mapper.error("Unknown tool: " + name, toolError(
                        "tool.unknown",
                        "$.name",
                        "Unknown tool: " + name,
                        "Call tools/list and use one of the advertised Gatling MCP tool names."
                ));
            };
            auditLogger.event(response.error() ? "tool.failed" : "tool.finished",
                    toolAuditFields(name, safeArgs, response.error(), response.text(), errorCode(response)));
            return response;
        } catch (ToolException exc) {
            var response = mapper.error(safeMessage(exc.getMessage()), exc.error().toMap());
            auditLogger.event("tool.failed", toolAuditFields(name, safeArgs, true, response.text(), exc.error().code()));
            return response;
        } catch (PathAccessException exc) {
            var response = mapper.error(safeMessage(exc.getMessage()), toolError(
                    exc.code(),
                    exc.path(),
                    exc.getMessage(),
                    exc.suggestion()
            ));
            auditLogger.event("tool.failed", toolAuditFields(name, safeArgs, true, response.text(), exc.code()));
            return response;
        } catch (UncheckedIOException exc) {
            var response = mapper.error(safeMessage(exc.getMessage()), toolError(
                    "io.read.failed",
                    "$",
                    exc.getMessage(),
                    "Verify the path exists, is readable, and stays under the configured workspace roots."
            ));
            auditLogger.event("tool.failed", toolAuditFields(name, safeArgs, true, response.text(), "io.read.failed"));
            return response;
        } catch (IllegalArgumentException exc) {
            var response = mapper.error(safeMessage(exc.getMessage()), toolError(
                    "tool.input.invalid",
                    "$",
                    exc.getMessage(),
                    "Check the tool input schema and pass only supported argument names and values."
            ));
            auditLogger.event("tool.failed", toolAuditFields(name, safeArgs, true, response.text(), "tool.input.invalid"));
            return response;
        }
    }

    public GatlingResourceCatalog resources() {
        return resourceCatalog;
    }

    public GatlingPromptCatalog prompts() {
        return promptCatalog;
    }

    private List<String> completionValues(String argumentName) {
        return switch (argumentName) {
            case "gatlingVersion", "version" -> sourceData.supportedGatlingVersions();
            case "language" -> Arrays.stream(DslLanguage.values()).map(Enum::name).toList();
            case "buildTool" -> Arrays.stream(BuildTool.values())
                    .filter(tool -> tool != BuildTool.UNKNOWN)
                    .map(Enum::name)
                    .toList();
            case "protocol" -> Arrays.stream(Protocol.values()).map(Enum::name).toList();
            case "category" -> Arrays.stream(DslMethodCategory.values()).map(Enum::name).toList();
            case "method", "methodName" -> List.of(
                    "Simulation", "scenario", "exec", "http", "baseUrl", "get", "post",
                    "header", "body.StringBody", "asJson", "check", "status.is",
                    "jsonPath.exists", "jsonPath.saveAs", "rampUsersPerSec",
                    "stressPeakUsers", "global.failedRequests.percent.lt"
            );
            case "pattern" -> List.of("login-token-orders");
            case "plugin" -> Arrays.stream(CommunityPlugin.values()).map(Enum::name).toList();
            case "sourceType" -> List.of("OPENAPI", "HAR", "CURL", "POSTMAN");
            case "contentType" -> List.of("AUTO", "STATS_JS", "STATS_JSON");
            case "logType" -> List.of("AUTO", "SIMULATION_LOG", "RUNTIME_LOG");
            default -> List.of();
        };
    }

    private ToolResultMapper.ToolResponse interactionStatus(McpClientInteraction interaction) {
        var status = interaction.status().toMap();
        return mapper.ok(
                "elicitation=%s sampling=%s progress=%s"
                        .formatted(status.get("elicitation"), status.get("sampling"), status.get("progress")),
                status
        );
    }

    private ToolResultMapper.ToolResponse resolveCapabilities(Map<String, Object> args) {
        var target = target(args);
        var report = capabilityService.resolve(target, protocol(args));
        return mapper.ok(
                "supported=%s generationMode=%s".formatted(report.supported(), report.generationMode()),
                Map.ofEntries(
                        Map.entry("target", targetMap(target)),
                        Map.entry("supported", report.supported()),
                        Map.entry("generationMode", report.generationMode()),
                        Map.entry("features", report.features()),
                        Map.entry("warnings", warningMaps(report.warnings())),
                        Map.entry("metadata", capabilityMetadata(report.metadata()))
                )
        );
    }

    private ToolResultMapper.ToolResponse detectProject(Map<String, Object> args) {
        var result = projectDetector.detect(workspacePath(args, "path"));
        return mapper.ok(
                "buildTool=%s language=%s".formatted(result.buildTool(), result.language()),
                Map.of(
                        "buildTool", result.buildTool().name(),
                        "language", result.language().name(),
                        "gatlingVersion", result.gatlingVersion().orElse("unknown"),
                        "confidence", result.confidence()
                )
        );
    }

    private ToolResultMapper.ToolResponse getProjectContext(Map<String, Object> args, McpClientInteraction interaction) {
        progress(interaction, 0.1, "Scanning project");
        progress(interaction, 0.35, "Resolving build model");
        var context = projectContext(args);
        progress(interaction, 0.75, "Inspecting simulations");
        progress(interaction, 1.0, "Returning context");
        return mapper.ok(
                "buildTool=%s language=%s gatlingVersion=%s simulations=%d"
                        .formatted(context.buildTool(), context.language(), context.detectedGatlingVersion(),
                                context.existingSimulationFiles().size()),
                context.toMap()
        );
    }

    private ToolResultMapper.ToolResponse explainProjectContext(Map<String, Object> args) {
        var context = projectContext(args);
        return mapper.ok(context.explain(), context.toMap());
    }

    private ToolResultMapper.ToolResponse resolveEffectiveContext(Map<String, Object> args) {
        var context = projectContext(args);
        var values = effectiveContext(args, context);
        return mapper.ok(
                "gatlingVersion=%s language=%s buildTool=%s protocol=%s"
                        .formatted(values.get("gatlingVersion"), values.get("language"),
                                values.get("buildTool"), values.get("protocol")),
                values
        );
    }

    private ToolResultMapper.ToolResponse generateSimulation(Map<String, Object> args) {
        var target = target(args);
        var generated = simulationGenerator.generate(new GenerationRequest(
                target,
                protocol(args),
                string(args, "simulationClassName"),
                Map.of()
        ));
        return mapper.ok(
                generated.code(),
                Map.ofEntries(
                        Map.entry("target", targetMap(target)),
                        Map.entry("code", generated.code()),
                        Map.entry("generationMode", generated.generationMode()),
                        Map.entry("warnings", warningMaps(generated.warnings())),
                        Map.entry("plugin", pluginIdentity(generated.metadata()))
                )
        );
    }

    private ToolResultMapper.ToolResponse analyzeSimulation(Map<String, Object> args) {
        var analysis = simulationAnalyzer.analyze(target(args), limitedString(args, "code", InputLimits.CODE_MAX_CHARS));
        return mapper.ok(
                "requests=%d checks=%d findings=%d".formatted(
                        analysis.requestNames().size(),
                        analysis.checks().size(),
                        analysis.validationFindings().size()
                ),
                Map.ofEntries(
                        Map.entry("target", targetMap(analysis.target())),
                        Map.entry("scenarioNames", analysis.scenarioNames()),
                        Map.entry("protocolConfigurations", analysis.protocolConfigurations()),
                        Map.entry("requestNames", analysis.requestNames()),
                        Map.entry("checks", analysis.checks()),
                        Map.entry("feeders", analysis.feeders()),
                        Map.entry("correlations", analysis.correlations()),
                        Map.entry("injectionProfiles", analysis.injectionProfiles()),
                        Map.entry("assertions", analysis.assertions()),
                        Map.entry("validationFindings", validationFindingMaps(analysis.validationFindings()))
                )
        );
    }

    private ToolResultMapper.ToolResponse validateFeatureUsage(Map<String, Object> args) {
        var validation = featureUsageValidator.validate(target(args), limitedString(args, "code", InputLimits.CODE_MAX_CHARS));
        return mapper.ok(
                "findings=%d".formatted(validation.findings().size()),
                Map.of(
                        "target", targetMap(target(args)),
                        "valid", validation.findings().stream().noneMatch(finding -> "error".equals(finding.severity())),
                        "findingCount", validation.findings().size(),
                        "findings", validationFindingMaps(validation.findings())
                )
        );
    }

    private ToolResultMapper.ToolResponse listDslMethods(Map<String, Object> args) {
        var target = targetForDslCatalog(args);
        var protocol = protocol(args);
        var category = optionalString(args, "category")
                .map(value -> DslMethodCategory.valueOf(value.toUpperCase(Locale.ROOT)));
        var matchedLine = sourceData.supportedVersionLineFor(target.gatlingVersion()).orElse("unsupported");
        var methods = sourceData.dslMethods(target, protocol).stream()
                .filter(method -> category.isEmpty() || method.category() == category.orElseThrow())
                .map(McpToolRegistry::methodMap)
                .toList();

        return mapper.ok(
                "methods=%d matchedGatlingLine=%s".formatted(methods.size(), matchedLine),
                Map.of(
                        "gatlingVersion", target.gatlingVersion(),
                        "matchedGatlingLine", matchedLine,
                        "language", target.language().name(),
                        "protocol", protocol.name(),
                        "category", category.map(Enum::name).orElse("ALL"),
                        "count", methods.size(),
                        "methods", methods
                )
        );
    }

    private ToolResultMapper.ToolResponse explainVersionConstraints(Map<String, Object> args) {
        var target = targetForDslCatalog(args);
        var protocol = protocol(args);
        var matchedLine = sourceData.supportedVersionLineFor(target.gatlingVersion()).orElse("unsupported");
        var rules = sourceData.featureRulesFor(target.gatlingVersion());
        var features = rules.stream()
                .filter(rule -> !rule.feature().isBlank())
                .map(rule -> Map.<String, Object>of(
                        "since", rule.since(),
                        "feature", rule.feature(),
                        "message", rule.message()
                ))
                .toList();
        var breakingChanges = rules.stream()
                .filter(rule -> !rule.invalidPattern().isBlank())
                .map(rule -> Map.<String, Object>of(
                        "since", rule.since(),
                        "invalidPattern", rule.invalidPattern(),
                        "replacement", rule.replacement(),
                        "message", rule.message()
                ))
                .toList();
        var dslMethods = sourceData.dslMethods(target, protocol);
        var categories = dslMethods.stream()
                .map(method -> method.category().name())
                .distinct()
                .collect(Collectors.toList());

        return mapper.ok(
                "matchedGatlingLine=%s features=%d breakingChanges=%d dslMethods=%d"
                        .formatted(matchedLine, features.size(), breakingChanges.size(), dslMethods.size()),
                Map.of(
                        "gatlingVersion", target.gatlingVersion(),
                        "matchedGatlingLine", matchedLine,
                        "language", target.language().name(),
                        "buildTool", target.buildTool().name(),
                        "protocol", protocol.name(),
                        "features", features,
                        "breakingChanges", breakingChanges,
                        "dslMethodCount", dslMethods.size(),
                        "dslMethodCategories", categories
                )
        );
    }

    private ToolResultMapper.ToolResponse validateMethodChain(Map<String, Object> args) {
        var target = targetForDslCatalog(args);
        var protocol = protocol(args);
        var methods = stringList(args, "methods");
        var result = dslMethodService.validateChain(target, protocol, methods);
        return mapper.ok(
                "valid=%s methods=%s matchedGatlingLine=%s"
                        .formatted(result.get("valid"), result.get("methodCount"), result.get("matchedGatlingLine")),
                result
        );
    }

    private ToolResultMapper.ToolResponse explainDslMethod(Map<String, Object> args) {
        var target = targetForDslCatalog(args);
        var protocol = protocol(args);
        var method = string(args, "method");
        var result = dslMethodService.explainMethod(target, protocol, method);
        return mapper.ok(
                "found=%s method=%s matchedGatlingLine=%s"
                        .formatted(result.get("found"), method, result.get("matchedGatlingLine")),
                result
        );
    }

    private ToolResultMapper.ToolResponse findReplacementMethod(Map<String, Object> args) {
        var target = targetForDslCatalog(args);
        var protocol = protocol(args);
        var method = string(args, "method");
        var result = dslMethodService.findReplacement(target, protocol, method);
        return mapper.ok(
                "found=%s method=%s replacements=%s"
                        .formatted(result.get("found"), method,
                                ((List<?>) result.getOrDefault("replacements", List.of())).size()),
                result
        );
    }

    private ToolResultMapper.ToolResponse planSimulation(Map<String, Object> args, McpClientInteraction interaction) {
        var target = target(args);
        var protocol = protocol(args);
        if (protocol != Protocol.HTTP) {
            return mapper.error("Structured simulation planning is available for HTTP in v1",
                    Map.of("protocol", protocol.name()));
        }
        progress(interaction, 0.1, "Planning simulation");
        var interactionSummary = interactionSummary(interaction);
        var simulationClassName = optionalString(args, "simulationClassName").orElse("ApiSimulation");
        var goal = optionalString(args, "goal").orElse("");
        var baseUrl = optionalString(args, "baseUrl").orElse("https://example.test");
        if (booleanValue(args, "useElicitation", false) && goal.isBlank()) {
            var elicited = interaction.elicitForm(
                    "Need missing Gatling HTTP simulation details before planning.",
                    elicitationSchema()
            );
            if (elicited.isPresent()) {
                var values = elicited.orElseThrow();
                var elicitedFields = new ArrayList<String>();
                var elicitedGoal = optionalText(values.get("goal"));
                if (elicitedGoal.isPresent()) {
                    goal = elicitedGoal.orElseThrow();
                    elicitedFields.add("goal");
                }
                var elicitedBaseUrl = optionalText(values.get("baseUrl"));
                if (elicitedBaseUrl.isPresent()) {
                    baseUrl = elicitedBaseUrl.orElseThrow();
                    elicitedFields.add("baseUrl");
                }
                var elicitedClassName = optionalText(values.get("simulationClassName"));
                if (elicitedClassName.isPresent()) {
                    simulationClassName = elicitedClassName.orElseThrow();
                    elicitedFields.add("simulationClassName");
                }
                interactionSummary.put("elicitation", "completed");
                interactionSummary.put("elicitedFields", elicitedFields);
            } else {
                interactionSummary.put("elicitation", interaction.status().elicitation() ? "declined-or-empty" : "unsupported");
                interactionSummary.put("elicitedFields", List.of());
            }
        } else {
            interactionSummary.put("elicitation", booleanValue(args, "useElicitation", false) ? "not-needed" : "not-requested");
            interactionSummary.put("elicitedFields", List.of());
        }
        var plan = planPlanner.plan(
                simulationClassName,
                goal,
                baseUrl
        );
        progress(interaction, 0.6, "Validating simulation plan");
        var validation = planValidator.validate(target, plan);
        progress(interaction, 1.0, "Simulation plan ready");
        var response = new LinkedHashMap<>(planResponse(plan, validation));
        if (booleanValue(args, "useSampling", false)) {
            var sampling = samplingSummary(
                    interaction,
                    true,
                    "You are reviewing one local Gatling Community simulation plan. Be concise and operational.",
                    "Review this Gatling simulation plan and return authoring risks and next actions:\n" + response
            );
            interactionSummary.put("sampling", sampling.get("sampling"));
            interactionSummary.put("sampledText", sampling.get("sampledText"));
        }
        response.put("interaction", Map.copyOf(interactionSummary));
        return mapper.ok(
                "valid=%s requests=%d matchedGatlingLine=%s"
                        .formatted(validation.valid(), plan.requests().size(), validation.matchedGatlingLine()),
                response
        );
    }

    private ToolResultMapper.ToolResponse planCorrelation(Map<String, Object> args) {
        var items = planPlanner.planCorrelation(optionalString(args, "goal").orElse(""));
        return mapper.ok("correlations=%d".formatted(items.size()), Map.of("correlations", items));
    }

    private ToolResultMapper.ToolResponse planChecks(Map<String, Object> args) {
        var items = planPlanner.planChecks(optionalString(args, "goal").orElse(""));
        return mapper.ok("checks=%d".formatted(items.size()), Map.of("checks", items));
    }

    private ToolResultMapper.ToolResponse planInjection() {
        return mapper.ok("injectionProfile=rampAndConstant", Map.of("injectionProfile", planPlanner.planInjection()));
    }

    private ToolResultMapper.ToolResponse planAssertions() {
        var assertions = planPlanner.planAssertions();
        return mapper.ok("assertions=%d".formatted(assertions.size()), Map.of("assertions", assertions));
    }

    @SuppressWarnings("unchecked")
    private ToolResultMapper.ToolResponse validateSimulationPlan(Map<String, Object> args) {
        var target = target(args);
        var plan = planMapper.fromMap((Map<String, Object>) args.getOrDefault("plan", Map.of()));
        var validation = planValidator.validate(target, plan);
        return mapper.ok(
                "valid=%s findings=%d matchedGatlingLine=%s"
                        .formatted(validation.valid(), validation.findings().size(), validation.matchedGatlingLine()),
                planResponse(plan, validation)
        );
    }

    @SuppressWarnings("unchecked")
    private ToolResultMapper.ToolResponse generateFromPlan(Map<String, Object> args) {
        var target = target(args);
        var plan = planMapper.fromMap((Map<String, Object>) args.getOrDefault("plan", Map.of()));
        var validation = planValidator.validate(target, plan);
        if (!validation.valid()) {
            return mapper.error(
                    "Simulation plan is invalid; fix validation findings before generation",
                    planResponse(plan, validation)
            );
        }
        return mapper.ok(
                planGenerator.generate(target, plan),
                Map.of(
                        "valid", true,
                        "matchedGatlingLine", validation.matchedGatlingLine(),
                        "language", target.language().name(),
                        "methodRequirements", validation.methodRequirements()
                )
        );
    }

    @SuppressWarnings("unchecked")
    private ToolResultMapper.ToolResponse explainGenerationDecisions(Map<String, Object> args) {
        var target = target(args);
        var plan = planMapper.fromMap((Map<String, Object>) args.getOrDefault("plan", Map.of()));
        var decisions = generationDecisionService.explain(target, plan).stream()
                .map(decision -> decision.toMap())
                .toList();
        return mapper.ok(
                "decisions=%d".formatted(decisions.size()),
                Map.of(
                        "decisionCount", decisions.size(),
                        "decisions", decisions
                )
        );
    }

    @SuppressWarnings("unchecked")
    private ToolResultMapper.ToolResponse generatePatch(Map<String, Object> args) {
        var target = target(args);
        var plan = planMapper.fromMap((Map<String, Object>) args.getOrDefault("plan", Map.of()));
        var validation = planValidator.validate(target, plan);
        if (!validation.valid()) {
            return mapper.error(
                    "Simulation plan is invalid; fix validation findings before patch generation",
                    planResponse(plan, validation)
            );
        }
        var generatedCode = planGenerator.generate(target, plan);
        var targetPath = optionalString(args, "targetPath")
                .orElse(defaultSimulationPath(target.language(), plan.simulationClassName()));
        var warnings = new ArrayList<String>();
        if (targetPath.startsWith("/") || targetPath.contains("..")) {
            warnings.add("patch.path.not_applied: targetPath is used only as diff metadata and no files are written.");
        }
        return mapper.ok(
                "patch targetPath=%s writesFiles=false".formatted(targetPath),
                Map.of(
                        "targetPath", targetPath,
                        "unifiedDiff", patchGenerationService.unifiedDiff(targetPath, generatedCode),
                        "writesFiles", false,
                        "warnings", warnings
                )
        );
    }

    private ToolResultMapper.ToolResponse validateGeneratedCode(Map<String, Object> args) {
        var result = generatedCodeValidationService.validate(
                target(args),
                limitedString(args, "code", InputLimits.CODE_MAX_CHARS)
        );
        return mapper.ok(
                "valid=%s language=%s findings=%d"
                        .formatted(result.get("valid"), result.get("language"),
                                ((List<?>) result.getOrDefault("findings", List.of())).size()),
                result
        );
    }

    private ToolResultMapper.ToolResponse compileCheck(Map<String, Object> args, McpClientInteraction interaction) {
        progress(interaction, 0.1, "Planning command");
        var buildTool = optionalString(args, "buildTool")
                .map(value -> BuildTool.valueOf(value.toUpperCase(Locale.ROOT)))
                .orElse(BuildTool.UNKNOWN);
        if (booleanValue(args, "execute", true)) {
            progress(interaction, 0.35, "Copying project");
            progress(interaction, 0.6, "Running compile");
        }
        var result = compileCheckService.check(
                workspacePath(args, "path"),
                buildTool,
                intValue(args, "timeoutSeconds", 120),
                booleanValue(args, "execute", true)
        );
        progress(interaction, 1.0, "Returning output");
        return mapper.ok(
                "status=%s success=%s executed=%s"
                        .formatted(result.get("status"), result.get("success"), result.get("executed")),
                result
        );
    }

    private ToolResultMapper.ToolResponse importOpenApi(Map<String, Object> args, McpClientInteraction interaction) {
        return importHttpPlan(args, interaction, () -> importService.importOpenApi(
                limitedString(args, "document", InputLimits.IMPORT_DOCUMENT_MAX_CHARS), importOptions(args)));
    }

    private ToolResultMapper.ToolResponse importHar(Map<String, Object> args, McpClientInteraction interaction) {
        return importHttpPlan(args, interaction, () -> importService.importHar(
                limitedString(args, "document", InputLimits.IMPORT_DOCUMENT_MAX_CHARS), importOptions(args)));
    }

    private ToolResultMapper.ToolResponse importCurl(Map<String, Object> args, McpClientInteraction interaction) {
        return importHttpPlan(args, interaction, () -> importService.importCurl(
                limitedString(args, "curl", InputLimits.CURL_MAX_CHARS), importOptions(args)));
    }

    private ToolResultMapper.ToolResponse importPostmanCollection(Map<String, Object> args, McpClientInteraction interaction) {
        return importHttpPlan(args, interaction, () -> importService.importPostmanCollection(
                limitedString(args, "document", InputLimits.IMPORT_DOCUMENT_MAX_CHARS), importOptions(args)));
    }

    private ToolResultMapper.ToolResponse extractEndpointsFromCodebase(Map<String, Object> args) {
        var result = codebaseEndpointExtractor.extract(
                workspacePath(args, "path"),
                optionalString(args, "languageHint").orElse(""),
                intValue(args, "maxFiles", 500)
        );
        return mapper.ok(
                "endpoints=%d scannedFiles=%d matchedFiles=%d warnings=%d"
                        .formatted(result.endpointInventory().endpoints().size(),
                                result.scannedFiles(), result.matchedFiles(), result.warnings().size()),
                endpointExtractionResponse(result)
        );
    }

    private ToolResultMapper.ToolResponse analyzeReport(Map<String, Object> args, McpClientInteraction interaction) {
        try {
            progress(interaction, 0.1, "Analyzing Gatling report");
            var result = optionalLimitedString(args, "reportContent", InputLimits.REPORT_CONTENT_MAX_CHARS)
                    .map(content -> reportAnalyzer.analyzeReportContent(content, reportAnalysisOptions(args)))
                    .orElseGet(() -> reportAnalyzer.analyzeReportPath(
                            workspacePath(args, "reportPath"), reportAnalysisOptions(args)));
            progress(interaction, 0.8, "Preparing report findings");
            var structured = new LinkedHashMap<>(result.toMap());
            structured.put("interaction", samplingSummary(
                    interaction,
                    booleanValue(args, "useSampling", false),
                    "You are reviewing one local Gatling Community report. Be concise and operational.",
                    "Review this Gatling report analysis and return the top risks and next actions:\n" + result.toMap()
            ));
            progress(interaction, 1.0, "Report analysis ready");
            return mapper.ok(
                    "sourceType=%s requests=%d findings=%d warnings=%d"
                            .formatted(result.sourceType(), result.requestStats().size(),
                                    result.findings().size(), result.warnings().size()),
                    structured
            );
        } catch (IllegalArgumentException | UncheckedIOException exc) {
            return mapper.error("Report analysis failed: " + exc.getMessage(), Map.of(
                    "sourceType", "UNKNOWN",
                    "requestCount", 0,
                    "findings", List.of(Map.of(
                            "severity", "error",
                            "code", "report.analysis.failed",
                            "path", "$",
                            "message", exc.getMessage()
                    )),
                    "warnings", List.of()
            ));
        }
    }

    private ToolResultMapper.ToolResponse analyzeLog(Map<String, Object> args, McpClientInteraction interaction) {
        try {
            progress(interaction, 0.1, "Analyzing Gatling log");
            var result = optionalLimitedString(args, "logText", InputLimits.LOG_TEXT_MAX_CHARS)
                    .map(content -> reportAnalyzer.analyzeLogContent(content, logAnalysisOptions(args)))
                    .orElseGet(() -> reportAnalyzer.analyzeLogPath(
                            workspacePath(args, "logPath"), logAnalysisOptions(args)));
            var structured = new LinkedHashMap<>(result.toMap());
            structured.put("interaction", samplingSummary(
                    interaction,
                    booleanValue(args, "useSampling", false),
                    "You are reviewing one local Gatling Community runtime log. Be concise and operational.",
                    "Review this Gatling log analysis and return the likely root-cause checks:\n" + result.toMap()
            ));
            progress(interaction, 1.0, "Log analysis ready");
            return mapper.ok(
                    "sourceType=%s requests=%d findings=%d warnings=%d"
                            .formatted(result.sourceType(), result.requestStats().size(),
                                    result.findings().size(), result.warnings().size()),
                    structured
            );
        } catch (IllegalArgumentException | UncheckedIOException exc) {
            return mapper.error("Log analysis failed: " + exc.getMessage(), Map.of(
                    "sourceType", "UNKNOWN",
                    "requestCount", 0,
                    "findings", List.of(Map.of(
                            "severity", "error",
                            "code", "log.analysis.failed",
                            "path", "$",
                            "message", exc.getMessage()
                    )),
                    "warnings", List.of()
            ));
        }
    }

    private ToolResultMapper.ToolResponse explainErrors(Map<String, Object> args) {
        var content = String.join("\n",
                optionalLimitedString(args, "errorText", InputLimits.LOG_TEXT_MAX_CHARS).orElse(""),
                optionalLimitedString(args, "logText", InputLimits.LOG_TEXT_MAX_CHARS).orElse(""),
                optionalLimitedString(args, "reportContent", InputLimits.REPORT_CONTENT_MAX_CHARS).orElse("")
        );
        var result = troubleshootingService.explainErrors(content);
        return mapper.ok(
                "findings=%d hypotheses=%d confidence=%s"
                        .formatted(result.findings().size(), result.rootCauseHypotheses().size(), result.confidence()),
                result.toMap()
        );
    }

    private ToolResultMapper.ToolResponse suggestLoadModel(Map<String, Object> args) {
        var result = troubleshootingService.suggestLoadModel(
                doubleValue(args, "targetRps", 1.0),
                intValue(args, "expectedUsers", 0),
                intValue(args, "durationSeconds", 300),
                intValue(args, "rampSeconds", 60),
                longValue(args, "p95Ms", 0),
                optionalString(args, "workloadType").orElse("open")
        );
        return mapper.ok(
                "workloadType=%s estimatedConcurrentUsers=%s confidence=%s"
                        .formatted(result.suggestedModel().getOrDefault("workloadType", ""),
                                result.suggestedModel().getOrDefault("estimatedConcurrentUsers", ""),
                                result.confidence()),
                result.toMap()
        );
    }

    @SuppressWarnings("unchecked")
    private ToolResultMapper.ToolResponse checkFeederRisk(Map<String, Object> args) {
        var plan = args.get("plan") instanceof Map<?, ?> raw
                ? planMapper.fromMap((Map<String, Object>) raw)
                : null;
        var result = troubleshootingService.checkFeederRisk(
                plan,
                optionalLimitedString(args, "sampleCsv", InputLimits.IMPORT_DOCUMENT_MAX_CHARS).orElse(""),
                intValue(args, "expectedUsers", 0),
                intValue(args, "durationSeconds", 0)
        );
        return mapper.ok(
                "findings=%d confidence=%s".formatted(result.findings().size(), result.confidence()),
                result.toMap()
        );
    }

    @SuppressWarnings("unchecked")
    private ToolResultMapper.ToolResponse checkCorrelationRisk(Map<String, Object> args) {
        var plan = args.get("plan") instanceof Map<?, ?> raw
                ? planMapper.fromMap((Map<String, Object>) raw)
                : null;
        var result = troubleshootingService.checkCorrelationRisk(
                plan,
                optionalLimitedString(args, "code", InputLimits.CODE_MAX_CHARS).orElse("")
        );
        return mapper.ok(
                "findings=%d confidence=%s".formatted(result.findings().size(), result.confidence()),
                result.toMap()
        );
    }

    @SuppressWarnings("unchecked")
    private ToolResultMapper.ToolResponse checkAssertionQuality(Map<String, Object> args) {
        var plan = args.get("plan") instanceof Map<?, ?> raw
                ? planMapper.fromMap((Map<String, Object>) raw)
                : null;
        var result = troubleshootingService.checkAssertionQuality(
                plan,
                optionalLimitedString(args, "code", InputLimits.CODE_MAX_CHARS).orElse("")
        );
        return mapper.ok(
                "findings=%d confidence=%s".formatted(result.findings().size(), result.confidence()),
                result.toMap()
        );
    }

    private ToolResultMapper.ToolResponse importHttpPlan(Map<String, Object> args,
                                                         McpClientInteraction interaction,
                                                         ImportAction action) {
        progress(interaction, 0.1, "Reading input");
        var target = target(args);
        var protocol = protocol(args);
        if (protocol != Protocol.HTTP) {
            return mapper.error("HTTP imports can only create HTTP simulation plans in v1",
                    Map.of("protocol", protocol.name()));
        }
        try {
            progress(interaction, 0.35, "Extracting endpoints");
            var imported = action.importPlan();
            progress(interaction, 0.65, "Building plan");
            var validation = planValidator.validate(target, imported.plan());
            progress(interaction, 0.85, "Validating plan");
            progress(interaction, 1.0, "Import ready");
            return mapper.ok(
                    "sourceType=%s valid=%s requests=%d warnings=%d matchedGatlingLine=%s"
                            .formatted(imported.sourceType(), validation.valid(), imported.requestCount(),
                                    imported.warnings().size(), validation.matchedGatlingLine()),
                    importResponse(imported, validation)
            );
        } catch (IllegalArgumentException exc) {
            return mapper.error("Import failed: " + exc.getMessage(), Map.of(
                    "valid", false,
                    "error", exc.getMessage()
            ));
        }
    }

    private Map<String, Object> planResponse(HttpSimulationPlan plan, PlanValidationResult validation) {
        return Map.of(
                "valid", validation.valid(),
                "matchedGatlingLine", validation.matchedGatlingLine(),
                "plan", plan.toMap(),
                "findings", planMapper.findingsToMaps(validation.findings()),
                "methodRequirements", validation.methodRequirements()
        );
    }

    private Map<String, Object> importResponse(HttpImportResult imported, PlanValidationResult validation) {
        var values = new java.util.LinkedHashMap<String, Object>();
        values.put("sourceType", imported.sourceType());
        values.put("sourceVersion", imported.sourceVersion());
        values.put("requestCount", imported.requestCount());
        values.put("endpointInventory", imported.endpointInventory().toMap());
        values.put("feederCandidates", imported.feederCandidates());
        values.put("correlationCandidates", imported.correlationCandidates());
        values.put("correlationUsages", imported.correlationUsages());
        values.put("warnings", imported.warnings().stream().map(warning -> warning.toMap()).toList());
        values.putAll(planResponse(imported.plan(), validation));
        return Map.copyOf(values);
    }

    private Map<String, Object> endpointExtractionResponse(CodebaseEndpointExtractionResult result) {
        return Map.of(
                "endpointInventory", result.endpointInventory().toMap(),
                "warnings", result.warnings().stream().map(warning -> warning.toMap()).toList(),
                "scannedFiles", result.scannedFiles(),
                "matchedFiles", result.matchedFiles()
        );
    }

    private ProjectContext projectContext(Map<String, Object> args) {
        return projectContextService.inspect(workspacePath(args, "path"));
    }

    private Path workspacePath(Map<String, Object> args, String key) {
        return pathPolicy.resolve(string(args, key), key);
    }

    private Map<String, Object> effectiveContext(Map<String, Object> args, ProjectContext context) {
        var warnings = new ArrayList<String>();
        var sources = new LinkedHashMap<String, Object>();
        var gatlingVersion = optionalString(args, "gatlingVersion").orElseGet(() -> {
            sources.put("gatlingVersion", "project");
            return context.detectedGatlingVersion();
        });
        if (optionalString(args, "gatlingVersion").isPresent()) {
            sources.put("gatlingVersion", "explicit");
        }
        if ("unknown".equals(gatlingVersion)) {
            gatlingVersion = sourceData.supportedGatlingVersions().getLast();
            warnings.add("effective_context.gatling_version.defaulted_latest_supported");
            sources.put("gatlingVersion", "default");
        }

        var language = optionalString(args, "language")
                .map(value -> {
                    sources.put("language", "explicit");
                    return DslLanguage.valueOf(value.toUpperCase(Locale.ROOT));
                })
                .orElseGet(() -> {
                    sources.put("language", "project");
                    return context.language();
                });
        var buildTool = optionalString(args, "buildTool")
                .map(value -> {
                    sources.put("buildTool", "explicit");
                    return BuildTool.valueOf(value.toUpperCase(Locale.ROOT));
                })
                .orElseGet(() -> {
                    sources.put("buildTool", "project");
                    return context.buildTool();
                });
        var protocol = optionalString(args, "protocol")
                .map(value -> {
                    sources.put("protocol", "explicit");
                    return Protocol.valueOf(value.toUpperCase(Locale.ROOT));
                })
                .orElseGet(() -> {
                    sources.put("protocol", "default");
                    return Protocol.HTTP;
                });
        var javaVersion = optionalString(args, "javaVersion").orElseGet(() -> {
            sources.put("javaVersion", "project");
            return context.javaVersion();
        });
        if (optionalString(args, "javaVersion").isPresent()) {
            sources.put("javaVersion", "explicit");
        }
        var nodeVersion = optionalString(args, "nodeVersion").orElseGet(() -> {
            sources.put("nodeVersion", "project");
            return context.nodeVersion();
        });
        if (optionalString(args, "nodeVersion").isPresent()) {
            sources.put("nodeVersion", "explicit");
        }

        var values = new LinkedHashMap<String, Object>();
        values.put("root", context.root().toString());
        values.put("gatlingVersion", gatlingVersion);
        values.put("language", language.name());
        values.put("buildTool", buildTool.name());
        values.put("protocol", protocol.name());
        values.put("javaVersion", javaVersion);
        values.put("nodeVersion", nodeVersion);
        values.put("plugin", optionalString(args, "plugin").orElse(""));
        values.put("sources", sources);
        values.put("warnings", warnings);
        values.put("projectContext", context.toMap());
        return Map.copyOf(values);
    }

    private static Map<String, Object> toolError(String code, String path, String message, String suggestion) {
        return Map.of(
                "code", code,
                "severity", "error",
                "path", path,
                "message", safeMessage(message),
                "suggestion", safeMessage(suggestion)
        );
    }

    private static String safeMessage(String message) {
        return SECRET_MASKER.mask(message == null ? "" : message);
    }

    private static Map<String, Object> toolAuditFields(String name,
                                                       Map<String, Object> args,
                                                       boolean error,
                                                       String resultText,
                                                       String errorCode) {
        var safeArgs = args == null ? Map.<String, Object>of() : args;
        return Map.of(
                "tool", name == null || name.isBlank() ? "unknown" : name,
                "argumentCount", safeArgs.size(),
                "argumentKeys", safeArgs.keySet().stream().sorted().toList(),
                "argumentTextChars", totalStringChars(safeArgs),
                "error", error,
                "errorCode", errorCode == null ? "" : errorCode,
                "resultTextChars", resultText == null ? 0 : resultText.length(),
                "resultTextSample", error ? clipAuditText(safeMessage(resultText)) : ""
        );
    }

    private static String errorCode(ToolResultMapper.ToolResponse response) {
        if (response == null || !response.error()) {
            return "";
        }
        return String.valueOf(response.structured().getOrDefault("code", ""));
    }

    private static int totalStringChars(Object value) {
        if (value instanceof String text) {
            return text.length();
        }
        if (value instanceof Map<?, ?> map) {
            var total = 0;
            for (var entry : map.entrySet()) {
                total += String.valueOf(entry.getKey()).length();
                total += totalStringChars(entry.getValue());
            }
            return total;
        }
        if (value instanceof List<?> list) {
            return list.stream().mapToInt(McpToolRegistry::totalStringChars).sum();
        }
        return 0;
    }

    private static String clipAuditText(String value) {
        var text = value == null ? "" : value;
        return text.length() <= 240 ? text : text.substring(0, 240);
    }

    private static Map<String, Object> interactionSummary(McpClientInteraction interaction) {
        var status = interaction.status();
        var values = new LinkedHashMap<String, Object>();
        values.put("clientName", status.clientName() == null || status.clientName().isBlank() ? "unknown" : status.clientName());
        values.put("clientVersion", status.clientVersion() == null || status.clientVersion().isBlank() ? "unknown" : status.clientVersion());
        values.put("elicitation", status.elicitation() ? "supported" : "unsupported");
        values.put("sampling", "not-requested");
        values.put("sampledText", "");
        values.put("progress", status.progress());
        values.put("progressTokenPresent", status.progress());
        return values;
    }

    private static void progress(McpClientInteraction interaction, double value, String message) {
        if (interaction != null && interaction.policy().allowProgress()) {
            interaction.progress(value, 1.0, message);
        }
    }

    private static Map<String, Object> samplingSummary(McpClientInteraction interaction,
                                                       boolean requested,
                                                       String systemPrompt,
                                                       String userPrompt) {
        var values = interactionSummary(interaction);
        if (!requested) {
            values.put("sampling", "not-requested");
            values.put("sampledText", "");
            return Map.copyOf(values);
        }
        if (!interaction.policy().allowSampling() || !interaction.status().sampling()) {
            values.put("sampling", "unsupported");
            values.put("sampledText", "");
            return Map.copyOf(values);
        }
        if (interaction.policy().maxSamplingCallsPerTool() == 0) {
            values.put("sampling", "budget-exhausted");
            values.put("sampledText", "");
            return Map.copyOf(values);
        }
        var sampled = interaction.sampleText(systemPrompt, SECRET_MASKER.mask(userPrompt), 500);
        if (sampled.isPresent()) {
            values.put("sampling", "completed");
            values.put("sampledText", sampled.orElseThrow());
        } else {
            values.put("sampling", "empty");
            values.put("sampledText", "");
        }
        return Map.copyOf(values);
    }

    private static Map<String, Object> elicitationSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("goal"),
                "properties", Map.of(
                        "goal", Map.of("type", "string", "description", "Gatling HTTP flow goal"),
                        "baseUrl", Map.of("type", "string", "description", "HTTP base URL"),
                        "simulationClassName", Map.of("type", "string", "description", "Simulation class name")
                ),
                "additionalProperties", false
        );
    }

    private static Map<String, Object> methodMap(GatlingDslMethod method) {
        return Map.of(
                "name", method.name(),
                "category", method.category().name(),
                "since", method.since(),
                "until", method.until(),
                "callTemplate", method.callTemplate(),
                "sourceUrl", method.sourceUrl(),
                "confidenceLevel", method.confidenceLevel().name()
        );
    }

    private static String defaultSimulationPath(DslLanguage language, String simulationClassName) {
        return switch (language) {
            case JAVA -> "src/gatling/java/%s.java".formatted(simulationClassName);
            case KOTLIN -> "src/gatling/kotlin/%s.kt".formatted(simulationClassName);
            case SCALA -> "src/gatling/scala/%s.scala".formatted(simulationClassName);
            case JAVASCRIPT -> "src/gatling/js/%s.gatling.js".formatted(simulationClassName);
            case TYPESCRIPT -> "src/gatling/ts/%s.gatling.ts".formatted(simulationClassName);
        };
    }

    private static String string(Map<String, Object> args, String key) {
        var value = args.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Missing string argument: " + key);
        }
        return text;
    }

    private static String limitedString(Map<String, Object> args, String key, int maxChars) {
        return InputLimits.requireMaxChars(string(args, key), maxChars, "$." + key);
    }

    private static Optional<String> optionalString(Map<String, Object> args, String key) {
        var value = args.get(key);
        return optionalText(value);
    }

    private static Optional<String> optionalLimitedString(Map<String, Object> args, String key, int maxChars) {
        return optionalString(args, key)
                .map(value -> InputLimits.requireMaxChars(value, maxChars, "$." + key));
    }

    private static Optional<String> optionalText(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return Optional.of(text);
        }
        return Optional.empty();
    }

    private static List<String> stringList(Map<String, Object> args, String key) {
        var value = args.get(key);
        if (!(value instanceof List<?> raw) || raw.isEmpty()) {
            throw new IllegalArgumentException("Missing non-empty string array argument: " + key);
        }
        return raw.stream()
                .map(item -> {
                    if (!(item instanceof String text) || text.isBlank()) {
                        throw new IllegalArgumentException("Argument " + key + " must contain only non-empty strings");
                    }
                    return text;
                })
                .toList();
    }

    private static boolean booleanValue(Map<String, Object> args, String key, boolean fallback) {
        var value = args.get(key);
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return fallback;
    }

    private static HttpImportOptions importOptions(Map<String, Object> args) {
        return new HttpImportOptions(
                optionalString(args, "simulationClassName").orElse(""),
                optionalString(args, "scenarioName").orElse(""),
                optionalString(args, "baseUrl").orElse("")
        );
    }

    private static ReportAnalysisOptions reportAnalysisOptions(Map<String, Object> args) {
        return new ReportAnalysisOptions(
                optionalString(args, "contentType").orElse("AUTO"),
                doubleValue(args, "errorRateThreshold", 1.0),
                longValue(args, "p95ThresholdMs", 1_000),
                longValue(args, "p99ThresholdMs", 2_000)
        );
    }

    private static LogAnalysisOptions logAnalysisOptions(Map<String, Object> args) {
        return new LogAnalysisOptions(
                optionalString(args, "logType").orElse("AUTO"),
                doubleValue(args, "errorRateThreshold", 1.0),
                longValue(args, "p95ThresholdMs", 1_000),
                longValue(args, "p99ThresholdMs", 2_000)
        );
    }

    private static double doubleValue(Map<String, Object> args, String key, double fallback) {
        var value = args.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.parseDouble(text);
        }
        return fallback;
    }

    private static long longValue(Map<String, Object> args, String key, long fallback) {
        var value = args.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return fallback;
    }

    private static int intValue(Map<String, Object> args, String key, int fallback) {
        var value = args.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return fallback;
    }

    private static Protocol protocol(Map<String, Object> args) {
        return Protocol.valueOf(string(args, "protocol").toUpperCase(Locale.ROOT));
    }

    private static TargetContext target(Map<String, Object> args) {
        return new TargetContext(
                string(args, "gatlingVersion"),
                "community",
                DslLanguage.valueOf(string(args, "language").toUpperCase(Locale.ROOT)),
                BuildTool.valueOf(string(args, "buildTool").toUpperCase(Locale.ROOT)),
                optionalString(args, "javaVersion"),
                optionalString(args, "nodeVersion"),
                pluginContext(args)
        );
    }

    private static TargetContext targetForDslCatalog(Map<String, Object> args) {
        var language = DslLanguage.valueOf(string(args, "language").toUpperCase(Locale.ROOT));
        return new TargetContext(
                string(args, "gatlingVersion"),
                "community",
                language,
                optionalString(args, "buildTool")
                        .map(value -> BuildTool.valueOf(value.toUpperCase(Locale.ROOT)))
                        .orElse(defaultBuildTool(language)),
                optionalString(args, "javaVersion"),
                optionalString(args, "nodeVersion"),
                Optional.empty()
        );
    }

    private static BuildTool defaultBuildTool(DslLanguage language) {
        return switch (language) {
            case JAVA, KOTLIN -> BuildTool.MAVEN;
            case SCALA -> BuildTool.SBT;
            case JAVASCRIPT, TYPESCRIPT -> BuildTool.NPM;
        };
    }

    private static Optional<PluginContext> pluginContext(Map<String, Object> args) {
        var plugin = optionalString(args, "plugin");
        if (plugin.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PluginContext(
                CommunityPlugin.valueOf(plugin.orElseThrow().toUpperCase(Locale.ROOT)),
                optionalString(args, "pluginVersion").orElse(""),
                optionalString(args, "confidenceLevel")
                        .map(value -> ConfidenceLevel.valueOf(value.toUpperCase(Locale.ROOT)))
                        .orElse(ConfidenceLevel.PLUGIN_README)
        ));
    }

    private static Map<String, Object> targetMap(TargetContext target) {
        var plugin = target.pluginContext();
        return Map.of(
                "gatlingVersion", target.gatlingVersion(),
                "edition", target.edition(),
                "language", target.language().name(),
                "buildTool", target.buildTool().name(),
                "javaVersion", target.javaVersion().orElse(""),
                "nodeVersion", target.nodeVersion().orElse(""),
                "plugin", plugin.map(value -> value.plugin().name()).orElse(""),
                "pluginVersion", plugin.map(PluginContext::pluginVersion).orElse("")
        );
    }

    private static List<Map<String, Object>> warningMaps(List<WarningMessage> warnings) {
        return warnings.stream()
                .map(warning -> Map.<String, Object>of(
                        "code", warning.code(),
                        "message", warning.message(),
                        "source", warning.source()
                ))
                .toList();
    }

    private static List<Map<String, Object>> validationFindingMaps(List<ValidationFinding> findings) {
        return findings.stream()
                .map(finding -> Map.<String, Object>of(
                        "code", finding.code(),
                        "severity", finding.severity(),
                        "message", finding.message(),
                        "evidence", finding.evidence()
                ))
                .toList();
    }

    private static Map<String, Object> capabilityMetadata(Map<String, Object> metadata) {
        return Map.ofEntries(
                Map.entry("protocol", metadata.getOrDefault("protocol", "")),
                Map.entry("matchedGatlingLine", metadata.getOrDefault("matchedGatlingLine", "")),
                Map.entry("compatibilityPolicy", metadata.getOrDefault("compatibilityPolicy", "")),
                Map.entry("dslMethodCount", metadata.getOrDefault("dslMethodCount", 0)),
                Map.entry("dslMethodCategories", metadata.getOrDefault("dslMethodCategories", List.of())),
                Map.entry("plugin", metadata.getOrDefault("plugin", "")),
                Map.entry("pluginVersion", metadata.getOrDefault("pluginVersion", "")),
                Map.entry("verifiedAgainstGatlingVersion", metadata.getOrDefault("verifiedAgainstGatlingVersion", "")),
                Map.entry("minimumJavaVersion", metadata.getOrDefault("minimumJavaVersion", "")),
                Map.entry("confidence", metadata.getOrDefault("confidence", "")),
                Map.entry("repositoryUrl", metadata.getOrDefault("repositoryUrl", "")),
                Map.entry("sourceUrl", metadata.getOrDefault("sourceUrl", ""))
        );
    }

    private static Map<String, Object> pluginIdentity(Map<String, Object> metadata) {
        return Map.of(
                "selected", "strict-verified".equals(metadata.get("compatibilityPolicy")),
                "name", metadata.getOrDefault("plugin", ""),
                "version", metadata.getOrDefault("pluginVersion", ""),
                "verifiedAgainstGatlingVersion", metadata.getOrDefault("verifiedAgainstGatlingVersion", ""),
                "minimumJavaVersion", metadata.getOrDefault("minimumJavaVersion", ""),
                "confidence", metadata.getOrDefault("confidence", ""),
                "sourceUrl", metadata.getOrDefault("sourceUrl", "")
        );
    }

    @FunctionalInterface
    private interface ImportAction {
        HttpImportResult importPlan();
    }

    private static final class SamplingBudgetedInteraction implements McpClientInteraction {
        private final McpClientInteraction delegate;
        private final AtomicInteger samplingCalls = new AtomicInteger();

        private SamplingBudgetedInteraction(McpClientInteraction delegate) {
            this.delegate = delegate;
        }

        @Override
        public ClientInteractionStatus status() {
            return delegate.status();
        }

        @Override
        public InteractionPolicy policy() {
            return delegate.policy();
        }

        @Override
        public void progress(double progress, Double total, String message) {
            delegate.progress(progress, total, message);
        }

        @Override
        public Optional<Map<String, Object>> elicitForm(String message, Map<String, Object> requestedSchema) {
            return delegate.elicitForm(message, requestedSchema);
        }

        @Override
        public Optional<String> sampleText(String systemPrompt, String userPrompt, int maxTokens) {
            if (!policy().allowSampling()
                    || samplingCalls.incrementAndGet() > policy().maxSamplingCallsPerTool()) {
                return Optional.empty();
            }
            return delegate.sampleText(systemPrompt, userPrompt, maxTokens);
        }
    }
}
