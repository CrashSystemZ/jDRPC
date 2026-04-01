package fun.crashsystem.jdrpc.protocol;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Discord IPC wire protocol operation codes.
 * <p>
 * Each IPC message is framed as {@code [OpCode:4B LE][Length:4B LE][JSON payload UTF-8]}.
 */
@Getter
@Accessors(fluent = true)
public enum OpCode {
    /** Initial handshake message sent after connecting to the IPC pipe. */
    HANDSHAKE(0),
    /** Standard data frame containing JSON command/response payloads. */
    FRAME(1),
    /** Connection close notification with optional close reason. */
    CLOSE(2),
    /** Keep-alive ping request. Server should respond with {@link #PONG}. */
    PING(3),
    /** Keep-alive pong response to a {@link #PING} request. */
    PONG(4);

    private final int code;

    OpCode(int code) {
        this.code = code;
    }

    /**
     * Resolves an {@link OpCode} from its integer code.
     *
     * @param code the integer code
     * @return the matching OpCode, or {@code null} if unknown
     */
    public static OpCode fromCode(int code) {
        for (OpCode op : values()) {
            if (op.code == code) {
                return op;
            }
        }
        return null;
    }
}
