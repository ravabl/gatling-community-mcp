package io.github.gatlingcommunity.mcp.core.model;

import java.util.List;
import java.util.Map;

public record GatlingDslSemanticRule(
        String name,
        DslMethodCategory category,
        Protocol protocol,
        String allowedParentContext,
        String returnType,
        String chainType,
        String requiredPrecedingMethod,
        List<String> incompatibleMethods,
        Map<DslLanguage, String> dslSpecificSyntax,
        String exampleSnippet,
        String compileRiskNotes
) {
    public GatlingDslSemanticRule {
        incompatibleMethods = incompatibleMethods == null ? List.of() : List.copyOf(incompatibleMethods);
        dslSpecificSyntax = dslSpecificSyntax == null ? Map.of() : Map.copyOf(dslSpecificSyntax);
    }
}
