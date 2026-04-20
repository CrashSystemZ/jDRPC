package fun.crashsystem.jdrpc.event;

import com.google.gson.JsonObject;
import fun.crashsystem.jdrpc.entity.User;
import fun.crashsystem.jdrpc.util.JsonUtils;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Routes incoming Discord IPC events to registered {@link DiscordEventListener}s.
 */
@Log4j2
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

    /** Dispatches an error event (Discord returned an error on a command). */
    public void dispatchError(int errorCode, String message) {
        notifyListeners("ERROR", listener -> listener.onError(errorCode, message));
    }

    /** Dispatches disconnect event with error code and message from the CLOSE frame. */
    public void dispatchDisconnect(int errorCode, String message) {
        notifyListeners("DISCONNECT", listener -> listener.onDisconnect(errorCode, message));
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
