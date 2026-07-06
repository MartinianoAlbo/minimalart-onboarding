package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ConfigureWpConfigStepTest {

    private static final String WP_CONFIG = """
        <?php
        define( 'DB_NAME', 'old' );
        define( 'DB_USER', 'old' );
        define( 'DB_PASSWORD', 'old' );
        define( 'DB_HOST', 'old' );
        $table_prefix = 'wp_';
        /* That's all, stop editing! Happy publishing. */
        """;

    private OnboardingContext contextFor(Path wpRoot) {
        return new OnboardingContext(
            wpRoot,
            new MysqlConnection("127.0.0.1", 3306, "root", "secret", "arcorencasa"),
            wpRoot.resolve("dump.sql"),
            new WpAdminUser("admin", "pw", "dev@minimalart.co"),
            "http://arcorencasa.local",
            new RepoConfig("u", "staging", "wp-content/plugins/arcorencasa"),
            new RepoConfig("u", "master", "wp-content/themes/arcor"));
    }

    private final ProgressReporter noop = new ProgressReporter() {
        public void log(String m) {}
        public void percent(int v) {}
    };

    @Test
    void writesDbSettingsAndSiteConstants(@TempDir Path wpRoot) throws Exception {
        Files.writeString(wpRoot.resolve("wp-config.php"), WP_CONFIG);
        new ConfigureWpConfigStep().execute(contextFor(wpRoot), noop);
        String content = Files.readString(wpRoot.resolve("wp-config.php"));
        assertTrue(content.contains("define( 'DB_NAME', 'arcorencasa' );"));
        assertTrue(content.contains("define( 'DB_USER', 'root' );"));
        assertTrue(content.contains("define( 'DB_PASSWORD', 'secret' );"));
        assertTrue(content.contains("define( 'DB_HOST', '127.0.0.1:3306' );"));
        assertTrue(content.contains("define( 'WP_HOME', 'http://arcorencasa.local' );"));
        assertTrue(content.contains("define( 'WP_SITEURL', 'http://arcorencasa.local' );"));
    }

    @Test
    void validateFailsWhenWpConfigMissing(@TempDir Path wpRoot) {
        StepException ex = assertThrows(StepException.class,
            () -> new ConfigureWpConfigStep().validate(contextFor(wpRoot)));
        assertTrue(ex.getMessage().contains("wp-config.php"));
    }
}
