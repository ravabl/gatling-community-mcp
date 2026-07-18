package io.github.gatlingcommunity.mcp.authoring;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record LoopPlan(
        String type,
        int count,
        int durationSeconds,
        List<HttpScenarioStepPlan> steps
) {
    public LoopPlan {
        type = type == null || type.isBlank() ? "repeat" : type;
        count = Math.max(count, 0);
        durationSeconds = Math.max(durationSeconds, 0);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("type", type);
        values.put("count", count);
        values.put("durationSeconds", durationSeconds);
        values.put("steps", steps.stream().map(HttpScenarioStepPlan::toMap).toList());
        return values;
    }
}
