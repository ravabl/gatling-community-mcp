package io.github.gatlingcommunity.mcp.core.model;

public record PluginContext(
        CommunityPlugin plugin,
        String pluginVersion,
        ConfidenceLevel confidenceLevel
) {
}
