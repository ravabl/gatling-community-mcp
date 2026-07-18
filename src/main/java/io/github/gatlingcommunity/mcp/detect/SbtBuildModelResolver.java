package io.github.gatlingcommunity.mcp.detect;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

final class SbtBuildModelResolver implements BuildModelResolver {
    private static final Pattern SCALA_VERSION = Pattern.compile("scalaVersion\\s*:=\\s*\"([^\"]+)\"");
    private static final Pattern GATLING_DEPENDENCY = Pattern.compile(
            "\"io\\.gatling\"\\s*%{1,2}\\s*\"([^\"]+)\"\\s*%\\s*\"([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)\"");

    private final ConventionOnlyBuildModelResolver fallback = new ConventionOnlyBuildModelResolver();

    @Override
    public BuildTool buildTool() {
        return BuildTool.SBT;
    }

    @Override
    public boolean supports(Path root) {
        return Files.isRegularFile(root.resolve("build.sbt"));
    }

    @Override
    public BuildModel resolve(Path root) {
        var normalized = root.toAbsolutePath().normalize();
        var raw = fallback.resolve(normalized);
        var text = ConventionOnlyBuildModelResolver.buildText(normalized, BuildTool.SBT);
        var properties = new LinkedHashMap<String, String>(raw.properties());
        firstMatch(SCALA_VERSION, text, 1).ifPresent(value -> properties.putIfAbsent("scala.version", value));

        var dependencies = new LinkedHashSet<String>(raw.dependencies());
        var matcher = GATLING_DEPENDENCY.matcher(text);
        while (matcher.find()) {
            dependencies.add(matcher.group(1));
            properties.putIfAbsent("gatling.version", matcher.group(2));
        }

        return new BuildModel(
                BuildTool.SBT,
                properties,
                List.copyOf(dependencies),
                raw.plugins(),
                raw.sourceRoots(),
                raw.testRoots(),
                ordered(raw.warnings(), List.of("build_model.sbt.static_parse"))
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
