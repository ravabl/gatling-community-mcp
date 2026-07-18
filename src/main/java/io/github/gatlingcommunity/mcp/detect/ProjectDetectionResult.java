package io.github.gatlingcommunity.mcp.detect;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import io.github.gatlingcommunity.mcp.core.model.DslLanguage;
import java.util.Optional;

public record ProjectDetectionResult(
        BuildTool buildTool,
        DslLanguage language,
        Optional<String> gatlingVersion,
        String confidence
) {
    public ProjectDetectionResult {
        gatlingVersion = gatlingVersion == null ? Optional.empty() : gatlingVersion;
    }
}
