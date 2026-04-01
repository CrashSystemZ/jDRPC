package fun.crashsystem.jdrpc.util;

/**
 * Utility for obtaining the current JVM process ID.
 */
public final class ProcessId {

    private ProcessId() {}

    /**
     * Returns the PID of the current JVM process.
     *
     * @return the process ID
     */
    public static long current() {
        return ProcessHandle.current().pid();
    }
}
