package fun.crashsystem.jdrpc.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;

/**
 * Shared validation and parsing helpers for Discord IPC frames.
 */
public final class FrameSupport {
    public static final int HEADER_SIZE = 8;
    public static final int MAX_FRAME_SIZE = 64 * 1024;

    private FrameSupport() {}

    /**
     * Validates a declared frame payload length before any allocation happens.
     */
    public static int validatePayloadLength(int length) throws FrameFormatException {
        if (length < 0) {
            throw new FrameFormatException("Frame payload length must be >= 0, got " + length);
        }
        if (length > MAX_FRAME_SIZE) {
            throw new FrameFormatException(
                    "Frame payload length exceeds limit " + MAX_FRAME_SIZE + ", got " + length
            );
        }
        return length;
    }

    /**
     * Ensures that a decoded payload is fully present in the backing buffer.
     */
    public static void validateAvailablePayload(int available, int expected) throws FrameFormatException {
        if (available < expected) {
            throw new FrameFormatException(
                    "Frame payload truncated: expected " + expected + " bytes, got " + available
            );
        }
    }

    /**
     * Parses a UTF-8 encoded JSON object payload.
     */
    public static JsonObject parseJsonObject(byte[] payload) throws FrameFormatException {
        try {
            JsonElement parsed = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                throw new FrameFormatException("Frame payload must be a JSON object");
            }
            return parsed.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new FrameFormatException("Invalid frame JSON payload", e);
        }
    }

    /**
     * Internal checked format error used so callers can map failures to API-specific exceptions.
     */
    public static final class FrameFormatException extends Exception {
        public FrameFormatException(String message) {
            super(message);
        }

        public FrameFormatException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
