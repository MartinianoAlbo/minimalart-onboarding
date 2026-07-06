package co.minimalart.arcoronboarding.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AppConfigTest {

    @Test
    void loadsEmbeddedDefaults() {
        AppConfig config = AppConfig.load();
        assertEquals("git@bitbucket.org:gapwebapps/aec-plugin.git", config.plugin().url());
        assertEquals("staging", config.plugin().branch());
        assertEquals("wp-content/themes/arcor", config.theme().relativePath());
        assertEquals("arcorencasa", config.dbName());
        assertEquals("http://arcorencasa.local", config.siteUrl());
        assertEquals("arcorencasa.local", config.hostname());
        assertEquals("arcorencasa/arcorencasa.php", config.pluginActivationSlug());
        assertEquals("arcor", config.themeStylesheet());
        assertEquals("127.0.0.1", config.defaultMysqlHost());
        assertEquals(3306, config.defaultMysqlPort());
        assertEquals("root", config.defaultMysqlUser());
        assertEquals("", config.defaultMysqlPassword());
    }
}
