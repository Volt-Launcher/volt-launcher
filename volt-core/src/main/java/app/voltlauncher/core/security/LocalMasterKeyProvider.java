package app.voltlauncher.core.security;

import app.voltlauncher.core.AppPaths;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;

public class LocalMasterKeyProvider {

    public SecretKey loadOrCreateKey() throws IOException {
        Path keyPath = AppPaths.masterKeyPath();
        Files.createDirectories(keyPath.getParent());

        if (Files.exists(keyPath)) {
            byte[] keyBytes = Base64.getDecoder().decode(Files.readString(keyPath).trim());
            return new SecretKeySpec(keyBytes, "AES");
        }

        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        Files.writeString(keyPath, Base64.getEncoder().encodeToString(keyBytes));
        applyOwnerOnlyPermissions(keyPath);
        return new SecretKeySpec(keyBytes, "AES");
    }

    private void applyOwnerOnlyPermissions(Path path) {
        try {
            Set < PosixFilePermission> permissions = EnumSet.of( PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
        }
    }
}

