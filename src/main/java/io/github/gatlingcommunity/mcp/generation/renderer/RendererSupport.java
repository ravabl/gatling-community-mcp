package io.github.gatlingcommunity.mcp.generation.renderer;

import io.github.gatlingcommunity.mcp.authoring.CheckPlan;
import io.github.gatlingcommunity.mcp.authoring.ConditionalPlan;
import io.github.gatlingcommunity.mcp.authoring.HttpFeederPlan;
import io.github.gatlingcommunity.mcp.authoring.HttpRequestPlan;
import io.github.gatlingcommunity.mcp.authoring.HttpScenarioStepPlan;
import io.github.gatlingcommunity.mcp.authoring.HttpSimulationPlan;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

abstract class RendererSupport {
    protected static String escape(String value) {
        return (value == null ? "" : value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    protected static String lowerMethod(HttpRequestPlan request) {
        return request.method().toLowerCase(Locale.ROOT);
    }

    protected static List<HttpScenarioStepPlan> steps(HttpSimulationPlan plan) {
        return plan.steps().isEmpty()
                ? plan.requests().stream().map(HttpScenarioStepPlan::request).toList()
                : plan.steps();
    }

    protected static String feederVariable(HttpFeederPlan feeder) {
        return sanitizeIdentifier(feeder.name());
    }

    protected static String feederFactory(HttpFeederPlan feeder) {
        return switch (feeder.type().toLowerCase(Locale.ROOT)) {
            case "json" -> "jsonFile";
            case "array" -> "arrayFeeder";
            default -> "csv";
        };
    }

    protected static String sanitizeIdentifier(String value) {
        var candidate = (value == null || value.isBlank() ? "feeder" : value)
                .replaceAll("[^A-Za-z0-9_]", "_");
        if (!candidate.matches("[A-Za-z_].*")) {
            candidate = "_" + candidate;
        }
        return candidate;
    }

    protected static String javaExpected(CheckPlan check) {
        return check.expected().isBlank() ? "200" : check.expected();
    }

    protected static String javaAssertions(HttpSimulationPlan plan) {
        return plan.assertions().stream()
                .map(assertion -> "global().failedRequests().percent().%s(%s)"
                        .formatted(assertion.operator(), assertion.value()))
                .collect(Collectors.joining(", "));
    }

    protected static String javaCondition(ConditionalPlan conditional) {
        var variable = sessionVariable(conditional.left());
        if ("exists".equalsIgnoreCase(conditional.operator()) && !variable.isBlank()) {
            return "session -> session.contains(\"%s\")".formatted(escape(variable));
        }
        if (!variable.isBlank() && !conditional.right().isBlank()) {
            return "session -> \"%s\".equals(session.getString(\"%s\"))"
                    .formatted(escape(conditional.right()), escape(variable));
        }
        return "session -> false";
    }

    protected static String kotlinCondition(ConditionalPlan conditional) {
        var variable = sessionVariable(conditional.left());
        if ("exists".equalsIgnoreCase(conditional.operator()) && !variable.isBlank()) {
            return "{ session -> session.contains(\"%s\") }".formatted(escape(variable));
        }
        if (!variable.isBlank() && !conditional.right().isBlank()) {
            return "{ session -> \"%s\" == session.getString(\"%s\") }"
                    .formatted(escape(conditional.right()), escape(variable));
        }
        return "{ _ -> false }";
    }

    protected static String scalaCondition(ConditionalPlan conditional) {
        var variable = sessionVariable(conditional.left());
        if ("exists".equalsIgnoreCase(conditional.operator()) && !variable.isBlank()) {
            return "session => session.contains(\"%s\")".formatted(escape(variable));
        }
        if (!variable.isBlank() && !conditional.right().isBlank()) {
            return "session => session(\"%s\").asOption[String].contains(\"%s\")"
                    .formatted(escape(variable), escape(conditional.right()));
        }
        return "session => false";
    }

    protected static String javascriptCondition(ConditionalPlan conditional) {
        var variable = sessionVariable(conditional.left());
        if ("exists".equalsIgnoreCase(conditional.operator()) && !variable.isBlank()) {
            return "(session) => session.contains(\"%s\")".formatted(escape(variable));
        }
        if (!variable.isBlank() && !conditional.right().isBlank()) {
            return "(session) => session.get(\"%s\") === \"%s\""
                    .formatted(escape(variable), escape(conditional.right()));
        }
        return "() => false";
    }

    private static String sessionVariable(String expression) {
        if (expression == null || expression.isBlank()) {
            return "";
        }
        var value = expression.strip();
        if (value.startsWith("#{") && value.endsWith("}") && value.length() > 3) {
            return value.substring(2, value.length() - 1);
        }
        return value.matches("[A-Za-z_][A-Za-z0-9_]*") ? value : "";
    }
}
