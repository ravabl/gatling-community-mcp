package io.github.gatlingcommunity.mcp.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gatlingcommunity.mcp.core.model.CommunityPlugin;
import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import io.github.gatlingcommunity.mcp.core.model.ConfidenceLevel;
import io.github.gatlingcommunity.mcp.core.model.DslLanguage;
import io.github.gatlingcommunity.mcp.core.model.DslMethodCategory;
import io.github.gatlingcommunity.mcp.core.model.GatlingDslSemanticRule;
import io.github.gatlingcommunity.mcp.core.model.GatlingVersion;
import io.github.gatlingcommunity.mcp.core.model.GatlingDslMethod;
import io.github.gatlingcommunity.mcp.core.model.Protocol;
import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class SourceDataRepository {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JsonNode featureMatrix;
    private final JsonNode pluginMatrix;
    private final JsonNode dslMethodMatrix;
    private final JsonNode dslSemantics;

    private SourceDataRepository(JsonNode featureMatrix,
                                 JsonNode pluginMatrix,
                                 JsonNode dslMethodMatrix,
                                 JsonNode dslSemantics) {
        this.featureMatrix = featureMatrix;
        this.pluginMatrix = pluginMatrix;
        this.dslMethodMatrix = dslMethodMatrix;
        this.dslSemantics = dslSemantics;
    }

    public static SourceDataRepository loadDefault() {
        return new SourceDataRepository(
                readJson("/data/gatling-feature-matrix.json"),
                readJson("/data/gatling-community-plugin-matrix.json"),
                readJson("/data/gatling-dsl-method-matrix.json"),
                readJson("/data/gatling-dsl-semantics.json")
        );
    }

    public List<String> supportedGatlingVersions() {
        var versions = new ArrayList<String>();
        featureMatrix.withArray("supportedVersions").forEach(node -> versions.add(node.asText()));
        return List.copyOf(versions);
    }

    public List<String> knownGatlingVersions() {
        var versions = new ArrayList<String>();
        featureMatrix.withArray("knownVersions").forEach(node -> versions.add(node.asText()));
        return List.copyOf(versions);
    }

    public Optional<String> supportedVersionLineFor(String gatlingVersion) {
        return versionLineFor(gatlingVersion, supportedGatlingVersions());
    }

    public Optional<DslLanguageSupport> dslLanguageSupport(DslLanguage language) {
        for (var node : dslMethodMatrix.withArray("languageSupport")) {
            var languages = parseLanguages(node.withArray("languages"));
            if (languages.contains(language)) {
                return Optional.of(new DslLanguageSupport(
                        languages,
                        node.path("since").asText(),
                        node.path("warningCode").asText(),
                        node.path("warningMessage").asText(),
                        node.path("sourceUrl").asText()
                ));
            }
        }
        return Optional.empty();
    }

    public List<FeatureRule> featureRulesFor(String gatlingVersion) {
        var rules = new ArrayList<FeatureRule>();
        featureMatrix.withArray("rules").forEach(node -> {
            if (versionAtLeast(gatlingVersion, node.path("since").asText())) {
                rules.add(new FeatureRule(
                        node.path("since").asText(),
                        node.path("feature").asText(""),
                        node.path("invalidPattern").asText(""),
                        node.path("replacement").asText(""),
                        node.path("message").asText("")
                ));
            }
        });
        return List.copyOf(rules);
    }

    public ProtocolSupport protocolSupport(Protocol protocol) {
        for (var node : featureMatrix.withArray("protocols")) {
            if (node.path("protocol").asText().equals(protocol.name())) {
                return new ProtocolSupport(protocol, node.path("generationMode").asText());
            }
        }
        return new ProtocolSupport(protocol, "unsupported");
    }

    public List<GatlingDslMethod> dslMethods(TargetContext target, Protocol protocol) {
        if (supportedVersionLineFor(target.gatlingVersion()).isEmpty()
                || !isDslLanguageSupported(target)) {
            return List.of();
        }
        var methods = new ArrayList<GatlingDslMethod>();
        dslMethodMatrix.withArray("methods").forEach(node -> {
            var methodProtocol = Protocol.valueOf(node.path("protocol").asText());
            if (methodProtocol != protocol) {
                return;
            }
            var languages = parseLanguages(node.withArray("languages"));
            if (!languages.contains(target.language())) {
                return;
            }
            var since = node.path("since").asText("3.7");
            var until = node.path("until").asText("");
            if (!versionAtLeast(target.gatlingVersion(), since)) {
                return;
            }
            if (!until.isBlank() && versionAtLeast(target.gatlingVersion(), until)) {
                return;
            }
            methods.add(new GatlingDslMethod(
                    node.path("name").asText(),
                    DslMethodCategory.valueOf(node.path("category").asText()),
                    methodProtocol,
                    target.language(),
                    since,
                    until,
                    callTemplate(node, target.language()),
                    node.path("sourceUrl").asText(),
                    ConfidenceLevel.valueOf(node.path("confidence").asText())
            ));
        });
        methods.sort(Comparator.comparing(GatlingDslMethod::category).thenComparing(GatlingDslMethod::name));
        return List.copyOf(methods);
    }

    public PluginSupport pluginSupport(
            CommunityPlugin plugin,
            String gatlingVersion,
            DslLanguage language,
            BuildTool buildTool,
            String requestedPluginVersion
    ) {
        for (var node : pluginMatrix.withArray("plugins")) {
            if (!node.path("plugin").asText().equals(plugin.name())) {
                continue;
            }
            var requestedVersion = requestedPluginVersion == null ? "" : requestedPluginVersion.strip();
            PluginSupport nearest = null;
            for (var target : node.withArray("verifiedTargets")) {
                var pluginVersion = target.path("pluginVersion").asText();
                var gatlingLine = target.path("gatlingVersionLine").asText();
                var languageBuildTools = target.path("languageBuildTools");
                var languages = new ArrayList<DslLanguage>();
                languageBuildTools.fieldNames().forEachRemaining(name -> languages.add(DslLanguage.valueOf(name)));
                var buildTools = languageBuildTools.has(language.name())
                        ? parseBuildTools(languageBuildTools.withArray(language.name()))
                        : List.<BuildTool>of();
                var pluginVersionMatches = requestedVersion.isBlank() || requestedVersion.equals(pluginVersion);
                var gatlingMatches = versionLineFor(gatlingVersion, List.of(gatlingLine)).isPresent();
                var languageMatches = languages.contains(language);
                var buildToolMatches = buildTools.contains(buildTool);
                var confidence = ConfidenceLevel.valueOf(target.path("confidence").asText());
                var support = new PluginSupport(
                        plugin,
                        pluginVersionMatches && gatlingMatches && languageMatches && buildToolMatches
                                && confidence != ConfidenceLevel.INFERRED,
                        pluginVersion,
                        gatlingLine,
                        target.path("verifiedAgainstGatlingVersion").asText(),
                        List.copyOf(languages),
                        buildTools,
                        target.path("minimumJavaVersion").asText(),
                        confidence,
                        node.path("repositoryUrl").asText(),
                        target.path("sourceUrl").asText(),
                        compatibilityReason(pluginVersionMatches, gatlingMatches, languageMatches, buildToolMatches)
                );
                if (support.supported()) {
                    return support;
                }
                if (nearest == null || gatlingMatches || pluginVersion.equals(requestedVersion)) {
                    nearest = support;
                }
            }
            return nearest == null ? unsupportedPlugin(plugin, node.path("repositoryUrl").asText()) : nearest;
        }
        return unsupportedPlugin(plugin, "");
    }

    private static List<BuildTool> parseBuildTools(JsonNode array) {
        var values = new ArrayList<BuildTool>();
        array.forEach(node -> values.add(BuildTool.valueOf(node.asText())));
        return List.copyOf(values);
    }

    private static String compatibilityReason(
            boolean pluginVersionMatches,
            boolean gatlingMatches,
            boolean languageMatches,
            boolean buildToolMatches
    ) {
        if (!languageMatches) {
            return "plugin.dsl.unsupported";
        }
        if (!buildToolMatches) {
            return "plugin.build-tool.unsupported";
        }
        if (!pluginVersionMatches || !gatlingMatches) {
            return "plugin.compatibility.unverified";
        }
        return "verified";
    }

    private static PluginSupport unsupportedPlugin(CommunityPlugin plugin, String repositoryUrl) {
        return new PluginSupport(plugin, false, "", "", "", List.of(), List.of(), "",
                ConfidenceLevel.INFERRED, repositoryUrl, repositoryUrl, "plugin.compatibility.unverified");
    }

    public Optional<GatlingDslSemanticRule> semanticRule(GatlingDslMethod method) {
        var defaults = dslSemantics.path("defaultsByCategory").path(method.category().name());
        if (!defaults.isObject()) {
            return Optional.empty();
        }
        var override = semanticOverride(method.name());
        var syntax = new LinkedHashMap<DslLanguage, String>();
        var methodNode = dslMethodNode(method).orElseGet(MAPPER::createObjectNode);
        for (var language : DslLanguage.values()) {
            syntax.put(language, semanticLanguageSyntax(override, methodNode, language, method.callTemplate()));
        }
        return Optional.of(new GatlingDslSemanticRule(
                method.name(),
                method.category(),
                method.protocol(),
                semanticText(defaults, override, "allowedParentContext"),
                semanticText(defaults, override, "returnType"),
                semanticText(defaults, override, "chainType"),
                semanticText(defaults, override, "requiredPrecedingMethod"),
                semanticStrings(defaults, override, "incompatibleMethods"),
                Map.copyOf(syntax),
                override.path("exampleSnippet").asText(method.callTemplate()),
                semanticText(defaults, override, "compileRiskNotes")
        ));
    }

    private static JsonNode readJson(String path) {
        try (InputStream input = SourceDataRepository.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing source data resource: " + path);
            }
            return MAPPER.readTree(input);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load source data resource: " + path, e);
        }
    }

    private static List<String> parseStrings(JsonNode array) {
        var values = new ArrayList<String>();
        array.forEach(node -> values.add(node.asText()));
        return List.copyOf(values);
    }

    private JsonNode semanticOverride(String methodName) {
        for (var node : dslSemantics.withArray("overrides")) {
            if (node.path("method").asText().equals(methodName)) {
                return node;
            }
        }
        return MAPPER.createObjectNode();
    }

    private Optional<JsonNode> dslMethodNode(GatlingDslMethod method) {
        for (var node : dslMethodMatrix.withArray("methods")) {
            if (node.path("name").asText().equals(method.name())
                    && node.path("protocol").asText().equals(method.protocol().name())) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    private static String semanticText(JsonNode defaults, JsonNode override, String field) {
        var overridden = override.path(field);
        if (overridden.isTextual()) {
            return overridden.asText();
        }
        return defaults.path(field).asText("");
    }

    private static List<String> semanticStrings(JsonNode defaults, JsonNode override, String field) {
        var array = override.has(field) ? override.withArray(field) : defaults.withArray(field);
        return parseStrings(array);
    }

    private static String semanticLanguageSyntax(JsonNode override,
                                                 JsonNode methodNode,
                                                 DslLanguage language,
                                                 String fallback) {
        var overrideSyntax = override.path("languageSyntax");
        if (overrideSyntax.isObject() && overrideSyntax.path(language.name()).isTextual()) {
            return overrideSyntax.path(language.name()).asText();
        }
        var languageTemplates = methodNode.path("languageTemplates");
        if (languageTemplates.isObject() && languageTemplates.path(language.name()).isTextual()) {
            return languageTemplates.path(language.name()).asText();
        }
        return methodNode.path("callTemplate").asText(fallback);
    }

    private static List<DslLanguage> parseLanguages(JsonNode array) {
        var values = new ArrayList<DslLanguage>();
        array.forEach(node -> values.add(DslLanguage.valueOf(node.asText().toUpperCase(Locale.ROOT))));
        return List.copyOf(values);
    }

    private boolean isDslLanguageSupported(TargetContext target) {
        return dslLanguageSupport(target.language())
                .map(support -> versionAtLeast(target.gatlingVersion(), support.since()))
                .orElse(false);
    }

    private static boolean versionAtLeast(String current, String minimum) {
        return GatlingVersion.atLeast(current, minimum);
    }

    private static Optional<String> versionLineFor(String requestedVersion, List<String> availableLines) {
        return availableLines.stream()
                .filter(line -> sameMajorMinor(requestedVersion, line))
                .findFirst();
    }

    private static boolean sameMajorMinor(String requestedVersion, String availableLine) {
        var requested = majorMinor(requestedVersion);
        var available = majorMinor(availableLine);
        return requested.equals(available);
    }

    private static String majorMinor(String version) {
        var parts = version.split("\\.");
        if (parts.length < 2) {
            return version;
        }
        return parts[0] + "." + parts[1].replaceAll("[^0-9].*$", "");
    }

    private static String callTemplate(JsonNode node, DslLanguage language) {
        var languageTemplates = node.path("languageTemplates");
        if (languageTemplates.isObject()) {
            var languageTemplate = languageTemplates.path(language.name());
            if (languageTemplate.isTextual()) {
                return languageTemplate.asText();
            }
        }
        return node.path("callTemplate").asText();
    }

    public record FeatureRule(String since, String feature, String invalidPattern, String replacement, String message) {
    }

    public record ProtocolSupport(Protocol protocol, String generationMode) {
    }

    public record DslLanguageSupport(
            List<DslLanguage> languages,
            String since,
            String warningCode,
            String warningMessage,
            String sourceUrl
    ) {
    }

    public record PluginSupport(
            CommunityPlugin plugin,
            boolean supported,
            String pluginVersion,
            String matchedGatlingLine,
            String verifiedAgainstGatlingVersion,
            List<DslLanguage> languages,
            List<BuildTool> buildTools,
            String minimumJavaVersion,
            ConfidenceLevel confidenceLevel,
            String repositoryUrl,
            String sourceUrl,
            String reasonCode
    ) {
    }
}
