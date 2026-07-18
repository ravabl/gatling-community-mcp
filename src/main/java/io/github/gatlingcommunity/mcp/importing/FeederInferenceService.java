package io.github.gatlingcommunity.mcp.importing;

import io.github.gatlingcommunity.mcp.authoring.HttpRequestPlan;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

public final class FeederInferenceService {
    private static final Pattern PLACEHOLDER = Pattern.compile("(?:#\\{|\\{\\{)([A-Za-z_][A-Za-z0-9_.-]*)(?:}|}})");

    public List<String> inferRequestPlaceholderCandidates(List<HttpRequestPlan> requests) {
        var candidates = new LinkedHashSet<String>();
        for (var request : requests == null ? List.<HttpRequestPlan>of() : requests) {
            collectPlaceholders(request.path(), candidates);
            collectPlaceholders(request.body(), candidates);
            request.formParams().values().forEach(value -> collectPlaceholders(value, candidates));
            request.multipartParts().forEach(part -> {
                collectPlaceholders(part.value(), candidates);
                collectPlaceholders(part.fileName(), candidates);
            });
        }
        return List.copyOf(candidates);
    }

    private void collectPlaceholders(String value, LinkedHashSet<String> candidates) {
        var matcher = PLACEHOLDER.matcher(value == null ? "" : value);
        while (matcher.find()) {
            candidates.add(matcher.group(1));
        }
    }
}
