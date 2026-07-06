package co.minimalart.arcoronboarding.infra;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlStatementSplitterTest {

    private final SqlStatementSplitter splitter = new SqlStatementSplitter();

    @Test
    void splitsSimpleStatements() {
        assertEquals(List.of("SELECT 1", "SELECT 2"), splitter.split("SELECT 1; SELECT 2;"));
    }

    @Test
    void ignoresSemicolonInsideSingleQuotedString() {
        assertEquals(
            List.of("INSERT INTO t VALUES ('a;b')"),
            splitter.split("INSERT INTO t VALUES ('a;b');"));
    }

    @Test
    void handlesBackslashEscapedQuote() {
        assertEquals(
            List.of("INSERT INTO t VALUES ('a\\'b;c')"),
            splitter.split("INSERT INTO t VALUES ('a\\'b;c');"));
    }

    @Test
    void handlesDoubledQuoteEscape() {
        assertEquals(
            List.of("INSERT INTO t VALUES ('a''b;c')"),
            splitter.split("INSERT INTO t VALUES ('a''b;c');"));
    }

    @Test
    void ignoresSemicolonInLineComment() {
        List<String> result = splitter.split("SELECT 1;\n-- a; b\nSELECT 2;");
        assertEquals(2, result.size());
        assertEquals("SELECT 1", result.get(0));
    }

    @Test
    void keepsExecutableConditionalCommentAsOneStatement() {
        assertEquals(
            List.of("/*!40101 SET NAMES utf8mb4 */"),
            splitter.split("/*!40101 SET NAMES utf8mb4 */;"));
    }

    @Test
    void honorsDelimiterChange() {
        String script = "DELIMITER $$\n"
            + "CREATE TRIGGER t BEGIN SELECT 1; SELECT 2; END$$\n"
            + "DELIMITER ;\n"
            + "SELECT 3;";
        List<String> result = splitter.split(script);
        assertEquals(2, result.size());
        assertEquals("SELECT 3", result.get(1));
    }

    @Test
    void ignoresTrailingWhitespaceOnlyChunk() {
        assertEquals(List.of("SELECT 1"), splitter.split("SELECT 1;   \n  "));
    }
}
