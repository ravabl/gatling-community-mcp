package io.github.gatlingcommunity.mcp.core.model;

public record GatlingDslMethod(
        String name,
        DslMethodCategory category,
        Protocol protocol,
        DslLanguage language,
        String since,
        String until,
        String callTemplate,
        String sourceUrl,
        ConfidenceLevel confidenceLevel
) {
}
