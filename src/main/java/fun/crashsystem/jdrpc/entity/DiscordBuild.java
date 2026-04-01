package fun.crashsystem.jdrpc.entity;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Discord client build variants.
 */
@Getter
@Accessors(fluent = true)
public enum DiscordBuild {
    /** Main stable release. */
    STABLE("//discord.com/api"),
    /** Public Test Build. */
    PTB("//ptb.discord.com/api"),
    /** Canary — bleeding edge. */
    CANARY("//canary.discord.com/api"),
    /** Development build (internal). */
    DEVELOPMENT("//discordapp.com/api"),
    /** Wildcard — accept any build. */
    ANY(null);

    private final String endpoint;

    DiscordBuild(String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * Resolves a build from the API endpoint string returned during IPC handshake.
     * Returns {@link #ANY} if not recognized.
     */
    public static DiscordBuild fromEndpoint(String endpoint) {
        if (endpoint == null) {
            return ANY;
        }
        for (DiscordBuild b : values()) {
            if (b.endpoint != null && endpoint.contains(b.endpoint)) {
                return b;
            }
        }
        if (endpoint.contains("canary")) {
            return CANARY;
        }
        if (endpoint.contains("ptb")) {
            return PTB;
        }
        if (endpoint.contains("discord")) {
            return STABLE;
        }
        return ANY;
    }
}
