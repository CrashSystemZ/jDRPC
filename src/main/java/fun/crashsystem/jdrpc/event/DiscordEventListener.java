package fun.crashsystem.jdrpc.event;

import fun.crashsystem.jdrpc.entity.User;
import com.google.gson.JsonObject;

/**
 * Listener interface for Discord IPC events.
 * All methods have default empty implementations — override only what you need.
 */
public interface DiscordEventListener {
    /** Fired when IPC connection is established and handshake complete. */
    default void onReady(User user) {}
    /** Fired when another user accepts a Join invite. */
    default void onActivityJoin(String secret) {}
    /** Fired when another user clicks Spectate. */
    default void onActivitySpectate(String secret) {}
    /** Fired when another user sends an Ask to Join request. */
    default void onActivityJoinRequest(User user) {}
    /** Fired when a voice channel is selected. */
    default void onVoiceChannelSelect(String channelId, String guildId) {}
    /** Fired when voice settings are updated. */
    default void onVoiceSettingsUpdate(JsonObject data) {}
    /** Fired when a user starts speaking. */
    default void onSpeakingStart(String userId) {}
    /** Fired when a user stops speaking. */
    default void onSpeakingStop(String userId) {}
    /** Fired on notification. */
    default void onNotificationCreate(String channelId, JsonObject message) {}
    /** Fired on unexpected disconnect. */
    default void onDisconnect(Throwable cause) {}
    /** Fired on clean close. */
    default void onClose() {}
}
