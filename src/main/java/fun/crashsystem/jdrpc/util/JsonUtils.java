package fun.crashsystem.jdrpc.util;

import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

/**
 * Null-safe JSON extraction utilities for Discord IPC payloads.
 */
public final class JsonUtils {

    private JsonUtils() {}

    /**
     * Extracts a string value from a JSON object.
     * <p>
     * Returns {@link Optional#empty()} if the field is missing or explicit JSON null.
     * If the field is present but not a JSON string, this method attempts
     * to coerce via {@code getAsString()}, preserving the current runtime behavior.
     *
     * @param json  the JSON object to read from
     * @param field the field name
     * @return an Optional containing the string value, or empty
     */
    public static Optional<String> optString(JsonObject json, String field) {
        if (json == null || !json.has(field) || json.get(field).isJsonNull()) {
            return Optional.empty();
        }
        return Optional.of(json.get(field).getAsString());
    }

    /**
     * Extracts a string value from a JSON object, returning a default if absent or null.
     * <p>
     * If the field is present but not a JSON string, this method uses
     * {@code getAsString()} from Gson, matching the runtime behavior.
     *
     * @param json         the JSON object to read from
     * @param field        the field name
     * @param defaultValue the default value to return if the field is missing or null
     * @return the string value, or {@code defaultValue}
     */
    public static String getString(JsonObject json, String field, String defaultValue) {
        return optString(json, field).orElse(defaultValue);
    }

    /**
     * Extracts a nested JSON object, returning {@link Optional#empty()}
     * if the field is missing, null, or not a JSON object.
     *
     * @param json  the parent JSON object
     * @param field the field name
     * @return an Optional containing the nested object, or empty
     */
    public static Optional<JsonObject> optObject(JsonObject json, String field) {
        if (json == null || !json.has(field) || !json.get(field).isJsonObject()) {
            return Optional.empty();
        }
        return Optional.of(json.getAsJsonObject(field));
    }

    /**
     * Extracts a boolean value from a JSON object, returning a default if absent or null.
     *
     * @param json         the JSON object to read from
     * @param field        the field name
     * @param defaultValue the default value to return if the field is missing or null
     * @return the boolean value, or {@code defaultValue}
     */
    public static boolean getBoolean(JsonObject json, String field, boolean defaultValue) {
        if (json == null || !json.has(field) || json.get(field).isJsonNull()) {
            return defaultValue;
        }
        return json.get(field).getAsBoolean();
    }

    /**
     * Extracts an int value from a JSON object, returning a default if absent or null.
     *
     * @param json         the JSON object to read from
     * @param field        the field name
     * @param defaultValue the default value to return if the field is missing or null
     * @return the int value, or {@code defaultValue}
     */
    public static int getInt(JsonObject json, String field, int defaultValue) {
        if (json == null || !json.has(field) || json.get(field).isJsonNull()) {
            return defaultValue;
        }
        return json.get(field).getAsInt();
    }
}
