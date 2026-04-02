# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

jDRPC is a Java 17+ library for Discord Rich Presence via the Discord IPC protocol. Package: `fun.crashsystem.jdrpc`.

## Build Commands

```bash
./gradlew build       # Compile and package
./gradlew javadoc     # Generate Javadoc
```

No tests exist yet. The build uses `java-library` plugin with Java 17 toolchain. Lombok is compile-only (annotation processing).

## Architecture

**Layered, event-driven design** with a single facade entry point:

- **`DiscordIPC`** - Main facade. Thread-safe, implements Closeable. All public API goes through here.
- **`DiscordIPCConfig`** - Immutable config record with builder pattern.

### Connection Layer (`connection/`)
- `ConnectionManager` - Lifecycle management, auto-reconnect with exponential backoff (1s-60s)
- `ConnectionState` - Sealed interface state machine: Disconnected -> Connecting -> Connected -> Reconnecting -> Failed/Closed
- `Connection` interface with platform implementations: `UnixConnection` (Java 17 Unix domain sockets) and `WindowsConnection` (named pipes via RandomAccessFile)
- `PipeLocator` / `PipePathProvider` - Discovers Discord IPC pipes 0-9 across builds (STABLE/PTB/CANARY)

### Protocol Layer (`protocol/`)
Wire format: `[OpCode:4B LE][Length:4B LE][JSON UTF-8]`. OpCodes: HANDSHAKE(0), FRAME(1), CLOSE(2), PING(3), PONG(4). Max frame size 64KB.

### Command Layer (`command/`)
`CommandExecutor` uses nonce-based request/response correlation with CompletableFuture. Optional token-bucket rate limiting. Default 10s timeout.

### Event Layer (`event/`)
`EventDispatcher` routes frames to `DiscordEventListener` callbacks (CopyOnWriteArrayList). Default empty implementations so consumers override only what they need.

### Models
- `activity/` - Activity record with builder, ActivityType enum, assets, buttons, party, timestamps, secrets
- `entity/` - User record, DiscordBuild enum
- `error/` - Exception hierarchy: `DiscordIPCException` -> `ConnectionException`, `CommandException`, `NoDiscordClientException`. `RpcErrorCode` enum.

## Threading

Two daemon threads: `jDRPC-worker` (I/O read loop, responds to Discord PINGs with PONGs), `jDRPC-async` (async API calls). Thread safety via AtomicReference, ReentrantLock on writes, ConcurrentHashMap for pending commands.

## Dependencies

- **Gson 2.11.0** - All JSON serialization (API scope)
- **SLF4J 2.0.13** - Logging facade, no impl bundled (API scope)
- **Commons Lang3 3.14.0** - StringUtils, ObjectUtils (API scope)
- **Lombok 1.18.36** - @Getter, @Slf4j, @Builder, @NonNull (compile-only)
