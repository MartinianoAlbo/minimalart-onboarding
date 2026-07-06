package co.minimalart.arcoronboarding.infra;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Consumer;

/** ProcessRunner backed by ProcessBuilder, streaming merged stdout/stderr to the log. */
public final class DefaultProcessRunner implements ProcessRunner {

    @Override
    public int run(Path workingDir, Consumer<String> log, String... command)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        if (workingDir != null) {
            pb.directory(workingDir.toFile());
        }
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.accept(line);
            }
        }
        return process.waitFor();
    }

    @Override
    public boolean isOnPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        for (String dir : path.split(java.io.File.pathSeparator)) {
            Path candidate = Path.of(dir, windows ? executable + ".exe" : executable);
            if (java.nio.file.Files.isRegularFile(candidate)) {
                return true;
            }
        }
        return false;
    }
}
