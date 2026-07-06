package co.minimalart.arcoronboarding.infra;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Runs external processes. Abstracted so GitClient is testable without git installed. */
public interface ProcessRunner {
    int run(Path workingDir, Consumer<String> log, String... command)
        throws IOException, InterruptedException;

    boolean isOnPath(String executable);
}
