package app.voltlauncher.setup;

import app.voltlauncher.setup.shortcut.LinuxShortcutInstaller;
import app.voltlauncher.setup.shortcut.MacShortcutInstaller;
import app.voltlauncher.setup.shortcut.ShortcutInstaller;
import app.voltlauncher.setup.shortcut.WindowsShortcutInstaller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Cross-platform installer for VoltLauncher.
 *
 * <p>Copies a built launcher into a per-user application directory and registers it with the
 * desktop environment, so it shows up in the Windows Start Menu, the Linux application menu or
 * macOS Launchpad. Nothing here needs administrator rights.
 */
public final class VoltSetup {

    /** Files that belong to the installer itself and must not be copied into the installation. */
    private static final Set<String> PAYLOAD_EXCLUSIONS = Set.of("volt-setup.jar", "setup.exe");

    private final SetupOptions options;

    private VoltSetup(SetupOptions options) {
        this.options = options;
    }

    public static void main(String[] args) {
        try {
            SetupOptions options = SetupOptions.parse(args);
            new VoltSetup(options).run();
        } catch (SetupOptions.ExitRequested ignored) {
            // --help already printed usage.
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage() + System.lineSeparator());
            SetupOptions.printUsage();
            System.exit(2);
        } catch (Exception e) {
            System.err.println("Installation failed: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
            System.exit(1);
        }
    }

    private void run() throws Exception {
        info("VoltLauncher setup");
        info("  Source: " + options.payload());
        info("  Target: " + options.targetDirectory());

        Path executableSource = locateExecutable(options.payload());
        Path installedExecutable = installPayload(executableSource);
        Path icon = locateIcon(options.targetDirectory());

        info("");
        info("Installed to " + options.targetDirectory());

        List<String> created = createShortcuts(installedExecutable, icon);
        created.forEach(entry -> info("  " + entry));

        info("");
        info("Done. Launch VoltLauncher from your application menu, or run:");
        info("  " + installedExecutable);
    }

    // ── payload ───────────────────────────────────────────────────────────────

    /**
     * Finds the launcher executable inside the payload. The build scripts emit it at the top level,
     * but a nested directory (e.g. an unpacked archive) is searched as a convenience.
     */
    private Path locateExecutable(Path payload) throws IOException {
        if (!Files.isDirectory(payload)) {
            throw new IllegalArgumentException("Payload directory does not exist: " + payload);
        }

        String executableName = SetupOptions.executableName();
        Path direct = payload.resolve(executableName);
        if (Files.isRegularFile(direct)) return direct;

        try (Stream<Path> walk = Files.walk(payload, 3)) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(executableName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Could not find '" + executableName + "' under " + payload
                                    + ". Build the launcher first, or pass --payload."));
        }
    }

    /**
     * Copies the payload into the target directory. The launcher expects its {@code electron/}
     * directory to sit next to the executable, so the executable's whole parent directory is
     * copied rather than the file alone.
     */
    private Path installPayload(Path executableSource) throws IOException {
        Path source = executableSource.getParent();
        Path target = options.targetDirectory();
        Files.createDirectories(target);

        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.sorted().toList()) {
                Path relative = source.relativize(path);
                if (relative.toString().isEmpty()) continue;
                if (PAYLOAD_EXCLUSIONS.contains(path.getFileName().toString())) continue;

                Path destination = target.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                    continue;
                }
                Files.createDirectories(destination.getParent());
                Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }

        Path installedExecutable = target.resolve(executableSource.getFileName().toString());
        makeExecutable(installedExecutable);
        makeElectronExecutable(target);
        return installedExecutable;
    }

    /** The bundled Electron runtime ships its own binaries, whose executable bit must survive the copy. */
    private void makeElectronExecutable(Path target) throws IOException {
        Path electron = target.resolve("electron");
        if (!Files.isDirectory(electron)) return;

        try (Stream<Path> walk = Files.walk(electron)) {
            walk.filter(Files::isRegularFile)
                    .filter(this::looksExecutable)
                    .forEach(this::makeExecutableQuietly);
        }
    }

    private boolean looksExecutable(Path path) {
        String name = path.getFileName().toString();
        return name.equals("voltlauncher")
                || name.equals("VoltLauncher")
                || name.endsWith(".sh")
                || name.startsWith("chrome-sandbox")
                || name.startsWith("chrome_crashpad_handler");
    }

    private Path locateIcon(Path installDirectory) {
        for (String candidate : List.of(
                "voltlauncher.png", "icon.png", "favicon.ico", "voltlauncher.ico", "voltlauncher.icns")) {
            Path icon = installDirectory.resolve(candidate);
            if (Files.isRegularFile(icon)) return icon;
        }
        return null;
    }

    // ── shortcuts ─────────────────────────────────────────────────────────────

    private List<String> createShortcuts(Path executable, Path icon) {
        ShortcutInstaller installer = switch (SetupOptions.Platform.current()) {
            case WINDOWS -> new WindowsShortcutInstaller();
            case MACOS -> new MacShortcutInstaller();
            case LINUX -> new LinuxShortcutInstaller();
        };

        List<String> created = new ArrayList<>();
        if (options.createStartMenuEntry()) {
            try {
                created.add(installer.createApplicationEntry(executable, icon));
            } catch (Exception e) {
                // A failed shortcut must not undo an otherwise successful installation.
                created.add("Could not create the application menu entry: " + e.getMessage());
            }
        }
        if (options.createDesktopShortcut()) {
            try {
                created.add(installer.createDesktopShortcut(executable, icon));
            } catch (Exception e) {
                created.add("Could not create the desktop shortcut: " + e.getMessage());
            }
        }
        return created;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void makeExecutable(Path path) {
        if (!Files.exists(path)) return;
        makeExecutableQuietly(path);
    }

    private void makeExecutableQuietly(Path path) {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            permissions.add(PosixFilePermission.GROUP_EXECUTE);
            permissions.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows has no POSIX permissions and does not need them.
        }
    }

    private void info(String message) {
        if (!options.quiet()) System.out.println(message);
    }

    /** Kept for callers that want to remove a previous installation before reinstalling. */
    static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
