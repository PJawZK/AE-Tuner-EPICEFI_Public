package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateFocusModel;
import se.anders.tunerstudio.aetuner.guided.method.FoundationThresholdFocusModel;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Locale;

/** Production binding of the validated v0.19 Review Results presentation. */
final class GuidedV019ReviewViews {
    enum Decision { PROPOSAL_READY, NO_CHANGE, EVIDENCE_ONLY, MORE_EVIDENCE, CONFLICT }
    enum Action { APPLY, FINISH, RECAPTURE, CLOSE }
    interface Listener { void action(Action action); }

    static final class ReviewModel {
        final GuidedProductionTask task;
        final Decision decision;
        final String evidence;
        final String confidence;
        final String finding;
        final String writeSummary;
        final String nextAction;

        ReviewModel(GuidedProductionTask task, Decision decision, String evidence,
                    String confidence, String finding, String writeSummary, String nextAction) {
            this.task = task;
            this.decision = decision;
            this.evidence = evidence;
            this.confidence = confidence;
            this.finding = finding;
            this.writeSummary = writeSummary;
            this.nextAction = nextAction;
        }

        boolean hasProposal() { return decision == Decision.PROPOSAL_READY; }
        boolean needsMoreEvidence() { return decision == Decision.MORE_EVIDENCE || decision == Decision.CONFLICT; }
    }

    private GuidedV019ReviewViews() { }

    static JComponent create(GuidedProductionTask task, AeProjectSnapshot tune,
                             GuidedV019ProductionBridge bridge, Listener listener) {
        GuidedFocusHub.State state = GuidedFocusHub.snapshot();
        ReviewModel model = modelFor(task, bridge, state);
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(AeUiTheme.background());
        root.setBorder(new EmptyBorder(10, 12, 10, 12));
        root.add(header(model, tune), BorderLayout.NORTH);
        root.add(taskBody(task, tune, bridge, state), BorderLayout.CENTER);
        root.add(footer(model, listener), BorderLayout.SOUTH);
        return root;
    }

    static ReviewModel modelFor(GuidedProductionTask task, GuidedV019ProductionBridge bridge,
                                GuidedFocusHub.State state) {
        boolean complete = state != null && state.captureState == GuidedCaptureState.COMPLETE;
        if (task == GuidedProductionTask.TPS_MOVEMENT_TIMING) {
            complete = complete || EngagementPassiveCapture.snapshot().complete();
        }
        if (!complete) {
            return new ReviewModel(task, Decision.MORE_EVIDENCE,
                    "Capture incomplete", "BUILDING",
                    "The production evidence session is not yet complete.",
                    "No write authority", "Return to Guided Focus");
        }
        if (bridge.applyEnabled()) {
            return new ReviewModel(task, Decision.PROPOSAL_READY,
                    evidenceSummary(task, state), confidence(task, state),
                    "Reviewed evidence supports an exact production ProposalWritePlan.",
                    proposalWriteSummary(task, state), "Continue to guarded Apply");
        }
        if (task == GuidedProductionTask.MAP_ESTIMATE && state != null && state.mapEstimate != null
                && state.mapEstimate.conflictCount > 0) {
            return new ReviewModel(task, Decision.CONFLICT,
                    evidenceSummary(task, state), "RECHECK",
                    "Conflicting MAP Estimate evidence remains; preserve it and gather targeted recheck data.",
                    "No write proposal", "Collect targeted recheck evidence");
        }
        if (task == GuidedProductionTask.BLEND_DURATION) {
            return new ReviewModel(task, Decision.EVIDENCE_ONLY,
                    evidenceSummary(task, state), confidence(task, state),
                    "Physical manifold-response evidence is usable, but numerical Blend conversion remains intentionally withheld.",
                    "No numerical proposal", "Finish review — retain evidence");
        }
        if (task == GuidedProductionTask.TPS_MOVEMENT_TIMING
                || task == GuidedProductionTask.THRESHOLD_SENSITIVITY
                || task == GuidedProductionTask.MAP_ESTIMATE) {
            return new ReviewModel(task, Decision.NO_CHANGE,
                    evidenceSummary(task, state), confidence(task, state),
                    "The reviewed evidence does not justify a changed production target.",
                    "No changed targets", "Finish review — retain current values");
        }
        return new ReviewModel(task, Decision.EVIDENCE_ONLY,
                evidenceSummary(task, state), confidence(task, state),
                "Production evidence is retained without inventing a write recommendation.",
                "No write proposal", "Finish review — keep evidence");
    }

    private static JComponent header(ReviewModel model, AeProjectSnapshot tune) {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setOpaque(false);
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        JPanel title = new JPanel(new BorderLayout());
        title.setOpaque(false);
        title.add(label(model.task.displayName + " — Review Results", 21, Font.BOLD, AeUiTheme.text()), BorderLayout.NORTH);
        title.add(label("Task-specific decision surface — evidence → finding → exact write/no-write result",
                11, Font.PLAIN, AeUiTheme.muted()), BorderLayout.SOUTH);
        titleRow.add(title, BorderLayout.WEST);
        JPanel pills = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        pills.setOpaque(false);
        pills.add(pill("PRODUCTION REVIEW", AeUiTheme.softBlue(), AeUiTheme.blue()));
        pills.add(pill(decisionText(model.decision), decisionBg(model.decision), decisionColor(model.decision)));
        titleRow.add(pills, BorderLayout.EAST);
        root.add(titleRow, BorderLayout.NORTH);

        JPanel summaries = new JPanel(new GridLayout(1, 5, 8, 0));
        summaries.setOpaque(false);
        summaries.add(summary("Evidence", model.evidence, "Production evidence authority", AeUiTheme.blue()));
        summaries.add(summary("Confidence", model.confidence, "Evidence quality / repeatability", confidenceColor(model.confidence)));
        summaries.add(summary("Calibration", tune == null ? "Working Tune unavailable" : tune.getConfigurationName(),
                tune == null ? "READ TUNE" : "Current production snapshot", AeUiTheme.green()));
        summaries.add(summary("Finding", shortText(model.finding, 68), "Task-specific decision below", decisionColor(model.decision)));
        summaries.add(summary("Write result", model.writeSummary,
                model.hasProposal() ? "Proposal only — not applied" : "Successful Review may have no write",
                model.hasProposal() ? AeUiTheme.amber() : AeUiTheme.muted()));
        root.add(summaries, BorderLayout.SOUTH);
        return root;
    }

    private static JComponent taskBody(GuidedProductionTask task, AeProjectSnapshot tune,
                                       GuidedV019ProductionBridge bridge, GuidedFocusHub.State state) {
        if (task == GuidedProductionTask.TPS_MOVEMENT_TIMING) return tpsBody(tune, state);
        if (task == GuidedProductionTask.THRESHOLD_SENSITIVITY) return thresholdBody(state);
        if (task == GuidedProductionTask.MAP_ESTIMATE) return mapBody(state);
        if (task == GuidedProductionTask.BLEND_DURATION) return blendBody(state);
        JPanel p = new JPanel(new GridLayout(1, 2, 8, 0));
        p.setOpaque(false);
        p.add(infoCard("Finding", findingText(task, state, bridge.applyEnabled())));
        p.add(textCard("Production review detail", bridge.proposalText()));
        return p;
    }

    private static JComponent tpsBody(AeProjectSnapshot tune, GuidedFocusHub.State state) {
        EngagementPassiveCapture.Snapshot p = EngagementPassiveCapture.snapshot();
        EngagementFocusModel m = state == null ? null : state.engagement;
        JPanel root = new JPanel(new GridLayout(1, 3, 8, 0));
        root.setOpaque(false);
        JPanel detector = verticalCard("Detector / timing evidence");
        detector.add(note("Comparable movements", p.comparableEvents + " / " + p.targetComparable));
        detector.add(Box.createVerticalStrut(7));
        detector.add(note("Accepted / rejected", p.comparableEvents + " accepted / "
                + (p.rejectedSmall + p.rejectedLarge + p.rejectedDuration) + " rejected"));
        detector.add(Box.createVerticalStrut(7));
        detector.add(note("Re-arm", p.settling ? "SETTLING" : p.readyForMovement() ? "READY" : "MOVING"));
        if (m != null) {
            detector.add(Box.createVerticalStrut(7));
            detector.add(note("Fuel: TPS AE change / AccelThreshold",
                    fmt(m.productionDeltaTps) + " / " + fmt(m.threshold)));
        }
        root.add(titled("Evidence", detector));
        root.add(textCard("Same-event / timing review", EngagementPassiveCapture.reviewText(tune)));
        JPanel decision = verticalCard("Decision");
        decision.add(big(p.complete() ? "REVIEWED" : "MORE EVIDENCE", p.complete() ? AeUiTheme.green() : AeUiTheme.amber()));
        decision.add(Box.createVerticalStrut(8));
        EngagementPassiveCapture.TimingStatus ts = m == null ? null : m.timingStatus;
        decision.add(note("Delta Window", ts != null && ts.deltaResolved ? fmt0(ts.deltaWindowMs) + " ms" : "unresolved / retain until proven"));
        decision.add(Box.createVerticalStrut(7));
        decision.add(note("Sample Length", ts != null && ts.sampleResolved ? fmt0(ts.sampleLengthMs) + " ms" : "capacity evidence pending"));
        root.add(titled("Finding", decision));
        return root;
    }

    private static JComponent thresholdBody(GuidedFocusHub.State state) {
        FoundationThresholdFocusModel m = state == null ? null : state.foundationThreshold;
        if (m == null) return infoCard("Threshold Review", "Production Threshold Focus state is unavailable.");
        String[] cols = {"RPM region", "Current", "Normal corr.", "Accel opening", "P95 / P25", "Gap", "Proposed", "State"};
        Object[][] rows = new Object[m.bins.size()][cols.length];
        for (int i = 0; i < m.bins.size(); i++) {
            FoundationThresholdFocusModel.Bin b = m.bins.get(i);
            rows[i] = new Object[]{b.regionLabel, fmt(b.currentThreshold), b.normalCorrectionEvents,
                    b.accelerationOpeningEvents, fmt(b.normalCorrectionP95) + " / " + fmt(b.accelerationOpeningP25),
                    fmt(b.separationGap), b.changed ? fmt(b.proposedThreshold) : "—", b.status};
        }
        JPanel root = new JPanel(new BorderLayout(8, 0));
        root.setOpaque(false);
        root.add(titled("Normal Correction / Acceleration Opening separation", themedScroll(table(cols, rows))), BorderLayout.CENTER);
        JPanel right = verticalCard("Decision");
        right.add(big(m.recommendationPlanAvailable ? "PROPOSAL READY" : m.changedBins == 0 ? "NO CHANGE" : "PLAN WITHHELD",
                m.recommendationPlanAvailable ? AeUiTheme.amber() : AeUiTheme.green()));
        right.add(Box.createVerticalStrut(8));
        right.add(note("Validated / eligible", m.effectiveValidatedBins + " / " + m.eligibleBins + " region(s)"));
        right.add(Box.createVerticalStrut(7));
        right.add(note("Quiet calibration", m.quietSamples + " / " + m.quietTarget + " samples; " + fmt(m.quietDurationSeconds) + " s"));
        right.add(Box.createVerticalStrut(7));
        right.add(note("Dynamic semantics", m.dynamicThresholdEnabled
                ? (m.averageStaticEnabled ? "Dynamic ON; static/dynamic averaging ON" : "Dynamic ON; static curve has no runtime authority")
                : "Dynamic OFF; static curve authoritative"));
        root.add(titled("Proposal summary", right), BorderLayout.EAST);
        return root;
    }

    private static JComponent mapBody(GuidedFocusHub.State state) {
        final MapEstimateFocusModel m = state == null ? null : state.mapEstimate;
        if (m == null || !m.hasTable()) return infoCard("MAP Estimate Review", "Production MAP Estimate table is unavailable.");
        String[] cols = new String[m.rpmAxis.length + 1];
        cols[0] = "TPS / RPM";
        for (int c = 0; c < m.rpmAxis.length; c++) cols[c + 1] = fmt0(m.rpmAxis[c]);
        Object[][] rows = new Object[m.tpsAxis.length][cols.length];
        for (int r = 0; r < m.tpsAxis.length; r++) {
            rows[r][0] = fmt0(m.tpsAxis[r]) + "%";
            for (int c = 0; c < m.rpmAxis.length; c++) {
                MapEstimateFocusModel.Cell cell = m.cell(r, c);
                rows[r][c + 1] = cell.proposalChange
                        ? fmt(cell.currentKpa) + " → " + fmt(cell.proposedKpa)
                        : fmt(cell.currentKpa);
            }
        }
        JTable table = table(cols, rows);
        table.setRowHeight(35);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                                      boolean focus, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, value, selected, focus, row, col);
                l.setOpaque(true);
                l.setForeground(AeUiTheme.text());
                if (col == 0) l.setBackground(AeUiTheme.panel());
                else {
                    MapEstimateFocusModel.Cell cell = m.cell(row, col - 1);
                    l.setBackground(cell.proposalChange ? AeUiTheme.softBlue() : AeUiTheme.card());
                    l.setBorder(new MatteBorder(0, 0, 1, 1, cell.proposalChange ? AeUiTheme.blue() : AeUiTheme.border()));
                }
                return l;
            }
        });
        JPanel right = verticalCard("Proposal summary");
        right.add(big(m.proposalChangeCount + " CHANGED CELL(S)", m.proposalChangeCount > 0 ? AeUiTheme.blue() : AeUiTheme.green()));
        right.add(Box.createVerticalStrut(8));
        right.add(note("Evidence quality", m.directCount + " Direct | " + m.interpolatedStrongCount + " Interpolated | "
                + m.weakCount + " Weak | " + m.conflictCount + " Conflict"));
        right.add(Box.createVerticalStrut(7));
        right.add(note("Proposal boundary", "Evidence state and proposal mask are separate. Only declared changed cells may enter a write plan."));
        right.add(Box.createVerticalStrut(7));
        right.add(note("Protected", "Unchanged cells and both TPS/RPM axes remain untouched; exact readback required; no Burn."));
        JPanel root = new JPanel(new BorderLayout(8, 0));
        root.setOpaque(false);
        root.add(titled("Current → Proposed MAP Estimate table", themedScroll(table)), BorderLayout.CENTER);
        root.add(titled("Proposal summary", right), BorderLayout.EAST);
        return root;
    }

    private static JComponent blendBody(GuidedFocusHub.State state) {
        BlendDurationFocusModel m = state == null ? null : state.blendDuration;
        if (m == null) return infoCard("Blend Duration Review", "Production Blend Duration Focus state is unavailable.");
        JPanel root = new JPanel(new GridLayout(1, 2, 8, 0));
        root.setOpaque(false);
        JPanel left = verticalCard("Measured response");
        left.add(big("PROPOSAL WITHHELD — EVIDENCE RETAINED", AeUiTheme.amber()));
        left.add(Box.createVerticalStrut(8));
        left.add(note("Comparable events", m.matchingEvents + " / " + m.targetEvents + " matching; " + m.allValidEvents + " total valid"));
        left.add(Box.createVerticalStrut(7));
        left.add(note("Physical MAP catch-up", fmtMillis(m.physicalCatchupSeconds)));
        left.add(Box.createVerticalStrut(7));
        left.add(note("Current RPM-interpolated Blend Duration", fmtMillis(m.currentBlendDuration)));
        left.add(Box.createVerticalStrut(7));
        left.add(note("Replay", m.effectiveMapReplaySamples + " sample(s); mean |error| " + fmt(m.effectiveMapMeanAbsoluteError) + " kPa"));
        root.add(titled("Finding", left));
        root.add(textCard("Production diagnostics", m.detail));
        return root;
    }

    private static JComponent footer(final ReviewModel model, final Listener listener) {
        JPanel p = card(new BorderLayout(8, 0));
        p.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.border()), new EmptyBorder(7, 9, 7, 9)));
        p.add(label("Next: " + model.nextAction, 11, Font.BOLD, AeUiTheme.text()), BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        JButton back = button("Back to Guided Focus");
        JButton primary;
        final Action primaryAction;
        if (model.hasProposal()) {
            primary = button("Continue to Apply");
            primaryAction = Action.APPLY;
        } else if (model.needsMoreEvidence()) {
            primary = button("Collect More Evidence");
            primaryAction = Action.RECAPTURE;
        } else {
            primary = button("Finish Review");
            primaryAction = Action.FINISH;
        }
        highlight(primary);
        JButton close = button("Close");
        back.addActionListener(e -> { if (listener != null) listener.action(Action.RECAPTURE); });
        primary.addActionListener(e -> { if (listener != null) listener.action(primaryAction); });
        close.addActionListener(e -> { if (listener != null) listener.action(Action.CLOSE); });
        buttons.add(back);
        buttons.add(primary);
        buttons.add(close);
        p.add(buttons, BorderLayout.EAST);
        return p;
    }

    private static String proposalWriteSummary(GuidedProductionTask task, GuidedFocusHub.State state) {
        if (task == GuidedProductionTask.THRESHOLD_SENSITIVITY && state != null && state.foundationThreshold != null)
            return state.foundationThreshold.changedBins + " threshold point(s)";
        if (task == GuidedProductionTask.MAP_ESTIMATE && state != null && state.mapEstimate != null)
            return state.mapEstimate.proposalChangeCount + " MAP cell(s)";
        return "Reviewed ProposalWritePlan";
    }

    private static String evidenceSummary(GuidedProductionTask task, GuidedFocusHub.State state) {
        if (task == GuidedProductionTask.TPS_MOVEMENT_TIMING) {
            EngagementPassiveCapture.Snapshot p = EngagementPassiveCapture.snapshot();
            return p.comparableEvents + " / " + p.targetComparable + " comparable movements";
        }
        if (state == null) return "Production evidence unavailable";
        if (state.foundationThreshold != null)
            return state.foundationThreshold.movementEvents + " movements / " + state.foundationThreshold.effectiveValidatedBins + " validated region(s)";
        if (state.mapEstimate != null)
            return state.mapEstimate.evidenceSamplesUsed + " samples / " + state.mapEstimate.proposalChangeCount + " changed cell(s)";
        if (state.blendDuration != null)
            return state.blendDuration.matchingEvents + " / " + state.blendDuration.targetEvents + " comparable events";
        return "Production evidence retained";
    }

    private static String confidence(GuidedProductionTask task, GuidedFocusHub.State state) {
        if (task == GuidedProductionTask.TPS_MOVEMENT_TIMING)
            return EngagementPassiveCapture.snapshot().complete() ? "GOOD" : "BUILDING";
        if (state != null && state.mapEstimate != null && state.mapEstimate.conflictCount > 0) return "RECHECK";
        if (state != null && state.captureState == GuidedCaptureState.COMPLETE) return "GOOD";
        return "BUILDING";
    }

    private static String findingText(GuidedProductionTask task, GuidedFocusHub.State state, boolean proposal) {
        if (proposal) return "Reviewed production evidence supports an exact guarded proposal. No write occurs until Apply.";
        if (task == GuidedProductionTask.MAP_ESTIMATE && state != null && state.mapEstimate != null)
            return state.mapEstimate.proposalChangeCount == 0 ? "Working MAP Estimate values remain supported." : "Changed candidates exist but no reviewed Apply plan is available.";
        return "Production evidence is retained without inventing a write recommendation.";
    }

    private static String decisionText(Decision d) {
        switch (d) {
            case PROPOSAL_READY: return "PROPOSAL READY";
            case NO_CHANGE: return "NO CHANGE";
            case EVIDENCE_ONLY: return "EVIDENCE ONLY";
            case CONFLICT: return "CONFLICT / RECHECK";
            default: return "MORE EVIDENCE";
        }
    }

    private static Color decisionColor(Decision d) {
        if (d == Decision.PROPOSAL_READY) return AeUiTheme.amber();
        if (d == Decision.NO_CHANGE || d == Decision.EVIDENCE_ONLY) return AeUiTheme.green();
        if (d == Decision.CONFLICT) return AeUiTheme.red();
        return AeUiTheme.blue();
    }

    private static Color decisionBg(Decision d) {
        if (d == Decision.PROPOSAL_READY) return AeUiTheme.softAmber();
        if (d == Decision.NO_CHANGE || d == Decision.EVIDENCE_ONLY) return AeUiTheme.softGreen();
        if (d == Decision.CONFLICT) return AeUiTheme.softRed();
        return AeUiTheme.softBlue();
    }

    private static Color confidenceColor(String confidence) {
        return "GOOD".equals(confidence) ? AeUiTheme.green() : "RECHECK".equals(confidence) ? AeUiTheme.red() : AeUiTheme.amber();
    }

    private static JPanel summary(String title, String value, String detail, Color accent) {
        JPanel p = card(new BorderLayout(0, 4));
        p.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.border()), new EmptyBorder(7, 8, 7, 8)));
        p.add(label(title.toUpperCase(Locale.ROOT), 9, Font.BOLD, AeUiTheme.muted()), BorderLayout.NORTH);
        p.add(label("<html><b>" + html(value) + "</b></html>", 11, Font.BOLD, accent), BorderLayout.CENTER);
        p.add(label("<html>" + html(detail) + "</html>", 9, Font.PLAIN, AeUiTheme.muted()), BorderLayout.SOUTH);
        return p;
    }

    private static JPanel titled(String title, JComponent content) {
        JPanel p = card(new BorderLayout(0, 6));
        p.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.border()), new EmptyBorder(8, 10, 8, 10)));
        p.add(label(title, 13, Font.BOLD, AeUiTheme.text()), BorderLayout.NORTH);
        p.add(content, BorderLayout.CENTER);
        return p;
    }

    private static JPanel verticalCard(String ignored) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    private static JPanel note(String title, String text) {
        JPanel p = card(new BorderLayout(0, 2));
        p.setBorder(new EmptyBorder(5, 6, 5, 6));
        p.add(label(title, 10, Font.BOLD, AeUiTheme.muted()), BorderLayout.NORTH);
        p.add(wrap(text, 11, AeUiTheme.text()), BorderLayout.CENTER);
        return p;
    }

    private static JPanel infoCard(String title, String text) { return titled(title, wrap(text, 12, AeUiTheme.text())); }

    private static JPanel textCard(String title, String text) {
        JTextArea area = new JTextArea(text == null ? "" : text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBackground(AeUiTheme.card());
        area.setForeground(AeUiTheme.text());
        area.setFont(new Font("Dialog", Font.PLAIN, 11));
        return titled(title, themedScroll(area));
    }

    private static JTable table(String[] cols, Object[][] rows) {
        JTable t = new JTable(new DefaultTableModel(rows, cols) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        });
        t.setGridColor(AeUiTheme.border());
        t.setBackground(AeUiTheme.card());
        t.setForeground(AeUiTheme.text());
        t.setSelectionBackground(AeUiTheme.softBlue());
        t.setSelectionForeground(AeUiTheme.text());
        t.setFillsViewportHeight(true);
        t.getTableHeader().setBackground(AeUiTheme.tableHeader());
        t.getTableHeader().setForeground(AeUiTheme.text());
        t.getTableHeader().setFont(new Font("Dialog", Font.BOLD, 10));
        return t;
    }

    private static JScrollPane themedScroll(Component c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(new LineBorder(AeUiTheme.border()));
        s.getViewport().setBackground(AeUiTheme.card());
        return s;
    }

    private static JPanel card(java.awt.LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setBackground(AeUiTheme.card());
        return p;
    }

    private static JLabel label(String text, int size, int style, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Dialog", style, size));
        l.setForeground(color);
        return l;
    }

    private static JLabel wrap(String text, int size, Color color) {
        JLabel l = label("<html><div style='width:410px'>" + html(text) + "</div></html>", size, Font.PLAIN, color);
        l.setVerticalAlignment(SwingConstants.TOP);
        return l;
    }

    private static JLabel big(String text, Color color) { return label(text, 17, Font.BOLD, color); }

    private static JLabel pill(String text, Color bg, Color fg) {
        JLabel l = label(text, 9, Font.BOLD, fg);
        l.setOpaque(true);
        l.setBackground(bg);
        l.setBorder(new CompoundBorder(new LineBorder(fg), new EmptyBorder(4, 7, 4, 7)));
        return l;
    }

    private static JButton button(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Dialog", Font.PLAIN, 10));
        b.setFocusPainted(false);
        b.setBackground(AeUiTheme.button());
        b.setForeground(AeUiTheme.text());
        b.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.buttonBorder()), new EmptyBorder(5, 8, 5, 8)));
        return b;
    }

    private static void highlight(JButton b) {
        b.setBackground(AeUiTheme.amber());
        b.setForeground(new Color(42, 31, 4));
        b.setBorder(new CompoundBorder(new LineBorder(AeUiTheme.amberDark(), 2), new EmptyBorder(4, 7, 4, 7)));
    }

    private static String fmt(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.2f", value) : "n/a"; }
    private static String fmt0(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.0f", value) : "n/a"; }
    private static String fmtMillis(double seconds) { return Double.isFinite(seconds) ? String.format(Locale.ROOT, "%.0f ms", seconds * 1000.0) : "n/a"; }
    private static String shortText(String text, int max) { return text == null ? "" : text.length() <= max ? text : text.substring(0, max - 1) + "…"; }
    private static String html(String text) { return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
