package fun.crashsystem.jdrpc;

import fun.crashsystem.jdrpc.entity.DiscordBuild;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.List;

import static fun.crashsystem.jdrpc.entity.DiscordBuild.*;

/**
 * Configuration for the {@link DiscordIPC} client.
 *
 * <pre>{@code
 * DiscordIPCConfig config = DiscordIPCConfig.builder()
 *     .clientId(1865300077429561342L)
 *     .preferredBuilds(List.of(DiscordBuild.STABLE))
 *     .reconnect(true)
 *     .build();
 * }</pre>
 */
@Getter
@Accessors(fluent = true)
@Builder
public final class DiscordIPCConfig {

    /**
     * The Discord application client ID from the Developer Portal.
     */
    private final long clientId;

    /**
     * Ordered list of preferred Discord client builds. Default: [STABLE, PTB, CANARY].
     */
    @Builder.Default
    private final List<DiscordBuild> preferredBuilds = List.of(STABLE, PTB, CANARY);

    /**
     * Whether to auto-reconnect on disconnection. Default: true.
     */
    @Builder.Default
    private final boolean reconnect = true;

    /**
     * Maximum reconnection attempts (0 = unlimited). Default: 0.
     */
    @Builder.Default
    private final int maxReconnectAttempts = 0;

    /**
     * Base delay for exponential backoff reconnect (ms). Default: 1000.
     */
    @Builder.Default
    private final long reconnectBaseDelayMs = 1000;

    /**
     * Maximum delay between reconnects (ms). Default: 60000.
     */
    @Builder.Default
    private final long reconnectMaxDelayMs = 60_000;

    /**
     * Interval between keep-alive PING messages (ms). Default: 15000.
     */
    @Builder.Default
    private final long heartbeatIntervalMs = 15_000;

    /**
     * Maximum time to wait for a command response (ms). Default: 10000.
     */
    @Builder.Default
    private final long commandTimeoutMs = 10_000;

    /**
     * Maximum commands sent per second (0 = disabled). Default: 0.
     */
    @Builder.Default
    private final int maxCommandsPerSecond = 0;
}
