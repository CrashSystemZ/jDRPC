package fun.crashsystem.jdrpc.activity;

import com.google.gson.JsonObject;
import fun.crashsystem.jdrpc.util.UrlValidator;

/**
 * A clickable button displayed on the Discord activity card.
 * Discord supports a maximum of <b>2 buttons</b> per activity.
 *
 * @param label the text displayed on the button (1-32 characters)
 * @param url   the URL opened on click (must be an absolute HTTPS URL, max 256 characters)
 */
public record ActivityButton(String label, String url) {

    /**
     * Validates label and URL constraints.
     */
    public ActivityButton {
        if (label == null || label.isEmpty() || label.length() > 32) {
            throw new IllegalArgumentException(
                    "Button label must be 1-32 characters, got: " + (label == null ? "null" : label.length()));
        }
        if (url == null || url.length() > 256) {
            throw new IllegalArgumentException("Button URL must be at most 256 characters");
        }
        UrlValidator.requireAbsoluteHttpsUrl(url, "Button URL", 256);
    }

    /** Serializes to JSON for the IPC wire format. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("label", label);
        json.addProperty("url", url);
        return json;
    }
}
