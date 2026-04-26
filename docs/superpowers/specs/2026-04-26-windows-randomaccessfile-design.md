# Windows RandomAccessFile Transport Design

Date: 2026-04-26

## Goal

Replace the current Windows Discord IPC transport with a simple pure-Java
`RandomAccessFile` implementation, following the communication style used by
`jagrosh/DiscordIPC` while preserving jDRPC's existing public API and connection
architecture.

The main outcome is a smaller, easier to understand Windows transport with no
JNA or Win32 overlapped I/O dependency.

## Scope

In scope:

- Refactor `WindowsConnection` to use `RandomAccessFile`.
- Keep the existing `Connection` interface.
- Keep `ConnectionManager`, handshake, reconnect, command dispatch, and event
  handling behavior intact.
- Reuse `FrameReader` and `FrameWriter` for frame decoding and encoding.
- Remove the JNA dependency and related shadow-jar exclusions from Gradle.
- Update README wording so Windows is documented as plain named-pipe support,
  not JNA/overlapped I/O.

Out of scope:

- Rewriting the connection lifecycle around DiscordIPC's internal `PipeStatus`
  model.
- Changing the wire protocol, JSON payloads, or public API.
- Refactoring Unix domain socket support.
- Adding native Windows cancellation or overlapped I/O through another library.

## Architecture

`ConnectionManager` remains the owner of lifecycle behavior: pipe discovery,
handshake, background read loop, reconnect, disconnect, and shutdown.
`WindowsConnection` becomes a focused transport adapter.

The Windows transport opens the Discord IPC path with:

```java
new RandomAccessFile(path, "rw")
```

It exposes that file to the existing protocol helpers through small stream
adapters:

- `InputStream` delegates reads to `RandomAccessFile.read(...)`.
- `OutputStream` delegates writes to `RandomAccessFile.write(...)`.

`read()` calls `FrameReader.read(inputStream)`.
`write(Frame)` serializes writes with the existing `writeLock` and calls
`FrameWriter.write(outputStream, frame)`.

## Data Flow

The external flow stays unchanged:

1. `PipeLocator` returns Windows paths such as `\\.\pipe\discord-ipc-0`.
2. `ConnectionManager` creates a `WindowsConnection` through `ConnectionFactory`.
3. `ConnectionManager` sends the `HANDSHAKE` frame.
4. Discord replies with `READY`.
5. `ConnectionManager` starts the read loop and routes incoming frames.

Inside `WindowsConnection`, reads and writes are plain blocking file operations
on the named pipe. No native events, `OVERLAPPED` structures, or explicit Win32
handle management remain.

## Error Handling And Close Behavior

`WindowsConnection.close()` marks the transport closed and closes the underlying
`RandomAccessFile`.

If a blocked read or write fails because the file was closed or the pipe was
broken, the resulting `IOException` is allowed to propagate. `ConnectionManager`
already maps those failures into disconnect, reconnect, or closed-state behavior
based on the current configuration.

This deliberately accepts the simpler DiscordIPC-style model. The benefit is a
dependency-free and readable implementation. The trade-off is that shutdown and
blocking I/O cancellation are left to Java and Windows pipe behavior instead of
being controlled through Win32 overlapped I/O.

## Dependency Changes

Remove:

- `implementation("net.java.dev.jna:jna-platform:5.15.0")`
- shadow-jar exclusions for `jna` and `jna-platform`

Keep existing Gson, Commons Lang, Log4j API, and Lombok dependencies unchanged
unless compilation reveals a directly related issue.

## Documentation Changes

Update README text that currently claims Windows requires JNA and overlapped I/O.
The new wording should describe the project as:

- pure Java on macOS/Linux through JDK Unix domain sockets
- pure Java on Windows through named pipes opened with `RandomAccessFile`

Any developer-facing docs that describe Windows as `RandomAccessFile` are already
consistent with the target design and should only be edited if they contain
contradictory details.

## Testing

Implementation verification should include:

- `./gradlew build`
- Search for leftover JNA references in source, Gradle, and README files.
- Inspect `WindowsConnection` to confirm it uses `RandomAccessFile`,
  `FrameReader`, `FrameWriter`, and `writeLock`.

This environment is not expected to provide a real Windows named pipe, so runtime
validation against Discord on Windows remains a manual smoke test after the code
change is built.
