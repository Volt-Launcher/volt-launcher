package app.voltlauncher.game.install;

import app.voltlauncher.core.util.HttpFetcher;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;

final class LibraryInstaller {

    private static final String DEFAULT_MAVEN_REPO = "https://libraries.minecraft.net/";

    private final HttpFetcher http;

    LibraryInstaller(HttpFetcher http) {
        this.http = http;
    }

    void downloadLibraries(JSONObject meta, Path libsDir, Path nativesDir, LinkedHashSet<String> cp) throws Exception {
        JSONArray libraries = meta.optJSONArray("libraries");
        if (libraries == null) return;
        for (int i = 0; i < libraries.length(); i++) {
            JSONObject lib = libraries.optJSONObject(i);
            if (lib == null) continue;
            if (!OsRules.isAllowedByRules(lib.optJSONArray("rules"))) continue;

            JSONObject downloads = lib.optJSONObject("downloads");
            if (downloads == null) {
                downloadMavenLibrary(lib, libsDir, cp);
                continue;
            }
            downloadArtifact(lib, downloads, libsDir, nativesDir, cp);
            downloadNativeClassifier(lib, downloads, libsDir, nativesDir);
        }
    }

    /**
     * Downloads the libraries an install profile's processors need. They must exist on disk but
     * are deliberately kept off the game's classpath, hence the throwaway classpath set.
     */
    void downloadInstallProfileLibraries(JSONObject installProfile, Path libsDir, Path nativesDir) throws Exception {
        JSONArray installLibraries = installProfile.optJSONArray("libraries");
        if (installLibraries == null || installLibraries.isEmpty()) return;
        JSONObject pseudoMeta = new JSONObject().put("libraries", new JSONArray(installLibraries.toString()));
        downloadLibraries(pseudoMeta, libsDir, nativesDir, new LinkedHashSet<>());
    }

    private void downloadArtifact(JSONObject lib, JSONObject downloads, Path libsDir, Path nativesDir,
                                  LinkedHashSet<String> cp) throws Exception {
        JSONObject artifact = downloads.optJSONObject("artifact");
        if (artifact == null) return;

        String relativePath = artifact.optString("path", "");
        if (relativePath.isBlank()) {
            relativePath = mavenRelativePath(lib.optString("name", ""));
            if (relativePath == null) return;
        }
        Path target = libsDir.resolve(relativePath);

        String url = artifact.optString("url", "").trim();
        if (url.isBlank()) {
            // Forge and NeoForge declare their patched/remapped artifacts with an empty URL:
            // the install profile's processors produce them locally. They belong on the classpath
            // regardless, and the processors run before launch, so a missing file here is fine.
            cp.add(target.toString());
            return;
        }

        http.download(url, target, artifact.optString("sha1", ""));
        cp.add(target.toString());
        extractIfNativeBundle(lib, target, nativesDir);
    }

    /**
     * Modern versions ship natives as ordinary library artifacts named {@code *-natives-<os>}.
     * They work from the classpath, but extracting them keeps {@code java.library.path} usable
     * for anything that resolves native libraries the old way.
     */
    private void extractIfNativeBundle(JSONObject lib, Path artifact, Path nativesDir) throws Exception {
        String name = lib.optString("name", "").toLowerCase(Locale.ROOT);
        if (!name.contains(":natives-") && !name.contains("-natives-")) return;
        if (!Files.exists(artifact)) return;
        NativeExtractor.extractNative(artifact, nativesDir, lib.optJSONObject("extract"));
    }

    private void downloadNativeClassifier(JSONObject lib, JSONObject downloads, Path libsDir, Path nativesDir)
            throws Exception {
        JSONObject classifiers = downloads.optJSONObject("classifiers");
        if (classifiers == null || !lib.has("natives")) return;
        String classifier = OsRules.resolveNativeClassifier(lib.getJSONObject("natives"));
        if (classifier == null || !classifiers.has(classifier)) return;

        JSONObject nativeDownload = classifiers.getJSONObject(classifier);
        Path archive = libsDir.resolve(nativeDownload.getString("path"));
        http.download(nativeDownload.getString("url"), archive, nativeDownload.optString("sha1", ""));
        NativeExtractor.extractNative(archive, nativesDir, lib.optJSONObject("extract"));
    }

    /** Libraries without a {@code downloads} block are plain Maven coordinates. */
    private void downloadMavenLibrary(JSONObject lib, Path libsDir, LinkedHashSet<String> cp) throws Exception {
        String gav = lib.optString("name", "").trim();
        String relativePath = mavenRelativePath(gav);
        if (relativePath == null) return;

        Path target = libsDir.resolve(relativePath);
        String baseRepo = lib.optString("url", DEFAULT_MAVEN_REPO).trim();
        if (baseRepo.isBlank()) baseRepo = DEFAULT_MAVEN_REPO;
        if (!baseRepo.endsWith("/")) baseRepo = baseRepo + "/";

        try {
            http.download(baseRepo + relativePath, target, "");
        } catch (Exception e) {
            // Loader profiles occasionally list an artifact their own processors generate; a
            // download failure is only fatal if nothing produced the file either.
            if (!Files.exists(target)) throw e;
        }
        cp.add(target.toString());
    }

    /** {@code group:artifact:version[:classifier][@ext]} → {@code group/path/artifact-version.jar}. */
    static String mavenRelativePath(String gav) {
        if (gav == null || gav.isBlank()) return null;
        String[] parts = gav.split(":");
        if (parts.length < 3) return null;
        MavenCoord coord = MavenCoord.parse(parts);
        String fileName = coord.artifact() + "-" + coord.version()
                + (coord.classifier().isBlank() ? "" : "-" + coord.classifier())
                + "." + coord.extension();
        return coord.group().replace('.', '/') + "/" + coord.artifact() + "/" + coord.version() + "/" + fileName;
    }

    private record MavenCoord(String group, String artifact, String version, String classifier, String extension) {

        static MavenCoord parse(String[] parts) {
            String group = parts[0];
            String artifact = parts[1];
            String version = parts[2];
            String classifier = parts.length >= 4 ? parts[3] : "";
            String extension = "jar";

            int versionAt = version.indexOf('@');
            if (versionAt >= 0) {
                String ext = version.substring(versionAt + 1).trim();
                if (!ext.isBlank()) extension = ext;
                version = version.substring(0, versionAt);
            }
            int classifierAt = classifier.indexOf('@');
            if (classifierAt >= 0) {
                String ext = classifier.substring(classifierAt + 1).trim();
                if (!ext.isBlank()) extension = ext;
                classifier = classifier.substring(0, classifierAt);
            }
            return new MavenCoord(group, artifact, version, classifier, extension);
        }
    }
}
