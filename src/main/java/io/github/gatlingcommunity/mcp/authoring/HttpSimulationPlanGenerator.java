package io.github.gatlingcommunity.mcp.authoring;

import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import io.github.gatlingcommunity.mcp.generation.renderer.JavaHttpRenderer;
import io.github.gatlingcommunity.mcp.generation.renderer.JavascriptHttpRenderer;
import io.github.gatlingcommunity.mcp.generation.renderer.KotlinHttpRenderer;
import io.github.gatlingcommunity.mcp.generation.renderer.ScalaHttpRenderer;

public final class HttpSimulationPlanGenerator {
    public String generate(TargetContext target, HttpSimulationPlan plan) {
        return switch (target.language()) {
            case JAVA -> new JavaHttpRenderer().render(target, plan);
            case KOTLIN -> new KotlinHttpRenderer().render(target, plan);
            case SCALA -> new ScalaHttpRenderer().render(target, plan);
            case JAVASCRIPT, TYPESCRIPT -> new JavascriptHttpRenderer(target.language()).render(target, plan);
        };
    }
}
