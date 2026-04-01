package fun.crashsystem.jdrpc;

import com.google.gson.JsonObject;
import fun.crashsystem.jdrpc.activity.Activity;
import fun.crashsystem.jdrpc.command.CommandExecutor;
import fun.crashsystem.jdrpc.connection.Connection;
import fun.crashsystem.jdrpc.connection.ConnectionManager;
import fun.crashsystem.jdrpc.connection.ConnectionState;
import fun.crashsystem.jdrpc.entity.DiscordBuild;
import fun.crashsystem.jdrpc.entity.User;
import fun.crashsystem.jdrpc.error.ConnectionException;
import fun.crashsystem.jdrpc.event.DiscordEventListener;
import fun.crashsystem.jdrpc.event.EventDispatcher;
import fun.crashsystem.jdrpc.event.EventType;
import fun.crashsystem.jdrpc.util.ProcessId;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main entry point for the jDRPC Discord IPC library.
 * <p>
 * Provides a clean Java API for Discord Rich Presence, activity management,
 * event handling, and IPC command execution.
 *
 * <h3>Quick Start</h3>
 * <pre>{@code
 * DiscordIPC client = DiscordIPC.create(CLIENT_ID);
 * client.connect();
 *
 * client.setActivity(new Activity.Builder()
 *     .setType(ActivityType.PLAYING)
 *     .setDetails("In a match")
 *     .addButton("Play", "https://example.com")
 *     .build());
 *
 * // When done:
 * client.close();
 * }</pre>
 *
 * @see DiscordIPCConfig
 * @see Activity
 */
public final class DiscordIPC implements Closeable {
    private final CommandExecutor commandExecutor;
    private final EventDispatcher eventDispatcher;
    private final ConnectionManager connectionManager;
    private final ExecutorService asyncExecutor;

    private DiscordIPC(DiscordIPCConfig config) {
        this(
                new CommandExecutor(config.commandTimeoutMs(), config.maxCommandsPerSecond()),
                new EventDispatcher(),
                createAsyncExecutor(),
                config
        );
    }

    private DiscordIPC(CommandExecutor commandExecutor,
                       EventDispatcher eventDispatcher,
                       ExecutorService asyncExecutor,
                       DiscordIPCConfig config) {
        this(commandExecutor, eventDispatcher, new ConnectionManager(config, commandExecutor, eventDispatcher), asyncExecutor);
    }

    DiscordIPC(CommandExecutor commandExecutor,
               EventDispatcher eventDispatcher,
               ConnectionManager connectionManager,
               ExecutorService asyncExecutor) {
        this.commandExecutor = commandExecutor;
        this.eventDispatcher = eventDispatcher;
        this.connectionManager = connectionManager;
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * Creates a new {@link DiscordIPC} client with the given client ID.
     *
     * @param clientId the Discord application client ID from the Developer Portal
     * @return a new, unconnected client
     */
    public static DiscordIPC create(long clientId) {
        return new DiscordIPC(DiscordIPCConfig.builder().clientId(clientId).build());
    }

    /**
     * Creates a new {@link DiscordIPC} client with explicit configuration.
     *
     * @param config the configuration
     * @return a new, unconnected client
     */
    public static DiscordIPC create(DiscordIPCConfig config) {
        return new DiscordIPC(config);
    }

    /**
     * Connects to the Discord IPC pipe (blocking).
     * Tries pipe indices 0-9, preferring the configured builds.
     *
     * @throws fun.crashsystem.jdrpc.error.NoDiscordClientException if no Discord client is found
     * @throws ConnectionException                                  if the handshake fails
     */
    public void connect() {
        connectionManager.connect();
    }

    /**
     * Connects asynchronously, returning a {@link CompletableFuture}.
     *
     * @return a future that completes when connected
     */
    public CompletableFuture<Void> connectAsync() {
        return CompletableFuture.runAsync(this::connect, asyncExecutor);
    }

    /**
     * Disconnects from the Discord IPC pipe gracefully.
     */
    public void disconnect() {
        connectionManager.disconnect();
    }

    /**
     * Returns whether the client is currently connected to Discord.
     */
    public boolean isConnected() {
        return connectionManager.state() instanceof ConnectionState.Connected;
    }

    /**
     * Returns the current connection state.
     */
    public ConnectionState status() {
        return connectionManager.state();
    }

    /**
     * Returns the currently connected user, or {@link Optional#empty()}.
     */
    public Optional<User> currentUser() {
        return Optional.ofNullable(connectionManager.currentUser());
    }

    /**
     * Returns the connected Discord build, or {@link Optional#empty()}.
     */
    public Optional<DiscordBuild> connectedBuild() {
        return Optional.ofNullable(connectionManager.currentBuild());
    }

    /**
     * Sets the Rich Presence activity (blocking).
     *
     * @param activity the activity to display
     * @return the command response data
     * @throws ConnectionException                          if not connected
     * @throws fun.crashsystem.jdrpc.error.CommandException if Discord returns an error
     */
    public JsonObject setActivity(Activity activity) throws IOException {
        Connection conn = requireConnection();
        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessId.current());
        args.add("activity", activity.toJson());
        return commandExecutor.execute(conn, "SET_ACTIVITY", args, null);
    }

    /**
     * Sets activity asynchronously.
     *
     * @param activity the activity to display
     * @return a future with the response data
     */
    public CompletableFuture<JsonObject> setActivityAsync(Activity activity) {
        return supplyAsync(() -> setActivity(activity));
    }

    /**
     * Clears the current Rich Presence activity (blocking).
     *
     * @return the command response data
     * @throws ConnectionException if not connected
     */
    public JsonObject clearActivity() throws IOException {
        Connection conn = requireConnection();
        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessId.current());
        return commandExecutor.execute(conn, "SET_ACTIVITY", args, null);
    }

    /**
     * Clears activity asynchronously.
     *
     * @return a future with the response data
     */
    public CompletableFuture<JsonObject> clearActivityAsync() {
        return supplyAsync(this::clearActivity);
    }

    /**
     * Adds an event listener.
     *
     * @param listener the listener to add
     */
    public void addListener(DiscordEventListener listener) {
        eventDispatcher.addListener(listener);
    }

    /**
     * Removes an event listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(DiscordEventListener listener) {
        eventDispatcher.removeListener(listener);
    }

    /**
     * Subscribes to a Discord IPC event type (blocking).
     *
     * @param eventType the event type to subscribe to
     * @return the command response data
     * @throws IllegalArgumentException if the event is not subscribable
     * @throws IOException              if an I/O error occurs
     */
    public JsonObject subscribe(EventType eventType) throws IOException {
        return subscribe(eventType, null);
    }

    /**
     * Subscribes to a Discord IPC event type with optional channel scope.
     *
     * @param eventType the event type
     * @param channelId optional channel ID scope
     * @return the command response data
     * @throws IOException if an I/O error occurs
     */
    public JsonObject subscribe(EventType eventType, String channelId) throws IOException {
        if (!eventType.subscribable()) {
            throw new IllegalArgumentException(eventType.name() + " is not subscribable");
        }
        Connection conn = requireConnection();
        JsonObject args = null;
        if (channelId != null) {
            args = new JsonObject();
            args.addProperty("channel_id", channelId);
        }
        return commandExecutor.execute(conn, "SUBSCRIBE", args, eventType.value());
    }

    /**
     * Unsubscribes from an event type.
     *
     * @param eventType the event type to unsubscribe from
     * @param channelId optional channel ID scope
     * @return the command response data
     * @throws IOException if an I/O error occurs
     */
    public JsonObject unsubscribe(EventType eventType, String channelId) throws IOException {
        Connection conn = requireConnection();
        JsonObject args = null;
        if (channelId != null) {
            args = new JsonObject();
            args.addProperty("channel_id", channelId);
        }
        return commandExecutor.execute(conn, "UNSUBSCRIBE", args, eventType.value());
    }

    /**
     * Retrieves guild list. Requires rpc OAuth2 scope.
     *
     * @return the command response data containing guilds
     * @throws IOException         if an I/O error occurs
     * @throws ConnectionException if not connected
     */
    public JsonObject getGuilds() throws IOException {
        Connection conn = requireConnection();
        return commandExecutor.execute(conn, "GET_GUILDS", null, null);
    }

    /**
     * Retrieves a specific guild.
     *
     * @param guildId the guild ID to retrieve
     * @return the command response data containing the guild
     * @throws IOException         if an I/O error occurs
     * @throws ConnectionException if not connected
     */
    public JsonObject getGuild(String guildId) throws IOException {
        Connection conn = requireConnection();
        JsonObject args = new JsonObject();
        args.addProperty("guild_id", guildId);
        return commandExecutor.execute(conn, "GET_GUILD", args, null);
    }

    /**
     * Retrieves channels for a guild.
     *
     * @param guildId the guild ID to retrieve channels for
     * @return the command response data containing channels
     * @throws IOException         if an I/O error occurs
     * @throws ConnectionException if not connected
     */
    public JsonObject getChannels(String guildId) throws IOException {
        Connection conn = requireConnection();
        JsonObject args = new JsonObject();
        args.addProperty("guild_id", guildId);
        return commandExecutor.execute(conn, "GET_CHANNELS", args, null);
    }

    /**
     * Selects a voice channel.
     *
     * @param channelId the channel ID to select, or {@code null} to deselect
     * @param force     whether to force the channel selection
     * @return the command response data
     * @throws IOException         if an I/O error occurs
     * @throws ConnectionException if not connected
     */
    public JsonObject selectVoiceChannel(String channelId, boolean force) throws IOException {
        Connection conn = requireConnection();
        JsonObject args = new JsonObject();
        if (channelId != null) {
            args.addProperty("channel_id", channelId);
        }
        if (force) {
            args.addProperty("force", true);
        }
        return commandExecutor.execute(conn, "SELECT_VOICE_CHANNEL", args, null);
    }

    /**
     * Gets current voice settings.
     *
     * @return the command response data containing voice settings
     * @throws IOException         if an I/O error occurs
     * @throws ConnectionException if not connected
     */
    public JsonObject getVoiceSettings() throws IOException {
        Connection conn = requireConnection();
        return commandExecutor.execute(conn, "GET_VOICE_SETTINGS", null, null);
    }

    /**
     * Sends an activity join invite.
     *
     * @param userId the user ID to invite
     * @return the command response data
     * @throws IOException         if an I/O error occurs
     * @throws ConnectionException if not connected
     */
    public JsonObject sendActivityJoinInvite(String userId) throws IOException {
        Connection conn = requireConnection();
        JsonObject args = new JsonObject();
        args.addProperty("user_id", userId);
        return commandExecutor.execute(conn, "SEND_ACTIVITY_JOIN_INVITE", args, null);
    }

    /**
     * Closes an activity join request.
     *
     * @param userId the user ID whose request to close
     * @return the command response data
     * @throws IOException         if an I/O error occurs
     * @throws ConnectionException if not connected
     */
    public JsonObject closeActivityRequest(String userId) throws IOException {
        Connection conn = requireConnection();
        JsonObject args = new JsonObject();
        args.addProperty("user_id", userId);
        return commandExecutor.execute(conn, "CLOSE_ACTIVITY_REQUEST", args, null);
    }

    /**
     * Closes the IPC connection and shuts down all background threads.
     */
    @Override
    public void close() {
        connectionManager.shutdown();
        asyncExecutor.shutdownNow();
    }

    private Connection requireConnection() {
        return Optional.ofNullable(connectionManager.connection())
                .orElseThrow(() -> new ConnectionException("Not connected"));
    }

    private static ExecutorService createAsyncExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "jDRPC-async");
            t.setDaemon(true);
            return t;
        });
    }

    private <T> CompletableFuture<T> supplyAsync(IOSupplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }

    @FunctionalInterface
    private interface IOSupplier<T> {
        T get() throws IOException;
    }
}
