package se.anders.tunerstudio.aetuner.ui;

import se.anders.tunerstudio.aetuner.guided.GuidedAudioCueLabPanel;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.Timer;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.DefaultCaret;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.function.Supplier;

/**
 * Diagnostics workspace separated from tuning/capture surfaces.
 *
 * Runtime, recovery and audit text are still supplied by their existing
 * subsystem owners. This class is presentation/navigation only and does not
 * create a second controller subscription or write path.
 */
public final class EvidenceDiagnosticsPanel extends JPanel {
    private static final String OVERVIEW = "overview";
    private static final String RUNTIME = "runtime";
    private static final String AUDIO = "audio";
    private static final String RECOVERY = "recovery";

    private final JTextArea overview = textArea(false);
    private final JTextArea runtime = textArea(true);
    private final JTextArea recoveryAudit = textArea(false);
    private final GuidedAudioCueLabPanel audioLab;
    private final Supplier<String> overviewSupplier;
    private final Supplier<String> runtimeSupplier;
    private final Supplier<String> recoveryAuditSupplier;
    private final Timer refreshTimer;
    private final AeUtilityWorkspacePanel workspace;

    public EvidenceDiagnosticsPanel(GuidedAudioCueLabPanel audioLab,
                                    Supplier<String> overviewSupplier,
                                    Supplier<String> runtimeSupplier,
                                    Supplier<String> recoveryAuditSupplier) {
        super(new BorderLayout());
        this.audioLab = audioLab;
        this.overviewSupplier = overviewSupplier;
        this.runtimeSupplier = runtimeSupplier;
        this.recoveryAuditSupplier = recoveryAuditSupplier;
        setBorder(BorderFactory.createEmptyBorder());

        workspace = new AeUtilityWorkspacePanel(
                "Evidence / Diagnostics",
                "Runtime health, channel resolution, audio verification and recovery audit. Read-only unless a tool explicitly states otherwise.");
        workspace.addSection(OVERVIEW, "Overview",
                "Plugin lifecycle, evidence and recovery status",
                textCard(overview));
        workspace.addSection(RUNTIME, "Channels / Runtime",
                "Controller, project and resolved live-channel diagnostics",
                textCard(runtime));
        workspace.addSection(AUDIO, "Audio Cue Lab",
                "Stationary cue profile editor and workflow demonstrations",
                audioCard());
        workspace.addSection(RECOVERY, "Recovery / Audit",
                "Local evidence recovery and guarded Apply/Restore audit",
                textCard(recoveryAudit));
        workspace.addSelectionListener(new Runnable() {
            @Override public void run() { updateAudioLifecycle(); }
        });
        add(workspace, BorderLayout.CENTER);

        refreshTimer = new Timer(500, event -> refreshText());
        refreshText();
    }

    private static JTextArea textArea(boolean monospace) {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(monospace ? Font.MONOSPACED : Font.SANS_SERIF,
                Font.PLAIN, monospace ? 11 : 12));
        area.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        area.setFocusable(false);
        ((DefaultCaret) area.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        return area;
    }

    private static JComponent textCard(JTextArea area) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(new CompoundBorder(
                new LineBorder(AeUiTheme.border()),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getVerticalScrollBar().setBlockIncrement(90);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private JComponent audioCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(new CompoundBorder(
                new LineBorder(AeUiTheme.border()),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        card.add(audioLab, BorderLayout.CENTER);
        return card;
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
        if (AUDIO.equals(workspace.selectedSectionId())) {
            audioLab.resumePanel();
        } else {
            audioLab.disposePanel();
        }
    }

    public void selectAudioCueLab() {
        workspace.selectSection(AUDIO);
        updateAudioLifecycle();
    }

    public void resumePanel() {
        if (!refreshTimer.isRunning()) refreshTimer.start();
        refreshText();
        workspace.applyTheme();
        updateAudioLifecycle();
    }

    public void disposePanel() {
        refreshTimer.stop();
        audioLab.disposePanel();
    }

    public int tabCountForTest() {
        return workspace.sectionCount();
    }

    public String tabTitleForTest(int index) {
        return workspace.sectionTitleAt(index);
    }

    String selectedSectionForTest() {
        return workspace.selectedSectionId();
    }
}
