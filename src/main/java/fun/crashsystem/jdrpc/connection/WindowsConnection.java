package fun.crashsystem.jdrpc.connection;

import fun.crashsystem.jdrpc.protocol.Frame;
import fun.crashsystem.jdrpc.protocol.FrameReader;
import fun.crashsystem.jdrpc.protocol.FrameWriter;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReentrantLock;

/**
 * IPC connection for Windows using named pipes via {@link RandomAccessFile}.
 * <p>
 * A synchronous pipe handle (which is what {@code RandomAccessFile} opens — without
 * {@code FILE_FLAG_OVERLAPPED}) serializes concurrent {@code ReadFile}/{@code WriteFile}
 * operations at the kernel level. A blocking {@code ReadFile} on the worker thread
 * would prevent any subsequent write from ever returning, deadlocking the client.
 * <p>
 * To avoid that, reads never call into a long-blocking {@code ReadFile}. The input
 * stream polls {@link RandomAccessFile#length()} (which on Windows pipes reports the
 * bytes currently buffered) and only invokes {@code file.read(...)} when data is
 * already available — so the underlying {@code ReadFile} returns immediately. Between
 * polls, write calls have free windows to complete.
 */
@Log4j2
final class WindowsConnection implements Connection {
    private static final int POLL_INTERVAL_MS = 50;

    private final ReentrantLock writeLock = new ReentrantLock();
    private final RandomAccessFile file;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private volatile boolean closed;

    WindowsConnection(String path) throws IOException {
        this.file = new RandomAccessFile(path, "rw");
        this.inputStream = new InputStream() {
            @Override
            public int read() throws IOException {
                byte[] b = new byte[1];
                int n = read(b, 0, 1);
                return n == -1 ? -1 : b[0] & 0xFF;
            }

            @Override
            public int read(byte @NonNull [] b, int off, int len) throws IOException {
                if (len == 0) {
                    return 0;
                }
                while (!closed) {
                    long available;
                    try {
                        available = file.length();
                    } catch (IOException e) {
                        if (closed) {
                            return -1;
                        }
                        throw e;
                    }
                    if (available > 0) {
                        return file.read(b, off, len);
                    }
                    try {
                        Thread.sleep(POLL_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException("Read interrupted");
                    }
                }
                return -1;
            }
        };
        this.outputStream = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                file.write(b);
            }

            @Override
            public void write(byte @NonNull [] b, int off, int len) throws IOException {
                file.write(b, off, len);
            }
        };
        log.debug("Connected to Windows pipe: {}", path);
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public Frame read() throws IOException {
        return FrameReader.read(inputStream);
    }

    @Override
    public void write(Frame frame) throws IOException {
        ensureOpen();
        writeLock.lock();
        try {
            ensureOpen();
            FrameWriter.write(outputStream, frame);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        file.close();
        log.debug("Windows connection closed");
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Connection is closed");
        }
    }
}
