package io.github.gatlingcommunity.mcp.generation.renderer;

import io.github.gatlingcommunity.mcp.authoring.HttpSimulationPlan;
import io.github.gatlingcommunity.mcp.core.model.DslLanguage;
import io.github.gatlingcommunity.mcp.core.model.TargetContext;

public interface GatlingCodeRenderer {
    DslLanguage language();

    String render(TargetContext target, HttpSimulationPlan plan);
}
