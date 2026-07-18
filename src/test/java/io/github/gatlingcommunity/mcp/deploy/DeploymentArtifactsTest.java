package io.github.gatlingcommunity.mcp.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DeploymentArtifactsTest {
    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(\\S.*)$");
    private static final Pattern VERSION = Pattern.compile("<version>(\\d+\\.\\d+\\.\\d+)</version>");

    @Test
    void dockerfileBuildsAndRunsAsNonRootOnJava25() throws Exception {
        var dockerfile = read("Dockerfile");

        assertThat(dockerfile).contains(
                "maven:3.9.11-eclipse-temurin-25 AS build",
                "FROM maven:3.9.11-eclipse-temurin-25",
                "org.opencontainers.image.title=\"MCP Gatling-community\"",
                "mvn -q -DskipTests package",
                "USER 10001",
                "ENTRYPOINT [\"java\", \"-jar\"",
                "gatling-community-mcp.jar",
                "EXPOSE 8765");
    }

    @Test
    void dockerignoreExcludesBuildVcsAndInternalDocumentation() throws Exception {
        var dockerignore = read(".dockerignore");

        assertThat(dockerignore).contains("target/", ".git/", ".DS_Store", "docs/");
    }

    @Test
    void smokeRunnerCoversTheCompleteMcpLifecycleAndStressPath() throws Exception {
        var smoke = read("scripts/mcp-smoke.py");

        assertThat(smoke).contains(
                "initialize", "notifications/initialized", "tools/list", "tools/call",
                "resources/list", "resources/read", "resources/templates/list",
                "prompts/list", "prompts/get", "completion/complete",
                "gatling_validate_method_chain", "gatling_compile_check",
                "gatling_generate_simulation", "gatling_analyze_report",
                "for iteration in range(128)", "stdio transport stress call");
    }

    @Test
    void quickStartIsTheFirstOperationalSectionInBothReadmes() throws Exception {
        var readme = read("README.md");
        var readmeRu = read("README_RU.md");

        assertThat(firstHeadingAtLevel(readme, 2)).isEqualTo("Quick Start");
        assertThat(firstHeadingAtLevel(readmeRu, 2)).isEqualTo("Быстрый старт");
        assertThat(section(readme, "Quick Start")).contains(
                "### Docker", "### Standalone JAR", "127.0.0.1:8765/mcp",
                "GATLING_MCP_WORKSPACE_ROOTS=/workspace", "java -version");
        assertThat(section(readmeRu, "Быстрый старт")).contains(
                "### Docker", "### Standalone JAR", "127.0.0.1:8765/mcp",
                "GATLING_MCP_WORKSPACE_ROOTS=/workspace", "java -version");
    }

    @Test
    void bilingualReadmesHaveMatchingStructureCommandsAndLinks() throws Exception {
        var readme = read("README.md");
        var readmeRu = read("README_RU.md");

        assertThat(headingLevels(readmeRu)).containsExactlyElementsOf(headingLevels(readme));
        assertThat(fencedCodeBlocks(readmeRu)).containsExactlyElementsOf(fencedCodeBlocks(readme));
        assertThat(normalizedLinks(readmeRu)).containsExactlyElementsOf(normalizedLinks(readme));
    }

    @Test
    void readmesDocumentTheRichPublicSurface() throws Exception {
        var required = List.of(
                "gatling_get_project_context", "gatling_list_dsl_methods",
                "gatling_validate_method_chain", "gatling_generate_from_plan",
                "gatling_validate_generated_code", "gatling_compile_check",
                "gatling_import_openapi", "gatling_import_har", "gatling_import_curl",
                "gatling_import_postman_collection", "gatling_analyze_report",
                "gatling_analyze_log", "gatling_explain_errors",
                "gatling_check_feeder_risk", "gatling_check_correlation_risk",
                "gatling_check_assertion_quality", "gatling_interaction_status");

        assertThat(read("README.md")).contains(required.toArray(String[]::new));
        assertThat(read("README_RU.md")).contains(required.toArray(String[]::new));
    }

    @Test
    void readmesDocumentExactStrictVerifiedPluginTargets() throws Exception {
        var required = List.of("1.0.6", "1.2.0", "1.3.3", "1.24.0", "3.13.5",
                "plugin.compatibility.unverified", "plugin.dsl.unsupported",
                "plugin.build-tool.unsupported", "plugin.java.unsupported");

        assertThat(read("README.md")).contains(required.toArray(String[]::new));
        assertThat(read("README_RU.md")).contains(required.toArray(String[]::new));
        assertThat(read(".dockerhub/README.md")).contains(required.subList(0, 5).toArray(String[]::new));
        assertThat(read("src/main/resources/data/gatling-community-plugin-matrix.json"))
                .contains("verifiedTargets", "verifiedAgainstGatlingVersion", "PLUGIN_RELEASE");
    }

    @Test
    void publicDocumentationDoesNotLinkToInternalFilesOrExposePersonalData() throws Exception {
        for (var path : List.of("README.md", "README_RU.md", ".dockerhub/README.md")) {
            var content = read(path);
            assertThat(content).doesNotContain(
                    "](docs/", "CONTRIBUTING.md", "docs/superpowers", "ravil" + "ablyamitov",
                    "GATLING_MCP_" + "TOKEN", "Bear" + "er");
        }
    }

    @Test
    void internalDocumentationPolicyIsEncodedInIgnoreFiles() throws Exception {
        assertThat(read(".gitignore")).contains("/docs/", "/CONTRIBUTING.md");
        assertThat(read(".dockerignore")).contains("docs/", "CONTRIBUTING.md");
    }

    @Test
    void dockerHubOverviewStartsWithQuickStartAndDocumentsAllRuntimeModes() throws Exception {
        var overview = read(".dockerhub/README.md");

        assertThat(firstHeadingAtLevel(overview, 2)).isEqualTo("Quick Start");
        assertThat(overview).contains(
                "### Docker", "### Standalone JAR", "docker run --rm -d",
                "--publish 127.0.0.1:8765:8765", "http --bind 0.0.0.0 --port 8765",
                "Local MCP Clients", "\"run\"", "\"-i\"", "Do not put `-d`",
                "\"bridge\"", "\"--url\"", "type=bind,source=/absolute/path/to/project,target=/workspace,readonly");
    }

    @Test
    void releaseVersionIsSynchronizedAcrossPublicArtifacts() throws Exception {
        var matcher = VERSION.matcher(read("pom.xml"));
        assertThat(matcher.find()).isTrue();
        var version = matcher.group(1);
        var minor = version.substring(0, version.lastIndexOf('.'));

        assertThat(read("README.md")).contains(version, "`%s`".formatted(minor));
        assertThat(read("README_RU.md")).contains(version, "`%s`".formatted(minor));
        assertThat(read(".dockerhub/README.md")).contains(version, "`%s`".formatted(minor));
        assertThat(read(".github/workflows/docker-release.yml"))
                .contains("target/gatling-community-mcp-%s-all.jar".formatted(version));
    }

    @Test
    void releaseWorkflowPublishesAndSmokeTestsStdioHttpAndBridge() throws Exception {
        var workflow = read(".github/workflows/docker-release.yml");

        assertThat(workflow).contains(
                "Build and publish Docker image", "Smoke-test published image",
                "Smoke-test published image HTTP daemon and bridge",
                "docker network create", "bridge --url http://mcp-gatling-community-release-http:8765/mcp",
                "dockerhub-description", "Apply Docker Hub retention", "dry_run: false");
        assertThat(workflow.split("--read-only", -1).length - 1).isGreaterThanOrEqualTo(4);
    }

    @Test
    void retentionWorkflowKeepsOnlyCurrentStableAliases() throws Exception {
        var workflow = read(".github/workflows/dockerhub-retention.yml");

        assertThat(workflow).contains(
                "name: Docker Hub Retention", "workflow_dispatch", "workflow_call",
                "KEEP_TAGS", "KEEP_DIGESTS", "CANDIDATE_DIGESTS", "extra_delete_digests",
                "registry-1.docker.io", "latest", "DELETE", "DOCKERHUB_TOKEN", "DOCKERHUB_IMAGE");
        assertThat(read(".dockerhub/README.md")).contains(
                "## Tag Retention", "Do not rely on `latest` alone", "old unreferenced manifest digests");
    }

    @Test
    void issueAndContributionFlowRemainSelfContained() throws Exception {
        var readme = read("README.md");

        assertThat(readme).contains(
                "## How To Contribute", "## Issue Flow", "what changed", "verification commands");
        assertThat(read(".github/ISSUE_TEMPLATE/config.yml")).contains("blank_issues_enabled: false");
        assertThat(read(".github/PULL_REQUEST_TEMPLATE.md")).contains("## What changed", "## Verification");
        for (var template : List.of("bug_report.yml", "feature_request.yml", "docs.yml",
                "compatibility.yml", "community_plugin.yml", "question.yml")) {
            assertThat(PROJECT_ROOT.resolve(".github/ISSUE_TEMPLATE").resolve(template)).exists();
        }
    }

    @Test
    void consolidatedReadmesContainFormerStandaloneDocumentationTopics() throws Exception {
        assertThat(read("README.md")).contains(
                "## Source Data Maintenance", "## Release Notes", "### 0.4.0", "## License",
                "src/main/resources/data", "Apache License 2.0");
        assertThat(read("README_RU.md")).contains(
                "## Обновление исходных данных", "## Примечания к выпуску", "### 0.4.0", "## Лицензия",
                "src/main/resources/data", "Apache License 2.0");
        assertThat(read("pom.xml")).contains(
                "jacoco-maven-plugin", "<counter>LINE</counter>", "<minimum>0.85</minimum>",
                "<counter>BRANCH</counter>", "<minimum>0.60</minimum>");
    }

    @Test
    void demoFixturesRemainExecutableInputsRatherThanDocumentation() throws Exception {
        assertThat(read("examples/openapi/orders-api.yaml"))
                .contains("openapi: 3.0.3", "/login", "/orders/{userId}", "bearerAuth", "token", "userId");
        assertThat(read("examples/curl/login-orders.sh"))
                .contains("curl", "/login", "/orders", "Authorization: Bearer {{token}}");
    }

    @Test
    void runtimeCommandsPreserveStdioAndDaemonSafetyBoundaries() throws Exception {
        var readme = read("README.md");

        assertThat(readme).contains(
                "docker run --rm -i", "container run --rm -i",
                "Do not use `-d`; the MCP client must own the child process pipes",
                "docker run --rm -d", "--publish 127.0.0.1:8765:8765",
                "No content to map due to end-of-input", "path.outside_workspace");
    }

    private static String read(String relativePath) throws Exception {
        return Files.readString(PROJECT_ROOT.resolve(relativePath));
    }

    private static String firstHeadingAtLevel(String markdown, int level) {
        return headings(markdown).stream()
                .filter(heading -> heading.level() == level)
                .findFirst()
                .orElseThrow()
                .title();
    }

    private static List<Heading> headings(String markdown) {
        return markdown.lines()
                .map(HEADING::matcher)
                .filter(matcher -> matcher.matches())
                .map(matcher -> new Heading(matcher.group(1).length(), matcher.group(2)))
                .toList();
    }

    private static List<Integer> headingLevels(String markdown) {
        return headings(markdown).stream().map(Heading::level).toList();
    }

    private static String section(String markdown, String heading) {
        var marker = "## " + heading;
        var start = markdown.indexOf(marker);
        if (start < 0) {
            throw new AssertionError("Missing section: " + heading);
        }
        var end = markdown.indexOf("\n## ", start + marker.length());
        return markdown.substring(start, end < 0 ? markdown.length() : end);
    }

    private static List<CodeFence> fencedCodeBlocks(String markdown) {
        var result = new ArrayList<CodeFence>();
        String language = null;
        var content = new StringBuilder();
        for (var line : markdown.split("\\R", -1)) {
            if (line.startsWith("```")) {
                if (language == null) {
                    language = line.substring(3).trim();
                    content.setLength(0);
                } else {
                    result.add(new CodeFence(language, content.toString()));
                    language = null;
                }
            } else if (language != null) {
                if (!content.isEmpty()) {
                    content.append('\n');
                }
                content.append(line);
            }
        }
        assertThat(language).as("unclosed fenced code block").isNull();
        return result;
    }

    private static List<String> normalizedLinks(String markdown) {
        var pattern = Pattern.compile("\\[[^]]+]\\(([^)]+)\\)");
        var values = new ArrayList<String>();
        var matcher = pattern.matcher(markdown);
        while (matcher.find()) {
            var value = matcher.group(1);
            values.add(value.equals("README.md") || value.equals("README_RU.md") ? "<language-readme>" : value);
        }
        return values;
    }

    private record Heading(int level, String title) {
    }

    private record CodeFence(String language, String content) {
    }
}
