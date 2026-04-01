# jDRPC

`Java Discord Rich Presence` is a modern Java 17 library for Discord IPC / Rich Presence.
It is lightweight, cross-platform and has no native dependencies.

## Features

- Java 17+ support
- Unix domain sockets on Unix-like systems and named pipes on Windows
- Rich Presence activity updates (`SET_ACTIVITY`, clear activity)
- Optional auto-reconnect with exponential backoff
- RPC commands (`GET_GUILDS`, `GET_CHANNELS`, `SELECT_VOICE_CHANNEL`, etc.)
- Event listener support (`READY`, `ACTIVITY_JOIN_REQUEST`, speaking events, ...)
- Async API wrappers (`connectAsync`, `setActivityAsync`, ...)

## Requirements

- Java **17**+
- A running Discord desktop client
- A Discord application `CLIENT_ID` from the Developer Portal

## Installation

### Gradle

```kotlin
implementation("fun.crashsystem:jDRPC:1.0.0")
```

### Maven

```xml
<dependency>
    <groupId>fun.crashsystem</groupId>
    <artifactId>jDRPC</artifactId>
    <version>1.0.0</version>
</dependency>
```

If the artifact is not yet published in your environment, use local build publishing:

```bash
./gradlew publishToMavenLocal
```

Or add the source project as a local module:

- Git submodule or nested module:
```text
include(":jDRPC")
project(":jDRPC").projectDir = file("../jDRPC")
```

```kotlin
dependencies {
    implementation(project(":jDRPC"))
}
```

## Quick start

```java
import fun.crashsystem.jdrpc.DiscordIPC;
import fun.crashsystem.jdrpc.activity.Activity;
import fun.crashsystem.jdrpc.activity.ActivityType;

public class Example {
    public static void main(String[] args) {
        long clientId = 123456789012345678L;

        try (DiscordIPC discord = DiscordIPC.create(clientId)) {
            discord.connect();

            Activity activity = new Activity.Builder()
                    .setType(ActivityType.PLAYING)
                    .setState("Main Menu")
                    .setDetails("Building Discord rich presence")
                    .setLargeImage("game-logo", "jDRPC")
                    .setStartTimestamp(System.currentTimeMillis())
                    .addButton("GitHub", "https://github.com/")
                    .build();

            discord.setActivity(activity);

            System.out.println("Connected: " + discord.isConnected());
            System.out.println("State: " + discord.status());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
```

## Async usage

```java
DiscordIPC client = DiscordIPC.create(clientId);
client.connectAsync().thenRun(() -> System.out.println("Connected asynchronously"));

client.setActivityAsync(activity)
      .thenAccept(resp -> System.out.println("Activity sent"))
      .exceptionally(err -> {
          err.printStackTrace();
          return null;
      });
```

## Event handling + subscriptions

```java
import fun.crashsystem.jdrpc.activity.Activity;
import fun.crashsystem.jdrpc.event.DiscordEventListener;
import fun.crashsystem.jdrpc.event.EventType;
import fun.crashsystem.jdrpc.entity.User;

DiscordIPC client = DiscordIPC.create(123456789012345678L);

client.addListener(new DiscordEventListener() {
    @Override
    public void onReady(User user) {
        System.out.println("Connected as: " + user.username());
    }

    @Override
    public void onActivityJoinRequest(User user) {
        System.out.println("Join request from: " + user.username());
    }

    @Override
    public void onDisconnect(Throwable cause) {
        System.err.println("Disconnected: " + cause.getMessage());
    }

    @Override
    public void onClose() {
        System.out.println("Connection closed");
    }
});

client.connect();
client.subscribe(EventType.ACTIVITY_JOIN_REQUEST);
```

You can subscribe only to event types with `subscribable = true` in `EventType`.

## Useful command calls

```java
client.clearActivity();
client.currentUser().ifPresent(user -> System.out.println(user.username()));
client.connectedBuild().ifPresent(System.out::println);

client.getGuilds();
client.getChannels("123456789012345678");
client.selectVoiceChannel("123456789012345678", false);
client.sendActivityJoinInvite("123456789012345678");
```

## Configuration (optional)

```java
import fun.crashsystem.jdrpc.DiscordIPCConfig;
import fun.crashsystem.jdrpc.entity.DiscordBuild;

DiscordIPC client = DiscordIPC.create(
    DiscordIPCConfig.builder()
        .clientId(123456789012345678L)
        .preferredBuilds(java.util.List.of(DiscordBuild.STABLE))
        .reconnect(true)
        .maxReconnectAttempts(5)
        .heartbeatIntervalMs(15000)
        .build()
);
```

## Shutdown

- `disconnect()` — graceful IPC close
- `close()` — stop background threads and close connection
- If created manually with try-with-resources, `close()` is called automatically.

```java
client.disconnect();
```

## Build this project

```bash
./gradlew build          # build + run tests
./gradlew test           # run tests only
./gradlew publishToMavenLocal
```

## Notes

- This library currently uses `com.google.gson` for JSON serialization.
- Logging is done through `org.slf4j:slf4j-api`.
