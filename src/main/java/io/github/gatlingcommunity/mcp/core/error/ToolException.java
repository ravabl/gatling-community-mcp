package io.github.gatlingcommunity.mcp.core.error;

public final class ToolException extends RuntimeException {
    private final ToolError error;

    public ToolException(ToolError error) {
        super(error == null ? "tool.error" : error.message());
        this.error = error == null ? new ToolError(null, null, null, null, null, null) : error;
    }

    public ToolError error() {
        return error;
    }
}
