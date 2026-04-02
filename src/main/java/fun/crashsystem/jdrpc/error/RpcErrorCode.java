package fun.crashsystem.jdrpc.error;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Discord RPC error codes returned in error responses.
 */
@Getter
@Accessors(fluent = true)
public enum RpcErrorCode {
    UNKNOWN_ERROR(1000, "Unknown error"),
    INVALID_PAYLOAD(4000, "Invalid payload"),
    INVALID_COMMAND(4002, "Invalid command"),
    INVALID_EVENT(4004, "Invalid event"),
    INVALID_CHANNEL(4005, "Invalid channel"),
    INVALID_PERMISSIONS(4006, "Invalid permissions"),
    INVALID_CLIENT_ID(4007, "Invalid client ID"),
    INVALID_ORIGIN(4008, "Invalid origin"),
    INVALID_USER(4010, "Invalid user");

    private final int code;
    private final String description;

    RpcErrorCode(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Resolves an {@link RpcErrorCode} from its integer code.
     * Returns {@link #UNKNOWN_ERROR} if not recognized.
     */
    public static RpcErrorCode fromCode(int code) {
        for (RpcErrorCode e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        return UNKNOWN_ERROR;
    }
}
