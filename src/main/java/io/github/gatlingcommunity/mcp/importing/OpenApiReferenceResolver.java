package io.github.gatlingcommunity.mcp.importing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class OpenApiReferenceResolver {
    private static final int MAX_REFERENCE_DEPTH = 8;

    private final Map<String, Object> root;
    private final List<ImportWarning> warnings;
    private final Set<String> emittedWarnings = new LinkedHashSet<>();

    OpenApiReferenceResolver(Map<String, Object> root, List<ImportWarning> warnings) {
        this.root = root;
        this.warnings = warnings;
    }

    Map<String, Object> resolveMap(Object value, String path) {
        return map(resolve(value, path, 0, Set.of()));
    }

    private Object resolve(Object value, String path, int depth, Set<String> referenceChain) {
        if (value instanceof Map<?, ?>) {
            return resolveMapValue(map(value), path, depth, referenceChain);
        }
        if (value instanceof List<?> values) {
            var resolved = new ArrayList<Object>();
            for (int index = 0; index < values.size(); index++) {
                resolved.add(resolve(values.get(index), path + "[" + index + "]", depth, referenceChain));
            }
            return List.copyOf(resolved);
        }
        return value;
    }

    private Map<String, Object> resolveMapValue(Map<String, Object> value,
                                                String path,
                                                int depth,
                                                Set<String> referenceChain) {
        var reference = text(value.get("$ref"), "");
        if (!reference.isBlank()) {
            var resolvedReference = resolveReference(value, reference, path, depth, referenceChain);
            if (resolvedReference != null) {
                return resolvedReference;
            }
        }
        return copyMap(value, path, depth, referenceChain);
    }

    private Map<String, Object> resolveReference(Map<String, Object> referenceValue,
                                                 String reference,
                                                 String path,
                                                 int depth,
                                                 Set<String> referenceChain) {
        if (!reference.startsWith("#/components/")) {
            var code = reference.startsWith("#")
                    ? "import.openapi.ref_unsupported"
                    : "import.openapi.remote_ref_unsupported";
            warn(code, path + ".$ref", "Only local OpenAPI #/components/... references are supported.");
            return null;
        }
        if (referenceChain.contains(reference)) {
            warn("import.openapi.ref_circular", path + ".$ref",
                    "A circular local OpenAPI reference was not resolved.");
            return null;
        }
        if (depth >= MAX_REFERENCE_DEPTH) {
            warn("import.openapi.ref_depth_exceeded", path + ".$ref",
                    "Local OpenAPI reference resolution stopped after 8 dereferences.");
            return null;
        }
        var target = localReferenceTarget(reference);
        if (target == null) {
            warn("import.openapi.ref_unresolved", path + ".$ref",
                    "The local OpenAPI reference does not resolve to a component.");
            return null;
        }
        var nextChain = new LinkedHashSet<>(referenceChain);
        nextChain.add(reference);
        var resolvedTarget = resolve(target, path, depth + 1, nextChain);
        if (!(resolvedTarget instanceof Map<?, ?>)) {
            return copyMap(referenceValue, path, depth, referenceChain);
        }
        var merged = new LinkedHashMap<>(map(resolvedTarget));
        for (var entry : referenceValue.entrySet()) {
            if (!"$ref".equals(entry.getKey())) {
                merged.put(entry.getKey(), resolve(entry.getValue(), path + "." + entry.getKey(), depth, referenceChain));
            }
        }
        return merged;
    }

    private Object localReferenceTarget(String reference) {
        Object current = root;
        for (var token : reference.substring(2).split("/")) {
            var currentMap = map(current);
            var key = unescapeJsonPointerToken(token);
            if (currentMap.isEmpty() || !currentMap.containsKey(key)) {
                return null;
            }
            current = currentMap.get(key);
        }
        return current;
    }

    private String unescapeJsonPointerToken(String token) {
        return token.replace("~1", "/").replace("~0", "~");
    }

    private Map<String, Object> copyMap(Map<String, Object> value,
                                        String path,
                                        int depth,
                                        Set<String> referenceChain) {
        var copied = new LinkedHashMap<String, Object>();
        for (var entry : value.entrySet()) {
            copied.put(entry.getKey(), resolve(entry.getValue(), path + "." + entry.getKey(), depth, referenceChain));
        }
        return copied;
    }

    private void warn(String code, String path, String message) {
        if (emittedWarnings.add(code + "\\u0000" + path)) {
            warnings.add(new ImportWarning("warning", code, path, message));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> raw) {
            return (Map<String, Object>) raw;
        }
        return Map.of();
    }

    private static String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        var text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }
}
