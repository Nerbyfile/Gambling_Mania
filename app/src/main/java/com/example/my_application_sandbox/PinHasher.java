package com.example.my_application_sandbox;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Hachage salé du PIN (PBKDF2). Le PIN en clair n'est jamais stocké. */
public final class PinHasher {

    private static final int ITERATIONS = 20000;
    private static final int KEY_BITS = 256;

    private PinHasher() { }

    public static String newSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    public static String hash(String pin, String saltBase64) {
        try {
            byte[] salt = Base64.decode(saltBase64, Base64.NO_WRAP);
            PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            return Base64.encodeToString(f.generateSecret(spec).getEncoded(), Base64.NO_WRAP);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Comparaison à temps constant. */
    public static boolean verify(String pin, String saltBase64, String expectedHash) {
        byte[] a = hash(pin, saltBase64).getBytes(StandardCharsets.UTF_8);
        byte[] b = expectedHash.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}