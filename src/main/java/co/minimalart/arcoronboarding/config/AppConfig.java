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
