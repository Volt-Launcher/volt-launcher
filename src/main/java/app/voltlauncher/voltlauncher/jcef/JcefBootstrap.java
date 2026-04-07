package app.voltlauncher.voltlauncher.jcef;

import app.voltlauncher.voltlauncher.AppPaths;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.cef.CefApp;
import org.cef.CefSettings;
import org.cef.SystemBootstrap;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class JcefBootstrap {

    private static final String JCEF_VERSION = "1.0.69";
    private static final String DOWNLOAD_BASE =
            "https://github.com/jcefmaven/jcefbuild/releases/download/" + JCEF_VERSION + "/";

    private JcefBootstrap() {}

    public static CefApp initialize(String[] args) throws Exception {
        Path installDir = AppPaths.jcefInstallDirectory();
        ensureInstalled(installDir);
        Path nativesDir = resolveNativesDir(installDir);
        System.out.println("[JCEF] Natives-Verzeichnis: " + nativesDir);
        loadNatives(installDir, nativesDir);
        return startCef(installDir, nativesDir, args);
    }

    private static void ensureInstalled(Path installDir) throws Exception {
        if (isInstalled(installDir)) {
            System.out.println("[JCEF] Bereits installiert: " + installDir);
            return;
        }
        Files.createDirectories(installDir);
        String platform = detectPlatform();
        String filename = platform + (isWindows() ? ".zip" : ".tar.gz");
        String url = DOWNLOAD_BASE + filename;
        System.out.println("[JCEF] Lade herunter: " + url);
        Path archive = installDir.resolve("_download" + (isWindows() ? ".zip" : ".tar.gz"));
        download(url, archive);
        System.out.println("[JCEF] Entpacke...");
        if (isWindows()) {
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
        } catch (IOException _) {
            return false;
        }
    }

    // ──────────────────────────── Natives-Dir ─────────────────────────────

    /**
     * Ermittelt das Verzeichnis, in dem libjcef.so tatsächlich liegt.
     * jcefbuild-Strukturen variieren je nach Version:
     *   v1.x:    lib/linux64/libjcef.so
     *   v143.x:  bin/libjcef.so  oder  libjcef.so (Root)
     */
    private static Path resolveNativesDir(Path installDir) throws IOException {
        return findNativeLib(installDir, "jcef").getParent();
    }

    // ──────────────────────────── Native Loading ──────────────────────────

    private static void loadNatives(Path installDir, Path nativesDir) {
        // libcef.so muss VOR libjcef.so geladen werden
        Path cefLib = nativesDir.resolve(nativeLibFilename("cef"));
        if (Files.exists(cefLib)) {
            System.load(cefLib.toAbsolutePath().toString());
            System.out.println("[JCEF] Geladen: " + cefLib.getFileName());
        }

        // SystemBootstrap.setLoader überschreibt den java-cef Library-Loader.
        // Alle System.loadLibrary("jcef")-Aufrufe von CefApp werden hierher geleitet.
        SystemBootstrap.setLoader(libname -> {
            Path lib = nativesDir.resolve(nativeLibFilename(libname));
            if (!Files.exists(lib)) {
                // Fallback: rekursiv suchen falls Struktur abweicht
                try {
                    lib = findNativeLib(installDir, libname);
                } catch (IOException e) {
                    throw new UnsatisfiedLinkError("[JCEF] Library nicht gefunden: " + libname);
                }
            }
            System.load(lib.toAbsolutePath().toString());
            System.out.println("[JCEF] Geladen: " + lib.getFileName());
        });
    }

    // ──────────────────────────── CEF Start ───────────────────────────────

    private static CefApp startCef(Path installDir, Path nativesDir, String[] args) throws IOException {
        CefApp.startup(args);

        CefSettings settings = new CefSettings();
        settings.windowless_rendering_enabled = false;
        settings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_DISABLE;
        // resources_dir_path zeigt auf das Verzeichnis mit libjcef.so,
        // dort liegen auch icudtl.dat, cef.pak, devtools_resources.pak etc.
        settings.resources_dir_path = nativesDir.toAbsolutePath().toString();

        // locales-Ordner: direkt neben oder innerhalb von nativesDir
        Path localesInNatives = nativesDir.resolve("locales");
        if (Files.isDirectory(localesInNatives)) {
            settings.locales_dir_path = localesInNatives.toAbsolutePath().toString();
        } else {
            // Fallback: eine Ebene höher suchen (z.B. lib/locales statt lib/linux64/locales)
            Path localesSibling = nativesDir.getParent().resolve("locales");
            if (Files.isDirectory(localesSibling)) {
                settings.locales_dir_path = localesSibling.toAbsolutePath().toString();
            }
        }

        // jcef_helper: direkt neben libjcef.so oder rekursiv
        String helperName = isWindows() ? "jcef_helper.exe" : "jcef_helper";
        Path helper = nativesDir.resolve(helperName);
        if (!Files.exists(helper)) {
            try (var walk = Files.walk(installDir, 3)) {
                helper = walk
                        .filter(p -> p.getFileName().toString().equals(helperName))
                        .findFirst()
                        .orElse(helper);
            }
        }
        if (Files.exists(helper)) {
            helper.toFile().setExecutable(true, false);
            settings.browser_subprocess_path = helper.toAbsolutePath().toString();
            System.out.println("[JCEF] subprocess: " + helper);
        }

        return CefApp.getInstance(settings);
    }

    // ──────────────────────────── Helpers ─────────────────────────────────

    private static Path findNativeLib(Path installDir, String libname) throws IOException {
        String filename = nativeLibFilename(libname);
        try (var walk = Files.walk(installDir)) {
            return walk
                    .filter(p -> p.getFileName().toString().equals(filename))
                    .findFirst()
                    .orElseThrow(() -> new IOException(
                            "Native Library nicht gefunden: " + filename + " in " + installDir));
        }
    }

    private static String nativeLibFilename(String libname) {
        if (isWindows()) {
            return libname + ".dll";
        }
        if (isMac()) {
            return "lib" + libname + ".dylib";
        }
        return "lib" + libname + ".so";
    }

    private static String detectPlatform() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        boolean arm = arch.contains("aarch64") || arch.contains("arm64");
        if (os.contains("win")) {
            return arm ? "windows-arm64" : "windows-amd64";
        }
        if (os.contains("mac")) {
            return arm ? "macos-aarch64" : "macos-x86_64";
        }
        if (arm) {
            return "linux-aarch64";
        }
        return "linux-amd64";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static boolean isMac() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }

    // ──────────────────────────── Download ────────────────────────────────

    private static void download(String rawUrl, Path target) throws Exception {
        String url = rawUrl;
        for (int redirects = 0; redirects < 5; redirects++) {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "TheLauncherProject-JcefInstaller/1.0");
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(120_000);
            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == 307 || status == 308) {
                url = conn.getHeaderField("Location");
                conn.disconnect();
                continue;
            }
            long total = conn.getContentLengthLong();
            long downloaded = 0;
            long lastPrinted = -1;
            try (InputStream in = conn.getInputStream();
                 OutputStream out = Files.newOutputStream(target)) {
                byte[] buf = new byte[65_536];
                int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                    downloaded += read;
                    if (total > 0) {
                        long pct = downloaded * 100 / total;
                        // Nur bei Änderung drucken – verhindert die riesigen Log-Floods
                        if (pct != lastPrinted) {
                            System.out.printf("[JCEF] Download %d%% (%d / %d MB)%n",
                                    pct, downloaded / 1_048_576, total / 1_048_576);
                            lastPrinted = pct;
                        }
                    }
                }
            }
            return;
        }
        throw new IOException("[JCEF] Zu viele Redirects beim Download von: " + rawUrl);
    }

    // ──────────────────────────── Extraktion ─────────────────────────────

    private static void extractTarGz(Path archive, Path targetDir) throws Exception {
        try (InputStream fi = Files.newInputStream(archive);
             GzipCompressorInputStream gz = new GzipCompressorInputStream(fi);
             TarArchiveInputStream tar = new TarArchiveInputStream(gz)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                Path dest = targetDir.resolve(stripTopDir(entry.getName())).normalize();
                if (!dest.startsWith(targetDir)) {
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(dest);
                    continue;
                }
                Files.createDirectories(dest.getParent());
                try (OutputStream out = Files.newOutputStream(dest)) {
                    tar.transferTo(out);
                }
                if ((entry.getMode() & 0b001_001_001) != 0) {
                    dest.toFile().setExecutable(true, false);
                }
            }
        }
    }

    private static void extractZip(Path archive, Path targetDir) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path dest = targetDir.resolve(stripTopDir(entry.getName())).normalize();
                if (!dest.startsWith(targetDir)) {
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(dest);
                    continue;
                }
                Files.createDirectories(dest.getParent());
                try (OutputStream out = Files.newOutputStream(dest)) {
                    zip.transferTo(out);
                }
            }
        }
    }

    private static String stripTopDir(String name) {
        int idx = name.indexOf('/');
        if (idx <= 0 || idx == name.length() - 1) {
            return name;
        }
        return name.substring(idx + 1);
    }
}