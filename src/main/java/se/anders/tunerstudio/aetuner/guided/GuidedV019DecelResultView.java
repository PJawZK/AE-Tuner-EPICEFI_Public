package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalEvidenceStrength;
import se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalResultModel;
import se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalThresholdDecision;
import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;

/** Compact operator result surface; technical replay remains available separately. */
final class GuidedV019DecelResultView {
    private GuidedV019DecelResultView() { }

    static JComponent create(DecelHistoricalResultModel model) {
        JPanel root = new JPanel(new BorderLayout(0, 9));
        root.setBackground(AeUiTheme.background());
        root.setBorder(new EmptyBorder(8, 10, 8, 10));
        if (model == null) {
            root.add(note("No Decel historical result is available."), BorderLayout.CENTER);
            return root;
        }

        JPanel head = card(new BorderLayout(8, 6));
        JLabel title = new JLabel(model.headline);
        title.setFont(new Font("Dialog", Font.BOLD, 20));
        title.setForeground(stateColor(model.state));
        head.add(title, BorderLayout.NORTH);
        JTextArea recommendation = area(model.recommendation + "\n" + model.explanation
                + (model.validatedBins == 0 && model.totalEvents > 0
                    ? "\nRecorded event counts alone do not establish a threshold target. Select an RPM row below to see its evidence gate." : ""), 12, Font.PLAIN);
        head.add(recommendation, BorderLayout.CENTER);
        root.add(head, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setOpaque(false);
        center.add(summary(model), BorderLayout.NORTH);
        center.add(binTable(model), BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);

        JPanel foot = new JPanel(new GridLayout(1, 2, 8, 0));
        foot.setOpaque(false);
        String next = model.proposalAvailable
                ? "Review the exact classification-boundary ECU value for each CHANGE cell. Actual writes remain guarded by Apply/readback/Restore."
                : model.targetedCaptureUseful
                    ? "Keep resolved cells. Live follow-up should target only " + model.missingBinsText()
                        + " RPM, aiming for at least 3 independent Normal Correction + 3 clear Decel Release events in each missing region."
                    : model.state == DecelHistoricalResultModel.OverallState.VALIDATED_KEEP
                        ? "All supported log-derived targets already match the ECU."
                        : "Resolve the stated evidence/calibration issue before changing the threshold curve.";
        foot.add(info("Recommended next action", next));
        foot.add(info("Evidence strength",
                model.evidenceStrengthSummary() + ". "
                        + "Strength requires a qualified threshold result first; counts alone are insufficient. Then independent events determine STRONG = 10+ Normal and 10+ Release, GOOD = 5+5, MINIMUM = 3+3. "
                        + model.totalEvents + " physical events total: " + model.normalEvents + " Normal Correction / "
                        + model.releaseEvents + " Decel Release / " + model.ambiguousEvents + " Ambiguous"
                        + (model.releaseMisses > 0 ? "; " + model.releaseMisses + " clear releases missed the current threshold" : "")
                        + ". Strength is coverage context only and does not expand proposal/write authority."));
        root.add(foot, BorderLayout.SOUTH);
        return root;
    }

    private static JComponent summary(DecelHistoricalResultModel model) {
        JPanel row = new JPanel(new GridLayout(1, 5, 7, 0));
        row.setOpaque(false);
        row.add(metric("Evidence-supported", model.validatedBins + " / " + model.totalBins,
                model.validatedBins > 0 ? AeUiTheme.green() : AeUiTheme.muted()));
        row.add(metric("Change", String.valueOf(model.proposedBins),
                model.proposedBins > 0 ? AeUiTheme.amber() : AeUiTheme.green()));
        row.add(metric("Need evidence", String.valueOf(model.missingBins),
                model.missingBins > 0 ? AeUiTheme.amber() : AeUiTheme.green()));
        row.add(metric("Strength", model.evidenceStrengthSummary(),
                model.minimumBins > 0 ? AeUiTheme.amber() : AeUiTheme.green()));
        row.add(metric("Missing RPM", model.missingBinsText(),
                model.missingBins > 0 ? AeUiTheme.amber() : AeUiTheme.muted()));
        return row;
    }

    private static JComponent binTable(DecelHistoricalResultModel model) {
        String[] columns = {"RPM", "Current ECU", "Log result ECU", "Normal", "Release", "Strength", "Misses", "Result"};
        Object[][] rows = new Object[model.bins.size()][columns.length];
        for (int i = 0; i < model.bins.size(); i++) {
            DecelHistoricalResultModel.BinResult b = model.bins.get(i);
            rows[i] = new Object[]{Math.round(b.rpm), DecelHistoricalResultModel.fmt(b.current),
                    ecuResultValue(b), b.normalEvents, b.releaseEvents,
                    DecelHistoricalEvidenceStrength.display(b.evidenceStrength),
                    b.releaseMisses, b.operatorState};
        }
        DefaultTableModel data = new DefaultTableModel(rows, columns) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable table = new JTable(data);
        table.setFont(new Font("Dialog", Font.PLAIN, 11));
        table.setRowHeight(25);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setFont(new Font("Dialog", Font.BOLD, 11));
        table.setBackground(AeUiTheme.card());
        table.setForeground(AeUiTheme.text());
        table.setGridColor(AeUiTheme.border());
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new LineBorder(AeUiTheme.border()));
        scroll.setPreferredSize(new Dimension(840, Math.min(275, 58 + Math.max(1, model.bins.size()) * 25)));
        JTextArea detail = area("Select an RPM row to see its evidence gate.", 11, Font.PLAIN);
        detail.setRows(3);
        table.getSelectionModel().addListSelectionListener(event -> {
            int row = table.getSelectedRow();
            if (row >= 0 && row < model.bins.size()) {
                DecelHistoricalResultModel.BinResult bin = model.bins.get(table.convertRowIndexToModel(row));
                detail.setText("RPM " + Math.round(bin.rpm) + " — " + bin.operatorState
                        + "\nWhy: " + bin.reason + "\nStrength: " + bin.evidenceStrengthReason);
                detail.setCaretPosition(0);
            }
        });
        if (!model.bins.isEmpty()) table.setRowSelectionInterval(0, 0);
        JPanel result = new JPanel(new BorderLayout(0, 5));
        result.setOpaque(false);
        result.add(area("RPM-bin log result — one value per controller cell; strength uses independent event counts",
                11, Font.BOLD), BorderLayout.NORTH);
        result.add(scroll, BorderLayout.CENTER);
        result.add(detail, BorderLayout.SOUTH);
        return result;
    }

    private static String ecuResultValue(DecelHistoricalResultModel.BinResult b) {
        if (b == null || b.outcome == null) return "—";
        if (b.outcome == DecelHistoricalThresholdDecision.Outcome.KEEP
                || b.outcome == DecelHistoricalThresholdDecision.Outcome.PROPOSE) {
            return DecelHistoricalResultModel.fmt(b.proposed);
        }
        return "—";
    }

    private static JPanel metric(String name, String value, Color color) {
        JPanel p = card(new BorderLayout(0, 3));
        JLabel n = new JLabel(name);
        n.setFont(new Font("Dialog", Font.PLAIN, 10));
        n.setForeground(AeUiTheme.muted());
        JLabel v = new JLabel(value);
        v.setFont(new Font("Dialog", Font.BOLD, 14));
        v.setForeground(color);
        p.add(n, BorderLayout.NORTH);
        p.add(v, BorderLayout.CENTER);
        return p;
    }

    private static JPanel info(String title, String text) {
        JPanel p = card(new BorderLayout(0, 5));
        JLabel t = new JLabel(title);
        t.setFont(new Font("Dialog", Font.BOLD, 12));
        t.setForeground(AeUiTheme.text());
        p.add(t, BorderLayout.NORTH);
        p.add(area(text, 11, Font.PLAIN), BorderLayout.CENTER);
        return p;
    }

    private static JPanel card(java.awt.LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setBackground(AeUiTheme.card());
        p.setBorder(new EmptyBorder(8, 9, 8, 9));
        return p;
    }

    private static JComponent note(String text) {
        JPanel p = card(new BorderLayout());
        p.add(area(text, 12, Font.PLAIN), BorderLayout.CENTER);
        return p;
    }

    private static JTextArea area(String text, int size, int style) {
        JTextArea a = new JTextArea(text == null ? "" : text);
        a.setEditable(false);
        a.setFocusable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setOpaque(false);
        a.setFont(new Font("Dialog", style, size));
        a.setForeground(AeUiTheme.text());
        a.setBorder(null);
        return a;
    }

    private static Color stateColor(DecelHistoricalResultModel.OverallState state) {
        if (state == DecelHistoricalResultModel.OverallState.CHANGE_RECOMMENDED) return AeUiTheme.amber();
        if (state == DecelHistoricalResultModel.OverallState.RESULT_WITHHELD
                || state == DecelHistoricalResultModel.OverallState.MORE_EVIDENCE_NEEDED) return AeUiTheme.amber();
        return AeUiTheme.green();
    }
}
