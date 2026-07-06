package co.minimalart.arcoronboarding.infra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitClientTest {

    /** Records the last command it was asked to run and returns a fixed exit code. */
    static final class FakeRunner implements ProcessRunner {
        List<String> lastCommand = new ArrayList<>();
        Path lastWorkingDir;
        int exitCode = 0;

        @Override
        public int run(Path workingDir, Consumer<String> log, String... command) {
            this.lastWorkingDir = workingDir;
            this.lastCommand = List.of(command);
            return exitCode;
        }

        @Override
        public boolean isOnPath(String executable) {
            return true;
        }
    }

    @Test
    void clonesWhenTargetHasNoGitDir(@TempDir Path root) throws Exception {
        FakeRunner runner = new FakeRunner();
        Path target = root.resolve("wp-content/plugins/arcorencasa");
        new GitClient(runner).cloneOrPull(
            "git@bitbucket.org:gapwebapps/aec-plugin.git", "staging", target, msg -> {});
        assertEquals(
            List.of("git", "clone", "--branch", "staging",
                    "git@bitbucket.org:gapwebapps/aec-plugin.git", "arcorencasa"),
            runner.lastCommand);
        assertEquals(target.getParent(), runner.lastWorkingDir);
    }

    @Test
    void pullsWhenTargetIsAlreadyARepo(@TempDir Path root) throws Exception {
        FakeRunner runner = new FakeRunner();
        Path target = root.resolve("wp-content/themes/arcor");
        Files.createDirectories(target.resolve(".git"));
        new GitClient(runner).cloneOrPull(
            "git@bitbucket.org:gapwebapps/aec-theme.git", "master", target, msg -> {});
        assertEquals(List.of("git", "pull", "--ff-only"), runner.lastCommand);
        assertEquals(target, runner.lastWorkingDir);
    }

    @Test
    void throwsOnNonZeroExit(@TempDir Path root) {
        FakeRunner runner = new FakeRunner();
        runner.exitCode = 1;
        Path target = root.resolve("wp-content/plugins/arcorencasa");
        IOException ex = org.junit.jupiter.api.Assertions.assertThrows(
            IOException.class,
            () -> new GitClient(runner).cloneOrPull("url", "staging", target, msg -> {}));
        assertTrue(ex.getMessage().toLowerCase().contains("clone"));
    }
}
