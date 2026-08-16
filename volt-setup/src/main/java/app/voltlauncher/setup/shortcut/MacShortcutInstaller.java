package app.voltlauncher.setup.shortcut;

import app.voltlauncher.setup.SetupOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/**
 * macOS has no separate "shortcut" concept: an app in {@code ~/Applications} is what Launchpad and
 * Spotlight index. The installer therefore ensures the payload is a well-formed {@code .app}
 * bundle, and a desktop "shortcut" becomes a symlink to it.
 */
public final class MacShortcutInstaller implements ShortcutInstaller {

    @Override
    public String createApplicationEntry(Path executable, Path iconFile) throws Exception {
        Path bundle = bundleRootOf(executable);
        if (bundle == null) {
            return "Installed to " + executable.getParent()
                    + " (not an .app bundle, so it will not appear in Launchpad)";
        }
        ensureBundleMetadata(bundle, executable, iconFile);
        touchBundle(bundle);
        return "Application bundle: " + bundle;
    }

    @Override
    public String createDesktopShortcut(Path executable, Path iconFile) throws Exception {
        Path bundle = bundleRootOf(executable);
        Path target = bundle != null ? bundle : executable;
        Path link = Path.of(System.getProperty("user.home"), "Desktop", SetupOptions.APP_NAME);

        Files.createDirectories(link.getParent());
        Files.deleteIfExists(link);
        Files.createSymbolicLink(link, target);
        return "Desktop alias: " + link;
    }

    /** Walks up from the executable to the enclosing {@code *.app} directory, if there is one. */
    private Path bundleRootOf(Path executable) {
        for (Path current = executable.toAbsolutePath(); current != null; current = current.getParent()) {
            if (current.getFileName() != null && current.getFileName().toString().endsWith(".app")) {
                return current;
            }
        }
        return null;
    }

    /**
     * Writes the {@code Info.plist} Launch Services needs to treat the directory as an
     * application. Existing bundles from a signed build are left untouched.
     */
    private void ensureBundleMetadata(Path bundle, Path executable, Path iconFile) throws IOException {
        Path contents = bundle.resolve("Contents");
        Path plist = contents.resolve("Info.plist");
        if (Files.exists(plist)) return;

        Files.createDirectories(contents.resolve("MacOS"));
        Files.createDirectories(contents.resolve("Resources"));

        String iconEntry = "";
        if (iconFile != null && Files.exists(iconFile)) {
            Path icon = contents.resolve("Resources").resolve(SetupOptions.EXECUTABLE_BASE_NAME + ".icns");
            Files.copy(iconFile, icon, StandardCopyOption.REPLACE_EXISTING);
            iconEntry = "    <key>CFBundleIconFile</key>\n    <string>"
                    + SetupOptions.EXECUTABLE_BASE_NAME + "</string>\n";
        }

        String content = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>CFBundleName</key>
                    <string>%s</string>
                    <key>CFBundleDisplayName</key>
                    <string>%s</string>
                    <key>CFBundleIdentifier</key>
                    <string>app.voltlauncher</string>
                    <key>CFBundleVersion</key>
                    <string>0.2.0</string>
                    <key>CFBundleShortVersionString</key>
                    <string>0.2.0</string>
                    <key>CFBundlePackageType</key>
                    <string>APPL</string>
                    <key>CFBundleExecutable</key>
                    <string>%s</string>
                %s    <key>LSMinimumSystemVersion</key>
                    <string>11.0</string>
                    <key>NSHighResolutionCapable</key>
                    <true/>
                </dict>
                </plist>
                """.formatted(
                SetupOptions.APP_NAME, SetupOptions.APP_NAME,
                executable.getFileName().toString(), iconEntry);

        Files.writeString(plist, content, StandardCharsets.UTF_8);
        makeExecutable(executable);
    }

    /** Nudges Launch Services to re-read a bundle whose contents just changed. */
    private void touchBundle(Path bundle) {
        try {
            Files.setLastModifiedTime(bundle, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
            new ProcessBuilder(
                    "/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister",
                    "-f", bundle.toString())
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException ignored) {
            // lsregister is best effort; Finder notices the bundle on its own soon enough.
        }
    }

    private void makeExecutable(Path path) {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            permissions.add(PosixFilePermission.GROUP_EXECUTE);
            permissions.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Not a POSIX filesystem.
        }
    }
}
