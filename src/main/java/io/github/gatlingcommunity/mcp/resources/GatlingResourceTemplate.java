package io.github.gatlingcommunity.mcp.resources;

public record GatlingResourceTemplate(
        String uriTemplate,
        String name,
        String title,
        String description,
        String mimeType,
        double priority
) {
}
