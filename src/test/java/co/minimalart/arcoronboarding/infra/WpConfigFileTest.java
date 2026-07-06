package co.minimalart.arcoronboarding.infra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WpConfigFileTest {

    private static final String SAMPLE = """
        <?php
        define( 'DB_NAME', 'old_db' );
        define( 'DB_USER', 'old_user' );
        $table_prefix = 'wp_';
        /* That's all, stop editing! Happy publishing. */
        require_once ABSPATH . 'wp-settings.php';
        """;

    private WpConfigFile write(Path dir, String content) throws IOException {
        Path p = Files.writeString(dir.resolve("wp-config.php"), content);
        return new WpConfigFile(p);
    }

    @Test
    void readsTablePrefix(@TempDir Path dir) throws IOException {
        assertEquals("wp_", write(dir, SAMPLE).readTablePrefix());
    }

    @Test
    void replacesExistingDefine(@TempDir Path dir) throws IOException {
        WpConfigFile cfg = write(dir, SAMPLE);
        cfg.setDefine("DB_NAME", "arcorencasa");
        String content = Files.readString(dir.resolve("wp-config.php"));
        assertTrue(content.contains("define( 'DB_NAME', 'arcorencasa' );"));
        assertTrue(!content.contains("old_db"));
    }

    @Test
    void insertsNewDefineBeforeStopMarker(@TempDir Path dir) throws IOException {
        WpConfigFile cfg = write(dir, SAMPLE);
        cfg.setDefine("WP_HOME", "http://arcorencasa.local");
        String content = Files.readString(dir.resolve("wp-config.php"));
        assertTrue(content.contains("define( 'WP_HOME', 'http://arcorencasa.local' );"));
        assertTrue(content.indexOf("WP_HOME") < content.indexOf("stop editing"));
    }

    @Test
    void emitsBooleanDefineWithoutQuotes(@TempDir Path dir) throws IOException {
        WpConfigFile cfg = write(dir, SAMPLE);
        cfg.setDefine("WP_DEBUG", true);
        String content = Files.readString(dir.resolve("wp-config.php"));
        assertTrue(content.contains("define( 'WP_DEBUG', true );"));
    }
}
