package io.github.gatlingcommunity.mcp.data;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import io.github.gatlingcommunity.mcp.core.model.DslLanguage;
import io.github.gatlingcommunity.mcp.core.model.DslMethodCategory;
import io.github.gatlingcommunity.mcp.core.model.Protocol;
import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import io.github.gatlingcommunity.mcp.core.model.GatlingVersion;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DslMethodCatalogTest {
    private final SourceDataRepository repository = SourceDataRepository.loadDefault();

    @Test
    void exposesHttpMethodMatrixForEverySupportedVersionLineAndCoreDsl() {
        for (var version : repository.supportedGatlingVersions()) {
            for (var language : DslLanguage.values()) {
                var target = new TargetContext(version, "community", language, buildTool(language),
                        javaVersion(language), nodeVersion(language), Optional.empty());

                var methods = repository.dslMethods(target, Protocol.HTTP);

                if (isJavascriptOrTypescript(language) && !GatlingVersion.atLeast(version, "3.11.2")) {
                    assertThat(methods)
                            .describedAs("HTTP DSL methods for unsupported %s %s", version, language)
                            .isEmpty();
                    continue;
                }

                assertThat(methods)
                        .describedAs("HTTP DSL methods for %s %s", version, language)
                        .hasSizeGreaterThanOrEqualTo(95)
                        .anySatisfy(method -> {
                            assertThat(method.category()).isEqualTo(DslMethodCategory.HTTP_REQUEST);
                            assertThat(method.name()).isEqualTo("get");
                            assertThat(method.callTemplate()).contains(".get(");
                        })
                        .anySatisfy(method -> assertThat(method.name()).isEqualTo("post"))
                        .anySatisfy(method -> assertThat(method.name()).isEqualTo("status.is"))
                        .anySatisfy(method -> assertThat(method.name()).isEqualTo("jsonPath.saveAs"))
                        .anySatisfy(method -> assertThat(method.name()).isEqualTo("global.failedRequests.percent.lt"));
            }
        }
    }

    @Test
    void exposesDslSpecificCallTemplates() {
        var javaTarget = new TargetContext("3.15.1", "community", DslLanguage.JAVA, BuildTool.MAVEN,
                Optional.of("25"), Optional.empty(), Optional.empty());
        var scalaTarget = new TargetContext("3.15.1", "community", DslLanguage.SCALA, BuildTool.SBT,
                Optional.of("25"), Optional.empty(), Optional.empty());
        var kotlinTarget = new TargetContext("3.15.1", "community", DslLanguage.KOTLIN, BuildTool.MAVEN,
                Optional.of("25"), Optional.empty(), Optional.empty());

        assertThat(repository.dslMethods(javaTarget, Protocol.HTTP))
                .anySatisfy(method -> {
                    assertThat(method.name()).isEqualTo("status.is");
                    assertThat(method.callTemplate()).contains("status().is(200)");
                });
        assertThat(repository.dslMethods(scalaTarget, Protocol.HTTP))
                .anySatisfy(method -> {
                    assertThat(method.name()).isEqualTo("status.is");
                    assertThat(method.callTemplate()).contains("status.is(200)");
                });
        assertThat(repository.dslMethods(kotlinTarget, Protocol.HTTP))
                .anySatisfy(method -> {
                    assertThat(method.name()).isEqualTo("status.is");
                    assertThat(method.callTemplate()).contains("status().shouldBe(200)");
                });
    }

    @Test
    void exposesDslSpecificSemanticSyntaxFromOverridesAndMethodMatrix() {
        var javaTarget = new TargetContext("3.15.1", "community", DslLanguage.JAVA, BuildTool.MAVEN,
                Optional.of("25"), Optional.empty(), Optional.empty());

        var status = repository.dslMethods(javaTarget, Protocol.HTTP).stream()
                .filter(method -> method.name().equals("status.is"))
                .findFirst()
                .orElseThrow();
        var statusSemantic = repository.semanticRule(status).orElseThrow();

        assertThat(statusSemantic.dslSpecificSyntax())
                .containsEntry(DslLanguage.JAVA, "status().is(200)")
                .containsEntry(DslLanguage.KOTLIN, "status().shouldBe(200)")
                .containsEntry(DslLanguage.SCALA, "status.is(200)");

        var during = repository.dslMethods(javaTarget, Protocol.HTTP).stream()
                .filter(method -> method.name().equals("during"))
                .findFirst()
                .orElseThrow();
        var duringSemantic = repository.semanticRule(during).orElseThrow();

        assertThat(duringSemantic.dslSpecificSyntax())
                .containsEntry(DslLanguage.JAVA, ".during(Duration.ofSeconds(seconds)).on(chain)")
                .containsEntry(DslLanguage.SCALA, ".during(seconds.seconds).on(chain)")
                .containsEntry(DslLanguage.JAVASCRIPT, ".during(seconds).on(chain)");
    }

    @Test
    void normalizesPatchVersionsToTheirSupportedMethodLine() {
        for (var language : List.of(DslLanguage.JAVA, DslLanguage.KOTLIN, DslLanguage.SCALA)) {
            var target = new TargetContext("3.9.5", "community", language, buildTool(language),
                    javaVersion(language), nodeVersion(language), Optional.empty());

            assertThat(repository.dslMethods(target, Protocol.HTTP))
                    .describedAs("HTTP DSL methods for patch version 3.9.5 %s", language)
                    .anySatisfy(method -> assertThat(method.name()).isEqualTo("get"))
                    .anySatisfy(method -> assertThat(method.name()).isEqualTo("jsonPath.saveAs"));
        }
    }

    @Test
    void exposesDocumentedHttpRequestShapingMethodsAndHonorsJavascriptExclusions() {
        var javaTarget = new TargetContext("3.9.5", "community", DslLanguage.JAVA, BuildTool.MAVEN,
                Optional.of("25"), Optional.empty(), Optional.empty());
        var javascriptTarget = new TargetContext("3.11.2", "community", DslLanguage.JAVASCRIPT, BuildTool.NPM,
                Optional.empty(), Optional.of("22"), Optional.empty());
        var typescriptTarget = new TargetContext("3.11.2", "community", DslLanguage.TYPESCRIPT, BuildTool.NPM,
                Optional.empty(), Optional.of("22"), Optional.empty());

        var javaNames = repository.dslMethods(javaTarget, Protocol.HTTP).stream()
                .map(io.github.gatlingcommunity.mcp.core.model.GatlingDslMethod::name)
                .collect(java.util.stream.Collectors.toSet());
        var javascriptNames = repository.dslMethods(javascriptTarget, Protocol.HTTP).stream()
                .map(io.github.gatlingcommunity.mcp.core.model.GatlingDslMethod::name)
                .collect(java.util.stream.Collectors.toSet());
        var typescriptNames = repository.dslMethods(typescriptTarget, Protocol.HTTP).stream()
                .map(io.github.gatlingcommunity.mcp.core.model.GatlingDslMethod::name)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(javaNames).contains(
                "httpRequest", "ignoreProtocolChecks", "requestTimeout", "digestAuth",
                "form", "formUpload", "bodyParts", "body.InputStreamBody",
                "processRequestBody", "formParamSeq", "queryParamSeq", "multivaluedQueryParam"
        );
        assertThat(javascriptNames).contains(
                "httpRequest", "ignoreProtocolChecks", "requestTimeout", "digestAuth",
                "form", "formUpload", "bodyParts", "multivaluedQueryParam"
        );
        assertThat(javascriptNames).doesNotContain(
                "body.InputStreamBody", "processRequestBody", "formParamSeq", "queryParamSeq"
        );
        assertThat(typescriptNames).doesNotContain(
                "body.InputStreamBody", "processRequestBody", "formParamSeq", "queryParamSeq"
        );
    }

    @Test
    void gatesJavascriptAndTypescriptDslMethodsAtTheirFirstPublishedVersion() {
        for (var language : List.of(DslLanguage.JAVASCRIPT, DslLanguage.TYPESCRIPT)) {
            assertThat(repository.dslMethods(target("3.9.5", language), Protocol.HTTP))
                    .describedAs("%s DSL methods before the JavaScript SDK release", language)
                    .isEmpty();
            assertThat(repository.dslMethods(target("3.11.1", language), Protocol.HTTP))
                    .describedAs("%s DSL methods before 3.11.2", language)
                    .isEmpty();
            assertThat(repository.dslMethods(target("3.11.2", language), Protocol.HTTP))
                    .describedAs("%s DSL methods at 3.11.2", language)
                    .anySatisfy(method -> assertThat(method.name()).isEqualTo("get"));
        }
    }

    @Test
    void keepsJvmDslMethodsAvailableForGatling395() {
        for (var language : List.of(DslLanguage.JAVA, DslLanguage.KOTLIN, DslLanguage.SCALA)) {
            assertThat(repository.dslMethods(target("3.9.5", language), Protocol.HTTP))
                    .describedAs("%s JVM DSL methods for 3.9.5", language)
                    .anySatisfy(method -> assertThat(method.name()).isEqualTo("httpRequest"))
                    .anySatisfy(method -> assertThat(method.name()).isEqualTo("formParamSeq"))
                    .anySatisfy(method -> assertThat(method.name()).isEqualTo("sitemap"))
                    .anySatisfy(method -> assertThat(method.name()).isEqualTo("recordsCount"))
                    .anySatisfy(method -> assertThat(method.name()).isEqualTo("jdbcFeeder"))
                    .anySatisfy(method -> assertThat(method.name()).isEqualTo("redisFeeder"))
                    .anySatisfy(method -> assertThat(method.name()).isEqualTo("details.requestsPerSec.between"));
        }
    }

    @Test
    void honorsJVMOnlyFeederSupportAndKeepsStrategiesOnGatling315() {
        var javaNames = repository.dslMethods(target("3.15.1", DslLanguage.JAVA), Protocol.HTTP).stream()
                .map(io.github.gatlingcommunity.mcp.core.model.GatlingDslMethod::name)
                .collect(java.util.stream.Collectors.toSet());
        var javascriptNames = repository.dslMethods(
                        target("3.15.1", DslLanguage.JAVASCRIPT), Protocol.HTTP).stream()
                .map(io.github.gatlingcommunity.mcp.core.model.GatlingDslMethod::name)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(javaNames).contains(
                "ssv", "tsv", "sitemap", "listFeeder", "jdbcFeeder", "redisFeeder",
                "recordsCount", "queue", "random", "shuffle", "circular");
        assertThat(javascriptNames)
                .contains("ssv", "tsv", "sitemap", "recordsCount", "queue", "random", "shuffle", "circular")
                .doesNotContain("listFeeder", "jdbcFeeder", "redisFeeder");
    }

    @Test
    void exposesSourceDataDrivenSemanticRulesForEveryCatalogMethod() {
        for (var version : repository.supportedGatlingVersions()) {
            for (var language : DslLanguage.values()) {
                var target = new TargetContext(version, "community", language, buildTool(language),
                        javaVersion(language), nodeVersion(language), Optional.empty());

                for (var method : repository.dslMethods(target, Protocol.HTTP)) {
                    var semantic = repository.semanticRule(method);

                    assertThat(semantic)
                            .describedAs("semantic rule for %s %s %s", version, language, method.name())
                            .isPresent();
                    assertThat(semantic.orElseThrow().allowedParentContext()).isNotBlank();
                    assertThat(semantic.orElseThrow().returnType()).isNotBlank();
                    assertThat(semantic.orElseThrow().chainType()).isNotBlank();
                    assertThat(semantic.orElseThrow().dslSpecificSyntax()).containsKey(language);
                    assertThat(semantic.orElseThrow().compileRiskNotes()).isNotBlank();
                }
            }
        }
    }

    @Test
    void filtersDslMethodsByVersionLine() {
        var target36 = new TargetContext("3.6", "community", DslLanguage.JAVA, BuildTool.MAVEN,
                Optional.of("17"), Optional.empty(), Optional.empty());
        var target310 = new TargetContext("3.10", "community", DslLanguage.JAVA, BuildTool.MAVEN,
                Optional.of("17"), Optional.empty(), Optional.empty());
        var target311 = new TargetContext("3.11", "community", DslLanguage.JAVA, BuildTool.MAVEN,
                Optional.of("17"), Optional.empty(), Optional.empty());
        var target315Patch = new TargetContext("3.15.1", "community", DslLanguage.JAVA, BuildTool.MAVEN,
                Optional.of("25"), Optional.empty(), Optional.empty());

        assertThat(repository.dslMethods(target36, Protocol.HTTP)).isEmpty();
        assertThat(repository.dslMethods(target310, Protocol.HTTP))
                .noneSatisfy(method -> assertThat(method.name()).isEqualTo("stressPeakUsers"));
        assertThat(repository.dslMethods(target311, Protocol.HTTP))
                .anySatisfy(method -> assertThat(method.name()).isEqualTo("stressPeakUsers"));
        assertThat(repository.dslMethods(target315Patch, Protocol.HTTP))
                .anySatisfy(method -> {
                    assertThat(method.name()).isEqualTo("httpConcurrentRequests");
                    assertThat(method.since()).isEqualTo("3.15");
                })
                .anySatisfy(method -> assertThat(method.name()).isEqualTo("queue"))
                .anySatisfy(method -> assertThat(method.name()).isEqualTo("random"))
                .anySatisfy(method -> assertThat(method.name()).isEqualTo("shuffle"))
                .anySatisfy(method -> assertThat(method.name()).isEqualTo("circular"));
    }

    private static BuildTool buildTool(DslLanguage language) {
        return switch (language) {
            case JAVA, KOTLIN -> BuildTool.MAVEN;
            case SCALA -> BuildTool.SBT;
            case JAVASCRIPT, TYPESCRIPT -> BuildTool.NPM;
        };
    }

    private static Optional<String> javaVersion(DslLanguage language) {
        return switch (language) {
            case JAVA, KOTLIN, SCALA -> Optional.of("25");
            case JAVASCRIPT, TYPESCRIPT -> Optional.empty();
        };
    }

    private static Optional<String> nodeVersion(DslLanguage language) {
        return switch (language) {
            case JAVA, KOTLIN, SCALA -> Optional.empty();
            case JAVASCRIPT, TYPESCRIPT -> Optional.of("24");
        };
    }

    private static TargetContext target(String version, DslLanguage language) {
        return new TargetContext(version, "community", language, buildTool(language),
                javaVersion(language), nodeVersion(language), Optional.empty());
    }

    private static boolean isJavascriptOrTypescript(DslLanguage language) {
        return language == DslLanguage.JAVASCRIPT || language == DslLanguage.TYPESCRIPT;
    }
}
