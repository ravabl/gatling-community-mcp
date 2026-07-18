package io.github.gatlingcommunity.mcp.runtime;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import reactor.core.publisher.Mono;

public final class StdioHttpBridge {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration EXPIRED_RESPONSE_TTL = Duration.ofSeconds(60);

    private final GatlingMcpRuntimeConfig config;
    private final BridgeRequestTimeoutPolicy timeoutPolicy;
    private final BridgeTransport transport;
    private final BridgeClock clock;
    private final ExpiredRequestIds expiredRequests;

    public StdioHttpBridge(GatlingMcpRuntimeConfig config) {
        this(config, BridgeRequestTimeoutPolicy.defaults(), new HttpBridgeTransport(config), SystemBridgeClock.INSTANCE);
    }

    StdioHttpBridge(
            GatlingMcpRuntimeConfig config,
            BridgeRequestTimeoutPolicy timeoutPolicy,
            BridgeTransport transport
    ) {
        this(config, timeoutPolicy, transport, SystemBridgeClock.INSTANCE);
    }

    StdioHttpBridge(
            GatlingMcpRuntimeConfig config,
            BridgeRequestTimeoutPolicy timeoutPolicy,
            BridgeTransport transport,
            BridgeClock clock
    ) {
        if (config.mode() != GatlingMcpRuntimeConfig.Mode.BRIDGE) {
            throw new IllegalArgumentException("stdio HTTP bridge requires BRIDGE mode");
        }
        this.config = config;
        this.timeoutPolicy = timeoutPolicy;
        this.transport = transport;
        this.clock = clock;
        this.expiredRequests = new ExpiredRequestIds(EXPIRED_RESPONSE_TTL, clock::nanoTime);
    }

    public void run(InputStream stdin, OutputStream stdout, PrintStream stderr) throws IOException {
        var mapper = McpJsonDefaults.getMapper();
        var output = new PrintWriter(new OutputStreamWriter(stdout, StandardCharsets.UTF_8), true);
        var pendingRequests = ConcurrentHashMap.newKeySet();
        var outputLock = new Object();

        try {
            transport.connect(
                    message -> {
                        forwardInbound(message, mapper, output, outputLock, stderr, pendingRequests, expiredRequests);
                    },
                    failure -> writeStderr(stderr, "MCP bridge transport failure: " + failure.getMessage()),
                    CONNECT_TIMEOUT
            );
        } catch (Exception exception) {
            throw new IOException("Failed to connect MCP bridge", exception);
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
             var reader = new BufferedReader(new InputStreamReader(stdin, StandardCharsets.UTF_8))) {
            var inFlightSends = ConcurrentHashMap.<CompletableFuture<Void>>newKeySet();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                var message = readMessage(mapper, line);
                dispatch(message, inFlightSends, executor, mapper, output, outputLock, stderr, pendingRequests, expiredRequests);
            }
            awaitInFlightSends(inFlightSends);
        } finally {
            try {
                transport.close(CONNECT_TIMEOUT);
            } catch (Exception exception) {
                writeStderr(stderr, "MCP bridge close failure: " + exception.getMessage());
            }
        }
    }

    private void dispatch(
            McpSchema.JSONRPCMessage message,
            Set<CompletableFuture<Void>> inFlightSends,
            java.util.concurrent.Executor executor,
            McpJsonMapper mapper,
            PrintWriter output,
            Object outputLock,
            PrintStream stderr,
            Set<Object> pendingRequests,
            ExpiredRequestIds expiredRequests
    ) {
        var requestId = requestId(message);
        if (requestId.filter(expiredRequests::isQuarantined).isPresent()) {
            var id = requestId.orElseThrow();
            var error = new McpSchema.JSONRPCResponse.JSONRPCError(
                    -32003,
                    "Request id is temporarily quarantined after a bridge timeout"
            );
            writeMessage(mapper, output, outputLock, stderr, McpSchema.JSONRPCResponse.error(id, error));
            writeStderr(stderr, "MCP bridge rejected quarantined request id=" + id);
            return;
        }
        requestId.ifPresent(pendingRequests::add);
        var deadline = Deadline.after(timeoutPolicy.timeoutFor(message), clock);
        CompletionStage<Void> send;
        try {
            // dispatch is called by the single stdin loop, so initiation preserves input order.
            send = transport.send(message, deadline.remaining());
        } catch (Exception exception) {
            handleFailure(requestId, exception, mapper, output, outputLock, stderr, pendingRequests, expiredRequests);
            return;
        }
        var inFlight = send.toCompletableFuture();
        inFlightSends.add(inFlight);
        inFlight.whenComplete((ignored, failure) -> {
            inFlightSends.remove(inFlight);
            if (failure != null) {
                handleFailure(requestId, failure, mapper, output, outputLock, stderr, pendingRequests, expiredRequests);
            }
        });
        requestId.ifPresent(id -> executor.execute(() -> awaitResponse(
                id, deadline, mapper, output, outputLock, stderr, pendingRequests, expiredRequests
        )));
    }

    private static McpSchema.JSONRPCMessage readMessage(McpJsonMapper mapper, String line) throws IOException {
        Map<String, Object> raw = mapper.readValue(line, new TypeRef<>() {
        });
        var method = raw.get("method");
        if (method instanceof String methodName) {
            if (raw.containsKey("id")) {
                return new McpSchema.JSONRPCRequest(methodName, raw.get("id"), raw.get("params"));
            }
            return new McpSchema.JSONRPCNotification(methodName, raw.get("params"));
        }
        if (raw.containsKey("result") || raw.containsKey("error")) {
            var error = raw.get("error") == null
                    ? null
                    : mapper.convertValue(raw.get("error"), McpSchema.JSONRPCResponse.JSONRPCError.class);
            return new McpSchema.JSONRPCResponse("2.0", raw.get("id"), raw.get("result"), error);
        }
        throw new IOException("Unsupported JSON-RPC message");
    }

    private static java.util.Optional<Object> requestId(McpSchema.JSONRPCMessage message) {
        return message instanceof McpSchema.JSONRPCRequest request
                ? java.util.Optional.ofNullable(request.id())
                : java.util.Optional.empty();
    }

    private static void forwardInbound(
            McpSchema.JSONRPCMessage message,
            McpJsonMapper mapper,
            PrintWriter output,
            Object outputLock,
            PrintStream stderr,
            Set<Object> pendingRequests,
            ExpiredRequestIds expiredRequests
    ) {
        if (message instanceof McpSchema.JSONRPCResponse response) {
            if (expiredRequests.consume(response.id())) {
                writeStderr(stderr, "MCP bridge suppressed late response id=" + response.id());
                return;
            }
            writeMessage(mapper, output, outputLock, stderr, message);
            // Publish completion only after stdout contains the terminal response. Otherwise the
            // EOF path can close the transport while this callback is still serializing the result.
            pendingRequests.remove(response.id());
            return;
        }
        writeMessage(mapper, output, outputLock, stderr, message);
    }

    private void awaitResponse(
            Object requestId,
            Deadline deadline,
            McpJsonMapper mapper,
            PrintWriter output,
            Object outputLock,
            PrintStream stderr,
            Set<Object> pendingRequests,
            ExpiredRequestIds expiredRequests
    ) {
        try {
            waitForResponse(pendingRequests, requestId, deadline);
        } catch (Exception exception) {
            handleFailure(java.util.Optional.of(requestId), exception, mapper, output, outputLock, stderr,
                    pendingRequests, expiredRequests);
        }
    }

    private void waitForResponse(Set<Object> pendingRequests, Object requestId, Deadline deadline)
            throws InterruptedException, TimeoutException {
        while (pendingRequests.contains(requestId)) {
            var remaining = deadline.remaining();
            clock.pause(Math.min(10, Math.max(1, remaining.toMillis())));
        }
    }

    private static void handleFailure(
            java.util.Optional<Object> requestId,
            Throwable failure,
            McpJsonMapper mapper,
            PrintWriter output,
            Object outputLock,
            PrintStream stderr,
            Set<Object> pendingRequests,
            ExpiredRequestIds expiredRequests
    ) {
        var activeRequest = requestId.map(pendingRequests::remove).orElse(true);
        if (!activeRequest) {
            return;
        }
        var cause = rootCause(failure);
        var timeout = isTimeout(cause);
        var interrupted = isInterrupted(cause);
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        if (requestId.isPresent()) {
            var id = requestId.orElseThrow();
            if (timeout) {
                expiredRequests.mark(id);
            }
            var error = new McpSchema.JSONRPCResponse.JSONRPCError(
                    timeout ? -32001 : interrupted ? -32002 : -32000,
                    timeout ? "Bridge request timed out" : interrupted
                            ? "Bridge request interrupted" : "Bridge transport failed"
            );
            writeMessage(mapper, output, outputLock, stderr, McpSchema.JSONRPCResponse.error(id, error));
        }
        var description = timeout ? "timed out" : interrupted ? "interrupted" : "failed";
        writeStderr(stderr, "MCP bridge request id=" + requestId.orElse("notification") + " " + description
                + ": " + cause.getMessage());
    }

    private static Throwable rootCause(Throwable failure) {
        var cause = failure;
        while (cause.getCause() != null
                && (cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException)) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static boolean isTimeout(Throwable exception) {
        return exception instanceof TimeoutException
                || exception.getMessage() != null && exception.getMessage().toLowerCase().contains("timeout");
    }

    private static boolean isInterrupted(Throwable exception) {
        return exception instanceof InterruptedException;
    }

    private static void writeMessage(
            McpJsonMapper mapper,
            PrintWriter stdout,
            Object outputLock,
            PrintStream stderr,
            McpSchema.JSONRPCMessage message
    ) {
        try {
            synchronized (outputLock) {
                stdout.println(mapper.writeValueAsString(message));
            }
        } catch (IOException exception) {
            writeStderr(stderr, "Failed to write MCP bridge response: " + exception.getMessage());
        }
    }

    private static void writeStderr(PrintStream stderr, String message) {
        synchronized (stderr) {
            stderr.println(message);
        }
    }

    interface BridgeTransport {
        void connect(Consumer<McpSchema.JSONRPCMessage> inbound, Consumer<Throwable> failure, Duration timeout) throws Exception;

        CompletionStage<Void> send(McpSchema.JSONRPCMessage message, Duration timeout) throws Exception;

        void close(Duration timeout) throws Exception;
    }

    private static final class HttpBridgeTransport implements BridgeTransport {
        private final HttpClientStreamableHttpTransport delegate;
        private final Object initializationLock = new Object();
        private final Deque<PendingMessage> pendingInitializationMessages = new ArrayDeque<>();
        private boolean initializationInFlight;
        private boolean drainingInitializationMessages;

        private HttpBridgeTransport(GatlingMcpRuntimeConfig config) {
            var endpoint = BridgeEndpoint.from(config.bridgeUrl(), config.endpoint());
            this.delegate = HttpClientStreamableHttpTransport.builder(endpoint.baseUri().toString())
                    .endpoint(endpoint.path())
                    .connectTimeout(CONNECT_TIMEOUT)
                    .requestBuilder(HttpRequest.newBuilder())
                    .build();
        }

        @Override
        public void connect(Consumer<McpSchema.JSONRPCMessage> inbound, Consumer<Throwable> failure, Duration timeout) {
            delegate.setExceptionHandler(failure);
            delegate.connect(messages -> messages.doOnNext(inbound).then(Mono.empty())).block(timeout);
        }

        @Override
        public CompletionStage<Void> send(McpSchema.JSONRPCMessage message, Duration timeout) {
            if (isInitialize(message)) {
                synchronized (initializationLock) {
                    initializationInFlight = true;
                }
                var initialization = sendDelegate(message, timeout);
                initialization.whenComplete((ignored, failure) -> completeInitialization(failure));
                return initialization;
            }
            synchronized (initializationLock) {
                if (initializationInFlight || drainingInitializationMessages) {
                    var pending = new PendingMessage(message, timeout);
                    pendingInitializationMessages.addLast(pending);
                    return pending.completion();
                }
            }
            return sendDelegate(message, timeout);
        }

        private CompletionStage<Void> sendDelegate(McpSchema.JSONRPCMessage message, Duration timeout) {
            return delegate.sendMessage(message).timeout(timeout).toFuture();
        }

        private void completeInitialization(Throwable failure) {
            if (failure != null) {
                var pending = new ArrayDeque<PendingMessage>();
                synchronized (initializationLock) {
                    initializationInFlight = false;
                    pending.addAll(pendingInitializationMessages);
                    pendingInitializationMessages.clear();
                }
                pending.forEach(message -> message.completion().completeExceptionally(failure));
                return;
            }
            synchronized (initializationLock) {
                initializationInFlight = false;
                drainingInitializationMessages = true;
            }
            while (true) {
                PendingMessage pending;
                synchronized (initializationLock) {
                    pending = pendingInitializationMessages.pollFirst();
                    if (pending == null) {
                        drainingInitializationMessages = false;
                        return;
                    }
                }
                try {
                    sendDelegate(pending.message(), pending.timeout()).whenComplete((ignored, sendFailure) -> {
                        if (sendFailure == null) {
                            pending.completion().complete(null);
                        } else {
                            pending.completion().completeExceptionally(sendFailure);
                        }
                    });
                } catch (Exception exception) {
                    pending.completion().completeExceptionally(exception);
                }
            }
        }

        private static boolean isInitialize(McpSchema.JSONRPCMessage message) {
            return message instanceof McpSchema.JSONRPCRequest request && "initialize".equals(request.method());
        }

        @Override
        public void close(Duration timeout) {
            delegate.closeGracefully().block(timeout);
        }

        private record PendingMessage(
                McpSchema.JSONRPCMessage message,
                Duration timeout,
                CompletableFuture<Void> completion
        ) {
            private PendingMessage(McpSchema.JSONRPCMessage message, Duration timeout) {
                this(message, timeout, new CompletableFuture<>());
            }
        }
    }

    interface BridgeClock {
        long nanoTime();

        void pause(long millis) throws InterruptedException;
    }

    private enum SystemBridgeClock implements BridgeClock {
        INSTANCE;

        @Override
        public long nanoTime() {
            return System.nanoTime();
        }

        @Override
        public void pause(long millis) throws InterruptedException {
            Thread.sleep(millis);
        }
    }

    private record Deadline(long expiresAtNanos, BridgeClock clock) {
        static Deadline after(Duration timeout, BridgeClock clock) {
            return new Deadline(clock.nanoTime() + timeout.toNanos(), clock);
        }

        Duration remaining() throws TimeoutException {
            var nanos = expiresAtNanos - clock.nanoTime();
            if (nanos <= 0) {
                throw new TimeoutException("bridge request deadline elapsed");
            }
            return Duration.ofNanos(nanos);
        }
    }

    private static void awaitInFlightSends(Set<CompletableFuture<Void>> inFlightSends) {
        while (!inFlightSends.isEmpty()) {
            var snapshot = inFlightSends.toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(snapshot).handle((ignored, failure) -> null).join();
        }
    }

    private static final class ExpiredRequestIds {
        private static final int MAX_TRACKED_IDS = 1_024;
        private final long ttlNanos;
        private final LongSupplier nanoTime;
        private final LinkedHashMap<Object, Long> expiresAtNanos = new LinkedHashMap<>();

        private ExpiredRequestIds(Duration ttl, LongSupplier nanoTime) {
            this.ttlNanos = ttl.toNanos();
            this.nanoTime = nanoTime;
        }

        private synchronized void mark(Object id) {
            prune();
            expiresAtNanos.put(id, nanoTime.getAsLong() + ttlNanos);
            if (expiresAtNanos.size() > MAX_TRACKED_IDS) {
                var iterator = expiresAtNanos.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
        }

        private synchronized boolean consume(Object id) {
            prune();
            return expiresAtNanos.remove(id) != null;
        }

        private synchronized boolean isQuarantined(Object id) {
            prune();
            return expiresAtNanos.containsKey(id);
        }

        private void prune() {
            var now = nanoTime.getAsLong();
            expiresAtNanos.entrySet().removeIf(entry -> entry.getValue() <= now);
        }
    }

    private record BridgeEndpoint(URI baseUri, String path) {
        static BridgeEndpoint from(URI uri, String fallbackPath) {
            if (uri == null || !uri.isAbsolute() || uri.getHost() == null) {
                throw new IllegalArgumentException("bridge URL must be absolute");
            }
            var base = URI.create(uri.getScheme() + "://" + uri.getRawAuthority());
            var path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? fallbackPath : uri.getRawPath();
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            return new BridgeEndpoint(base, path);
        }
    }
}
