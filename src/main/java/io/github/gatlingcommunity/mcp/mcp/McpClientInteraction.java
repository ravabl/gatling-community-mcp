package io.github.gatlingcommunity.mcp.mcp;

import java.util.Map;
import java.util.Optional;

public interface McpClientInteraction {
    McpClientInteraction NOOP = new McpClientInteraction() {
        private final ClientInteractionStatus status =
                new ClientInteractionStatus("unknown", "unknown", false, false, false);

        @Override
        public ClientInteractionStatus status() {
            return status;
        }

        @Override
        public void progress(double progress, Double total, String message) {
        }

        @Override
        public Optional<Map<String, Object>> elicitForm(String message, Map<String, Object> requestedSchema) {
            return Optional.empty();
        }

        @Override
        public Optional<String> sampleText(String systemPrompt, String userPrompt, int maxTokens) {
            return Optional.empty();
        }
    };

    ClientInteractionStatus status();

    default InteractionPolicy policy() {
        return InteractionPolicy.defaults();
    }

    void progress(double progress, Double total, String message);

    Optional<Map<String, Object>> elicitForm(String message, Map<String, Object> requestedSchema);

    Optional<String> sampleText(String systemPrompt, String userPrompt, int maxTokens);
}
