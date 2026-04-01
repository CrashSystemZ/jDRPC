package fun.crashsystem.jdrpc.protocol;

import com.google.gson.JsonObject;
import fun.crashsystem.jdrpc.protocol.FrameSupport.FrameFormatException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Represents a single Discord IPC wire frame.
 * <p>
 * Wire format: {@code [OpCode: 4 bytes LE][Length: 4 bytes LE][JSON payload: UTF-8 bytes]}
 *
 * @param op   the operation code identifying the frame type
 * @param data the JSON payload of the frame, or {@code null} for empty frames
 */
public record Frame(OpCode op, JsonObject data) {

    /**
     * Encodes this frame into the Discord IPC wire format.
     *
     * @return a byte array containing {@code [op:4B LE][length:4B LE][json UTF-8]}
     */
    public byte[] encode() {
        byte[] jsonBytes = data != null ? data.toString().getBytes(StandardCharsets.UTF_8) : new byte[0];
        try {
            FrameSupport.validatePayloadLength(jsonBytes.length);
        } catch (FrameFormatException e) {
            throw new IllegalArgumentException("Invalid frame payload length: " + e.getMessage(), e);
        }
        ByteBuffer buffer = ByteBuffer.allocate(8 + jsonBytes.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(op.code());
        buffer.putInt(jsonBytes.length);
        buffer.put(jsonBytes);
        return buffer.array();
    }

    /**
     * Decodes a frame from raw bytes containing the full wire message.
     *
     * @param bytes the raw bytes including the 8-byte header and JSON payload
     * @return the decoded frame
     * @throws IllegalArgumentException if the bytes are too short or contain an unknown opcode
     */
    public static Frame decode(byte[] bytes) {
        if (bytes.length < 8) {
            throw new IllegalArgumentException("Frame must be at least 8 bytes, got " + bytes.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int opCode = buffer.getInt();
        int length = buffer.getInt();
        OpCode op = OpCode.fromCode(opCode);
        if (op == null) {
            throw new IllegalArgumentException("Unknown opcode: " + opCode);
        }
        JsonObject data = null;
        try {
            FrameSupport.validatePayloadLength(length);
            if (length > 0) {
                FrameSupport.validateAvailablePayload(buffer.remaining(), length);
                byte[] jsonBytes = new byte[length];
                buffer.get(jsonBytes);
                data = FrameSupport.parseJsonObject(jsonBytes);
            }
        } catch (FrameFormatException e) {
            throw new IllegalArgumentException("Invalid frame: " + e.getMessage(), e);
        }
        return new Frame(op, data);
    }
}
