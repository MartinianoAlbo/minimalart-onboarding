package co.minimalart.arcoronboarding.domain;

/** Sink for user-facing progress. Implemented by the UI; a no-op/console impl is used in tests. */
public interface ProgressReporter {
    void log(String message);
    void percent(int value);
}
