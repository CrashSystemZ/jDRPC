package fun.crashsystem.jdrpc.error;

/**
 * Thrown when no running Discord client can be found on any IPC pipe (indices 0-9).
 */
public class NoDiscordClientException extends ConnectionException {

    public NoDiscordClientException() {
        super("No Discord client found on any IPC pipe (0-9). Is Discord running?");
    }
}
