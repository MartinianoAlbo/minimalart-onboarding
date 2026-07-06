package co.minimalart.arcoronboarding;

import co.minimalart.arcoronboarding.config.AppConfig;
import co.minimalart.arcoronboarding.ui.OnboardingWizard;
import co.minimalart.arcoronboarding.ui.theme.MinimalartTheme;

import javax.swing.SwingUtilities;

/** Application entry point: install the brand theme, then show the onboarding wizard. */
public final class App {

    private App() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MinimalartTheme.install();
            new OnboardingWizard(AppConfig.load()).setVisible(true);
        });
    }
}
