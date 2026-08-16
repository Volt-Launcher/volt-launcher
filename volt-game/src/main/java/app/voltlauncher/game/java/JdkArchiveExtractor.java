package app.voltlauncher.game.java;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class JdkArchiveExtractor {

    private JdkArchiveExtractor() {}

    static void extract(Path archive, Path targetDir) throws Exception {
        String name = archive.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            extractTarGz(archive, targetDir);
        } else {
            extractZip(archive, targetDir);
        }
    }

    static Path findJavaExecutable(Path dir, String execName) throws Exception {
        try (var walk = Files.walk(dir)) {
            return walk
                    .filter(p -> p.getFileName().toString().equals(execName))
                    .filter(p -> p.getParent().getFileName().toString().equals("bin"))
                    .findFirst()
                    .orElse(null);
        }
    }

    static void copyDirectory(Path src, Path dst) throws IOException {
        try (var walk = Files.walk(src)) {
            for (Path source : (Iterable<Path>) walk::iterator) {
                Path target = dst.resolve(src.relativize(source));
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void extractTarGz(Path archive, Path targetDir) throws Exception {
        try (InputStream fi = Files.newInputStream(archive);
             GZIPInputStream gi = new GZIPInputStream(fi);
             TarArchiveInputStream tar = new TarArchiveInputStream(gi)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (!tar.canReadEntryData(entry)) continue;
                Path out = targetDir.resolve(entry.getName()).normalize();
                if (!out.startsWith(targetDir)) throw new IOException("Invalid tar path: " + entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                Files.copy(tar, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void extractZip(Path archive, Path targetDir) throws Exception {
        try (InputStream fi = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(fi)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path out = targetDir.resolve(entry.getName()).normalize();
                if (!out.startsWith(targetDir)) throw new IOException("Invalid zip path: " + entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
