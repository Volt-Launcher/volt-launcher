package app.voltlauncher.providers;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/** Content hashes used to match a local file against a provider's catalogue. */
public final class FileHashes {

    private FileHashes() {}

    public static String sha1(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[65_536];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder(40);
        for (byte b : digest.digest()) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    /**
     * CurseForge's file fingerprint: MurmurHash2 (32-bit, seed 1) over the file with whitespace
     * stripped out.
     *
     * <p>The stripping is not an optimisation — CurseForge computes the hash over the file with
     * tab, newline, carriage return and space bytes removed, and matching their value exactly is
     * the whole point. The result is treated as unsigned, which is how their API expects it.
     */
    public static long curseForgeFingerprint(Path file) throws Exception {
        byte[] raw = Files.readAllBytes(file);

        byte[] data = new byte[raw.length];
        int length = 0;
        for (byte b : raw) {
            if (b == 9 || b == 10 || b == 13 || b == 32) continue;
            data[length++] = b;
        }

        final int m = 0x5bd1e995;
        final int r = 24;
        int h = 1 ^ length;
        int index = 0;
        int remaining = length;

        while (remaining >= 4) {
            int k = (data[index] & 0xff)
                    | ((data[index + 1] & 0xff) << 8)
                    | ((data[index + 2] & 0xff) << 16)
                    | ((data[index + 3] & 0xff) << 24);
            k *= m;
            k ^= k >>> r;
            k *= m;
            h *= m;
            h ^= k;
            index += 4;
            remaining -= 4;
        }

        switch (remaining) {
            case 3 -> {
                h ^= (data[index + 2] & 0xff) << 16;
                h ^= (data[index + 1] & 0xff) << 8;
                h ^= data[index] & 0xff;
                h *= m;
            }
            case 2 -> {
                h ^= (data[index + 1] & 0xff) << 8;
                h ^= data[index] & 0xff;
                h *= m;
            }
            case 1 -> {
                h ^= data[index] & 0xff;
                h *= m;
            }
            default -> { }
        }

        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;
        return h & 0xFFFFFFFFL;
    }
}
