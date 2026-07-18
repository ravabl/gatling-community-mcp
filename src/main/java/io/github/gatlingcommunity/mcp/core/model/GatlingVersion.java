package io.github.gatlingcommunity.mcp.core.model;

public record GatlingVersion(String value) implements Comparable<GatlingVersion> {
    public GatlingVersion {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Gatling version must not be blank");
        }
    }

    @Override
    public int compareTo(GatlingVersion other) {
        return compare(value, other.value);
    }

    public static boolean atLeast(String current, String minimum) {
        return compare(current, minimum) >= 0;
    }

    private static int compare(String left, String right) {
        var leftParts = left.split("\\.");
        var rightParts = right.split("\\.");
        var max = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < max; index++) {
            var leftValue = index < leftParts.length ? parsePart(leftParts[index]) : 0;
            var rightValue = index < rightParts.length ? parsePart(rightParts[index]) : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private static int parsePart(String value) {
        var digits = value.replaceAll("[^0-9].*$", "");
        return digits.isBlank() ? 0 : Integer.parseInt(digits);
    }
}
