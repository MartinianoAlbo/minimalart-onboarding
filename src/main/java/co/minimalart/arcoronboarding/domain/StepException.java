package co.minimalart.arcoronboarding.domain;

/** Thrown by a step to signal a developer-facing failure with a clear message. */
public class StepException extends Exception {
    public StepException(String message) { super(message); }
    public StepException(String message, Throwable cause) { super(message, cause); }
}
