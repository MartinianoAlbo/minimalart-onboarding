package co.minimalart.arcoronboarding.infra;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Produces a legacy 32-char MD5 password hash. WordPress's wp_check_password()
 * accepts a bare MD5 (hash length <= 32) and transparently upgrades it to the modern
 * scheme on the user's first successful login, so this works on every WP version.
 * Isolated on purpose: swapping to phpass later means changing only this class. */
public final class WordPressPasswordHasher {

    public String hash(String password) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(32);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }
}
