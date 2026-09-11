package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModules;
import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;

/** v0.19 Decel Detection focus over the production falling-TPS evidence route. */
final class GuidedV019DecelDetectionFocus extends GuidedV019FocusBase {
    private final JLabel cue = label("● READY", 27, Font.BOLD, AeUiTheme.focusBlue());
    private final JTextArea action = textArea("Start Capture from the main workflow.", 16, Font.BOLD);
    private final JTextArea guidance = textArea("", 11, Font.PLAIN);
    private final JLabel stateValue = smallValue();
    private final JLabel reviewValue = smallValue();
    private final JLabel thresholdAuthority = smallValue();
    private final JLabel holdAuthority = smallValue();

    GuidedV019DecelDetectionFocus(JDialog owner, Runnable done) {
        super(owner, done);
        build();
    }

    @Override String taskTitle() { return "Decel Detection"; }
    @Override String taskSubtitle() { return "Driver-guided falling-TPS threshold evidence"; }

    @Override JComponent drivingContent() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setOpaque(false);
        root.add(sequence(new String[][]{
                {"1", "QUIET", "freeze noise/rate baseline", "green"},
                {"2", "CORRECT", "small pedal reduction", "blue"},
                {"3", "RELEASE", "clear normal lift", "amber"},
                {"4", "SEPARATE", "lock 3+3 evidence", "blue"},
                {"5", "REVIEW", "threshold only", "gray"}
        }), BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 2, 8, 0));
        center.setOpaque(false);
        center.add(chartCard("What counts as useful evidence", new FallingTpsInstructionChart()));

        JPanel coach = card(new BorderLayout(0, 8));
        coach.setBorder(cardBorder());
        JPanel heading = new JPanel(new BorderLayout(0, 3));
        heading.setOpaque(false);
        heading.add(cue, BorderLayout.NORTH);
        heading.add(action, BorderLayout.SOUTH);
        coach.add(heading, BorderLayout.NORTH);
        guidance.setOpaque(false);
        guidance.setForeground(AeUiTheme.focusText());
        coach.add(guidance, BorderLayout.CENTER);
        JLabel boundary = label(
                "INSTRUCTIONAL VIEW — signed falling-TPS production evidence owns the recommendation",
                9, Font.BOLD, AeUiTheme.focusMuted());
        coach.add(boundary, BorderLayout.SOUTH);
        center.add(coach);
        root.add(center, BorderLayout.CENTER);

        JPanel lower = new JPanel(new GridLayout(1, 2, 8, 0));
        lower.setOpaque(false);
        lower.add(summaryGrid("Production session",
                new String[]{"State", "Review", "Capture writes"},
                new JLabel[]{stateValue, reviewValue, fixed("NONE")}));
        lower.add(summaryGrid("Recommendation authority",
                new String[]{"Threshold curve", "Threshold 0", "Hold cycles"},
                new JLabel[]{thresholdAuthority, fixed("never auto-enabled"), holdAuthority}));
        lower.setPreferredSize(new java.awt.Dimension(100, 94));
        root.add(lower, BorderLayout.SOUTH);
        return root;
    }

    @Override JComponent diagnosticsContent() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(AeUiTheme.focusBackground());
        root.setBorder(new javax.swing.border.EmptyBorder(8, 10, 8, 10));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(label("Decel Detection — Diagnostics", 22, Font.BOLD,
                AeUiTheme.focusText()), BorderLayout.NORTH);
        head.add(label("Firmware sign/disable semantics and production review boundary.",
                11, Font.PLAIN, AeUiTheme.focusMuted()), BorderLayout.SOUTH);
        root.add(head, BorderLayout.NORTH);

        JPanel body = new JPanel(new GridLayout(1, 2, 8, 0));
        body.setOpaque(false);
        body.add(notePanel("Firmware authority",
                "Fuel: TPS AE change is signed. Closing movement is negative; the tune stores a positive threshold magnitude. Firmware triggers only when decelTps < -DecelThreshold. A threshold of 0 disables detection at that RPM. Fuel: TPS Decel Active includes the hold-cycle state and therefore is diagnostic around the edge, not a replacement for signed edge evidence.",
                AeUiTheme.focusSoftBlue(), AeUiTheme.focusBlue()));
        body.add(notePanel("Current production review",
                GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.DECEL_DETECTION).reviewOutputs(),
                AeUiTheme.neutralSoft(), AeUiTheme.navy()));
        root.add(body, BorderLayout.CENTER);
        return root;
    }

    @Override void refreshFromProduction() {
        GuidedFocusHub.State state = GuidedFocusHub.snapshot();
        boolean matching = state != null && state.recipe == GuidedTuningRecipe.DECEL_DETECTION;
        GuidedCaptureState capture = matching ? state.captureState : GuidedCaptureState.IDLE;
        String activeGuidance = matching && state.guidance != null && state.guidance.trim().length() > 0
                ? state.guidance
                : GuidedTuningRecipe.DECEL_DETECTION.guidance;
        guidance.setText(activeGuidance);
        guidance.setCaretPosition(0);
        stateValue.setText(String.valueOf(capture));
        boolean complete = capture == GuidedCaptureState.COMPLETE;
        reviewValue.setText(complete ? "READY" : "LOCKED");
        review.setEnabled(complete);
        thresholdAuthority.setText("evidence-backed cells only");
        holdAuthority.setText("context / manual only");

        if (capture == GuidedCaptureState.COMPLETE) {
            cue.setText("● REVIEW READY");
            cue.setForeground(AeUiTheme.focusGreen());
            action.setText("Review the locked falling-TPS separation and threshold-only proposal.");
        } else if (capture == GuidedCaptureState.PAUSED) {
            cue.setText("● PAUSED");
            cue.setForeground(AeUiTheme.focusAmber());
            action.setText("Resume only when the requested correction or release can be repeated safely.");
        } else if (capture == GuidedCaptureState.CAPTURING) {
            cue.setText("● CAPTURING");
            cue.setForeground(AeUiTheme.focusBlue());
            action.setText("Begin steady. Then alternate small NORMAL CORRECTIONS and clear DECEL RELEASES across representative RPM regions. Do not change threshold or hold during capture.");
        } else {
            cue.setText("● READY");
            cue.setForeground(AeUiTheme.focusBlue());
            action.setText("Start Capture. The first evidence phase is a quiet / steady pedal calibration.");
        }
        repaint();
    }

    private static JLabel fixed(String text) {
        JLabel value = smallValue();
        value.setText(text);
        return value;
    }

    private static JTextArea textArea(String text, int size, int style) {
        JTextArea area = new JTextArea(text == null ? "" : text);
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFont(new Font("Dialog", style, size));
        area.setForeground(AeUiTheme.focusText());
        return area;
    }

    /** Static instruction only; no synthetic measurements are plotted. */
    private static final class FallingTpsInstructionChart extends JPanel {
        FallingTpsInstructionChart() { setBackground(AeUiTheme.card()); }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int l = 42, r = getWidth() - 24, t = 42, b = getHeight() - 42;
            int w = Math.max(60, r - l), h = Math.max(80, b - t);
            g2.setColor(AeUiTheme.chartGridSoft());
            for (int i = 0; i <= 4; i++) {
                int y = t + h * i / 4;
                g2.drawLine(l, y, r, y);
            }
            int thresholdY = t + h * 2 / 3;
            g2.setColor(AeUiTheme.red());
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER, 10f, new float[]{7, 5}, 0));
            g2.drawLine(l, thresholdY, r, thresholdY);

            int[] normalX = new int[]{l, l + w / 5, l + 2 * w / 5};
            int[] normalY = new int[]{t + h / 3, t + h / 2, t + h / 3};
            draw(g2, normalX, normalY, AeUiTheme.focusBlue(), 4f, false);
            int x = l + 3 * w / 5;
            g2.setColor(AeUiTheme.focusAmber());
            g2.setStroke(new BasicStroke(5f));
            g2.drawLine(x, t + h / 3, x, b - 18);
            g2.drawLine(x, b - 18, x + w / 7, b - 18);

            g2.setFont(new Font("Dialog", Font.BOLD, 10));
            g2.setColor(AeUiTheme.focusBlue());
            g2.drawString("NORMAL CORRECTION", l + 10, t + 20);
            g2.setColor(AeUiTheme.focusAmber());
            g2.drawString("DECEL RELEASE", x - 25, t + 20);
            g2.setColor(AeUiTheme.red());
            g2.drawString("positive threshold magnitude", Math.max(l + 10, r - 170), thresholdY - 7);
            g2.setColor(AeUiTheme.focusMuted());
            g2.setFont(new Font("Dialog", Font.BOLD, 9));
            g2.drawString("INSTRUCTION — signed closing movement points downward; NOT LIVE EVIDENCE", l, b + 24);
            g2.dispose();
        }
    }
}
