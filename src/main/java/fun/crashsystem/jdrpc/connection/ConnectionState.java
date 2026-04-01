package fun.crashsystem.jdrpc.connection;

import fun.crashsystem.jdrpc.entity.DiscordBuild;
import fun.crashsystem.jdrpc.entity.User;

/**
 * Represents the current state of the Discord IPC connection.
 */
public sealed interface ConnectionState {
    /** No connection established. Initial state. */
    record Disconnected() implements ConnectionState {}
    /** Attempting to connect and handshake. */
    record Connecting() implements ConnectionState {}
    /** Successfully connected. */
    record Connected(User user, DiscordBuild build) implements ConnectionState {}
    /** Connection lost, attempting reconnect. */
    record Reconnecting(int attempt, FailureInfo failure) implements ConnectionState {}
    /** Reconnection failed after max retries. */
    record Failed(FailureInfo failure) implements ConnectionState {}
    /** Intentionally closed. Terminal state. */
    record Closed() implements ConnectionState {}
}
