package io.github.gatlingcommunity.mcp.detect;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import io.github.gatlingcommunity.mcp.core.model.DslLanguage;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ProjectContext(
        Path root,
        BuildTool buildTool,
        DslLanguage language,
        String detectedGatlingVersion,
        String javaVersion,
        String nodeVersion,
        List<String> sourceRoots,
        List<String> testRoots,
        List<String> existingSimulationFiles,
        Map<String, Object> dependencyState,
        Map<String, Object> pluginState,
        Map<String, Object> packageNaming,
        List<String> styleConventions,
        List<String> warnings
) {
    public ProjectContext {
        detectedGatlingVersion = blankDefault(detectedGatlingVersion, "unknown");
        javaVersion = blankDefault(javaVersion, "unknown");
        nodeVersion = blankDefault(nodeVersion, "unknown");
        sourceRoots = List.copyOf(sourceRoots == null ? List.of() : sourceRoots);
        testRoots = List.copyOf(testRoots == null ? List.of() : testRoots);
        existingSimulationFiles = List.copyOf(existingSimulationFiles == null ? List.of() : existingSimulationFiles);
        dependencyState = Map.copyOf(dependencyState == null ? Map.of() : dependencyState);
        pluginState = Map.copyOf(pluginState == null ? Map.of() : pluginState);
        packageNaming = Map.copyOf(packageNaming == null ? Map.of() : packageNaming);
        styleConventions = List.copyOf(styleConventions == null ? List.of() : styleConventions);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("root", root.toString());
        values.put("buildTool", buildTool.name());
        values.put("language", language.name());
        values.put("detectedGatlingVersion", detectedGatlingVersion);
        values.put("javaVersion", javaVersion);
        values.put("nodeVersion", nodeVersion);
        values.put("sourceRoots", sourceRoots);
        values.put("testRoots", testRoots);
        values.put("existingSimulationFiles", existingSimulationFiles);
        values.put("dependencyState", dependencyState);
        values.put("pluginState", pluginState);
        values.put("packageNaming", packageNaming);
        values.put("styleConventions", styleConventions);
        values.put("warnings", warnings);
        return Map.copyOf(values);
    }

    public String explain() {
        return """
                buildTool=%s language=%s gatlingVersion=%s javaVersion=%s nodeVersion=%s
                sourceRoots=%s
                testRoots=%s
                existingSimulationFiles=%s
                dependencyState=%s
                pluginState=%s
                packageNaming=%s
                styleConventions=%s
                warnings=%s
                """.formatted(
                buildTool,
                language,
                detectedGatlingVersion,
                javaVersion,
                nodeVersion,
                sourceRoots,
                testRoots,
                existingSimulationFiles,
                dependencyState,
                pluginState,
                packageNaming,
                styleConventions,
                warnings
        ).strip();
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
