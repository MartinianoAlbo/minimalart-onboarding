package co.minimalart.arcoronboarding.infra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostsFileTest {

    @Test
    void detectsMissingAndPresentEntries(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("hosts"), "127.0.0.1 localhost\n");
        HostsFile hosts = new HostsFile(file);
        assertFalse(hosts.hasEntry("arcorencasa.local"));
        assertTrue(hosts.hasEntry("localhost"));
    }

    @Test
    void ignoresCommentedEntries(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("hosts"), "# 127.0.0.1 arcorencasa.local\n");
        assertFalse(new HostsFile(file).hasEntry("arcorencasa.local"));
    }

    @Test
    void addsEntryOnceAndIsIdempotent(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("hosts"), "127.0.0.1 localhost\n");
        HostsFile hosts = new HostsFile(file);
        assertTrue(hosts.addEntry("arcorencasa.local"));
        assertTrue(hosts.hasEntry("arcorencasa.local"));
        assertFalse(hosts.addEntry("arcorencasa.local"));
    }
}
