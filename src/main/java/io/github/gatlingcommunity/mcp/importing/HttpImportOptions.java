package io.github.gatlingcommunity.mcp.importing;

public record HttpImportOptions(
        String simulationClassName,
        String scenarioName,
        String baseUrl
) {
    public HttpImportOptions {
        simulationClassName = normalize(simulationClassName);
        scenarioName = normalize(scenarioName);
        baseUrl = normalize(baseUrl);
    }

    public static HttpImportOptions defaults() {
        return new HttpImportOptions("", "", "");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
