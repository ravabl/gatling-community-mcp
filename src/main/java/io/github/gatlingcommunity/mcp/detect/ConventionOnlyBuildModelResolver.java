package io.github.gatlingcommunity.mcp.detect;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class ConventionOnlyBuildModelResolver implements BuildModelResolver {
    private static final Pattern MAVEN_PROPERTIES = Pattern.compile("(?s)<properties>(.*?)</properties>");
    private static final Pattern XML_TAG = Pattern.compile("<([a-zA-Z0-9_.-]+)>([^<]+)</\\1>");
    private static final Pattern ARTIFACT_ID = Pattern.compile("<artifactId>([^<]+)</artifactId>");
    private static final Pattern JSON_NODE_ENGINE = Pattern.compile("\"node\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NPM_GATLING_VERSION = Pattern.compile("\"@gatling\\.io/[^\"]+\"\\s*:\\s*\"[~^]?([^\"]+)\"");
    private static final Pattern GRADLE_GATLING_VERSION = Pattern.compile("gatling[^\\n'\"]*['\"]([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)['\"]");
    private static final Pattern SBT_GATLING_VERSION = Pattern.compile("io\\.gatling[^\\n\"]+\"([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)\"");

    @Override
    public BuildTool buildTool() {
        return BuildTool.UNKNOWN;
    }

    @Override
    public boolean supports(Path root) {
        return true;
    }

    @Override
    public BuildModel resolve(Path root) {
        var normalized = root.toAbsolutePath().normalize();
        var buildTool = detectBuildTool(normalized);
        var buildText = buildText(normalized, buildTool);
        var properties = resolvedProperties(normalized, buildTool, buildText);
        var dependencies = dependencies(buildText, buildTool);
        return new BuildModel(
                buildTool,
                properties,
                dependencies,
                plugins(buildText, dependencies),
                existingConventionalRoots(normalized, sourceRootCandidates()),
                existingConventionalRoots(normalized, testRootCandidates()),
                List.of("build_model.resolver.convention_only")
        );
    }

    static BuildTool detectBuildTool(Path root) {
        if (Files.exists(root.resolve("pom.xml"))) {
            return BuildTool.MAVEN;
        }
        if (Files.exists(root.resolve("build.gradle")) || Files.exists(root.resolve("build.gradle.kts"))) {
            return BuildTool.GRADLE;
        }
        if (Files.exists(root.resolve("build.sbt"))) {
            return BuildTool.SBT;
        }
        if (Files.exists(root.resolve("package.json"))) {
            return BuildTool.NPM;
        }
        return BuildTool.UNKNOWN;
    }

    static String buildText(Path root, BuildTool buildTool) {
        return switch (buildTool) {
            case MAVEN -> readIfExists(root.resolve("pom.xml"));
            case GRADLE -> readIfExists(root.resolve("build.gradle")) + "\n" + readIfExists(root.resolve("build.gradle.kts"));
            case SBT -> readIfExists(root.resolve("build.sbt"));
            case NPM -> readIfExists(root.resolve("package.json"));
            case UNKNOWN -> "";
        };
    }

    private static Map<String, String> resolvedProperties(Path root, BuildTool buildTool, String buildText) {
        var values = new LinkedHashMap<String, String>();
        if (buildTool == BuildTool.MAVEN) {
            var matcher = MAVEN_PROPERTIES.matcher(buildText);
            while (matcher.find()) {
                var tagMatcher = XML_TAG.matcher(matcher.group(1));
                while (tagMatcher.find()) {
                    values.put(tagMatcher.group(1).trim(), tagMatcher.group(2).trim());
                }
            }
        }
        firstMatch(GRADLE_GATLING_VERSION, buildText).ifPresent(value -> values.putIfAbsent("gatling.version", value));
        firstMatch(SBT_GATLING_VERSION, buildText).ifPresent(value -> values.putIfAbsent("gatling.version", value));
        firstMatch(NPM_GATLING_VERSION, buildText).ifPresent(value -> values.putIfAbsent("gatling.version", value));
        firstMatch(JSON_NODE_ENGINE, buildText).ifPresent(value -> values.putIfAbsent("node.version", value));
        readFirstLine(root.resolve(".java-version")).ifPresent(value -> values.putIfAbsent("java.version", value));
        readFirstLine(root.resolve(".nvmrc")).ifPresent(value -> values.putIfAbsent("node.version", value));

        var resolved = new LinkedHashMap<String, String>();
        values.forEach((key, value) -> resolved.put(key, resolvePropertyValue(value, values, 0)));
        return Map.copyOf(resolved);
    }

    private static String resolvePropertyValue(String value, Map<String, String> properties, int depth) {
        if (value == null || depth > 8) {
            return value;
        }
        var result = value;
        for (var entry : properties.entrySet()) {
            var placeholder = "${" + entry.getKey() + "}";
            if (result.contains(placeholder)) {
                result = result.replace(placeholder,
                        resolvePropertyValue(entry.getValue(), properties, depth + 1));
            }
        }
        return result;
    }

    private static List<String> dependencies(String buildText, BuildTool buildTool) {
        var values = new LinkedHashSet<String>();
        if (buildTool == BuildTool.MAVEN) {
            var matcher = ARTIFACT_ID.matcher(buildText);
            while (matcher.find()) {
                values.add(matcher.group(1));
            }
        } else {
            for (var token : List.of(
                    "gatling-charts-highcharts",
                    "gatling-app",
                    "gatling-kafka-plugin",
                    "gatling-jdbc-plugin",
                    "gatling-amqp-plugin",
                    "gatling-picatinny",
                    "@gatling.io/core",
                    "@gatling.io/http")) {
                if (buildText.contains(token)) {
                    values.add(token);
                }
            }
        }
        return List.copyOf(values);
    }

    private static List<String> plugins(String buildText, List<String> dependencies) {
        var haystack = (buildText + "\n" + dependencies).toLowerCase(Locale.ROOT);
        var values = new ArrayList<String>();
        for (var plugin : List.of("gatling-kafka-plugin", "gatling-jdbc-plugin", "gatling-amqp-plugin", "gatling-picatinny")) {
            if (haystack.contains(plugin)) {
                values.add(plugin);
            }
        }
        return List.copyOf(values);
    }

    private static List<String> existingConventionalRoots(Path root, List<String> candidates) {
        return candidates.stream()
                .map(root::resolve)
                .filter(Files::isDirectory)
                .sorted(Comparator.comparing(Path::toString))
                .map(path -> relative(root, path))
                .toList();
    }

    private static List<String> sourceRootCandidates() {
        return List.of(
                "src/gatling/java",
                "src/gatling/kotlin",
                "src/gatling/scala",
                "src/gatling/javascript",
                "src/gatling/typescript",
                "src/main/java",
                "src/main/kotlin",
                "src/main/scala"
        );
    }

    private static List<String> testRootCandidates() {
        return List.of(
                "src/test/java",
                "src/test/kotlin",
                "src/test/scala",
                "src/test/javascript",
                "src/test/typescript"
        );
    }

    private static java.util.Optional<String> firstMatch(Pattern pattern, String text) {
        var matcher = pattern.matcher(text);
        return matcher.find() ? java.util.Optional.of(matcher.group(1).trim()) : java.util.Optional.empty();
    }

    private static java.util.Optional<String> readFirstLine(Path path) {
        if (!Files.isRegularFile(path)) {
            return java.util.Optional.empty();
        }
        try {
            return Files.readAllLines(path).stream()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .findFirst();
        } catch (IOException exc) {
            throw new UncheckedIOException("Failed to read " + path, exc);
        }
    }

    private static String readIfExists(Path path) {
        if (!Files.isRegularFile(path)) {
            return "";
        }
        try {
            return Files.readString(path);
        } catch (IOException exc) {
            throw new UncheckedIOException("Failed to read " + path, exc);
        }
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path.toAbsolutePath().normalize()).toString();
    }
}
