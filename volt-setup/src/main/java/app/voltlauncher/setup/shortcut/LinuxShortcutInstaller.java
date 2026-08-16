package app.voltlauncher.setup.shortcut;

import app.voltlauncher.setup.SetupOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/**
 * Writes freedesktop.org {@code .desktop} entries, which every major Linux desktop reads for its
 * application menu.
 */
public final class LinuxShortcutInstaller implements ShortcutInstaller {

    private static final String DESKTOP_FILE_NAME = SetupOptions.EXECUTABLE_BASE_NAME + ".desktop";

    @Override
    public String createApplicationEntry(Path executable, Path iconFile) throws Exception {
        Path applications = xdgDataHome().resolve("applications");
        Path entry = applications.resolve(DESKTOP_FILE_NAME);
        Path installedIcon = installIcon(iconFile);
        write(entry, executable, installedIcon);
        refreshDesktopDatabase(applications);
        return "Application menu entry: " + entry;
    }

    @Override
    public String createDesktopShortcut(Path executable, Path iconFile) throws Exception {
        Path desktopDir = desktopDirectory();
        Files.createDirectories(desktopDir);
        Path entry = desktopDir.resolve(DESKTOP_FILE_NAME);
        write(entry, executable, installIcon(iconFile));
        // Newer GNOME/KDE only offer to run a desktop file that is marked executable.
        makeExecutable(entry);
        return "Desktop shortcut: " + entry;
    }

    private void write(Path entry, Path executable, Path iconFile) throws IOException {
        Files.createDirectories(entry.getParent());
        String content = """
                [Desktop Entry]
                Type=Application
                Name=%s
                GenericName=Minecraft Launcher
                Comment=A modern Minecraft launcher
                Exec=%s
                Icon=%s
                Terminal=false
                Categories=Game;
                StartupWMClass=%s
                """.formatted(
                SetupOptions.APP_NAME,
                escapeExec(executable.toString()),
                iconFile != null ? iconFile.toString() : SetupOptions.EXECUTABLE_BASE_NAME,
                SetupOptions.APP_NAME);
        Files.writeString(entry, content, StandardCharsets.UTF_8);
    }

    /** Places the icon where the hicolor theme expects it so the menu entry renders properly. */
    private Path installIcon(Path iconFile) throws IOException {
        if (iconFile == null || !Files.exists(iconFile)) return null;
        Path iconDir = xdgDataHome().resolve("icons/hicolor/256x256/apps");
        Files.createDirectories(iconDir);
        Path target = iconDir.resolve(SetupOptions.EXECUTABLE_BASE_NAME + ".png");
        Files.copy(iconFile, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private void refreshDesktopDatabase(Path applications) {
        try {
            new ProcessBuilder("update-desktop-database", applications.toString())
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException ignored) {
            // Optional: most desktops pick new entries up on their own.
        }
    }

    private void makeExecutable(Path entry) {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(entry);
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(entry, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Not a POSIX filesystem; the entry still works from the menu.
        }
    }

    private Path xdgDataHome() {
        String xdg = System.getenv("XDG_DATA_HOME");
        return xdg != null && !xdg.isBlank()
                ? Path.of(xdg)
                : Path.of(System.getProperty("user.home"), ".local", "share");
    }

    /** Resolves the localised desktop directory when the user has one configured. */
    private Path desktopDirectory() {
        Path home = Path.of(System.getProperty("user.home"));
        Path config = home.resolve(".config/user-dirs.dirs");
        if (Files.isReadable(config)) {
            try {
                for (String line : Files.readAllLines(config, StandardCharsets.UTF_8)) {
                    if (!line.startsWith("XDG_DESKTOP_DIR=")) continue;
                    String value = line.substring("XDG_DESKTOP_DIR=".length()).trim()
                            .replaceAll("^\"|\"$", "")
                            .replace("$HOME", home.toString());
                    if (!value.isBlank()) return Path.of(value);
                }
            } catch (IOException ignored) {
                // Fall back to the conventional location.
            }
        }
        return home.resolve("Desktop");
    }

    /** Exec keys are space-separated, so a path with spaces has to be quoted. */
    private String escapeExec(String command) {
        return command.contains(" ") ? "\"" + command + "\"" : command;
    }
}
