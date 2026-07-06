package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.MysqlGateway;
import co.minimalart.arcoronboarding.infra.PhpSerializer;
import co.minimalart.arcoronboarding.infra.WordPressPasswordHasher;
import co.minimalart.arcoronboarding.infra.WpConfigFile;

import java.util.LinkedHashMap;
import java.util.Map;

/** Creates (or updates) the WordPress admin user directly in the imported database,
 * with a WP-compatible password hash and administrator capabilities. */
public final class CreateAdminUserStep implements OnboardingStep {

    private final MysqlGateway gateway;
    private final WordPressPasswordHasher hasher;

    public CreateAdminUserStep(MysqlGateway gateway, WordPressPasswordHasher hasher) {
        this.gateway = gateway;
        this.hasher = hasher;
    }

    @Override
    public String name() {
        return "Create admin user";
    }

    @Override
    public void validate(OnboardingContext ctx) throws StepException {
        WpAdminUser user = ctx.adminUser();
        if (user.username().isBlank() || user.password().isBlank() || user.email().isBlank()) {
            throw new StepException("Admin user requires a username, password and email.");
        }
    }

    @Override
    public void execute(OnboardingContext ctx, ProgressReporter progress) throws StepException {
        try {
            String prefix = new WpConfigFile(ctx.wpConfigPath()).readTablePrefix();
            Map<String, Boolean> caps = new LinkedHashMap<>();
            caps.put("administrator", true);
            gateway.upsertAdminUser(prefix, ctx.adminUser(),
                hasher.hash(ctx.adminUser().password()), PhpSerializer.serialize(caps));
            progress.log("Admin user '" + ctx.adminUser().username() + "' ready.");
        } catch (Exception e) {
            throw new StepException("Failed to create admin user: " + e.getMessage(), e);
        }
    }
}
