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
