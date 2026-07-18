package io.github.gatlingcommunity.mcp.runtime;

import io.github.gatlingcommunity.mcp.compile.CompileCheckService;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.Map;

final class BridgeRequestTimeoutPolicy {
    static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    /** Additional bridge-only time for HTTP transport and stdio JSON serialization after service completion. */
    static final Duration BRIDGE_TRANSPORT_HEADROOM = Duration.ofSeconds(3);

    private final Duration requestTimeout;
    private final Duration compileCheckOverhead;

    BridgeRequestTimeoutPolicy(Duration requestTimeout, Duration compileCheckOverhead) {
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("request timeout must be positive");
        }
        if (compileCheckOverhead == null || compileCheckOverhead.isNegative()) {
            throw new IllegalArgumentException("compile check overhead must not be negative");
        }
        this.requestTimeout = requestTimeout;
        this.compileCheckOverhead = compileCheckOverhead;
    }

    static BridgeRequestTimeoutPolicy defaults() {
        return new BridgeRequestTimeoutPolicy(DEFAULT_REQUEST_TIMEOUT, BRIDGE_TRANSPORT_HEADROOM);
    }

    Duration timeoutFor(McpSchema.JSONRPCMessage message) {
        if (!(message instanceof McpSchema.JSONRPCRequest request)
                || !"tools/call".equals(request.method())
                || !(request.params() instanceof Map<?, ?> params)
                || !"gatling_compile_check".equals(params.get("name"))) {
            return requestTimeout;
        }
        var arguments = params.get("arguments");
        var requested = arguments instanceof Map<?, ?> values && values.get("timeoutSeconds") instanceof Number number
                ? number.intValue()
                : CompileCheckService.DEFAULT_TIMEOUT_SECONDS;
        return CompileCheckService.serviceResponseDeadline(requested).plus(compileCheckOverhead);
    }
}
