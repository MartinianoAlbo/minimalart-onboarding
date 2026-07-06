package co.minimalart.arcoronboarding.domain;

/** One onboarding action. validate() must not mutate anything. */
public interface OnboardingStep {
    String name();
    void validate(OnboardingContext ctx) throws StepException;
    void execute(OnboardingContext ctx, ProgressReporter progress) throws StepException;
}
