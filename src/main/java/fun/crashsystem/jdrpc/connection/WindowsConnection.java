package fun.crashsystem.jdrpc.connection;

import fun.crashsystem.jdrpc.protocol.Frame;
import fun.crashsystem.jdrpc.protocol.FrameReader;
import fun.crashsystem.jdrpc.protocol.FrameWriter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReentrantLock;

/**
 * IPC connection for Windows using named pipes via {@link RandomAccessFile}.
 * <p>
 * On Windows, the kernel serializes all operations on a non-overlapped file handle,
 * including {@code CloseHandle()}. This means {@link RandomAccessFile#close()} will block
 * indefinitely if another thread has a pending read or write on the same pipe.
 * <p>
 * To avoid deadlocks during shutdown, {@link #close()} first marks the transport logically
 * closed, then only closes the underlying handle when no read/write is in flight. If a read
 * or write is still active, the actual handle close is deferred until the last operation
 * completes. This keeps shutdown non-blocking while still releasing the handle as soon as it
 * is safe to do so.
 */
@Slf4j
final class WindowsConnection implements Connection {
    private final ReentrantLock writeLock = new ReentrantLock();
    private final RandomAccessFile file;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final Object stateLock = new Object();
    private volatile boolean closeRequested;
    private volatile boolean handleClosed;
    private boolean reading;

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
                if (closeRequested) {
                    return -1;
                }
                return file.read(b, off, len);
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
        return !closeRequested;
    }

    @Override
    public Frame read() throws IOException {
        beginRead();
        boolean closeAfterRead;
        try {
            return FrameReader.read(inputStream);
        } finally {
            closeAfterRead = endRead();
            if (closeAfterRead) {
                closeHandleQuietly("completed read");
            }
        }
    }

    @Override
    public void write(Frame frame) throws IOException {
        // Pre-lock check: bail out immediately if connection is closing,
        // instead of blocking on writeLock, which may be held by a thread stuck in native I/O.
        ensureOpen();
        try {
            writeLock.lock();
            try {
                ensureOpen();
                FrameWriter.write(outputStream, frame);
            } finally {
                writeLock.unlock();
            }
        } finally {
            if (closeRequested && !handleClosed) {
                closeHandleQuietly("completed write");
            }
        }
    }

    @Override
    public void close() throws IOException {
        closeRequested = true;
        attemptHandleClose("close requested");
    }

    private void ensureOpen() throws IOException {
        if (closeRequested) {
            throw new IOException("Connection is closed");
        }
    }

    private void beginRead() throws IOException {
        synchronized (stateLock) {
            ensureOpen();
            reading = true;
        }
    }

    private boolean endRead() {
        synchronized (stateLock) {
            reading = false;
            return closeRequested && !handleClosed;
        }
    }

    private void attemptHandleClose(String reason) throws IOException {
        synchronized (stateLock) {
            if (handleClosed) {
                return;
            }
            if (reading) {
                log.debug("Windows pipe marked closed ({}; read pending, deferring handle close)", reason);
                return;
            }
        }

        if (!writeLock.tryLock()) {
            log.debug("Windows pipe marked closed ({}; write pending, deferring handle close)", reason);
            return;
        }

        try {
            synchronized (stateLock) {
                if (handleClosed) {
                    return;
                }
                if (reading) {
                    log.debug("Windows pipe marked closed ({}; read started while waiting for write lock)", reason);
                    return;
                }

                file.close();
                handleClosed = true;
            }
            log.debug("Windows connection closed");
        } finally {
            writeLock.unlock();
        }
    }

    private void closeHandleQuietly(String reason) {
        try {
            attemptHandleClose(reason);
        } catch (IOException e) {
            log.warn("Failed to close Windows pipe ({})", reason, e);
        }
    }
}
