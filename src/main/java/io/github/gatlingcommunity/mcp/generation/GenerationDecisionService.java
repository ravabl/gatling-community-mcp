package io.github.gatlingcommunity.mcp.generation;

import io.github.gatlingcommunity.mcp.authoring.HttpSimulationPlan;
import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import java.util.ArrayList;
import java.util.List;

public final class GenerationDecisionService {
    public List<GenerationDecision> explain(TargetContext target, HttpSimulationPlan plan) {
        var decisions = new ArrayList<GenerationDecision>();
        decisions.add(new GenerationDecision(
                "simulation",
                target.language().name(),
                "why: render with a DSL-specific renderer for the requested Gatling language.",
                "target.language",
                "The caller selected the effective DSL language.",
                "Generated code must still be compiled in the user's project.",
                "Run gatling_validate_generated_code and gatling_compile_check."
        ));
        if (!plan.feeders().isEmpty() || plan.toMap().toString().contains("#{username}")) {
            decisions.add(new GenerationDecision(
                    "feeder",
                    "feed + csv",
                    "why: session placeholders such as #{username} should come from feeder data rather than constants.",
                    "plan.feeders and request placeholders",
                    "The generated feeder file exists or will be created by the user.",
                    "Queue feeders can exhaust under higher user counts; circular is safer for a first smoke run.",
                    "Run gatling_check_feeder_risk before executing a load run."
            ));
        }
        if (plan.toMap().toString().contains("jwtToken")) {
            decisions.add(new GenerationDecision(
                    "correlation",
                    "jsonPath.saveAs",
                    "why: the login response token is reused by later requests through a session variable.",
                    "request checks and Authorization header references",
                    "$.token is present in the login response.",
                    "If the token path differs, later authenticated requests will fail with 401/403.",
                    "Run gatling_check_correlation_risk and verify the response body sample."
            ));
        }
        decisions.add(new GenerationDecision(
                "checks",
                "status.is",
                "why: every generated HTTP request gets at least one status check to fail fast on bad responses.",
                "plan.requests.checks",
                "The expected successful status is represented in the plan.",
                "Status-only checks are not enough for business correctness.",
                "Add jsonPath/body checks and run gatling_check_assertion_quality."
        ));
        return List.copyOf(decisions);
    }
}
