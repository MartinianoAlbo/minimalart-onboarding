# WordPress Local Onboarding (Arcor en Casa) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A cross-OS Swing desktop wizard (single fat JAR) that automates the AeC WordPress local onboarding: clone plugin+theme, import the DB dump, create an admin user, add the host entry, configure `wp-config.php`, and activate plugin+theme.

**Architecture:** A `Step` pipeline (`OnboardingStep` implementations run by an `OnboardingRunner`) with a thin Swing view. Execution is self-contained (JDBC + direct file/DB edits) except `git clone`, which shells out to the dev's git to reuse their SSH keys. All logic lives outside the UI so it is unit-testable.

**Tech Stack:** Java 17, Maven (+ shade plugin), Swing + FlatLaf, MySQL Connector/J (JDBC), JUnit 5, Mockito.

**Reference spec:** `docs/superpowers/specs/2026-07-06-wp-local-onboarding-design.md`

**Known real values (no placeholders):**
- Plugin repo `git@bitbucket.org:gapwebapps/aec-plugin.git`, branch `staging`, target `wp-content/plugins/arcorencasa`, activation slug `arcorencasa/arcorencasa.php`.
- Theme repo `git@bitbucket.org:gapwebapps/aec-theme.git`, branch `master`, target `wp-content/themes/arcor`, stylesheet `arcor`.
- DB name `arcorencasa`, domain `arcorencasa.local`, Devilbox MySQL defaults `127.0.0.1:3306`, user `root`, empty password.
- Brand assets source dir: `~/.claude/skills/minimalart-brand/assets/`.

---

## File Structure

```
arcor-onboarding/
├─ pom.xml
├─ README.md
├─ config.properties                        # optional external override (documented, not committed with secrets)
└─ src/
   ├─ main/
   │  ├─ java/co/minimalart/arcoronboarding/
   │  │  ├─ App.java
   │  │  ├─ domain/
   │  │  │  ├─ MysqlConnection.java
   │  │  │  ├─ WpAdminUser.java
   │  │  │  ├─ RepoConfig.java
   │  │  │  ├─ OnboardingContext.java
   │  │  │  ├─ OnboardingStep.java
   │  │  │  ├─ ProgressReporter.java
   │  │  │  ├─ StepException.java
   │  │  │  └─ OnboardingRunner.java
   │  │  ├─ infra/
   │  │  │  ├─ PhpSerializer.java
   │  │  │  ├─ WordPressPasswordHasher.java
   │  │  │  ├─ SqlStatementSplitter.java
   │  │  │  ├─ ArchiveExtractor.java
   │  │  │  ├─ WpConfigFile.java
   │  │  │  ├─ HostsFile.java
   │  │  │  ├─ ProcessRunner.java
   │  │  │  ├─ DefaultProcessRunner.java
   │  │  │  ├─ GitClient.java
   │  │  │  └─ MysqlGateway.java
   │  │  ├─ config/
   │  │  │  └─ AppConfig.java
   │  │  ├─ steps/
   │  │  │  ├─ ConfigureWpConfigStep.java
   │  │  │  ├─ CloneRepositoriesStep.java
   │  │  │  ├─ ImportDatabaseDumpStep.java
   │  │  │  ├─ CreateAdminUserStep.java
   │  │  │  ├─ ActivatePluginAndThemeStep.java
   │  │  │  └─ RegisterHostEntryStep.java
   │  │  └─ ui/
   │  │     ├─ OnboardingWizard.java
   │  │     ├─ ParametersPanel.java
   │  │     ├─ ProgressPanel.java
   │  │     └─ theme/
   │  │        ├─ MinimalartTheme.java
   │  │        ├─ BrandColors.java
   │  │        └─ BrandFonts.java
   │  └─ resources/
   │     ├─ config.properties               # embedded defaults
   │     └─ brand/
   │        ├─ fonts/ (4 TTFs)
   │        └─ logos/ (logo + favicon PNGs)
   └─ test/java/co/minimalart/arcoronboarding/ (mirrors main)
```

---

## Task 1: Project scaffold (Maven + fat JAR)

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/co/minimalart/arcoronboarding/App.java`
- Create: `.gitignore`

- [ ] **Step 1: Create `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>co.minimalart</groupId>
  <artifactId>arcor-onboarding</artifactId>
  <version>1.0.0</version>
  <packaging>jar</packaging>

  <properties>
    <maven.compiler.release>17</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>com.formdev</groupId>
      <artifactId>flatlaf</artifactId>
      <version>3.4.1</version>
    </dependency>
    <dependency>
      <groupId>com.mysql</groupId>
      <artifactId>mysql-connector-j</artifactId>
      <version>8.4.0</version>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.10.2</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.mockito</groupId>
      <artifactId>mockito-core</artifactId>
      <version>5.11.0</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <finalName>arcor-onboarding</finalName>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.2.5</version>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.5.3</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
              <transformers>
                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                  <mainClass>co.minimalart.arcoronboarding.App</mainClass>
                </transformer>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: Create `.gitignore`**

```
target/
*.class
config.properties
```

- [ ] **Step 3: Create the `App` entry point stub**

`src/main/java/co/minimalart/arcoronboarding/App.java`:

```java
package co.minimalart.arcoronboarding;

/** Application entry point. Wiring is added in Task 21. */
public final class App {
    private App() {}

    public static void main(String[] args) {
        System.out.println("arcor-onboarding starting…");
    }
}
```

- [ ] **Step 4: Build to verify the toolchain and fat JAR**

Run: `mvn -q clean package`
Expected: BUILD SUCCESS and a runnable `target/arcor-onboarding.jar`.

- [ ] **Step 5: Verify the JAR runs**

Run: `java -jar target/arcor-onboarding.jar`
Expected: prints `arcor-onboarding starting…`

- [ ] **Step 6: Commit**

```bash
git add pom.xml .gitignore src/main/java/co/minimalart/arcoronboarding/App.java
git commit -m "chore: maven scaffold with fat JAR packaging"
```

---

## Task 2: Domain types

**Files:**
- Create: `domain/MysqlConnection.java`, `domain/WpAdminUser.java`, `domain/RepoConfig.java`, `domain/OnboardingContext.java`, `domain/OnboardingStep.java`, `domain/ProgressReporter.java`, `domain/StepException.java`
- Test: `test/.../domain/OnboardingContextTest.java`

- [ ] **Step 1: Write the failing test for context path helpers**

`src/test/java/co/minimalart/arcoronboarding/domain/OnboardingContextTest.java`:

```java
package co.minimalart.arcoronboarding.domain;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OnboardingContextTest {

    private OnboardingContext sample() {
        return new OnboardingContext(
            Path.of("/wp"),
            new MysqlConnection("127.0.0.1", 3306, "root", "", "arcorencasa"),
            Path.of("/tmp/dump.sql"),
            new WpAdminUser("admin", "secret", "dev@minimalart.co"),
            "http://arcorencasa.local",
            new RepoConfig("git@bitbucket.org:gapwebapps/aec-plugin.git", "staging", "wp-content/plugins/arcorencasa"),
            new RepoConfig("git@bitbucket.org:gapwebapps/aec-theme.git", "master", "wp-content/themes/arcor"));
    }

    @Test
    void resolvesWpConfigPath() {
        assertEquals(Path.of("/wp/wp-config.php"), sample().wpConfigPath());
    }

    @Test
    void resolvesRepoTargets() {
        assertEquals(Path.of("/wp/wp-content/plugins/arcorencasa"), sample().pluginTarget());
        assertEquals(Path.of("/wp/wp-content/themes/arcor"), sample().themeTarget());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=OnboardingContextTest test`
Expected: compilation failure (types do not exist yet).

- [ ] **Step 3: Create the records and interfaces**

`domain/MysqlConnection.java`:

```java
package co.minimalart.arcoronboarding.domain;

public record MysqlConnection(String host, int port, String user, String password, String database) {}
```

`domain/WpAdminUser.java`:

```java
package co.minimalart.arcoronboarding.domain;

public record WpAdminUser(String username, String password, String email) {}
```

`domain/RepoConfig.java`:

```java
package co.minimalart.arcoronboarding.domain;

/** A git repository plus the path (relative to the WP root) it is cloned into. */
public record RepoConfig(String url, String branch, String relativePath) {}
```

`domain/OnboardingContext.java`:

```java
package co.minimalart.arcoronboarding.domain;

import java.nio.file.Path;

public record OnboardingContext(
        Path wpRootPath,
        MysqlConnection mysql,
        Path dumpFile,
        WpAdminUser adminUser,
        String siteUrl,
        RepoConfig plugin,
        RepoConfig theme) {

    public Path wpConfigPath() {
        return wpRootPath.resolve("wp-config.php");
    }

    public Path pluginTarget() {
        return wpRootPath.resolve(plugin.relativePath());
    }

    public Path themeTarget() {
        return wpRootPath.resolve(theme.relativePath());
    }
}
```

`domain/ProgressReporter.java`:

```java
package co.minimalart.arcoronboarding.domain;

/** Sink for user-facing progress. Implemented by the UI; a no-op/console impl is used in tests. */
public interface ProgressReporter {
    void log(String message);
    void percent(int value);
}
```

`domain/StepException.java`:

```java
package co.minimalart.arcoronboarding.domain;

/** Thrown by a step to signal a developer-facing failure with a clear message. */
public class StepException extends Exception {
    public StepException(String message) { super(message); }
    public StepException(String message, Throwable cause) { super(message, cause); }
}
```

`domain/OnboardingStep.java`:

```java
package co.minimalart.arcoronboarding.domain;

/** One onboarding action. validate() must not mutate anything. */
public interface OnboardingStep {
    String name();
    void validate(OnboardingContext ctx) throws StepException;
    void execute(OnboardingContext ctx, ProgressReporter progress) throws StepException;
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=OnboardingContextTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/co/minimalart/arcoronboarding/domain src/test/java/co/minimalart/arcoronboarding/domain
git commit -m "feat(domain): onboarding context, step interface and value types"
```

---

## Task 3: PhpSerializer

**Files:**
- Create: `infra/PhpSerializer.java`
- Test: `test/.../infra/PhpSerializerTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/co/minimalart/arcoronboarding/infra/PhpSerializerTest.java`:

```java
package co.minimalart.arcoronboarding.infra;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PhpSerializerTest {

    @Test
    void serializesString() {
        assertEquals("s:13:\"administrator\";", PhpSerializer.serialize("administrator"));
    }

    @Test
    void serializesBooleanAndInt() {
        assertEquals("b:1;", PhpSerializer.serialize(true));
        assertEquals("b:0;", PhpSerializer.serialize(false));
        assertEquals("i:5;", PhpSerializer.serialize(5));
    }

    @Test
    void serializesActivePluginsList() {
        assertEquals(
            "a:1:{i:0;s:27:\"arcorencasa/arcorencasa.php\";}",
            PhpSerializer.serialize(List.of("arcorencasa/arcorencasa.php")));
    }

    @Test
    void serializesCapabilitiesMap() {
        Map<String, Boolean> caps = new LinkedHashMap<>();
        caps.put("administrator", true);
        assertEquals("a:1:{s:13:\"administrator\";b:1;}", PhpSerializer.serialize(caps));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=PhpSerializerTest test`
Expected: compilation failure (`PhpSerializer` missing).

- [ ] **Step 3: Implement `PhpSerializer`**

`infra/PhpSerializer.java`:

```java
package co.minimalart.arcoronboarding.infra;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Serializes the minimal subset of PHP's serialize() that WordPress options and
 * usermeta need: strings, ints, booleans, integer-indexed lists and string-keyed maps. */
public final class PhpSerializer {

    private PhpSerializer() {}

    public static String serialize(Object value) {
        if (value instanceof String s)   return serializeString(s);
        if (value instanceof Boolean b)  return "b:" + (b ? 1 : 0) + ";";
        if (value instanceof Integer i)  return "i:" + i + ";";
        if (value instanceof Long l)     return "i:" + l + ";";
        if (value instanceof List<?> l)  return serializeList(l);
        if (value instanceof Map<?, ?> m) return serializeMap(m);
        throw new IllegalArgumentException("Unsupported type: " + value.getClass());
    }

    private static String serializeString(String s) {
        int byteLength = s.getBytes(StandardCharsets.UTF_8).length;
        return "s:" + byteLength + ":\"" + s + "\";";
    }

    private static String serializeList(List<?> list) {
        StringBuilder sb = new StringBuilder("a:").append(list.size()).append(":{");
        for (int i = 0; i < list.size(); i++) {
            sb.append(serialize(i)).append(serialize(list.get(i)));
        }
        return sb.append("}").toString();
    }

    private static String serializeMap(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder("a:").append(map.size()).append(":{");
        for (Map.Entry<?, ?> e : map.entrySet()) {
            sb.append(serialize(e.getKey())).append(serialize(e.getValue()));
        }
        return sb.append("}").toString();
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=PhpSerializerTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/co/minimalart/arcoronboarding/infra/PhpSerializer.java src/test/java/co/minimalart/arcoronboarding/infra/PhpSerializerTest.java
git commit -m "feat(infra): PHP serializer for WordPress options and usermeta"
```

---

## Task 4: WordPressPasswordHasher (legacy MD5)

**Files:**
- Create: `infra/WordPressPasswordHasher.java`
- Test: `test/.../infra/WordPressPasswordHasherTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/co/minimalart/arcoronboarding/infra/WordPressPasswordHasherTest.java`:

```java
package co.minimalart.arcoronboarding.infra;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WordPressPasswordHasherTest {

    private final WordPressPasswordHasher hasher = new WordPressPasswordHasher();

    @Test
    void producesCanonicalMd5Hash() {
        // md5("password") is the canonical, widely published vector.
        assertEquals("5f4dcc3b5aa765d61d8327deb882cf99", hasher.hash("password"));
    }

    @Test
    void producesLowercase32CharHex() {
        String hash = hasher.hash("Correct-Horse-Battery-Staple");
        assertEquals(32, hash.length());
        assertEquals(hash.toLowerCase(), hash);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=WordPressPasswordHasherTest test`
Expected: compilation failure (`WordPressPasswordHasher` missing).

- [ ] **Step 3: Implement `WordPressPasswordHasher`**

`infra/WordPressPasswordHasher.java`:

```java
package co.minimalart.arcoronboarding.infra;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Produces a legacy 32-char MD5 password hash. WordPress's wp_check_password()
 * accepts a bare MD5 (hash length <= 32) and transparently upgrades it to the modern
 * scheme on the user's first successful login, so this works on every WP version.
 * Isolated on purpose: swapping to phpass later means changing only this class. */
public final class WordPressPasswordHasher {

    public String hash(String password) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(32);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=WordPressPasswordHasherTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/co/minimalart/arcoronboarding/infra/WordPressPasswordHasher.java src/test/java/co/minimalart/arcoronboarding/infra/WordPressPasswordHasherTest.java
git commit -m "feat(infra): WordPress-compatible legacy MD5 password hasher"
```

---

## Task 5: SqlStatementSplitter (highest-risk unit)

**Files:**
- Create: `infra/SqlStatementSplitter.java`
- Test: `test/.../infra/SqlStatementSplitterTest.java`

- [ ] **Step 1: Write the failing tests**

`src/test/java/co/minimalart/arcoronboarding/infra/SqlStatementSplitterTest.java`:

```java
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q -Dtest=SqlStatementSplitterTest test`
Expected: compilation failure (`SqlStatementSplitter` missing).

- [ ] **Step 3: Implement `SqlStatementSplitter`**

`infra/SqlStatementSplitter.java`:

```java
package co.minimalart.arcoronboarding.infra;

import java.util.ArrayList;
import java.util.List;

/** Splits a MySQL dump script into individual statements, honoring quoted strings,
 * backslash and doubled-quote escapes, line/block comments and DELIMITER changes so
 * that semicolons inside data or comments never split a statement. Comment text is
 * kept in the emitted statement so executable conditional comments (/*! ... *​/) run. */
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
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -Dtest=SqlStatementSplitterTest test`
Expected: PASS (all 8 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/co/minimalart/arcoronboarding/infra/SqlStatementSplitter.java src/test/java/co/minimalart/arcoronboarding/infra/SqlStatementSplitterTest.java
git commit -m "feat(infra): robust SQL dump statement splitter"
```

---

## Task 6: ArchiveExtractor

**Files:**
- Create: `infra/ArchiveExtractor.java`
- Test: `test/.../infra/ArchiveExtractorTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/co/minimalart/arcoronboarding/infra/ArchiveExtractorTest.java`:

```java
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=ArchiveExtractorTest test`
Expected: compilation failure (`ArchiveExtractor` missing).

- [ ] **Step 3: Implement `ArchiveExtractor`**

`infra/ArchiveExtractor.java`:

```java
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
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=ArchiveExtractorTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/co/minimalart/arcoronboarding/infra/ArchiveExtractor.java src/test/java/co/minimalart/arcoronboarding/infra/ArchiveExtractorTest.java
git commit -m "feat(infra): dump archive extractor (.sql/.zip)"
```

---

## Task 7: WpConfigFile

**Files:**
- Create: `infra/WpConfigFile.java`
- Test: `test/.../infra/WpConfigFileTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/co/minimalart/arcoronboarding/infra/WpConfigFileTest.java`:

```java
package co.minimalart.arcoronboarding.infra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WpConfigFileTest {

    private static final String SAMPLE = """
        <?php
        define( 'DB_NAME', 'old_db' );
        define( 'DB_USER', 'old_user' );
        $table_prefix = 'wp_';
        /* That's all, stop editing! Happy publishing. */
        require_once ABSPATH . 'wp-settings.php';
        """;

    private WpConfigFile write(Path dir, String content) throws IOException {
        Path p = Files.writeString(dir.resolve("wp-config.php"), content);
        return new WpConfigFile(p);
    }

    @Test
    void readsTablePrefix(@TempDir Path dir) throws IOException {
        assertEquals("wp_", write(dir, SAMPLE).readTablePrefix());
    }

    @Test
    void replacesExistingDefine(@TempDir Path dir) throws IOException {
        WpConfigFile cfg = write(dir, SAMPLE);
        cfg.setDefine("DB_NAME", "arcorencasa");
        String content = Files.readString(dir.resolve("wp-config.php"));
        assertTrue(content.contains("define( 'DB_NAME', 'arcorencasa' );"));
        assertTrue(!content.contains("old_db"));
    }

    @Test
    void insertsNewDefineBeforeStopMarker(@TempDir Path dir) throws IOException {
        WpConfigFile cfg = write(dir, SAMPLE);
        cfg.setDefine("WP_HOME", "http://arcorencasa.local");
        String content = Files.readString(dir.resolve("wp-config.php"));
        assertTrue(content.contains("define( 'WP_HOME', 'http://arcorencasa.local' );"));
        assertTrue(content.indexOf("WP_HOME") < content.indexOf("stop editing"));
    }

    @Test
    void emitsBooleanDefineWithoutQuotes(@TempDir Path dir) throws IOException {
        WpConfigFile cfg = write(dir, SAMPLE);
        cfg.setDefine("WP_DEBUG", true);
        String content = Files.readString(dir.resolve("wp-config.php"));
        assertTrue(content.contains("define( 'WP_DEBUG', true );"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=WpConfigFileTest test`
Expected: compilation failure (`WpConfigFile` missing).

- [ ] **Step 3: Implement `WpConfigFile`**

`infra/WpConfigFile.java`:

```java
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
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=WpConfigFileTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/co/minimalart/arcoronboarding/infra/WpConfigFile.java src/test/java/co/minimalart/arcoronboarding/infra/WpConfigFileTest.java
git commit -m "feat(infra): wp-config.php reader/editor"
```

---

## Task 8: HostsFile

**Files:**
- Create: `infra/HostsFile.java`
- Test: `test/.../infra/HostsFileTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/co/minimalart/arcoronboarding/infra/HostsFileTest.java`:

```java
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=HostsFileTest test`
Expected: compilation failure (`HostsFile` missing).

- [ ] **Step 3: Implement `HostsFile`**

`infra/HostsFile.java`:

```java
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
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=HostsFileTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/co/minimalart/arcoronboarding/infra/HostsFile.java src/test/java/co/minimalart/arcoronboarding/infra/HostsFileTest.java
git commit -m "feat(infra): OS-aware hosts file reader/appender"
```

---

## Task 9: ProcessRunner + GitClient

**Files:**
- Create: `infra/ProcessRunner.java`, `infra/DefaultProcessRunner.java`, `infra/GitClient.java`
- Test: `test/.../infra/GitClientTest.java`

- [ ] **Step 1: Write the failing test (fake runner records commands)**

`src/test/java/co/minimalart/arcoronboarding/infra/GitClientTest.java`:

```java
package co.minimalart.arcoronboarding.infra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitClientTest {

    /** Records the last command it was asked to run and returns a fixed exit code. */
    static final class FakeRunner implements ProcessRunner {
        List<String> lastCommand = new ArrayList<>();
        Path lastWorkingDir;
        int exitCode = 0;

        @Override
        public int run(Path workingDir, Consumer<String> log, String... command) {
            this.lastWorkingDir = workingDir;
            this.lastCommand = List.of(command);
            return exitCode;
        }

        @Override
        public boolean isOnPath(String executable) {
            return true;
        }
    }

    @Test
    void clonesWhenTargetHasNoGitDir(@TempDir Path root) throws Exception {
        FakeRunner runner = new FakeRunner();
        Path target = root.resolve("wp-content/plugins/arcorencasa");
        new GitClient(runner).cloneOrPull(
            "git@bitbucket.org:gapwebapps/aec-plugin.git", "staging", target, msg -> {});
        assertEquals(
            List.of("git", "clone", "--branch", "staging",
                    "git@bitbucket.org:gapwebapps/aec-plugin.git", "arcorencasa"),
            runner.lastCommand);
        assertEquals(target.getParent(), runner.lastWorkingDir);
    }

    @Test
    void pullsWhenTargetIsAlreadyARepo(@TempDir Path root) throws Exception {
        FakeRunner runner = new FakeRunner();
        Path target = root.resolve("wp-content/themes/arcor");
        Files.createDirectories(target.resolve(".git"));
        new GitClient(runner).cloneOrPull(
            "git@bitbucket.org:gapwebapps/aec-theme.git", "master", target, msg -> {});
        assertEquals(List.of("git", "pull", "--ff-only"), runner.lastCommand);
        assertEquals(target, runner.lastWorkingDir);
    }

    @Test
    void throwsOnNonZeroExit(@TempDir Path root) {
        FakeRunner runner = new FakeRunner();
        runner.exitCode = 1;
        Path target = root.resolve("wp-content/plugins/arcorencasa");
        IOException ex = org.junit.jupiter.api.Assertions.assertThrows(
            IOException.class,
            () -> new GitClient(runner).cloneOrPull("url", "staging", target, msg -> {}));
        assertTrue(ex.getMessage().toLowerCase().contains("clone"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=GitClientTest test`
Expected: compilation failure (`ProcessRunner`/`GitClient` missing).

- [ ] **Step 3: Implement `ProcessRunner`, `DefaultProcessRunner`, `GitClient`**

`infra/ProcessRunner.java`:

```java
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
```

`infra/DefaultProcessRunner.java`:

```java
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
```

`infra/GitClient.java`:

```java
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
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=GitClientTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/co/minimalart/arcoronboarding/infra/ProcessRunner.java src/main/java/co/minimalart/arcoronboarding/infra/DefaultProcessRunner.java src/main/java/co/minimalart/arcoronboarding/infra/GitClient.java src/test/java/co/minimalart/arcoronboarding/infra/GitClientTest.java
git commit -m "feat(infra): process runner and git clone/pull client"
```

---

## Task 10: MysqlGateway (JDBC)

> DB access can't be meaningfully unit-tested without a server; correctness is proven by the acceptance test (Task 22) against a real MySQL. Keep this class thin. If you want an automated check, an optional Testcontainers integration test is out of MVP scope.

**Files:**
- Create: `infra/MysqlGateway.java`

- [ ] **Step 1: Implement `MysqlGateway`**

`infra/MysqlGateway.java`:

```java
package co.minimalart.arcoronboarding.infra;

import co.minimalart.arcoronboarding.domain.MysqlConnection;
import co.minimalart.arcoronboarding.domain.WpAdminUser;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Self-contained MySQL access over JDBC for the onboarding steps. */
public final class MysqlGateway {

    private final MysqlConnection cfg;

    public MysqlGateway(MysqlConnection cfg) {
        this.cfg = cfg;
    }

    public boolean canConnect() {
        try (Connection ignored = connect(false)) {
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public void createDatabaseIfMissing() throws SQLException {
        try (Connection c = connect(false); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + cfg.database()
                + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    public void importStatements(List<String> statements, Consumer<String> log) throws SQLException {
        try (Connection c = connect(true); Statement st = c.createStatement()) {
            int i = 0;
            for (String stmt : statements) {
                st.execute(stmt);
                if (++i % 200 == 0) {
                    log.accept("Imported " + i + "/" + statements.size() + " statements");
                }
            }
            log.accept("Imported " + statements.size() + " statements");
        }
    }

    public void setOption(String prefix, String name, String value) throws SQLException {
        String sql = "INSERT INTO `" + prefix + "options` (option_name, option_value, autoload) "
            + "VALUES (?, ?, 'yes') ON DUPLICATE KEY UPDATE option_value = VALUES(option_value)";
        try (Connection c = connect(true); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    /** Inserts the admin user (or updates password/email if the login already exists),
     * then writes the administrator capabilities and user_level usermeta. */
    public void upsertAdminUser(String prefix, WpAdminUser user, String passwordHash,
                                String capabilitiesSerialized) throws SQLException {
        try (Connection c = connect(true)) {
            long userId = upsertUserRow(c, prefix, user, passwordHash);
            setMeta(c, prefix, userId, prefix + "capabilities", capabilitiesSerialized);
            setMeta(c, prefix, userId, prefix + "user_level", "10");
        }
    }

    private long upsertUserRow(Connection c, String prefix, WpAdminUser user, String passwordHash)
            throws SQLException {
        Long existing = findUserId(c, prefix, user.username());
        if (existing != null) {
            String update = "UPDATE `" + prefix + "users` SET user_pass = ?, user_email = ? "
                + "WHERE ID = ?";
            try (PreparedStatement ps = c.prepareStatement(update)) {
                ps.setString(1, passwordHash);
                ps.setString(2, user.email());
                ps.setLong(3, existing);
                ps.executeUpdate();
            }
            return existing;
        }
        String insert = "INSERT INTO `" + prefix + "users` "
            + "(user_login, user_pass, user_nicename, user_email, user_registered, "
            + " display_name) VALUES (?, ?, ?, ?, NOW(), ?)";
        try (PreparedStatement ps = c.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.username());
            ps.setString(2, passwordHash);
            ps.setString(3, user.username());
            ps.setString(4, user.email());
            ps.setString(5, user.username());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private Long findUserId(Connection c, String prefix, String login) throws SQLException {
        String sql = "SELECT ID FROM `" + prefix + "users` WHERE user_login = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, login);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private void setMeta(Connection c, String prefix, long userId, String key, String value)
            throws SQLException {
        String delete = "DELETE FROM `" + prefix + "usermeta` WHERE user_id = ? AND meta_key = ?";
        try (PreparedStatement ps = c.prepareStatement(delete)) {
            ps.setLong(1, userId);
            ps.setString(2, key);
            ps.executeUpdate();
        }
        String insert = "INSERT INTO `" + prefix + "usermeta` (user_id, meta_key, meta_value) "
            + "VALUES (?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(insert)) {
            ps.setLong(1, userId);
            ps.setString(2, key);
            ps.setString(3, value);
            ps.executeUpdate();
        }
    }

    private Connection connect(boolean withDatabase) throws SQLException {
        String url = "jdbc:mysql://" + cfg.host() + ":" + cfg.port() + "/"
            + (withDatabase ? cfg.database() : "")
            + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
        return DriverManager.getConnection(url, cfg.user(), cfg.password());
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Manual smoke test against Devilbox MySQL (optional but recommended)**

With Devilbox running, from a scratch `Main`, or a throwaway JShell, confirm `new MysqlGateway(new MysqlConnection("127.0.0.1",3306,"root","","arcorencasa")).canConnect()` returns `true`. (Definitive DB verification happens in Task 22.)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/co/minimalart/arcoronboarding/infra/MysqlGateway.java
git commit -m "feat(infra): JDBC MySQL gateway (import, options, admin user)"
```

---

## Task 11: AppConfig (defaults + external override)

**Files:**
- Create: `src/main/resources/config.properties`
- Create: `config/AppConfig.java`
- Test: `test/.../config/AppConfigTest.java`

- [ ] **Step 1: Create embedded defaults**

`src/main/resources/config.properties`:

```properties
plugin.repo.url=git@bitbucket.org:gapwebapps/aec-plugin.git
plugin.repo.branch=staging
plugin.relativePath=wp-content/plugins/arcorencasa
plugin.activationSlug=arcorencasa/arcorencasa.php

theme.repo.url=git@bitbucket.org:gapwebapps/aec-theme.git
theme.repo.branch=master
theme.relativePath=wp-content/themes/arcor
theme.stylesheet=arcor

db.name=arcorencasa
site.url=http://arcorencasa.local
site.hostname=arcorencasa.local

mysql.host=127.0.0.1
mysql.port=3306
mysql.user=root
mysql.password=
```

- [ ] **Step 2: Write the failing test**

`src/test/java/co/minimalart/arcoronboarding/config/AppConfigTest.java`:

```java
package co.minimalart.arcoronboarding.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AppConfigTest {

    @Test
    void loadsEmbeddedDefaults() {
        AppConfig config = AppConfig.load();
        assertEquals("git@bitbucket.org:gapwebapps/aec-plugin.git", config.plugin().url());
        assertEquals("staging", config.plugin().branch());
        assertEquals("wp-content/themes/arcor", config.theme().relativePath());
        assertEquals("arcorencasa", config.dbName());
        assertEquals("http://arcorencasa.local", config.siteUrl());
        assertEquals("arcorencasa.local", config.hostname());
        assertEquals("arcorencasa/arcorencasa.php", config.pluginActivationSlug());
        assertEquals("arcor", config.themeStylesheet());
        assertEquals("127.0.0.1", config.defaultMysqlHost());
        assertEquals(3306, config.defaultMysqlPort());
        assertEquals("root", config.defaultMysqlUser());
        assertEquals("", config.defaultMysqlPassword());
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `mvn -q -Dtest=AppConfigTest test`
Expected: compilation failure (`AppConfig` missing).

- [ ] **Step 4: Implement `AppConfig`**

`config/AppConfig.java`:

```java
package co.minimalart.arcoronboarding.config;

import co.minimalart.arcoronboarding.domain.RepoConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Loads onboarding defaults from the embedded config.properties, overlaying an optional
 * external ./config.properties so URLs/branches can change without recompiling. */
public final class AppConfig {

    private final Properties props;

    private AppConfig(Properties props) {
        this.props = props;
    }

    public static AppConfig load() {
        Properties props = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream("/config.properties")) {
            if (in == null) {
                throw new IllegalStateException("Embedded config.properties is missing");
            }
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load embedded config", e);
        }
        Path external = Path.of("config.properties");
        if (Files.isRegularFile(external)) {
            try (InputStream in = Files.newInputStream(external)) {
                props.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load external config.properties", e);
            }
        }
        return new AppConfig(props);
    }

    public RepoConfig plugin() {
        return new RepoConfig(get("plugin.repo.url"), get("plugin.repo.branch"),
            get("plugin.relativePath"));
    }

    public RepoConfig theme() {
        return new RepoConfig(get("theme.repo.url"), get("theme.repo.branch"),
            get("theme.relativePath"));
    }

    public String dbName()               { return get("db.name"); }
    public String siteUrl()              { return get("site.url"); }
    public String hostname()             { return get("site.hostname"); }
    public String pluginActivationSlug() { return get("plugin.activationSlug"); }
    public String themeStylesheet()      { return get("theme.stylesheet"); }
    public String defaultMysqlHost()     { return get("mysql.host"); }
    public int defaultMysqlPort()        { return Integer.parseInt(get("mysql.port")); }
    public String defaultMysqlUser()     { return get("mysql.user"); }
    public String defaultMysqlPassword() { return props.getProperty("mysql.password", ""); }

    private String get(String key) {
        String value = props.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("Missing config key: " + key);
        }
        return value;
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q -Dtest=AppConfigTest test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/config.properties src/main/java/co/minimalart/arcoronboarding/config/AppConfig.java src/test/java/co/minimalart/arcoronboarding/config/AppConfigTest.java
git commit -m "feat(config): embedded defaults with optional external override"
```

---

## Task 12: ConfigureWpConfigStep

**Files:**
- Create: `steps/ConfigureWpConfigStep.java`
- Test: `test/.../steps/ConfigureWpConfigStepTest.java`

- [ ] **Step 1: Write the failing test (real wp-config in a temp dir)**

`src/test/java/co/minimalart/arcoronboarding/steps/ConfigureWpConfigStepTest.java`:

```java
package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ConfigureWpConfigStepTest {

    private static final String WP_CONFIG = """
        <?php
        define( 'DB_NAME', 'old' );
        define( 'DB_USER', 'old' );
        define( 'DB_PASSWORD', 'old' );
        define( 'DB_HOST', 'old' );
        $table_prefix = 'wp_';
        /* That's all, stop editing! Happy publishing. */
        """;

    private OnboardingContext contextFor(Path wpRoot) {
        return new OnboardingContext(
            wpRoot,
            new MysqlConnection("127.0.0.1", 3306, "root", "secret", "arcorencasa"),
            wpRoot.resolve("dump.sql"),
            new WpAdminUser("admin", "pw", "dev@minimalart.co"),
            "http://arcorencasa.local",
            new RepoConfig("u", "staging", "wp-content/plugins/arcorencasa"),
            new RepoConfig("u", "master", "wp-content/themes/arcor"));
    }

    private final ProgressReporter noop = new ProgressReporter() {
        public void log(String m) {}
        public void percent(int v) {}
    };

    @Test
    void writesDbSettingsAndSiteConstants(@TempDir Path wpRoot) throws Exception {
        Files.writeString(wpRoot.resolve("wp-config.php"), WP_CONFIG);
        new ConfigureWpConfigStep().execute(contextFor(wpRoot), noop);
        String content = Files.readString(wpRoot.resolve("wp-config.php"));
        assertTrue(content.contains("define( 'DB_NAME', 'arcorencasa' );"));
        assertTrue(content.contains("define( 'DB_USER', 'root' );"));
        assertTrue(content.contains("define( 'DB_PASSWORD', 'secret' );"));
        assertTrue(content.contains("define( 'DB_HOST', '127.0.0.1:3306' );"));
        assertTrue(content.contains("define( 'WP_HOME', 'http://arcorencasa.local' );"));
        assertTrue(content.contains("define( 'WP_SITEURL', 'http://arcorencasa.local' );"));
    }

    @Test
    void validateFailsWhenWpConfigMissing(@TempDir Path wpRoot) {
        StepException ex = assertThrows(StepException.class,
            () -> new ConfigureWpConfigStep().validate(contextFor(wpRoot)));
        assertTrue(ex.getMessage().contains("wp-config.php"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=ConfigureWpConfigStepTest test`
Expected: compilation failure (`ConfigureWpConfigStep` missing).

- [ ] **Step 3: Implement `ConfigureWpConfigStep`**

`steps/ConfigureWpConfigStep.java`:

```java
package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.WpConfigFile;

import java.io.IOException;
import java.nio.file.Files;

/** Writes DB credentials and the WP_HOME/WP_SITEURL/WP_DEBUG constants into wp-config.php.
 * Defining WP_HOME/WP_SITEURL overrides the URLs stored in the dumped DB, which is what
 * lets us skip a search-replace. */
public final class ConfigureWpConfigStep implements OnboardingStep {

    @Override
    public String name() {
        return "Configure wp-config.php";
    }

    @Override
    public void validate(OnboardingContext ctx) throws StepException {
        if (!Files.isRegularFile(ctx.wpConfigPath())) {
            throw new StepException("wp-config.php not found at " + ctx.wpConfigPath()
                + " — is the WordPress root correct?");
        }
    }

    @Override
    public void execute(OnboardingContext ctx, ProgressReporter progress) throws StepException {
        WpConfigFile config = new WpConfigFile(ctx.wpConfigPath());
        MysqlConnection db = ctx.mysql();
        try {
            config.setDefine("DB_NAME", db.database());
            config.setDefine("DB_USER", db.user());
            config.setDefine("DB_PASSWORD", db.password());
            config.setDefine("DB_HOST", db.host() + ":" + db.port());
            config.setDefine("WP_HOME", ctx.siteUrl());
            config.setDefine("WP_SITEURL", ctx.siteUrl());
            config.setDefine("WP_DEBUG", true);
            progress.log("wp-config.php configured for " + ctx.siteUrl());
        } catch (IOException e) {
            throw new StepException("Failed to edit wp-config.php: " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=ConfigureWpConfigStepTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/co/minimalart/arcoronboarding/steps/ConfigureWpConfigStep.java src/test/java/co/minimalart/arcoronboarding/steps/ConfigureWpConfigStepTest.java
git commit -m "feat(steps): configure wp-config.php (DB + site URL constants)"
```

---

## Task 13: CloneRepositoriesStep

**Files:**
- Create: `steps/CloneRepositoriesStep.java`
- Test: `test/.../steps/CloneRepositoriesStepTest.java`

- [ ] **Step 1: Write the failing test (mock GitClient)**

`src/test/java/co/minimalart/arcoronboarding/steps/CloneRepositoriesStepTest.java`:

```java
package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.GitClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CloneRepositoriesStepTest {

    private final ProgressReporter noop = new ProgressReporter() {
        public void log(String m) {}
        public void percent(int v) {}
    };

    private OnboardingContext contextFor(Path wpRoot) {
        return new OnboardingContext(
            wpRoot,
            new MysqlConnection("127.0.0.1", 3306, "root", "", "arcorencasa"),
            wpRoot.resolve("dump.sql"),
            new WpAdminUser("admin", "pw", "dev@minimalart.co"),
            "http://arcorencasa.local",
            new RepoConfig("git@bitbucket.org:gapwebapps/aec-plugin.git", "staging", "wp-content/plugins/arcorencasa"),
            new RepoConfig("git@bitbucket.org:gapwebapps/aec-theme.git", "master", "wp-content/themes/arcor"));
    }

    @Test
    void clonesPluginAndTheme(@TempDir Path wpRoot) throws Exception {
        GitClient git = mock(GitClient.class);
        when(git.isAvailable()).thenReturn(true);
        OnboardingContext ctx = contextFor(wpRoot);

        new CloneRepositoriesStep(git).execute(ctx, noop);

        verify(git).cloneOrPull(eq("git@bitbucket.org:gapwebapps/aec-plugin.git"), eq("staging"),
            eq(ctx.pluginTarget()), any());
        verify(git).cloneOrPull(eq("git@bitbucket.org:gapwebapps/aec-theme.git"), eq("master"),
            eq(ctx.themeTarget()), any());
    }

    @Test
    void validateFailsWhenGitMissing(@TempDir Path wpRoot) {
        GitClient git = mock(GitClient.class);
        when(git.isAvailable()).thenReturn(false);
        StepException ex = assertThrows(StepException.class,
            () -> new CloneRepositoriesStep(git).validate(contextFor(wpRoot)));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().toLowerCase().contains("git"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=CloneRepositoriesStepTest test`
Expected: compilation failure (`CloneRepositoriesStep` missing).

- [ ] **Step 3: Implement `CloneRepositoriesStep`**

`steps/CloneRepositoriesStep.java`:

```java
package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.GitClient;

/** Clones (or pulls) the custom plugin and theme into the WordPress content dirs. */
public final class CloneRepositoriesStep implements OnboardingStep {

    private final GitClient git;

    public CloneRepositoriesStep(GitClient git) {
        this.git = git;
    }

    @Override
    public String name() {
        return "Clone plugin and theme";
    }

    @Override
    public void validate(OnboardingContext ctx) throws StepException {
        if (!git.isAvailable()) {
            throw new StepException("git was not found on your PATH — install git and retry.");
        }
    }

    @Override
    public void execute(OnboardingContext ctx, ProgressReporter progress) throws StepException {
        clone(ctx.plugin(), ctx.pluginTarget(), progress);
        clone(ctx.theme(), ctx.themeTarget(), progress);
    }

    private void clone(RepoConfig repo, java.nio.file.Path target, ProgressReporter progress)
            throws StepException {
        progress.log("Fetching " + repo.url() + " (" + repo.branch() + ")");
        try {
            git.cloneOrPull(repo.url(), repo.branch(), target, progress::log);
        } catch (Exception e) {
            throw new StepException("Failed to clone " + repo.url() + ": " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=CloneRepositoriesStepTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/co/minimalart/arcoronboarding/steps/CloneRepositoriesStep.java src/test/java/co/minimalart/arcoronboarding/steps/CloneRepositoriesStepTest.java
git commit -m "feat(steps): clone/pull plugin and theme repositories"
```

---

## Task 14: ImportDatabaseDumpStep

**Files:**
- Create: `steps/ImportDatabaseDumpStep.java`
- Test: `test/.../steps/ImportDatabaseDumpStepTest.java`

- [ ] **Step 1: Write the failing test (mock gateway, real splitter/extractor)**

`src/test/java/co/minimalart/arcoronboarding/steps/ImportDatabaseDumpStepTest.java`:

```java
package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.ArchiveExtractor;
import co.minimalart.arcoronboarding.infra.MysqlGateway;
import co.minimalart.arcoronboarding.infra.SqlStatementSplitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImportDatabaseDumpStepTest {

    private final ProgressReporter noop = new ProgressReporter() {
        public void log(String m) {}
        public void percent(int v) {}
    };

    private OnboardingContext contextWithDump(Path wpRoot, Path dump) {
        return new OnboardingContext(
            wpRoot,
            new MysqlConnection("127.0.0.1", 3306, "root", "", "arcorencasa"),
            dump,
            new WpAdminUser("admin", "pw", "dev@minimalart.co"),
            "http://arcorencasa.local",
            new RepoConfig("u", "staging", "wp-content/plugins/arcorencasa"),
            new RepoConfig("u", "master", "wp-content/themes/arcor"));
    }

    @Test
    void createsDbThenImportsSplitStatements(@TempDir Path dir) throws Exception {
        Path dump = Files.writeString(dir.resolve("arcorencasa.sql"), "SELECT 1; SELECT 2;");
        MysqlGateway gateway = mock(MysqlGateway.class);
        new ImportDatabaseDumpStep(gateway, new ArchiveExtractor(), new SqlStatementSplitter())
            .execute(contextWithDump(dir, dump), noop);

        verify(gateway).createDatabaseIfMissing();
        verify(gateway).importStatements(eq(List.of("SELECT 1", "SELECT 2")), any());
    }

    @Test
    void validateFailsWhenDumpMissing(@TempDir Path dir) {
        MysqlGateway gateway = mock(MysqlGateway.class);
        when(gateway.canConnect()).thenReturn(true);
        Path missing = dir.resolve("nope.sql");
        StepException ex = assertThrows(StepException.class,
            () -> new ImportDatabaseDumpStep(gateway, new ArchiveExtractor(), new SqlStatementSplitter())
                .validate(contextWithDump(dir, missing)));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().toLowerCase().contains("dump"));
    }

    @Test
    void validateFailsWhenMysqlUnreachable(@TempDir Path dir) throws Exception {
        Path dump = Files.writeString(dir.resolve("arcorencasa.sql"), "SELECT 1;");
        MysqlGateway gateway = mock(MysqlGateway.class);
        when(gateway.canConnect()).thenReturn(false);
        StepException ex = assertThrows(StepException.class,
            () -> new ImportDatabaseDumpStep(gateway, new ArchiveExtractor(), new SqlStatementSplitter())
                .validate(contextWithDump(dir, dump)));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().toLowerCase().contains("mysql"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=ImportDatabaseDumpStepTest test`
Expected: compilation failure (`ImportDatabaseDumpStep` missing).

- [ ] **Step 3: Implement `ImportDatabaseDumpStep`**

`steps/ImportDatabaseDumpStep.java`:

```java
package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.ArchiveExtractor;
import co.minimalart.arcoronboarding.infra.MysqlGateway;
import co.minimalart.arcoronboarding.infra.SqlStatementSplitter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Extracts the dump (if zipped), creates the database if missing, and imports the
 * dump statement by statement over JDBC. */
public final class ImportDatabaseDumpStep implements OnboardingStep {

    private final MysqlGateway gateway;
    private final ArchiveExtractor extractor;
    private final SqlStatementSplitter splitter;

    public ImportDatabaseDumpStep(MysqlGateway gateway, ArchiveExtractor extractor,
                                  SqlStatementSplitter splitter) {
        this.gateway = gateway;
        this.extractor = extractor;
        this.splitter = splitter;
    }

    @Override
    public String name() {
        return "Import database dump";
    }

    @Override
    public void validate(OnboardingContext ctx) throws StepException {
        if (!Files.isRegularFile(ctx.dumpFile())) {
            throw new StepException("Dump file not found: " + ctx.dumpFile());
        }
        if (!gateway.canConnect()) {
            throw new StepException("Cannot reach MySQL at " + ctx.mysql().host() + ":"
                + ctx.mysql().port() + " — check that your stack is running and the credentials.");
        }
    }

    @Override
    public void execute(OnboardingContext ctx, ProgressReporter progress) throws StepException {
        try {
            Path sql = extractor.resolveSqlFile(ctx.dumpFile(), ctx.dumpFile().getParent());
            progress.log("Reading dump " + sql.getFileName());
            String script = Files.readString(sql);
            List<String> statements = splitter.split(script);
            progress.log("Parsed " + statements.size() + " SQL statements");
            gateway.createDatabaseIfMissing();
            gateway.importStatements(statements, progress::log);
        } catch (Exception e) {
            throw new StepException("Database import failed: " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=ImportDatabaseDumpStepTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/co/minimalart/arcoronboarding/steps/ImportDatabaseDumpStep.java src/test/java/co/minimalart/arcoronboarding/steps/ImportDatabaseDumpStepTest.java
git commit -m "feat(steps): import database dump over JDBC"
```

---

## Task 15: CreateAdminUserStep

**Files:**
- Create: `steps/CreateAdminUserStep.java`
- Test: `test/.../steps/CreateAdminUserStepTest.java`

- [ ] **Step 1: Write the failing test (mock gateway, real hasher)**

`src/test/java/co/minimalart/arcoronboarding/steps/CreateAdminUserStepTest.java`:

```java
package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.MysqlGateway;
import co.minimalart.arcoronboarding.infra.WordPressPasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CreateAdminUserStepTest {

    private final ProgressReporter noop = new ProgressReporter() {
        public void log(String m) {}
        public void percent(int v) {}
    };

    @Test
    void writesUserWithMd5PasswordAndAdministratorCaps(@TempDir Path wpRoot) throws Exception {
        Files.writeString(wpRoot.resolve("wp-config.php"), "<?php\n$table_prefix = 'wp_';\n");
        MysqlGateway gateway = mock(MysqlGateway.class);
        OnboardingContext ctx = new OnboardingContext(
            wpRoot,
            new MysqlConnection("127.0.0.1", 3306, "root", "", "arcorencasa"),
            wpRoot.resolve("dump.sql"),
            new WpAdminUser("admin", "password", "dev@minimalart.co"),
            "http://arcorencasa.local",
            new RepoConfig("u", "staging", "wp-content/plugins/arcorencasa"),
            new RepoConfig("u", "master", "wp-content/themes/arcor"));

        new CreateAdminUserStep(gateway, new WordPressPasswordHasher()).execute(ctx, noop);

        // md5("password") and the serialized administrator capabilities map.
        verify(gateway).upsertAdminUser(
            eq("wp_"),
            eq(ctx.adminUser()),
            eq("5f4dcc3b5aa765d61d8327deb882cf99"),
            eq("a:1:{s:13:\"administrator\";b:1;}"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=CreateAdminUserStepTest test`
Expected: compilation failure (`CreateAdminUserStep` missing).

- [ ] **Step 3: Implement `CreateAdminUserStep`**

`steps/CreateAdminUserStep.java`:

```java
package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.MysqlGateway;
import co.minimalart.arcoronboarding.infra.PhpSerializer;
import co.minimalart.arcoronboarding.infra.WordPressPasswordHasher;
import co.minimalart.arcoronboarding.infra.WpConfigFile;

import java.util.LinkedHashMap;
import java.util.Map;

/** Creates (or updates) the WordPress admin user directly in the imported database,
 * with a WP-compatible password hash and administrator capabilities. */
public final class CreateAdminUserStep implements OnboardingStep {

    private final MysqlGateway gateway;
    private final WordPressPasswordHasher hasher;

    public CreateAdminUserStep(MysqlGateway gateway, WordPressPasswordHasher hasher) {
        this.gateway = gateway;
        this.hasher = hasher;
    }

    @Override
    public String name() {
        return "Create admin user";
    }

    @Override
    public void validate(OnboardingContext ctx) throws StepException {
        WpAdminUser user = ctx.adminUser();
        if (user.username().isBlank() || user.password().isBlank() || user.email().isBlank()) {
            throw new StepException("Admin user requires a username, password and email.");
        }
    }

    @Override
    public void execute(OnboardingContext ctx, ProgressReporter progress) throws StepException {
        try {
            String prefix = new WpConfigFile(ctx.wpConfigPath()).readTablePrefix();
            Map<String, Boolean> caps = new LinkedHashMap<>();
            caps.put("administrator", true);
            gateway.upsertAdminUser(prefix, ctx.adminUser(),
                hasher.hash(ctx.adminUser().password()), PhpSerializer.serialize(caps));
            progress.log("Admin user '" + ctx.adminUser().username() + "' ready.");
        } catch (Exception e) {
            throw new StepException("Failed to create admin user: " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=CreateAdminUserStepTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/co/minimalart/arcoronboarding/steps/CreateAdminUserStep.java src/test/java/co/minimalart/arcoronboarding/steps/CreateAdminUserStepTest.java
git commit -m "feat(steps): create WordPress admin user in imported DB"
```

---

## Task 16: ActivatePluginAndThemeStep

**Files:**
- Create: `steps/ActivatePluginAndThemeStep.java`
- Test: `test/.../steps/ActivatePluginAndThemeStepTest.java`

> The activation slug (`arcorencasa/arcorencasa.php`) and stylesheet (`arcor`) come from `AppConfig`. Pass them into the step via constructor so it stays free of config lookups and is easy to test.

- [ ] **Step 1: Write the failing test**

`src/test/java/co/minimalart/arcoronboarding/steps/ActivatePluginAndThemeStepTest.java`:

```java
package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.MysqlGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.mockito.Mockito.*;

class ActivatePluginAndThemeStepTest {

    private final ProgressReporter noop = new ProgressReporter() {
        public void log(String m) {}
        public void percent(int v) {}
    };

    @Test
    void setsActivePluginsAndThemeOptions(@TempDir Path wpRoot) throws Exception {
        Files.writeString(wpRoot.resolve("wp-config.php"), "<?php\n$table_prefix = 'wp_';\n");
        MysqlGateway gateway = mock(MysqlGateway.class);
        OnboardingContext ctx = new OnboardingContext(
            wpRoot,
            new MysqlConnection("127.0.0.1", 3306, "root", "", "arcorencasa"),
            wpRoot.resolve("dump.sql"),
            new WpAdminUser("admin", "pw", "dev@minimalart.co"),
            "http://arcorencasa.local",
            new RepoConfig("u", "staging", "wp-content/plugins/arcorencasa"),
            new RepoConfig("u", "master", "wp-content/themes/arcor"));

        new ActivatePluginAndThemeStep(gateway, "arcorencasa/arcorencasa.php", "arcor")
            .execute(ctx, noop);

        verify(gateway).setOption("wp_", "active_plugins",
            "a:1:{i:0;s:27:\"arcorencasa/arcorencasa.php\";}");
        verify(gateway).setOption("wp_", "template", "arcor");
        verify(gateway).setOption("wp_", "stylesheet", "arcor");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=ActivatePluginAndThemeStepTest test`
Expected: compilation failure (`ActivatePluginAndThemeStep` missing).

- [ ] **Step 3: Implement `ActivatePluginAndThemeStep`**

`steps/ActivatePluginAndThemeStep.java`:

```java
package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.MysqlGateway;
import co.minimalart.arcoronboarding.infra.PhpSerializer;
import co.minimalart.arcoronboarding.infra.WpConfigFile;

import java.util.List;

/** Activates the custom plugin and theme by writing the relevant wp_options rows. */
public final class ActivatePluginAndThemeStep implements OnboardingStep {

    private final MysqlGateway gateway;
    private final String pluginActivationSlug;
    private final String themeStylesheet;

    public ActivatePluginAndThemeStep(MysqlGateway gateway, String pluginActivationSlug,
                                      String themeStylesheet) {
        this.gateway = gateway;
        this.pluginActivationSlug = pluginActivationSlug;
        this.themeStylesheet = themeStylesheet;
    }

    @Override
    public String name() {
        return "Activate plugin and theme";
    }

    @Override
    public void validate(OnboardingContext ctx) throws StepException {
        // Nothing to validate before running; wp-config presence is covered by earlier steps.
    }

    @Override
    public void execute(OnboardingContext ctx, ProgressReporter progress) throws StepException {
        try {
            String prefix = new WpConfigFile(ctx.wpConfigPath()).readTablePrefix();
            String activePlugins = PhpSerializer.serialize(List.of(pluginActivationSlug));
            gateway.setOption(prefix, "active_plugins", activePlugins);
            gateway.setOption(prefix, "template", themeStylesheet);
            gateway.setOption(prefix, "stylesheet", themeStylesheet);
            progress.log("Activated plugin '" + pluginActivationSlug
                + "' and theme '" + themeStylesheet + "'.");
        } catch (Exception e) {
            throw new StepException("Failed to activate plugin/theme: " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=ActivatePluginAndThemeStepTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/co/minimalart/arcoronboarding/steps/ActivatePluginAndThemeStep.java src/test/java/co/minimalart/arcoronboarding/steps/ActivatePluginAndThemeStepTest.java
git commit -m "feat(steps): activate plugin and theme via wp_options"
```

---

## Task 17: RegisterHostEntryStep

**Files:**
- Create: `steps/RegisterHostEntryStep.java`
- Test: `test/.../steps/RegisterHostEntryStepTest.java`

- [ ] **Step 1: Write the failing test (inject a temp-file HostsFile)**

`src/test/java/co/minimalart/arcoronboarding/steps/RegisterHostEntryStepTest.java`:

```java
package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.HostsFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterHostEntryStepTest {

    private final ProgressReporter noop = new ProgressReporter() {
        public void log(String m) {}
        public void percent(int v) {}
    };

    private OnboardingContext ctx(Path wpRoot) {
        return new OnboardingContext(
            wpRoot,
            new MysqlConnection("127.0.0.1", 3306, "root", "", "arcorencasa"),
            wpRoot.resolve("dump.sql"),
            new WpAdminUser("admin", "pw", "dev@minimalart.co"),
            "http://arcorencasa.local",
            new RepoConfig("u", "staging", "wp-content/plugins/arcorencasa"),
            new RepoConfig("u", "master", "wp-content/themes/arcor"));
    }

    @Test
    void appendsHostEntry(@TempDir Path dir) throws Exception {
        Path hostsPath = Files.writeString(dir.resolve("hosts"), "127.0.0.1 localhost\n");
        new RegisterHostEntryStep(new HostsFile(hostsPath), "arcorencasa.local")
            .execute(ctx(dir), noop);
        assertTrue(Files.readString(hostsPath).contains("127.0.0.1 arcorencasa.local"));
    }

    @Test
    void doesNotThrowWhenPermissionDenied(@TempDir Path dir) throws Exception {
        // A hosts path that cannot be written (points at a directory) must degrade gracefully.
        HostsFile unwritable = new HostsFile(dir);
        new RegisterHostEntryStep(unwritable, "arcorencasa.local").execute(ctx(dir), noop);
        // No exception thrown => graceful degradation.
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=RegisterHostEntryStepTest test`
Expected: compilation failure (`RegisterHostEntryStep` missing).

- [ ] **Step 3: Implement `RegisterHostEntryStep`**

`steps/RegisterHostEntryStep.java`:

```java
package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.HostsFile;

/** Adds "127.0.0.1 arcorencasa.local" to the OS hosts file. If it can't (no elevation),
 * it does not fail the run — it logs the exact line for the developer to add manually. */
public final class RegisterHostEntryStep implements OnboardingStep {

    private final HostsFile hostsFile;
    private final String hostname;

    public RegisterHostEntryStep(HostsFile hostsFile, String hostname) {
        this.hostsFile = hostsFile;
        this.hostname = hostname;
    }

    @Override
    public String name() {
        return "Register host entry";
    }

    @Override
    public void validate(OnboardingContext ctx) {
        // No pre-validation: this step degrades gracefully at execution time.
    }

    @Override
    public void execute(OnboardingContext ctx, ProgressReporter progress) {
        try {
            if (hostsFile.addEntry(hostname)) {
                progress.log("Added '127.0.0.1 " + hostname + "' to " + hostsFile.path());
            } else {
                progress.log("Host entry for " + hostname + " already present.");
            }
        } catch (Exception e) {
            progress.log("Could not edit " + hostsFile.path() + " (" + e.getMessage() + ").");
            progress.log("Add this line manually with admin rights: 127.0.0.1 " + hostname);
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=RegisterHostEntryStepTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/co/minimalart/arcoronboarding/steps/RegisterHostEntryStep.java src/test/java/co/minimalart/arcoronboarding/steps/RegisterHostEntryStepTest.java
git commit -m "feat(steps): register arcorencasa.local host entry (graceful fallback)"
```

---

## Task 18: OnboardingRunner

**Files:**
- Create: `domain/OnboardingRunner.java`
- Test: `test/.../domain/OnboardingRunnerTest.java`

- [ ] **Step 1: Write the failing test (fake steps record call order)**

`src/test/java/co/minimalart/arcoronboarding/domain/OnboardingRunnerTest.java`:

```java
package co.minimalart.arcoronboarding.domain;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OnboardingRunnerTest {

    private final List<String> events = new ArrayList<>();

    private final ProgressReporter recorder = new ProgressReporter() {
        public void log(String m) { events.add("log:" + m); }
        public void percent(int v) {}
    };

    private OnboardingContext anyContext() {
        return new OnboardingContext(Path.of("/wp"),
            new MysqlConnection("h", 1, "u", "", "d"), Path.of("/d.sql"),
            new WpAdminUser("a", "p", "e"), "http://arcorencasa.local",
            new RepoConfig("u", "b", "p"), new RepoConfig("u", "b", "p"));
    }

    private OnboardingStep step(String name, boolean failExecute) {
        return new OnboardingStep() {
            public String name() { return name; }
            public void validate(OnboardingContext c) { events.add("validate:" + name); }
            public void execute(OnboardingContext c, ProgressReporter p) throws StepException {
                events.add("execute:" + name);
                if (failExecute) throw new StepException("boom in " + name);
            }
        };
    }

    @Test
    void validatesAllThenExecutesInOrder() {
        OnboardingRunner runner = new OnboardingRunner(List.of(step("A", false), step("B", false)));
        assertTrue(runner.run(anyContext(), recorder));
        assertEquals(List.of("validate:A", "validate:B", "execute:A", "execute:B"),
            events.stream().filter(e -> !e.startsWith("log")).toList());
    }

    @Test
    void stopsAtFirstFailingStep() {
        OnboardingRunner runner = new OnboardingRunner(
            List.of(step("A", true), step("B", false)));
        assertFalse(runner.run(anyContext(), recorder));
        assertTrue(events.contains("execute:A"));
        assertFalse(events.contains("execute:B"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=OnboardingRunnerTest test`
Expected: compilation failure (`OnboardingRunner` missing).

- [ ] **Step 3: Implement `OnboardingRunner`**

`domain/OnboardingRunner.java`:

```java
package co.minimalart.arcoronboarding.domain;

import java.util.List;

/** Validates every step first (fail-fast, no mutation), then executes them in order,
 * stopping at the first failure. Returns true when all steps succeed. */
public final class OnboardingRunner {

    private final List<OnboardingStep> steps;

    public OnboardingRunner(List<OnboardingStep> steps) {
        this.steps = List.copyOf(steps);
    }

    public boolean run(OnboardingContext ctx, ProgressReporter progress) {
        try {
            for (OnboardingStep step : steps) {
                step.validate(ctx);
            }
        } catch (StepException e) {
            progress.log("✗ Validation failed: " + e.getMessage());
            return false;
        }

        int total = steps.size();
        for (int i = 0; i < total; i++) {
            OnboardingStep step = steps.get(i);
            progress.log("▶ " + step.name());
            try {
                step.execute(ctx, progress);
            } catch (StepException e) {
                progress.log("✗ " + step.name() + " failed: " + e.getMessage());
                return false;
            }
            progress.percent((int) Math.round((i + 1) * 100.0 / total));
        }
        progress.log("✓ Done. Open " + ctx.siteUrl());
        return true;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=OnboardingRunnerTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/co/minimalart/arcoronboarding/domain/OnboardingRunner.java src/test/java/co/minimalart/arcoronboarding/domain/OnboardingRunnerTest.java
git commit -m "feat(domain): onboarding runner (validate-all then execute)"
```

---

## Task 19: Brand assets + theme (Minimalart in Swing)

**Files:**
- Copy: 4 TTFs into `src/main/resources/brand/fonts/`
- Copy: logo + favicon PNGs into `src/main/resources/brand/logos/`
- Create: `ui/theme/BrandColors.java`, `ui/theme/BrandFonts.java`, `ui/theme/MinimalartTheme.java`
- Test: `test/.../ui/theme/BrandFontsTest.java`

- [ ] **Step 1: Copy brand assets into resources**

```bash
mkdir -p src/main/resources/brand/fonts src/main/resources/brand/logos
cp ~/.claude/skills/minimalart-brand/assets/fonts/Fraunces_72pt-SemiBold.ttf src/main/resources/brand/fonts/
cp ~/.claude/skills/minimalart-brand/assets/fonts/ZalandoSans-Regular.ttf src/main/resources/brand/fonts/
cp ~/.claude/skills/minimalart-brand/assets/fonts/ZalandoSans-Medium.ttf src/main/resources/brand/fonts/
cp ~/.claude/skills/minimalart-brand/assets/fonts/ZalandoSans-SemiBold.ttf src/main/resources/brand/fonts/
cp ~/.claude/skills/minimalart-brand/assets/logos/14_logo_negativo_fondo_transparente.png src/main/resources/brand/logos/
cp ~/.claude/skills/minimalart-brand/assets/logos/32_favicon_positivo_fondo_transparente.png src/main/resources/brand/logos/
```

- [ ] **Step 2: Implement `BrandColors`**

`ui/theme/BrandColors.java`:

```java
package co.minimalart.arcoronboarding.ui.theme;

import java.awt.Color;

/** Minimalart brand colors (from the brand tokens). No hex is hardcoded elsewhere. */
public final class BrandColors {

    private BrandColors() {}

    public static final Color CELESTE      = Color.decode("#4B8EEF"); // accent
    public static final Color CELESTE_PALE  = Color.decode("#B9D4FB");
    public static final Color NEUTRAL_1     = Color.decode("#0F172A"); // text (darkest)
    public static final Color NEUTRAL_5     = Color.decode("#64748B"); // muted text
    public static final Color NEUTRAL_8     = Color.decode("#E2E8F0"); // border
    public static final Color NEUTRAL_9     = Color.decode("#F1F5F9"); // sunken surface
    public static final Color NEUTRAL_10    = Color.decode("#F8FAFC"); // surface (lightest)
    public static final Color TEXT_ON_ACCENT = NEUTRAL_10;
}
```

- [ ] **Step 3: Write the failing test for `BrandFonts`**

`src/test/java/co/minimalart/arcoronboarding/ui/theme/BrandFontsTest.java`:

```java
package co.minimalart.arcoronboarding.ui.theme;

import org.junit.jupiter.api.Test;
import java.awt.Font;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BrandFontsTest {

    @Test
    void loadsBundledFonts() {
        BrandFonts fonts = BrandFonts.load();
        Font body = fonts.body(14f, Font.PLAIN);
        Font display = fonts.display(24f);
        assertNotNull(body);
        assertNotNull(display);
        assertEquals(14, body.getSize());
        assertEquals(24, display.getSize());
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `mvn -q -Dtest=BrandFontsTest test`
Expected: compilation failure (`BrandFonts` missing).

- [ ] **Step 5: Implement `BrandFonts`**

`ui/theme/BrandFonts.java`:

```java
package co.minimalart.arcoronboarding.ui.theme;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;

/** Loads the bundled Minimalart TTFs once and derives sized variants.
 * Body = Zalando Sans (UI/text); display = Fraunces SemiBold (titles). */
public final class BrandFonts {

    private final Font bodyBase;
    private final Font displayBase;

    private BrandFonts(Font bodyBase, Font displayBase) {
        this.bodyBase = bodyBase;
        this.displayBase = displayBase;
    }

    public static BrandFonts load() {
        Font body = register("/brand/fonts/ZalandoSans-Regular.ttf");
        register("/brand/fonts/ZalandoSans-Medium.ttf");
        register("/brand/fonts/ZalandoSans-SemiBold.ttf");
        Font display = register("/brand/fonts/Fraunces_72pt-SemiBold.ttf");
        return new BrandFonts(body, display);
    }

    public Font body(float size, int style) {
        return bodyBase.deriveFont(style, size);
    }

    public Font display(float size) {
        return displayBase.deriveFont(size);
    }

    private static Font register(String resource) {
        try (InputStream in = BrandFonts.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled font: " + resource);
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font;
        } catch (IOException | FontFormatException e) {
            throw new IllegalStateException("Failed to load font " + resource, e);
        }
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn -q -Dtest=BrandFontsTest test`
Expected: PASS.
(Note: on a truly headless CI this uses the headless toolkit, which still loads fonts. If a `HeadlessException` ever occurs, run with `-Djava.awt.headless=false`.)

- [ ] **Step 7: Implement `MinimalartTheme`**

`ui/theme/MinimalartTheme.java`:

```java
package co.minimalart.arcoronboarding.ui.theme;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.UIManager;
import java.awt.Font;

/** Installs FlatLaf and maps the Minimalart brand tokens onto Swing UI defaults. */
public final class MinimalartTheme {

    private MinimalartTheme() {}

    public static BrandFonts install() {
        FlatLightLaf.setup();
        BrandFonts fonts = BrandFonts.load();

        UIManager.put("defaultFont", fonts.body(14f, Font.PLAIN));

        // Accent + surfaces from the brand palette.
        UIManager.put("Component.focusColor", BrandColors.CELESTE);
        UIManager.put("Component.focusedBorderColor", BrandColors.CELESTE);
        UIManager.put("Button.default.background", BrandColors.CELESTE);
        UIManager.put("Button.default.foreground", BrandColors.TEXT_ON_ACCENT);
        UIManager.put("ProgressBar.foreground", BrandColors.CELESTE);
        UIManager.put("Panel.background", BrandColors.NEUTRAL_10);
        UIManager.put("Component.borderColor", BrandColors.NEUTRAL_8);

        // Rounded look.
        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        return fonts;
    }
}
```

- [ ] **Step 8: Verify build with resources**

Run: `mvn -q -DskipTests package`
Expected: BUILD SUCCESS; fonts/logos are inside `target/arcor-onboarding.jar` (check with `jar tf target/arcor-onboarding.jar | grep brand`).

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/brand src/main/java/co/minimalart/arcoronboarding/ui/theme src/test/java/co/minimalart/arcoronboarding/ui/theme
git commit -m "feat(ui): Minimalart brand theme for Swing (FlatLaf + fonts + colors)"
```

---

## Task 20: UI — ProgressPanel, ParametersPanel, OnboardingWizard

> Swing wiring is verified by a manual smoke test (Task 21/22), not unit tests. Keep zero business logic here — the panels only collect input and render progress.

**Files:**
- Create: `ui/ProgressPanel.java`, `ui/ParametersPanel.java`, `ui/OnboardingWizard.java`

- [ ] **Step 1: Implement `ProgressPanel` (implements ProgressReporter)**

`ui/ProgressPanel.java`:

```java
package co.minimalart.arcoronboarding.ui;

import co.minimalart.arcoronboarding.domain.ProgressReporter;

import javax.swing.*;
import java.awt.BorderLayout;

/** Progress bar + append-only log. All updates are marshalled onto the EDT. */
public final class ProgressPanel extends JPanel implements ProgressReporter {

    private final JProgressBar bar = new JProgressBar(0, 100);
    private final JTextArea logArea = new JTextArea();

    public ProgressPanel() {
        super(new BorderLayout(0, 8));
        bar.setStringPainted(true);
        logArea.setEditable(false);
        add(bar, BorderLayout.NORTH);
        add(new JScrollPane(logArea), BorderLayout.CENTER);
    }

    @Override
    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + System.lineSeparator());
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    @Override
    public void percent(int value) {
        SwingUtilities.invokeLater(() -> bar.setValue(value));
    }
}
```

- [ ] **Step 2: Implement `ParametersPanel`**

`ui/ParametersPanel.java`:

```java
package co.minimalart.arcoronboarding.ui;

import co.minimalart.arcoronboarding.config.AppConfig;
import co.minimalart.arcoronboarding.domain.MysqlConnection;
import co.minimalart.arcoronboarding.domain.WpAdminUser;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;

/** Form for the values that vary per developer, pre-filled with Devilbox defaults. */
public final class ParametersPanel extends JPanel {

    private final JTextField wpRoot = new JTextField(30);
    private final JTextField dumpFile = new JTextField(30);
    private final JTextField mysqlHost;
    private final JTextField mysqlPort;
    private final JTextField mysqlUser;
    private final JPasswordField mysqlPassword = new JPasswordField(16);
    private final JTextField adminUser = new JTextField("admin", 16);
    private final JPasswordField adminPassword = new JPasswordField("admin", 16);
    private final JTextField adminEmail = new JTextField("dev@minimalart.co", 20);

    public ParametersPanel(AppConfig config) {
        super(new GridBagLayout());
        mysqlHost = new JTextField(config.defaultMysqlHost(), 16);
        mysqlPort = new JTextField(String.valueOf(config.defaultMysqlPort()), 6);
        mysqlUser = new JTextField(config.defaultMysqlUser(), 16);
        mysqlPassword.setText(config.defaultMysqlPassword());

        int row = 0;
        addRow(row++, "WordPress root:", withBrowse(wpRoot, JFileChooser.DIRECTORIES_ONLY));
        addRow(row++, "Database dump (.sql/.zip):", withBrowse(dumpFile, JFileChooser.FILES_ONLY));
        addRow(row++, "MySQL host:", mysqlHost);
        addRow(row++, "MySQL port:", mysqlPort);
        addRow(row++, "MySQL user:", mysqlUser);
        addRow(row++, "MySQL password:", mysqlPassword);
        addRow(row++, "Admin username:", adminUser);
        addRow(row++, "Admin password:", adminPassword);
        addRow(row++, "Admin email:", adminEmail);
    }

    private JPanel withBrowse(JTextField field, int selectionMode) {
        JPanel panel = new JPanel(new BorderLayout(4, 0));
        panel.add(field, BorderLayout.CENTER);
        JButton browse = new JButton("Browse…");
        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(selectionMode);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        panel.add(browse, BorderLayout.EAST);
        return panel;
    }

    private void addRow(int row, String label, Component field) {
        GridBagConstraints l = new GridBagConstraints();
        l.gridx = 0; l.gridy = row; l.anchor = GridBagConstraints.LINE_END;
        l.insets = new Insets(4, 8, 4, 8);
        add(new JLabel(label), l);
        GridBagConstraints f = new GridBagConstraints();
        f.gridx = 1; f.gridy = row; f.anchor = GridBagConstraints.LINE_START;
        f.fill = GridBagConstraints.HORIZONTAL; f.weightx = 1.0;
        f.insets = new Insets(4, 0, 4, 8);
        add(field, f);
    }

    public Path wpRootPath() { return Path.of(wpRoot.getText().trim()); }

    public Path dumpFilePath() { return Path.of(dumpFile.getText().trim()); }

    public MysqlConnection mysqlConnection(String database) {
        return new MysqlConnection(
            mysqlHost.getText().trim(),
            Integer.parseInt(mysqlPort.getText().trim()),
            mysqlUser.getText().trim(),
            new String(mysqlPassword.getPassword()),
            database);
    }

    public WpAdminUser adminUser() {
        return new WpAdminUser(
            adminUser.getText().trim(),
            new String(adminPassword.getPassword()),
            adminEmail.getText().trim());
    }
}
```

- [ ] **Step 3: Implement `OnboardingWizard`**

`ui/OnboardingWizard.java`:

```java
package co.minimalart.arcoronboarding.ui;

import co.minimalart.arcoronboarding.config.AppConfig;
import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.*;
import co.minimalart.arcoronboarding.steps.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/** Main window: collects parameters, then runs the onboarding pipeline on a SwingWorker. */
public final class OnboardingWizard extends JFrame {

    private final AppConfig config;
    private final ParametersPanel parameters;
    private final ProgressPanel progress = new ProgressPanel();
    private final JButton runButton = new JButton("Run onboarding");

    public OnboardingWizard(AppConfig config) {
        super("Arcor en Casa — Local Onboarding");
        this.config = config;
        this.parameters = new ParametersPanel(config);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 8));
        add(header(), BorderLayout.NORTH);
        add(parameters, BorderLayout.CENTER);
        add(south(), BorderLayout.SOUTH);
        loadWindowIcon();
        pack();
        setMinimumSize(new Dimension(640, 560));
        setLocationRelativeTo(null);

        runButton.addActionListener(e -> runOnboarding());
    }

    private JComponent header() {
        JLabel logo = new JLabel();
        java.net.URL url = getClass().getResource("/brand/logos/14_logo_negativo_fondo_transparente.png");
        if (url != null) {
            Image img = new ImageIcon(url).getImage()
                .getScaledInstance(180, -1, Image.SCALE_SMOOTH);
            logo.setIcon(new ImageIcon(img));
        }
        JPanel band = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 12));
        band.setBackground(co.minimalart.arcoronboarding.ui.theme.BrandColors.CELESTE);
        band.add(logo);
        return band;
    }

    private JComponent south() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        runButton.putClientProperty("JButton.buttonType", "default");
        buttons.add(runButton);
        panel.add(buttons, BorderLayout.NORTH);
        progress.setPreferredSize(new Dimension(600, 200));
        panel.add(progress, BorderLayout.CENTER);
        return panel;
    }

    private void loadWindowIcon() {
        java.net.URL url = getClass().getResource("/brand/logos/32_favicon_positivo_fondo_transparente.png");
        if (url != null) {
            setIconImage(new ImageIcon(url).getImage());
        }
    }

    private void runOnboarding() {
        OnboardingContext ctx;
        OnboardingRunner runner;
        try {
            ctx = buildContext();
            runner = buildRunner(ctx.mysql());
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Check your inputs: " + ex.getMessage(),
                "Invalid parameters", JOptionPane.ERROR_MESSAGE);
            return;
        }
        runButton.setEnabled(false);
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() {
                return runner.run(ctx, progress);
            }
            @Override protected void done() {
                runButton.setEnabled(true);
            }
        }.execute();
    }

    private OnboardingContext buildContext() {
        return new OnboardingContext(
            parameters.wpRootPath(),
            parameters.mysqlConnection(config.dbName()),
            parameters.dumpFilePath(),
            parameters.adminUser(),
            config.siteUrl(),
            config.plugin(),
            config.theme());
    }

    private OnboardingRunner buildRunner(MysqlConnection mysql) {
        GitClient git = new GitClient(new DefaultProcessRunner());
        MysqlGateway gateway = new MysqlGateway(mysql);
        List<OnboardingStep> steps = List.of(
            new CloneRepositoriesStep(git),
            new ConfigureWpConfigStep(),
            new ImportDatabaseDumpStep(gateway, new ArchiveExtractor(), new SqlStatementSplitter()),
            new CreateAdminUserStep(gateway, new WordPressPasswordHasher()),
            new ActivatePluginAndThemeStep(gateway, config.pluginActivationSlug(), config.themeStylesheet()),
            new RegisterHostEntryStep(HostsFile.forCurrentOs(), config.hostname()));
        return new OnboardingRunner(steps);
    }
}
```

- [ ] **Step 4: Verify it compiles**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/co/minimalart/arcoronboarding/ui/ProgressPanel.java src/main/java/co/minimalart/arcoronboarding/ui/ParametersPanel.java src/main/java/co/minimalart/arcoronboarding/ui/OnboardingWizard.java
git commit -m "feat(ui): parameters form, progress panel and wizard window"
```

---

## Task 21: App entry point

**Files:**
- Modify: `App.java`

- [ ] **Step 1: Wire the theme + wizard into `App`**

Replace `App.java` with:

```java
package co.minimalart.arcoronboarding;

import co.minimalart.arcoronboarding.config.AppConfig;
import co.minimalart.arcoronboarding.ui.OnboardingWizard;
import co.minimalart.arcoronboarding.ui.theme.MinimalartTheme;

import javax.swing.SwingUtilities;

/** Application entry point: install the brand theme, then show the onboarding wizard. */
public final class App {

    private App() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MinimalartTheme.install();
            new OnboardingWizard(AppConfig.load()).setVisible(true);
        });
    }
}
```

- [ ] **Step 2: Build the fat JAR**

Run: `mvn -q clean package`
Expected: BUILD SUCCESS; all unit tests green.

- [ ] **Step 3: Manual smoke test (launches the window)**

Run: `java -jar target/arcor-onboarding.jar`
Expected: the wizard opens with the Minimalart header (celeste band + logo), the parameters form pre-filled with Devilbox defaults, and a "Run onboarding" button.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/co/minimalart/arcoronboarding/App.java
git commit -m "feat: wire theme and wizard into the app entry point"
```

---

## Task 22: Packaging, README and acceptance test

**Files:**
- Create: `README.md`

- [ ] **Step 1: Write `README.md`**

```markdown
# Arcor en Casa — Local Onboarding

Desktop wizard that sets up a local **Arcor en Casa** WordPress environment on top of a
WordPress + MySQL stack you already created (Devilbox, Local, XAMPP, …).

## Requirements
- Java 17+
- git (with your Bitbucket SSH key configured)
- A running, empty WordPress + MySQL stack

## What it does
1. Clones the plugin (`aec-plugin`) and theme (`aec-theme`) into `wp-content/`.
2. Configures `wp-config.php` (DB credentials, `WP_HOME`/`WP_SITEURL` = `http://arcorencasa.local`).
3. Imports the database dump you downloaded from Drive.
4. Creates a WordPress admin user.
5. Activates the plugin and theme.
6. Adds `127.0.0.1 arcorencasa.local` to your hosts file.

## Run
```
java -jar arcor-onboarding.jar
```
Fill the form (defaults match Devilbox), pick your WP root and the dump file, then
**Run onboarding**. When it finishes, open http://arcorencasa.local.

## Config overrides
Repo URLs, branches and defaults live in the embedded `config.properties`. To override
without rebuilding, drop a `config.properties` next to the JAR (same keys).

## Notes
- The hosts entry needs admin rights. If it can't be written, the log prints the exact
  line to add manually.
- Keep the domain `arcorencasa.local` and DB name `arcorencasa` to avoid URL rewrites.
```

- [ ] **Step 2: Build and run the full test suite**

Run: `mvn -q clean package`
Expected: BUILD SUCCESS; every unit test passes; `target/arcor-onboarding.jar` produced.

- [ ] **Step 3: End-to-end acceptance test (manual, definitive)**

On a machine with Devilbox running and an empty `arcorencasa` WordPress:
1. Launch the JAR, point WP root at `~/devilbox/data/www/arcorencasa/htdocs`, pick a real dump.
2. Run onboarding; watch the log complete all six steps.
3. Open `http://arcorencasa.local` — the AeC site renders with the `arcor` theme.
4. Log into `http://arcorencasa.local/wp-admin` with the admin user you set — confirms the
   MD5 password path works and the user has administrator capabilities.
5. Confirm the `arcorencasa` plugin is active in `wp-admin › Plugins`.

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: usage README and acceptance checklist"
```

---

## Self-Review

**1. Spec coverage:**
- Assume existing stack → the app never provisions WP/MySQL (Tasks 12–17 only touch an existing install). ✓
- Dump located via file picker → `ParametersPanel` browse button + `ImportDatabaseDumpStep` (Tasks 14, 20). ✓
- Swing GUI wizard → Tasks 19–21. ✓
- Self-contained execution + system git → JDBC `MysqlGateway`, direct file/DB edits, `GitClient` shell-out (Tasks 9, 10, 12, 15, 16). ✓
- Fat JAR → shade plugin (Task 1). ✓
- Step pipeline (Enfoque A) → `OnboardingStep`/`OnboardingRunner` (Tasks 2, 18). ✓
- All six config steps → Tasks 12–17. ✓
- Avoid search-replace via WP_HOME/WP_SITEURL → Task 12. ✓
- Branding via FlatLaf + tokens/fonts → Task 19. ✓
- Config defaults + external override → Task 11. ✓
- Validate-all-then-execute, idempotency, hosts graceful fallback → Tasks 18, 8, 17. ✓
- SQL import risk → `SqlStatementSplitter` with thorough tests (Task 5). ✓
- Testing strategy (logic units unit-tested; DB/UI manual) → Tasks 3–9, 11–18 unit; 10/20/21/22 manual. ✓
- MD5 hashing decision (spec updated) → Task 4. ✓

**2. Placeholder scan:** No TBD/TODO/"handle errors appropriately"; every code step shows full code. ✓

**3. Type consistency:** `OnboardingStep.validate/execute(throws StepException)`, `ProgressReporter.log/percent`, `MysqlGateway.upsertAdminUser(prefix, user, hash, capsSerialized)` and `setOption(prefix,name,value)` match across Tasks 10, 15, 16; `RepoConfig(url,branch,relativePath)` used consistently; `WpConfigFile.readTablePrefix/setDefine`, `GitClient.cloneOrPull(url,branch,target,log)` consistent. ✓
