package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.HostsFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterHostEntryStepTest {

    private final ProgressReporter noop = new ProgressReporter() {
        public void log(String m) {}
        public void percent(int v) {}
    };

    private OnboardingContext ctx(Path wpRoot) {
        return new OnboardingContext(
            wpRoot,
            new MysqlConnection("127.0.0.1", 3306, "root", "", "arcorencasa"),
            wpRoot.resolve("dump.sql"),
            new WpAdminUser("admin", "pw", "dev@minimalart.co"),
            "http://arcorencasa.local",
            new RepoConfig("u", "staging", "wp-content/plugins/arcorencasa"),
            new RepoConfig("u", "master", "wp-content/themes/arcor"));
    }

    @Test
    void appendsHostEntry(@TempDir Path dir) throws Exception {
        Path hostsPath = Files.writeString(dir.resolve("hosts"), "127.0.0.1 localhost\n");
        new RegisterHostEntryStep(new HostsFile(hostsPath), "arcorencasa.local")
            .execute(ctx(dir), noop);
        assertTrue(Files.readString(hostsPath).contains("127.0.0.1 arcorencasa.local"));
    }

    @Test
    void doesNotThrowWhenPermissionDenied(@TempDir Path dir) throws Exception {
        // A hosts path that cannot be written (points at a directory) must degrade gracefully.
        HostsFile unwritable = new HostsFile(dir);
        new RegisterHostEntryStep(unwritable, "arcorencasa.local").execute(ctx(dir), noop);
        // No exception thrown => graceful degradation.
    }
}
