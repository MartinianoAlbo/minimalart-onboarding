package co.minimalart.arcoronboarding.infra;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads and edits a wp-config.php file with primitive, sufficient operations:
 * read the table prefix and set/replace a define() constant. */
public final class WpConfigFile {

    private static final Pattern TABLE_PREFIX =
        Pattern.compile("\\$table_prefix\\s*=\\s*['\"]([^'\"]*)['\"]\\s*;");
    private static final String STOP_MARKER = "/* That's all, stop editing!";

    private final Path path;

    public WpConfigFile(Path path) {
        this.path = path;
    }

    public String readTablePrefix() throws IOException {
        Matcher m = TABLE_PREFIX.matcher(read());
        if (!m.find()) {
            throw new IOException("$table_prefix not found in " + path);
        }
        return m.group(1);
    }

    /** Replaces an existing define(NAME, ...) or inserts one before the "stop editing"
     * marker. Booleans are emitted bare (true/false); everything else is single-quoted. */
    public void setDefine(String name, Object value) throws IOException {
        String literal = (value instanceof Boolean) ? value.toString() : "'" + value + "'";
        String replacement = "define( '" + name + "', " + literal + " );";
        String content = read();
        Pattern existing = Pattern.compile(
            "define\\(\\s*['\"]" + Pattern.quote(name) + "['\"]\\s*,\\s*[^;]*\\)\\s*;");
        Matcher m = existing.matcher(content);
        String updated = m.find()
            ? m.replaceFirst(Matcher.quoteReplacement(replacement))
            : insertBeforeStopMarker(content, replacement);
        write(updated);
    }

    private String insertBeforeStopMarker(String content, String line) {
        int idx = content.indexOf(STOP_MARKER);
        String nl = System.lineSeparator();
        if (idx < 0) {
            return content + nl + line + nl;
        }
        return content.substring(0, idx) + line + nl + nl + content.substring(idx);
    }

    private String read() throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private void write(String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
