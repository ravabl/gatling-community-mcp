package io.github.gatlingcommunity.mcp.authoring;

import io.github.gatlingcommunity.mcp.core.model.GatlingDslMethod;
import io.github.gatlingcommunity.mcp.core.model.Protocol;
import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import io.github.gatlingcommunity.mcp.data.SourceDataRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HttpSimulationPlanValidator {
    private final SourceDataRepository sourceData;
    private final HttpPlanTraversal traversal = new HttpPlanTraversal();

    public HttpSimulationPlanValidator(SourceDataRepository sourceData) {
        this.sourceData = sourceData;
    }

    public PlanValidationResult validate(TargetContext target, HttpSimulationPlan plan) {
        var findings = new ArrayList<PlanFinding>();
        var matchedLine = sourceData.supportedVersionLineFor(target.gatlingVersion()).orElse("unsupported");
        if ("unsupported".equals(matchedLine)) {
            findings.add(new PlanFinding(
                    "error",
                    "gatling.version.unsupported.v1",
                    "$.gatlingVersion",
                    "Gatling version is outside the supported v1 authoring matrix"
            ));
        }
        if (traversal.requestsInExecutionOrder(plan).isEmpty()) {
            findings.add(new PlanFinding(
                    "error",
                    "plan.requests.empty",
                    "$.plan.steps",
                    "Simulation plan must contain at least one HTTP request"
            ));
        }

        var availableMethods = sourceData.dslMethods(target, Protocol.HTTP).stream()
                .map(GatlingDslMethod::name)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        var requiredMethods = requiredMethodNames(target, plan);
        requiredMethods.forEach(method -> {
            if (!availableMethods.contains(method)) {
                findings.add(new PlanFinding(
                        "error",
                        "plan.method.unsupported",
                        "$.plan",
                        "Required DSL method is not available for this target: " + method
                ));
            }
        });

        validateCorrelation(plan, findings, traversal);
        var methodRequirements = methodRequirements(target, requiredMethods);
        return new PlanValidationResult(findings.stream().noneMatch(f -> "error".equals(f.severity())),
                matchedLine, findings, methodRequirements);
    }

    public List<String> requiredMethodNames(TargetContext target, HttpSimulationPlan plan) {
        var methods = new LinkedHashSet<String>();
        switch (target.language()) {
            case JAVASCRIPT, TYPESCRIPT -> methods.add("simulation");
            case JAVA, KOTLIN, SCALA -> methods.add("Simulation");
        }
        methods.add("scenario");
        methods.add("exec");
        methods.add("http");
        methods.add("baseUrl");
        methods.add("acceptHeader");
        methods.add("contentTypeHeader");
        methods.add("rampUsersPerSec");
        methods.add("constantUsersPerSec");
        methods.add("global.failedRequests.percent.lt");
        for (var feeder : plan.feeders()) {
            methods.add("feed");
            switch (feeder.type().toLowerCase()) {
                case "json" -> methods.add("jsonFile");
                case "array" -> methods.add("arrayFeeder");
                default -> methods.add("csv");
            }
            var strategy = feeder.strategy().toLowerCase();
            if (Set.of("queue", "shuffle", "random", "circular").contains(strategy)) {
                methods.add(strategy);
            }
        }
        for (var step : traversal.stepsInExecutionOrder(plan)) {
            switch (step.type()) {
                case "feed" -> methods.add("feed");
                case "pause" -> methods.add("pause");
                case "repeat" -> methods.add("repeat");
                case "during" -> methods.add("during");
                case "group" -> methods.add("group");
                case "ifEquals", "doIf" -> methods.add("doIf");
                default -> {
                    // Request steps are represented by RequestRef entries below.
                }
            }
        }
        if (plan.protocolOptions().http2()) {
            methods.add("enableHttp2");
        }
        if (!plan.protocolOptions().followRedirects()) {
            methods.add("disableFollowRedirect");
        }
        if (!plan.protocolOptions().proxyHost().isBlank()) {
            methods.add("proxy");
        }
        for (var requestRef : traversal.requestsInExecutionOrder(plan)) {
            var request = requestRef.request();
            methods.add(request.method().toLowerCase());
            if (!request.headers().isEmpty()) {
                methods.add("header");
            }
            if (!request.queryParams().isEmpty()) {
                methods.add("queryParam");
            }
            if (!request.formParams().isEmpty()) {
                methods.add("formParam");
            }
            if (!request.resources().isEmpty()) {
                methods.add("resources");
            }
            if (request.auth() != null && "basic".equalsIgnoreCase(request.auth().type())) {
                methods.add("basicAuth");
            }
            if (request.options() != null && !request.options().followRedirects()) {
                methods.add("disableFollowRedirect");
            }
            if (!request.body().isBlank()) {
                methods.add("body.StringBody");
                methods.add("asJson");
            }
            for (var check : request.checks()) {
                switch (check.type()) {
                    case "status" -> methods.add("status.is");
                    case "jsonPath" -> {
                        if (!check.saveAs().isBlank() || "saveAs".equals(check.operator())) {
                            methods.add("jsonPath.saveAs");
                        } else {
                            methods.add("jsonPath.exists");
                        }
                    }
                    default -> methods.add(check.type() + "." + check.operator());
                }
            }
        }
        return List.copyOf(methods);
    }

    private static void validateCorrelation(HttpSimulationPlan plan,
                                            List<PlanFinding> findings,
                                            HttpPlanTraversal traversal) {
        var saved = new LinkedHashSet<String>();
        var builtIns = new LinkedHashSet<>(Set.of("username", "password"));
        plan.feeders().stream()
                .flatMap(feeder -> feeder.columns().stream())
                .filter(column -> !column.isBlank())
                .forEach(builtIns::add);

        for (var event : traversal.sessionEventsInExecutionOrder(plan)) {
            if (event.type() == HttpPlanTraversal.SessionEventType.SAVE) {
                saved.add(event.name());
                continue;
            }
            if (!saved.contains(event.name()) && !builtIns.contains(event.name())) {
                findings.add(new PlanFinding(
                        "error",
                        "plan.correlation.missing",
                        event.path(),
                        "Session variable is referenced before it is saved: " + event.name()
                ));
            }
        }
    }

    private List<Map<String, Object>> methodRequirements(TargetContext target, List<String> requiredMethods) {
        var catalog = sourceData.dslMethods(target, Protocol.HTTP);
        return requiredMethods.stream()
                .map(methodName -> catalog.stream()
                        .filter(method -> method.name().equals(methodName))
                        .findFirst()
                        .map(HttpSimulationPlanValidator::methodMap)
                        .orElseGet(() -> missingMethodMap(methodName)))
                .toList();
    }

    private static Map<String, Object> methodMap(GatlingDslMethod method) {
        var values = new LinkedHashMap<String, Object>();
        values.put("name", method.name());
        values.put("category", method.category().name());
        values.put("since", method.since());
        values.put("until", method.until());
        values.put("callTemplate", method.callTemplate());
        values.put("sourceUrl", method.sourceUrl());
        values.put("confidenceLevel", method.confidenceLevel().name());
        return values;
    }

    private static Map<String, Object> missingMethodMap(String methodName) {
        var values = new LinkedHashMap<String, Object>();
        values.put("name", methodName);
        values.put("missing", true);
        return values;
    }

}
