package io.github.gatlingcommunity.mcp.validation;

public record ValidationFinding(String code, String severity, String message, String evidence) {
}
