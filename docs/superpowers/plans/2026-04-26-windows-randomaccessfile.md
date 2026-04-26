# Windows RandomAccessFile Transport Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Windows Discord IPC transport with a simple pure-Java `RandomAccessFile` implementation and remove the JNA dependency.

**Architecture:** `ConnectionManager` remains responsible for lifecycle, handshake, reconnect, and the background read loop. `WindowsConnection` becomes a focused transport adapter that opens the named pipe with `RandomAccessFile`, reads frames through `FrameReader`, writes frames through `FrameWriter`, and serializes writes with `writeLock`.

**Tech Stack:** Java 17, Gradle Kotlin DSL, Gson, Log4j API, Lombok, JUnit Jupiter for focused transport tests.

---

## File Structure

- Modify: `build.gradle.kts`
  - Remove JNA from runtime dependencies and shadow exclusions.
  - Add JUnit Jupiter test dependency and enable the JUnit Platform.
- Modify: `src/main/java/fun/crashsystem/jdrpc/connection/WindowsConnection.java`
  - Replace Win32/JNA overlapped I/O with `RandomAccessFile`.
  - Keep package-private constructor, `Connection` methods, and write locking.
- Create: `src/test/java/fun/crashsystem/jdrpc/connection/WindowsConnectionTest.java`
  - Verify read, write, and close behavior using temporary files.
- Modify: `README.md`
  - Remove JNA and overlapped I/O wording.
  - Document pure Java Windows named-pipe support through `RandomAccessFile`.
- Inspect only: `CLAUDE.md`
  - It already describes `WindowsConnection` as `RandomAccessFile`; do not edit unless implementation reveals contradictory wording.
- Do not touch: `AGENTS.md`
  - It is currently untracked user workspace state.

## Task 1: Add A Failing Windows Transport Test

**Files:**
- Modify: `build.gradle.kts`
- Create: `src/test/java/fun/crashsystem/jdrpc/connection/WindowsConnectionTest.java`

- [ ] **Step 1: Add JUnit Jupiter to the Gradle test configuration**

In `build.gradle.kts`, replace the current `dependencies` block with:

```kotlin
dependencies {
    api("com.google.code.gson:gson:2.11.0")
    api("org.apache.commons:commons-lang3:3.18.0")

    implementation("org.apache.logging.log4j:log4j-api:2.24.1")
    implementation("net.java.dev.jna:jna-platform:5.15.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")

    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")
}
```

Add this block after `tasks.javadoc { ... }` and before `tasks.named<ShadowJar>("shadowJar") { ... }`:

```kotlin
tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Create the failing transport tests**

Create `src/test/java/fun/crashsystem/jdrpc/connection/WindowsConnectionTest.java`:

```java
package fun.crashsystem.jdrpc.connection;

import com.google.gson.JsonObject;
import fun.crashsystem.jdrpc.protocol.Frame;
import fun.crashsystem.jdrpc.protocol.OpCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WindowsConnectionTest {
    @TempDir
    Path tempDir;

    @Test
    void writeEncodesFrameToRandomAccessFile() throws Exception {
        Path pipeFile = tempDir.resolve("discord-ipc-0");

        try (WindowsConnection connection = new WindowsConnection(pipeFile.toString())) {
            JsonObject data = new JsonObject();
            data.addProperty("cmd", "PING");

            connection.write(new Frame(OpCode.FRAME, data));
        }

        byte[] bytes = Files.readAllBytes(pipeFile);
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(OpCode.FRAME.code(), header.getInt());

        int payloadLength = header.getInt();
        assertEquals(bytes.length - 8, payloadLength);
        assertEquals(
                "{\"cmd\":\"PING\"}",
                new String(bytes, 8, payloadLength, StandardCharsets.UTF_8)
        );
    }

    @Test
    void readDecodesFrameFromRandomAccessFile() throws Exception {
        Path pipeFile = tempDir.resolve("discord-ipc-1");
        JsonObject data = new JsonObject();
        data.addProperty("evt", "READY");
        Files.write(pipeFile, new Frame(OpCode.FRAME, data).encode());

        try (WindowsConnection connection = new WindowsConnection(pipeFile.toString())) {
            Frame frame = connection.read();

            assertEquals(OpCode.FRAME, frame.op());
            assertEquals("READY", frame.data().get("evt").getAsString());
        }
    }

    @Test
    void closeMarksConnectionClosedAndRejectsWrites() throws Exception {
        Path pipeFile = tempDir.resolve("discord-ipc-2");
        WindowsConnection connection = new WindowsConnection(pipeFile.toString());

        connection.close();

        assertFalse(connection.isOpen());

        JsonObject data = new JsonObject();
        data.addProperty("cmd", "PING");
        IOException thrown = assertThrows(
                IOException.class,
                () -> connection.write(new Frame(OpCode.FRAME, data))
        );
        assertEquals("Connection is closed", thrown.getMessage());
    }
}
```

- [ ] **Step 3: Run the targeted test and verify it fails against the current implementation**

Run:

```bash
./gradlew test --tests fun.crashsystem.jdrpc.connection.WindowsConnectionTest
```

Expected: FAIL. On non-Windows systems the current JNA implementation should fail before it can open a temp file because it tries to load or call `kernel32`. On Windows it should still fail because the current implementation uses `CreateFile(..., OPEN_EXISTING, FILE_FLAG_OVERLAPPED, ...)` instead of creating a plain temp file the way `RandomAccessFile` does.

- [ ] **Step 4: Commit the failing test**

```bash
git add build.gradle.kts src/test/java/fun/crashsystem/jdrpc/connection/WindowsConnectionTest.java
git commit -m "test: cover Windows RandomAccessFile transport"
```

## Task 2: Replace WindowsConnection With RandomAccessFile

**Files:**
- Modify: `src/main/java/fun/crashsystem/jdrpc/connection/WindowsConnection.java`
- Test: `src/test/java/fun/crashsystem/jdrpc/connection/WindowsConnectionTest.java`

- [ ] **Step 1: Replace the full WindowsConnection implementation**

Replace `src/main/java/fun/crashsystem/jdrpc/connection/WindowsConnection.java` with:

```java
package fun.crashsystem.jdrpc.connection;

import fun.crashsystem.jdrpc.protocol.Frame;
import fun.crashsystem.jdrpc.protocol.FrameReader;
import fun.crashsystem.jdrpc.protocol.FrameWriter;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReentrantLock;

/**
 * IPC connection for Windows using named pipes via {@link RandomAccessFile}.
 */
@Log4j2
final class WindowsConnection implements Connection {
    private final ReentrantLock writeLock = new ReentrantLock();
    private final RandomAccessFile file;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private volatile boolean closed;

    WindowsConnection(String path) throws IOException {
        this.file = new RandomAccessFile(path, "rw");
        this.inputStream = new InputStream() {
            @Override
            public int read() throws IOException {
                byte[] b = new byte[1];
                int n = read(b, 0, 1);
                return n == -1 ? -1 : b[0] & 0xFF;
            }

            @Override
            public int read(byte @NonNull [] b, int off, int len) throws IOException {
                if (closed) {
                    return -1;
                }
                return file.read(b, off, len);
            }
        };
        this.outputStream = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                file.write(b);
            }

            @Override
            public void write(byte @NonNull [] b, int off, int len) throws IOException {
                file.write(b, off, len);
            }

            @Override
            public void flush() throws IOException {
                file.getFD().sync();
            }
        };
        log.debug("Connected to Windows pipe: {}", path);
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public Frame read() throws IOException {
        return FrameReader.read(inputStream);
    }

    @Override
    public void write(Frame frame) throws IOException {
        ensureOpen();
        writeLock.lock();
        try {
            ensureOpen();
            FrameWriter.write(outputStream, frame);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        file.close();
        log.debug("Windows connection closed");
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Connection is closed");
        }
    }
}
```

- [ ] **Step 2: Run the targeted WindowsConnection test and verify it passes**

Run:

```bash
./gradlew test --tests fun.crashsystem.jdrpc.connection.WindowsConnectionTest
```

Expected: PASS.

- [ ] **Step 3: Run the full test task**

Run:

```bash
./gradlew test
```

Expected: PASS.

- [ ] **Step 4: Commit the transport refactor**

```bash
git add src/main/java/fun/crashsystem/jdrpc/connection/WindowsConnection.java
git commit -m "refactor: use RandomAccessFile for Windows IPC"
```

## Task 3: Remove JNA From Gradle

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Remove the JNA dependency and shadow exclusions**

In `build.gradle.kts`, replace the `dependencies` block with:

```kotlin
dependencies {
    api("com.google.code.gson:gson:2.11.0")
    api("org.apache.commons:commons-lang3:3.18.0")

    implementation("org.apache.logging.log4j:log4j-api:2.24.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")

    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")
}
```

In the `tasks.named<ShadowJar>("shadowJar") { ... }` block, replace the nested `dependencies { ... }` block with:

```kotlin
    dependencies {
        exclude(dependency("org.apache.logging.log4j:log4j-api:.*"))
    }
```

- [ ] **Step 2: Run the test task**

Run:

```bash
./gradlew test
```

Expected: PASS.

- [ ] **Step 3: Confirm Gradle no longer references JNA**

Run:

```bash
rg -n "jna|JNA|Kernel32|WinBase|WinNT" build.gradle.kts src/main/java
```

Expected: no output.

- [ ] **Step 4: Commit the dependency cleanup**

```bash
git add build.gradle.kts
git commit -m "build: remove JNA dependency"
```

## Task 4: Update README Windows Wording

**Files:**
- Modify: `README.md`
- Inspect: `CLAUDE.md`

- [ ] **Step 1: Replace the overview Windows sentence**

In `README.md`, replace:

```markdown
It is lightweight and cross-platform: pure Java on macOS/Linux (Unix domain sockets via JDK 17+),
and JNA on Windows (overlapped I/O on named pipes, required for safe concurrent read/write).
```

with:

```markdown
It is lightweight and cross-platform: pure Java on macOS/Linux (Unix domain sockets via JDK 17+),
and pure Java on Windows (named pipes via `RandomAccessFile`).
```

- [ ] **Step 2: Replace the feature bullet**

In `README.md`, replace:

```markdown
- Unix domain sockets on macOS/Linux (pure JDK); overlapped named pipes on Windows (via JNA)
```

with:

```markdown
- Unix domain sockets on macOS/Linux (pure JDK); named pipes on Windows (pure Java)
```

- [ ] **Step 3: Remove the Windows JNA note**

In `README.md`, delete this bullet:

```markdown
- On Windows the library requires `net.java.dev.jna:jna-platform` at runtime to open Discord's named pipe with `FILE_FLAG_OVERLAPPED`. This is needed because synchronous pipe I/O serializes concurrent reads and writes on the same handle at the kernel level and deadlocks the read loop. macOS/Linux do not need JNA.
```

- [ ] **Step 4: Inspect developer docs for contradictions**

Run:

```bash
rg -n "JNA|overlapped|RandomAccessFile|WindowsConnection" CLAUDE.md README.md
```

Expected: `README.md` contains no `JNA` or `overlapped`; `CLAUDE.md` may mention `RandomAccessFile`, which matches the new implementation.

- [ ] **Step 5: Commit the README update**

```bash
git add README.md
git commit -m "docs: document pure Java Windows IPC"
```

## Task 5: Final Verification

**Files:**
- Verify: `build.gradle.kts`
- Verify: `src/main/java/fun/crashsystem/jdrpc/connection/WindowsConnection.java`
- Verify: `README.md`
- Verify: `docs/superpowers/specs/2026-04-26-windows-randomaccessfile-design.md`

- [ ] **Step 1: Run the full build**

Run:

```bash
./gradlew build
```

Expected: PASS.

- [ ] **Step 2: Confirm source and public docs no longer reference JNA or overlapped I/O**

Run:

```bash
rg -n "jna|JNA|overlapped|Kernel32|WinBase|WinNT|CancelIoEx|ReadFile|WriteFile|FILE_FLAG_OVERLAPPED" build.gradle.kts README.md src/main/java
```

Expected: no output.

- [ ] **Step 3: Confirm WindowsConnection has the intended shape**

Run:

```bash
rg -n "RandomAccessFile|FrameReader|FrameWriter|writeLock|overlapped|Kernel32|Memory" src/main/java/fun/crashsystem/jdrpc/connection/WindowsConnection.java
```

Expected: output includes `RandomAccessFile`, `FrameReader`, `FrameWriter`, and `writeLock`; output does not include `overlapped`, `Kernel32`, or `Memory`.

- [ ] **Step 4: Inspect final git status**

Run:

```bash
git status --short
```

Expected: no modified tracked files. The pre-existing untracked `AGENTS.md` may still appear and should remain untouched.

## Self-Review

- Spec coverage: Task 2 implements `RandomAccessFile`; Task 3 removes JNA from Gradle; Task 4 updates README; Task 5 verifies source/docs/build. Public API, `ConnectionManager`, Unix support, and wire protocol are not modified.
- Completeness scan: every code-changing step includes exact snippets and commands.
- Type consistency: `WindowsConnection`, `Frame`, `FrameReader`, `FrameWriter`, `OpCode`, `RandomAccessFile`, and `writeLock` match the existing codebase names and package layout.
