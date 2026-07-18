package io.github.gatlingcommunity.mcp.detect;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import io.github.gatlingcommunity.mcp.core.model.DslLanguage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ProjectDetector {
    private static final Pattern GATLING_VERSION =
            Pattern.compile("<gatling\\.version>([^<]+)</gatling\\.version>");

    public ProjectDetectionResult detect(Path root) {
        var normalized = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("Project path must be a directory: " + normalized);
        }
        var pom = normalized.resolve("pom.xml");
        if (Files.exists(pom)) {
            return detectMaven(pom);
        }
        if (Files.exists(normalized.resolve("build.gradle")) || Files.exists(normalized.resolve("build.gradle.kts"))) {
            return new ProjectDetectionResult(BuildTool.GRADLE, DslLanguage.JAVA, Optional.empty(), "build-file");
        }
        if (Files.exists(normalized.resolve("build.sbt"))) {
            return new ProjectDetectionResult(BuildTool.SBT, DslLanguage.SCALA, Optional.empty(), "build-file");
        }
        if (Files.exists(normalized.resolve("package.json"))) {
            return new ProjectDetectionResult(BuildTool.NPM, DslLanguage.TYPESCRIPT, Optional.empty(), "build-file");
        }
        return new ProjectDetectionResult(BuildTool.UNKNOWN, DslLanguage.JAVA, Optional.empty(), "none");
    }

    private static ProjectDetectionResult detectMaven(Path pom) {
        try {
            var text = Files.readString(pom);
            var matcher = GATLING_VERSION.matcher(text);
            var version = matcher.find() ? Optional.of(matcher.group(1)) : Optional.<String>empty();
            var language = text.contains("kotlin-maven-plugin") ? DslLanguage.KOTLIN
                    : text.contains("scala-maven-plugin") ? DslLanguage.SCALA
                    : DslLanguage.JAVA;
            return new ProjectDetectionResult(BuildTool.MAVEN, language, version, "pom.xml");
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read pom.xml: " + pom, e);
        }
    }
}
