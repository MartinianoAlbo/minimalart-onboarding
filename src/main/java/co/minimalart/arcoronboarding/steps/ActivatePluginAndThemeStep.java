package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.MysqlGateway;
import co.minimalart.arcoronboarding.infra.PhpSerializer;
import co.minimalart.arcoronboarding.infra.WpConfigFile;

import java.util.List;

/** Activates the custom plugin and theme by writing the relevant wp_options rows. */
public final class ActivatePluginAndThemeStep implements OnboardingStep {

    private final MysqlGateway gateway;
    private final String pluginActivationSlug;
    private final String themeStylesheet;

    public ActivatePluginAndThemeStep(MysqlGateway gateway, String pluginActivationSlug,
                                      String themeStylesheet) {
        this.gateway = gateway;
        this.pluginActivationSlug = pluginActivationSlug;
        this.themeStylesheet = themeStylesheet;
    }

    @Override
    public String name() {
        return "Activate plugin and theme";
    }

    @Override
    public void validate(OnboardingContext ctx) throws StepException {
        // Nothing to validate before running; wp-config presence is covered by earlier steps.
    }

    @Override
    public void execute(OnboardingContext ctx, ProgressReporter progress) throws StepException {
        try {
            String prefix = new WpConfigFile(ctx.wpConfigPath()).readTablePrefix();
            String activePlugins = PhpSerializer.serialize(List.of(pluginActivationSlug));
            gateway.setOption(prefix, "active_plugins", activePlugins);
            gateway.setOption(prefix, "template", themeStylesheet);
            gateway.setOption(prefix, "stylesheet", themeStylesheet);
            progress.log("Activated plugin '" + pluginActivationSlug
                + "' and theme '" + themeStylesheet + "'.");
        } catch (Exception e) {
            throw new StepException("Failed to activate plugin/theme: " + e.getMessage(), e);
        }
    }
}
