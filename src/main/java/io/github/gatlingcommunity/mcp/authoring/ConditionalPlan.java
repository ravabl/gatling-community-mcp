package io.github.gatlingcommunity.mcp.authoring;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ConditionalPlan(
        String expression,
        String left,
        String operator,
        String right,
        List<HttpScenarioStepPlan> steps
) {
    public ConditionalPlan {
        expression = expression == null ? "" : expression;
        left = left == null ? "" : left;
        operator = operator == null || operator.isBlank() ? "equals" : operator;
        right = right == null ? "" : right;
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("expression", expression);
        values.put("left", left);
        values.put("operator", operator);
        values.put("right", right);
        values.put("steps", steps.stream().map(HttpScenarioStepPlan::toMap).toList());
        return values;
    }
}
