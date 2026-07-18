package io.github.gatlingcommunity.mcp.mcp;

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Mono;

/** Serializes SDK stdio outbound emissions to avoid concurrent unicast-sink failures. */
final class SerializedStdioServerTransportProvider implements McpServerTransportProvider {
    private final StdioServerTransportProvider delegate;

    SerializedStdioServerTransportProvider(StdioServerTransportProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public void setSessionFactory(McpServerSession.Factory sessionFactory) {
        delegate.setSessionFactory(transport -> sessionFactory.create(new SerializedTransport(transport)));
    }

    @Override
    public Mono<Void> notifyClients(String method, Object params) {
        return delegate.notifyClients(method, params);
    }

    @Override
    public Mono<Void> notifyClient(String sessionId, String method, Object params) {
        return delegate.notifyClient(sessionId, method, params);
    }

    @Override
    public Mono<Void> closeGracefully() {
        return delegate.closeGracefully();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public List<String> protocolVersions() {
        return delegate.protocolVersions();
    }

    static final class SerializedTransport implements McpServerTransport {
        private final McpServerTransport delegate;
        private final AtomicReference<Mono<Void>> outboundTail = new AtomicReference<>(Mono.empty());

        SerializedTransport(McpServerTransport delegate) {
            this.delegate = delegate;
        }

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
            while (true) {
                var previous = outboundTail.get();
                var current = previous
                        .onErrorResume(ignored -> Mono.empty())
                        .then(Mono.defer(() -> delegate.sendMessage(message)))
                        .cache();
                if (outboundTail.compareAndSet(previous, current)) {
                    return current.doFinally(ignored -> outboundTail.compareAndSet(current, Mono.empty()));
                }
            }
        }

        @Override
        public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
            return delegate.unmarshalFrom(data, typeRef);
        }

        @Override
        public Mono<Void> closeGracefully() {
            return delegate.closeGracefully();
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public List<String> protocolVersions() {
            return delegate.protocolVersions();
        }
    }
}
