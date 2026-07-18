package io.github.gatlingcommunity.mcp.mcp;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SdkMcpClientInteraction implements McpClientInteraction {
    private final McpSyncServerExchange exchange;
    private final Object progressToken;
    private final InteractionPolicy policy;

    private SdkMcpClientInteraction(McpSyncServerExchange exchange, Object progressToken, InteractionPolicy policy) {
        this.exchange = exchange;
        this.progressToken = progressToken;
        this.policy = policy == null ? InteractionPolicy.defaults() : policy;
    }

    public static McpClientInteraction from(McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        if (exchange == null) {
            return McpClientInteraction.NOOP;
        }
        var token = request == null || request.meta() == null ? null : request.meta().get("progressToken");
        return new SdkMcpClientInteraction(exchange, token, InteractionPolicy.defaults());
    }

    @Override
    public InteractionPolicy policy() {
        return policy;
    }

    @Override
    public ClientInteractionStatus status() {
        var capabilities = exchange.getClientCapabilities();
        var clientInfo = exchange.getClientInfo();
        var elicitation = capabilities != null
                && capabilities.elicitation() != null
                && capabilities.elicitation().form() != null;
        var sampling = capabilities != null && capabilities.sampling() != null;
        return new ClientInteractionStatus(
                clientInfo == null ? "unknown" : clientInfo.name(),
                clientInfo == null ? "unknown" : clientInfo.version(),
                elicitation,
                sampling,
                progressToken != null
        );
    }

    @Override
    public void progress(double progress, Double total, String message) {
        if (!policy.allowProgress() || progressToken == null) {
            return;
        }
        try {
            var builder = McpSchema.ProgressNotification.builder(progressToken, progress)
                    .message(message);
            if (total != null) {
                builder.total(total);
            }
            exchange.progressNotification(builder.build());
        } catch (RuntimeException ignored) {
            // Progress is advisory. The base tool result must remain deterministic.
        }
    }

    @Override
    public Optional<Map<String, Object>> elicitForm(String message, Map<String, Object> requestedSchema) {
        if (!policy.allowElicitation() || !status().elicitation()) {
            return Optional.empty();
        }
        try {
            var builder = McpSchema.ElicitRequest.builder(message, requestedSchema == null ? Map.of() : requestedSchema);
            if (progressToken != null) {
                builder.progressToken(progressToken);
            }
            var result = exchange.createElicitation(builder.build());
            if (result.action() != McpSchema.ElicitResult.Action.ACCEPT || result.content() == null) {
                return Optional.empty();
            }
            return Optional.of(result.content());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> sampleText(String systemPrompt, String userPrompt, int maxTokens) {
        if (!policy.allowSampling() || !status().sampling()) {
            return Optional.empty();
        }
        try {
            var message = McpSchema.SamplingMessage.builder(
                    McpSchema.Role.USER,
                    McpSchema.TextContent.builder(userPrompt == null ? "" : userPrompt).build()
            ).build();
            var builder = McpSchema.CreateMessageRequest.builder(List.of(message), Math.max(maxTokens, 1))
                    .systemPrompt(systemPrompt == null ? "" : systemPrompt)
                    .includeContext(McpSchema.CreateMessageRequest.ContextInclusionStrategy.NONE)
                    .temperature(0.1)
                    .modelPreferences(McpSchema.ModelPreferences.builder()
                            .speedPriority(0.4)
                            .intelligencePriority(0.6)
                            .costPriority(0.2)
                            .build());
            if (progressToken != null) {
                builder.progressToken(progressToken);
            }
            var result = exchange.createMessage(builder.build());
            if (result.content() instanceof McpSchema.TextContent text) {
                return Optional.ofNullable(text.text()).filter(value -> !value.isBlank());
            }
            return Optional.ofNullable(String.valueOf(result.content())).filter(value -> !value.isBlank());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }
}
