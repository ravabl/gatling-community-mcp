package io.github.gatlingcommunity.mcp.authoring;

import java.util.Map;

public record PausePlan(int durationSeconds) {
    public PausePlan {
        durationSeconds = Math.max(durationSeconds, 0);
    }

    public Map<String, Object> toMap() {
        return Map.of("durationSeconds", durationSeconds);
    }
}
