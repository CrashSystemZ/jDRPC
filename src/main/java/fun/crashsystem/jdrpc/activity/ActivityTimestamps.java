package fun.crashsystem.jdrpc.activity;

import com.google.gson.JsonObject;

import java.util.Optional;

/**
 * Timestamps for an activity, controlling the "elapsed" or "remaining" display.
 * <p>
 * When {@code start} is set, Discord shows "XX:XX elapsed".
 * When {@code end} is set, Discord shows "XX:XX remaining".
 * <p>
 * <b>Important:</b> Discord expects Unix epoch timestamps in <b>seconds</b>, not milliseconds.
 *
 * @param start Unix timestamp (<b>seconds</b>) when the activity started, or {@code null}
 * @param end   Unix timestamp (<b>seconds</b>) when the activity will end, or {@code null}
 */
public record ActivityTimestamps(Long start, Long end) {

    /**
     * Creates timestamps with only a start time.
     *
     * @param epochSeconds the start time as Unix epoch <b>seconds</b>
     */
    public static ActivityTimestamps startingAt(long epochSeconds) {
        return new ActivityTimestamps(epochSeconds, null);
    }

    /**
     * Creates timestamps with only an end time.
     *
     * @param epochSeconds the end time as Unix epoch <b>seconds</b>
     */
    public static ActivityTimestamps endingAt(long epochSeconds) {
        return new ActivityTimestamps(null, epochSeconds);
    }

    /** Serializes to JSON for the IPC wire format. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        Optional.ofNullable(start).ifPresent(s -> json.addProperty("start", s));
        Optional.ofNullable(end).ifPresent(e -> json.addProperty("end", e));
        return json;
    }
}
