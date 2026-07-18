package io.github.gatlingcommunity.mcp.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspacePathPolicyTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesRelativeAndNonExistingPathsInsideRoot() throws Exception {
        var project = Files.createDirectory(tempDir.resolve("project"));
        var existing = Files.writeString(project.resolve("pom.xml"), "<project/>");
        var policy = new WorkspacePathPolicy(List.of(project));

        assertThat(policy.resolve("pom.xml", "path")).isEqualTo(existing.toRealPath());
        assertThat(policy.resolve("generated/Simulation.java", "path"))
                .isEqualTo(project.toRealPath().resolve("generated/Simulation.java"));
        assertThat(policy.roots()).containsExactly(project.toRealPath());
    }

    @Test
    void parsesMultipleEnvironmentRootsAndDropsBlankItems() throws Exception {
        var first = Files.createDirectory(tempDir.resolve("first"));
        var second = Files.createDirectory(tempDir.resolve("second"));

        var policy = WorkspacePathPolicy.fromEnvironment(Map.of(
                WorkspacePathPolicy.ROOTS_ENV, first + ",  ," + second
        ));

        assertThat(policy.roots()).containsExactly(first.toRealPath(), second.toRealPath());
    }

    @Test
    void rejectsMissingOutsideAndSymlinkEscapePaths() throws Exception {
        var root = Files.createDirectory(tempDir.resolve("root"));
        var outside = Files.createDirectory(tempDir.resolve("outside"));
        var link = root.resolve("outside-link");
        Files.createSymbolicLink(link, outside);
        var policy = new WorkspacePathPolicy(List.of(root));

        assertThatThrownBy(() -> policy.resolve(" ", "projectPath"))
                .isInstanceOf(PathAccessException.class)
                .hasMessageContaining("Missing path argument");
        assertThatThrownBy(() -> policy.resolve(outside.toString(), "projectPath"))
                .isInstanceOf(PathAccessException.class)
                .hasMessageContaining("outside configured workspace roots");
        assertThatThrownBy(() -> policy.resolve(link.toString(), "projectPath"))
                .isInstanceOf(PathAccessException.class)
                .hasMessageContaining("outside configured workspace roots");
    }

    @Test
    void rejectsEmptyOrMissingWorkspaceRoots() {
        assertThatThrownBy(() -> new WorkspacePathPolicy(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one workspace root");
        assertThatThrownBy(() -> new WorkspacePathPolicy(List.of(tempDir.resolve("missing"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("existing directory");
    }

    @Test
    void usesDefaultRootForBlankEnvironmentValue() {
        var blank = WorkspacePathPolicy.fromEnvironment(Map.of(WorkspacePathPolicy.ROOTS_ENV, " "));
        var nullEnvironment = WorkspacePathPolicy.fromEnvironment(null);

        assertThat(blank.roots()).containsExactly(Path.of("").toAbsolutePath().normalize().toFile().toPath());
        assertThat(nullEnvironment.roots()).isEqualTo(blank.roots());
    }
}
