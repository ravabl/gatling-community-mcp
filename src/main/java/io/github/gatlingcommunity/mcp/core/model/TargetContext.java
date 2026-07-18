package io.github.gatlingcommunity.mcp.core.model;

import java.util.Optional;

public record TargetContext(
        String gatlingVersion,
        String edition,
        DslLanguage language,
        BuildTool buildTool,
        Optional<String> javaVersion,
        Optional<String> nodeVersion,
        Optional<PluginContext> pluginContext
) {
    public TargetContext {
        edition = edition == null || edition.isBlank() ? "community" : edition;
        pluginContext = pluginContext == null ? Optional.empty() : pluginContext;
        javaVersion = javaVersion == null ? Optional.empty() : javaVersion;
        nodeVersion = nodeVersion == null ? Optional.empty() : nodeVersion;
    }
}
