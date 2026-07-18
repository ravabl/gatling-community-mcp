package io.github.gatlingcommunity.mcp.authoring;

import java.util.LinkedHashMap;
import java.util.Map;

public record HttpScenarioStepPlan(
        String type,
        String feederName,
        HttpRequestPlan request,
        PausePlan pause,
        LoopPlan loop,
        GroupPlan group,
        ConditionalPlan conditional
) {
    public HttpScenarioStepPlan {
        type = type == null || type.isBlank() ? "request" : type;
        feederName = feederName == null ? "" : feederName;
    }

    public static HttpScenarioStepPlan request(HttpRequestPlan request) {
        return new HttpScenarioStepPlan("request", "", request, null, null, null, null);
    }

    public static HttpScenarioStepPlan feed(String feederName) {
        return new HttpScenarioStepPlan("feed", feederName, null, null, null, null, null);
    }

    public static HttpScenarioStepPlan pause(PausePlan pause) {
        return new HttpScenarioStepPlan("pause", "", null, pause, null, null, null);
    }

    public static HttpScenarioStepPlan loop(String type, LoopPlan loop) {
        return new HttpScenarioStepPlan(type, "", null, null, loop, null, null);
    }

    public static HttpScenarioStepPlan group(GroupPlan group) {
        return new HttpScenarioStepPlan("group", "", null, null, null, group, null);
    }

    public static HttpScenarioStepPlan conditional(String type, ConditionalPlan conditional) {
        return new HttpScenarioStepPlan(type, "", null, null, null, null, conditional);
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("type", type);
        if (!feederName.isBlank()) {
            values.put("feederName", feederName);
        }
        if (request != null) {
            values.put("request", request.toMap());
        }
        if (pause != null) {
            values.put("pause", pause.toMap());
        }
        if (loop != null) {
            values.put("loop", loop.toMap());
        }
        if (group != null) {
            values.put("group", group.toMap());
        }
        if (conditional != null) {
            values.put("conditional", conditional.toMap());
        }
        return values;
    }
}
