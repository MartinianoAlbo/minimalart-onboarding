package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.GitClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CloneRepositoriesStepTest {

    private final ProgressReporter noop = new ProgressReporter() {
        public void log(String m) {}
        public void percent(int v) {}
    };

    private OnboardingContext contextFor(Path wpRoot) {
        return new OnboardingContext(
            wpRoot,
            new MysqlConnection("127.0.0.1", 3306, "root", "", "arcorencasa"),
            wpRoot.resolve("dump.sql"),
            new WpAdminUser("admin", "pw", "dev@minimalart.co"),
            "http://arcorencasa.local",
            new RepoConfig("git@bitbucket.org:gapwebapps/aec-plugin.git", "staging", "wp-content/plugins/arcorencasa"),
            new RepoConfig("git@bitbucket.org:gapwebapps/aec-theme.git", "master", "wp-content/themes/arcor"));
    }

    @Test
    void clonesPluginAndTheme(@TempDir Path wpRoot) throws Exception {
        GitClient git = mock(GitClient.class);
        when(git.isAvailable()).thenReturn(true);
        OnboardingContext ctx = contextFor(wpRoot);

        new CloneRepositoriesStep(git).execute(ctx, noop);

        verify(git).cloneOrPull(eq("git@bitbucket.org:gapwebapps/aec-plugin.git"), eq("staging"),
            eq(ctx.pluginTarget()), any());
        verify(git).cloneOrPull(eq("git@bitbucket.org:gapwebapps/aec-theme.git"), eq("master"),
            eq(ctx.themeTarget()), any());
    }

    @Test
    void validateFailsWhenGitMissing(@TempDir Path wpRoot) {
        GitClient git = mock(GitClient.class);
        when(git.isAvailable()).thenReturn(false);
        StepException ex = assertThrows(StepException.class,
            () -> new CloneRepositoriesStep(git).validate(contextFor(wpRoot)));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().toLowerCase().contains("git"));
    }
}
