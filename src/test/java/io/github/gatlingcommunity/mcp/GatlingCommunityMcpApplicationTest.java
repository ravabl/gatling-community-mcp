package io.github.gatlingcommunity.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GatlingCommunityMcpApplicationTest {
    @Test
    void printsHelpWithoutStartingATransport() throws Exception {
        var output = new ByteArrayOutputStream();
        var original = System.out;
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
        try {
            GatlingCommunityMcpApplication.main(new String[]{"--help"});
        } finally {
            System.setOut(original);
        }

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("gatling-community-mcp", "stdio", "http", "bridge");
    }

    @Test
    void createsFullyPopulatedDefaultRegistry() {
        var registry = GatlingCommunityMcpApplication.createDefaultRegistry();

        assertThat(registry.toolNames()).hasSize(38);
        assertThat(registry.resources().listUris()).hasSizeGreaterThan(20);
        assertThat(registry.prompts().listNames()).hasSize(7);
        assertThat(registry.resourceTemplates()).hasSize(4);
    }
}
