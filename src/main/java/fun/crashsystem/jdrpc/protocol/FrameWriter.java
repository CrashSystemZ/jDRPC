package fun.crashsystem.jdrpc.protocol;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes Discord IPC frames to an {@link OutputStream}.
 */
public final class FrameWriter {

    private FrameWriter() {}

    /**
     * Encodes and writes a single frame to the given output stream.
     *
     * @param output the output stream to write to
     * @param frame  the frame to encode and write
     * @throws IOException if an I/O error occurs
     */
    public static void write(OutputStream output, Frame frame) throws IOException {
        byte[] bytes = frame.encode();
        output.write(bytes);
        output.flush();
    }
}
