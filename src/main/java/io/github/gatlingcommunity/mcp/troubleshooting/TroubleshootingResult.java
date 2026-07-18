package io.github.gatlingcommunity.mcp.troubleshooting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TroubleshootingResult(
        List<TroubleshootingFinding> findings,
        List<RootCauseHypothesis> rootCauseHypotheses,
        List<String> verificationSteps,
        List<String> nextActions,
        String confidence,
        Map<String, Object> suggestedModel,
        Map<String, Object> injectionProfile,
        List<String> assumptions,
        List<String> risks
) {
    public TroubleshootingResult {
        findings = List.copyOf(findings == null ? List.of() : findings);
        rootCauseHypotheses = List.copyOf(rootCauseHypotheses == null ? List.of() : rootCauseHypotheses);
        verificationSteps = List.copyOf(verificationSteps == null ? List.of() : verificationSteps);
        nextActions = List.copyOf(nextActions == null ? List.of() : nextActions);
        confidence = confidence == null || confidence.isBlank() ? "medium" : confidence;
        suggestedModel = Map.copyOf(suggestedModel == null ? Map.of() : suggestedModel);
        injectionProfile = Map.copyOf(injectionProfile == null ? Map.of() : injectionProfile);
        assumptions = List.copyOf(assumptions == null ? List.of() : assumptions);
        risks = List.copyOf(risks == null ? List.of() : risks);
    }

    public static TroubleshootingResult empty(String confidence) {
        return new TroubleshootingResult(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                confidence,
                Map.of(),
                Map.of(),
                List.of(),
                List.of()
        );
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("findings", findings.stream().map(TroubleshootingFinding::toMap).toList());
        values.put("rootCauseHypotheses", rootCauseHypotheses.stream()
                .map(RootCauseHypothesis::toMap)
                .toList());
        values.put("verificationSteps", verificationSteps);
        values.put("nextActions", nextActions);
        values.put("confidence", confidence);
        values.put("suggestedModel", suggestedModel);
        values.put("injectionProfile", injectionProfile);
        values.put("assumptions", assumptions);
        values.put("risks", risks);
        return Map.copyOf(values);
    }
}
