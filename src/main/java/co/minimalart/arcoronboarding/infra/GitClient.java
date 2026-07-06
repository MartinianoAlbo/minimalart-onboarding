package co.minimalart.arcoronboarding.infra;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Clones or updates a git repository by shelling out to the developer's git,
 * which reuses their existing SSH keys for the private Bitbucket repos. */
public final class GitClient {

    private final ProcessRunner runner;

    public GitClient(ProcessRunner runner) {
        this.runner = runner;
    }

    public boolean isAvailable() {
        return runner.isOnPath("git");
    }

    public void cloneOrPull(String repoUrl, String branch, Path targetDir, Consumer<String> log)
            throws IOException, InterruptedException {
        if (Files.isDirectory(targetDir.resolve(".git"))) {
            int code = runner.run(targetDir, log, "git", "pull", "--ff-only");
            if (code != 0) {
                throw new IOException("git pull failed (exit " + code + ") in " + targetDir);
            }
            return;
        }
        Files.createDirectories(targetDir.getParent());
        int code = runner.run(targetDir.getParent(), log,
            "git", "clone", "--branch", branch, repoUrl, targetDir.getFileName().toString());
        if (code != 0) {
            throw new IOException("git clone failed (exit " + code + ") for " + repoUrl);
        }
    }
}
