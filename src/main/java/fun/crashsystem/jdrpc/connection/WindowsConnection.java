package fun.crashsystem.jdrpc.connection;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import fun.crashsystem.jdrpc.protocol.Frame;
import fun.crashsystem.jdrpc.protocol.FrameReader;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.ReentrantLock;

/**
 * IPC connection for Windows using named pipes with overlapped (asynchronous) I/O via JNA.
 * <p>
 * A single synchronous pipe handle ({@link java.io.RandomAccessFile}) serializes concurrent
 * {@code ReadFile}/{@code WriteFile} operations at the kernel level: a blocking read in the
 * background thread prevents any subsequent write from ever returning. Opening two separate
 * pipe handles does not work either because Discord treats the second client handle as a
 * new connection without a handshake and rejects it.
 * <p>
 * The fix is to open one handle with {@code FILE_FLAG_OVERLAPPED} and drive each I/O call
 * through per-operation {@code OVERLAPPED} structures with distinct events for reads and
 * writes. In overlapped mode the kernel does not serialize reads against writes on the
 * same handle.
 */
@Log4j2
final class WindowsConnection implements Connection {
    private static final Kernel32 K32 = Kernel32.INSTANCE;
    private static final Kernel32Extra K32X = Kernel32Extra.INSTANCE;

    private final ReentrantLock writeLock = new ReentrantLock();
    private final WinNT.HANDLE handle;
    private final WinNT.HANDLE readEvent;
    private final WinNT.HANDLE writeEvent;
    private final InputStream inputStream;
    private final Object stateLock = new Object();
    private volatile boolean closeRequested;
    private volatile boolean handleClosed;

    WindowsConnection(String path) throws IOException {
        WinNT.HANDLE h = K32.CreateFile(
                path,
                WinNT.GENERIC_READ | WinNT.GENERIC_WRITE,
                0,
                null,
                WinNT.OPEN_EXISTING,
                WinNT.FILE_FLAG_OVERLAPPED,
                null
        );
        if (h == null || WinBase.INVALID_HANDLE_VALUE.equals(h)) {
            int err = K32.GetLastError();
            throw new IOException("CreateFile(" + path + ") failed: " + Kernel32Util.formatMessage(err) + " (code " + err + ")");
        }
        this.handle = h;
        this.readEvent = K32.CreateEvent(null, true, false, null);
        this.writeEvent = K32.CreateEvent(null, true, false, null);
        if (readEvent == null || writeEvent == null) {
            int err = K32.GetLastError();
            closeQuietlyHandle(handle);
            closeQuietlyHandle(readEvent);
            closeQuietlyHandle(writeEvent);
            throw new IOException("CreateEvent failed: " + Kernel32Util.formatMessage(err));
        }
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
                return overlappedRead(b, off, len);
            }
        };
        log.debug("Connected to Windows pipe (overlapped): {}", path);
    }

    @Override
    public boolean isOpen() {
        return !closeRequested;
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
            byte[] bytes = frame.encode();
            overlappedWrite(bytes, 0, bytes.length);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void close() {
        closeRequested = true;
        synchronized (stateLock) {
            if (handleClosed) {
                return;
            }
            handleClosed = true;
            try {
                K32X.CancelIoEx(handle, null);
            } catch (Throwable ignored) {
            }
            closeQuietlyHandle(handle);
            closeQuietlyHandle(readEvent);
            closeQuietlyHandle(writeEvent);
        }
        log.debug("Windows connection closed");
    }

    private int overlappedRead(byte[] buf, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        if (closeRequested) {
            return -1;
        }

        WinBase.OVERLAPPED overlapped = new WinBase.OVERLAPPED();
        overlapped.hEvent = readEvent;
        K32.ResetEvent(readEvent);

        // Overlapped I/O requires the buffer to stay alive until the kernel completes the I/O.
        // Using a Java byte[] is unsafe because JNA releases the pinned reference after the
        // ReadFile call returns ERROR_IO_PENDING — the kernel would then write to freed memory.
        // Allocate native Memory that lives for the duration of the operation.
        Memory buffer = new Memory(len);
        IntByReference bytesRead = new IntByReference(0);
        boolean ok = K32X.ReadFile(handle, buffer, len, null, overlapped);
        if (!ok) {
            int err = K32.GetLastError();
            if (err == WinError.ERROR_IO_PENDING) {
                ok = K32X.GetOverlappedResult(handle, overlapped, bytesRead, true);
                if (!ok) {
                    err = K32.GetLastError();
                    if (err == WinError.ERROR_BROKEN_PIPE || err == WinError.ERROR_OPERATION_ABORTED) {
                        return -1;
                    }
                    throw new IOException("GetOverlappedResult(read) failed: " + Kernel32Util.formatMessage(err) + " (code " + err + ")");
                }
            } else if (err == WinError.ERROR_BROKEN_PIPE) {
                return -1;
            } else {
                throw new IOException("ReadFile failed: " + Kernel32Util.formatMessage(err) + " (code " + err + ")");
            }
        } else {
            ok = K32X.GetOverlappedResult(handle, overlapped, bytesRead, true);
            if (!ok) {
                int err = K32.GetLastError();
                throw new IOException("GetOverlappedResult(read-sync) failed: " + Kernel32Util.formatMessage(err) + " (code " + err + ")");
            }
        }
        int n = bytesRead.getValue();
        if (n <= 0) {
            return -1;
        }
        buffer.read(0, buf, off, n);
        return n;
    }

    private void overlappedWrite(byte[] buf, int off, int len) throws IOException {
        WinBase.OVERLAPPED overlapped = new WinBase.OVERLAPPED();
        overlapped.hEvent = writeEvent;
        K32.ResetEvent(writeEvent);

        Memory buffer = new Memory(len);
        buffer.write(0, buf, off, len);
        IntByReference bytesWritten = new IntByReference(0);
        boolean ok = K32X.WriteFile(handle, buffer, len, null, overlapped);
        if (!ok) {
            int err = K32.GetLastError();
            if (err == WinError.ERROR_IO_PENDING) {
                ok = K32X.GetOverlappedResult(handle, overlapped, bytesWritten, true);
                if (!ok) {
                    err = K32.GetLastError();
                    throw new IOException("GetOverlappedResult(write) failed: " + Kernel32Util.formatMessage(err) + " (code " + err + ")");
                }
            } else {
                throw new IOException("WriteFile failed: " + Kernel32Util.formatMessage(err) + " (code " + err + ")");
            }
        } else {
            ok = K32X.GetOverlappedResult(handle, overlapped, bytesWritten, true);
            if (!ok) {
                int err = K32.GetLastError();
                throw new IOException("GetOverlappedResult(write-sync) failed: " + Kernel32Util.formatMessage(err) + " (code " + err + ")");
            }
        }
        int written = bytesWritten.getValue();
        if (written != len) {
            throw new IOException("Partial write: " + written + "/" + len + " bytes");
        }
    }

    private void ensureOpen() throws IOException {
        if (closeRequested) {
            throw new IOException("Connection is closed");
        }
    }

    private static void closeQuietlyHandle(WinNT.HANDLE h) {
        if (h == null) {
            return;
        }
        try {
            K32.CloseHandle(h);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Extra Kernel32 entry points not exposed by the JNA {@code Kernel32} interface in this version,
     * plus Pointer-based {@code ReadFile}/{@code WriteFile} so we can supply {@link Memory} buffers
     * that stay alive during asynchronous I/O.
     */
    private interface Kernel32Extra extends Library {
        Kernel32Extra INSTANCE = Native.load("kernel32", Kernel32Extra.class);

        boolean GetOverlappedResult(WinNT.HANDLE hFile, WinBase.OVERLAPPED lpOverlapped,
                                     IntByReference lpNumberOfBytesTransferred, boolean bWait);

        boolean CancelIoEx(WinNT.HANDLE hFile, WinBase.OVERLAPPED lpOverlapped);

        boolean ReadFile(WinNT.HANDLE hFile, Pointer lpBuffer, int nNumberOfBytesToRead,
                         IntByReference lpNumberOfBytesRead, WinBase.OVERLAPPED lpOverlapped);

        boolean WriteFile(WinNT.HANDLE hFile, Pointer lpBuffer, int nNumberOfBytesToWrite,
                          IntByReference lpNumberOfBytesWritten, WinBase.OVERLAPPED lpOverlapped);
    }
}
