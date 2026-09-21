package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Locale;

/** Shared chrome/helpers copied from the validated v0.19 Guided Focus prototype. */
abstract class GuidedV019FocusBase extends JPanel {
    static final Font TITLE = new Font("Dialog", Font.BOLD, 24);
    static final Font H2 = new Font("Dialog", Font.BOLD, 16);
    static final Font H3 = new Font("Dialog", Font.BOLD, 13);
    static final Font BODY = new Font("Dialog", Font.PLAIN, 12);
    static final Font SMALL = new Font("Dialog", Font.PLAIN, 10);

    final JDialog owner;
    final Runnable onReviewReady;
    final JButton diagnostics = button("Options / Info");
    final JButton pauseCapture = button("Pause Capture");
    final JButton finishCapture = button("Finish Capture");
    final JButton exportEvidence = button("Export Current Session");
    final JButton review = button("Review Results");
    final Timer refreshTimer;
    private final JLabel captureAction = label("Start Capture from the main workflow.",
            10, Font.BOLD, AeUiTheme.focusMuted());
    private JDialog diagnosticsDialog;

    GuidedV019FocusBase(JDialog owner, Runnable onReviewReady) {
        this.owner = owner == null ? null : GuidedDialogLifecycle.own(owner);
        this.onReviewReady = onReviewReady;
        setLayout(new BorderLayout(0, 8));
        setBackground(AeUiTheme.focusBackground());
        setBorder(new EmptyBorder(8, 10, 8, 10));
        review.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                if (GuidedV019FocusBase.this.onReviewReady != null) GuidedV019FocusBase.this.onReviewReady.run();
                else if (GuidedV019FocusBase.this.owner != null) GuidedV019FocusBase.this.owner.dispose();
            }
        });
        pauseCapture.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                GuidedFocusHub.toggleActiveCapturePause();
                refreshFromProduction();
                refreshCaptureControls();
            }
        });
        finishCapture.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                if (GuidedFocusHub.canContinueActiveCapture()) {
                    GuidedFocusHub.continueActiveCapture();
                } else {
                    GuidedFocusHub.finishActiveCapture();
                }
                refreshFromProduction();
                refreshCaptureControls();
            }
        });
        exportEvidence.setToolTipText("Export the currently collected session report/CSV snapshot. This does not make incomplete evidence Review-ready.");
        exportEvidence.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                GuidedFocusHub.exportActiveSession();
                refreshCaptureControls();
            }
        });
        diagnostics.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { openDiagnostics(); }
        });
        refreshTimer = new Timer(150, new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                refreshFromProduction();
                refreshCaptureControls();
            }
        });
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) == 0L) return;
            if (isShowing()) {
                refreshFromProduction();
                refreshCaptureControls();
                refreshTimer.start();
            } else {
                refreshTimer.stop();
                if (diagnosticsDialog != null) {
                    diagnosticsDialog.dispose();
                    diagnosticsDialog = null;
                }
            }
        });
    }

    abstract String taskTitle();
    abstract String taskSubtitle();
    abstract JComponent drivingContent();
    abstract JComponent diagnosticsContent();
    abstract void refreshFromProduction();

    int refreshIntervalMillis() { return 150; }

    final void build() {
        refreshTimer.setDelay(refreshIntervalMillis());
        refreshTimer.setInitialDelay(refreshIntervalMillis());
        add(header(), BorderLayout.NORTH);
        add(drivingContent(), BorderLayout.CENTER);
        add(bottomBar(), BorderLayout.SOUTH);
        refreshFromProduction();
        refreshCaptureControls();
    }

    private JComponent header() {
        JPanel p = new JPanel(new BorderLayout(10, 0)); p.setOpaque(false);
        JLabel title = label(taskTitle(), 24, Font.BOLD, AeUiTheme.focusText());
        JLabel sub = label(taskSubtitle(), 12, Font.PLAIN, AeUiTheme.focusMuted());
        JPanel left = new JPanel(new BorderLayout(10, 0)); left.setOpaque(false);
        left.add(title, BorderLayout.WEST); left.add(sub, BorderLayout.CENTER);
        JLabel state = label("GUIDED FOCUS — DRIVING VIEW", 10, Font.BOLD, AeUiTheme.navy());
        p.add(left, BorderLayout.CENTER); p.add(state, BorderLayout.EAST);
        return p;
    }

    private JComponent bottomBar() {
        JPanel p = new JPanel(new BorderLayout(8, 0)); p.setOpaque(false);
        JPanel status = new JPanel(new BorderLayout(0, 2));
        status.setOpaque(false);
        status.add(captureAction, BorderLayout.NORTH);
        status.add(label("Production evidence model  •  capture is read-only  •  no Burn",
                9, Font.PLAIN, AeUiTheme.focusMuted()), BorderLayout.SOUTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0)); buttons.setOpaque(false);
        final JCheckBox alwaysOnTop = new JCheckBox("Always on top", false);
        alwaysOnTop.setOpaque(false); alwaysOnTop.setForeground(AeUiTheme.focusText()); alwaysOnTop.setFont(SMALL);
        alwaysOnTop.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                Window w = SwingUtilities.getWindowAncestor(GuidedV019FocusBase.this);
                if (w != null) w.setAlwaysOnTop(alwaysOnTop.isSelected());
            }
        });
        buttons.add(alwaysOnTop);
        buttons.add(diagnostics);
        buttons.add(pauseCapture);
        buttons.add(finishCapture);
        buttons.add(exportEvidence);
        buttons.add(review);
        p.add(status, BorderLayout.CENTER); p.add(buttons, BorderLayout.EAST);
        return p;
    }

    private void refreshCaptureControls() {
        GuidedCaptureState capture = GuidedFocusHub.activeCaptureState();
        boolean active = capture.isCaptureInProgress();
        boolean reviewReady = capture == GuidedCaptureState.COMPLETE
                && GuidedFocusHub.isActiveEvidenceReviewReady();
        boolean canContinue = capture == GuidedCaptureState.COMPLETE
                && !reviewReady && GuidedFocusHub.canContinueActiveCapture();
        pauseCapture.setEnabled(active);
        finishCapture.setEnabled(canContinue || active && GuidedFocusHub.canFinishCapture());
        finishCapture.setText(canContinue ? "Continue Capture" : "Finish Capture");
        exportEvidence.setEnabled(GuidedFocusHub.canExportActiveSession());
        review.setEnabled(reviewReady);
        pauseCapture.setText(capture == GuidedCaptureState.PAUSED ? "Resume Capture" : "Pause Capture");
        if (capture == GuidedCaptureState.CAPTURING) {
            captureAction.setText("CAPTURE ACTIVE — follow the maneuver shown above; when you have enough clean events, press Finish Capture.");
            captureAction.setForeground(AeUiTheme.focusBlue());
        } else if (capture == GuidedCaptureState.PAUSED) {
            captureAction.setText("CAPTURE PAUSED — resume when it is safe to repeat the requested maneuver, or finish with the evidence already collected.");
            captureAction.setForeground(AeUiTheme.focusAmber());
        } else if (capture == GuidedCaptureState.COMPLETE && reviewReady) {
            captureAction.setText("CAPTURE COMPLETE — evidence requirement satisfied. Export Evidence for this task, then Review Results.");
            captureAction.setForeground(AeUiTheme.focusGreen());
        } else if (capture == GuidedCaptureState.COMPLETE && canContinue) {
            captureAction.setText("CAPTURE STOPPED — evidence is still incomplete. Review and Export remain locked; press Continue Capture to keep the retained evidence and collect the remaining clean events.");
            captureAction.setForeground(AeUiTheme.focusAmber());
        } else if (capture == GuidedCaptureState.COMPLETE) {
            captureAction.setText("CAPTURE STOPPED — evidence is incomplete, but continuation is currently blocked by the production capture gate. Return to the main workflow and resolve the shown prerequisite before continuing.");
            captureAction.setForeground(AeUiTheme.focusAmber());
        } else {
            captureAction.setText("Start Capture from the main workflow; this Focus view will then coach the maneuver and show progress.");
            captureAction.setForeground(AeUiTheme.focusMuted());
        }
    }

    private void openDiagnostics() {
        if (diagnosticsDialog != null && diagnosticsDialog.isDisplayable()) {
            diagnosticsDialog.toFront(); diagnosticsDialog.requestFocus(); return;
        }
        Window host = SwingUtilities.getWindowAncestor(this);
        diagnosticsDialog = modelessDialog(host, taskTitle() + " — Diagnostics");
        diagnosticsDialog.setContentPane(diagnosticsContent());
        diagnosticsDialog.setMinimumSize(new Dimension(980, 600));
        diagnosticsDialog.setSize(1200, 680);
        diagnosticsDialog.setLocationRelativeTo(host);
        diagnosticsDialog.setVisible(true);
    }

    static JPanel card(LayoutManager layout) { JPanel p = new JPanel(layout); p.setBackground(AeUiTheme.card()); return p; }
    static javax.swing.border.Border cardBorder() { return new CompoundBorder(new LineBorder(AeUiTheme.border()), new EmptyBorder(8, 10, 8, 10)); }
    static JButton button(String text) { JButton b = new JButton(text); b.setFont(SMALL); b.setFocusPainted(false); b.setOpaque(true); b.setBackground(AeUiTheme.button()); b.setForeground(AeUiTheme.focusText()); b.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.focusButtonBorder()), new EmptyBorder(5, 8, 5, 8))); return b; }
    static JDialog modelessDialog(Window host, String title) { return GuidedDialogLifecycle.create(host, title, false); }
    static JLabel label(String text, int size, int style, Color color) { JLabel l = new JLabel(text); l.setFont(new Font("Dialog", style, size)); l.setForeground(color); return l; }
    static JLabel valueLabel() { return label("n/a", 20, Font.BOLD, AeUiTheme.focusText()); }
    static JLabel smallValue() { return label("n/a", 11, Font.BOLD, AeUiTheme.focusText()); }
    static JPanel chartCard(String title, JComponent content) { JPanel p = card(new BorderLayout(0, 5)); p.setBorder(cardBorder()); p.add(label(title, 13, Font.BOLD, AeUiTheme.focusText()), BorderLayout.NORTH); p.add(content, BorderLayout.CENTER); return p; }
    static JPanel liveTriple(String title, String k1, JLabel v1, String k2, JLabel v2, String k3, JLabel v3) { JPanel p = card(new BorderLayout(0, 5)); p.setBorder(cardBorder()); p.add(label(title, 13, Font.BOLD, AeUiTheme.focusText()), BorderLayout.NORTH); JPanel grid = new JPanel(new GridLayout(1, 3, 6, 0)); grid.setOpaque(false); grid.add(valueTile(k1, v1)); grid.add(valueTile(k2, v2)); grid.add(valueTile(k3, v3)); p.add(grid, BorderLayout.CENTER); return p; }
    static JPanel valueTile(String key, JLabel value) { JPanel p = new JPanel(new BorderLayout()); p.setOpaque(false); p.setBorder(new MatteBorder(0, 0, 0, 1, AeUiTheme.border())); p.add(label(key, 9, Font.PLAIN, AeUiTheme.focusMuted()), BorderLayout.NORTH); p.add(value, BorderLayout.CENTER); return p; }
    static JPanel summaryGrid(String title, String[] keys, JLabel[] values) { JPanel p = card(new BorderLayout(0, 4)); p.setBorder(cardBorder()); p.add(label(title, 13, Font.BOLD, AeUiTheme.focusText()), BorderLayout.NORTH); JPanel grid = new JPanel(new GridLayout(keys.length, 2, 4, 2)); grid.setOpaque(false); for (int i = 0; i < keys.length; i++) { grid.add(label(keys[i], 10, Font.PLAIN, AeUiTheme.focusMuted())); grid.add(values[i]); } p.add(grid, BorderLayout.CENTER); return p; }
    static JPanel notePanel(String title, String text, Color bg, Color accent) { JPanel p = new JPanel(new BorderLayout(4, 3)); p.setBackground(bg); p.setBorder(new CompoundBorder(new LineBorder(accent), new EmptyBorder(9, 10, 9, 10))); p.add(label(title, 14, Font.BOLD, accent), BorderLayout.NORTH); JTextArea a = new JTextArea(text == null ? "" : text); a.setEditable(false); a.setLineWrap(true); a.setWrapStyleWord(true); a.setOpaque(false); a.setFocusable(false); a.setFont(new Font("Dialog", Font.PLAIN, 11)); a.setForeground(AeUiTheme.focusText()); p.add(a, BorderLayout.CENTER); return p; }
    static JPanel infoTable(String title, String[][] rows) { JPanel root = card(new BorderLayout(0, 6)); root.setBorder(cardBorder()); root.add(label(title, 13, Font.BOLD, AeUiTheme.focusText()), BorderLayout.NORTH); JPanel grid = new JPanel(new GridLayout(rows.length, 2, 6, 3)); grid.setOpaque(false); for (String[] row : rows) { grid.add(label(row[0], 10, Font.PLAIN, AeUiTheme.focusMuted())); grid.add(label(row[1], 11, Font.BOLD, AeUiTheme.focusText())); } root.add(grid, BorderLayout.CENTER); return root; }
    static JScrollPane themedTableScroll(JTable table) { styleTable(table); JScrollPane s = new JScrollPane(table); s.setBorder(new LineBorder(AeUiTheme.border())); s.setBackground(AeUiTheme.card()); s.getViewport().setBackground(AeUiTheme.card()); s.getViewport().setOpaque(true); return s; }
    static void styleTable(final JTable table) { table.setGridColor(AeUiTheme.border()); table.setBackground(AeUiTheme.card()); table.setForeground(AeUiTheme.focusText()); table.setSelectionBackground(AeUiTheme.softBlue()); table.setSelectionForeground(AeUiTheme.focusText()); table.setFillsViewportHeight(true); table.getTableHeader().setBackground(AeUiTheme.tableHeader()); table.getTableHeader().setForeground(AeUiTheme.focusText()); table.getTableHeader().setFont(new Font("Dialog", Font.BOLD, 10)); table.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() { @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int column) { JLabel l = (JLabel) super.getTableCellRendererComponent(t, value, selected, focus, row, column); l.setOpaque(true); l.setBackground(AeUiTheme.tableHeader()); l.setForeground(AeUiTheme.focusText()); l.setFont(new Font("Dialog", Font.BOLD, 10)); l.setHorizontalAlignment(SwingConstants.CENTER); l.setBorder(new MatteBorder(0, 0, 1, 1, AeUiTheme.border())); return l; }}); }
    static JPanel sequence(String[][] items) { JPanel p = card(new GridLayout(1, items.length * 2 - 1, 5, 0)); p.setBorder(cardBorder()); for (int i = 0; i < items.length; i++) { Color accent = "green".equals(items[i][3]) ? AeUiTheme.focusGreen() : "blue".equals(items[i][3]) ? AeUiTheme.focusBlue() : "amber".equals(items[i][3]) ? AeUiTheme.focusAmber() : AeUiTheme.focusMuted(); Color bg = "green".equals(items[i][3]) ? AeUiTheme.focusSoftGreen() : "blue".equals(items[i][3]) ? AeUiTheme.focusSoftBlue() : "amber".equals(items[i][3]) ? AeUiTheme.softAmber() : AeUiTheme.neutralSoft(); JPanel box = new JPanel(new BorderLayout(5, 0)); box.setBackground(bg); box.setBorder(new LineBorder(accent)); box.add(label(items[i][0], 16, Font.BOLD, accent), BorderLayout.WEST); JPanel text = new JPanel(new BorderLayout()); text.setOpaque(false); text.add(label(items[i][1], 12, Font.BOLD, AeUiTheme.focusText()), BorderLayout.NORTH); text.add(label(items[i][2], 9, Font.PLAIN, AeUiTheme.focusMuted()), BorderLayout.SOUTH); box.add(text, BorderLayout.CENTER); p.add(box); if (i < items.length - 1) { JLabel arrow = label("→", 16, Font.BOLD, AeUiTheme.navy()); arrow.setHorizontalAlignment(SwingConstants.CENTER); p.add(arrow); }} return p; }
    static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }
    static void draw(Graphics2D g2, int[] xs, int[] ys, Color color, float width, boolean dashed) { g2.setColor(color); g2.setStroke(dashed ? new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{5f, 4f}, 0f) : new BasicStroke(width)); for (int i = 1; i < xs.length; i++) g2.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i]); }
    static String fmt(double v) { return Double.isFinite(v) ? String.format(Locale.ROOT, "%.2f", v) : "n/a"; }
    static String fmt0(double v) { return Double.isFinite(v) ? String.format(Locale.ROOT, "%.0f", v) : "n/a"; }
    static String fmtMillis(double seconds) { return Double.isFinite(seconds) ? String.format(Locale.ROOT, "%.0f ms", seconds * 1000.0) : "n/a"; }
    static String trim(String s, int max) { return s == null ? "" : s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…"; }
}
