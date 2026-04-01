package fun.crashsystem.jdrpc.util;

import java.util.Locale;

/**
 * Detected operating system platform.
 * Used to determine the correct IPC pipe implementation and path format.
 */
public enum Platform {
    WINDOWS,
    MACOS,
    LINUX;

    /** The current operating system platform, detected at class load time. */
    public static final Platform CURRENT = detect();

    private static Platform detect() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return WINDOWS;
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return MACOS;
        }
        return LINUX;
    }
}
