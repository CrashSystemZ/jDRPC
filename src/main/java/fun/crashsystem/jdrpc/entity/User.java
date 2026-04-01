package fun.crashsystem.jdrpc.entity;

import com.google.gson.JsonObject;
import fun.crashsystem.jdrpc.util.JsonUtils;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a Discord user received from IPC events and commands.
 * <p>
 * Supports both the legacy discriminator system (User#1234) and the
 * modern username system (where discriminator is "0").
 */
@Accessors(fluent = true)
public record User(String id, String username, String discriminator, String globalName, String avatar, boolean bot) {

    /**
     * Constructs a User from individual fields.
     */
    public User(String id, String username, String discriminator, String globalName, String avatar, boolean bot) {
        this.id = id;
        this.username = username;
        this.discriminator = StringUtils.defaultIfBlank(discriminator, "0");
        this.globalName = globalName;
        this.avatar = avatar;
        this.bot = bot;
    }

    /**
     * Parses a User from a Discord IPC JSON object.
     *
     * @param json the JSON object containing user data
     * @return a new User instance
     */
    public static User fromJson(JsonObject json) {
        return new User(
                JsonUtils.getString(json, "id", "0"),
                JsonUtils.getString(json, "username", "Unknown"),
                JsonUtils.getString(json, "discriminator", "0"),
                JsonUtils.optString(json, "global_name").orElse(null),
                JsonUtils.optString(json, "avatar").orElse(null),
                JsonUtils.getBoolean(json, "bot", false)
        );
    }

    /**
     * Returns the global name as Optional.
     */
    public Optional<String> optionalGlobalName() {
        return Optional.ofNullable(globalName);
    }

    /**
     * Returns the avatar hash as Optional.
     */
    public Optional<String> optionalAvatar() {
        return Optional.ofNullable(avatar);
    }

    /**
     * Returns the user's snowflake ID as a long.
     */
    public long idLong() {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("User ID is not a numeric Discord snowflake: " + id, e);
        }
    }

    /**
     * Returns the effective display name (globalName if available, otherwise username).
     */
    public String displayName() {
        return ObjectUtils.defaultIfNull(StringUtils.trimToNull(globalName), username);
    }

    /**
     * Returns the username (alias for {@link #username()}, for migration compatibility).
     */
    public String getName() {
        return username;
    }

    /**
     * Returns the full tag: "Username#1234" (legacy) or "Username" (modern).
     */
    public String tag() {
        return "0".equals(discriminator) ? username : username + "#" + discriminator;
    }

    /**
     * Returns the URL to the user's custom avatar, or {@link Optional#empty()} if using default.
     * Animated avatars (starting with "a_") are returned as GIF.
     */
    public Optional<String> avatarUrl() {
        return optionalAvatar().map(a -> {
            String ext = a.startsWith("a_") ? "gif" : "png";
            return "https://cdn.discordapp.com/avatars/" + id + "/" + a + "." + ext;
        });
    }

    /**
     * Returns the URL to the user's default avatar.
     */
    public String defaultAvatarUrl() {
        int index;
        try {
            if ("0".equals(discriminator)) {
                index = (int) ((idLong() >> 22) % 6);
            } else {
                index = Integer.parseInt(discriminator) % 5;
            }
        } catch (RuntimeException e) {
            index = 0;
        }
        return "https://cdn.discordapp.com/embed/avatars/" + index + ".png";
    }

    /**
     * Returns the effective avatar URL (custom if available, otherwise default).
     */
    public String effectiveAvatarUrl() {
        return avatarUrl().orElseGet(this::defaultAvatarUrl);
    }

    /**
     * Returns a Discord mention string: {@code <@ID>}.
     */
    public String asMention() {
        return "<@" + id + ">";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User u)) {
            return false;
        }
        return Objects.equals(id, u.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "User:" + tag() + "(" + id + ")";
    }
}
