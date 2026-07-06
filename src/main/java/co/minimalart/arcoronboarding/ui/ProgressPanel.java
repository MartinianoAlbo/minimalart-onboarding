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
