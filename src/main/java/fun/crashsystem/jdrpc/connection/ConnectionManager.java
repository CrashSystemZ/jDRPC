package fun.crashsystem.jdrpc.connection;

import com.google.gson.JsonObject;
import fun.crashsystem.jdrpc.DiscordIPCConfig;
import fun.crashsystem.jdrpc.command.CommandExecutor;
import fun.crashsystem.jdrpc.entity.DiscordBuild;
import fun.crashsystem.jdrpc.entity.User;
import fun.crashsystem.jdrpc.error.ConnectionException;
import fun.crashsystem.jdrpc.error.NoDiscordClientException;
import fun.crashsystem.jdrpc.event.EventDispatcher;
import fun.crashsystem.jdrpc.protocol.Frame;
import fun.crashsystem.jdrpc.protocol.OpCode;
import fun.crashsystem.jdrpc.util.JsonUtils;
import fun.crashsystem.jdrpc.util.Platform;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Manages the Discord IPC connection lifecycle including pipe discovery, handshake,
 * background read loop, and auto-reconnect with exponential backoff.
 * <p>
 * Discord sends PING frames; the client responds with PONG (handled in the read loop).
 */
@Slf4j
@Getter
@Accessors(fluent = true)
public final class ConnectionManager {
    private final AtomicReference<ConnectionState> stateRef = new AtomicReference<>(new ConnectionState.Disconnected());
    private final AtomicLong reconnectGeneration = new AtomicLong(-1);
    private final AtomicLong generation = new AtomicLong();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "jDRPC-worker");
        t.setDaemon(true);
        return t;
    });
    private final DiscordIPCConfig config;
    private final CommandExecutor commandExecutor;
    private final EventDispatcher eventDispatcher;
    private final PipePathProvider pipePathProvider;
    private final ConnectionFactory connectionFactory;
    private volatile Connection connection;
    private volatile User currentUser;
    private volatile DiscordBuild currentBuild;
    private volatile Future<?> readFuture;
    private Consumer<ConnectionState> stateListener;

    public ConnectionManager(DiscordIPCConfig config, CommandExecutor commandExecutor, EventDispatcher eventDispatcher) {
        this(
                config,
                commandExecutor,
                eventDispatcher,
                PipeLocator::locateAll,
                path -> switch (Platform.CURRENT) {
                    case WINDOWS -> new WindowsConnection(path);
                    case MACOS, LINUX -> new UnixConnection(path);
                }
        );
    }

    ConnectionManager(DiscordIPCConfig config,
                      CommandExecutor commandExecutor,
                      EventDispatcher eventDispatcher,
                      PipePathProvider pipePathProvider,
                      ConnectionFactory connectionFactory) {
        this.config = config;
        this.commandExecutor = commandExecutor;
        this.eventDispatcher = eventDispatcher;
        this.pipePathProvider = pipePathProvider;
        this.connectionFactory = connectionFactory;
    }

    /**
     * Returns the current connection state.
     */
    public ConnectionState state() {
        return stateRef.get();
    }

    /**
     * Sets a listener called on every state change.
     */
    public void setStateListener(Consumer<ConnectionState> listener) {
        this.stateListener = listener;
    }

    private void setState(ConnectionState newState) {
        stateRef.set(newState);
        Optional.ofNullable(this.stateListener).ifPresent(listener -> {
            try {
                listener.accept(newState);
            } catch (Exception e) {
                log.warn("State listener failed for {}", newState, e);
            }
        });
    }

    /**
     * Connects to Discord IPC. Tries all pipe indices (0-9).
     *
     * @throws NoDiscordClientException if no Discord client is found
     * @throws ConnectionException      if the handshake fails
     */
    public void connect() {
        ConnectionState currentState = stateRef.get();
        if (currentState instanceof ConnectionState.Connected
                || currentState instanceof ConnectionState.Connecting
                || currentState instanceof ConnectionState.Reconnecting) {
            log.debug("Ignoring connect() in state {}", currentState);
            return;
        }

        long generationToken = generation.incrementAndGet();
        reconnectGeneration.set(-1);
        cancelBackgroundTasks();
        commandExecutor.markTransportUnavailable();
        setState(new ConnectionState.Connecting());

        try {
            HandshakeResult result = openAndHandshake();
            activateConnection(generationToken, result, false);
        } catch (Exception e) {
            if (!isGenerationActive(generationToken)) {
                log.debug("Discarding stale connect failure for generation {}", generationToken, e);
                return;
            }

            clearConnectionState();
            commandExecutor.markTransportUnavailable();
            setState(new ConnectionState.Failed(FailureInfo.from(e)));
            if (e instanceof NoDiscordClientException) {
                throw (NoDiscordClientException) e;
            }
            throw new ConnectionException("Failed to connect", e);
        }
    }

    /**
     * Disconnects gracefully.
     */
    public void disconnect() {
        long generationToken = generation.incrementAndGet();
        reconnectGeneration.set(-1);
        cancelBackgroundTasks();
        commandExecutor.markTransportUnavailable();

        Connection conn = this.connection;
        clearConnectionState();
        if (conn != null) {
            try {
                JsonObject closeData = new JsonObject();
                closeData.addProperty("code", 1000);
                closeData.addProperty("message", "Client disconnecting");
                conn.write(new Frame(OpCode.CLOSE, closeData));
            } catch (Exception e) {
                log.debug("Failed to send CLOSE frame during disconnect for generation {}", generationToken, e);
            }
            closeQuietly(conn, "disconnect");
        }

        commandExecutor.cancelAll(new ConnectionException("Disconnected"));
        setState(new ConnectionState.Closed());
        eventDispatcher.dispatchClose();
        log.info("Disconnected from Discord");
    }

    /**
     * Shuts down all executor services. Call on application exit.
     */
    public void shutdown() {
        disconnect();
        executor.shutdownNow();
    }

    private void startReadLoop(long generationToken, Connection conn) {
        readFuture = executor.submit(() -> {
            log.debug("Read loop started for generation {}", generationToken);
            try {
                while (isGenerationActive(generationToken) && conn.isOpen() && !Thread.currentThread().isInterrupted()) {
                    Frame frame = conn.read();
                    if (!isGenerationActive(generationToken)) {
                        return;
                    }

                    if (log.isDebugEnabled()) {
                        String preview = frame.data() != null ? frame.data().toString() : "null";
                        if (preview.length() > 200) {
                            preview = preview.substring(0, 200) + "...";
                        }
                        log.debug("Received frame: op={}, data={}", frame.op(), preview);
                    }

                    switch (frame.op()) {
                        case CLOSE -> {
                            int closeCode = JsonUtils.getInt(frame.data(), "code", 0);
                            String closeMsg = JsonUtils.getString(frame.data(), "message", "Discord closed connection");
                            log.info("Received CLOSE frame from Discord: code={}, message={}", closeCode, closeMsg);
                            handleDisconnect(closeCode, closeMsg, new ConnectionException(closeMsg), generationToken);
                            return;
                        }
                        case PING -> conn.write(new Frame(OpCode.PONG, frame.data()));
                        case PONG -> log.debug("Received PONG");
                        case FRAME -> handleIncomingFrame(frame.data());
                        default -> log.warn("Unexpected opcode in read loop: {}", frame.op());
                    }
                }
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted() && isGenerationActive(generationToken)) {
                    log.warn("Read loop error: {}", e.getMessage(), e);
                    handleDisconnect(0, e.getMessage(), e, generationToken);
                }
            }
            log.debug("Read loop ended for generation {}", generationToken);
        });
    }


    private void handleIncomingFrame(JsonObject json) {
        if (json == null) {
            return;
        }

        String evt = JsonUtils.optString(json, "evt").orElse(null);
        if ("ERROR".equals(evt) && JsonUtils.optString(json, "nonce").isPresent()) {
            JsonObject data = JsonUtils.optObject(json, "data").orElse(null);
            int code = JsonUtils.getInt(data, "code", 1000);
            String message = JsonUtils.getString(data, "message", "Unknown error");
            eventDispatcher.dispatchError(code, message);
        }

        boolean handled = commandExecutor.handleResponse(json);
        log.debug("Frame handled by command executor: {}", handled);
        if (handled) {
            return;
        }

        String cmd = JsonUtils.getString(json, "cmd", "");
        if (!"DISPATCH".equals(cmd)) {
            return;
        }

        JsonUtils.optString(json, "evt").ifPresent(eventName -> {
            JsonObject eventData = JsonUtils.optObject(json, "data").orElse(null);
            eventDispatcher.dispatch(eventName, eventData);
        });
    }

    private void handleDisconnect(int errorCode, String errorMessage, Throwable cause, long generationToken) {
        if (!generation.compareAndSet(generationToken, generationToken + 1)) {
            log.debug("Ignoring stale disconnect for generation {}", generationToken);
            return;
        }

        long reconnectToken = generationToken + 1;
        cancelBackgroundTasks();
        Connection conn = connection;
        clearConnectionState();
        closeQuietly(conn, "disconnect");
        commandExecutor.markTransportUnavailable();
        commandExecutor.cancelAll(new ConnectionException("Disconnected", cause));
        eventDispatcher.dispatchDisconnect(errorCode, errorMessage != null ? errorMessage : (cause != null ? cause.getMessage() : "Unknown"));

        if (!config.reconnect()) {
            reconnectGeneration.set(-1);
            setState(new ConnectionState.Failed(FailureInfo.from(cause)));
            return;
        }

        scheduleReconnect(cause, reconnectToken);
    }

    private void reconnect(Throwable initialCause, long generationToken) {
        int attempt = 1;
        int maxAttempts = config.maxReconnectAttempts();
        long delay = config.reconnectBaseDelayMs();
        long maxDelay = config.reconnectMaxDelayMs();
        Throwable lastFailure = initialCause;

        while (!Thread.currentThread().isInterrupted()
                && isGenerationActive(generationToken)
                && (maxAttempts == 0 || attempt <= maxAttempts)) {
            setState(new ConnectionState.Reconnecting(attempt, FailureInfo.from(lastFailure)));
            log.info("Reconnecting (attempt {})...", attempt);

            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                reconnectGeneration.compareAndSet(generationToken, -1);
                return;
            }

            if (!isGenerationActive(generationToken)) {
                reconnectGeneration.compareAndSet(generationToken, -1);
                return;
            }

            try {
                HandshakeResult result = openAndHandshake();
                if (activateConnection(generationToken, result, true)) {
                    return;
                }
            } catch (Exception e) {
                lastFailure = e;
                log.debug("Reconnect attempt {} failed: {}", attempt, e.getMessage(), e);
            }

            attempt++;
            delay = Math.min(delay * 2, maxDelay);
        }

        reconnectGeneration.compareAndSet(generationToken, -1);
        if (isGenerationActive(generationToken)) {
            setState(new ConnectionState.Failed(FailureInfo.from(lastFailure)));
            log.error("Failed to reconnect after {} attempts", attempt - 1);
        }
    }

    HandshakeResult openAndHandshake() {
        List<String> paths = pipePathProvider.locateAll();
        boolean acceptAnyPreferred = config.preferredBuilds().contains(DiscordBuild.ANY);

        for (String path : paths) {
            try {
                HandshakeResult result = tryOpenAndHandshake(path);
                if (acceptAnyPreferred || config.preferredBuilds().contains(result.build)) {
                    return result;
                }
                closeQuietly(result.connection, "skipping non-preferred build from " + path);
            } catch (Exception e) {
                log.debug("Pipe {} unavailable: {}", path, e.getMessage(), e);
            }
        }

        for (String path : paths) {
            try {
                return tryOpenAndHandshake(path);
            } catch (Exception e) {
                log.debug("Pipe {} failed during second pass: {}", path, e.getMessage(), e);
            }
        }

        throw new NoDiscordClientException();
    }

    HandshakeResult tryOpenAndHandshake(String path) throws IOException {
        Connection conn = null;
        boolean keepOpen = false;
        try {
            conn = connectionFactory.create(path);
            HandshakeResult result = handshake(conn);
            keepOpen = true;
            return result;
        } finally {
            if (!keepOpen) {
                closeQuietly(conn, "failed handshake on " + path);
            }
        }
    }

    HandshakeResult handshake(Connection conn) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("v", 1);
        payload.addProperty("client_id", String.valueOf(config.clientId()));
        conn.write(new Frame(OpCode.HANDSHAKE, payload));

        Frame response = conn.read();
        if (response.op() == OpCode.CLOSE) {
            throw new ConnectionException("Discord rejected handshake");
        }
        if (response.op() != OpCode.FRAME) {
            throw new ConnectionException("Unexpected opcode in handshake response: " + response.op());
        }

        JsonObject data = response.data();
        if (data == null || data.entrySet().isEmpty()) {
            throw new ConnectionException("Empty handshake response");
        }

        JsonUtils.optString(data, "cmd")
                .filter(cmd -> !"DISPATCH".equals(cmd))
                .ifPresent(cmd -> {
                    throw new ConnectionException("Unexpected handshake command: " + cmd);
                });

        JsonUtils.optString(data, "evt")
                .filter(evt -> !"READY".equals(evt))
                .ifPresent(evt -> {
                    throw new ConnectionException("Unexpected handshake event: " + evt);
                });

        JsonObject responseData = JsonUtils.optObject(data, "data")
                .filter(d -> !d.entrySet().isEmpty())
                .orElseThrow(() -> new ConnectionException("Malformed handshake response: missing data object"));

        JsonObject userJson = JsonUtils.optObject(responseData, "user")
                .filter(u -> !u.entrySet().isEmpty())
                .orElseThrow(() -> new ConnectionException("No user in handshake response"));

        User user = validateHandshakeUser(userJson);

        String endpoint = JsonUtils.optObject(responseData, "config")
                .flatMap(cfg -> JsonUtils.optString(cfg, "api_endpoint"))
                .orElse(null);
        DiscordBuild build = DiscordBuild.fromEndpoint(endpoint);

        return new HandshakeResult(conn, user, build);
    }

    private boolean activateConnection(long generationToken, HandshakeResult result, boolean reconnecting) {
        if (!isGenerationActive(generationToken)) {
            closeQuietly(result.connection, "stale activation for generation " + generationToken);
            return false;
        }

        this.connection = result.connection;
        this.currentUser = result.user;
        this.currentBuild = result.build;
        commandExecutor.markTransportAvailable();
        reconnectGeneration.compareAndSet(generationToken, -1);
        setState(new ConnectionState.Connected(result.user, result.build));
        eventDispatcher.dispatchReady(result.user);
        startReadLoop(generationToken, result.connection);
        return true;
    }

    private void scheduleReconnect(Throwable cause, long generationToken) {
        if (!reconnectGeneration.compareAndSet(-1, generationToken)) {
            log.debug("Reconnect already scheduled for generation {}", reconnectGeneration.get());
            return;
        }

        try {
            executor.submit(() -> reconnect(cause, generationToken));
        } catch (RejectedExecutionException e) {
            reconnectGeneration.compareAndSet(generationToken, -1);
            if (isGenerationActive(generationToken)) {
                setState(new ConnectionState.Failed(FailureInfo.from(cause)));
            }
            log.warn("Failed to schedule reconnect", e);
        }
    }

    private void cancelBackgroundTasks() {
        Future<?> currentReadFuture = this.readFuture;
        if (currentReadFuture != null) {
            currentReadFuture.cancel(true);
            this.readFuture = null;
        }
    }

    private void clearConnectionState() {
        this.connection = null;
        this.currentUser = null;
        this.currentBuild = null;
    }

    private boolean isGenerationActive(long generationToken) {
        return generation.get() == generationToken;
    }

    private User validateHandshakeUser(JsonObject userJson) {
        try {
            User user = User.fromJson(userJson);
            if (user.id() == null || user.id().isBlank()) {
                throw new ConnectionException("Handshake user is missing id");
            }
            if (user.username() == null || user.username().isBlank()) {
                throw new ConnectionException("Handshake user is missing username");
            }
            user.idLong();
            return user;
        } catch (ConnectionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ConnectionException("Invalid user in handshake response", e);
        }
    }

    private void closeQuietly(Connection conn, String context) {
        if (conn == null) {
            return;
        }

        try {
            conn.close();
        } catch (Exception e) {
            log.warn("Failed to close connection ({})", context, e);
        }
    }

    record HandshakeResult(Connection connection, User user, DiscordBuild build) {
    }
}
