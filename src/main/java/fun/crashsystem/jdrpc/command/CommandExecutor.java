package fun.crashsystem.jdrpc.command;

import com.google.gson.JsonObject;
import fun.crashsystem.jdrpc.connection.Connection;
import fun.crashsystem.jdrpc.error.CommandException;
import fun.crashsystem.jdrpc.error.RpcErrorCode;
import fun.crashsystem.jdrpc.protocol.Frame;
import fun.crashsystem.jdrpc.util.JsonUtils;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static fun.crashsystem.jdrpc.protocol.OpCode.FRAME;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Executes Discord IPC commands and handles nonce-based response matching.
 * Thread-safe via {@link ConcurrentHashMap}.
 */
@Log4j2
public final class CommandExecutor {
    private final ConcurrentHashMap<String, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final AtomicLong nonceCounter = new AtomicLong();
    private final AtomicBoolean transportAvailable = new AtomicBoolean(false);
    private final long commandTimeoutMs;
    private final TokenBucketRateLimiter rateLimiter;

    public CommandExecutor(long commandTimeoutMs, int maxCommandsPerSecond) {
        if (commandTimeoutMs <= 0) {
            throw new IllegalArgumentException("commandTimeoutMs must be > 0");
        }
        if (maxCommandsPerSecond < 0) {
            throw new IllegalArgumentException("maxCommandsPerSecond must be >= 0");
        }
        this.commandTimeoutMs = commandTimeoutMs;
        this.rateLimiter = maxCommandsPerSecond > 0 ? new TokenBucketRateLimiter(maxCommandsPerSecond) : null;
    }

    /**
     * Marks the underlying transport as ready to send commands.
     */
    public void markTransportAvailable() {
        transportAvailable.set(true);
    }

    /**
     * Marks the underlying transport as unavailable.
     */
    public void markTransportUnavailable() {
        transportAvailable.set(false);
    }

    /**
     * Sends a command and waits for the response.
     *
     * @param connection the active IPC connection
     * @param cmd        the command name (e.g., "SET_ACTIVITY")
     * @param args       command arguments, or {@code null}
     * @param evt        event type for SUBSCRIBE/UNSUBSCRIBE, or {@code null}
     * @return the response data
     * @throws CommandException if Discord returns an error
     * @throws IOException      if the connection is broken
     */
    public JsonObject execute(Connection connection, String cmd, JsonObject args, String evt) throws IOException {
        ensureTransportAvailable();

        String nonce = String.valueOf(nonceCounter.incrementAndGet());
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pending.put(nonce, future);

        try {
            ensurePendingTransportAvailability(nonce, future);

            JsonObject payload = new JsonObject();
            payload.addProperty("cmd", cmd);
            if (args != null) {
                payload.add("args", args);
            }
            if (evt != null) {
                payload.addProperty("evt", evt);
            }
            payload.addProperty("nonce", nonce);

            log.debug("Sending command: {} (nonce: {})", cmd, nonce);
            acquireRateLimit();
            ensurePendingTransportAvailability(nonce, future);
            connection.write(new Frame(FRAME, payload));

            return future.get(commandTimeoutMs, MILLISECONDS);
        } catch (CommandException e) {
            throw e;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof CommandException ce) {
                throw ce;
            }
            throw new IOException("Command failed", e.getCause());
        } catch (TimeoutException e) {
            throw new IOException("Command timed out after " + commandTimeoutMs + " ms", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while waiting for command response");
        } catch (Exception e) {
            throw new IOException("Command timeout or error", e);
        } finally {
            pending.remove(nonce, future);
        }
    }

    /**
     * Handles an incoming response frame by matching its nonce.
     *
     * @param json the response JSON
     * @return {@code true} if matched to a pending command
     */
    public boolean handleResponse(JsonObject json) {
        String nonce = JsonUtils.optString(json, "nonce").orElse(null);
        if (nonce == null) {
            return false;
        }
        CompletableFuture<JsonObject> future = pending.remove(nonce);
        if (future == null) {
            return false;
        }

        String evt = JsonUtils.optString(json, "evt").orElse(null);
        JsonObject data = JsonUtils.optObject(json, "data").orElse(null);
        if ("ERROR".equals(evt)) {
            int code = JsonUtils.getInt(data, "code", 1000);
            String message = JsonUtils.getString(data, "message", "Unknown error");
            future.completeExceptionally(new CommandException(RpcErrorCode.fromCode(code), message));
        } else {
            future.complete(data != null ? data : new JsonObject());
        }
        return true;
    }

    /**
     * Cancels all pending commands with the given exception.
     */
    public void cancelAll(Throwable cause) {
        transportAvailable.set(false);
        pending.forEach((nonce, future) -> {
            if (pending.remove(nonce, future)) {
                log.debug("Cancelling pending command: {}", nonce);
                future.completeExceptionally(cause);
            }
        });
    }

    private void ensureTransportAvailable() throws IOException {
        if (!transportAvailable.get()) {
            throw new IOException("Connection is not available");
        }
    }

    private void ensurePendingTransportAvailability(String nonce, CompletableFuture<JsonObject> future) throws IOException {
        if (transportAvailable.get()) {
            return;
        }
        pending.remove(nonce, future);
        throw new IOException("Connection is not available");
    }

    private void acquireRateLimit() throws IOException {
        if (rateLimiter == null) {
            return;
        }
        try {
            rateLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while waiting for command rate limiter");
        }
    }

    private static final class TokenBucketRateLimiter {
        private final int capacity;
        private double tokens;
        private long lastRefillNanos;

        private TokenBucketRateLimiter(int capacity) {
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        private synchronized void acquire() throws InterruptedException {
            while (true) {
                refill();
                if (tokens >= 1.0d) {
                    tokens -= 1.0d;
                    return;
                }

                long waitNanos = (long) Math.ceil(((1.0d - tokens) / capacity) * 1_000_000_000d);
                long waitMillis = Math.max(1L, waitNanos / 1_000_000L);
                int nanosPart = (int) Math.max(0L, waitNanos % 1_000_000L);
                wait(waitMillis, nanosPart);
            }
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000d;
            tokens = Math.min(capacity, tokens + elapsedSeconds * capacity);
            lastRefillNanos = now;
        }
    }
}
