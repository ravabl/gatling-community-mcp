package io.github.gatlingcommunity.mcp.detect;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import io.github.gatlingcommunity.mcp.core.model.CommunityPlugin;
import io.github.gatlingcommunity.mcp.core.model.DslLanguage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class ProjectContextService {
    private static final Pattern XML_TAG = Pattern.compile("<%s>([^<]+)</%s>");
    private static final Pattern ARTIFACT_ID = Pattern.compile("<artifactId>([^<]+)</artifactId>");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([a-zA-Z_][\\w.]*);?");
    private static final Pattern JSON_NODE_ENGINE = Pattern.compile("\"node\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NPM_GATLING_VERSION = Pattern.compile("\"@gatling\\.io/[^\"]+\"\\s*:\\s*\"[~^]?([^\"]+)\"");
    private static final Pattern GRADLE_GATLING_VERSION = Pattern.compile("gatling[^\\n'\"]*['\"]([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)['\"]");
    private static final Pattern SBT_GATLING_VERSION = Pattern.compile("io\\.gatling[^\\n\"]+\"([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)\"");
    private static final Set<String> EXCLUDED_SCAN_DIRECTORIES = Set.of(
            ".git", ".gradle", ".idea", ".mvn", ".bsp", ".metals",
            "target", "build", "node_modules", "dist", "out"
    );
    private static final int MAX_SIMULATION_FILES = 200;
    private final List<BuildModelResolver> resolvers;

    public ProjectContextService() {
        this(defaultResolvers());
    }

    public ProjectContextService(List<BuildModelResolver> resolvers) {
        this.resolvers = List.copyOf(resolvers == null || resolvers.isEmpty()
                ? defaultResolvers()
                : resolvers);
    }

    private static List<BuildModelResolver> defaultResolvers() {
        return List.of(
                new MavenBuildModelResolver(),
                new GradleBuildModelResolver(),
                new SbtBuildModelResolver(),
                new NpmBuildModelResolver(),
                new ConventionOnlyBuildModelResolver()
        );
    }

    public ProjectContext inspect(Path root) {
        var normalized = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("Project path must be a directory: " + normalized);
        }
        var buildModel = buildModel(normalized);
        var buildTool = buildModel.buildTool();
        var buildText = buildText(normalized, buildTool);
        var sourceRoots = buildModel.sourceRoots().isEmpty()
                ? existingConventionalRoots(normalized, sourceRootCandidates())
                : buildModel.sourceRoots();
        var testRoots = buildModel.testRoots().isEmpty()
                ? existingConventionalRoots(normalized, testRootCandidates())
                : buildModel.testRoots();
        var simulationFiles = simulationFiles(normalized, sourceRoots, testRoots);
        var language = detectLanguage(buildTool, buildText, simulationFiles);
        var dependencies = buildModel.dependencies();
        var packages = packages(normalized, simulationFiles);

        var warnings = new ArrayList<String>(buildModel.warnings());
        if (simulationFiles.isEmpty()) {
            warnings.add("project.no_simulation_files");
        }
        var gatlingVersion = detectGatlingVersion(normalized, buildTool, buildText, buildModel.properties())
                .orElseGet(() -> {
                    warnings.add("project.gatling_version.unknown");
                    return "unknown";
                });

        return new ProjectContext(
                normalized,
                buildTool,
                language,
                gatlingVersion,
                detectJavaVersion(normalized, buildText, buildModel.properties()).orElse("unknown"),
                detectNodeVersion(normalized, buildText, buildModel.properties()).orElse("unknown"),
                sourceRoots,
                testRoots,
                simulationFiles,
                dependencyState(dependencies, buildTool, buildModel.properties()),
                pluginState(buildText, dependencies),
                packageNaming(packages),
                styleConventions(normalized, simulationFiles, language),
                warnings
        );
    }

    private BuildModel buildModel(Path root) {
        return resolvers.stream()
                .filter(resolver -> resolver.supports(root))
                .findFirst()
                .orElseGet(ConventionOnlyBuildModelResolver::new)
                .resolve(root);
    }

    private static BuildTool detectBuildTool(Path root) {
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

    private static String buildText(Path root, BuildTool buildTool) {
        return switch (buildTool) {
            case MAVEN -> readIfExists(root.resolve("pom.xml"));
            case GRADLE -> readIfExists(root.resolve("build.gradle")) + "\n" + readIfExists(root.resolve("build.gradle.kts"));
            case SBT -> readIfExists(root.resolve("build.sbt"));
            case NPM -> readIfExists(root.resolve("package.json"));
            case UNKNOWN -> "";
        };
    }

    private static DslLanguage detectLanguage(BuildTool buildTool, String buildText, List<String> simulationFiles) {
        var lower = buildText.toLowerCase(Locale.ROOT);
        if (lower.contains("kotlin-maven-plugin") || simulationFiles.stream().anyMatch(file -> file.endsWith(".kt"))) {
            return DslLanguage.KOTLIN;
        }
        if (lower.contains("scala-maven-plugin") || buildTool == BuildTool.SBT
                || simulationFiles.stream().anyMatch(file -> file.endsWith(".scala"))) {
            return DslLanguage.SCALA;
        }
        if (simulationFiles.stream().anyMatch(file -> file.endsWith(".ts"))) {
            return DslLanguage.TYPESCRIPT;
        }
        if (buildTool == BuildTool.NPM || simulationFiles.stream().anyMatch(file -> file.endsWith(".js"))) {
            return DslLanguage.JAVASCRIPT;
        }
        return DslLanguage.JAVA;
    }

    private static Optional<String> detectGatlingVersion(Path root,
                                                         BuildTool buildTool,
                                                         String buildText,
                                                         Map<String, String> properties) {
        var property = resolvedProperty(properties, "gatling.version").or(() -> xmlTag(buildText, "gatling.version"));
        if (property.isPresent()) {
            return property;
        }
        return switch (buildTool) {
            case MAVEN -> firstVersionNear(buildText, "gatling");
            case GRADLE -> firstMatch(GRADLE_GATLING_VERSION, buildText);
            case SBT -> firstMatch(SBT_GATLING_VERSION, buildText);
            case NPM -> firstMatch(NPM_GATLING_VERSION, buildText);
            case UNKNOWN -> Optional.empty();
        };
    }

    private static Optional<String> detectJavaVersion(Path root, String buildText, Map<String, String> properties) {
        return resolvedProperty(properties, "maven.compiler.release")
                .or(() -> resolvedProperty(properties, "maven.compiler.source"))
                .or(() -> resolvedProperty(properties, "java.version"))
                .or(() -> xmlTag(buildText, "maven.compiler.release"))
                .or(() -> xmlTag(buildText, "maven.compiler.source"))
                .or(() -> readFirstLine(root.resolve(".java-version")));
    }

    private static Optional<String> detectNodeVersion(Path root, String buildText, Map<String, String> properties) {
        return resolvedProperty(properties, "node.version")
                .or(() -> firstMatch(JSON_NODE_ENGINE, buildText))
                .or(() -> readFirstLine(root.resolve(".nvmrc")));
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

    private static Map<String, Object> dependencyState(List<String> dependencies,
                                                       BuildTool buildTool,
                                                       Map<String, String> resolvedProperties) {
        var values = new LinkedHashMap<String, Object>();
        values.put("buildTool", buildTool.name());
        values.put("dependencies", dependencies);
        values.put("gatlingArtifacts", dependencies.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).contains("gatling"))
                .toList());
        values.put("hasGatlingDependency", dependencies.stream()
                .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains("gatling")));
        values.put("resolvedProperties", resolvedProperties);
        return Map.copyOf(values);
    }

    private static Map<String, Object> pluginState(String buildText, List<String> dependencies) {
        var haystack = (buildText + "\n" + dependencies).toLowerCase(Locale.ROOT);
        var values = new LinkedHashMap<String, Object>();
        values.put(CommunityPlugin.KAFKA.name(), pluginInfo(haystack, "gatling-kafka-plugin"));
        values.put(CommunityPlugin.JDBC.name(), pluginInfo(haystack, "gatling-jdbc-plugin"));
        values.put(CommunityPlugin.AMQP.name(), pluginInfo(haystack, "gatling-amqp-plugin"));
        values.put(CommunityPlugin.PICATINNY.name(), pluginInfo(haystack, "gatling-picatinny"));
        return Map.copyOf(values);
    }

    private static Map<String, Object> pluginInfo(String haystack, String marker) {
        return Map.of(
                "detected", haystack.contains(marker),
                "marker", marker
        );
    }

    private static Map<String, Object> packageNaming(List<String> packages) {
        var values = new LinkedHashMap<String, Object>();
        values.put("primaryPackage", packages.isEmpty() ? "default-package" : packages.getFirst());
        values.put("packages", packages);
        values.put("usesDefaultPackage", packages.isEmpty());
        return Map.copyOf(values);
    }

    private static List<String> styleConventions(Path root, List<String> simulationFiles, DslLanguage language) {
        var conventions = new LinkedHashSet<String>();
        conventions.add("dsl-" + language.name().toLowerCase(Locale.ROOT));
        for (var file : simulationFiles) {
            var text = readIfExists(root.resolve(file));
            if (text.contains("import static io.gatling")) {
                conventions.add("static-imports");
            }
            if (text.contains("extends Simulation")) {
                conventions.add("class-extends-simulation");
            }
            if (text.contains("simulation(")) {
                conventions.add("function-simulation");
            }
        }
        return List.copyOf(conventions);
    }

    private static List<String> simulationFiles(Path root, List<String> sourceRoots, List<String> testRoots) {
        var matches = new HashSet<String>();
        var searchRoots = new LinkedHashSet<Path>();
        for (var configuredRoot : ordered(sourceRoots, testRoots)) {
            var candidate = root.resolve(configuredRoot).normalize();
            if (candidate.startsWith(root) && Files.isDirectory(candidate)) {
                searchRoots.add(candidate);
            }
        }
        if (searchRoots.isEmpty()) {
            searchRoots.add(root);
        } else {
            scanProjectRootFiles(root, matches);
        }

        for (var searchRoot : searchRoots) {
            scanSimulationFiles(root, searchRoot, matches);
            if (matches.size() >= MAX_SIMULATION_FILES) {
                break;
            }
        }
        return matches.stream().sorted().limit(MAX_SIMULATION_FILES).toList();
    }

    private static void scanProjectRootFiles(Path root, Set<String> matches) {
        try (var entries = Files.list(root)) {
            entries.filter(Files::isRegularFile)
                    .filter(ProjectContextService::isSimulationCandidate)
                    .filter(ProjectContextService::containsSimulationMarker)
                    .map(path -> relative(root, path))
                    .forEach(matches::add);
        } catch (IOException exc) {
            throw new UncheckedIOException("Failed to scan project root: " + root, exc);
        }
    }

    private static void scanSimulationFiles(Path projectRoot, Path searchRoot, Set<String> matches) {
        try {
            Files.walkFileTree(searchRoot, Set.of(), 12, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (!directory.equals(searchRoot)
                            && EXCLUDED_SCAN_DIRECTORIES.contains(directory.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isRegularFile()
                            && isSimulationCandidate(file)
                            && containsSimulationMarker(file)) {
                        matches.add(relative(projectRoot, file));
                    }
                    return matches.size() >= MAX_SIMULATION_FILES
                            ? FileVisitResult.TERMINATE
                            : FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exc) {
            throw new UncheckedIOException("Failed to scan project simulations: " + searchRoot, exc);
        }
    }

    private static boolean isSimulationCandidate(Path path) {
        var name = path.getFileName().toString();
        return name.endsWith(".java") || name.endsWith(".scala") || name.endsWith(".kt")
                || name.endsWith(".js") || name.endsWith(".ts");
    }

    private static boolean containsSimulationMarker(Path path) {
        var text = readIfExists(path);
        return text.contains("extends Simulation") || text.contains("simulation(");
    }

    private static List<String> packages(Path root, List<String> simulationFiles) {
        var values = new LinkedHashSet<String>();
        for (var file : simulationFiles) {
            var matcher = PACKAGE.matcher(readIfExists(root.resolve(file)));
            if (matcher.find()) {
                values.add(matcher.group(1));
            }
        }
        return values.stream().sorted().toList();
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

    private static Optional<String> xmlTag(String text, String tag) {
        var matcher = Pattern.compile(XML_TAG.pattern().formatted(Pattern.quote(tag), Pattern.quote(tag))).matcher(text);
        return matcher.find() ? Optional.of(matcher.group(1).trim()) : Optional.empty();
    }

    private static Optional<String> resolvedProperty(Map<String, String> properties, String key) {
        var value = properties.get(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static Optional<String> firstVersionNear(String text, String marker) {
        var index = text.toLowerCase(Locale.ROOT).indexOf(marker);
        if (index < 0) {
            return Optional.empty();
        }
        return firstMatch(Pattern.compile("([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)"), text.substring(index));
    }

    private static Optional<String> firstMatch(Pattern pattern, String text) {
        var matcher = pattern.matcher(text);
        return matcher.find() ? Optional.of(matcher.group(1).trim()) : Optional.empty();
    }

    private static Optional<String> readFirstLine(Path path) {
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
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

    private static <T> List<T> ordered(List<T> first, List<T> second) {
        var values = new LinkedHashSet<T>();
        values.addAll(first == null ? List.of() : first);
        values.addAll(second == null ? List.of() : second);
        return List.copyOf(values);
    }
}
