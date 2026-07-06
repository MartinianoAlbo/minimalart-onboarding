package co.minimalart.arcoronboarding.domain;

import java.util.List;

/** Validates every step first (fail-fast, no mutation), then executes them in order,
 * stopping at the first failure. Returns true when all steps succeed. */
public final class OnboardingRunner {

    private final List<OnboardingStep> steps;

    public OnboardingRunner(List<OnboardingStep> steps) {
        this.steps = List.copyOf(steps);
    }

    public boolean run(OnboardingContext ctx, ProgressReporter progress) {
        try {
            for (OnboardingStep step : steps) {
                step.validate(ctx);
            }
        } catch (StepException e) {
            progress.log("✗ Validation failed: " + e.getMessage());
            return false;
        }

        int total = steps.size();
        for (int i = 0; i < total; i++) {
            OnboardingStep step = steps.get(i);
            progress.log("▶ " + step.name());
            try {
                step.execute(ctx, progress);
            } catch (StepException e) {
                progress.log("✗ " + step.name() + " failed: " + e.getMessage());
                return false;
            }
            progress.percent((int) Math.round((i + 1) * 100.0 / total));
        }
        progress.log("✓ Done. Open " + ctx.siteUrl());
        return true;
    }
}
