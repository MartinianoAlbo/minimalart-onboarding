package co.minimalart.arcoronboarding.infra;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/** Reads and appends entries to the OS hosts file. The path is injectable so it can be
 * tested against a temp file and swapped per OS. */
public final class HostsFile {

    private final Path path;

    public HostsFile(Path path) {
        this.path = path;
    }

    public static HostsFile forCurrentOs() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path p = os.contains("win")
            ? Path.of("C:\\Windows\\System32\\drivers\\etc\\hosts")
            : Path.of("/etc/hosts");
        return new HostsFile(p);
    }

    public Path path() {
        return path;
    }

    public boolean hasEntry(String hostname) throws IOException {
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                continue;
            }
            for (String token : trimmed.split("\\s+")) {
                if (token.equalsIgnoreCase(hostname)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Appends "127.0.0.1 hostname" if absent. Returns true if it was added. */
    public boolean addEntry(String hostname) throws IOException {
        if (hasEntry(hostname)) {
            return false;
        }
        String entry = System.lineSeparator() + "127.0.0.1 " + hostname + System.lineSeparator();
        Files.writeString(path, entry, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        return true;
    }
}
