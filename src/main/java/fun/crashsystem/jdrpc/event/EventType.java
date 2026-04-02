package fun.crashsystem.jdrpc.event;

import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.Optional;

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
    ACTIVITY_JOIN_REQUEST("ACTIVITY_JOIN_REQUEST", true);

    private final String value;
    private final boolean subscribable;

    EventType(String value, boolean subscribable) {
        this.value = value;
        this.subscribable = subscribable;
    }

    /** Resolves from wire value, or {@code null}. */
    public static Optional<EventType> fromValue(String value) {
        for (EventType t : values()) {
            if (t.value.equals(value)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
