package app.voltlauncher.setup;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Command line options for the installer.
 *
 * <p>Everything has a sensible per-OS default, so {@code java -jar volt-setup.jar} performs a
 * complete per-user installation with no arguments.
 */
public record SetupOptions(
        Path payload,
        Path targetDirectory,
        boolean createStartMenuEntry,
        boolean createDesktopShortcut,
        boolean quiet) {

    public static final String APP_NAME = "VoltLauncher";
    public static final String EXECUTABLE_BASE_NAME = "volt-launcher";

    public static SetupOptions parse(String[] args) {
        Path payload = null;
        Path target = null;
        boolean startMenu = true;
        boolean desktop = false;
        boolean quiet = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--payload" -> payload = Path.of(requireValue(args, ++i, "--payload"));
                case "--target" -> target = Path.of(requireValue(args, ++i, "--target"));
                case "--no-start-menu" -> startMenu = false;
                case "--desktop-shortcut" -> desktop = true;
                case "--quiet" -> quiet = true;
                case "--help", "-h" -> {
                    printUsage();
                    throw new ExitRequested();
                }
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }

        return new SetupOptions(
                payload != null ? payload : defaultPayload(),
                target != null ? target.toAbsolutePath().normalize() : defaultTarget(),
                startMenu, desktop, quiet);
    }

    /** Thrown for {@code --help}, which is a successful exit rather than an error. */
    public static final class ExitRequested extends RuntimeException {
        public ExitRequested() {
            super(null, null, false, false);
        }
    }

    public static void printUsage() {
        System.out.println("""
                VoltLauncher setup

                Usage: java -jar volt-setup.jar [options]

                  --payload <path>       Directory containing the built launcher.
                                         Defaults to the directory holding this installer.
                  --target <path>        Where to install. Defaults to the per-user
                                         application directory for your platform.
                  --no-start-menu        Skip creating the Start Menu / application entry.
                  --desktop-shortcut     Also place a shortcut on the desktop.
                  --quiet                Only print errors.
                  -h, --help             Show this message.
                """);
    }

    /**
     * The directory the installer itself lives in — the layout produced by the build scripts puts
     * the installer next to the launcher binary and its {@code electron/} directory.
     */
    private static Path defaultPayload() {
        try {
            Path self = Path.of(SetupOptions.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            Path directory = java.nio.file.Files.isDirectory(self) ? self : self.getParent();
            return directory == null ? Path.of(".").toAbsolutePath().normalize() : directory;
        } catch (Exception e) {
            return Path.of(".").toAbsolutePath().normalize();
        }
    }

    /** Per-user install locations, chosen so the installer never needs elevation. */
    private static Path defaultTarget() {
        String home = System.getProperty("user.home");
        return switch (Platform.current()) {
            case WINDOWS -> {
                String localAppData = System.getenv("LOCALAPPDATA");
                yield (localAppData != null && !localAppData.isBlank()
                        ? Path.of(localAppData)
                        : Path.of(home, "AppData", "Local")).resolve("Programs").resolve(APP_NAME);
            }
            case MACOS -> Path.of(home, "Applications", APP_NAME + ".app");
            case LINUX -> Path.of(home, ".local", "share", EXECUTABLE_BASE_NAME);
        };
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return args[index];
    }

    /** The launcher executable's file name on this platform. */
    public static String executableName() {
        return Platform.current() == Platform.WINDOWS
                ? EXECUTABLE_BASE_NAME + ".exe"
                : EXECUTABLE_BASE_NAME;
    }

    public enum Platform {
        WINDOWS, MACOS, LINUX;

        public static Platform current() {
            String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (name.contains("win")) return WINDOWS;
            if (name.contains("mac") || name.contains("darwin")) return MACOS;
            return LINUX;
        }
    }
}
