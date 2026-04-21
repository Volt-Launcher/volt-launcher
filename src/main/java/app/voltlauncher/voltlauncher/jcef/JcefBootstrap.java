package app.voltlauncher.voltlauncher.jcef;

import app.voltlauncher.voltlauncher.AppPaths;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.cef.CefApp;
import org.cef.CefSettings;
import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.CefInitializationException;
import me.friwi.jcefmaven.UnsupportedPlatformException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class JcefBootstrap {

    private static final String JCEF_VERSION = "1.0.69";
    private static final String DOWNLOAD_BASE =
            "https://github.com/jcefmaven/jcefbuild/releases/download/" + JCEF_VERSION + "/";

    private static final boolean IS_MAC =
            System.getProperty("os.name").toLowerCase().contains("mac");
    private static final boolean IS_WINDOWS =
            System.getProperty("os.name").toLowerCase().contains("win");

    private JcefBootstrap() {}

    public static Path prepareInstallation() throws Exception {
        Path installDir = AppPaths.jcefInstallDirectory();
        ensureInstalled(installDir);
        return installDir;
    }

    public static CefApp initialize(String[] args) throws Exception {
        Path installDir = AppPaths.jcefInstallDirectory();

        ensureInstalled(installDir);

        System.out.println("[JCEF] Installationsverzeichnis: " + installDir);
        return startCef(installDir, args);
    }

    private static void ensureInstalled(Path installDir) throws Exception {
        if (isInstalled(installDir)) {
            System.out.println("[JCEF] Bereits installiert.");
            return;
        }

        Files.createDirectories(installDir);

        String archiveName = getPlatformArchive();
        String url = DOWNLOAD_BASE + archiveName;

        System.out.println("[JCEF] Download: " + url);

        Path archive = installDir.resolve("_download" + (IS_WINDOWS ? ".zip" : ".tar.gz"));

        download(url, archive);

        System.out.println("[JCEF] Entpacke...");

        if (IS_WINDOWS) {
            extractZip(archive, installDir);
        } else {
            extractTarGz(archive, installDir);
        }

        Files.deleteIfExists(archive);

        System.out.println("[JCEF] Installation abgeschlossen.");
    }

    private static boolean isInstalled(Path installDir) {
        if (!Files.exists(installDir)) {
            return false;
        }

        try {
            findNativeLib(installDir, "jcef");
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static CefApp startCef(Path installDir, String[] args)
            throws IOException, UnsupportedPlatformException, InterruptedException, CefInitializationException {

        CefAppBuilder builder = new CefAppBuilder();
        builder.setInstallDir(installDir.toFile());
        builder.addJcefArgs(args);
        builder.setSkipInstallation(true);

        CefSettings settings = new CefSettings();

        settings.windowless_rendering_enabled = !IS_MAC;
        settings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_WARNING;

        Path logDir = AppPaths.logsDirectory();
        Files.createDirectories(logDir);

        settings.log_file = logDir.resolve("jcef.log")
                .toAbsolutePath()
                .toString();

        Path cacheDir = AppPaths.jcefCacheDirectory();
        Files.createDirectories(cacheDir);

        settings.root_cache_path = cacheDir.toAbsolutePath().toString();
        settings.cache_path = cacheDir.resolve("default")
                .toAbsolutePath()
                .toString();

        Path nativesDir = resolveNativesDir(installDir);
        Path resourcesDir = IS_MAC ? findMacResourcesDir(installDir) : nativesDir;

        settings.resources_dir_path = resourcesDir.toAbsolutePath().toString();

        Path locales = resourcesDir.resolve("locales");

        if (!Files.isDirectory(locales) && !IS_MAC) {
            locales = nativesDir.getParent().resolve("locales");
        }

        if (Files.isDirectory(locales)) {
            settings.locales_dir_path =
                    locales.toAbsolutePath().toString();
        }

        String helperName = IS_WINDOWS
                ? "jcef_helper.exe"
                : "jcef_helper";

        Path helper = nativesDir.resolve(helperName);

        if (!Files.exists(helper)) {
            try (var walk = Files.walk(installDir, 4)) {
                helper = walk
                        .filter(path -> path.getFileName().toString().equals(helperName))
                        .findFirst()
                        .orElse(helper);
            }
        }

        if (Files.exists(helper)) {
            if (!helper.toFile().setExecutable(true, false)) {
                System.out.println("[JCEF] Subprocess nicht als ausführbar markiert: " + helper);
            }

            settings.browser_subprocess_path =
                    helper.toAbsolutePath().toString();

            System.out.println("[JCEF] Subprocess: " + helper);
        }

        builder.getCefSettings().windowless_rendering_enabled = settings.windowless_rendering_enabled;
        builder.getCefSettings().log_severity = settings.log_severity;
        builder.getCefSettings().log_file = settings.log_file;
        builder.getCefSettings().root_cache_path = settings.root_cache_path;
        builder.getCefSettings().cache_path = settings.cache_path;
        builder.getCefSettings().resources_dir_path = settings.resources_dir_path;
        builder.getCefSettings().locales_dir_path = settings.locales_dir_path;
        builder.getCefSettings().browser_subprocess_path = settings.browser_subprocess_path;

        System.out.println("[JCEF] CefAppBuilder.build...");
        CefApp app = builder.build();
        System.out.println("[JCEF] CefAppBuilder.build abgeschlossen.");
        return app;
    }

    private static Path resolveNativesDir(Path installDir) throws IOException {
        Path jcef = findNativeLib(installDir, "jcef");

        System.out.println("[JCEF] libjcef: " + jcef);

        return jcef.getParent();
    }

    private static Path findNativeLib(Path installDir, String libname)
            throws IOException {

        String filename = nativeLibFilename(libname);

        try (var walk = Files.walk(installDir)) {
            return walk
                    .filter(path -> path.getFileName().toString().equals(filename))
                    .findFirst()
                    .orElseThrow(() -> new IOException(
                            "Library nicht gefunden: " + filename
                    ));
        }
    }

    private static Path findMacFrameworkBinary(Path installDir) throws IOException {
        try (var walk = Files.walk(installDir)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().contains("Chromium Embedded Framework.framework"))
                    .filter(path -> path.getFileName().toString().equals("Chromium Embedded Framework"))
                    .findFirst()
                    .orElseThrow(() -> new IOException("CEF-Framework nicht gefunden"));
        }
    }

    private static Path findMacResourcesDir(Path installDir) throws IOException {
        try (var walk = Files.walk(installDir)) {
            return walk
                    .filter(Files::isDirectory)
                    .filter(path -> path.toString().contains("jcef_app.app/Contents/Resources"))
                    .findFirst()
                    .orElseThrow(() -> new IOException("CEF-Resources nicht gefunden"));
        }
    }

    private static String nativeLibFilename(String libname) {
        if (IS_WINDOWS) {
            return libname + ".dll";
        }

        if (IS_MAC) {
            return "lib" + libname + ".dylib";
        }

        return "lib" + libname + ".so";
    }

    private static String getPlatformArchive() {
        String arch = System.getProperty("os.arch").toLowerCase();

        boolean arm = arch.contains("arm64")
                || arch.contains("aarch64");

        if (IS_MAC) {
            return arm
                    ? "macosx-arm64.tar.gz"
                    : "macosx-amd64.tar.gz";
        }

        if (IS_WINDOWS) {
            return arm
                    ? "windows-arm64.tar.gz"
                    : "windows-amd64.tar.gz";
        }

        return arm
                ? "linux-arm64.tar.gz"
                : "linux-amd64.tar.gz";
    }

    private static void download(String rawUrl, Path target) throws Exception {
        String url = rawUrl;

        for (int redirects = 0; redirects < 5; redirects++) {
            HttpURLConnection connection =
                    (HttpURLConnection) URI.create(url).toURL().openConnection();

            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty(
                    "User-Agent",
                    "VoltLauncher-JcefInstaller/1.0"
            );

            connection.setConnectTimeout(15000);
            connection.setReadTimeout(120000);

            int status = connection.getResponseCode();

            if (status == 301 || status == 302 || status == 307 || status == 308) {
                url = connection.getHeaderField("Location");
                connection.disconnect();
                continue;
            }

            try (InputStream in = connection.getInputStream();
                 OutputStream out = Files.newOutputStream(target)) {

                byte[] buffer = new byte[65536];
                int read;

                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            return;
        }

        throw new IOException("Zu viele Redirects: " + rawUrl);
    }

    private static void extractTarGz(Path archive, Path targetDir) throws Exception {
        try (InputStream fileIn = Files.newInputStream(archive);
             GzipCompressorInputStream gzip = new GzipCompressorInputStream(fileIn);
             TarArchiveInputStream tar = new TarArchiveInputStream(gzip)) {

            TarArchiveEntry entry;

            while ((entry = tar.getNextTarEntry()) != null) {
                Path destination = targetDir
                        .resolve(stripTopDir(entry.getName()))
                        .normalize();

                if (!destination.startsWith(targetDir)) {
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                    continue;
                }

                Files.createDirectories(destination.getParent());

                try (OutputStream out = Files.newOutputStream(destination)) {
                    tar.transferTo(out);
                }

                if ((entry.getMode() & 0b001_001_001) != 0) {
                    if (!destination.toFile().setExecutable(true, false)) {
                        System.out.println("[JCEF] Datei nicht als ausführbar markiert: " + destination);
                    }
                }
            }
        }
    }

    private static void extractZip(Path archive, Path targetDir) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;

            while ((entry = zip.getNextEntry()) != null) {
                Path destination = targetDir
                        .resolve(stripTopDir(entry.getName()))
                        .normalize();

                if (!destination.startsWith(targetDir)) {
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                    continue;
                }

                Files.createDirectories(destination.getParent());

                try (OutputStream out = Files.newOutputStream(destination)) {
                    zip.transferTo(out);
                }
            }
        }
    }

    private static String stripTopDir(String path) {
        int index = path.indexOf('/');

        if (index <= 0 || index == path.length() - 1) {
            return path;
        }

        return path.substring(index + 1);
    }
}