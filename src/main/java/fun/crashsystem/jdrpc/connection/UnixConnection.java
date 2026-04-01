package fun.crashsystem.jdrpc.connection;

import com.google.gson.JsonObject;
import fun.crashsystem.jdrpc.protocol.Frame;
import fun.crashsystem.jdrpc.protocol.FrameSupport;
import fun.crashsystem.jdrpc.protocol.FrameSupport.FrameFormatException;
import fun.crashsystem.jdrpc.protocol.OpCode;
import lombok.extern.slf4j.Slf4j;

import java.io.EOFException;
import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

import static fun.crashsystem.jdrpc.protocol.FrameSupport.HEADER_SIZE;
import static java.net.StandardProtocolFamily.UNIX;
import static java.nio.ByteOrder.LITTLE_ENDIAN;

/**
 * IPC connection for macOS/Linux using Java 17+ Unix domain sockets.
 * <p>
 * Reads/writes directly via {@link SocketChannel} and {@link ByteBuffer}
 * instead of InputStream wrappers, which avoids buffering issues on macOS.
 */
@Slf4j
final class UnixConnection implements Connection {
    private final ReentrantLock writeLock = new ReentrantLock();
    private final SocketChannel channel;
    private volatile boolean closed;

    UnixConnection(String path) throws IOException {
        this.channel = SocketChannel.open(UNIX);
        channel.connect(UnixDomainSocketAddress.of(Path.of(path)));
        channel.configureBlocking(true);
        log.debug("Connected to Unix pipe: {}", path);
    }

    @Override
    public boolean isOpen() {
        return !closed && channel.isOpen() && channel.isConnected();
    }

    @Override
    public Frame read() throws IOException {
        ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE).order(LITTLE_ENDIAN);
        readFully(header);
        header.flip();

        int opCode = header.getInt();
        int length = header.getInt();

        OpCode op = OpCode.fromCode(opCode);
        if (op == null) {
            throw new IOException("Unknown opcode: " + opCode);
        }

        JsonObject data = null;
        try {
            FrameSupport.validatePayloadLength(length);
            if (length > 0) {
                ByteBuffer payload = ByteBuffer.allocate(length);
                readFully(payload);
                payload.flip();
                byte[] bytes = new byte[length];
                payload.get(bytes);
                data = FrameSupport.parseJsonObject(bytes);
            }
        } catch (FrameFormatException e) {
            throw new IOException("Invalid frame payload", e);
        }

        return new Frame(op, data);
    }

    @Override
    public void write(Frame frame) throws IOException {
        writeLock.lock();
        try {
            ensureOpen();
            byte[] encoded = frame.encode();
            ByteBuffer buffer = ByteBuffer.wrap(encoded);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        writeLock.lock();
        try {
            closed = true;
            channel.close();
            log.debug("Unix connection closed");
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Reads until the buffer is full, blocking as needed.
     */
    private void readFully(ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer);
            if (read == -1) {
                throw new EOFException("Unexpected end of stream, needed " + buffer.remaining() + " more bytes");
            }
            if (read == 0) {
                Thread.yield();
            }
        }
    }

    private void ensureOpen() throws IOException {
        if (closed || !channel.isOpen()) {
            throw new IOException("Connection is closed");
        }
    }
}
