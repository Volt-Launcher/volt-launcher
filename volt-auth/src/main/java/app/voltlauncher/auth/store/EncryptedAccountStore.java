package app.voltlauncher.auth.store;

import app.voltlauncher.core.AppPaths;
import app.voltlauncher.auth.MinecraftAccountSession;
import app.voltlauncher.core.security.Aes256GcmCipher;
import app.voltlauncher.core.security.LocalMasterKeyProvider;
import org.json.JSONObject;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class EncryptedAccountStore {

    private final Path storagePath = AppPaths.encryptedSessionPath();
    private final Aes256GcmCipher cipher;

    public EncryptedAccountStore() throws Exception {
        SecretKey secretKey = new LocalMasterKeyProvider().loadOrCreateKey();
        this.cipher = new Aes256GcmCipher(secretKey);
    }

    public record AccountData(Map<String, MinecraftAccountSession> accounts, String selectedUuid) {
        public AccountData {
            accounts = Collections.unmodifiableMap(new LinkedHashMap<>(accounts));
        }
    }

    public synchronized AccountData loadAll() throws Exception {
        if (!Files.exists(storagePath)) {
            return new AccountData(new LinkedHashMap<>(), null);
        }

        JSONObject wrapper = new JSONObject(Files.readString(storagePath, StandardCharsets.UTF_8));
        int version = wrapper.optInt("version", 1);

        Aes256GcmCipher.EncryptedPayload payload = new Aes256GcmCipher.EncryptedPayload(
                wrapper.getString("iv"), wrapper.getString("cipherText"));
        byte[] plainBytes = cipher.decrypt(payload);
        JSONObject plain = new JSONObject(new String(plainBytes, StandardCharsets.UTF_8));

        if (version == 1) {
            // Migrate single-account v1 format
            MinecraftAccountSession session = parseSession(plain);
            Map<String, MinecraftAccountSession> map = new LinkedHashMap<>();
            map.put(session.uuid(), session);
            return new AccountData(map, session.uuid());
        }

        String selectedUuid = plain.optString("selectedUuid", "");
        Map<String, MinecraftAccountSession> map = new LinkedHashMap<>();
        JSONObject accounts = plain.optJSONObject("accounts");
        if (accounts != null) {
            for (String uuid : accounts.keySet()) {
                map.put(uuid, parseSession(accounts.getJSONObject(uuid)));
            }
        }
        return new AccountData(map, selectedUuid.isBlank() ? null : selectedUuid);
    }

    public synchronized MinecraftAccountSession load() throws Exception {
        AccountData data = loadAll();
        if (data.selectedUuid() == null) return null;
        return data.accounts().get(data.selectedUuid());
    }

    public synchronized void save(MinecraftAccountSession session) throws Exception {
        AccountData data = loadAll();
        Map<String, MinecraftAccountSession> accounts = new LinkedHashMap<>(data.accounts());
        accounts.put(session.uuid(), session);
        // New account always becomes selected
        String selected = session.uuid();
        persistAll(new AccountData(accounts, selected));
    }

    public synchronized void setSelected(String uuid) throws Exception {
        AccountData data = loadAll();
        if (!data.accounts().containsKey(uuid)) {
            throw new IllegalArgumentException("Account not found: " + uuid);
        }
        persistAll(new AccountData(data.accounts(), uuid));
    }

    public synchronized void remove(String uuid) throws Exception {
        AccountData data = loadAll();
        Map<String, MinecraftAccountSession> accounts = new LinkedHashMap<>(data.accounts());
        accounts.remove(uuid);
        if (accounts.isEmpty()) {
            Files.deleteIfExists(storagePath);
            return;
        }
        String selected = uuid.equals(data.selectedUuid())
                ? accounts.keySet().iterator().next()
                : data.selectedUuid();
        persistAll(new AccountData(accounts, selected));
    }

    public synchronized void clear() throws Exception {
        Files.deleteIfExists(storagePath);
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private void persistAll(AccountData data) throws Exception {
        Files.createDirectories(storagePath.getParent());

        JSONObject accountsJson = new JSONObject();
        for (Map.Entry<String, MinecraftAccountSession> entry : data.accounts().entrySet()) {
            accountsJson.put(entry.getKey(), sessionToJson(entry.getValue()));
        }

        JSONObject plain = new JSONObject();
        plain.put("selectedUuid", data.selectedUuid() != null ? data.selectedUuid() : "");
        plain.put("accounts", accountsJson);

        Aes256GcmCipher.EncryptedPayload encryptedPayload = cipher.encrypt(
                plain.toString().getBytes(StandardCharsets.UTF_8));

        JSONObject wrapper = new JSONObject();
        wrapper.put("version", 2);
        wrapper.put("algorithm", "AES-256-GCM");
        wrapper.put("iv", encryptedPayload.ivBase64());
        wrapper.put("cipherText", encryptedPayload.cipherTextBase64());

        Files.writeString(storagePath, wrapper.toString(2), StandardCharsets.UTF_8);
        applyOwnerOnlyPermissions();
    }

    private JSONObject sessionToJson(MinecraftAccountSession session) {
        JSONObject j = new JSONObject();
        j.put("uuid", session.uuid());
        j.put("username", session.username());
        j.put("minecraftAccessToken", session.minecraftAccessToken());
        j.put("minecraftAccessTokenExpiresAt", session.minecraftAccessTokenExpiresAt());
        j.put("microsoftAccessToken", session.microsoftAccessToken());
        j.put("microsoftAccessTokenExpiresAt", session.microsoftAccessTokenExpiresAt());
        j.put("microsoftRefreshToken", session.microsoftRefreshToken());
        j.put("userHash", session.userHash());
        j.put("xuid", session.xuid());
        return j;
    }

    private MinecraftAccountSession parseSession(JSONObject j) {
        return new MinecraftAccountSession(
                j.getString("uuid"),
                j.getString("username"),
                j.getString("minecraftAccessToken"),
                j.getLong("minecraftAccessTokenExpiresAt"),
                j.getString("microsoftAccessToken"),
                j.getLong("microsoftAccessTokenExpiresAt"),
                j.getString("microsoftRefreshToken"),
                j.optString("userHash", ""),
                j.optString("xuid", ""));
    }

    private void applyOwnerOnlyPermissions() {
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(storagePath, permissions);
        } catch (UnsupportedOperationException | java.io.IOException ignored) {
        }
    }
}
