package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.ArchiveExtractor;
import co.minimalart.arcoronboarding.infra.MysqlGateway;
import co.minimalart.arcoronboarding.infra.SqlStatementSplitter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Extracts the dump (if zipped), creates the database if missing, and imports the
 * dump statement by statement over JDBC. */
public final class ImportDatabaseDumpStep implements OnboardingStep {

    private final MysqlGateway gateway;
    private final ArchiveExtractor extractor;
    private final SqlStatementSplitter splitter;

    public ImportDatabaseDumpStep(MysqlGateway gateway, ArchiveExtractor extractor,
                                  SqlStatementSplitter splitter) {
        this.gateway = gateway;
        this.extractor = extractor;
        this.splitter = splitter;
    }

    @Override
    public String name() {
        return "Import database dump";
    }

    @Override
    public void validate(OnboardingContext ctx) throws StepException {
        if (!Files.isRegularFile(ctx.dumpFile())) {
            throw new StepException("Dump file not found: " + ctx.dumpFile());
        }
        if (!gateway.canConnect()) {
            throw new StepException("Cannot reach MySQL at " + ctx.mysql().host() + ":"
                + ctx.mysql().port() + " — check that your stack is running and the credentials.");
        }
    }

    @Override
    public void execute(OnboardingContext ctx, ProgressReporter progress) throws StepException {
        try {
            Path sql = extractor.resolveSqlFile(ctx.dumpFile(), ctx.dumpFile().getParent());
            progress.log("Reading dump " + sql.getFileName());
            String script = Files.readString(sql);
            List<String> statements = splitter.split(script);
            progress.log("Parsed " + statements.size() + " SQL statements");
            gateway.createDatabaseIfMissing();
            gateway.importStatements(statements, progress::log);
        } catch (Exception e) {
            throw new StepException("Database import failed: " + e.getMessage(), e);
        }
    }
}
