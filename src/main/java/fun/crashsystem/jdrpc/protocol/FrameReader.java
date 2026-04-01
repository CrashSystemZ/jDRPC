package fun.crashsystem.jdrpc.protocol;

import com.google.gson.JsonObject;
import fun.crashsystem.jdrpc.protocol.FrameSupport.FrameFormatException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Reads Discord IPC frames from an {@link InputStream}.
 * <p>
 * Each frame consists of an 8-byte header (opcode + length, both little-endian)
 * followed by a UTF-8 JSON payload of the specified length.
 */
public final class FrameReader {

    private FrameReader() {}

    /**
     * Reads a single frame from the given input stream.
     *
     * @param input the input stream to read from
     * @return the decoded frame
     * @throws EOFException              if the stream ends before a complete frame is read
     * @throws IOException               if an I/O error occurs
     * @throws IllegalArgumentException  if the frame contains an unknown opcode
     */
    public static Frame read(InputStream input) throws IOException {
        byte[] header = readExact(input, FrameSupport.HEADER_SIZE);
        ByteBuffer headerBuf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int opCode = headerBuf.getInt();
        int length = headerBuf.getInt();

        OpCode op = OpCode.fromCode(opCode);
        if (op == null) {
            throw new IllegalArgumentException("Unknown opcode: " + opCode);
        }

        JsonObject data = null;
        try {
            FrameSupport.validatePayloadLength(length);
            if (length > 0) {
                byte[] payload = readExact(input, length);
                data = FrameSupport.parseJsonObject(payload);
            }
        } catch (FrameFormatException e) {
            throw new IOException("Invalid frame payload", e);
        }
        return new Frame(op, data);
    }

    private static byte[] readExact(InputStream input, int count) throws IOException {
        byte[] buffer = new byte[count];
        int offset = 0;
        while (offset < count) {
            int read = input.read(buffer, offset, count - offset);
            if (read == -1) {
                throw new EOFException("Unexpected end of stream after " + offset + "/" + count + " bytes");
            }
            offset += read;
        }
        return buffer;
    }
}
