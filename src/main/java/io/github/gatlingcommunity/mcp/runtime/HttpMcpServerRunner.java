package io.github.gatlingcommunity.mcp.runtime;

import io.github.gatlingcommunity.mcp.mcp.GatlingMcpServerFactory;
import io.github.gatlingcommunity.mcp.mcp.McpToolRegistry;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityValidator;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.EnumSet;
import org.eclipse.jetty.ee11.servlet.FilterHolder;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

public final class HttpMcpServerRunner implements AutoCloseable {
    private final GatlingMcpRuntimeConfig config;
    private final Server server;
    private final ServerConnector connector;
    private final McpSyncServer mcpServer;
    private final HttpServletStreamableServerTransportProvider transport;

    private HttpMcpServerRunner(
            GatlingMcpRuntimeConfig config,
            Server server,
            ServerConnector connector,
            McpSyncServer mcpServer,
            HttpServletStreamableServerTransportProvider transport
    ) {
        this.config = config;
        this.server = server;
        this.connector = connector;
        this.mcpServer = mcpServer;
        this.transport = transport;
    }

    public static HttpMcpServerRunner start(GatlingMcpRuntimeConfig config, McpToolRegistry registry) {
        if (config.mode() != GatlingMcpRuntimeConfig.Mode.HTTP) {
            throw new IllegalArgumentException("HTTP runner requires HTTP mode");
        }

        var transport = HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(McpJsonDefaults.getMapper())
                .mcpEndpoint(config.endpoint())
                .securityValidator(ServerTransportSecurityValidator.NOOP)
                .build();
        var mcpServer = GatlingMcpServerFactory.createStreamable(transport, registry);
        var server = new Server();
        var connector = new ServerConnector(server);
        connector.setHost(config.bindAddress());
        connector.setPort(config.port());
        server.addConnector(connector);

        var context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");
        context.addFilter(
                new FilterHolder(new McpHttpOriginFilter(config.endpoint(), config.allowedOrigins())),
                "/*",
                EnumSet.of(DispatcherType.REQUEST)
        );
        context.addServlet(new ServletHolder("mcp", transport), config.endpoint());
        context.addServlet(new ServletHolder("healthz", new HealthServlet()), "/healthz");
        server.setHandler(context);

        try {
            server.start();
            return new HttpMcpServerRunner(config, server, connector, mcpServer, transport);
        } catch (Exception exception) {
            mcpServer.closeGracefully();
            transport.closeGracefully().block();
            throw new IllegalStateException("Failed to start MCP HTTP server", exception);
        }
    }

    public URI baseUri() {
        return URI.create("http://" + clientHost() + ":" + port());
    }

    public URI endpointUri() {
        return baseUri().resolve(config.endpoint());
    }

    public URI healthUri() {
        return baseUri().resolve("/healthz");
    }

    public int port() {
        return connector.getLocalPort();
    }

    private String clientHost() {
        return switch (config.bindAddress()) {
            case "0.0.0.0", "::" -> "127.0.0.1";
            default -> config.bindAddress();
        };
    }

    @Override
    public void close() {
        try {
            mcpServer.closeGracefully();
        } finally {
            transport.closeGracefully().block();
            try {
                server.stop();
                server.destroy();
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to stop MCP HTTP server", exception);
            }
        }
    }

    private static final class HealthServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/plain; charset=utf-8");
            response.getWriter().write("ok");
        }
    }
}
