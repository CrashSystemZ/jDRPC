package fun.crashsystem.jdrpc.activity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Optional;

/**
 * Party information displayed on the activity card, showing group size and privacy.
 *
 * @param id          unique party identifier
 * @param currentSize current number of members
 * @param maxSize     maximum capacity
 * @param privacy     party privacy: {@link #PRIVACY_PRIVATE} (0) or {@link #PRIVACY_PUBLIC} (1), or {@code null} for default
 */
public record ActivityParty(String id, int currentSize, int maxSize, Integer privacy) {

    /** Party is private (invite-only). */
    public static final int PRIVACY_PRIVATE = 0;

    /** Party is public (joinable). */
    public static final int PRIVACY_PUBLIC = 1;

    /**
     * Creates a party with validation (default privacy).
     *
     * @throws IllegalArgumentException if currentSize &gt; maxSize or either is negative
     */
    public static ActivityParty of(String id, int currentSize, int maxSize) {
        return of(id, currentSize, maxSize, null);
    }

    /**
     * Creates a party with validation and explicit privacy setting.
     *
     * @param privacy {@link #PRIVACY_PRIVATE}, {@link #PRIVACY_PUBLIC}, or {@code null}
     * @throws IllegalArgumentException if currentSize &gt; maxSize or either is negative
     */
    public static ActivityParty of(String id, int currentSize, int maxSize, Integer privacy) {
        if (currentSize < 0) {
            throw new IllegalArgumentException("currentSize must be non-negative, got " + currentSize);
        }
        if (maxSize < 0) {
            throw new IllegalArgumentException("maxSize must be non-negative, got " + maxSize);
        }
        if (currentSize > maxSize) {
            throw new IllegalArgumentException("currentSize (" + currentSize + ") must be <= maxSize (" + maxSize + ")");
        }
        return new ActivityParty(id, currentSize, maxSize, privacy);
    }

    /** Serializes to JSON for the IPC wire format. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        Optional.ofNullable(id).ifPresent(v -> json.addProperty("id", v));
        if (maxSize > 0) {
            JsonArray sizeArr = new JsonArray();
            sizeArr.add(new JsonPrimitive(currentSize));
            sizeArr.add(new JsonPrimitive(maxSize));
            json.add("size", sizeArr);
        }
        Optional.ofNullable(privacy).ifPresent(p -> json.addProperty("privacy", p));
        return json;
    }
}
