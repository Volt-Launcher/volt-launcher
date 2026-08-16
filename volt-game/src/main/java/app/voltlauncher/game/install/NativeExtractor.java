package app.voltlauncher.game.install;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class NativeExtractor {

    private NativeExtractor() {}

    static void extractNative(Path archive, Path targetDir, JSONObject extractConfig) throws Exception {
        List<String> excludes = buildExcludeList(extractConfig);
        try (InputStream in = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || excludes.stream().anyMatch(name::startsWith)) continue;
                Path out = targetDir.resolve(name).normalize();
                if (!out.startsWith(targetDir)) throw new IOException("Invalid native path: " + name);
                Files.createDirectories(out.getParent());
                Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static List<String> buildExcludeList(JSONObject extractConfig) {
        List<String> excludes = new ArrayList<>();
        if (extractConfig == null) return excludes;
        JSONArray arr = extractConfig.optJSONArray("exclude");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                excludes.add(arr.getString(i));
            }
        }
        return excludes;
    }
}
