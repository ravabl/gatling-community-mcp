package io.github.gatlingcommunity.mcp.validation;

import java.util.List;

public record ValidationResult(List<ValidationFinding> findings) {
    public ValidationResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
