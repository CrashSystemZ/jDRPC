# jDRPC

[![JitPack](https://jitpack.io/v/CrashSystemZ/jDRPC.svg)](https://jitpack.io/#CrashSystemZ/jDRPC)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![License: MIT](https://img.shields.io/github/license/CrashSystemZ/jDRPC)](LICENSE)

`Java Discord Rich Presence` is a modern Java 17 library for Discord IPC / Rich Presence.
It is lightweight and cross-platform: pure Java on macOS/Linux (Unix domain sockets via JDK 17+),
and pure Java on Windows (named pipes via `RandomAccessFile`).

<img width="280" height="206" alt="preview" src="https://github.com/user-attachments/assets/072f8e6d-6dcf-4735-acc4-6566389c38fa" />

## Features

- Java 17+ support
- Unix domain sockets on macOS/Linux (pure JDK); named pipes on Windows (pure Java)
- Rich Presence activity updates (set / clear activity)
- Activity buttons, party info with privacy, timestamps, assets, secrets
- Event listener support (ready, disconnect, error, activity join/spectate/join request)
- Optional auto-reconnect with exponential backoff
- Async API wrappers (`connectAsync`, `setActivityAsync`, ...)

## Requirements

- Java **17**+
- A running Discord desktop client
- A Discord application `CLIENT_ID` from the Developer Portal

## Installation

The library is hosted on [JitPack](https://jitpack.io). You **must** add the JitPack repository to your project first.

### Gradle (Groovy DSL)

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.CrashSystemZ:jDRPC:v1.0.0'
}
```

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.CrashSystemZ:jDRPC:v1.0.0")
}
```

### Maven

Add the JitPack repository to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Then add the dependency:

```xml
<dependency>
    <groupId>com.github.CrashSystemZ</groupId>
    <artifactId>jDRPC</artifactId>
    <version>v1.0.0</version>
</dependency>
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
                    .setStartTimestamp(System.currentTimeMillis() / 1000L)
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
    public void onError(int errorCode, String message) {
        System.err.println("Error: " + message + " (code " + errorCode + ")");
    }

    @Override
    public void onDisconnect(int errorCode, String message) {
        System.err.println("Disconnected: " + message + " (code " + errorCode + ")");
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
        .build()
);
```

## Shutdown

- `disconnect()` -- graceful IPC close
- `close()` -- stop background threads and close connection
- If created with try-with-resources, `close()` is called automatically.

## Build this project

```bash
./gradlew build       # compile and package
./gradlew javadoc     # generate Javadoc
```

## Notes

- Timestamps are in **Unix epoch seconds**, not milliseconds.
- JSON serialization uses `com.google.gson` (bundled).
- Logging uses `org.apache.logging.log4j:log4j-api` as a `compileOnly` dependency — the consumer provides a Log4j2 implementation at runtime.
