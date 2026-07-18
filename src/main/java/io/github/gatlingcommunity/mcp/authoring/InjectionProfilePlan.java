package io.github.gatlingcommunity.mcp.authoring;

import java.util.LinkedHashMap;
import java.util.Map;

public record InjectionProfilePlan(
        String type,
        int rampFromUsersPerSec,
        int rampToUsersPerSec,
        int rampDurationSeconds,
        int constantUsersPerSec,
        int constantDurationSeconds
) {
    public InjectionProfilePlan {
        type = type == null || type.isBlank() ? "rampAndConstant" : type;
        rampFromUsersPerSec = rampFromUsersPerSec <= 0 ? 1 : rampFromUsersPerSec;
        rampToUsersPerSec = rampToUsersPerSec <= 0 ? 5 : rampToUsersPerSec;
        rampDurationSeconds = rampDurationSeconds <= 0 ? 30 : rampDurationSeconds;
        constantUsersPerSec = constantUsersPerSec <= 0 ? rampToUsersPerSec : constantUsersPerSec;
        constantDurationSeconds = constantDurationSeconds <= 0 ? 60 : constantDurationSeconds;
    }

    public static InjectionProfilePlan defaultOpenModel() {
        return new InjectionProfilePlan("rampAndConstant", 1, 5, 30, 5, 60);
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("type", type);
        values.put("rampFromUsersPerSec", rampFromUsersPerSec);
        values.put("rampToUsersPerSec", rampToUsersPerSec);
        values.put("rampDurationSeconds", rampDurationSeconds);
        values.put("constantUsersPerSec", constantUsersPerSec);
        values.put("constantDurationSeconds", constantDurationSeconds);
        return values;
    }
}
