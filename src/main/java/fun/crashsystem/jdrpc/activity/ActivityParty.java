package fun.crashsystem.jdrpc.activity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Optional;

/**
 * Party information displayed on the activity card, showing group size.
 *
 * @param id          unique party identifier
 * @param currentSize current number of members
 * @param maxSize     maximum capacity
 */
public record ActivityParty(String id, int currentSize, int maxSize) {

    /**
     * Creates a party with validation.
     *
     * @throws IllegalArgumentException if currentSize &gt; maxSize or either is negative
     */
    public static ActivityParty of(String id, int currentSize, int maxSize) {
        if (currentSize < 0) {
            throw new IllegalArgumentException("currentSize must be non-negative, got " + currentSize);
        }
        if (maxSize < 0) {
            throw new IllegalArgumentException("maxSize must be non-negative, got " + maxSize);
        }
        if (currentSize > maxSize) {
            throw new IllegalArgumentException("currentSize (" + currentSize + ") must be <= maxSize (" + maxSize + ")");
        }
        return new ActivityParty(id, currentSize, maxSize);
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
        return json;
    }
}
