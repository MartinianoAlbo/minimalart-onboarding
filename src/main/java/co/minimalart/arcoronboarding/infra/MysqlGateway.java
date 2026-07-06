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
