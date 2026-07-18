package io.github.gatlingcommunity.mcp.core.model;

import java.util.List;
import java.util.Map;

public record CapabilityReport(
        TargetContext target,
        boolean supported,
        String generationMode,
        List<String> features,
        List<WarningMessage> warnings,
        Map<String, Object> metadata
) {
}
