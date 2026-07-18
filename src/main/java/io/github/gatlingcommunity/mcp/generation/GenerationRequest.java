package io.github.gatlingcommunity.mcp.generation;

import io.github.gatlingcommunity.mcp.core.model.Protocol;
import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import java.util.Map;

public record GenerationRequest(
        TargetContext target,
        Protocol protocol,
        String simulationClassName,
        Map<String, String> parameters
) {
    public GenerationRequest {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
