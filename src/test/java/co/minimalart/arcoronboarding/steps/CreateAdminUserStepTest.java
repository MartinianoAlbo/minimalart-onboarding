package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.MysqlGateway;
import co.minimalart.arcoronboarding.infra.WordPressPasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CreateAdminUserStepTest {

    private final ProgressReporter noop = new ProgressReporter() {
        public void log(String m) {}
        public void percent(int v) {}
    };

    @Test
    void writesUserWithMd5PasswordAndAdministratorCaps(@TempDir Path wpRoot) throws Exception {
        Files.writeString(wpRoot.resolve("wp-config.php"), "<?php\n$table_prefix = 'wp_';\n");
        MysqlGateway gateway = mock(MysqlGateway.class);
        OnboardingContext ctx = new OnboardingContext(
            wpRoot,
            new MysqlConnection("127.0.0.1", 3306, "root", "", "arcorencasa"),
            wpRoot.resolve("dump.sql"),
            new WpAdminUser("admin", "password", "dev@minimalart.co"),
            "http://arcorencasa.local",
            new RepoConfig("u", "staging", "wp-content/plugins/arcorencasa"),
            new RepoConfig("u", "master", "wp-content/themes/arcor"));

        new CreateAdminUserStep(gateway, new WordPressPasswordHasher()).execute(ctx, noop);

        // md5("password") and the serialized administrator capabilities map.
        verify(gateway).upsertAdminUser(
            eq("wp_"),
            eq(ctx.adminUser()),
            eq("5f4dcc3b5aa765d61d8327deb882cf99"),
            eq("a:1:{s:13:\"administrator\";b:1;}"));
    }
}
