package io.github.gatlingcommunity.mcp.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransport;
import java.time.Duration;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class SerializedStdioServerTransportProviderTest {
    @Test
    void delegatesProviderLifecycleAndNotifications() {
        var delegate = new StdioServerTransportProvider(
                McpJsonDefaults.getMapper(),
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream()
        );
        var provider = new SerializedStdioServerTransportProvider(delegate);

        assertThat(provider.protocolVersions()).isEqualTo(delegate.protocolVersions());
        assertThatThrownBy(() -> provider.notifyClients("notifications/test", Map.of("ok", true))
                        .block(Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No session");
        assertThatThrownBy(() -> provider.notifyClient("missing", "notifications/test", Map.of())
                        .block(Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No session");
        provider.closeGracefully().block(Duration.ofSeconds(1));
        provider.close();
    }

    @Test
    void serializesConcurrentOutboundMessages() {
        var delegate = new OverlapDetectingTransport();
        var transport = new SerializedStdioServerTransportProvider.SerializedTransport(delegate);

        var sends = java.util.stream.IntStream.rangeClosed(1, 8)
                .mapToObj(id -> transport.sendMessage(response(id)).subscribeOn(Schedulers.parallel()))
                .toList();

        Mono.when(sends).block(Duration.ofSeconds(5));

        assertThat(delegate.sent()).isEqualTo(8);
        assertThat(delegate.maximumConcurrentSends()).isEqualTo(1);
    }

    @Test
    void releasesSerializationPermitAfterSendFailure() {
        var attempts = new AtomicInteger();
        McpServerTransport delegate = new McpServerTransport() {
            @Override
            public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
                return attempts.incrementAndGet() == 1
                        ? Mono.error(new IllegalStateException("synthetic send failure"))
                        : Mono.empty();
            }

            @Override
            public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Mono<Void> closeGracefully() {
                return Mono.empty();
            }
        };
        var transport = new SerializedStdioServerTransportProvider.SerializedTransport(delegate);

        transport.sendMessage(response(1))
                .onErrorResume(ignored -> Mono.empty())
                .then(transport.sendMessage(response(2)))
                .block(Duration.ofSeconds(1));

        assertThat(attempts).hasValue(2);
    }

    @Test
    void delegatesTransportUtilityAndLifecycleMethods() {
        var closeCalls = new AtomicInteger();
        var delegate = new McpServerTransport() {
            @Override
            public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
                return Mono.empty();
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
                return (T) data;
            }

            @Override
            public Mono<Void> closeGracefully() {
                closeCalls.incrementAndGet();
                return Mono.empty();
            }

            @Override
            public void close() {
                closeCalls.incrementAndGet();
            }

            @Override
            public List<String> protocolVersions() {
                return List.of("2025-06-18");
            }
        };
        var transport = new SerializedStdioServerTransportProvider.SerializedTransport(delegate);

        assertThat(transport.<String>unmarshalFrom("value", new TypeRef<>() {})).isEqualTo("value");
        assertThat(transport.protocolVersions()).containsExactly("2025-06-18");
        transport.closeGracefully().block(Duration.ofSeconds(1));
        transport.close();
        assertThat(closeCalls).hasValue(2);
    }

    private static McpSchema.JSONRPCResponse response(int id) {
        return new McpSchema.JSONRPCResponse("2.0", id, java.util.Map.of("ok", true), null);
    }

    private static final class OverlapDetectingTransport implements McpServerTransport {
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maximumActive = new AtomicInteger();
        private final AtomicInteger sent = new AtomicInteger();

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
            return Mono.defer(() -> {
                var current = active.incrementAndGet();
                maximumActive.accumulateAndGet(current, Math::max);
                sent.incrementAndGet();
                return Mono.delay(Duration.ofMillis(20))
                        .then()
                        .doOnSuccess(ignored -> active.decrementAndGet());
            });
        }

        @Override
        public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<Void> closeGracefully() {
            return Mono.empty();
        }

        private int sent() {
            return sent.get();
        }

        private int maximumConcurrentSends() {
            return maximumActive.get();
        }
    }
}
