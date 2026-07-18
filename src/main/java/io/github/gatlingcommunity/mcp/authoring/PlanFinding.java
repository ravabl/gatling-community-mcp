package io.github.gatlingcommunity.mcp.authoring;

public record PlanFinding(
        String severity,
        String code,
        String path,
        String message
) {
}
