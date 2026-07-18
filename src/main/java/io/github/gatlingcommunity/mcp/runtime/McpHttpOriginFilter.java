package io.github.gatlingcommunity.mcp.runtime;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

final class McpHttpOriginFilter implements Filter {
    private final String endpoint;
    private final List<String> allowedOrigins;

    McpHttpOriginFilter(String endpoint, List<String> allowedOrigins) {
        this.endpoint = normalizeEndpoint(endpoint);
        this.allowedOrigins = List.copyOf(allowedOrigins == null ? List.of() : allowedOrigins);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        if (!isMcpEndpoint(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        var origin = httpRequest.getHeader("Origin");
        if (origin != null && !origin.isBlank()
                && !allowedOrigins.isEmpty()
                && !allowedOrigins.contains(origin)) {
            reject(httpResponse, "origin is not allowed");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isMcpEndpoint(HttpServletRequest request) {
        var uri = request.getRequestURI();
        return uri.equals(endpoint) || uri.equals(endpoint + "/");
    }

    private static void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/plain; charset=utf-8");
        response.getWriter().write(message);
    }

    private static String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "/mcp";
        }
        var normalized = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        return normalized.length() > 1 && normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }
}
