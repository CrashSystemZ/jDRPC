package fun.crashsystem.jdrpc.activity;

import com.google.gson.JsonObject;

import java.util.Optional;

/**
 * Secrets for Rich Presence join/spectate functionality.
 *
 * @param join     secret for the "Join" button
 * @param spectate secret for the "Spectate" button
 * @param match    secret used for match identification
 */
public record ActivitySecrets(String join, String spectate, String match) {

    /** Serializes to JSON for the IPC wire format. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        Optional.ofNullable(join).ifPresent(v -> json.addProperty("join", v));
        Optional.ofNullable(spectate).ifPresent(v -> json.addProperty("spectate", v));
        Optional.ofNullable(match).ifPresent(v -> json.addProperty("match", v));
        return json;
    }
}
