package io.github.gatlingcommunity.mcp.generation.renderer;

import io.github.gatlingcommunity.mcp.authoring.CheckPlan;
import io.github.gatlingcommunity.mcp.authoring.HttpRequestPlan;
import io.github.gatlingcommunity.mcp.authoring.HttpScenarioStepPlan;
import io.github.gatlingcommunity.mcp.authoring.HttpSimulationPlan;
import io.github.gatlingcommunity.mcp.core.model.DslLanguage;
import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import java.util.stream.Collectors;

public final class JavascriptHttpRenderer extends RendererSupport implements GatlingCodeRenderer {
    private final DslLanguage language;

    public JavascriptHttpRenderer(DslLanguage language) {
        this.language = language == DslLanguage.TYPESCRIPT ? DslLanguage.TYPESCRIPT : DslLanguage.JAVASCRIPT;
    }

    @Override
    public DslLanguage language() {
        return language;
    }

    @Override
    public String render(TargetContext target, HttpSimulationPlan plan) {
        return """
                import { scenario, simulation, exec, feed, group, doIf, jsonPath, StringBody, StringBodyPart, csv, jsonFile, pause, repeat, during, forever, constantUsersPerSec, rampUsersPerSec } from "@gatling.io/core";
                import { http, status } from "@gatling.io/http";

                export default simulation((setUp) => {
                %s
                  const httpProtocol = http
                %s;

                  const scn = scenario("%s")%s;

                  setUp(
                    scn.injectOpen(
                      rampUsersPerSec(%d).to(%d).during(%d),
                      constantUsersPerSec(%d).during(%d)
                    )
                  ).protocols(httpProtocol);
                });
                """.formatted(
                feeders(plan),
                protocol(plan),
                escape(plan.scenarioName()),
                steps(plan).stream().map(JavascriptHttpRenderer::step).collect(Collectors.joining()),
                plan.injectionProfile().rampFromUsersPerSec(),
                plan.injectionProfile().rampToUsersPerSec(),
                plan.injectionProfile().rampDurationSeconds(),
                plan.injectionProfile().constantUsersPerSec(),
                plan.injectionProfile().constantDurationSeconds()
        );
    }

    private static String feeders(HttpSimulationPlan plan) {
        return plan.feeders().stream()
                .map(feeder -> "  const %s = %s(\"%s\").%s();"
                        .formatted(feederVariable(feeder), feederFactory(feeder), escape(feeder.source()), feeder.strategy()))
                .collect(Collectors.joining("\n"));
    }

    private static String protocol(HttpSimulationPlan plan) {
        var builder = new StringBuilder().append("    .baseUrl(\"").append(escape(plan.baseUrl())).append("\")");
        plan.protocolOptions().headers().forEach((name, value) -> {
            if ("accept".equalsIgnoreCase(name)) {
                builder.append("\n    .acceptHeader(\"").append(escape(value)).append("\")");
            } else if ("content-type".equalsIgnoreCase(name)) {
                builder.append("\n    .contentTypeHeader(\"").append(escape(value)).append("\")");
            } else {
                builder.append("\n    .header(\"").append(escape(name)).append("\", \"")
                        .append(escape(value)).append("\")");
            }
        });
        if (!plan.protocolOptions().followRedirects()) {
            builder.append("\n    .disableFollowRedirect()");
        }
        if (plan.protocolOptions().http2()) {
            builder.append("\n    .enableHttp2()");
        }
        if (!plan.protocolOptions().proxyHost().isBlank()) {
            builder.append("\n    .proxy({ host: \"").append(escape(plan.protocolOptions().proxyHost()))
                    .append("\", port: ").append(plan.protocolOptions().proxyPort()).append(" })");
        }
        return builder.toString();
    }

    private static String step(HttpScenarioStepPlan step) {
        return switch (step.type()) {
            case "feed" -> "\n    .feed(%s)".formatted(sanitizeIdentifier(step.feederName()));
            case "pause" -> "\n    .pause(%d)".formatted(step.pause().durationSeconds());
            case "group" -> "\n    .group(\"%s\").on(%s)".formatted(escape(step.group().name()), nested(step.group().steps()));
            case "repeat", "during", "forever" -> "\n    ." + loop(step);
            case "ifEquals", "doIf" -> "\n    .doIf(%s).then(%s)"
                    .formatted(javascriptCondition(step.conditional()), nested(step.conditional().steps()));
            default -> "\n    .exec(%s)".formatted(request(step.request()));
        };
    }

    private static String nested(java.util.List<HttpScenarioStepPlan> steps) {
        return steps.stream()
                .map(step -> switch (step.type()) {
                    case "feed" -> "feed(%s)".formatted(sanitizeIdentifier(step.feederName()));
                    case "pause" -> "pause(%d)".formatted(step.pause().durationSeconds());
                    case "group" -> "group(\"%s\").on(%s)".formatted(escape(step.group().name()), nested(step.group().steps()));
                    case "repeat", "during", "forever" -> loop(step);
                    case "ifEquals", "doIf" -> "doIf(%s).then(%s)"
                            .formatted(javascriptCondition(step.conditional()), nested(step.conditional().steps()));
                    default -> "exec(%s)".formatted(request(step.request()));
                })
                .collect(Collectors.joining(", "));
    }

    private static String loop(HttpScenarioStepPlan step) {
        if ("during".equals(step.type())) {
            return "during(%d).on(%s)".formatted(step.loop().durationSeconds(), nested(step.loop().steps()));
        }
        if ("forever".equals(step.type())) {
            return "forever().on(%s)".formatted(nested(step.loop().steps()));
        }
        return "repeat(%d).on(%s)".formatted(step.loop().count(), nested(step.loop().steps()));
    }

    private static String request(HttpRequestPlan request) {
        var builder = new StringBuilder()
                .append("http(\"").append(escape(request.name())).append("\")")
                .append(".").append(lowerMethod(request)).append("(\"").append(escape(request.path())).append("\")");
        request.headers().forEach((name, value) -> builder.append(".header(\"").append(escape(name))
                .append("\", \"").append(escape(value)).append("\")"));
        request.queryParams().forEach((name, value) -> builder.append(".queryParam(\"").append(escape(name))
                .append("\", \"").append(escape(value)).append("\")"));
        request.formParams().forEach((name, value) -> builder.append(".formParam(\"").append(escape(name))
                .append("\", \"").append(escape(value)).append("\")"));
        request.multipartParts().forEach(part -> builder.append(".bodyPart(StringBodyPart(\"")
                .append(escape(part.name())).append("\", \"").append(escape(part.value().isBlank() ? part.fileName() : part.value()))
                .append("\"))"));
        if (!request.body().isBlank()) {
            builder.append(".body(StringBody(\"").append(escape(request.body())).append("\")).asJson()");
        }
        if (!request.resources().isEmpty()) {
            builder.append(".resources(")
                    .append(request.resources().stream()
                            .map(resource -> "http(\"%s\").%s(\"%s\")"
                                    .formatted(escape(resource.name()), resource.method().toLowerCase(),
                                            escape(resource.path())))
                            .collect(Collectors.joining(", ")))
                    .append(")");
        }
        if ("basic".equalsIgnoreCase(request.auth().type())) {
            builder.append(".basicAuth(\"").append(escape(request.auth().username())).append("\", \"")
                    .append(escape(request.auth().password())).append("\")");
        } else if ("bearer".equalsIgnoreCase(request.auth().type()) && !request.auth().token().isBlank()) {
            builder.append(".header(\"").append(escape(request.auth().headerName())).append("\", \"Bearer ")
                    .append(escape(request.auth().token())).append("\")");
        } else if ("apiKey".equalsIgnoreCase(request.auth().type()) && !request.auth().token().isBlank()) {
            builder.append(".header(\"").append(escape(request.auth().headerName())).append("\", \"")
                    .append(escape(request.auth().token())).append("\")");
        }
        if (!request.cookies().isEmpty() && !request.headers().containsKey("Cookie")) {
            builder.append(".header(\"Cookie\", \"")
                    .append(request.cookies().stream()
                            .map(cookie -> escape(cookie.name()) + "=" + escape(cookie.value()))
                            .collect(Collectors.joining("; ")))
                    .append("\")");
        }
        if (!request.options().followRedirects()) {
            builder.append(".disableFollowRedirect()");
        }
        if (request.options().silent()) {
            builder.append(".silent()");
        }
        request.checks().forEach(check -> builder.append(".check(").append(check(check)).append(")"));
        return builder.toString();
    }

    private static String check(CheckPlan check) {
        if ("status".equals(check.type())) {
            return "status().is(%s)".formatted(javaExpected(check));
        }
        if ("jsonPath".equals(check.type()) && (!check.saveAs().isBlank() || "saveAs".equals(check.operator()))) {
            return "jsonPath(\"%s\").saveAs(\"%s\")".formatted(escape(check.expression()), escape(check.saveAs()));
        }
        return "jsonPath(\"%s\").exists()".formatted(escape(check.expression()));
    }
}
