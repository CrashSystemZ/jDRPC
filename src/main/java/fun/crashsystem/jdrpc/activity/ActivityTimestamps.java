package fun.crashsystem.jdrpc.activity;

import com.google.gson.JsonObject;

import java.util.Optional;

/**
 * Timestamps for an activity, controlling the "elapsed" or "remaining" display.
 * <p>
 * When {@code start} is set, Discord shows "XX:XX elapsed".
 * When {@code end} is set, Discord shows "XX:XX remaining".
 *
 * @param start Unix timestamp (milliseconds) when the activity started, or {@code null}
 * @param end   Unix timestamp (milliseconds) when the activity will end, or {@code null}
 */
public record ActivityTimestamps(Long start, Long end) {

    /**
     * Creates timestamps with only a start time.
     *
     * @param epochMillis the start time as Unix epoch milliseconds
     */
    public static ActivityTimestamps startingAt(long epochMillis) {
        return new ActivityTimestamps(epochMillis, null);
    }

    /**
     * Creates timestamps with only an end time.
     *
     * @param epochMillis the end time as Unix epoch milliseconds
     */
    public static ActivityTimestamps endingAt(long epochMillis) {
        return new ActivityTimestamps(null, epochMillis);
    }

    /** Serializes to JSON for the IPC wire format. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        Optional.ofNullable(start).ifPresent(s -> json.addProperty("start", s));
        Optional.ofNullable(end).ifPresent(e -> json.addProperty("end", e));
        return json;
    }
}
