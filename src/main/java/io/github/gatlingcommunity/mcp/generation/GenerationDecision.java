package io.github.gatlingcommunity.mcp.generation;

import java.util.LinkedHashMap;
import java.util.Map;

public record GenerationDecision(
        String subject,
        String selectedMethod,
        String why,
        String source,
        String assumption,
        String risk,
        String verification
) {
    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("subject", subject);
        values.put("selectedMethod", selectedMethod);
        values.put("why", why);
        values.put("source", source);
        values.put("assumption", assumption);
        values.put("risk", risk);
        values.put("verification", verification);
        return values;
    }
}
