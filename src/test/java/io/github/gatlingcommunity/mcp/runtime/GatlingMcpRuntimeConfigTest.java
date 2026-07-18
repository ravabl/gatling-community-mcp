package io.github.gatlingcommunity.mcp.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GatlingMcpRuntimeConfigTest {
    @Test
    void defaultsToStdioTransport() {
        var config = GatlingMcpRuntimeConfig.from(new String[0], Map.of());

        assertThat(config.mode()).isEqualTo(GatlingMcpRuntimeConfig.Mode.STDIO);
        assertThat(config.bindAddress()).isEqualTo("127.0.0.1");
        assertThat(config.port()).isEqualTo(8765);
        assertThat(config.endpoint()).isEqualTo("/mcp");
        assertThat(config.bridgeUrl()).isEqualTo(URI.create("http://127.0.0.1:8765/mcp"));
        assertThat(config.allowedOrigins()).containsExactly(
                "http://127.0.0.1:8765",
                "http://localhost:8765"
        );
    }

    @Test
    void parsesHttpModeFromArguments() {
        var config = GatlingMcpRuntimeConfig.from(new String[] {
                "http",
                "--bind", "0.0.0.0",
                "--port", "9876",
                "--endpoint", "/gatling-mcp",
                "--allowed-origin", "http://127.0.0.1:9876",
                "--allowed-origin", "http://localhost:9876"
        }, Map.of());

        assertThat(config.mode()).isEqualTo(GatlingMcpRuntimeConfig.Mode.HTTP);
        assertThat(config.bindAddress()).isEqualTo("0.0.0.0");
        assertThat(config.port()).isEqualTo(9876);
        assertThat(config.endpoint()).isEqualTo("/gatling-mcp");
        assertThat(config.allowedOrigins()).containsExactly(
                "http://127.0.0.1:9876",
                "http://localhost:9876"
        );
    }

    @Test
    void parsesBridgeModeFromEnvironmentAndArguments() {
        var config = GatlingMcpRuntimeConfig.from(new String[] {
                "bridge",
                "--url", "http://127.0.0.1:9999/mcp"
        }, Map.of(
                "GATLING_MCP_ALLOWED_ORIGINS", "http://127.0.0.1:9999,http://localhost:9999"
        ));

        assertThat(config.mode()).isEqualTo(GatlingMcpRuntimeConfig.Mode.BRIDGE);
        assertThat(config.bridgeUrl()).isEqualTo(URI.create("http://127.0.0.1:9999/mcp"));
        assertThat(config.allowedOrigins()).isEqualTo(List.of(
                "http://127.0.0.1:9999",
                "http://localhost:9999"
        ));
    }

    @Test
    void allowsHttpModeWithoutSecretsForLocalClients() {
        var config = GatlingMcpRuntimeConfig.from(new String[] {"http"}, Map.of());

        assertThat(config.mode()).isEqualTo(GatlingMcpRuntimeConfig.Mode.HTTP);
    }

    @Test
    void validatesEndpointAndPort() {
        assertThatThrownBy(() -> GatlingMcpRuntimeConfig.from(new String[] {
                "http", "--endpoint", "mcp"
        }, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpoint");

        assertThatThrownBy(() -> GatlingMcpRuntimeConfig.from(new String[] {
                "http", "--port", "70000"
        }, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("port");
    }

    @Test
    void detectsHelpMode() {
        var config = GatlingMcpRuntimeConfig.from(new String[] {"--help"}, Map.of());

        assertThat(config.mode()).isEqualTo(GatlingMcpRuntimeConfig.Mode.HELP);
        assertThat(GatlingMcpRuntimeConfig.usage()).contains(
                "stdio",
                "http",
                "bridge",
                "GATLING_MCP_BRIDGE_URL"
        );
    }
}
