package io.github.gatlingcommunity.mcp.importing;

import io.github.gatlingcommunity.mcp.authoring.HttpSimulationPlan;
import java.util.List;

public record HttpImportResult(
        String sourceType,
        String sourceVersion,
        HttpSimulationPlan plan,
        List<ImportWarning> warnings,
        EndpointInventory endpointInventory,
        List<String> feederCandidates,
        List<String> correlationCandidates,
        List<String> correlationUsages
) {
    public HttpImportResult(String sourceType,
                            String sourceVersion,
                            HttpSimulationPlan plan,
                            List<ImportWarning> warnings) {
        this(sourceType, sourceVersion, plan, warnings, null, List.of(), List.of(), List.of());
    }

    public HttpImportResult(String sourceType,
                            String sourceVersion,
                            HttpSimulationPlan plan,
                            List<ImportWarning> warnings,
                            EndpointInventory endpointInventory) {
        this(sourceType, sourceVersion, plan, warnings, endpointInventory, List.of(), List.of(), List.of());
    }

    public HttpImportResult {
        sourceType = sourceType == null || sourceType.isBlank() ? "UNKNOWN" : sourceType;
        sourceVersion = sourceVersion == null || sourceVersion.isBlank() ? "unknown" : sourceVersion;
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        endpointInventory = endpointInventory == null
                ? EndpointInventory.fromRequests(plan.requests())
                : endpointInventory;
        feederCandidates = feederCandidates == null ? List.of() : List.copyOf(feederCandidates);
        correlationCandidates = correlationCandidates == null ? List.of() : List.copyOf(correlationCandidates);
        correlationUsages = correlationUsages == null ? List.of() : List.copyOf(correlationUsages);
    }

    public int requestCount() {
        return plan.requests().size();
    }
}
