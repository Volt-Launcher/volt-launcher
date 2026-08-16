package app.voltlauncher.setup.shortcut;

import java.nio.file.Path;

/** Registers the installed launcher with the desktop environment. */
public interface ShortcutInstaller {

    /**
     * Creates the platform's application-menu entry (Start Menu on Windows, the applications
     * directory on Linux, Launchpad/Finder on macOS).
     *
     * @return a human-readable description of what was created
     */
    String createApplicationEntry(Path executable, Path iconFile) throws Exception;

    /** Creates a desktop shortcut. */
    String createDesktopShortcut(Path executable, Path iconFile) throws Exception;
}
