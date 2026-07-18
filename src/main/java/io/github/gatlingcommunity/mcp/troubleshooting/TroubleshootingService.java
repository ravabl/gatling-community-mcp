package io.github.gatlingcommunity.mcp.troubleshooting;

import io.github.gatlingcommunity.mcp.authoring.AssertionPlan;
import io.github.gatlingcommunity.mcp.authoring.CheckPlan;
import io.github.gatlingcommunity.mcp.authoring.HttpFeederPlan;
import io.github.gatlingcommunity.mcp.authoring.HttpPlanTraversal;
import io.github.gatlingcommunity.mcp.authoring.HttpRequestPlan;
import io.github.gatlingcommunity.mcp.authoring.HttpSimulationPlan;
import io.github.gatlingcommunity.mcp.validation.SecretMasker;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class TroubleshootingService {
    private static final SecretMasker SECRET_MASKER = new SecretMasker();
    private static final Pattern SESSION_REF = Pattern.compile("#\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private static final Pattern SAVE_AS = Pattern.compile("\\.saveAs\\(\"([^\"]+)\"\\)");
    private final HttpPlanTraversal traversal = new HttpPlanTraversal();
    private static final List<FailureRule> FAILURE_RULES = List.of(
            new FailureRule("log.connection.refused", "error",
                    Pattern.compile("connection refused|connectexception", Pattern.CASE_INSENSITIVE),
                    "Target TCP endpoint refused connections.",
                    "Wrong baseUrl/port, service not ready, container networking, or routing mismatch.",
                    "Run curl/nc from the same runtime namespace and verify host, port, scheme, and readiness."),
            new FailureRule("log.dns.unknown-host", "error",
                    Pattern.compile("unknownhostexception|name or service not known|nodename nor servname", Pattern.CASE_INSENSITIVE),
                    "Target host could not be resolved.",
                    "DNS/VPN/container resolver problem or typo in baseUrl.",
                    "Resolve the hostname from the same container or process with getent/nslookup/dig."),
            new FailureRule("log.tls.handshake", "error",
                    Pattern.compile("sslhandshakeexception|pkix path|certificate_unknown|handshake_failure", Pattern.CASE_INSENSITIVE),
                    "TLS handshake failed.",
                    "Missing CA trust, wrong hostname, expired certificate, mTLS, or proxy interception.",
                    "Check certificate chain and JVM trust store used by the Gatling process."),
            new FailureRule("log.timeout", "error",
                    Pattern.compile("readtimeoutexception|connecttimeoutexception|timeout|timed out", Pattern.CASE_INSENSITIVE),
                    "Request timed out.",
                    "Server saturation, too aggressive load profile, slow dependency, or timeout too low.",
                    "Compare response-time percentiles, server saturation metrics, and Gatling timeout settings."),
            new FailureRule("log.auth", "error",
                    Pattern.compile("\\b(401|403)\\b|unauthorized|forbidden", Pattern.CASE_INSENSITIVE),
                    "Authentication or authorization failures were observed.",
                    "Missing login step, expired token, wrong credentials, or correlation not applied.",
                    "Verify login response, saved token variable, Authorization header, and per-user session data."),
            new FailureRule("log.server.error", "error",
                    Pattern.compile("\\b(500|502|503|504)\\b|no_healthy_upstream|service unavailable", Pattern.CASE_INSENSITIVE),
                    "Server-side errors were observed.",
                    "Application failure, upstream saturation, gateway timeout, or load above capacity.",
                    "Correlate Gatling timestamps with application logs, upstream health, and saturation metrics."),
            new FailureRule("log.feeder.empty", "warning",
                    Pattern.compile("feeder is now empty|no more records|feed.*exhaust", Pattern.CASE_INSENSITIVE),
                    "A feeder appears to be exhausted.",
                    "Finite queue/shuffle feeder has fewer records than required user iterations.",
                    "Count feeder rows and compare them with users multiplied by loop/request iterations."),
            new FailureRule("log.jvm.oom", "error",
                    Pattern.compile("outofmemoryerror|java heap space|gc overhead limit", Pattern.CASE_INSENSITIVE),
                    "The load generator JVM ran out of memory.",
                    "Heap too small, large response retention, huge feeders, or report/log volume.",
                    "Inspect JVM heap settings, response body checks, feeder size, and generated report volume."),
            new FailureRule("log.check.failed", "warning",
                    Pattern.compile("check failed|status\\.find|jsonpath|but actually found", Pattern.CASE_INSENSITIVE),
                    "Gatling checks failed.",
                    "Wrong assertion, missing correlation, unexpected response shape, or backend error.",
                    "Inspect the failed response sample and validate status/body checks against real responses.")
    );

    public TroubleshootingResult explainErrors(String text) {
        var content = text == null ? "" : text;
        var findings = new ArrayList<TroubleshootingFinding>();
        var hypotheses = new ArrayList<RootCauseHypothesis>();
        var lines = content.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            var path = "$.lines[%d]".formatted(i + 1);
            for (var rule : FAILURE_RULES) {
                if (rule.pattern().matcher(line).find()) {
                    findings.add(new TroubleshootingFinding(
                            rule.severity(),
                            rule.code(),
                            path,
                            rule.message(),
                            clip(line),
                            rule.verification()
                    ));
                    hypotheses.add(new RootCauseHypothesis(
                            rule.code().replace("log.", "root_cause."),
                            rule.cause(),
                            clip(line),
                            confidenceFor(rule.severity()),
                            rule.verification()
                    ));
                }
            }
        }
        var matchedKnownPattern = !findings.isEmpty();
        if (!matchedKnownPattern) {
            findings.add(new TroubleshootingFinding(
                    "info",
                    "errors.no_known_patterns",
                    "$",
                    "No known Gatling runtime error patterns were detected.",
                    "",
                    "Provide Gatling runtime logs, simulation.log, or the failing response/check message for deeper analysis."
            ));
        }
        return new TroubleshootingResult(
                dedupeFindings(findings),
                dedupeHypotheses(hypotheses),
                List.of(
                        "Reproduce once with a small user count and preserve the full Gatling log.",
                        "Compare failure timestamps with application logs and infrastructure metrics.",
                        "Validate network, TLS, auth, and correlation from the same runtime environment."
                ),
                List.of(
                        "Fix the highest-confidence root cause first.",
                        "Re-run the scenario at smoke load before restoring the target load profile."
                ),
                matchedKnownPattern ? "medium" : "low",
                Map.of(),
                Map.of(),
                List.of("Pattern-based RCA uses supplied error text only; it does not query target systems."),
                findings.stream().filter(finding -> "error".equals(finding.severity())).map(TroubleshootingFinding::message).toList()
        );
    }

    public TroubleshootingResult suggestLoadModel(double targetRps,
                                                  int expectedUsers,
                                                  int durationSeconds,
                                                  int rampSeconds,
                                                  long p95Ms,
                                                  String workloadType) {
        var safeDuration = Math.max(durationSeconds, 60);
        var safeRamp = Math.max(rampSeconds, Math.min(60, safeDuration / 3));
        var safeP95 = p95Ms > 0 ? p95Ms : 500;
        var safeRps = Math.max(targetRps, 1.0);
        var concurrentUsersByLittleLaw = Math.max(1, (int) Math.ceil(safeRps * (safeP95 / 1000.0)));
        var modelUsers = expectedUsers > 0
                ? Math.max(expectedUsers, concurrentUsersByLittleLaw)
                : concurrentUsersByLittleLaw;
        var constantDuration = Math.max(1, safeDuration - safeRamp);
        var normalizedWorkload = workloadType == null || workloadType.isBlank()
                ? "open"
                : workloadType.toLowerCase(Locale.ROOT);
        var openModel = !normalizedWorkload.contains("closed");

        var injection = new LinkedHashMap<String, Object>();
        injection.put("type", openModel ? "rampAndConstant" : "rampConcurrentUsers");
        if (openModel) {
            injection.put("rampFromUsersPerSec", 1);
            injection.put("rampToUsersPerSec", Math.max(1, (int) Math.ceil(safeRps)));
            injection.put("rampDurationSeconds", safeRamp);
            injection.put("constantUsersPerSec", Math.max(1, (int) Math.ceil(safeRps)));
            injection.put("constantDurationSeconds", constantDuration);
        } else {
            injection.put("rampToConcurrentUsers", modelUsers);
            injection.put("rampDurationSeconds", safeRamp);
            injection.put("holdConcurrentUsers", modelUsers);
            injection.put("constantDurationSeconds", constantDuration);
        }

        var findings = new ArrayList<TroubleshootingFinding>();
        if (p95Ms <= 0) {
            findings.add(new TroubleshootingFinding(
                    "warning",
                    "load_model.p95.defaulted",
                    "$.p95Ms",
                    "p95 latency was not supplied; concurrency estimate uses a conservative 500 ms default.",
                    "",
                    "Use a recent smoke-test p95 or production p95 to refine the load model."
            ));
        }
        if (safeRamp < 30 && safeRps >= 10) {
            findings.add(new TroubleshootingFinding(
                    "warning",
                    "load_model.ramp_too_short",
                    "$.rampSeconds",
                    "Ramp duration is short for the requested target rate.",
                    String.format(Locale.ROOT, "rampSeconds=%d targetRps=%.2f", safeRamp, safeRps),
                    "Use a longer ramp to separate startup artifacts from steady-state saturation."
            ));
        }

        return new TroubleshootingResult(
                findings,
                List.of(new RootCauseHypothesis(
                        "load_model.capacity_estimate",
                        "Initial model uses Little's Law: concurrent users ~= targetRps * p95Seconds.",
                        String.format(Locale.ROOT, "targetRps=%.2f p95Ms=%d modelUsers=%d",
                                safeRps, safeP95, modelUsers),
                        "medium",
                        "Run a smoke test, then compare achieved RPS, p95, error rate, and server saturation."
                )),
                List.of(
                        "Run the suggested profile at a reduced scale first.",
                        "Check achieved RPS versus target RPS.",
                        "Check p95/p99, error rate, CPU, memory, DB/queue saturation, and upstream timeouts."
                ),
                List.of(
                        "Start with the generated injection profile.",
                        "Increase target rate only after the previous step is stable."
                ),
                "medium",
                Map.of(
                        "workloadType", openModel ? "open" : "closed",
                        "estimatedConcurrentUsers", modelUsers,
                        "targetRps", safeRps,
                        "durationSeconds", safeDuration,
                        "rampSeconds", safeRamp,
                        "p95Ms", safeP95
                ),
                Map.copyOf(injection),
                List.of("No think-time distribution or production traffic shape was supplied."),
                findings.stream().map(TroubleshootingFinding::message).toList()
        );
    }

    public TroubleshootingResult checkFeederRisk(HttpSimulationPlan plan,
                                                 String sampleCsv,
                                                 int expectedUsers,
                                                 int durationSeconds) {
        var findings = new ArrayList<TroubleshootingFinding>();
        var hypotheses = new ArrayList<RootCauseHypothesis>();
        var feederNames = plan == null ? Set.<String>of() : traversal.declaredFeederNames(plan);
        var feedSteps = plan == null ? Set.<String>of() : traversal.feedStepNames(plan);
        for (var feedStep : feedSteps) {
            if (!feederNames.contains(feedStep)) {
                findings.add(new TroubleshootingFinding(
                        "error",
                        "feeder.undefined",
                        "$.plan.steps",
                        "Scenario feeds from an undefined feeder.",
                        feedStep,
                        "Add the feeder definition or change the feed step to an existing feeder name."
                ));
            }
        }
        if (plan != null) {
            for (var feeder : plan.feeders()) {
                if (List.of("queue", "shuffle").contains(feeder.strategy().toLowerCase(Locale.ROOT))) {
                    findings.add(new TroubleshootingFinding(
                            "warning",
                            "feeder.finite_strategy",
                            "$.plan.feeders[%s]".formatted(feeder.name()),
                            "Feeder strategy can exhaust finite data under load.",
                            "%s:%s".formatted(feeder.name(), feeder.strategy()),
                            "Use circular/random for smoke/demo flows or provide enough unique records for every iteration."
                    ));
                }
            }
        }
        var rows = countCsvRows(sampleCsv);
        var estimatedConsumes = estimateFeederConsumes(plan, expectedUsers, durationSeconds);
        if (rows > 0 && estimatedConsumes > rows && !hasCircularFeeder(plan)) {
            findings.add(new TroubleshootingFinding(
                    "error",
                    "feeder.rows_insufficient",
                    "$.sampleCsv",
                    "Sample feeder rows are fewer than estimated feed consumptions.",
                    "rows=%d estimatedConsumes=%d".formatted(rows, estimatedConsumes),
                    "Increase feeder rows, lower user/loop volume, or switch to circular/random when uniqueness is not required."
            ));
        }
        var placeholders = plan == null ? Set.<String>of() : traversal.sessionPlaceholders(plan);
        if (plan != null && !placeholders.isEmpty() && plan.feeders().isEmpty()) {
            findings.add(new TroubleshootingFinding(
                    "warning",
                    "feeder.placeholders_without_feeder",
                    "$.plan",
                    "Plan contains session placeholders but no feeder definitions.",
                    placeholders.toString(),
                    "Define a feeder or ensure these values are created by checks/saveAs before use."
            ));
        }
        hypotheses.add(new RootCauseHypothesis(
                "root_cause.feeder_exhaustion",
                "Finite feeder data may be exhausted before the scenario finishes.",
                "rows=%d estimatedConsumes=%d feedSteps=%s".formatted(rows, estimatedConsumes, feedSteps),
                findings.stream().anyMatch(f -> f.code().startsWith("feeder.")) ? "medium" : "low",
                "Run a small test with DEBUG feeder logs or count feed calls versus available rows."
        ));
        return new TroubleshootingResult(
                findings,
                hypotheses,
                List.of(
                        "Count real feeder records excluding the CSV header.",
                        "Estimate feed calls as active users multiplied by loop/request feed occurrences.",
                        "Check whether uniqueness is required; otherwise prefer circular/random."
                ),
                findings.isEmpty()
                        ? List.of("Keep feeder strategy explicit in the plan.")
                        : List.of("Fix undefined feeders first, then verify record volume against the final injection profile."),
                findings.isEmpty() ? "medium" : "high",
                Map.of(),
                Map.of(),
                List.of("Feeder consumption is estimated from plan shape; exact runtime pacing is not simulated."),
                findings.stream().map(TroubleshootingFinding::message).toList()
        );
    }

    public TroubleshootingResult checkCorrelationRisk(HttpSimulationPlan plan, String code) {
        var saved = new HashSet<String>();
        if (plan != null) {
            saved.addAll(traversal.savedVariables(plan));
        }
        SAVE_AS.matcher(code == null ? "" : code).results()
                .map(match -> match.group(1))
                .forEach(saved::add);

        var referenced = new HashSet<String>();
        if (plan != null) {
            traversal.sessionPlaceholders(plan).forEach(referenced::add);
        }
        SESSION_REF.matcher(code == null ? "" : code).results()
                .map(match -> match.group(1))
                .forEach(referenced::add);
        var missing = referenced.stream()
                .filter(name -> !saved.contains(name))
                .filter(name -> !commonFeederColumns(name))
                .sorted()
                .toList();

        var findings = new ArrayList<TroubleshootingFinding>();
        for (var name : missing) {
            findings.add(new TroubleshootingFinding(
                    "error",
                    "correlation.reference_without_save",
                    "$.plan",
                    "Session variable is referenced but no check/saveAs source was found.",
                    name,
                    "Add a check(...saveAs(\"%s\")) before first use or feed the value from an explicit feeder.".formatted(name)
            ));
        }
        if (plan != null && traversal.requestsInExecutionOrder(plan).stream()
                .map(HttpPlanTraversal.RequestRef::request)
                .anyMatch(this::looksAuthenticated) && saved.isEmpty()) {
            findings.add(new TroubleshootingFinding(
                    "warning",
                    "correlation.auth_without_token_capture",
                    "$.plan.requests",
                    "Authenticated requests exist but no token/session capture is defined.",
                    "",
                    "Capture token/session values from login responses and reuse them in headers/cookies."
            ));
        }
        return new TroubleshootingResult(
                findings,
                List.of(new RootCauseHypothesis(
                        "root_cause.missing_correlation",
                        "A later request may depend on a value not captured from a previous response.",
                        "saved=%s referenced=%s".formatted(saved, referenced),
                        findings.isEmpty() ? "low" : "high",
                        "Inspect failed request headers/body and confirm every #{var} exists in the Gatling session."
                )),
                List.of(
                        "List every #{var} usage in headers, body, path, query params, cookies, and auth fields.",
                        "Confirm each runtime value comes from a feeder or an earlier check/saveAs.",
                        "Verify ordering: capture must happen before first use."
                ),
                findings.isEmpty()
                        ? List.of("Keep saveAs variable names stable and documented in the plan.")
                        : List.of("Add missing saveAs checks before first use and re-run at smoke load."),
                findings.isEmpty() ? "medium" : "high",
                Map.of(),
                Map.of(),
                List.of("Common credential columns such as username/password are treated as feeder-provided values."),
                findings.stream().map(TroubleshootingFinding::message).toList()
        );
    }

    public TroubleshootingResult checkAssertionQuality(HttpSimulationPlan plan, String code) {
        var assertions = plan == null ? List.<AssertionPlan>of() : plan.assertions();
        var checks = plan == null
                ? List.<CheckPlan>of()
                : traversal.checksInExecutionOrder(plan).stream().map(HttpPlanTraversal.CheckRef::check).toList();
        var codeText = code == null ? "" : code;
        var findings = new ArrayList<TroubleshootingFinding>();
        if (assertions.isEmpty() && !codeText.contains("assertions(")) {
            findings.add(new TroubleshootingFinding(
                    "warning",
                    "assertion.missing_global_assertions",
                    "$.plan.assertions",
                    "No global Gatling assertions were found.",
                    "",
                    "Add failed-requests and latency/percentile assertions for CI quality gates."
            ));
        }
        var hasFailureAssertion = assertions.stream().anyMatch(assertion -> assertion.metric().contains("failedRequests"))
                || codeText.contains("failedRequests");
        if (!hasFailureAssertion) {
            findings.add(new TroubleshootingFinding(
                    "warning",
                    "assertion.missing_error_budget",
                    "$.plan.assertions",
                    "No failed-request/error-rate assertion was detected.",
                    assertions.toString(),
                    "Add global().failedRequests().percent().lt(...) or an equivalent DSL assertion."
            ));
        }
        var hasLatencyAssertion = assertions.stream().anyMatch(assertion -> assertion.metric().contains("responseTime")
                || assertion.metric().contains("percentile"))
                || codeText.toLowerCase(Locale.ROOT).contains("responsetime")
                || codeText.toLowerCase(Locale.ROOT).contains("percentile");
        if (!hasLatencyAssertion) {
            findings.add(new TroubleshootingFinding(
                    "info",
                    "assertion.latency_budget_missing",
                    "$.plan.assertions",
                    "No latency percentile assertion was detected.",
                    "",
                    "Add p95/p99 or max response-time assertions when the test has an explicit latency SLO."
            ));
        }
        var hasStatusChecks = checks.stream().anyMatch(check -> "status".equalsIgnoreCase(check.type()))
                || codeText.contains("status().is") || codeText.contains("status.is");
        if (!hasStatusChecks) {
            findings.add(new TroubleshootingFinding(
                    "warning",
                    "assertion.request_status_checks_missing",
                    "$.plan.requests",
                    "No request-level status checks were detected.",
                    "",
                    "Add status checks to the business-critical requests so failures are visible at request level."
            ));
        }
        var onlyStatusChecks = !checks.isEmpty()
                && checks.stream().allMatch(check -> "status".equalsIgnoreCase(check.type()));
        if (onlyStatusChecks && checks.size() >= 2) {
            findings.add(new TroubleshootingFinding(
                    "info",
                    "assertion.only_status_checks",
                    "$.plan.requests",
                    "Plan uses only status checks; response semantics are not validated.",
                    "",
                    "Add JSON/body checks for critical response fields, ids, tokens, or non-empty arrays."
            ));
        }
        return new TroubleshootingResult(
                findings,
                List.of(new RootCauseHypothesis(
                        "root_cause.weak_quality_gate",
                        "The simulation may pass while hiding functional errors or latency regressions.",
                        "assertions=%d checks=%d".formatted(assertions.size(), checks.size()),
                        findings.isEmpty() ? "low" : "medium",
                        "Run with an intentionally failing response/check to verify the build fails as expected."
                )),
                List.of(
                        "Confirm every business-critical request has status and semantic checks.",
                        "Confirm global assertions cover error rate and latency budget.",
                        "Run a negative smoke test or inspect a failed response sample."
                ),
                findings.isEmpty()
                        ? List.of("Keep assertions close to the service SLOs.")
                        : List.of("Add missing status/error-rate/latency assertions before demo or CI use."),
                findings.isEmpty() ? "medium" : "high",
                Map.of(),
                Map.of(),
                List.of("Assertion quality is inferred from plan/code structure, not from production SLO documents."),
                findings.stream().map(TroubleshootingFinding::message).toList()
        );
    }

    private static int countCsvRows(String sampleCsv) {
        if (sampleCsv == null || sampleCsv.isBlank()) {
            return 0;
        }
        var nonBlank = sampleCsv.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> !line.startsWith("#"))
                .toList();
        return Math.max(0, nonBlank.size() - 1);
    }

    private static int estimateFeederConsumes(HttpSimulationPlan plan, int expectedUsers, int durationSeconds) {
        if (plan == null) {
            return Math.max(expectedUsers, 0);
        }
        var traversal = new HttpPlanTraversal();
        var feedStepCount = Math.max(1, traversal.feedStepNames(plan).size());
        var loopMultiplier = Math.max(1, traversal.maxLoopMultiplier(plan));
        var users = expectedUsers > 0 ? expectedUsers : Math.max(1, plan.injectionProfile().rampToUsersPerSec());
        var durationMultiplier = durationSeconds > 0 && plan.injectionProfile().constantDurationSeconds() > 0
                ? Math.max(1, durationSeconds / Math.max(1, plan.injectionProfile().constantDurationSeconds()))
                : 1;
        return users * feedStepCount * loopMultiplier * durationMultiplier;
    }

    private static boolean hasCircularFeeder(HttpSimulationPlan plan) {
        return plan != null && plan.feeders().stream()
                .map(HttpFeederPlan::strategy)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(strategy -> strategy.equals("circular") || strategy.equals("random"));
    }


    private boolean looksAuthenticated(HttpRequestPlan request) {
        return request.auth() != null && !request.auth().type().isBlank()
                || request.headers().keySet().stream().anyMatch(name -> name.equalsIgnoreCase("Authorization"))
                || request.cookies().stream().anyMatch(cookie -> cookie.name().toLowerCase(Locale.ROOT).contains("session"));
    }

    private static boolean commonFeederColumns(String name) {
        return Set.of("username", "password", "user", "email", "id").contains(name.toLowerCase(Locale.ROOT));
    }

    private static List<TroubleshootingFinding> dedupeFindings(List<TroubleshootingFinding> findings) {
        var seen = new HashSet<String>();
        var values = new ArrayList<TroubleshootingFinding>();
        for (var finding : findings) {
            var key = finding.code() + "|" + finding.path();
            if (seen.add(key)) {
                values.add(finding);
            }
        }
        return values;
    }

    private static List<RootCauseHypothesis> dedupeHypotheses(List<RootCauseHypothesis> hypotheses) {
        var seen = new HashSet<String>();
        var values = new ArrayList<RootCauseHypothesis>();
        for (var hypothesis : hypotheses) {
            if (seen.add(hypothesis.code())) {
                values.add(hypothesis);
            }
        }
        return values;
    }

    private static String confidenceFor(String severity) {
        return "error".equals(severity) ? "high" : "medium";
    }

    private static String clip(String value) {
        var text = SECRET_MASKER.mask(Optional.ofNullable(value).orElse("").strip());
        return text.length() <= 240 ? text : text.substring(0, 240);
    }

    private record FailureRule(
            String code,
            String severity,
            Pattern pattern,
            String message,
            String cause,
            String verification
    ) {
    }
}
