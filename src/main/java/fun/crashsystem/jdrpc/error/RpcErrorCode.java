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
    INVALID_GUILD(4003, "Invalid guild"),
    INVALID_EVENT(4004, "Invalid event"),
    INVALID_CHANNEL(4005, "Invalid channel"),
    INVALID_PERMISSIONS(4006, "Invalid permissions"),
    INVALID_CLIENT_ID(4007, "Invalid client ID"),
    INVALID_ORIGIN(4008, "Invalid origin"),
    INVALID_TOKEN(4009, "Invalid token"),
    INVALID_USER(4010, "Invalid user"),
    OAUTH2_ERROR(5000, "OAuth2 error"),
    SELECT_CHANNEL_TIMED_OUT(5001, "Select channel timed out"),
    GET_GUILD_TIMED_OUT(5002, "Get guild timed out"),
    SELECT_VOICE_FORCE_REQUIRED(5003, "Select voice force required"),
    CAPTURE_SHORTCUT_ALREADY_LISTENING(5004, "Capture shortcut already listening");

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
