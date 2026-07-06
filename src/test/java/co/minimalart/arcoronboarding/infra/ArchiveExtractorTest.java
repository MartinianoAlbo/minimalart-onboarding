package co.minimalart.arcoronboarding.infra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArchiveExtractorTest {

    private final ArchiveExtractor extractor = new ArchiveExtractor();

    @Test
    void returnsSqlFileAsIs(@TempDir Path dir) throws IOException {
        Path sql = Files.writeString(dir.resolve("arcorencasa.sql"), "SELECT 1;");
        assertEquals(sql, extractor.resolveSqlFile(sql, dir));
    }

    @Test
    void extractsSqlEntryFromZip(@TempDir Path dir) throws IOException {
        Path zip = dir.resolve("arcorencasa.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("arcorencasa.sql"));
            zos.write("SELECT 42;".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Path out = extractor.resolveSqlFile(zip, dir);
        assertEquals("SELECT 42;", Files.readString(out));
    }

    @Test
    void rejectsUnsupportedExtension(@TempDir Path dir) throws IOException {
        Path txt = Files.writeString(dir.resolve("dump.txt"), "x");
        assertThrows(IllegalArgumentException.class, () -> extractor.resolveSqlFile(txt, dir));
    }
}
