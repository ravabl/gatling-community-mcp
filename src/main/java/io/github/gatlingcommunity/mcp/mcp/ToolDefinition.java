package io.github.gatlingcommunity.mcp.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;

public record ToolDefinition(
        String name,
        String title,
        String description,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        McpSchema.ToolAnnotations annotations,
        Map<String, Object> meta
) {
    public ToolDefinition {
        meta = meta == null ? Map.of() : Map.copyOf(meta);
    }
}
