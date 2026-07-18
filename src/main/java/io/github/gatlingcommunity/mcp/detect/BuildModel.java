package io.github.gatlingcommunity.mcp.detect;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import java.util.List;
import java.util.Map;

public record BuildModel(
        BuildTool buildTool,
        Map<String, String> properties,
        List<String> dependencies,
        List<String> plugins,
        List<String> sourceRoots,
        List<String> testRoots,
        List<String> warnings
) {
    public BuildModel {
        buildTool = buildTool == null ? BuildTool.UNKNOWN : buildTool;
        properties = Map.copyOf(properties == null ? Map.of() : properties);
        dependencies = List.copyOf(dependencies == null ? List.of() : dependencies);
        plugins = List.copyOf(plugins == null ? List.of() : plugins);
        sourceRoots = List.copyOf(sourceRoots == null ? List.of() : sourceRoots);
        testRoots = List.copyOf(testRoots == null ? List.of() : testRoots);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
}
