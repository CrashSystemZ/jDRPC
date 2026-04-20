package fun.crashsystem.jdrpc.connection;

import fun.crashsystem.jdrpc.util.Platform;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Locates Discord IPC pipe paths on the current platform.
 * <p>
 * Discord opens up to 10 IPC pipes (indices 0-9):
 * <ul>
 *   <li><b>Windows</b>: {@code \\.\pipe\discord-ipc-{i}}</li>
 *   <li><b>macOS/Linux</b>: {@code {tmpdir}/discord-ipc-{i}}</li>
 * </ul>
 */
@Log4j2
final class PipeLocator {
    private static final List<String> TMP_ENV_VARS = List.of("XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP");
    private static final Path UNIX_TMP_DIR = resolveUnixPipeDir(
            System.getenv(),
            System.getProperty("java.io.tmpdir"),
            System.getProperty("user.name", "")
    );

    static final int MAX_PIPE_INDEX = 10;

    private PipeLocator() {}

    static List<String> locateAll() {
        List<String> paths = new ArrayList<>(MAX_PIPE_INDEX);
        for (int i = 0; i < MAX_PIPE_INDEX; i++) {
            paths.add(getPath(i));
        }
        return paths;
    }

    static String getPath(int index) {
        return switch (Platform.CURRENT) {
            case WINDOWS -> "\\\\.\\pipe\\discord-ipc-" + index;
            case MACOS, LINUX -> UNIX_TMP_DIR.resolve("discord-ipc-" + index).toString();
        };
    }

    static Path resolveUnixPipeDir(Map<String, String> env, String javaTmpDir, String currentUser) {
        for (String envVar : TMP_ENV_VARS) {
            Path candidate = validateUnixPipeDir(env.get(envVar), currentUser);
            if (candidate != null) {
                log.debug("Using {} = {} for pipe directory", envVar, candidate);
                return candidate;
            }
        }

        Path javaTmp = validateUnixPipeDir(javaTmpDir, currentUser);
        if (javaTmp != null) {
            log.debug("Using java.io.tmpdir = {} for pipe directory", javaTmp);
            return javaTmp;
        }

        Path fallback = validateUnixPipeDir("/tmp", currentUser);
        if (fallback != null) {
            return fallback;
        }

        return Path.of("/tmp").toAbsolutePath().normalize();
    }

    static Path validateUnixPipeDir(String candidate, String currentUser) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }

        try {
            return validateUnixPipeDir(Path.of(candidate), currentUser);
        } catch (InvalidPathException e) {
            log.debug("Ignoring invalid pipe directory path: {}", candidate, e);
            return null;
        }
    }

    static Path validateUnixPipeDir(Path candidate, String currentUser) {
        if (candidate == null) {
            return null;
        }

        try {
            if (Files.isSymbolicLink(candidate) || !Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }

            Path realPath = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!Files.isDirectory(realPath, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }

            if (supportsPosix(realPath) && !isTrustedOwner(realPath, currentUser)) {
                log.debug("Ignoring pipe directory not owned by trusted user: {}", realPath);
                return null;
            }

            return realPath;
        } catch (IOException e) {
            log.debug("Ignoring unusable pipe directory: {}", candidate, e);
            return null;
        }
    }

    private static boolean supportsPosix(Path path) {
        return path.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    private static boolean isTrustedOwner(Path path, String currentUser) throws IOException {
        if (currentUser == null || currentUser.isBlank()) {
            return true;
        }

        String owner = Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).getName();
        return currentUser.equals(owner) || "root".equals(owner);
    }
}
