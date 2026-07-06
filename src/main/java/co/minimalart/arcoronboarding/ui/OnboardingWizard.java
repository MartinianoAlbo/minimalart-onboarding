package co.minimalart.arcoronboarding.ui;

import co.minimalart.arcoronboarding.config.AppConfig;
import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.*;
import co.minimalart.arcoronboarding.steps.*;
import co.minimalart.arcoronboarding.ui.theme.BrandColors;

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
        band.setBackground(BrandColors.CELESTE);
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
