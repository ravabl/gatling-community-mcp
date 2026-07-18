package io.github.gatlingcommunity.mcp.authoring;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class HttpPlanTraversal {
    private static final Pattern SESSION_REFERENCE = Pattern.compile("#\\{([^}]+)}");

    public List<RequestRef> requestsInExecutionOrder(HttpSimulationPlan plan) {
        var requests = new ArrayList<RequestRef>();
        traverseSteps(plan == null ? List.of() : plan.steps(), "$.plan.steps", requests,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        if (requests.isEmpty() && plan != null && !plan.requests().isEmpty()) {
            collectLegacyRequests(plan.requests(), requests, new ArrayList<>(), new ArrayList<>());
        }
        return List.copyOf(requests);
    }

    public List<CheckRef> checksInExecutionOrder(HttpSimulationPlan plan) {
        var checks = new ArrayList<CheckRef>();
        traverseSteps(plan == null ? List.of() : plan.steps(), "$.plan.steps", new ArrayList<>(),
                checks, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        if (checks.isEmpty() && plan != null && !plan.requests().isEmpty()) {
            collectLegacyRequests(plan.requests(), new ArrayList<>(), checks, new ArrayList<>());
        }
        return List.copyOf(checks);
    }

    public Set<String> savedVariables(HttpSimulationPlan plan) {
        var variables = new LinkedHashSet<String>();
        checksInExecutionOrder(plan).stream()
                .map(CheckRef::check)
                .map(CheckPlan::saveAs)
                .filter(value -> !value.isBlank())
                .forEach(variables::add);
        return Set.copyOf(variables);
    }

    public Set<String> sessionPlaceholders(HttpSimulationPlan plan) {
        var placeholders = new LinkedHashSet<String>();
        sessionPlaceholderRefs(plan).stream()
                .map(PlaceholderRef::name)
                .forEach(placeholders::add);
        return Set.copyOf(placeholders);
    }

    public List<PlaceholderRef> sessionPlaceholderRefs(HttpSimulationPlan plan) {
        var placeholders = new ArrayList<PlaceholderRef>();
        traverseSteps(plan == null ? List.of() : plan.steps(), "$.plan.steps", new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), placeholders);
        if (placeholders.isEmpty() && plan != null && !plan.requests().isEmpty()) {
            collectLegacyRequests(plan.requests(), new ArrayList<>(), new ArrayList<>(), placeholders);
        }
        return List.copyOf(placeholders);
    }

    public List<SessionEvent> sessionEventsInExecutionOrder(HttpSimulationPlan plan) {
        var events = new ArrayList<SessionEvent>();
        collectSessionEvents(plan == null ? List.of() : plan.steps(), "$.plan.steps", events);
        if (events.isEmpty() && plan != null && !plan.requests().isEmpty()) {
            for (var i = 0; i < plan.requests().size(); i++) {
                collectRequestSessionEvents(plan.requests().get(i), "$.plan.requests[" + i + "]", events);
            }
        }
        return List.copyOf(events);
    }

    public Set<String> feedStepNames(HttpSimulationPlan plan) {
        var names = new LinkedHashSet<String>();
        feedStepsInExecutionOrder(plan).stream()
                .map(FeedRef::feederName)
                .filter(value -> !value.isBlank())
                .forEach(names::add);
        return Set.copyOf(names);
    }

    public List<FeedRef> feedStepsInExecutionOrder(HttpSimulationPlan plan) {
        var feeds = new ArrayList<FeedRef>();
        traverseSteps(plan == null ? List.of() : plan.steps(), "$.plan.steps", new ArrayList<>(),
                new ArrayList<>(), feeds, new ArrayList<>(), new ArrayList<>());
        return List.copyOf(feeds);
    }

    public Set<String> declaredFeederNames(HttpSimulationPlan plan) {
        var names = new LinkedHashSet<String>();
        if (plan != null) {
            plan.feeders().stream()
                    .map(HttpFeederPlan::name)
                    .filter(value -> !value.isBlank())
                    .forEach(names::add);
        }
        return Set.copyOf(names);
    }

    public List<StepRef> stepsInExecutionOrder(HttpSimulationPlan plan) {
        var steps = new ArrayList<StepRef>();
        traverseSteps(plan == null ? List.of() : plan.steps(), "$.plan.steps", new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), steps, new ArrayList<>());
        return List.copyOf(steps);
    }

    public int maxLoopMultiplier(HttpSimulationPlan plan) {
        return maxLoopMultiplier(plan == null ? List.of() : plan.steps());
    }

    private static void traverseSteps(List<HttpScenarioStepPlan> steps,
                                      String path,
                                      List<RequestRef> requests,
                                      List<CheckRef> checks,
                                      List<FeedRef> feeds,
                                      List<StepRef> stepRefs,
                                      List<PlaceholderRef> placeholders) {
        for (var i = 0; i < steps.size(); i++) {
            var step = steps.get(i);
            var stepPath = path + "[" + i + "]";
            stepRefs.add(new StepRef(stepPath, step.type()));
            switch (step.type()) {
                case "feed" -> {
                    feeds.add(new FeedRef(stepPath + ".feederName", step.feederName()));
                    collectPlaceholders(step.feederName(), stepPath + ".feederName", placeholders);
                }
                case "pause" -> {
                    // No request/check data to collect.
                }
                case "repeat", "during", "forever" -> {
                    if (step.loop() != null) {
                        traverseSteps(step.loop().steps(), stepPath + ".loop.steps", requests, checks,
                                feeds, stepRefs, placeholders);
                    }
                }
                case "group" -> {
                    if (step.group() != null) {
                        traverseSteps(step.group().steps(), stepPath + ".group.steps", requests, checks,
                                feeds, stepRefs, placeholders);
                    }
                }
                case "ifEquals", "doIf" -> {
                    if (step.conditional() != null) {
                        collectConditionalPlaceholders(step.conditional(), stepPath + ".conditional", placeholders);
                        traverseSteps(step.conditional().steps(), stepPath + ".conditional.steps", requests, checks,
                                feeds, stepRefs, placeholders);
                    }
                }
                default -> {
                    if (step.request() != null) {
                        collectRequest(step.request(), stepPath + ".request", requests, checks, placeholders);
                    }
                }
            }
        }
    }

    private static void collectRequest(HttpRequestPlan request,
                                       String path,
                                       List<RequestRef> requests,
                                       List<CheckRef> checks,
                                       List<PlaceholderRef> placeholders) {
        requests.add(new RequestRef(path, request));
        collectRequestPlaceholders(request, path, placeholders);
        for (var i = 0; i < request.checks().size(); i++) {
            checks.add(new CheckRef(path + ".checks[" + i + "]", request, request.checks().get(i)));
        }
    }

    private static void collectLegacyRequests(List<HttpRequestPlan> legacyRequests,
                                              List<RequestRef> requests,
                                              List<CheckRef> checks,
                                              List<PlaceholderRef> placeholders) {
        for (var i = 0; i < legacyRequests.size(); i++) {
            collectRequest(legacyRequests.get(i), "$.plan.requests[" + i + "]", requests, checks, placeholders);
        }
    }

    private static void collectRequestPlaceholders(HttpRequestPlan request,
                                                   String path,
                                                   List<PlaceholderRef> placeholders) {
        collectPlaceholders(request.path(), path + ".path", placeholders);
        collectPlaceholders(request.body(), path + ".body", placeholders);
        request.headers().forEach((name, value) ->
                collectPlaceholders(value, path + ".headers." + name, placeholders));
        request.queryParams().forEach((name, value) ->
                collectPlaceholders(value, path + ".queryParams." + name, placeholders));
        request.formParams().forEach((name, value) ->
                collectPlaceholders(value, path + ".formParams." + name, placeholders));
        for (var i = 0; i < request.multipartParts().size(); i++) {
            var part = request.multipartParts().get(i);
            var partPath = path + ".multipartParts[" + i + "]";
            collectPlaceholders(part.name(), partPath + ".name", placeholders);
            collectPlaceholders(part.value(), partPath + ".value", placeholders);
            collectPlaceholders(part.fileName(), partPath + ".fileName", placeholders);
            collectPlaceholders(part.filePath(), partPath + ".filePath", placeholders);
        }
        for (var i = 0; i < request.resources().size(); i++) {
            var resource = request.resources().get(i);
            var resourcePath = path + ".resources[" + i + "]";
            collectPlaceholders(resource.path(), resourcePath + ".path", placeholders);
            resource.headers().forEach((name, value) ->
                    collectPlaceholders(value, resourcePath + ".headers." + name, placeholders));
        }
        if (request.auth() != null) {
            collectPlaceholders(request.auth().username(), path + ".auth.username", placeholders);
            collectPlaceholders(request.auth().password(), path + ".auth.password", placeholders);
            collectPlaceholders(request.auth().token(), path + ".auth.token", placeholders);
        }
        for (var i = 0; i < request.cookies().size(); i++) {
            var cookie = request.cookies().get(i);
            collectPlaceholders(cookie.name(), path + ".cookies[" + i + "].name", placeholders);
            collectPlaceholders(cookie.value(), path + ".cookies[" + i + "].value", placeholders);
        }
    }

    private static void collectSessionEvents(List<HttpScenarioStepPlan> steps,
                                             String path,
                                             List<SessionEvent> events) {
        for (var i = 0; i < steps.size(); i++) {
            var step = steps.get(i);
            var stepPath = path + "[" + i + "]";
            switch (step.type()) {
                case "feed" -> collectSessionEvents(step.feederName(), stepPath + ".feederName", events);
                case "repeat", "during", "forever" -> {
                    if (step.loop() != null) {
                        collectSessionEvents(step.loop().steps(), stepPath + ".loop.steps", events);
                    }
                }
                case "group" -> {
                    if (step.group() != null) {
                        collectSessionEvents(step.group().steps(), stepPath + ".group.steps", events);
                    }
                }
                case "ifEquals", "doIf" -> {
                    if (step.conditional() != null) {
                        collectSessionEvents(step.conditional().expression(), stepPath + ".conditional.expression", events);
                        collectSessionEvents(step.conditional().left(), stepPath + ".conditional.left", events);
                        collectSessionEvents(step.conditional().right(), stepPath + ".conditional.right", events);
                        collectSessionEvents(step.conditional().steps(), stepPath + ".conditional.steps", events);
                    }
                }
                default -> {
                    if (step.request() != null) {
                        collectRequestSessionEvents(step.request(), stepPath + ".request", events);
                    }
                }
            }
        }
    }

    private static void collectRequestSessionEvents(HttpRequestPlan request,
                                                    String path,
                                                    List<SessionEvent> events) {
        collectSessionEvents(request.path(), path + ".path", events);
        collectSessionEvents(request.body(), path + ".body", events);
        request.headers().forEach((name, value) ->
                collectSessionEvents(value, path + ".headers." + name, events));
        request.queryParams().forEach((name, value) ->
                collectSessionEvents(value, path + ".queryParams." + name, events));
        request.formParams().forEach((name, value) ->
                collectSessionEvents(value, path + ".formParams." + name, events));
        for (var i = 0; i < request.multipartParts().size(); i++) {
            var part = request.multipartParts().get(i);
            var partPath = path + ".multipartParts[" + i + "]";
            collectSessionEvents(part.name(), partPath + ".name", events);
            collectSessionEvents(part.value(), partPath + ".value", events);
            collectSessionEvents(part.fileName(), partPath + ".fileName", events);
            collectSessionEvents(part.filePath(), partPath + ".filePath", events);
        }
        for (var i = 0; i < request.resources().size(); i++) {
            var resource = request.resources().get(i);
            var resourcePath = path + ".resources[" + i + "]";
            collectSessionEvents(resource.path(), resourcePath + ".path", events);
            resource.headers().forEach((name, value) ->
                    collectSessionEvents(value, resourcePath + ".headers." + name, events));
        }
        if (request.auth() != null) {
            collectSessionEvents(request.auth().username(), path + ".auth.username", events);
            collectSessionEvents(request.auth().password(), path + ".auth.password", events);
            collectSessionEvents(request.auth().token(), path + ".auth.token", events);
        }
        for (var i = 0; i < request.cookies().size(); i++) {
            var cookie = request.cookies().get(i);
            collectSessionEvents(cookie.name(), path + ".cookies[" + i + "].name", events);
            collectSessionEvents(cookie.value(), path + ".cookies[" + i + "].value", events);
        }
        for (var i = 0; i < request.checks().size(); i++) {
            var saveAs = request.checks().get(i).saveAs();
            if (!saveAs.isBlank()) {
                events.add(new SessionEvent(path + ".checks[" + i + "].saveAs", saveAs, SessionEventType.SAVE));
            }
        }
    }

    private static void collectSessionEvents(String value, String path, List<SessionEvent> events) {
        if (value == null || value.isBlank()) {
            return;
        }
        var matcher = SESSION_REFERENCE.matcher(value);
        while (matcher.find()) {
            events.add(new SessionEvent(path, matcher.group(1), SessionEventType.PLACEHOLDER));
        }
    }

    private static void collectConditionalPlaceholders(ConditionalPlan conditional,
                                                       String path,
                                                       List<PlaceholderRef> placeholders) {
        collectPlaceholders(conditional.expression(), path + ".expression", placeholders);
        collectPlaceholders(conditional.left(), path + ".left", placeholders);
        collectPlaceholders(conditional.right(), path + ".right", placeholders);
    }

    private static void collectPlaceholders(String value, String path, List<PlaceholderRef> placeholders) {
        if (value == null || value.isBlank()) {
            return;
        }
        var matcher = SESSION_REFERENCE.matcher(value);
        while (matcher.find()) {
            placeholders.add(new PlaceholderRef(path, matcher.group(1)));
        }
    }

    private static int maxLoopMultiplier(List<HttpScenarioStepPlan> steps) {
        var max = 1;
        for (var step : steps) {
            if (step.loop() != null) {
                var own = step.loop().count() > 0 ? step.loop().count() : 1;
                max = Math.max(max, own);
                max = Math.max(max, own * maxLoopMultiplier(step.loop().steps()));
            }
            if (step.group() != null) {
                max = Math.max(max, maxLoopMultiplier(step.group().steps()));
            }
            if (step.conditional() != null) {
                max = Math.max(max, maxLoopMultiplier(step.conditional().steps()));
            }
        }
        return max;
    }

    public record RequestRef(String path, HttpRequestPlan request) {
    }

    public record CheckRef(String path, HttpRequestPlan request, CheckPlan check) {
    }

    public record FeedRef(String path, String feederName) {
    }

    public record PlaceholderRef(String path, String name) {
    }

    public record SessionEvent(String path, String name, SessionEventType type) {
    }

    public enum SessionEventType {
        PLACEHOLDER,
        SAVE
    }

    public record StepRef(String path, String type) {
    }
}
