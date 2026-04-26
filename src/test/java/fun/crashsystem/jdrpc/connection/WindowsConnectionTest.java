package fun.crashsystem.jdrpc.connection;

import com.google.gson.JsonObject;
import fun.crashsystem.jdrpc.protocol.Frame;
import fun.crashsystem.jdrpc.protocol.OpCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WindowsConnectionTest {
    @TempDir
    Path tempDir;

    @Test
    void writeEncodesFrameToRandomAccessFile() throws Exception {
        Path pipeFile = tempDir.resolve("discord-ipc-0");

        try (WindowsConnection connection = new WindowsConnection(pipeFile.toString())) {
            JsonObject data = new JsonObject();
            data.addProperty("cmd", "PING");

            connection.write(new Frame(OpCode.FRAME, data));
        }

        byte[] bytes = Files.readAllBytes(pipeFile);
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(OpCode.FRAME.code(), header.getInt());

        int payloadLength = header.getInt();
        assertEquals(bytes.length - 8, payloadLength);
        assertEquals(
                "{\"cmd\":\"PING\"}",
                new String(bytes, 8, payloadLength, StandardCharsets.UTF_8)
        );
    }

    @Test
    void readDecodesFrameFromRandomAccessFile() throws Exception {
        Path pipeFile = tempDir.resolve("discord-ipc-1");
        JsonObject data = new JsonObject();
        data.addProperty("evt", "READY");
        Files.write(pipeFile, new Frame(OpCode.FRAME, data).encode());

        try (WindowsConnection connection = new WindowsConnection(pipeFile.toString())) {
            Frame frame = connection.read();

            assertEquals(OpCode.FRAME, frame.op());
            assertEquals("READY", frame.data().get("evt").getAsString());
        }
    }

    @Test
    void closeMarksConnectionClosedAndRejectsWrites() throws Exception {
        Path pipeFile = tempDir.resolve("discord-ipc-2");
        WindowsConnection connection = new WindowsConnection(pipeFile.toString());

        connection.close();

        assertFalse(connection.isOpen());

        JsonObject data = new JsonObject();
        data.addProperty("cmd", "PING");
        IOException thrown = assertThrows(
                IOException.class,
                () -> connection.write(new Frame(OpCode.FRAME, data))
        );
        assertEquals("Connection is closed", thrown.getMessage());
    }
}
