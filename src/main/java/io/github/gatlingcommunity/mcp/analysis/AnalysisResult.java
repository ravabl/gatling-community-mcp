package io.github.gatlingcommunity.mcp.analysis;

import io.github.gatlingcommunity.mcp.validation.ValidationFinding;
import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import java.util.List;

public record AnalysisResult(
        TargetContext target,
        List<String> scenarioNames,
        List<String> protocolConfigurations,
        List<String> requestNames,
        List<String> checks,
        List<String> feeders,
        List<String> correlations,
        List<String> injectionProfiles,
        List<String> assertions,
        List<ValidationFinding> validationFindings
) {
    public AnalysisResult {
        scenarioNames = scenarioNames == null ? List.of() : List.copyOf(scenarioNames);
        protocolConfigurations = protocolConfigurations == null ? List.of() : List.copyOf(protocolConfigurations);
        requestNames = requestNames == null ? List.of() : List.copyOf(requestNames);
        checks = checks == null ? List.of() : List.copyOf(checks);
        feeders = feeders == null ? List.of() : List.copyOf(feeders);
        correlations = correlations == null ? List.of() : List.copyOf(correlations);
        injectionProfiles = injectionProfiles == null ? List.of() : List.copyOf(injectionProfiles);
        assertions = assertions == null ? List.of() : List.copyOf(assertions);
        validationFindings = validationFindings == null ? List.of() : List.copyOf(validationFindings);
    }
}
