package co.minimalart.arcoronboarding.ui;

import co.minimalart.arcoronboarding.config.AppConfig;
import co.minimalart.arcoronboarding.domain.MysqlConnection;
import co.minimalart.arcoronboarding.domain.WpAdminUser;

import javax.swing.*;
import java.awt.*;
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
