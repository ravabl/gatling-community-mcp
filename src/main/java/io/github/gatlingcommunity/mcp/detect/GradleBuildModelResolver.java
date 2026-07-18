package io.github.gatlingcommunity.mcp.detect;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

final class GradleBuildModelResolver implements BuildModelResolver {
    private static final Pattern GATLING_PLUGIN_VERSION = Pattern.compile(
            "id\\s*\\(?['\"]io\\.gatling\\.gradle['\"]\\)?\\s*version\\s*['\"]([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)['\"]");
    private static final Pattern GATLING_DEPENDENCY = Pattern.compile(
            "io\\.gatling:([^:'\"]+):([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern JAVA_TOOLCHAIN = Pattern.compile("JavaLanguageVersion\\.of\\((\\d+)\\)");
    private static final Pattern JAVA_VERSION = Pattern.compile("JavaVersion\\.VERSION_(\\d+)");

    private final ConventionOnlyBuildModelResolver fallback = new ConventionOnlyBuildModelResolver();

    @Override
    public BuildTool buildTool() {
        return BuildTool.GRADLE;
    }

    @Override
    public boolean supports(Path root) {
        return Files.isRegularFile(root.resolve("build.gradle"))
                || Files.isRegularFile(root.resolve("build.gradle.kts"));
    }

    @Override
    public BuildModel resolve(Path root) {
        var normalized = root.toAbsolutePath().normalize();
        var raw = fallback.resolve(normalized);
        var text = ConventionOnlyBuildModelResolver.buildText(normalized, BuildTool.GRADLE);
        var properties = new LinkedHashMap<String, String>(raw.properties());
        firstMatch(GATLING_PLUGIN_VERSION, text, 1).ifPresent(value -> properties.putIfAbsent("gatling.version", value));
        firstMatch(JAVA_TOOLCHAIN, text, 1)
                .or(() -> firstMatch(JAVA_VERSION, text, 1))
                .ifPresent(value -> properties.putIfAbsent("java.version", value));

        var dependencies = new LinkedHashSet<String>(raw.dependencies());
        var matcher = GATLING_DEPENDENCY.matcher(text);
        while (matcher.find()) {
            dependencies.add(matcher.group(1));
            properties.putIfAbsent("gatling.version", matcher.group(2));
        }

        var plugins = new LinkedHashSet<String>(raw.plugins());
        if (text.contains("io.gatling.gradle")) {
            plugins.add("io.gatling.gradle");
        }

        return new BuildModel(
                BuildTool.GRADLE,
                properties,
                List.copyOf(dependencies),
                List.copyOf(plugins),
                raw.sourceRoots(),
                raw.testRoots(),
                ordered(raw.warnings(), List.of("build_model.gradle.static_parse"))
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
