package io.github.gatlingcommunity.mcp.analysis;

import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import io.github.gatlingcommunity.mcp.validation.FeatureUsageValidator;
import java.util.ArrayList;
import java.util.regex.Pattern;

public final class SimulationAnalyzer {
    private static final Pattern SCENARIO = Pattern.compile("scenario\\(\"([^\"]+)\"\\)");
    private static final Pattern HTTP_REQUEST = Pattern.compile("http\\(\"([^\"]+)\"\\)");
    private static final Pattern CORRELATION = Pattern.compile("saveAs\\(\"([^\"]+)\"\\)");

    private final FeatureUsageValidator validator;

    public SimulationAnalyzer(FeatureUsageValidator validator) {
        this.validator = validator;
    }

    public AnalysisResult analyze(TargetContext target, String code) {
        var source = code == null ? "" : code;
        return new AnalysisResult(
                target,
                matches(SCENARIO, source),
                invocations(source, "baseUrl(", "baseUrls(", "protocols(", "proxy("),
                matches(HTTP_REQUEST, source),
                checks(source),
                invocations(source, "csv(", "ssv(", "tsv(", "jsonFile(", "jdbcFeeder(",
                        "redisFeeder(", "RandomUUIDFeeder(", "GeneratedFeeder(", ".feed("),
                matches(CORRELATION, source),
                invocations(source, "atOnceUsers(", "rampUsers(", "constantUsersPerSec(",
                        "rampUsersPerSec(", "stressPeakUsers(", "nothingFor(", "incrementUsersPerSec("),
                invocations(source, ".assertions("),
                validator.validate(target, source).findings()
        );
    }

    private static ArrayList<String> matches(Pattern pattern, String code) {
        var result = new ArrayList<String>();
        var matcher = pattern.matcher(code);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private static ArrayList<String> checks(String code) {
        var result = new ArrayList<String>();
        var marker = ".check(";
        var start = code.indexOf(marker);
        while (start >= 0) {
            var contentStart = start + marker.length();
            var contentEnd = closingParenthesisIndex(code, contentStart);
            if (contentEnd < 0) {
                break;
            }
            result.add(code.substring(contentStart, contentEnd));
            start = code.indexOf(marker, contentEnd + 1);
        }
        return result;
    }

    private static ArrayList<String> invocations(String code, String... markers) {
        var result = new ArrayList<String>();
        for (var marker : markers) {
            var start = code.indexOf(marker);
            while (start >= 0) {
                var openingParenthesis = code.indexOf('(', start);
                var end = openingParenthesis < 0 ? -1 : closingParenthesisIndex(code, openingParenthesis + 1);
                if (end < 0) {
                    break;
                }
                result.add(code.substring(start, end + 1).strip());
                start = code.indexOf(marker, end + 1);
            }
        }
        return result;
    }

    private static int closingParenthesisIndex(String code, int start) {
        var depth = 1;
        var inString = false;
        for (var index = start; index < code.length(); index++) {
            var current = code.charAt(index);
            var previous = index == 0 ? '\0' : code.charAt(index - 1);
            if (current == '"' && previous != '\\') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }
}
