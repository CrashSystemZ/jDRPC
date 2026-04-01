package fun.crashsystem.jdrpc.error;

/**
 * Thrown when a connection-level error occurs with the Discord IPC pipe.
 */
public class ConnectionException extends DiscordIPCException {

    public ConnectionException(String message) {
        super(message);
    }

    public ConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
