package co.minimalart.arcoronboarding.domain;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OnboardingContextTest {

    private OnboardingContext sample() {
        return new OnboardingContext(
            Path.of("/wp"),
            new MysqlConnection("127.0.0.1", 3306, "root", "", "arcorencasa"),
            Path.of("/tmp/dump.sql"),
            new WpAdminUser("admin", "secret", "dev@minimalart.co"),
            "http://arcorencasa.local",
            new RepoConfig("git@bitbucket.org:gapwebapps/aec-plugin.git", "staging", "wp-content/plugins/arcorencasa"),
            new RepoConfig("git@bitbucket.org:gapwebapps/aec-theme.git", "master", "wp-content/themes/arcor"));
    }

    @Test
    void resolvesWpConfigPath() {
        assertEquals(Path.of("/wp/wp-config.php"), sample().wpConfigPath());
    }

    @Test
    void resolvesRepoTargets() {
        assertEquals(Path.of("/wp/wp-content/plugins/arcorencasa"), sample().pluginTarget());
        assertEquals(Path.of("/wp/wp-content/themes/arcor"), sample().themeTarget());
    }
}
