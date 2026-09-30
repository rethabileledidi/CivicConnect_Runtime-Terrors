package com.civicconnect.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password storage with PBKDF2-HMAC-SHA256 from the JDK (no extra dependency).
 * <ul>
 *   <li>600,000 iterations - OWASP Password Storage Cheat Sheet value for PBKDF2-HMAC-SHA256.</li>
 *   <li>16-byte random salt per password from {@link SecureRandom}.</li>
 *   <li>Constant-time comparison ({@link MessageDigest#isEqual}).</li>
 *   <li>Stored as {@code pbkdf2_sha256$iterations$salt$hash}, so the iteration count can be raised
 *       later and old hashes still verify.</li>
 * </ul>
 */
public final class PasswordHasher {

    public static final int DEFAULT_ITERATIONS = 600_000;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2_sha256";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final int iterations;
    private final SecureRandom random = new SecureRandom();

    public PasswordHasher() { this(DEFAULT_ITERATIONS); }

    /** Tests may use fewer iterations for speed; production always uses the default. */
    public PasswordHasher(int iterations) {
        if (iterations < 1) throw new IllegalArgumentException("iterations");
        this.iterations = iterations;
    }

    public String hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] key = derive(password, salt, iterations);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIX + "$" + iterations + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(key);
    }

    /** True only if {@code password} matches {@code stored}. Malformed or null hashes never match. */
    public boolean verify(char[] password, String stored) {
        if (stored == null) return false;
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) return false;
        try {
            int storedIterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password, salt, storedIterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Burns the same CPU time as a real check; used when the email is unknown (timing parity). */
    public void dummyVerify(char[] password) {
        derive(password, new byte[SALT_BYTES], iterations);
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available in this JDK", e);
        } finally {
            spec.clearPassword();
        }
    }
}
