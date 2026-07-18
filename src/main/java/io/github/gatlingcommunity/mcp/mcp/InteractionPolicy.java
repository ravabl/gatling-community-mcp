package io.github.gatlingcommunity.mcp.mcp;

public record InteractionPolicy(
        boolean allowElicitation,
        boolean allowSampling,
        boolean allowProgress,
        int maxSamplingCallsPerTool
) {
    public InteractionPolicy {
        maxSamplingCallsPerTool = Math.max(0, maxSamplingCallsPerTool);
    }

    public static InteractionPolicy defaults() {
        return new InteractionPolicy(true, true, true, 1);
    }
}
