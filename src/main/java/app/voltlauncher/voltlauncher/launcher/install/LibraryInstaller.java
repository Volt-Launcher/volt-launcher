package app.voltlauncher.voltlauncher.launcher.install;

import app.voltlauncher.voltlauncher.util.HttpFetcher;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.LinkedHashSet;

final class LibraryInstaller {

    private final HttpFetcher http;

    LibraryInstaller(HttpFetcher http) {
        this.http = http;
    }

    void downloadLibraries(JSONObject meta, Path libsDir, Path nativesDir, LinkedHashSet<String> cp) throws Exception {
        JSONArray libraries = meta.optJSONArray("libraries");
        if (libraries == null) return;
        for (int i = 0; i < libraries.length(); i++) {
            JSONObject lib = libraries.getJSONObject(i);
            if (!OsRules.isAllowedByRules(lib.optJSONArray("rules"))) continue;
            JSONObject downloads = lib.optJSONObject("downloads");
            if (downloads == null) {
                downloadMavenLibrary(lib, libsDir, cp);
                continue;
            }
            downloadArtifact(downloads, libsDir, cp);
            downloadNativeClassifier(lib, downloads, libsDir, nativesDir);
        }
    }

    void downloadInstallProfileLibraries(JSONObject installProfile, Path libsDir, Path nativesDir) throws Exception {
        JSONArray installLibraries = installProfile.optJSONArray("libraries");
        if (installLibraries == null || installLibraries.isEmpty()) return;
        JSONObject pseudoMeta = new JSONObject();
        pseudoMeta.put("libraries", new JSONArray(installLibraries.toString()));
        // Processor dependencies must exist locally but are not part of the game runtime classpath.
        downloadLibraries(pseudoMeta, libsDir, nativesDir, new LinkedHashSet<>());
    }

    private void downloadArtifact(JSONObject downloads, Path libsDir, LinkedHashSet<String> cp) throws Exception {
        JSONObject artifact = downloads.optJSONObject("artifact");
        if (artifact == null) return;
        Path p = libsDir.resolve(artifact.getString("path"));
        http.download(artifact.getString("url"), p, artifact.optString("sha1", ""));
        cp.add(p.toString());
    }

    private void downloadNativeClassifier(JSONObject lib, JSONObject downloads, Path libsDir, Path nativesDir) throws Exception {
        JSONObject classifiers = downloads.optJSONObject("classifiers");
        if (classifiers == null || !lib.has("natives")) return;
        String classifier = OsRules.resolveNativeClassifier(lib.getJSONObject("natives"));
        if (classifier == null || !classifiers.has(classifier)) return;
        JSONObject nd = classifiers.getJSONObject(classifier);
        Path archive = libsDir.resolve(nd.getString("path"));
        http.download(nd.getString("url"), archive, nd.optString("sha1", ""));
        NativeExtractor.extractNative(archive, nativesDir, lib.optJSONObject("extract"));
    }

    private void downloadMavenLibrary(JSONObject lib, Path libsDir, LinkedHashSet<String> cp) throws Exception {
        String gav = lib.optString("name", "").trim();
        if (gav.isBlank()) return;
        String[] parts = gav.split(":");
        if (parts.length < 3) return;

        MavenCoord coord = MavenCoord.parse(parts);
        String baseRepo = lib.optString("url", "https://libraries.minecraft.net/").trim();
        if (!baseRepo.endsWith("/")) baseRepo = baseRepo + "/";

        String rel = coord.group().replace('.', '/') + "/" + coord.artifact() + "/" + coord.version() + "/";
        String fileName = coord.artifact() + "-" + coord.version()
                + (coord.classifier().isBlank() ? "" : "-" + coord.classifier())
                + "." + coord.extension();
        Path target = libsDir.resolve(rel).resolve(fileName);
        http.download(baseRepo + rel + fileName, target, "");
        cp.add(target.toString());
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
