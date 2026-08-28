package se.anders.tunerstudio.aetuner.ui;

import se.anders.tunerstudio.aetuner.guided.GuidedAudioCueLabPanel;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.Timer;
import javax.swing.text.DefaultCaret;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.function.Supplier;

/**
 * Diagnostics workspace separated from tuning/capture surfaces.
 *
 * This panel intentionally owns presentation only. Runtime, recovery and audit
 * text are supplied by the existing subsystem owners so moving information out
 * of Passive Analysis does not create a second controller subscription.
 */
public final class EvidenceDiagnosticsPanel extends JPanel {
    private final JTabbedPane tabs = new StableTabbedPane();
    private final JTextArea overview = textArea();
    private final JTextArea runtime = textArea();
    private final JTextArea recoveryAudit = textArea();
    private final GuidedAudioCueLabPanel audioLab;
    private final Supplier<String> overviewSupplier;
    private final Supplier<String> runtimeSupplier;
    private final Supplier<String> recoveryAuditSupplier;
    private final Timer refreshTimer;

    public EvidenceDiagnosticsPanel(GuidedAudioCueLabPanel audioLab,
                                    Supplier<String> overviewSupplier,
                                    Supplier<String> runtimeSupplier,
                                    Supplier<String> recoveryAuditSupplier) {
        super(new BorderLayout(8, 8));
        this.audioLab = audioLab;
        this.overviewSupplier = overviewSupplier;
        this.runtimeSupplier = runtimeSupplier;
        this.recoveryAuditSupplier = recoveryAuditSupplier;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        tabs.addTab("Overview", scroll(overview));
        tabs.addTab("Channels / Runtime", scroll(runtime));
        tabs.addTab("Audio Cue Lab", audioLab);
        tabs.addTab("Recovery / Audit", scroll(recoveryAudit));
        tabs.setToolTipTextAt(0,
                "High-level plugin health and evidence status.");
        tabs.setToolTipTextAt(1,
                "Controller/project state, resolved live channels and runtime diagnostics.");
        tabs.setToolTipTextAt(2,
                "Stationary generated-tone editor and Guided workflow cue demonstrations.");
        tabs.setToolTipTextAt(3,
                "Automatic recovery state plus the latest Guided Apply/Restore audit status.");
        tabs.setFocusable(false);
        tabs.setRequestFocusEnabled(false);
        add(tabs, BorderLayout.CENTER);

        tabs.addChangeListener(event -> updateAudioLifecycle());
        refreshTimer = new Timer(500, event -> refreshText());
        refreshText();
    }

    private static JTextArea textArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        area.setFocusable(false);
        ((DefaultCaret) area.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        return area;
    }

    private static JComponent scroll(JTextArea area) {
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getVerticalScrollBar().setBlockIncrement(90);
        return scroll;
    }

    private void refreshText() {
        setText(overview, overviewSupplier);
        setText(runtime, runtimeSupplier);
        setText(recoveryAudit, recoveryAuditSupplier);
    }

    private static void setText(JTextArea area, Supplier<String> supplier) {
        String next;
        try {
            next = supplier == null ? "Unavailable." : supplier.get();
        } catch (RuntimeException ex) {
            next = "Diagnostic view unavailable: "
                    + (ex.getMessage() == null ? ex.getClass().getSimpleName()
                    : ex.getMessage());
        }
        if (next == null) next = "Unavailable.";
        if (!next.equals(area.getText())) {
            int caret = area.getCaretPosition();
            area.setText(next);
            area.setCaretPosition(Math.min(caret, area.getDocument().getLength()));
        }
    }

    private void updateAudioLifecycle() {
        if (tabs.getSelectedIndex() == 2) {
            audioLab.resumePanel();
        } else {
            audioLab.disposePanel();
        }
    }

    public void selectAudioCueLab() {
        tabs.setSelectedIndex(2);
        updateAudioLifecycle();
    }

    public void resumePanel() {
        if (!refreshTimer.isRunning()) refreshTimer.start();
        refreshText();
        updateAudioLifecycle();
    }

    public void disposePanel() {
        refreshTimer.stop();
        audioLab.disposePanel();
    }

    public int tabCountForTest() {
        return tabs.getTabCount();
    }

    public String tabTitleForTest(int index) {
        return tabs.getTitleAt(index);
    }
}
