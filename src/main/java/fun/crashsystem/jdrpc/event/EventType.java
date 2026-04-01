package fun.crashsystem.jdrpc.event;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Discord IPC event types that can be subscribed to.
 */
@Getter
@Accessors(fluent = true)
public enum EventType {
    READY("READY", false),
    ERROR("ERROR", false),
    ACTIVITY_JOIN("ACTIVITY_JOIN", true),
    ACTIVITY_SPECTATE("ACTIVITY_SPECTATE", true),
    ACTIVITY_JOIN_REQUEST("ACTIVITY_JOIN_REQUEST", true),
    VOICE_CHANNEL_SELECT("VOICE_CHANNEL_SELECT", true),
    VOICE_SETTINGS_UPDATE("VOICE_SETTINGS_UPDATE", true),
    SPEAKING_START("SPEAKING_START", true),
    SPEAKING_STOP("SPEAKING_STOP", true),
    NOTIFICATION_CREATE("NOTIFICATION_CREATE", true),
    MESSAGE_CREATE("MESSAGE_CREATE", true),
    MESSAGE_UPDATE("MESSAGE_UPDATE", true),
    MESSAGE_DELETE("MESSAGE_DELETE", true);

    private final String value;
    private final boolean subscribable;

    EventType(String value, boolean subscribable) {
        this.value = value;
        this.subscribable = subscribable;
    }

    /** Resolves from wire value, or {@code null}. */
    public static EventType fromValue(String value) {
        for (EventType t : values()) {
            if (t.value.equals(value)) {
                return t;
            }
        }
        return null;
    }
}
