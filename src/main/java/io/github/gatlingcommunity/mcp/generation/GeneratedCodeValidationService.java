package io.github.gatlingcommunity.mcp.generation;

import io.github.gatlingcommunity.mcp.core.model.DslLanguage;
import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class GeneratedCodeValidationService {
    private static final Pattern SESSION_REFERENCE = Pattern.compile("#\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private static final Pattern SAVE_AS = Pattern.compile("\\.saveAs\\([\"']([^\"']+)[\"']\\)");
    private static final Set<String> COMMON_FEEDER_VALUES = Set.of("username", "password", "user", "email", "id");

    public Map<String, Object> validate(TargetContext target, String code) {
        var findings = new ArrayList<Map<String, Object>>();
        var source = code == null ? "" : code;
        validateLanguageShape(target.language(), source, findings);
        requireContains(findings, code, "http", "$.code", "generated_code.http.missing",
                "Generated HTTP code should use the Gatling HTTP DSL.");
        requireContains(findings, code, "check", "$.code", "generated_code.checks.missing",
                "Generated HTTP code should include checks.");
        if (source.contains("doIf(session -> true)")
                || source.contains("doIf(_ => true)")
                || source.contains("doIf(() => true)")
                || source.contains("doIf((session) => true)")) {
            findings.add(finding("error", "generated_code.unsafe_condition_always_true", "$.code",
                    "Generated code contains a doIf condition that always evaluates to true."));
        }
        validateCorrelation(source, findings);
        if (source.contains("unsupported rich feature") || source.contains("rich_feature.dropped")) {
            findings.add(finding("warning", "generated_code.rich_feature.dropped", "$.code",
                    "Generated code reports that a rich HTTP plan feature was not rendered."));
        }

        var values = new LinkedHashMap<String, Object>();
        values.put("valid", findings.stream().noneMatch(f -> "error".equals(f.get("severity"))));
        values.put("language", target.language().name());
        values.put("findings", findings);
        values.put("compileCheck", Map.of("executed", false, "status", "not-requested"));
        values.put("compileReadiness", compileReadiness(findings));
        values.put("recommendedCompileCommand", recommendedCompileCommand(target));
        return Map.copyOf(values);
    }

    private static void validateLanguageShape(DslLanguage language,
                                              String code,
                                              List<Map<String, Object>> findings) {
        switch (language) {
            case JAVA -> {
                requireContains(findings, code, "extends Simulation", "$.code", "generated_code.simulation.missing",
                        "Java Gatling code should extend Simulation.");
                requireContains(findings, code, "setUp(", "$.code", "generated_code.setup.missing",
                        "Generated code should call setUp.");
                if (code.contains("io.gatling.core.Predef") || code.contains("export default simulation")) {
                    findings.add(finding("error", "generated_code.dsl_import_mismatch", "$.code",
                            "Java validation received source that looks like another Gatling DSL."));
                }
            }
            case KOTLIN -> {
                requireContains(findings, code, ": Simulation()", "$.code", "generated_code.simulation.missing",
                        "Kotlin Gatling code should extend Simulation().");
                requireContains(findings, code, "setUp(", "$.code", "generated_code.setup.missing",
                        "Generated code should call setUp.");
                if (code.contains("import static") || code.contains("public class ")) {
                    findings.add(finding("error", "generated_code.dsl_import_mismatch", "$.code",
                            "Kotlin code must not contain Java static imports or Java class declarations."));
                }
            }
            case SCALA -> {
                requireContains(findings, code, "extends Simulation", "$.code", "generated_code.simulation.missing",
                        "Scala Gatling code should extend Simulation.");
                requireContains(findings, code, "setUp(", "$.code", "generated_code.setup.missing",
                        "Generated code should call setUp.");
                if (code.contains("import static") || code.contains("io.gatling.javaapi")) {
                    findings.add(finding("error", "generated_code.dsl_import_mismatch", "$.code",
                            "Scala code must not contain Java DSL imports."));
                }
            }
            case JAVASCRIPT, TYPESCRIPT -> {
                requireContains(findings, code, "export default simulation", "$.code",
                        "generated_code.simulation.missing",
                        "JS/TS Gatling code should export default simulation.");
                requireContains(findings, code, "setUp(", "$.code", "generated_code.setup.missing",
                        "Generated code should call setUp.");
                if (code.contains("extends Simulation") || code.contains("io.gatling.javaapi")) {
                    findings.add(finding("error", "generated_code.dsl_import_mismatch", "$.code",
                            "JS/TS code must not contain JVM DSL class declarations or imports."));
                }
            }
        }
    }

    private static void validateCorrelation(String code, List<Map<String, Object>> findings) {
        var saved = new java.util.LinkedHashSet<String>();
        SAVE_AS.matcher(code).results().map(match -> match.group(1)).forEach(saved::add);
        SESSION_REFERENCE.matcher(code).results()
                .map(match -> match.group(1))
                .distinct()
                .filter(name -> !COMMON_FEEDER_VALUES.contains(name))
                .filter(name -> !saved.contains(name))
                .forEach(name -> findings.add(finding(
                        "error",
                        "generated_code.correlation.reference_without_save",
                        "$.code",
                        "Session variable is referenced without a preceding saveAs: " + name
                )));
    }

    private static Map<String, Object> compileReadiness(List<Map<String, Object>> findings) {
        var hasErrors = findings.stream().anyMatch(finding -> "error".equals(finding.get("severity")));
        return Map.of(
                "status", hasErrors ? "blocked" : "ready",
                "canRunCompileCheck", !hasErrors,
                "reason", hasErrors
                        ? "Static validation found errors; fix them before running compile check."
                        : "Static validation passed; compile-only check is the next verification step."
        );
    }

    private static List<String> recommendedCompileCommand(TargetContext target) {
        return switch (target.buildTool()) {
            case MAVEN -> List.of("mvn", "-q", "-DskipTests", "test-compile");
            case GRADLE -> List.of("./gradlew", "testClasses");
            case SBT -> List.of("sbt", "Test/compile");
            case NPM -> List.of("npm", "test");
            case UNKNOWN -> List.of();
        };
    }

    private static void requireContains(List<Map<String, Object>> findings,
                                        String code,
                                        String required,
                                        String path,
                                        String codeValue,
                                        String message) {
        if (code == null || !code.contains(required)) {
            findings.add(finding("error", codeValue, path, message));
        }
    }

    private static Map<String, Object> finding(String severity, String code, String path, String message) {
        return Map.of(
                "severity", severity,
                "code", code,
                "path", path,
                "message", message,
                "suggestion", "Regenerate from a validated plan or inspect the rendered DSL manually."
        );
    }
}
