package com.remipreparateur.ia.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Chiffrement symétrique (AES-GCM) des secrets stockés en base — les clés API IA des clubs.
 * La clé de chiffrement dérive du secret d'environnement {@code IA_KEYS_SECRET} (SHA-256 → 256 bits).
 * En l'absence de secret, un repli de développement est utilisé (⚠ jamais en prod : poser IA_KEYS_SECRET).
 */
@Service
public class CryptoService {

    private static final Logger log = LoggerFactory.getLogger(CryptoService.class);
    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(@Value("${IA_KEYS_SECRET:}") String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn("IA_KEYS_SECRET absent — chiffrement des clés IA en mode DÉVELOPPEMENT (poser IA_KEYS_SECRET en prod).");
            secret = "dev-fallback-insecure-ia-secret";
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(hash, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Init CryptoService impossible", e);
        }
    }

    /** Chiffre une valeur en clair → Base64(IV || ciphertext). */
    public String chiffrer(String clair) {
        if (clair == null) return null;
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(clair.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Chiffrement impossible", e);
        }
    }

    /** Déchiffre une valeur Base64(IV || ciphertext) → clair. */
    public String dechiffrer(String chiffre) {
        if (chiffre == null || chiffre.isBlank()) return null;
        try {
            byte[] all = Base64.getDecoder().decode(chiffre);
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            byte[] ct = new byte[all.length - IV_LEN];
            System.arraycopy(all, IV_LEN, ct, 0, ct.length);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Déchiffrement impossible (clé/secret changé ?)", e);
        }
    }

    /** Masque une clé pour l'affichage : « sk-ant-…a1b2 ». */
    public static String masquer(String clair) {
        if (clair == null || clair.isBlank()) return null;
        if (clair.length() <= 10) return "••••";
        return clair.substring(0, 6) + "…" + clair.substring(clair.length() - 4);
    }
}
