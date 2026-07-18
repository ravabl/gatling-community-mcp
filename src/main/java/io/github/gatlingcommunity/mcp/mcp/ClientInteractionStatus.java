package io.github.gatlingcommunity.mcp.mcp;

import java.util.LinkedHashMap;
import java.util.Map;

public record ClientInteractionStatus(
        String clientName,
        String clientVersion,
        boolean elicitation,
        boolean sampling,
        boolean progress
) {
    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("clientName", clientName == null || clientName.isBlank() ? "unknown" : clientName);
        values.put("clientVersion", clientVersion == null || clientVersion.isBlank() ? "unknown" : clientVersion);
        values.put("elicitation", elicitation);
        values.put("sampling", sampling);
        values.put("progress", progress);
        values.put("progressTokenPresent", progress);
        return Map.copyOf(values);
    }
}
