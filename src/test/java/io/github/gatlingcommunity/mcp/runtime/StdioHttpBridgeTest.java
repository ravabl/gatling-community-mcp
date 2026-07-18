package io.github.gatlingcommunity.mcp.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gatlingcommunity.mcp.GatlingCommunityMcpApplication;
import io.github.gatlingcommunity.mcp.compile.CompileCheckService;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class StdioHttpBridgeTest {
    @Test
    void preservesResponseForRequestThatExceedsOldTenSecondLimit() throws Exception {
        var clock = new ManualBridgeClock();
        var transport = new ScriptedTransport();
        var observedTimeout = new AtomicReference<Duration>();
        transport.setHandler((message, timeout) -> {
            observedTimeout.set(timeout);
            clock.respondAfter(Duration.ofMillis(10_100), () -> transportResponse(transport, message, Map.of("slow", true)));
        });
        var output = run(bridge(transport, Duration.ofSeconds(12), clock), request("tools/list", 1));

        assertThat(output.stdout()).contains("\"id\":1", "\"slow\":true");
        assertThat(output.stderr()).isEmpty();
        assertThat(observedTimeout.get()).isGreaterThan(Duration.ofSeconds(10));
        assertThat(Duration.ofNanos(clock.nanoTime())).isGreaterThan(Duration.ofSeconds(10));
    }

    @Test
    void writesJsonRpcErrorAndStderrWhenRequestDeadlineExpires() throws Exception {
        var transport = new ScriptedTransport();
        var observedTimeout = new AtomicReference<Duration>();
        transport.setHandler((message, timeout) -> {
            observedTimeout.set(timeout);
            throw new TimeoutException("synthetic deadline");
        });
        var output = run(bridge(transport, Duration.ofMillis(100)), request("tools/list", 1));

        assertThat(output.stdout()).contains("\"id\":1", "\"code\":-32001", "Bridge request timed out");
        assertThat(output.stderr()).contains("request id=1 timed out");
        assertThat(observedTimeout.get()).isLessThanOrEqualTo(Duration.ofMillis(100));
    }

    @Test
    void suppressesLateServerResponseAfterBridgeTimeout() throws Exception {
        var lateResponseDelivered = new CountDownLatch(1);
        var transport = new ScriptedTransport();
        transport.setHandler((message, timeout) -> Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(150);
                transportResponse(transport, message, Map.of("late", true));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                lateResponseDelivered.countDown();
            }
        }));

        var output = run(bridge(transport, Duration.ofMillis(50)), request("tools/list", 1));

        assertThat(lateResponseDelivered.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(output.stdout()).contains("\"id\":1", "Bridge request timed out");
        assertThat(output.stdout()).doesNotContain("\"late\":true");
        assertThat(countOccurrences(output.stdout(), "\"id\":1")).isEqualTo(1);
    }

    @Test
    void permitsIdReuseAfterNormalResponse() throws Exception {
        var transport = new ScriptedTransport();
        transport.setHandler((message, timeout) -> transportResponse(transport, message, Map.of("reused", true)));

        var output = run(bridge(transport, Duration.ofMillis(100)), toolCallRequest(1) + toolCallRequest(1));

        assertThat(countOccurrences(output.stdout(), "\"id\":1")).isEqualTo(2);
        assertThat(countOccurrences(output.stdout(), "\"reused\":true")).isEqualTo(2);
        assertThat(output.stderr()).isEmpty();
    }

    @Test
    void doesNotEmitTransportErrorAfterSuccessfulResponse() throws Exception {
        var transport = new ResponseThenFailureTransport();

        var output = run(bridge(transport, Duration.ofMillis(100)), request("tools/list", 1));

        assertThat(output.stdout()).contains("\"id\":1", "\"ok\":true");
        assertThat(output.stdout()).doesNotContain("Bridge transport failed");
        assertThat(countOccurrences(output.stdout(), "\"id\":1")).isEqualTo(1);
        assertThat(output.stderr()).isEmpty();
    }

    @Test
    void keepsTransportOpenUntilTerminalResponseIsWritten() throws Exception {
        var transport = new AsyncResponseTransport();
        var stdout = new BlockingOutputStream();
        var stderr = new ByteArrayOutputStream();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(() -> {
                bridge(transport, Duration.ofSeconds(1)).run(
                        new ByteArrayInputStream(request("tools/list", 1).getBytes(StandardCharsets.UTF_8)),
                        stdout,
                        new PrintStream(stderr, true, StandardCharsets.UTF_8)
                );
                return null;
            });

            assertThat(stdout.awaitWriteStarted()).isTrue();
            assertThat(transport.awaitClosed(Duration.ofMillis(100))).isFalse();
            stdout.releaseWrite();
            future.get(5, TimeUnit.SECONDS);
        } finally {
            stdout.releaseWrite();
        }

        assertThat(stdout.content()).contains("\"id\":1", "\"ok\":true");
        assertThat(stderr.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void expiresLateResponseSuppressionAfterItsBoundedTtl() throws Exception {
        var clock = new ManualBridgeClock();
        var transport = new ScriptedTransport();
        transport.setHandler((message, timeout) -> {
            // The controlled clock advances the pending request to its timeout.
        });
        var output = run(bridge(transport, Duration.ofMillis(50), clock), request("tools/list", 1));

        clock.pause(Duration.ofSeconds(61).toMillis());
        transport.receive(new McpSchema.JSONRPCResponse("2.0", 1, Map.of("afterTtl", true), null));

        assertThat(output.stdout()).contains("Bridge request timed out", "\"afterTtl\":true");
        assertThat(countOccurrences(output.stdout(), "\"id\":1")).isEqualTo(2);
    }

    @Test
    void rejectsTimedOutIdReuseWhileLateResponseIsQuarantined() throws Exception {
        var clock = new ManualBridgeClock();
        var transport = new ScriptedTransport();
        var sends = new AtomicInteger();
        transport.setHandler((message, timeout) -> sends.incrementAndGet());
        var bridge = bridge(transport, Duration.ofMillis(50), clock);

        var timedOut = run(bridge, request("tools/list", 1));
        var reused = run(bridge, request("tools/list", 1));

        assertThat(timedOut.stdout()).contains("Bridge request timed out");
        assertThat(reused.stdout()).contains("\"code\":-32003", "temporarily quarantined");
        assertThat(reused.stderr()).contains("rejected quarantined request id=1");
        assertThat(sends).hasValue(1);
    }

    @Test
    void forwardsCancellationWhileAnotherRequestIsInFlight() throws Exception {
        var slowRequestStarted = new CountDownLatch(1);
        var cancellationForwarded = new CountDownLatch(1);
        var allowSlowResponse = new CountDownLatch(1);
        var transport = new ScriptedTransport();
        transport.setHandler((message, timeout) -> {
            if (message instanceof McpSchema.JSONRPCRequest request) {
                slowRequestStarted.countDown();
                Thread.startVirtualThread(() -> {
                    try {
                        assertThat(allowSlowResponse.await(2, TimeUnit.SECONDS)).isTrue();
                        transportResponse(transport, request, Map.of("ok", true));
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });
            } else if (message instanceof McpSchema.JSONRPCNotification notification
                    && notification.method().equals("notifications/cancelled")) {
                cancellationForwarded.countDown();
            }
        });
        var input = toolCallRequest(1) + notification("notifications/cancelled") + "\n";
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(() -> {
                bridge(transport, Duration.ofSeconds(2)).run(
                        new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                        stdout,
                        new PrintStream(stderr, true, StandardCharsets.UTF_8)
                );
                return null;
            });
            assertThat(slowRequestStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(cancellationForwarded.await(1, TimeUnit.SECONDS)).isTrue();
            allowSlowResponse.countDown();
            future.get(3, TimeUnit.SECONDS);
        }

        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("\"id\":1", "\"ok\":true");
        assertThat(stderr.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(transport.cancellationParams()).isEqualTo(Map.of("requestId", 1));
    }

    @Test
    void initiatesRequestAndCancellationSendsInStdinOrder() throws Exception {
        var transport = new OrderedTransport();
        var input = toolCallRequest(1) + notification("notifications/cancelled");
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();

        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            var run = executor.submit(() -> {
                bridge(transport, Duration.ofSeconds(2)).run(
                        new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), stdout,
                        new PrintStream(stderr, true, StandardCharsets.UTF_8)
                );
                return null;
            });
            assertThat(transport.awaitFirstSend()).isTrue();
            var cancellationInitiated = transport.awaitSecondSend();
            if (!cancellationInitiated) {
                transport.completeFirstSend();
                transport.respond(1, Map.of("ok", true));
                run.get(3, TimeUnit.SECONDS);
                throw new AssertionError("cancellation send was not initiated before the request send completed");
            }
            assertThat(transport.sentMethods()).containsExactly("tools/call", "notifications/cancelled");
            assertThat(transport.isFirstSendComplete()).isFalse();
            transport.completeFirstSend();
            transport.respond(1, Map.of("ok", true));
            run.get(3, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(transport.sentMethods()).containsExactly("tools/call", "notifications/cancelled");
        assertThat(stderr.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void derivesCompileDeadlineFromRequestedContractAndBoundedOverhead() {
        var policy = new BridgeRequestTimeoutPolicy(Duration.ofSeconds(2), Duration.ofMillis(250));
        var compileRequest = new McpSchema.JSONRPCRequest("tools/call", 1, Map.of(
                "name", "gatling_compile_check",
                "arguments", Map.of("timeoutSeconds", 3)
        ));

        assertThat(policy.timeoutFor(compileRequest))
                .isEqualTo(CompileCheckService.serviceResponseDeadline(3).plusMillis(250));
        assertThat(policy.timeoutFor(new McpSchema.JSONRPCRequest("tools/list", 2, Map.of())))
                .isEqualTo(Duration.ofSeconds(2));
        assertThat(BridgeRequestTimeoutPolicy.defaults().timeoutFor(new McpSchema.JSONRPCRequest("tools/call", 3, Map.of(
                "name", "gatling_compile_check", "arguments", Map.of("timeoutSeconds", CompileCheckService.MAX_TIMEOUT_SECONDS)
        )))).isEqualTo(CompileCheckService.serviceResponseDeadline(CompileCheckService.MAX_TIMEOUT_SECONDS)
                .plus(BridgeRequestTimeoutPolicy.BRIDGE_TRANSPORT_HEADROOM));
        assertThat(BridgeRequestTimeoutPolicy.BRIDGE_TRANSPORT_HEADROOM)
                .isNotEqualTo(CompileCheckService.SERVICE_PREPARATION_RESERVE)
                .isNotEqualTo(CompileCheckService.SERVICE_CLEANUP_GRACE);
    }

    @Test
    void forwardsStdioJsonRpcToHttpDaemon() throws Exception {
        var httpConfig = new GatlingMcpRuntimeConfig(
                GatlingMcpRuntimeConfig.Mode.HTTP,
                "127.0.0.1",
                0,
                "/mcp",
                List.of("http://127.0.0.1"),
                URI.create("http://127.0.0.1:8765/mcp")
        );

        try (var runner = HttpMcpServerRunner.start(httpConfig, GatlingCommunityMcpApplication.createDefaultRegistry())) {
            var bridge = new StdioHttpBridge(new GatlingMcpRuntimeConfig(
                    GatlingMcpRuntimeConfig.Mode.BRIDGE,
                    "127.0.0.1",
                    8765,
                    "/mcp",
                    List.of("http://127.0.0.1"),
                    runner.endpointUri()
            ));
            var output = run(bridge, initializeRequest() + notification("notifications/initialized")
                    + request("tools/list", 2));

            assertThat(output.stdout()).contains("\"id\":1", "\"id\":2", "gatling_generate_simulation");
            assertThat(output.stderr()).doesNotContain("Exception");
        }
    }

    private static StdioHttpBridge bridge(StdioHttpBridge.BridgeTransport transport, Duration normalTimeout) {
        return new StdioHttpBridge(bridgeConfig(), new BridgeRequestTimeoutPolicy(normalTimeout, Duration.ofMillis(250)), transport);
    }

    private static StdioHttpBridge bridge(ScriptedTransport transport, Duration normalTimeout, ManualBridgeClock clock) {
        return new StdioHttpBridge(bridgeConfig(), new BridgeRequestTimeoutPolicy(normalTimeout, Duration.ofMillis(250)), transport, clock);
    }

    private static GatlingMcpRuntimeConfig bridgeConfig() {
        return new GatlingMcpRuntimeConfig(
                GatlingMcpRuntimeConfig.Mode.BRIDGE,
                "127.0.0.1",
                8765,
                "/mcp",
                List.of("http://127.0.0.1"),
                URI.create("http://127.0.0.1:8765/mcp")
        );
    }

    private static BridgeOutput run(StdioHttpBridge bridge, String input) throws Exception {
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(() -> {
                bridge.run(
                        new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                        stdout,
                        new PrintStream(stderr, true, StandardCharsets.UTF_8)
                );
                return null;
            });
            future.get(15, TimeUnit.SECONDS);
        }
        return new BridgeOutput(stdout, stderr);
    }

    private static String request(String method, int id) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\",\"params\":{}}\n";
    }

    private static String initializeRequest() {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{"
                + "\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"bridge-test\",\"version\":\"0.4.0\"}}}\n";
    }

    private static String notification(String method) {
        return "{\"jsonrpc\":\"2.0\",\"method\":\"" + method + "\",\"params\":{\"requestId\":1}}\n";
    }

    private static String toolCallRequest(int id) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id
                + ",\"method\":\"tools/call\",\"params\":{\"name\":\"gatling_resolve_capabilities\",\"arguments\":{}}}\n";
    }

    private static void transportResponse(ScriptedTransport transport,
                                          McpSchema.JSONRPCMessage message,
                                          Object result) {
        var request = (McpSchema.JSONRPCRequest) message;
        transport.receive(new McpSchema.JSONRPCResponse("2.0", request.id(), result, null));
    }

    private static int countOccurrences(String text, String token) {
        return text.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }

    private static final class BridgeOutput {
        private final ByteArrayOutputStream stdout;
        private final ByteArrayOutputStream stderr;

        private BridgeOutput(ByteArrayOutputStream stdout, ByteArrayOutputStream stderr) {
            this.stdout = stdout;
            this.stderr = stderr;
        }

        private String stdout() {
            return stdout.toString(StandardCharsets.UTF_8);
        }

        private String stderr() {
            return stderr.toString(StandardCharsets.UTF_8);
        }
    }

    private static final class ScriptedTransport implements StdioHttpBridge.BridgeTransport {
        private ThrowingSend handler;
        private final AtomicReference<Consumer<McpSchema.JSONRPCMessage>> receiver = new AtomicReference<>();
        private final AtomicReference<Object> cancellationParams = new AtomicReference<>();

        private void setHandler(ThrowingSend handler) {
            this.handler = handler;
        }

        @Override
        public void connect(Consumer<McpSchema.JSONRPCMessage> inbound, Consumer<Throwable> failure, Duration timeout) {
            receiver.set(inbound);
        }

        @Override
        public CompletionStage<Void> send(McpSchema.JSONRPCMessage message, Duration timeout) throws Exception {
            if (message instanceof McpSchema.JSONRPCNotification notification
                    && notification.method().equals("notifications/cancelled")) {
                cancellationParams.set(notification.params());
            }
            handler.send(message, timeout);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close(Duration timeout) {
        }

        private void receive(McpSchema.JSONRPCMessage message) {
            receiver.get().accept(message);
        }

        private Object cancellationParams() {
            return cancellationParams.get();
        }
    }

    @FunctionalInterface
    private interface ThrowingSend {
        void send(McpSchema.JSONRPCMessage message, Duration timeout) throws Exception;
    }

    private static final class ManualBridgeClock implements StdioHttpBridge.BridgeClock {
        private final AtomicLong nanos = new AtomicLong();
        private volatile long responseAtNanos = Long.MAX_VALUE;
        private volatile Runnable response = () -> {
        };

        private void respondAfter(Duration duration, Runnable response) {
            responseAtNanos = nanos.get() + duration.toNanos();
            this.response = response;
        }

        @Override
        public long nanoTime() {
            return nanos.get();
        }

        @Override
        public void pause(long millis) {
            var now = nanos.addAndGet(Duration.ofMillis(millis).toNanos());
            if (now >= responseAtNanos) {
                responseAtNanos = Long.MAX_VALUE;
                response.run();
            }
        }
    }

    private static final class OrderedTransport implements StdioHttpBridge.BridgeTransport {
        private final List<String> sentMethods = new ArrayList<>();
        private final CountDownLatch firstSend = new CountDownLatch(1);
        private final CountDownLatch secondSend = new CountDownLatch(1);
        private final CompletableFuture<Void> firstCompletion = new CompletableFuture<>();
        private Consumer<McpSchema.JSONRPCMessage> receiver;

        @Override
        public void connect(Consumer<McpSchema.JSONRPCMessage> inbound, Consumer<Throwable> failure, Duration timeout) {
            receiver = inbound;
        }

        @Override
        public synchronized CompletionStage<Void> send(McpSchema.JSONRPCMessage message, Duration timeout) {
            sentMethods.add(((message instanceof McpSchema.JSONRPCRequest request) ? request.method()
                    : ((McpSchema.JSONRPCNotification) message).method()));
            if (sentMethods.size() == 1) {
                firstSend.countDown();
                return firstCompletion;
            }
            secondSend.countDown();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close(Duration timeout) {
        }

        private boolean awaitFirstSend() throws InterruptedException {
            return firstSend.await(1, TimeUnit.SECONDS);
        }

        private boolean awaitSecondSend() throws InterruptedException {
            return secondSend.await(1, TimeUnit.SECONDS);
        }

        private void completeFirstSend() {
            firstCompletion.complete(null);
        }

        private boolean isFirstSendComplete() {
            return firstCompletion.isDone();
        }

        private synchronized List<String> sentMethods() {
            return List.copyOf(sentMethods);
        }

        private void respond(int id, Object result) {
            receiver.accept(new McpSchema.JSONRPCResponse("2.0", id, result, null));
        }
    }

    private static final class ResponseThenFailureTransport implements StdioHttpBridge.BridgeTransport {
        private Consumer<McpSchema.JSONRPCMessage> receiver;

        @Override
        public void connect(Consumer<McpSchema.JSONRPCMessage> inbound, Consumer<Throwable> failure, Duration timeout) {
            receiver = inbound;
        }

        @Override
        public CompletionStage<Void> send(McpSchema.JSONRPCMessage message, Duration timeout) {
            var request = (McpSchema.JSONRPCRequest) message;
            receiver.accept(new McpSchema.JSONRPCResponse("2.0", request.id(), Map.of("ok", true), null));
            return CompletableFuture.failedFuture(new IllegalStateException("late synthetic transport failure"));
        }

        @Override
        public void close(Duration timeout) {
        }
    }

    private static final class AsyncResponseTransport implements StdioHttpBridge.BridgeTransport {
        private final CountDownLatch closed = new CountDownLatch(1);
        private Consumer<McpSchema.JSONRPCMessage> receiver;

        @Override
        public void connect(Consumer<McpSchema.JSONRPCMessage> inbound, Consumer<Throwable> failure, Duration timeout) {
            receiver = inbound;
        }

        @Override
        public CompletionStage<Void> send(McpSchema.JSONRPCMessage message, Duration timeout) {
            var request = (McpSchema.JSONRPCRequest) message;
            Thread.startVirtualThread(() -> receiver.accept(new McpSchema.JSONRPCResponse(
                    "2.0", request.id(), Map.of("ok", true), null
            )));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close(Duration timeout) {
            closed.countDown();
        }

        private boolean awaitClosed(Duration timeout) throws InterruptedException {
            return closed.await(timeout.toNanos(), TimeUnit.NANOSECONDS);
        }
    }

    private static final class BlockingOutputStream extends ByteArrayOutputStream {
        private final CountDownLatch writeStarted = new CountDownLatch(1);
        private final CountDownLatch writeReleased = new CountDownLatch(1);

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            writeStarted.countDown();
            try {
                if (!writeReleased.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release response write");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Response write interrupted", exception);
            }
            super.write(bytes, offset, length);
        }

        private boolean awaitWriteStarted() throws InterruptedException {
            return writeStarted.await(5, TimeUnit.SECONDS);
        }

        private void releaseWrite() {
            writeReleased.countDown();
        }

        private String content() {
            return toString(StandardCharsets.UTF_8);
        }
    }
}
