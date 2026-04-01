package fun.crashsystem.jdrpc.error;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Thrown when a Discord IPC command returns an error response.
 */
@Getter
@Accessors(fluent = true)
public class CommandException extends DiscordIPCException {
    private final RpcErrorCode errorCode;

    public CommandException(RpcErrorCode errorCode, String message) {
        super("RPC error " + errorCode.code() + " (" + errorCode.name() + "): " + message);
        this.errorCode = errorCode;
    }
}
