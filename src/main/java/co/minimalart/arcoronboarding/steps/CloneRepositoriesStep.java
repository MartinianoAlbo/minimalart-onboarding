package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.GitClient;

/** Clones (or pulls) the custom plugin and theme into the WordPress content dirs. */
public final class CloneRepositoriesStep implements OnboardingStep {

    private final GitClient git;

    public CloneRepositoriesStep(GitClient git) {
        this.git = git;
    }

    @Override
    public String name() {
        return "Clone plugin and theme";
    }

    @Override
    public void validate(OnboardingContext ctx) throws StepException {
        if (!git.isAvailable()) {
            throw new StepException("git was not found on your PATH — install git and retry.");
        }
    }

    @Override
    public void execute(OnboardingContext ctx, ProgressReporter progress) throws StepException {
        clone(ctx.plugin(), ctx.pluginTarget(), progress);
        clone(ctx.theme(), ctx.themeTarget(), progress);
    }

    private void clone(RepoConfig repo, java.nio.file.Path target, ProgressReporter progress)
            throws StepException {
        progress.log("Fetching " + repo.url() + " (" + repo.branch() + ")");
        try {
            git.cloneOrPull(repo.url(), repo.branch(), target, progress::log);
        } catch (Exception e) {
            throw new StepException("Failed to clone " + repo.url() + ": " + e.getMessage(), e);
        }
    }
}
