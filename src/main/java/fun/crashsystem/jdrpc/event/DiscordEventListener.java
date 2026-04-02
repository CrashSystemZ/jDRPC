package fun.crashsystem.jdrpc.event;

import fun.crashsystem.jdrpc.entity.User;

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
    /** Fired when Discord returns an error in response to a command. */
    default void onError(int errorCode, String message) {}
    /** Fired on unexpected disconnect. */
    default void onDisconnect(int errorCode, String message) {}
    /** Fired on clean close. */
    default void onClose() {}
}
