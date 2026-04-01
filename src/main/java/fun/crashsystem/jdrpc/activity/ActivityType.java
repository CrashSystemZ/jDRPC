package fun.crashsystem.jdrpc.activity;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Discord activity types that determine how the presence is displayed.
 * <ul>
 *   <li>{@link #PLAYING} — "Playing {name}"</li>
 *   <li>{@link #STREAMING} — "Streaming {details}" (requires a Twitch/YouTube URL)</li>
 *   <li>{@link #LISTENING} — "Listening to {name}"</li>
 *   <li>{@link #WATCHING} — "Watching {name}"</li>
 *   <li>{@link #COMPETING} — "Competing in {name}"</li>
 * </ul>
 */
@Getter
@Accessors(fluent = true)
public enum ActivityType {
    PLAYING(0),
    STREAMING(1),
    LISTENING(2),
    WATCHING(3),
    COMPETING(5);

    private final int value;

    ActivityType(int value) {
        this.value = value;
    }

    /**
     * Resolves an {@link ActivityType} from its integer value.
     *
     * @throws IllegalArgumentException if the value doesn't match any known type
     */
    public static ActivityType fromValue(int value) {
        for (ActivityType t : values()) {
            if (t.value == value) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown activity type: " + value);
    }
}
