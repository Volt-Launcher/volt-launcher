package app.voltlauncher.voltlauncher.storage;

import app.voltlauncher.voltlauncher.AppPaths;
import app.voltlauncher.voltlauncher.auth.MinecraftAccountSession;
import app.voltlauncher.voltlauncher.security.Aes256GcmCipher;
import app.voltlauncher.voltlauncher.security.LocalMasterKeyProvider;
import org.json.JSONObject;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

public class EncryptedAccountStore {

    private final Path storagePath = AppPaths.encryptedSessionPath();
    private final Aes256GcmCipher cipher;

    public EncryptedAccountStore() throws Exception {
        SecretKey secretKey = new LocalMasterKeyProvider().loadOrCreateKey();
        this.cipher = new Aes256GcmCipher(secretKey);
    }

    public synchronized void save(MinecraftAccountSession session) throws Exception {
        Files.createDirectories(storagePath.getParent());

        JSONObject plain = new JSONObject();
        plain.put("uuid", session.uuid());
        plain.put("username", session.username());
        plain.put("minecraftAccessToken", session.minecraftAccessToken());
        plain.put("minecraftAccessTokenExpiresAt", session.minecraftAccessTokenExpiresAt());
        plain.put("microsoftAccessToken", session.microsoftAccessToken());
        plain.put("microsoftAccessTokenExpiresAt", session.microsoftAccessTokenExpiresAt());
        plain.put("microsoftRefreshToken", session.microsoftRefreshToken());
        plain.put("userHash", session.userHash());
        plain.put("xuid", session.xuid());

        Aes256GcmCipher.EncryptedPayload encryptedPayload = cipher.encrypt(plain.toString().getBytes(StandardCharsets.UTF_8));

        JSONObject wrapper = new JSONObject();
        wrapper.put("version", 1);
        wrapper.put("algorithm", "AES-256-GCM");
        wrapper.put("iv", encryptedPayload.ivBase64());
        wrapper.put("cipherText", encryptedPayload.cipherTextBase64());

        Files.writeString(storagePath, wrapper.toString(2), StandardCharsets.UTF_8);
        applyOwnerOnlyPermissions();
    }

    public synchronized MinecraftAccountSession load() throws Exception {
        if (!Files.exists(storagePath)) {
            return null;
        }

        JSONObject wrapper = new JSONObject(Files.readString(storagePath, StandardCharsets.UTF_8));
        Aes256GcmCipher.EncryptedPayload encryptedPayload = new Aes256GcmCipher.EncryptedPayload( wrapper.getString("iv"), wrapper.getString("cipherText"));

        byte[] plainBytes = cipher.decrypt(encryptedPayload);
        JSONObject plain = new JSONObject(new String(plainBytes, StandardCharsets.UTF_8));

        return new MinecraftAccountSession( plain.getString("uuid"), plain.getString("username"), plain.getString("minecraftAccessToken"), plain.getLong("minecraftAccessTokenExpiresAt"), plain.getString("microsoftAccessToken"), plain.getLong("microsoftAccessTokenExpiresAt"), plain.getString("microsoftRefreshToken"), plain.optString("userHash", ""), plain.optString("xuid", ""));
    }

    public synchronized void clear() throws Exception {
        Files.deleteIfExists(storagePath);
    }

    private void applyOwnerOnlyPermissions() {
        try {
            Set < PosixFilePermission> permissions = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(storagePath, permissions);
        } catch (UnsupportedOperationException | java.io.IOException ignored) {
        }
    }
}

