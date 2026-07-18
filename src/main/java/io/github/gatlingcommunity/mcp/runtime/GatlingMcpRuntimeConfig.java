package io.github.gatlingcommunity.mcp.runtime;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record GatlingMcpRuntimeConfig(
        Mode mode,
        String bindAddress,
        int port,
        String endpoint,
        List<String> allowedOrigins,
        URI bridgeUrl
) {
    private static final String DEFAULT_BIND_ADDRESS = "127.0.0.1";
    private static final int DEFAULT_PORT = 8765;
    private static final String DEFAULT_ENDPOINT = "/mcp";
    private static final URI DEFAULT_BRIDGE_URL = URI.create("http://127.0.0.1:8765/mcp");

    public enum Mode {
        STDIO,
        HTTP,
        BRIDGE,
        HELP
    }

    public GatlingMcpRuntimeConfig {
        if (mode == null) {
            throw new IllegalArgumentException("mode is required");
        }
        if (bindAddress == null || bindAddress.isBlank()) {
            throw new IllegalArgumentException("bind address is required");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        if (endpoint == null || !endpoint.startsWith("/")) {
            throw new IllegalArgumentException("endpoint must start with /");
        }
        if (endpoint.length() > 1 && endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        allowedOrigins = List.copyOf(allowedOrigins == null ? List.of() : allowedOrigins);
        bridgeUrl = bridgeUrl == null ? DEFAULT_BRIDGE_URL : bridgeUrl;
        if (mode == Mode.BRIDGE && !isHttpUrl(bridgeUrl)) {
            throw new IllegalArgumentException("bridge URL must use http or https");
        }
    }

    public static GatlingMcpRuntimeConfig from(String[] args, Map<String, String> env) {
        var parser = new Parser(args, env);
        return parser.parse();
    }

    public static String usage() {
        return """
                Usage:
                  gatling-community-mcp [stdio]
                  gatling-community-mcp http [--bind 127.0.0.1] [--port 8765] [--endpoint /mcp]
                  gatling-community-mcp bridge --url http://127.0.0.1:8765/mcp

                Environment:
                  GATLING_MCP_TRANSPORT=stdio|http|bridge
                  GATLING_MCP_BIND=127.0.0.1
                  GATLING_MCP_PORT=8765
                  GATLING_MCP_ENDPOINT=/mcp
                  GATLING_MCP_ALLOWED_ORIGINS=http://127.0.0.1:8765,http://localhost:8765
                  GATLING_MCP_BRIDGE_URL=http://127.0.0.1:8765/mcp
                """;
    }

    private static boolean isHttpUrl(URI uri) {
        return uri.isAbsolute()
                && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null
                && !uri.getHost().isBlank();
    }

    private static final class Parser {
        private final String[] args;
        private final Map<String, String> env;
        private Mode mode;
        private String bindAddress;
        private int port;
        private String endpoint;
        private List<String> allowedOrigins;
        private URI bridgeUrl;

        private Parser(String[] args, Map<String, String> env) {
            this.args = args == null ? new String[0] : args;
            this.env = env == null ? Map.of() : env;
            this.mode = parseMode(value("GATLING_MCP_TRANSPORT", "stdio"));
            this.bindAddress = value("GATLING_MCP_BIND", DEFAULT_BIND_ADDRESS);
            this.port = parsePort(value("GATLING_MCP_PORT", Integer.toString(DEFAULT_PORT)));
            this.endpoint = value("GATLING_MCP_ENDPOINT", DEFAULT_ENDPOINT);
            this.bridgeUrl = URI.create(value("GATLING_MCP_BRIDGE_URL", DEFAULT_BRIDGE_URL.toString()));
            this.allowedOrigins = splitCsv(value("GATLING_MCP_ALLOWED_ORIGINS", ""));
        }

        private GatlingMcpRuntimeConfig parse() {
            for (int i = 0; i < args.length; i++) {
                var arg = args[i];
                switch (arg) {
                    case "--help", "-h" -> mode = Mode.HELP;
                    case "stdio", "http", "bridge" -> mode = parseMode(arg);
                    case "--transport" -> mode = parseMode(requireValue(args, ++i, "--transport"));
                    case "--bind" -> bindAddress = requireValue(args, ++i, "--bind");
                    case "--port" -> port = parsePort(requireValue(args, ++i, "--port"));
                    case "--endpoint" -> endpoint = requireValue(args, ++i, "--endpoint");
                    case "--allowed-origin" -> allowedOrigins.add(requireValue(args, ++i, "--allowed-origin"));
                    case "--allowed-origins" -> allowedOrigins.addAll(splitCsv(requireValue(args, ++i, "--allowed-origins")));
                    case "--url", "--bridge-url" -> bridgeUrl = URI.create(requireValue(args, ++i, arg));
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }
            if (allowedOrigins.isEmpty()) {
                allowedOrigins = defaultAllowedOrigins(port);
            }
            return new GatlingMcpRuntimeConfig(mode, bindAddress, port, endpoint, allowedOrigins, bridgeUrl);
        }

        private String value(String name, String defaultValue) {
            var value = env.get(name);
            return value == null || value.isBlank() ? defaultValue : value.trim();
        }
    }

    private static Mode parseMode(String value) {
        try {
            return Mode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("transport must be stdio, http, or bridge", exception);
        }
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("port must be an integer", exception);
        }
    }

    private static List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        var result = new ArrayList<String>();
        for (var item : value.split(",")) {
            var trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static List<String> defaultAllowedOrigins(int port) {
        return new ArrayList<>(List.of(
                "http://127.0.0.1:" + port,
                "http://localhost:" + port
        ));
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length || args[index].startsWith("--")) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return args[index];
    }
}
