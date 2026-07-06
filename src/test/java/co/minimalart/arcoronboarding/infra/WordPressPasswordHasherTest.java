package co.minimalart.arcoronboarding.infra;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WordPressPasswordHasherTest {

    private final WordPressPasswordHasher hasher = new WordPressPasswordHasher();

    @Test
    void producesCanonicalMd5Hash() {
        // md5("password") is the canonical, widely published vector.
        assertEquals("5f4dcc3b5aa765d61d8327deb882cf99", hasher.hash("password"));
    }

    @Test
    void producesLowercase32CharHex() {
        String hash = hasher.hash("Correct-Horse-Battery-Staple");
        assertEquals(32, hash.length());
        assertEquals(hash.toLowerCase(), hash);
    }
}
