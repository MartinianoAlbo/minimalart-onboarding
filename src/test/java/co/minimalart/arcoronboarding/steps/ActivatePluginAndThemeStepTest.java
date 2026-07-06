package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.MysqlGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.mockito.Mockito.*;

class ActivatePluginAndThemeStepTest {

    private final ProgressReporter noop = new ProgressReporter() {
        public void log(String m) {}
        public void percent(int v) {}
    };

    @Test
    void setsActivePluginsAndThemeOptions(@TempDir Path wpRoot) throws Exception {
        Files.writeString(wpRoot.resolve("wp-config.php"), "<?php\n$table_prefix = 'wp_';\n");
        MysqlGateway gateway = mock(MysqlGateway.class);
        OnboardingContext ctx = new OnboardingContext(
            wpRoot,
            new MysqlConnection("127.0.0.1", 3306, "root", "", "arcorencasa"),
            wpRoot.resolve("dump.sql"),
            new WpAdminUser("admin", "pw", "dev@minimalart.co"),
            "http://arcorencasa.local",
            new RepoConfig("u", "staging", "wp-content/plugins/arcorencasa"),
            new RepoConfig("u", "master", "wp-content/themes/arcor"));

        new ActivatePluginAndThemeStep(gateway, "arcorencasa/arcorencasa.php", "arcor")
            .execute(ctx, noop);

        verify(gateway).setOption("wp_", "active_plugins",
            "a:1:{i:0;s:27:\"arcorencasa/arcorencasa.php\";}");
        verify(gateway).setOption("wp_", "template", "arcor");
        verify(gateway).setOption("wp_", "stylesheet", "arcor");
    }
}
