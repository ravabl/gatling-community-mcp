package io.github.gatlingcommunity.mcp.authoring;

import java.util.List;
import java.util.Map;

public record PlanValidationResult(
        boolean valid,
        String matchedGatlingLine,
        List<PlanFinding> findings,
        List<Map<String, Object>> methodRequirements
) {
    public PlanValidationResult {
        matchedGatlingLine = matchedGatlingLine == null ? "unsupported" : matchedGatlingLine;
        findings = findings == null ? List.of() : List.copyOf(findings);
        methodRequirements = methodRequirements == null ? List.of() : List.copyOf(methodRequirements);
    }
}
