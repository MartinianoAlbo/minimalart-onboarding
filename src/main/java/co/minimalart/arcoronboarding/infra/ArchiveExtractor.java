package co.minimalart.arcoronboarding.infra;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Resolves the dump file the developer selected to a plain .sql path,
 * extracting the first .sql entry from a .zip when needed. */
public final class ArchiveExtractor {

    public Path resolveSqlFile(Path input, Path targetDir) throws IOException {
        String name = input.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".sql")) {
            return input;
        }
        if (!name.endsWith(".zip")) {
            throw new IllegalArgumentException("Unsupported dump file (expected .sql or .zip): "
                + input.getFileName());
        }
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(input))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()
                        && entry.getName().toLowerCase(Locale.ROOT).endsWith(".sql")) {
                    Path out = targetDir.resolve(fileName(entry.getName()));
                    Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
                    return out;
                }
            }
        }
        throw new IOException("No .sql entry found inside " + input.getFileName());
    }

    private static String fileName(String entryName) {
        int slash = Math.max(entryName.lastIndexOf('/'), entryName.lastIndexOf('\\'));
        return slash >= 0 ? entryName.substring(slash + 1) : entryName;
    }
}
