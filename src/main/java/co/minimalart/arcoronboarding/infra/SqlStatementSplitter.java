package co.minimalart.arcoronboarding.infra;

import java.util.ArrayList;
import java.util.List;

/** Splits a MySQL dump script into individual statements, honoring quoted strings,
 * backslash and doubled-quote escapes, line/block comments and DELIMITER changes so
 * that semicolons inside data or comments never split a statement. Comment text is
 * kept in the emitted statement so executable conditional comments ({@code /*!...*}{@code /}) run. */
public final class SqlStatementSplitter {

    public List<String> split(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String delimiter = ";";
        int i = 0;
        int n = script.length();

        while (i < n) {
            if (atLineStart(script, i) && script.regionMatches(true, i, "DELIMITER ", 0, 10)) {
                int eol = lineEnd(script, i);
                delimiter = script.substring(i + 10, eol).trim();
                i = eol;
                continue;
            }
            char c = script.charAt(i);

            if (c == '#' || (c == '-' && i + 1 < n && script.charAt(i + 1) == '-'
                    && (i + 2 >= n || Character.isWhitespace(script.charAt(i + 2))))) {
                int eol = lineEnd(script, i);
                current.append(script, i, eol);
                i = eol;
                continue;
            }
            if (c == '/' && i + 1 < n && script.charAt(i + 1) == '*') {
                int end = script.indexOf("*/", i + 2);
                end = (end < 0) ? n : end + 2;
                current.append(script, i, end);
                i = end;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                i = appendQuoted(script, i, c, current);
                continue;
            }
            if (!delimiter.isEmpty() && script.regionMatches(i, delimiter, 0, delimiter.length())) {
                addIfNotBlank(statements, current);
                current.setLength(0);
                i += delimiter.length();
                continue;
            }
            current.append(c);
            i++;
        }
        addIfNotBlank(statements, current);
        return statements;
    }

    private void addIfNotBlank(List<String> out, StringBuilder buffer) {
        String stmt = buffer.toString().trim();
        if (!stmt.isEmpty()) out.add(stmt);
    }

    private int appendQuoted(String s, int start, char quote, StringBuilder out) {
        out.append(quote);
        int i = start + 1;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            out.append(c);
            if (c == '\\' && quote != '`' && i + 1 < n) {
                out.append(s.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == quote) {
                if (i + 1 < n && s.charAt(i + 1) == quote) {
                    out.append(quote);
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return i;
    }

    private boolean atLineStart(String s, int i) {
        int j = i - 1;
        while (j >= 0 && (s.charAt(j) == ' ' || s.charAt(j) == '\t')) j--;
        return j < 0 || s.charAt(j) == '\n' || s.charAt(j) == '\r';
    }

    private int lineEnd(String s, int i) {
        int idx = i;
        while (idx < s.length() && s.charAt(idx) != '\n') idx++;
        return idx;
    }
}
