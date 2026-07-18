package io.github.gatlingcommunity.mcp.dsl;

import io.github.gatlingcommunity.mcp.core.model.GatlingDslMethod;
import io.github.gatlingcommunity.mcp.core.model.Protocol;
import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import io.github.gatlingcommunity.mcp.data.SourceDataRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class DslMethodService {
    private final SourceDataRepository sourceData;

    public DslMethodService(SourceDataRepository sourceData) {
        this.sourceData = sourceData;
    }

    public Map<String, Object> validateChain(TargetContext target, Protocol protocol, List<String> methodNames) {
        var matchedLine = sourceData.supportedVersionLineFor(target.gatlingVersion()).orElse("unsupported");
        var methods = catalog(target, protocol);
        var findings = new ArrayList<Map<String, Object>>();
        var transitions = new ArrayList<Map<String, Object>>();
        var requirements = new ArrayList<Map<String, Object>>();
        var seen = new LinkedHashSet<String>();
        var seenCategories = new LinkedHashSet<String>();
        var seenSemantics = new LinkedHashMap<String, Map<String, Object>>();

        for (int index = 0; index < methodNames.size(); index++) {
            var rawName = methodNames.get(index);
            var name = normalizeName(rawName);
            var method = findMethod(methods, name);
            if (method.isEmpty()) {
                var replacement = replacementFor(target.gatlingVersion(), name);
                findings.add(finding(
                        "error",
                        "method_chain.method.unsupported",
                        "$.methods[%d]".formatted(index),
                        "Method is not available for Gatling %s %s %s: %s"
                                .formatted(target.gatlingVersion(), target.language(), protocol, name),
                        replacement.map(value -> "Use replacement method: " + value)
                                .orElse("Call gatling_list_dsl_methods for the selected version, DSL, and protocol.")
                ));
                transitions.add(transition(index, name, "UNKNOWN", "unknown", "unknown", false));
                continue;
            }

            var dslMethod = method.orElseThrow();
            var semantic = semanticForSourceData(dslMethod);
            requirements.add(semantic);
            var requiredPredecessor = semantic.get("requiredPrecedingMethod").toString();
            if (!predecessorSatisfied(requiredPredecessor, seen, seenCategories)) {
                findings.add(finding(
                        "error",
                        "method_chain.missing_predecessor",
                        "$.methods[%d]".formatted(index),
                        "Method %s requires a preceding %s call in this chain."
                                .formatted(dslMethod.name(), predecessorLabel(requiredPredecessor)),
                        "Insert %s before %s or validate a more complete chain."
                                .formatted(predecessorLabel(requiredPredecessor), dslMethod.name())
                ));
            }
            var incompatibleMethods = stringList(semantic.get("incompatibleMethods"));
            for (var incompatibleMethod : incompatibleMethods) {
                if (containsMethod(seen, incompatibleMethod)) {
                    findings.add(incompatibleFinding(index, dslMethod.name(), incompatibleMethod));
                }
            }
            for (var previous : seenSemantics.entrySet()) {
                if (stringList(previous.getValue().get("incompatibleMethods")).stream()
                        .anyMatch(incompatibleMethod -> incompatibleMethod.equalsIgnoreCase(dslMethod.name()))) {
                    findings.add(incompatibleFinding(index, dslMethod.name(), previous.getKey()));
                }
            }
            transitions.add(transition(
                    index,
                    dslMethod.name(),
                    dslMethod.category().name(),
                    semantic.get("allowedParentContext").toString(),
                    semantic.get("returnType").toString(),
                    true
            ));
            seen.add(dslMethod.name());
            seenCategories.add(dslMethod.category().name());
            seenSemantics.put(dslMethod.name(), semantic);
        }

        var valid = findings.stream().noneMatch(finding -> "error".equals(finding.get("severity")));
        var values = new LinkedHashMap<String, Object>();
        values.put("valid", valid);
        values.put("gatlingVersion", target.gatlingVersion());
        values.put("matchedGatlingLine", matchedLine);
        values.put("language", target.language().name());
        values.put("protocol", protocol.name());
        values.put("methodCount", methodNames.size());
        values.put("findings", findings);
        values.put("transitions", transitions);
        values.put("methodRequirements", requirements);
        return Map.copyOf(values);
    }

    public Map<String, Object> explainMethod(TargetContext target, Protocol protocol, String methodName) {
        var matchedLine = sourceData.supportedVersionLineFor(target.gatlingVersion()).orElse("unsupported");
        var methods = catalog(target, protocol);
        var method = findMethod(methods, methodName);
        var values = new LinkedHashMap<String, Object>();
        values.put("found", method.isPresent());
        values.put("gatlingVersion", target.gatlingVersion());
        values.put("matchedGatlingLine", matchedLine);
        values.put("language", target.language().name());
        values.put("protocol", protocol.name());
        values.put("methodName", methodName);
        if (method.isPresent()) {
            var dslMethod = method.orElseThrow();
            values.put("method", methodMap(dslMethod));
            values.put("semantic", semanticForSourceData(dslMethod));
            values.put("replacements", List.of());
        } else {
            values.put("method", Map.of());
            values.put("semantic", Map.of());
            values.put("replacements", replacementMaps(target.gatlingVersion(), methodName));
        }
        return Map.copyOf(values);
    }

    public Map<String, Object> findReplacement(TargetContext target, Protocol protocol, String methodName) {
        var replacements = replacementMaps(target.gatlingVersion(), methodName);
        var values = new LinkedHashMap<String, Object>();
        values.put("found", !replacements.isEmpty());
        values.put("gatlingVersion", target.gatlingVersion());
        values.put("matchedGatlingLine", sourceData.supportedVersionLineFor(target.gatlingVersion()).orElse("unsupported"));
        values.put("language", target.language().name());
        values.put("protocol", protocol.name());
        values.put("methodName", methodName);
        values.put("replacements", replacements);
        return Map.copyOf(values);
    }

    private List<GatlingDslMethod> catalog(TargetContext target, Protocol protocol) {
        return sourceData.dslMethods(target, protocol);
    }

    private static Optional<GatlingDslMethod> findMethod(List<GatlingDslMethod> methods, String methodName) {
        var normalized = normalizeName(methodName);
        return methods.stream()
                .filter(method -> method.name().equals(normalized)
                        || method.name().equalsIgnoreCase(normalized))
                .findFirst();
    }

    private Optional<String> replacementFor(String gatlingVersion, String methodName) {
        return replacementMaps(gatlingVersion, methodName).stream()
                .map(value -> value.get("replacement").toString())
                .findFirst();
    }

    private List<Map<String, Object>> replacementMaps(String gatlingVersion, String methodName) {
        var normalized = normalizeName(methodName).toLowerCase(Locale.ROOT);
        return sourceData.featureRulesFor(gatlingVersion).stream()
                .filter(rule -> !rule.invalidPattern().isBlank())
                .filter(rule -> {
                    var invalid = rule.invalidPattern().toLowerCase(Locale.ROOT);
                    return invalid.equals(normalized)
                            || invalid.contains(normalized)
                            || normalized.contains(invalid);
                })
                .map(rule -> Map.<String, Object>of(
                        "since", rule.since(),
                        "invalidPattern", rule.invalidPattern(),
                        "replacement", rule.replacement(),
                        "message", rule.message()
                ))
                .toList();
    }

    private Map<String, Object> semanticForSourceData(GatlingDslMethod method) {
        var rule = sourceData.semanticRule(method).orElseThrow();
        var values = new LinkedHashMap<String, Object>();
        values.put("name", method.name());
        values.put("category", method.category().name());
        values.put("allowedParentContext", rule.allowedParentContext());
        values.put("returnType", rule.returnType());
        values.put("chainType", rule.chainType());
        values.put("requiredPrecedingMethod", rule.requiredPrecedingMethod());
        values.put("incompatibleMethods", rule.incompatibleMethods());
        values.put("dslSpecificSyntax", rule.dslSpecificSyntax().getOrDefault(method.language(), method.callTemplate()));
        values.put("exampleSnippet", rule.exampleSnippet());
        values.put("compileRiskNotes", rule.compileRiskNotes());
        return Map.copyOf(values);
    }

    private static Map<String, Object> methodMap(GatlingDslMethod method) {
        return Map.of(
                "name", method.name(),
                "category", method.category().name(),
                "since", method.since(),
                "until", method.until(),
                "callTemplate", method.callTemplate(),
                "sourceUrl", method.sourceUrl(),
                "confidenceLevel", method.confidenceLevel().name()
        );
    }

    private static Map<String, Object> transition(int index,
                                                  String method,
                                                  String category,
                                                  String allowedParentContext,
                                                  String returnType,
                                                  boolean known) {
        return Map.of(
                "index", index,
                "method", method,
                "category", category,
                "allowedParentContext", allowedParentContext,
                "returnType", returnType,
                "known", known
        );
    }

    private static Map<String, Object> finding(String severity,
                                               String code,
                                               String path,
                                               String message,
                                               String suggestion) {
        return Map.of(
                "severity", severity,
                "code", code,
                "path", path,
                "message", message,
                "suggestion", suggestion
        );
    }

    private static Map<String, Object> incompatibleFinding(int index,
                                                           String method,
                                                           String incompatibleMethod) {
        return finding(
                "error",
                "method_chain.incompatible_method",
                "$.methods[%d]".formatted(index),
                "Method %s is incompatible with %s in the same semantic chain."
                        .formatted(method, incompatibleMethod),
                "Split the request shape or remove one of the incompatible methods."
        );
    }

    private static boolean predecessorSatisfied(String requiredPredecessor,
                                                LinkedHashSet<String> seen,
                                                LinkedHashSet<String> seenCategories) {
        if (requiredPredecessor == null || requiredPredecessor.isBlank()) {
            return true;
        }
        return switch (requiredPredecessor) {
            case "http-request" -> seenCategories.contains("HTTP_REQUEST");
            case "feeder-source" -> containsMethod(seen, "csv")
                    || containsMethod(seen, "ssv")
                    || containsMethod(seen, "tsv")
                    || containsMethod(seen, "jsonFile")
                    || containsMethod(seen, "sitemap")
                    || containsMethod(seen, "arrayFeeder")
                    || containsMethod(seen, "listFeeder")
                    || containsMethod(seen, "jdbcFeeder")
                    || containsMethod(seen, "redisFeeder");
            default -> containsMethod(seen, requiredPredecessor);
        };
    }

    private static String predecessorLabel(String requiredPredecessor) {
        return switch (requiredPredecessor) {
            case "http-request" -> "an HTTP request builder";
            case "feeder-source" -> "a feeder source such as csv/jsonFile/arrayFeeder";
            default -> requiredPredecessor;
        };
    }

    private static boolean containsMethod(LinkedHashSet<String> methods, String expected) {
        return methods.stream().anyMatch(method -> method.equalsIgnoreCase(expected));
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    private static String normalizeName(String methodName) {
        return methodName == null ? "" : methodName.trim();
    }
}
