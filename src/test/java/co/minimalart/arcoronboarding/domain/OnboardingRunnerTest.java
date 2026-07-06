package co.minimalart.arcoronboarding.domain;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OnboardingRunnerTest {

    private final List<String> events = new ArrayList<>();

    private final ProgressReporter recorder = new ProgressReporter() {
        public void log(String m) { events.add("log:" + m); }
        public void percent(int v) {}
    };

    private OnboardingContext anyContext() {
        return new OnboardingContext(Path.of("/wp"),
            new MysqlConnection("h", 1, "u", "", "d"), Path.of("/d.sql"),
            new WpAdminUser("a", "p", "e"), "http://arcorencasa.local",
            new RepoConfig("u", "b", "p"), new RepoConfig("u", "b", "p"));
    }

    private OnboardingStep step(String name, boolean failExecute) {
        return new OnboardingStep() {
            public String name() { return name; }
            public void validate(OnboardingContext c) { events.add("validate:" + name); }
            public void execute(OnboardingContext c, ProgressReporter p) throws StepException {
                events.add("execute:" + name);
                if (failExecute) throw new StepException("boom in " + name);
            }
        };
    }

    @Test
    void validatesAllThenExecutesInOrder() {
        OnboardingRunner runner = new OnboardingRunner(List.of(step("A", false), step("B", false)));
        assertTrue(runner.run(anyContext(), recorder));
        assertEquals(List.of("validate:A", "validate:B", "execute:A", "execute:B"),
            events.stream().filter(e -> !e.startsWith("log")).toList());
    }

    @Test
    void stopsAtFirstFailingStep() {
        OnboardingRunner runner = new OnboardingRunner(
            List.of(step("A", true), step("B", false)));
        assertFalse(runner.run(anyContext(), recorder));
        assertTrue(events.contains("execute:A"));
        assertFalse(events.contains("execute:B"));
    }
}
