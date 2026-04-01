package fun.crashsystem.jdrpc.connection;

/**
 * Safe, serializable connection failure metadata.
 *
 * @param type    the failure type, usually an exception class name
 * @param message the failure message, never {@code null}
 */
public record FailureInfo(String type, String message) {

    public FailureInfo {
        type = type == null || type.isBlank() ? "unknown" : type;
        message = message == null ? "" : message;
    }

    /**
     * Creates a failure descriptor from a throwable without exposing its stack trace.
     */
    public static FailureInfo from(Throwable throwable) {
        if (throwable == null) {
            return new FailureInfo("unknown", "");
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.toString();
        }
        return new FailureInfo(throwable.getClass().getName(), message);
    }
}
