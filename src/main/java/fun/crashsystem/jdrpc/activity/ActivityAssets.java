package fun.crashsystem.jdrpc.activity;

import com.google.gson.JsonObject;

import java.util.Optional;

/**
 * Image assets displayed on the activity card in Discord.
 * <p>
 * Assets can be an asset key from the Developer Portal or a direct image URL.
 *
 * @param largeImage the key or URL for the large image
 * @param largeText  tooltip text for the large image
 * @param smallImage the key or URL for the small circular overlay image
 * @param smallText  tooltip text for the small image
 */
public record ActivityAssets(String largeImage, String largeText, String smallImage, String smallText) {

    /** Serializes to JSON for the IPC wire format. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        Optional.ofNullable(largeImage).ifPresent(v -> json.addProperty("large_image", v));
        Optional.ofNullable(largeText).ifPresent(v -> json.addProperty("large_text", v));
        Optional.ofNullable(smallImage).ifPresent(v -> json.addProperty("small_image", v));
        Optional.ofNullable(smallText).ifPresent(v -> json.addProperty("small_text", v));
        return json;
    }
}
