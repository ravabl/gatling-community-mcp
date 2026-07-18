package io.github.gatlingcommunity.mcp.core.safety;

import io.github.gatlingcommunity.mcp.core.error.ToolError;
import io.github.gatlingcommunity.mcp.core.error.ToolException;
import java.util.List;
import java.util.Map;

public final class InputLimits {
    public static final int CODE_MAX_CHARS = 1_000_000;
    public static final int IMPORT_DOCUMENT_MAX_CHARS = 2_000_000;
    public static final int LOG_TEXT_MAX_CHARS = 5_000_000;
    public static final int REPORT_CONTENT_MAX_CHARS = 2_000_000;
    public static final int CURL_MAX_CHARS = 200_000;
    public static final int TOOL_ARGUMENT_MAX_DEPTH = 24;
    public static final int TOOL_ARGUMENT_MAX_NODES = 25_000;
    public static final int TOOL_ARGUMENT_MAX_TOTAL_CHARS = 8_000_000;
    public static final int TOOL_ARGUMENT_MAX_STRING_CHARS = 8_000_000;
    public static final int TOOL_ARGUMENT_MAX_KEY_CHARS = 256;

    private InputLimits() {
    }

    public static String requireMaxChars(String value, int maxChars, String path) {
        var safeValue = value == null ? "" : value;
        if (safeValue.length() > maxChars) {
            throw new ToolException(new ToolError(
                    "input.too_large",
                    "error",
                    path,
                    "Input value at %s has %d characters, which exceeds the limit of %d."
                            .formatted(path, safeValue.length(), maxChars),
                    "Reduce the input size or pass a smaller extracted subset.",
                    Map.of(
                            "maxChars", maxChars,
                            "actualChars", safeValue.length()
                    )
            ));
        }
        return safeValue;
    }

    public static void requireToolInputWithinLimits(Map<String, Object> args) {
        var counter = new Counter();
        inspect(args == null ? Map.of() : args, "$", 0, counter);
        if (counter.totalChars > TOOL_ARGUMENT_MAX_TOTAL_CHARS) {
            throwLimit(
                    "input.total_size_exceeded",
                    "$",
                    "Tool arguments contain %d characters, which exceeds the total limit of %d."
                            .formatted(counter.totalChars, TOOL_ARGUMENT_MAX_TOTAL_CHARS),
                    Map.of(
                            "maxChars", TOOL_ARGUMENT_MAX_TOTAL_CHARS,
                            "actualChars", counter.totalChars
                    )
            );
        }
    }

    @SuppressWarnings("unchecked")
    private static void inspect(Object value, String path, int depth, Counter counter) {
        if (depth > TOOL_ARGUMENT_MAX_DEPTH) {
            throwLimit(
                    "input.depth_exceeded",
                    path,
                    "Tool argument depth exceeds the limit of %d.".formatted(TOOL_ARGUMENT_MAX_DEPTH),
                    Map.of("maxDepth", TOOL_ARGUMENT_MAX_DEPTH)
            );
        }
        counter.nodes++;
        if (counter.nodes > TOOL_ARGUMENT_MAX_NODES) {
            throwLimit(
                    "input.node_count_exceeded",
                    path,
                    "Tool arguments contain more than %d nodes.".formatted(TOOL_ARGUMENT_MAX_NODES),
                    Map.of("maxNodes", TOOL_ARGUMENT_MAX_NODES, "actualNodes", counter.nodes)
            );
        }
        if (value instanceof String text) {
            counter.totalChars += text.length();
            if (text.length() > TOOL_ARGUMENT_MAX_STRING_CHARS) {
                throwLimit(
                        "input.string_too_large",
                        path,
                        "Input string at %s has %d characters, which exceeds the limit of %d."
                                .formatted(path, text.length(), TOOL_ARGUMENT_MAX_STRING_CHARS),
                        Map.of(
                                "maxChars", TOOL_ARGUMENT_MAX_STRING_CHARS,
                                "actualChars", text.length()
                        )
                );
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                var key = String.valueOf(entry.getKey());
                counter.totalChars += key.length();
                if (key.length() > TOOL_ARGUMENT_MAX_KEY_CHARS) {
                    throwLimit(
                            "input.key_too_large",
                            path,
                            "Input object key at %s has %d characters, which exceeds the limit of %d."
                                    .formatted(path, key.length(), TOOL_ARGUMENT_MAX_KEY_CHARS),
                            Map.of(
                                    "maxChars", TOOL_ARGUMENT_MAX_KEY_CHARS,
                                    "actualChars", key.length()
                            )
                    );
                }
                inspect(entry.getValue(), path + "." + key, depth + 1, counter);
            }
            return;
        }
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                inspect(list.get(i), path + "[%d]".formatted(i), depth + 1, counter);
            }
        }
    }

    private static void throwLimit(String code, String path, String message, Map<String, Object> details) {
        throw new ToolException(new ToolError(
                code,
                "error",
                path,
                message,
                "Reduce the input size or pass a smaller extracted subset.",
                details
        ));
    }

    private static final class Counter {
        private int nodes;
        private int totalChars;
    }
}
