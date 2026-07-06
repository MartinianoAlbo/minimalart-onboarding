package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.ArchiveExtractor;
import co.minimalart.arcoronboarding.infra.MysqlGateway;
import co.minimalart.arcoronboarding.infra.SqlStatementSplitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImportDatabaseDumpStepTest {

    private final ProgressReporter noop = new ProgressReporter() {
        public void log(String m) {}
        public void percent(int v) {}
    };

    private OnboardingContext contextWithDump(Path wpRoot, Path dump) {
        return new OnboardingContext(
            wpRoot,
            new MysqlConnection("127.0.0.1", 3306, "root", "", "arcorencasa"),
            dump,
            new WpAdminUser("admin", "pw", "dev@minimalart.co"),
            "http://arcorencasa.local",
            new RepoConfig("u", "staging", "wp-content/plugins/arcorencasa"),
            new RepoConfig("u", "master", "wp-content/themes/arcor"));
    }

    @Test
    void createsDbThenImportsSplitStatements(@TempDir Path dir) throws Exception {
        Path dump = Files.writeString(dir.resolve("arcorencasa.sql"), "SELECT 1; SELECT 2;");
        MysqlGateway gateway = mock(MysqlGateway.class);
        new ImportDatabaseDumpStep(gateway, new ArchiveExtractor(), new SqlStatementSplitter())
            .execute(contextWithDump(dir, dump), noop);

        verify(gateway).createDatabaseIfMissing();
        verify(gateway).importStatements(eq(List.of("SELECT 1", "SELECT 2")), any());
    }

    @Test
    void validateFailsWhenDumpMissing(@TempDir Path dir) {
        MysqlGateway gateway = mock(MysqlGateway.class);
        when(gateway.canConnect()).thenReturn(true);
        Path missing = dir.resolve("nope.sql");
        StepException ex = assertThrows(StepException.class,
            () -> new ImportDatabaseDumpStep(gateway, new ArchiveExtractor(), new SqlStatementSplitter())
                .validate(contextWithDump(dir, missing)));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().toLowerCase().contains("dump"));
    }

    @Test
    void validateFailsWhenMysqlUnreachable(@TempDir Path dir) throws Exception {
        Path dump = Files.writeString(dir.resolve("arcorencasa.sql"), "SELECT 1;");
        MysqlGateway gateway = mock(MysqlGateway.class);
        when(gateway.canConnect()).thenReturn(false);
        StepException ex = assertThrows(StepException.class,
            () -> new ImportDatabaseDumpStep(gateway, new ArchiveExtractor(), new SqlStatementSplitter())
                .validate(contextWithDump(dir, dump)));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().toLowerCase().contains("mysql"));
    }
}
