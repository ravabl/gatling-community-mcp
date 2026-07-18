package io.github.gatlingcommunity.mcp.importing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class CorrelationInferenceService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Pattern ARRAY_OR_OBJECT = Pattern.compile("^\\s*[\\[{].*");

    private final ObjectMapper json = new ObjectMapper();

    public List<String> inferCandidates(List<Object> responseExamples) {
        var candidates = new LinkedHashSet<String>();
        for (var example : responseExamples == null ? List.of() : responseExamples) {
            collectCandidates(parseIfJson(example), candidates);
        }
        return List.copyOf(candidates);
    }

    public List<String> findUsagesInText(String sourceText, List<String> candidates) {
        var usages = new LinkedHashSet<String>();
        var text = sourceText == null ? "" : sourceText;
        for (var candidate : candidates == null ? List.<String>of() : candidates) {
            if (containsPlaceholder(text, candidate)) {
                usages.add(candidate);
            }
        }
        return List.copyOf(usages);
    }

    private Object parseIfJson(Object value) {
        if (value instanceof String text && ARRAY_OR_OBJECT.matcher(text).matches()) {
            try {
                if (text.stripLeading().startsWith("[")) {
                    return json.readValue(text, List.class);
                }
                return json.readValue(text, MAP_TYPE);
            } catch (JsonProcessingException ignored) {
                return value;
            }
        }
        return value;
    }

    private void collectCandidates(Object value, LinkedHashSet<String> candidates) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nested) -> {
                var name = String.valueOf(key);
                if (isCorrelationName(name)) {
                    candidates.add(name);
                }
                collectCandidates(parseIfJson(nested), candidates);
            });
        } else if (value instanceof List<?> list) {
            for (var item : list) {
                collectCandidates(parseIfJson(item), candidates);
            }
        }
    }

    private boolean isCorrelationName(String name) {
        var lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return "token".equals(lower)
                || "access_token".equals(lower)
                || "id".equals(lower)
                || lower.endsWith("_id")
                || (lower.endsWith("id") && lower.length() > 2);
    }

    private boolean containsPlaceholder(String text, String candidate) {
        return text.contains("{{%s}}".formatted(candidate))
                || text.contains("#{%s}".formatted(candidate));
    }
}
