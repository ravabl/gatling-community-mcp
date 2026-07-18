package io.github.gatlingcommunity.mcp.generation;

public final class PatchGenerationService {
    public String unifiedDiff(String targetPath, String generatedCode) {
        var path = targetPath == null || targetPath.isBlank() ? "OrdersSimulation.java" : targetPath;
        return """
                --- a/%s
                +++ b/%s
                @@ -0,0 +1,%d @@
                %s
                """.formatted(path, path, generatedCode.lines().count(), prefixed(generatedCode));
    }

    private static String prefixed(String generatedCode) {
        return generatedCode.lines()
                .map(line -> "+" + line)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("+");
    }
}
