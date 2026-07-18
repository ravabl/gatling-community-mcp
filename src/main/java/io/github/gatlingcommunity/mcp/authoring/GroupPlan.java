package io.github.gatlingcommunity.mcp.authoring;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GroupPlan(String name, List<HttpScenarioStepPlan> steps) {
    public GroupPlan {
        name = name == null || name.isBlank() ? "group" : name;
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("name", name);
        values.put("steps", steps.stream().map(HttpScenarioStepPlan::toMap).toList());
        return values;
    }
}
