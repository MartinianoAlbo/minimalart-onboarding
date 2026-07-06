package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.WpConfigFile;

import java.io.IOException;
import java.nio.file.Files;

/** Writes DB credentials and the WP_HOME/WP_SITEURL/WP_DEBUG constants into wp-config.php.
 * Defining WP_HOME/WP_SITEURL overrides the URLs stored in the dumped DB, which is what
 * lets us skip a search-replace. */
public final class ConfigureWpConfigStep implements OnboardingStep {

    @Override
    public String name() {
        return "Configure wp-config.php";
    }

    @Override
    public void validate(OnboardingContext ctx) throws StepException {
        if (!Files.isRegularFile(ctx.wpConfigPath())) {
            throw new StepException("wp-config.php not found at " + ctx.wpConfigPath()
                + " — is the WordPress root correct?");
        }
    }

    @Override
    public void execute(OnboardingContext ctx, ProgressReporter progress) throws StepException {
        WpConfigFile config = new WpConfigFile(ctx.wpConfigPath());
        MysqlConnection db = ctx.mysql();
        try {
            config.setDefine("DB_NAME", db.database());
            config.setDefine("DB_USER", db.user());
            config.setDefine("DB_PASSWORD", db.password());
            config.setDefine("DB_HOST", db.host() + ":" + db.port());
            config.setDefine("WP_HOME", ctx.siteUrl());
            config.setDefine("WP_SITEURL", ctx.siteUrl());
            config.setDefine("WP_DEBUG", true);
            progress.log("wp-config.php configured for " + ctx.siteUrl());
        } catch (IOException e) {
            throw new StepException("Failed to edit wp-config.php: " + e.getMessage(), e);
        }
    }
}
