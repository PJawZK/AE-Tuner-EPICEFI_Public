package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalResultContext;
import se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalResultModel;
import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModules;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;

/** v0.19 Decel Detection focus over historical replay and production falling-TPS evidence. */
final class GuidedV019DecelDetectionFocus extends GuidedV019FocusBase {
    private final JLabel cue = label("● READY", 27, Font.BOLD, AeUiTheme.focusBlue());
    private final JTextArea action = textArea("Analyze an existing log first. Live capture is optional when historical evidence leaves gaps.", 16, Font.BOLD);
    private final JTextArea guidance = textArea("", 11, Font.PLAIN);
    private final JLabel stateValue = smallValue();
    private final JLabel reviewValue = smallValue();
    private final JLabel thresholdAuthority = smallValue();
    private final JLabel holdAuthority = smallValue();
    private final JButton analyzeExisting = new JButton("Analyze Existing Log…");

    GuidedV019DecelDetectionFocus(JDialog owner, Runnable done) {
        super(owner, done);
        build();
    }

    @Override String taskTitle() { return "Decel Detection"; }
    @Override String taskSubtitle() { return "Existing-log result first; live capture only for RPM regions the result identifies as missing"; }

    @Override JComponent drivingContent() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setOpaque(false);
        root.add(sequence(new String[][]{
                {"1", "IMPORT", "prior AE Tuner / TS log", "blue"},
                {"2", "VERIFY", "decelTps / falling twin", "blue"},
                {"3", "RESULT", "value + evidence strength", "green"},
                {"4", "REVIEW", "proposal only when justified", "amber"},
                {"5", "LIVE", "missing regions only", "gray"}
        }), BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 2, 8, 0));
        center.setOpaque(false);
        center.add(chartCard("What the analyzer separates", new FallingTpsInstructionChart()));

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

        JPanel lowerCoach = new JPanel(new BorderLayout(0, 5));
        lowerCoach.setOpaque(false);
        JPanel importRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        importRow.setOpaque(false);
        analyzeExisting.setFont(new Font("Dialog", Font.BOLD, 12));
        analyzeExisting.setToolTipText("Analyze one existing native MLG or text/CSV log against the current Working Tune. The result itself remains read-only, but can advance to Review with KEEP, CHANGE, evidence strength, or explicit missing-RPM guidance.");
        analyzeExisting.addActionListener(e -> {
            AeProjectSnapshot snapshot = GuidedV019HistoricalReplayContext.workingTune();
            if (snapshot != null && snapshot.hasDecelDetectionSettings()) {
                GuidedV019DecelHistoricalReplayDialog.chooseAndAnalyze(
                        this, snapshot, new Runnable() {
                            @Override public void run() {
                                GuidedFocusHub.startIdleCapture(GuidedTuningRecipe.DECEL_DETECTION);
                            }
                        });
            }
        });
        importRow.add(analyzeExisting);
        lowerCoach.add(importRow, BorderLayout.NORTH);
        JLabel boundary = label(
                "RESULT AUTHORITY IS READ-ONLY — only an explicitly reviewed CHANGE can stage the existing guarded ProposalWritePlan/Apply path",
                9, Font.BOLD, AeUiTheme.focusMuted());
        lowerCoach.add(boundary, BorderLayout.SOUTH);
        coach.add(lowerCoach, BorderLayout.SOUTH);
        center.add(coach);
        root.add(center, BorderLayout.CENTER);

        JPanel lower = new JPanel(new GridLayout(1, 2, 8, 0));
        lower.setOpaque(false);
        lower.add(summaryGrid("Evidence paths",
                new String[]{"Historical result", "Live capture", "Capture writes"},
                new JLabel[]{fixed("PRIMARY / READ ONLY"), stateValue, fixed("NONE")}));
        lower.add(summaryGrid("Recommendation authority",
                new String[]{"Review result", "Threshold 0", "Hold cycles"},
                new JLabel[]{reviewValue, fixed("never auto-enabled"), holdAuthority}));
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
        head.add(label("Detector identity, historical calibration, event separation, evidence strength, missing-region coverage and guarded proposal authority.",
                11, Font.PLAIN, AeUiTheme.focusMuted()), BorderLayout.SOUTH);
        root.add(head, BorderLayout.NORTH);

        JPanel body = new JPanel(new GridLayout(1, 3, 8, 0));
        body.setOpaque(false);
        body.add(notePanel("Historical result",
                "Use the same positive-side run when possible. Native MLG replay preserves Fuel: TPS AE change and Fuel: TPS decel change as different signals, selects the configured falling twin as detector authority, classifies threshold-independent physical falling events, and returns KEEP, CHANGE or exact missing RPM regions. Evidence strength uses independent physical-event counts only and does not change proposal authority. The full event/state audit remains available as technical detail rather than being the operator-facing result.",
                AeUiTheme.focusSoftBlue(), AeUiTheme.focusBlue()));
        body.add(notePanel("Firmware authority",
                "EpicEFI computes Decel as the negative/falling twin of the selected Accel detector. Threshold 0 remains a disable command, current Accel Active cancels Decel immediately, and tpsDecelHoldCycles remains read-only in this threshold pass. Historical result presentation does not alter those rules.",
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
        DecelHistoricalResultModel historical = DecelHistoricalResultContext.latest();
        boolean historicalReady = DecelHistoricalResultContext.reviewReady();
        boolean liveReviewReady = GuidedFocusHub.isActiveEvidenceReviewReady()
                && GuidedFocusHub.activeCaptureRecipe() == GuidedTuningRecipe.DECEL_DETECTION;
        boolean reviewReady = historicalReady || liveReviewReady;

        String activeGuidance;
        if (capture.isCaptureInProgress() && DecelHistoricalResultContext.targetedCaptureUseful()) {
            activeGuidance = "Target only the unresolved historical regions: "
                    + DecelHistoricalResultContext.targetedRpmText()
                    + " RPM. Aim for at least 3 independent NORMAL CORRECTION and 3 clear DECEL RELEASE events in each missing region; 5+5 gives GOOD coverage. Lower evidence-supported regions do not need to be repeated.";
        } else if (historical != null) {
            activeGuidance = historical.recommendation + " " + historical.explanation
                    + " Evidence strength: " + historical.evidenceStrengthSummary() + ".";
        } else if (matching && state.guidance != null && state.guidance.trim().length() > 0) {
            activeGuidance = state.guidance;
        } else {
            activeGuidance = "Import an existing log first. The same run used for positive-side Foundation work is preferred. The plugin will return the tuning result directly and ask for live capture only when specific RPM regions remain unresolved.";
        }
        guidance.setText(activeGuidance);
        guidance.setCaretPosition(0);
        stateValue.setText(capture == GuidedCaptureState.IDLE && historicalReady
                ? (DecelHistoricalResultContext.targetedCaptureUseful()
                    ? "OPTIONAL — " + DecelHistoricalResultContext.targetedRpmText() + " RPM"
                    : "NOT REQUIRED")
                : String.valueOf(capture));
        reviewValue.setText(reviewReady ? "READY" : "LOCKED");
        review.setEnabled(reviewReady);
        thresholdAuthority.setText("evidence-backed cells only");
        holdAuthority.setText("state replay / read only");

        AeProjectSnapshot replayTune = GuidedV019HistoricalReplayContext.workingTune();
        analyzeExisting.setEnabled(replayTune != null && replayTune.hasDecelDetectionSettings());

        if (capture == GuidedCaptureState.COMPLETE && liveReviewReady) {
            cue.setText("● LIVE REVIEW READY");
            cue.setForeground(AeUiTheme.focusGreen());
            action.setText("The targeted live follow-up is review-ready. Historical evidence-supported regions remain retained as context.");
        } else if (capture == GuidedCaptureState.COMPLETE) {
            cue.setText("● LIVE FOLLOW-UP INCOMPLETE");
            cue.setForeground(AeUiTheme.focusAmber());
            action.setText("The targeted live follow-up stopped before its evidence target was satisfied. Continue only the missing RPM regions.");
        } else if (capture == GuidedCaptureState.PAUSED) {
            cue.setText("● TARGETED LIVE PAUSED");
            cue.setForeground(AeUiTheme.focusAmber());
            action.setText("Resume only when ready to continue the unresolved RPM regions: " + DecelHistoricalResultContext.targetedRpmText() + ".");
        } else if (capture == GuidedCaptureState.CAPTURING) {
            cue.setText("● TARGETED LIVE CAPTURING");
            cue.setForeground(AeUiTheme.focusBlue());
            action.setText("Collect only the missing historical regions: " + DecelHistoricalResultContext.targetedRpmText() + " RPM. Aim for 3+3 minimum independent events per region; already supported regions do not need repeating.");
        } else if (historical != null) {
            cue.setText("● " + historical.headline);
            cue.setForeground(historical.proposalAvailable ? AeUiTheme.focusAmber() : AeUiTheme.focusGreen());
            action.setText(historical.recommendation
                    + " Evidence strength: " + historical.evidenceStrengthSummary() + "."
                    + (historical.targetedCaptureUseful ? " Missing RPM: " + historical.missingBinsText() + "." : ""));
        } else if (analyzeExisting.isEnabled()) {
            cue.setText("● LOG READY");
            cue.setForeground(AeUiTheme.focusBlue());
            action.setText("Analyze an existing positive-side or normal-driving log first. The plugin will return the Decel tuning result directly.");
        } else {
            cue.setText("● READ WORKING TUNE");
            cue.setForeground(AeUiTheme.focusAmber());
            action.setText("Read Working Tune first so imported Decel evidence can be resolved against the selected detector mode and actual RPM-indexed threshold curve.");
        }
        showLiveDataWait(cue, action);
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
            g2.drawString("current threshold replay", Math.max(l + 10, r - 145), thresholdY - 7);
            g2.setColor(AeUiTheme.focusMuted());
            g2.setFont(new Font("Dialog", Font.BOLD, 9));
            g2.drawString("INSTRUCTION — decelTps owns detection; physical class remains threshold-independent", l, b + 24);
            g2.dispose();
        }
    }
}
