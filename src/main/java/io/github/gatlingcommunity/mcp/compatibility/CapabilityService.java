package io.github.gatlingcommunity.mcp.compatibility;

import io.github.gatlingcommunity.mcp.core.model.CapabilityReport;
import io.github.gatlingcommunity.mcp.core.model.CommunityPlugin;
import io.github.gatlingcommunity.mcp.core.model.GatlingVersion;
import io.github.gatlingcommunity.mcp.core.model.Protocol;
import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import io.github.gatlingcommunity.mcp.core.model.WarningMessage;
import io.github.gatlingcommunity.mcp.data.SourceDataRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class CapabilityService {
    private final SourceDataRepository sourceData;

    public CapabilityService(SourceDataRepository sourceData) {
        this.sourceData = sourceData;
    }

    public CapabilityReport resolve(TargetContext target, Protocol protocol) {
        var warnings = new ArrayList<WarningMessage>();
        var matchedVersionLine = sourceData.supportedVersionLineFor(target.gatlingVersion());

        var languageSupport = sourceData.dslLanguageSupport(target.language());
        if (languageSupport.isPresent()
                && !GatlingVersion.atLeast(target.gatlingVersion(), languageSupport.orElseThrow().since())) {
            var support = languageSupport.orElseThrow();
            warnings.add(new WarningMessage(
                    support.warningCode(),
                    support.warningMessage(),
                    support.sourceUrl()
            ));
            return unsupported(target, protocol, warnings);
        }

        if (matchedVersionLine.isEmpty()) {
            warnings.add(new WarningMessage(
                    "gatling.version.unsupported.v1",
                    "v1 authoring support is verified for Gatling 3.7 through 3.15 version lines.",
                    "https://docs.gatling.io/release-notes/gatling/"
            ));
            return unsupported(target, protocol, warnings);
        }

        if (target.pluginContext().isPresent()) {
            return resolvePlugin(target, protocol, warnings, matchedVersionLine.orElseThrow());
        }

        var protocolSupport = sourceData.protocolSupport(protocol);
        if ("deep".equals(protocolSupport.generationMode())) {
            var dslMethods = sourceData.dslMethods(target, protocol);
            return new CapabilityReport(target, true, "deep",
                    List.of("http", "checks", "feeders", "correlation", "assertions"),
                    List.copyOf(warnings),
                    deepMetadata(protocol, matchedVersionLine.orElseThrow(), dslMethods));
        }

        if ("capability-only".equals(protocolSupport.generationMode())) {
            warnings.add(new WarningMessage(
                    "protocol.generation.disabled.v1",
                    "v1 reports capability metadata for this protocol but does not generate full code.",
                    "https://docs.gatling.io/reference/script/"
            ));
            return new CapabilityReport(target, true, "capability-only",
                    List.of(protocol.name().toLowerCase()),
                    List.copyOf(warnings),
                    baseMetadata(protocol, matchedVersionLine.orElseThrow()));
        }

        warnings.add(new WarningMessage("protocol.unsupported", "Protocol is not supported by v1.", ""));
        return unsupported(target, protocol, warnings);
    }

    private CapabilityReport resolvePlugin(
            TargetContext target,
            Protocol protocol,
            List<WarningMessage> warnings,
            String matchedVersionLine
    ) {
        var plugin = target.pluginContext().orElseThrow().plugin();
        var expectedProtocol = switch (plugin) {
            case KAFKA -> Protocol.KAFKA;
            case JDBC -> Protocol.JDBC;
            case AMQP -> Protocol.AMQP;
            case PICATINNY -> Protocol.PICATINNY;
        };
        if (protocol != expectedProtocol) {
            warnings.add(new WarningMessage("plugin.protocol.mismatch",
                    "Selected plugin does not match requested protocol.", ""));
            return unsupported(target, protocol, warnings);
        }

        var requestedPluginVersion = target.pluginContext().orElseThrow().pluginVersion();
        var pluginSupport = sourceData.pluginSupport(
                plugin,
                target.gatlingVersion(),
                target.language(),
                target.buildTool(),
                requestedPluginVersion
        );
        if (!pluginSupport.supported()) {
            warnings.add(new WarningMessage(
                    pluginSupport.reasonCode(),
                    "No strict-verified target matches plugin=%s version=%s Gatling=%s DSL=%s buildTool=%s. Verified candidate: %s for Gatling %s."
                            .formatted(plugin, requestedPluginVersion.isBlank() ? "auto" : requestedPluginVersion,
                                    target.gatlingVersion(), target.language(), target.buildTool(),
                                    pluginSupport.pluginVersion(), pluginSupport.verifiedAgainstGatlingVersion()),
                    pluginSupport.sourceUrl()
            ));
            return unsupported(target, protocol, warnings);
        }
        if (target.javaVersion().isEmpty()
                || !majorAtLeast(target.javaVersion().orElseThrow(), pluginSupport.minimumJavaVersion())) {
            warnings.add(new WarningMessage(
                    "plugin.java.unsupported",
                    "Verified plugin %s %s requires Java %s or newer; provide the effective Java runtime."
                            .formatted(plugin, pluginSupport.pluginVersion(), pluginSupport.minimumJavaVersion()),
                    pluginSupport.sourceUrl()
            ));
            return unsupported(target, protocol, warnings);
        }

        return new CapabilityReport(target, true, "deep-plugin",
                pluginFeatures(plugin),
                List.copyOf(warnings),
                Map.ofEntries(
                        Map.entry("protocol", protocol.name()),
                        Map.entry("matchedGatlingLine", matchedVersionLine),
                        Map.entry("compatibilityPolicy", "strict-verified"),
                        Map.entry("plugin", plugin.name()),
                        Map.entry("pluginVersion", pluginSupport.pluginVersion()),
                        Map.entry("verifiedAgainstGatlingVersion", pluginSupport.verifiedAgainstGatlingVersion()),
                        Map.entry("minimumJavaVersion", pluginSupport.minimumJavaVersion()),
                        Map.entry("confidence", pluginSupport.confidenceLevel().name()),
                        Map.entry("repositoryUrl", pluginSupport.repositoryUrl()),
                        Map.entry("sourceUrl", pluginSupport.sourceUrl())
                ));
    }

    private static boolean majorAtLeast(String actual, String minimum) {
        try {
            return Integer.parseInt(actual.replaceAll("[^0-9].*$", "")) >= Integer.parseInt(minimum);
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static Map<String, Object> baseMetadata(Protocol protocol, String matchedVersionLine) {
        return Map.of(
                "protocol", protocol.name(),
                "matchedGatlingLine", matchedVersionLine,
                "compatibilityPolicy", "version-line"
        );
    }

    private static Map<String, Object> deepMetadata(
            Protocol protocol,
            String matchedVersionLine,
            List<io.github.gatlingcommunity.mcp.core.model.GatlingDslMethod> dslMethods
    ) {
        return Map.of(
                "protocol", protocol.name(),
                "matchedGatlingLine", matchedVersionLine,
                "compatibilityPolicy", "version-line",
                "dslMethodCount", dslMethods.size(),
                "dslMethodCategories", dslMethods.stream()
                        .map(method -> method.category().name())
                        .distinct()
                        .collect(Collectors.toList())
        );
    }

    private static List<String> pluginFeatures(CommunityPlugin plugin) {
        return switch (plugin) {
            case KAFKA -> List.of("produce", "request-reply", "consume", "checks", "correlation");
            case JDBC -> List.of("query", "batch", "stored-procedure", "checks", "hikari-warnings");
            case AMQP -> List.of("publish", "request-reply", "consume", "checks", "correlation");
            case PICATINNY -> List.of("feeders", "transactions", "assertions", "redis", "utilities");
        };
    }

    private static CapabilityReport unsupported(TargetContext target, Protocol protocol, List<WarningMessage> warnings) {
        return new CapabilityReport(target, false, "unsupported", List.of(), List.copyOf(warnings),
                Map.of("protocol", protocol.name()));
    }
}
