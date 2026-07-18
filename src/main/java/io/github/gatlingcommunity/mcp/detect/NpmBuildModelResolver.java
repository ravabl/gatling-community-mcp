package io.github.gatlingcommunity.mcp.detect;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

final class NpmBuildModelResolver implements BuildModelResolver {
    private static final Pattern GATLING_PACKAGE = Pattern.compile(
            "\"(@gatling\\.io/[^\"]+)\"\\s*:\\s*\"[~^]?([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)\"");
    private static final Pattern NODE_ENGINE = Pattern.compile("\"node\"\\s*:\\s*\"([^\"]+)\"");

    private final ConventionOnlyBuildModelResolver fallback = new ConventionOnlyBuildModelResolver();

    @Override
    public BuildTool buildTool() {
        return BuildTool.NPM;
    }

    @Override
    public boolean supports(Path root) {
        return Files.isRegularFile(root.resolve("package.json"));
    }

    @Override
    public BuildModel resolve(Path root) {
        var normalized = root.toAbsolutePath().normalize();
        var raw = fallback.resolve(normalized);
        var text = ConventionOnlyBuildModelResolver.buildText(normalized, BuildTool.NPM);
        var properties = new LinkedHashMap<String, String>(raw.properties());
        firstMatch(NODE_ENGINE, text, 1).ifPresent(value -> properties.putIfAbsent("node.version", value));

        var dependencies = new LinkedHashSet<String>(raw.dependencies());
        var matcher = GATLING_PACKAGE.matcher(text);
        while (matcher.find()) {
            dependencies.add(matcher.group(1));
            properties.putIfAbsent("gatling.version", matcher.group(2));
        }

        var plugins = new LinkedHashSet<String>(raw.plugins());
        if (text.contains("\"typescript\"")) {
            plugins.add("typescript");
        }

        return new BuildModel(
                BuildTool.NPM,
                properties,
                List.copyOf(dependencies),
                List.copyOf(plugins),
                raw.sourceRoots(),
                raw.testRoots(),
                ordered(raw.warnings(), List.of("build_model.npm.static_parse"))
        );
    }

    private static java.util.Optional<String> firstMatch(Pattern pattern, String text, int group) {
        var matcher = pattern.matcher(text);
        return matcher.find() ? java.util.Optional.of(matcher.group(group)) : java.util.Optional.empty();
    }

    private static List<String> ordered(List<String> first, List<String> second) {
        var values = new LinkedHashSet<String>();
        values.addAll(first);
        values.addAll(second);
        return List.copyOf(values);
    }
}
