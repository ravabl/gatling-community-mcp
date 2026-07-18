package io.github.gatlingcommunity.mcp.core.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AuditLogger {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public void event(String event, Map<String, Object> fields) {
        var values = new LinkedHashMap<String, Object>();
        values.put("ts", Instant.now().toString());
        values.put("event", event);
        values.putAll(maskMap(fields == null ? Map.of() : fields));
        try {
            System.err.println(MAPPER.writeValueAsString(values));
        } catch (JsonProcessingException exc) {
            System.err.println("{\"event\":\"audit.serialization_failed\"}");
        }
    }

    private static Map<String, Object> maskMap(Map<String, Object> fields) {
        var values = new LinkedHashMap<String, Object>();
        fields.forEach((key, value) -> values.put(key, maskValue(value)));
        return Map.copyOf(values);
    }

    @SuppressWarnings("unchecked")
    private static Object maskValue(Object value) {
        if (value instanceof String text) {
            return maskSecrets(text);
        }
        if (value instanceof Map<?, ?> map) {
            var values = new LinkedHashMap<String, Object>();
            map.forEach((key, item) -> values.put(String.valueOf(key), maskValue(item)));
            return Map.copyOf(values);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(AuditLogger::maskValue).toList();
        }
        return value;
    }

    private static String maskSecrets(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text
                .replaceAll("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s\"]+", "$1<redacted>")
                .replaceAll("(?i)(password\\s*[:=]\\s*)[^\\s\"]+", "$1<redacted>")
                .replaceAll("(?i)(token\\s*[:=]\\s*)[^\\s\"]+", "$1<redacted>")
                .replaceAll("(?i)(secret\\s*[:=]\\s*)[^\\s\"]+", "$1<redacted>");
    }
}
