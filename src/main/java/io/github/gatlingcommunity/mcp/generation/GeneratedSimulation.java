package io.github.gatlingcommunity.mcp.generation;

import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import io.github.gatlingcommunity.mcp.core.model.WarningMessage;
import java.util.List;
import java.util.Map;

public record GeneratedSimulation(
        TargetContext target,
        String code,
        List<WarningMessage> warnings,
        String generationMode,
        Map<String, Object> metadata
) {
    public GeneratedSimulation {
        code = code == null ? "" : code;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        generationMode = generationMode == null ? "unsupported" : generationMode;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
