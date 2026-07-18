package io.github.gatlingcommunity.mcp.importing;

import java.util.List;

public record CodebaseEndpointExtractionResult(
        EndpointInventory endpointInventory,
        List<ImportWarning> warnings,
        int scannedFiles,
        int matchedFiles
) {
    public CodebaseEndpointExtractionResult {
        if (endpointInventory == null) {
            endpointInventory = EndpointInventory.fromEndpoints(List.of(), List.of(), List.of());
        }
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
