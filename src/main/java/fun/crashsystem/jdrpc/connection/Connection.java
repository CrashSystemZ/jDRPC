package fun.crashsystem.jdrpc.connection;

import fun.crashsystem.jdrpc.protocol.Frame;

import java.io.Closeable;
import java.io.IOException;

/**
 * Interface for a Discord IPC pipe connection.
 * Implementations handle platform-specific communication.
 */
public interface Connection extends Closeable {
    /** Whether the transport is currently open. */
    boolean isOpen();

    /**
     * Reads a single frame from the IPC pipe. Blocks until available.
     *
     * @return the decoded frame
     * @throws IOException if the pipe is closed or a read error occurs
     */
    Frame read() throws IOException;

    /**
     * Writes a single frame to the IPC pipe.
     *
     * @param frame the frame to send
     * @throws IOException if the pipe is closed or a write error occurs
     */
    void write(Frame frame) throws IOException;
}
