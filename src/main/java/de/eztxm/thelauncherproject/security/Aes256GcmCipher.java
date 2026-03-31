package de.eztxm.thelauncherproject.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.util.Base64;

public class Aes256GcmCipher {

    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public record EncryptedPayload(String ivBase64, String cipherTextBase64) {
    }

    public Aes256GcmCipher(SecretKey secretKey) {
        this.secretKey = secretKey;
    }

    public EncryptedPayload encrypt(byte[] plainBytes) throws Exception {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        byte[] cipherText = cipher.doFinal(plainBytes);

        return new EncryptedPayload(
                Base64.getEncoder().encodeToString(iv),
                Base64.getEncoder().encodeToString(cipherText));
    }

    public byte[] decrypt(EncryptedPayload encryptedPayload) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                new GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.getDecoder().decode(encryptedPayload.ivBase64())));
        return cipher.doFinal(Base64.getDecoder().decode(encryptedPayload.cipherTextBase64()));
    }
}

