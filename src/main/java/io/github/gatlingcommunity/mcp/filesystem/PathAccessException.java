package io.github.gatlingcommunity.mcp.filesystem;

public final class PathAccessException extends IllegalArgumentException {
    private final String code;
    private final String path;
    private final String suggestion;

    public PathAccessException(String code, String path, String message, String suggestion) {
        super(message);
        this.code = code;
        this.path = path;
        this.suggestion = suggestion;
    }

    public String code() {
        return code;
    }

    public String path() {
        return path;
    }

    public String suggestion() {
        return suggestion;
    }
}
