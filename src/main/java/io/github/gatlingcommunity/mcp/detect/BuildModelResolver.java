package io.github.gatlingcommunity.mcp.detect;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import java.nio.file.Path;

public interface BuildModelResolver {
    BuildTool buildTool();

    boolean supports(Path root);

    BuildModel resolve(Path root);
}
