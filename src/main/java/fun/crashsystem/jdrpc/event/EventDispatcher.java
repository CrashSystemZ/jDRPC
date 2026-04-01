package fun.crashsystem.jdrpc.event;

import com.google.gson.JsonObject;
import fun.crashsystem.jdrpc.entity.User;
import fun.crashsystem.jdrpc.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Routes incoming Discord IPC events to registered {@link DiscordEventListener}s.
 */
@Slf4j
public final class EventDispatcher {

    private final List<DiscordEventListener> listeners = new CopyOnWriteArrayList<>();

    /** Adds a listener. */
    public void addListener(DiscordEventListener listener) {
        listeners.add(listener);
    }

    /** Removes a listener. */
    public void removeListener(DiscordEventListener listener) {
        listeners.remove(listener);
    }

    /** Dispatches the READY event with user data. */
    public void dispatchReady(User user) {
        notifyListeners("READY", listener -> listener.onReady(user));
    }

    /** Dispatches disconnect event. */
    public void dispatchDisconnect(Throwable cause) {
        notifyListeners("DISCONNECT", listener -> listener.onDisconnect(cause));
    }

    /** Dispatches close event. */
    public void dispatchClose() {
        notifyListeners("CLOSE", DiscordEventListener::onClose);
    }

    /**
     * Dispatches a raw event from the IPC read loop.
     */
    public void dispatch(String eventName, JsonObject data) {
        switch (eventName) {
            case "ACTIVITY_JOIN" -> JsonUtils.optString(data, "secret")
                    .ifPresent(secret -> notifyListeners(eventName, listener -> listener.onActivityJoin(secret)));

            case "ACTIVITY_SPECTATE" -> JsonUtils.optString(data, "secret")
                    .ifPresent(secret -> notifyListeners(eventName, listener -> listener.onActivitySpectate(secret)));

            case "ACTIVITY_JOIN_REQUEST" -> JsonUtils.optObject(data, "user").ifPresent(userJson -> {
                try {
                    User user = User.fromJson(userJson);
                    notifyListeners(eventName, listener -> listener.onActivityJoinRequest(user));
                } catch (RuntimeException e) {
                    log.warn("Failed to parse user payload for event {}", eventName, e);
                }
            });

            case "VOICE_CHANNEL_SELECT" -> {
                String channelId = JsonUtils.optString(data, "channel_id").orElse(null);
                String guildId = JsonUtils.optString(data, "guild_id").orElse(null);
                notifyListeners(eventName, listener -> listener.onVoiceChannelSelect(channelId, guildId));
            }

            case "VOICE_SETTINGS_UPDATE" -> {
                JsonObject eventData = data != null ? data : new JsonObject();
                notifyListeners(eventName, listener -> listener.onVoiceSettingsUpdate(eventData));
            }

            case "SPEAKING_START" -> JsonUtils.optString(data, "user_id")
                    .ifPresent(userId -> notifyListeners(eventName, listener -> listener.onSpeakingStart(userId)));

            case "SPEAKING_STOP" -> JsonUtils.optString(data, "user_id")
                    .ifPresent(userId -> notifyListeners(eventName, listener -> listener.onSpeakingStop(userId)));

            case "NOTIFICATION_CREATE" -> {
                if (data == null) {
                    return;
                }
                JsonUtils.optString(data, "channel_id").ifPresent(channelId -> {
                    JsonObject message = JsonUtils.optObject(data, "message").orElseGet(JsonObject::new);
                    notifyListeners(eventName, listener -> listener.onNotificationCreate(channelId, message));
                });
            }

            default -> log.debug("Unknown event type: {}", eventName);
        }
    }

    private void notifyListeners(String eventName, ListenerCallback callback) {
        for (DiscordEventListener listener : listeners) {
            try {
                callback.accept(listener);
            } catch (Exception e) {
                log.warn("Error in listener for event {}", eventName, e);
            }
        }
    }

    @FunctionalInterface
    private interface ListenerCallback {
        void accept(DiscordEventListener listener) throws Exception;
    }
}
