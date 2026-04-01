package fun.crashsystem.jdrpc.activity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fun.crashsystem.jdrpc.util.UrlValidator;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Represents a Discord Rich Presence activity.
 * <p>
 * This is the primary data structure for controlling what is displayed on a user's Discord profile.
 * Use the {@link Builder} to construct instances fluently, or the constructor directly.
 *
 * <pre>{@code
 * Activity activity = new Activity.Builder()
 *     .setType(ActivityType.PLAYING)
 *     .setDetails("In a match")
 *     .setState("Gold III")
 *     .setStartTimestamp(System.currentTimeMillis())
 *     .setLargeImage("game-logo", "My Game")
 *     .addButton("Play", "https://example.com")
 *     .build();
 * }</pre>
 */
@Accessors(fluent = true)
public record Activity(ActivityType type, String state, String details, String url, ActivityTimestamps timestamps,
                       ActivityAssets assets, ActivityParty party, ActivitySecrets secrets,
                       List<ActivityButton> buttons, Boolean instance) {

    /**
     * Full constructor.
     */
    public Activity(ActivityType type, String state, String details, String url,
                    ActivityTimestamps timestamps, ActivityAssets assets, ActivityParty party,
                    ActivitySecrets secrets, List<ActivityButton> buttons, Boolean instance) {
        this.type = type != null ? type : ActivityType.PLAYING;
        this.state = state;
        this.details = details;
        this.url = url;
        this.timestamps = timestamps;
        this.assets = assets;
        this.party = party;
        this.secrets = secrets;
        this.buttons = buttons != null ? List.copyOf(buttons) : null;
        this.instance = instance;

        if (this.state != null && (this.state.length() < 2 || this.state.length() > 128)) {
            throw new IllegalArgumentException("Activity state must be 2-128 characters, got " + this.state.length());
        }
        if (this.details != null && (this.details.length() < 2 || this.details.length() > 128)) {
            throw new IllegalArgumentException("Activity details must be 2-128 characters, got " + this.details.length());
        }
        if (this.buttons != null && this.buttons.size() > 2) {
            throw new IllegalArgumentException("Activity supports a maximum of 2 buttons, got " + this.buttons.size());
        }
        if (this.type == ActivityType.STREAMING && this.url == null) {
            throw new IllegalArgumentException("Streaming activity type requires a URL");
        }
        if (this.type == ActivityType.STREAMING) {
            UrlValidator.requireAbsoluteHttpsUrl(this.url, "Streaming URL", -1);
        }
    }

    /** Returns the state as Optional. */
    public Optional<String> optionalState() {
        return Optional.ofNullable(state);
    }

    /** Returns the details as Optional. */
    public Optional<String> optionalDetails() {
        return Optional.ofNullable(details);
    }

    /** Returns the url as Optional. */
    public Optional<String> optionalUrl() {
        return Optional.ofNullable(url);
    }

    /** Returns the timestamps as Optional. */
    public Optional<ActivityTimestamps> optionalTimestamps() {
        return Optional.ofNullable(timestamps);
    }

    /** Returns the assets as Optional. */
    public Optional<ActivityAssets> optionalAssets() {
        return Optional.ofNullable(assets);
    }

    /** Returns the party as Optional. */
    public Optional<ActivityParty> optionalParty() {
        return Optional.ofNullable(party);
    }

    /** Returns the secrets as Optional. */
    public Optional<ActivitySecrets> optionalSecrets() {
        return Optional.ofNullable(secrets);
    }

    /** Returns the buttons as Optional. */
    public Optional<List<ActivityButton>> optionalButtons() {
        return Optional.ofNullable(buttons);
    }

    /** Returns the instance flag as Optional. */
    public Optional<Boolean> optionalInstance() {
        return Optional.ofNullable(instance);
    }

    /**
     * Serializes this activity to a {@link JsonObject} for the Discord IPC SET_ACTIVITY command.
     *
     * @return a JsonObject in the Discord IPC wire format
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", type.value());

        optionalState().ifPresent(s -> json.addProperty("state", s));
        optionalDetails().ifPresent(d -> json.addProperty("details", d));
        optionalUrl().ifPresent(u -> json.addProperty("url", u));
        optionalTimestamps().ifPresent(t -> json.add("timestamps", t.toJson()));
        optionalAssets().ifPresent(a -> json.add("assets", a.toJson()));
        optionalParty().ifPresent(p -> json.add("party", p.toJson()));
        optionalSecrets().ifPresent(s -> json.add("secrets", s.toJson()));
        optionalButtons().filter(b -> !b.isEmpty()).ifPresent(b -> {
            JsonArray arr = new JsonArray();
            b.forEach(btn -> arr.add(btn.toJson()));
            json.add("buttons", arr);
        });
        optionalInstance().ifPresent(i -> json.addProperty("instance", i));

        return json;
    }

    /**
     * Fluent builder for constructing {@link Activity} instances.
     */
    public static final class Builder {
        private ActivityType type = ActivityType.PLAYING;
        private String state;
        private String details;
        private String url;
        private ActivityTimestamps timestamps;
        private ActivityAssets assets;
        private ActivityParty party;
        private ActivitySecrets secrets;
        private final List<ActivityButton> buttons = new ArrayList<>();
        private Boolean instance;

        /** Sets the activity type. */
        public Builder setType(ActivityType type) {
            this.type = type;
            return this;
        }

        /** Sets the party status text (2-128 characters). */
        public Builder setState(String state) {
            this.state = state;
            return this;
        }

        /** Sets the activity detail text (2-128 characters). */
        public Builder setDetails(String details) {
            this.details = details;
            return this;
        }

        /** Sets the stream URL (required for {@link ActivityType#STREAMING}). */
        public Builder setUrl(String url) {
            this.url = url;
            return this;
        }

        /** Sets the start timestamp. Discord shows "XX:XX elapsed". */
        public Builder setStartTimestamp(long epochMillis) {
            this.timestamps = new ActivityTimestamps(epochMillis, timestamps != null ? timestamps.end() : null);
            return this;
        }

        /** Sets the end timestamp. Discord shows "XX:XX remaining". */
        public Builder setEndTimestamp(long epochMillis) {
            this.timestamps = new ActivityTimestamps(timestamps != null ? timestamps.start() : null, epochMillis);
            return this;
        }

        /** Sets the timestamps directly. */
        public Builder setTimestamps(ActivityTimestamps timestamps) {
            this.timestamps = timestamps;
            return this;
        }

        /** Sets the large image key/URL only. */
        public Builder setLargeImage(String key) {
            this.assets = new ActivityAssets(key, assets != null ? assets.largeText() : null,
                    assets != null ? assets.smallImage() : null, assets != null ? assets.smallText() : null);
            return this;
        }

        /** Sets the large image key/URL and tooltip text. */
        public Builder setLargeImage(String key, String text) {
            this.assets = new ActivityAssets(key, text,
                    assets != null ? assets.smallImage() : null, assets != null ? assets.smallText() : null);
            return this;
        }

        /** Sets the small image key/URL and tooltip text. */
        public Builder setSmallImage(String key, String text) {
            this.assets = new ActivityAssets(assets != null ? assets.largeImage() : null,
                    assets != null ? assets.largeText() : null, key, text);
            return this;
        }

        /** Sets the assets directly. */
        public Builder setAssets(ActivityAssets assets) {
            this.assets = assets;
            return this;
        }

        /** Sets the party information. */
        public Builder setParty(String id, int currentSize, int maxSize) {
            this.party = ActivityParty.of(id, currentSize, maxSize);
            return this;
        }

        /** Sets the secrets. */
        public Builder setSecrets(ActivitySecrets secrets) {
            this.secrets = secrets;
            return this;
        }

        /**
         * Adds a clickable button. Maximum 2 buttons.
         *
         * @param label button text (1-32 chars)
         * @param url   URL to open (http/https, max 256 chars)
         */
        public Builder addButton(String label, String url) {
            buttons.add(new ActivityButton(label, url));
            return this;
        }

        /** Sets the instance flag. */
        public Builder setInstance(boolean instance) {
            this.instance = instance;
            return this;
        }

        /**
         * Builds the {@link Activity} instance.
         *
         * @return a new immutable Activity
         * @throws IllegalArgumentException if validation fails
         */
        public Activity build() {
            return new Activity(type, state, details, url, timestamps, assets, party, secrets,
                    buttons.isEmpty() ? null : buttons, instance);
        }
    }
}
