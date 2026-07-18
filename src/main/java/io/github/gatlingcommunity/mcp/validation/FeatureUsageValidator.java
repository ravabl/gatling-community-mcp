package io.github.gatlingcommunity.mcp.validation;

import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import io.github.gatlingcommunity.mcp.data.SourceDataRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class FeatureUsageValidator {
    private static final Pattern INJECTION_IN_SCENARIO = Pattern.compile(
            "scenario\\s*\\([^)]*\\)(?:(?!setUp\\s*\\(|(?:val|var|final|const|let)\\s+).){0,2000}?\\.inject(?:Open|Closed)?\\s*\\(",
            Pattern.DOTALL);
    private static final Pattern REQUEST_IN_SETUP = Pattern.compile(
            "setUp\\s*\\(\\s*scenario\\s*\\([^)]*\\)(?:(?!\\.inject(?:Open|Closed)?\\s*\\().){0,2000}?\\.exec\\s*\\(\\s*http\\s*\\(",
            Pattern.DOTALL);
    private static final Pattern CASE_CLASS = Pattern.compile("\\bclass\\s+\\w*Case\\b");
    private static final Pattern SCENARIO_CLASS = Pattern.compile("\\bclass\\s+\\w*Scenario\\b");
    private static final Pattern SIMULATION_CLASS = Pattern.compile("\\bclass\\s+\\w*Simulation\\b");
    private final SourceDataRepository sourceData;
    private final SecretMasker secretMasker;

    public FeatureUsageValidator(SourceDataRepository sourceData, SecretMasker secretMasker) {
        this.sourceData = sourceData;
        this.secretMasker = secretMasker;
    }

    public ValidationResult validate(TargetContext target, String code) {
        var findings = new ArrayList<ValidationFinding>();
        var source = code == null ? "" : code;

        for (var rule : sourceData.featureRulesFor(target.gatlingVersion())) {
            if (!rule.invalidPattern().isBlank() && source.contains(rule.invalidPattern())) {
                findings.add(new ValidationFinding(
                        "version.invalid-api",
                        "error",
                        rule.message(),
                        secretMasker.mask(rule.invalidPattern())
                ));
            }
        }

        if (source.contains("Thread.sleep(")) {
            findings.add(new ValidationFinding(
                    "anti-pattern.thread-sleep",
                    "warning",
                    "Prefer Gatling pause or pace; do not use Thread.sleep.",
                    "Thread.sleep("
            ));
        }
        if (source.contains("println(") || source.contains("System.out.println(")) {
            findings.add(new ValidationFinding(
                    "anti-pattern.println",
                    "warning",
                    "Do not print from virtual-user flow under load; use smoke/debug only.",
                    "println("
            ));
        }
        if (INJECTION_IN_SCENARIO.matcher(source).find()) {
            findings.add(new ValidationFinding(
                    "anti-pattern.injection-in-scenario",
                    "warning",
                    "Injection belongs in Simulation setup, not scenario definitions.",
                    ".inject("
            ));
        }
        if (REQUEST_IN_SETUP.matcher(source).find()) {
            findings.add(new ValidationFinding(
                    "anti-pattern.request-in-simulation",
                    "warning",
                    "Keep HTTP requests in case or scenario definitions and pass a prepared scenario to Simulation setup.",
                    "setUp(scenario(...).exec(http(...)))"
            ));
        }
        if (CASE_CLASS.matcher(source).find()
                && SCENARIO_CLASS.matcher(source).find()
                && SIMULATION_CLASS.matcher(source).find()) {
            findings.add(new ValidationFinding(
                    "anti-pattern.mixed-layout-responsibilities",
                    "warning",
                    "Keep cases, scenarios, and simulations in separate source files with one responsibility each.",
                    "Case + Scenario + Simulation classes in one source"
            ));
        }
        if ((source.contains("status.is(200)") || source.contains("status().is(200)"))
                && !containsBusinessCheck(source)) {
            findings.add(new ValidationFinding(
                    "anti-pattern.status-only-check",
                    "warning",
                    "Add a business-level response check; HTTP 200 alone does not prove the operation succeeded.",
                    "status.is(200)"
            ));
        }
        if (containsSecret(source)) {
            findings.add(new ValidationFinding(
                    "security.hardcoded-secret",
                    "error",
                    "Hardcoded credentials or secrets must be moved to configuration or system properties.",
                    secretMasker.mask(source)
            ));
        }

        return new ValidationResult(findings);
    }

    private static boolean containsSecret(String code) {
        return code.matches("(?s).*(?i)(password|secret|apiKey|api_key|credential)\\s*=\\s*\"[^\"]+\".*");
    }

    private static boolean containsBusinessCheck(String code) {
        return List.of("jsonPath(", "jmesPath(", "xpath(", "regex(", "bodyString", "substring(", "css(")
                .stream()
                .anyMatch(code::contains);
    }
}
