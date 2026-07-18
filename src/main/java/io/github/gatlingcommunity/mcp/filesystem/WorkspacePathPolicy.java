package io.github.gatlingcommunity.mcp.filesystem;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class WorkspacePathPolicy {
    public static final String ROOTS_ENV = "GATLING_MCP_WORKSPACE_ROOTS";

    private final List<Path> roots;

    public WorkspacePathPolicy(List<Path> roots) {
        if (roots == null || roots.isEmpty()) {
            throw new IllegalArgumentException("At least one workspace root is required");
        }
        this.roots = roots.stream()
                .map(WorkspacePathPolicy::normalizeExistingRoot)
                .distinct()
                .toList();
    }

    public static WorkspacePathPolicy defaults() {
        return new WorkspacePathPolicy(List.of(Path.of("").toAbsolutePath().normalize()));
    }

    public static WorkspacePathPolicy fromEnvironment(Map<String, String> env) {
        var value = env == null ? "" : env.getOrDefault(ROOTS_ENV, "");
        if (value == null || value.isBlank()) {
            return defaults();
        }
        var roots = new ArrayList<Path>();
        for (var item : value.split(",")) {
            var trimmed = item.trim();
            if (!trimmed.isBlank()) {
                roots.add(Path.of(trimmed));
            }
        }
        return roots.isEmpty() ? defaults() : new WorkspacePathPolicy(roots);
    }

    public List<Path> roots() {
        return roots;
    }

    public Path resolve(String rawPath, String argumentName) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new PathAccessException(
                    "path.missing",
                    argumentName,
                    "Missing path argument: " + argumentName,
                    "Pass a project/report/log path inside one of the configured workspace roots."
            );
        }
        var candidate = Path.of(rawPath);
        if (!candidate.isAbsolute()) {
            candidate = roots.getFirst().resolve(candidate);
        }
        var normalized = normalizeCandidate(candidate);
        if (roots.stream().noneMatch(normalized::startsWith)) {
            throw new PathAccessException(
                    "path.outside_workspace",
                    normalized.toString(),
                    "Path is outside configured workspace roots: " + normalized,
                    "Use a path under one of: " + roots
            );
        }
        return normalized;
    }

    private static Path normalizeExistingRoot(Path root) {
        var absolute = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(absolute)) {
            throw new IllegalArgumentException("Workspace root must be an existing directory: " + absolute);
        }
        try {
            return absolute.toRealPath();
        } catch (IOException exc) {
            throw new UncheckedIOException("Failed to resolve workspace root: " + absolute, exc);
        }
    }

    private static Path normalizeCandidate(Path candidate) {
        var absolute = candidate.toAbsolutePath().normalize();
        if (!Files.exists(absolute)) {
            return absolute;
        }
        try {
            return absolute.toRealPath();
        } catch (IOException exc) {
            throw new UncheckedIOException("Failed to resolve path: " + absolute, exc);
        }
    }
}
