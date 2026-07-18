package io.github.gatlingcommunity.mcp.importing;

import io.github.gatlingcommunity.mcp.authoring.HttpRequestPlan;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class CodebaseEndpointExtractor {
    private static final int DEFAULT_MAX_FILES = 500;
    private static final int ABSOLUTE_MAX_FILES = 2_000;
    private static final Pattern SPRING_MAPPING = Pattern.compile(
            "@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping|RequestMapping)\\s*(?:\\(([^)]*)\\))?",
            Pattern.DOTALL
    );
    private static final Pattern REQUEST_METHOD = Pattern.compile("RequestMethod\\.([A-Z]+)");
    private static final Pattern FIRST_STRING_LITERAL = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern NODE_ROUTE = Pattern.compile(
            "\\b(?:app|router|fastify|server)\\.(get|post|put|patch|delete|options|head)\\s*\\(\\s*['\"]([^'\"]+)['\"]",
            Pattern.CASE_INSENSITIVE
    );

    private final HttpImportService importService = new HttpImportService();

    public CodebaseEndpointExtractionResult extract(Path root, String languageHint, int maxFiles) {
        var effectiveMaxFiles = normalizeMaxFiles(maxFiles);
        var warnings = new ArrayList<ImportWarning>();
        var endpoints = new ArrayList<EndpointInventory.Endpoint>();
        var authSchemes = new LinkedHashSet<String>();
        var matchedFiles = 0;

        var candidates = candidateFiles(root);
        if (candidates.size() > effectiveMaxFiles) {
            warnings.add(new ImportWarning("warning", "codebase.file_limit_reached", root.toString(),
                    "Codebase scan reached maxFiles=%d before reading all %d candidate files."
                            .formatted(effectiveMaxFiles, candidates.size())));
        }

        for (var file : candidates.stream().limit(effectiveMaxFiles).toList()) {
            var relativePath = root.relativize(file).toString();
            var content = readFile(file, warnings);
            if (content.isBlank()) {
                continue;
            }
            var beforeCount = endpoints.size();
            if (isSpringCandidate(file, content, languageHint)) {
                endpoints.addAll(extractSpringEndpoints(relativePath, content));
            }
            if (isNodeCandidate(file, content, languageHint)) {
                endpoints.addAll(extractNodeEndpoints(relativePath, content));
            }
            if (isOpenApiCandidate(file, content)) {
                var imported = importOpenApi(relativePath, content, warnings);
                endpoints.addAll(imported.endpointInventory().endpoints());
                authSchemes.addAll(imported.endpointInventory().authSchemes());
            }
            if (endpoints.size() > beforeCount) {
                matchedFiles++;
            }
        }

        return new CodebaseEndpointExtractionResult(
                EndpointInventory.fromEndpoints(endpoints, List.copyOf(authSchemes), List.of()),
                warnings,
                Math.min(candidates.size(), effectiveMaxFiles),
                matchedFiles
        );
    }

    private List<Path> candidateFiles(Path root) {
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedFile)
                    .filter(path -> !isSkippedPath(root.relativize(path)))
                    .sorted()
                    .toList();
        } catch (IOException exc) {
            throw new UncheckedIOException("Failed to scan codebase endpoints under " + root, exc);
        }
    }

    private List<EndpointInventory.Endpoint> extractSpringEndpoints(String relativePath, String content) {
        var endpoints = new ArrayList<EndpointInventory.Endpoint>();
        var classIndex = firstTypeIndex(content);
        var basePath = classIndex < 0 ? "" : lastClassRequestMapping(content.substring(0, classIndex));
        var searchable = classIndex < 0 ? content : content.substring(classIndex);
        var matcher = SPRING_MAPPING.matcher(searchable);
        while (matcher.find()) {
            var annotation = matcher.group(1);
            var args = matcher.group(2) == null ? "" : matcher.group(2);
            var method = springMethod(annotation, args);
            var path = joinPaths(basePath, firstStringLiteral(args));
            endpoints.add(endpoint(method + " " + path, method, path, List.of(springTag(path)), relativePath));
        }
        return endpoints;
    }

    private List<EndpointInventory.Endpoint> extractNodeEndpoints(String relativePath, String content) {
        var endpoints = new ArrayList<EndpointInventory.Endpoint>();
        var matcher = NODE_ROUTE.matcher(content);
        while (matcher.find()) {
            var method = matcher.group(1).toUpperCase(Locale.ROOT);
            var path = matcher.group(2);
            endpoints.add(endpoint(method + " " + path, method, path, List.of(springTag(path)), relativePath));
        }
        return endpoints;
    }

    private HttpImportResult importOpenApi(String relativePath, String content, List<ImportWarning> warnings) {
        try {
            return importService.importOpenApi(content, HttpImportOptions.defaults());
        } catch (IllegalArgumentException exc) {
            warnings.add(new ImportWarning("warning", "codebase.openapi_import_failed", relativePath,
                    "OpenAPI-like file could not be imported: " + exc.getMessage()));
            return new HttpImportResult("OPENAPI", "unknown",
                    new io.github.gatlingcommunity.mcp.authoring.HttpSimulationPlan(
                            "ImportedCodebaseOpenApiSimulation",
                            "Imported Codebase OpenAPI",
                            "https://example.test",
                            List.of(),
                            io.github.gatlingcommunity.mcp.authoring.InjectionProfilePlan.defaultOpenModel(),
                            List.of(io.github.gatlingcommunity.mcp.authoring.AssertionPlan.defaultFailedRequests())
                    ),
                    List.of()
            );
        }
    }

    private EndpointInventory.Endpoint endpoint(String name,
                                                String method,
                                                String path,
                                                List<String> tags,
                                                String relativePath) {
        return EndpointInventory.Endpoint.fromRequest(
                new HttpRequestPlan(name, method, path, java.util.Map.of(), "", List.of()),
                tags,
                relativePath
        );
    }

    private String readFile(Path file, List<ImportWarning> warnings) {
        try {
            return Files.readString(file);
        } catch (IOException exc) {
            warnings.add(new ImportWarning("warning", "codebase.file_read_failed", file.toString(),
                    "File could not be read during endpoint extraction: " + exc.getMessage()));
            return "";
        }
    }

    private boolean isSpringCandidate(Path file, String content, String languageHint) {
        var name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return (name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".scala")
                || "JAVA".equalsIgnoreCase(languageHint) || "KOTLIN".equalsIgnoreCase(languageHint))
                && content.contains("Mapping");
    }

    private boolean isNodeCandidate(Path file, String content, String languageHint) {
        var name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return (name.endsWith(".js") || name.endsWith(".ts")
                || "JAVASCRIPT".equalsIgnoreCase(languageHint) || "TYPESCRIPT".equalsIgnoreCase(languageHint))
                && NODE_ROUTE.matcher(content).find();
    }

    private boolean isOpenApiCandidate(Path file, String content) {
        var name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return (name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".json"))
                && (content.contains("openapi:") || content.contains("\"openapi\"") || content.contains("\"swagger\""));
    }

    private boolean isSupportedFile(Path path) {
        var name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".java")
                || name.endsWith(".kt")
                || name.endsWith(".scala")
                || name.endsWith(".js")
                || name.endsWith(".ts")
                || name.endsWith(".yaml")
                || name.endsWith(".yml")
                || name.endsWith(".json");
    }

    private boolean isSkippedPath(Path relative) {
        var value = relative.toString().replace('\\', '/');
        return value.contains("/target/")
                || value.startsWith("target/")
                || value.contains("/build/")
                || value.startsWith("build/")
                || value.contains("/node_modules/")
                || value.startsWith("node_modules/")
                || value.contains("/.git/")
                || value.startsWith(".git/");
    }

    private int normalizeMaxFiles(int maxFiles) {
        if (maxFiles <= 0) {
            return DEFAULT_MAX_FILES;
        }
        return Math.min(maxFiles, ABSOLUTE_MAX_FILES);
    }

    private int firstTypeIndex(String content) {
        var classIndex = content.indexOf("class ");
        var interfaceIndex = content.indexOf("interface ");
        var recordIndex = content.indexOf("record ");
        return List.of(classIndex, interfaceIndex, recordIndex).stream()
                .filter(index -> index >= 0)
                .min(Integer::compareTo)
                .orElse(-1);
    }

    private String lastClassRequestMapping(String content) {
        var matcher = Pattern.compile("@RequestMapping\\s*\\(([^)]*)\\)", Pattern.DOTALL).matcher(content);
        var result = "";
        while (matcher.find()) {
            result = firstStringLiteral(matcher.group(1));
        }
        return result;
    }

    private String firstStringLiteral(String value) {
        var matcher = FIRST_STRING_LITERAL.matcher(value == null ? "" : value);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String springMethod(String annotation, String args) {
        return switch (annotation) {
            case "PostMapping" -> "POST";
            case "PutMapping" -> "PUT";
            case "PatchMapping" -> "PATCH";
            case "DeleteMapping" -> "DELETE";
            case "RequestMapping" -> {
                var matcher = REQUEST_METHOD.matcher(args == null ? "" : args);
                yield matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : "GET";
            }
            default -> "GET";
        };
    }

    private String joinPaths(String basePath, String childPath) {
        var base = basePath == null ? "" : basePath.trim();
        var child = childPath == null ? "" : childPath.trim();
        if (base.isBlank()) {
            return child.isBlank() ? "/" : ensureLeadingSlash(child);
        }
        if (child.isBlank() || "/".equals(child)) {
            return ensureLeadingSlash(base);
        }
        return (ensureLeadingSlash(base) + "/" + child.replaceFirst("^/+", "")).replaceAll("/{2,}", "/");
    }

    private String ensureLeadingSlash(String value) {
        return value.startsWith("/") ? value : "/" + value;
    }

    private String springTag(String path) {
        var normalized = path == null ? "" : path.replaceFirst("^/+", "");
        var first = normalized.split("[/?]", 2)[0];
        return first.isBlank() ? "default" : first;
    }
}
