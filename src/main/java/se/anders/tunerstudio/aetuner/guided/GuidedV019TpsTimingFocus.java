package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.geom.Path2D;

/** v0.19 TPS Movement / Timing driving view bound to real production evidence. */
final class GuidedV019TpsTimingFocus extends GuidedV019FocusBase {
    private final DriverCue cue = new DriverCue();
    private final LiveMovementGraph liveGraph = new LiveMovementGraph();
    private final JLabel liveTps = valueLabel();
    private final JLabel liveRpm = valueLabel();
    private final JLabel liveDelta = valueLabel();
    private final JLabel threshold = smallValue();
    private final JLabel timing = smallValue();
    private final JLabel comparable = smallValue();
    private final JLabel sound = smallValue();

    GuidedV019TpsTimingFocus(JDialog owner, Runnable done) { super(owner, done); build(); }
    int refreshIntervalMillis() { return 50; }
    String taskTitle() { return "TPS Movement / Timing"; }
    String taskSubtitle() { return "Driver-guided passive capture — movement shape left, rolling live trace right"; }

    JComponent drivingContent() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setOpaque(false);
        cue.setPreferredSize(new Dimension(100, 132));
        root.add(cue, BorderLayout.NORTH);

        JPanel main = new JPanel(new GridBagLayout());
        main.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = 0;
        gc.fill = GridBagConstraints.BOTH;
        gc.weighty = 1.0;

        gc.gridx = 0;
        gc.weightx = 0.38;
        gc.insets = new Insets(0, 0, 0, 8);
        main.add(chartCard("TPS movement — what to do", new InstructionChart()), gc);

        gc.gridx = 1;
        gc.weightx = 0.62;
        gc.insets = new Insets(0, 0, 0, 0);
        main.add(chartCard("Live movement — TPS + detector", liveGraph), gc);

        root.add(main, BorderLayout.CENTER);

        JPanel lower = new JPanel(new GridLayout(1, 2, 8, 0));
        lower.setOpaque(false);
        lower.add(liveTriple("Live context", "TPS", liveTps, "RPM", liveRpm, "TPS AE change", liveDelta));
        lower.add(summaryGrid("Capture context",
                new String[]{"AccelThreshold", "Timing pair", "Comparable", "Sound cue"},
                new JLabel[]{threshold, timing, comparable, sound}));
        lower.setPreferredSize(new Dimension(100, 82));
        root.add(lower, BorderLayout.SOUTH);
        return root;
    }

    JComponent diagnosticsContent() { return new Diagnostics(); }

    void refreshFromProduction() {
        GuidedFocusHub.State state = GuidedFocusHub.snapshot();
        EngagementFocusModel model = state != null && state.recipe == GuidedTuningRecipe.ENGAGEMENT_DETECTION
                ? state.engagement : null;
        if (model == null) model = EngagementFocusModel.setupFromWorkingTune(GuidedCaptureState.IDLE);
        EngagementPassiveCapture.Snapshot passive = EngagementPassiveCapture.snapshot();
        cue.update(model, passive);
        liveGraph.update(model, passive);
        liveTps.setText(fmt(model.tps) + " %");
        liveRpm.setText(fmt0(model.rpm));
        liveDelta.setText(fmt(model.productionDeltaTps));
        threshold.setText(fmt(model.threshold));
        EngagementPassiveCapture.TimingStatus ts = model.timingStatus;
        timing.setText(ts.deltaResolved
                ? fmt0(ts.deltaWindowMs) + " / " + fmt0(ts.sampleLengthMs) + " ms"
                : "collecting");
        comparable.setText(passive.comparableEvents + " / " + passive.targetComparable);
        sound.setText("production cues");
        review.setEnabled(model.captureState == GuidedCaptureState.COMPLETE || passive.complete());
        repaint();
    }

    int liveGraphSamplesForTest() { return liveGraph.sampleCount(); }

    private static final class DriverCue extends JPanel {
        private final JLabel state = new JLabel(), instruction = new JLabel(), sub = new JLabel(), context = new JLabel();
        private final JPanel steps = new JPanel(new GridLayout(1, 4, 6, 0));

        DriverCue() {
            setLayout(new BorderLayout(12, 8));
            setBackground(AeUiTheme.card());
            setBorder(cardBorder());
            JPanel left = new JPanel(new BorderLayout(0, 2));
            left.setOpaque(false);
            state.setFont(new Font("Dialog", Font.BOLD, 27));
            instruction.setFont(new Font("Dialog", Font.BOLD, 17));
            instruction.setForeground(AeUiTheme.focusText());
            sub.setFont(BODY);
            sub.setForeground(AeUiTheme.focusMuted());
            left.add(state, BorderLayout.NORTH);
            left.add(instruction, BorderLayout.CENTER);
            left.add(sub, BorderLayout.SOUTH);
            add(left, BorderLayout.CENTER);
            context.setFont(new Font("Dialog", Font.BOLD, 11));
            context.setForeground(AeUiTheme.focusMuted());
            context.setHorizontalAlignment(SwingConstants.RIGHT);
            context.setVerticalAlignment(SwingConstants.TOP);
            add(context, BorderLayout.EAST);
            steps.setOpaque(false);
            add(steps, BorderLayout.SOUTH);
        }

        void update(EngagementFocusModel m, EngagementPassiveCapture.Snapshot p) {
            String title;
            Color color;
            int active;
            if (m.captureState == GuidedCaptureState.PAUSED) {
                title = "PAUSED"; color = AeUiTheme.focusAmber(); active = -1;
            } else if (p.moving) {
                title = "OPEN NOW"; color = AeUiTheme.focusBlue(); active = 1;
            } else if (p.settling) {
                title = "SETTLE"; color = AeUiTheme.focusGreen(); active = 2;
            } else if (p.comparableEvents > 0 && !p.complete()) {
                title = "RE-ARM / READY"; color = AeUiTheme.navy(); active = 3;
            } else {
                title = "READY"; color = AeUiTheme.focusAmber(); active = 0;
            }
            state.setText("● " + title);
            state.setForeground(color);
            String action = m.nextActionText();
            String[] lines = action.split("\\n", 2);
            instruction.setText(lines.length > 0 ? lines[0] : action);
            sub.setText(lines.length > 1 ? lines[1].replace('\n', ' ') : m.detectorStatusText());
            context.setText("<html><div style='text-align:right'>"
                    + (Double.isFinite(m.rpm) ? fmt0(m.rpm) + " RPM" : "RPM n/a")
                    + "<br>Evidence " + p.comparableEvents + " / " + p.targetComparable
                    + "<br>Capture writes NONE</div></html>");
            steps.removeAll();
            addStep("1", "QUIET", active == 0, AeUiTheme.focusGreen());
            addStep("2", "OPEN", active == 1, AeUiTheme.focusBlue());
            addStep("3", "SETTLE", active == 2, AeUiTheme.focusGreen());
            addStep("4", "RE-ARM", active == 3, AeUiTheme.navy());
            revalidate();
            repaint();
        }

        private void addStep(String n, String text, boolean active, Color accent) {
            JLabel l = new JLabel(n + "  " + text, SwingConstants.CENTER);
            l.setFont(new Font("Dialog", Font.BOLD, 10));
            l.setOpaque(true);
            l.setBackground(active ? alpha(accent, 35) : AeUiTheme.neutralSoft());
            l.setForeground(active ? accent : AeUiTheme.focusMuted());
            l.setBorder(new LineBorder(active ? accent : AeUiTheme.border()));
            steps.add(l);
        }
    }

    private static final class InstructionChart extends JPanel {
        InstructionChart() {
            setBackground(AeUiTheme.card());
            setPreferredSize(new Dimension(390, 260));
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int l = 28, r = getWidth() - 18, t = 30, b = getHeight() - 36, w = r - l;
                g2.setColor(AeUiTheme.chartGridSoft());
                for (int i = 0; i < 4; i++) {
                    int x = l + w * i / 3;
                    g2.drawLine(x, t, x, b);
                }
                g2.drawLine(l, b, r, b);
                int[] xs = {l, l + w * 18 / 100, l + w * 28 / 100, l + w * 38 / 100,
                        l + w * 50 / 100, l + w * 62 / 100, r};
                int[] ys = {b - 18, b - 18, b - 34, b - 95, t + 72, t + 58, t + 58};
                draw(g2, xs, ys, AeUiTheme.focusBlue(), 4f, false);
                g2.setFont(new Font("Dialog", Font.BOLD, 10));
                g2.setColor(AeUiTheme.focusGreen());
                g2.drawString("1  QUIET", l, t + 6);
                g2.setColor(AeUiTheme.focusBlue());
                g2.drawString("2  OPEN", l + w * 34 / 100, t + 6);
                g2.setColor(AeUiTheme.focusGreen());
                g2.drawString("3  SETTLE", l + w * 73 / 100, t + 6);
                g2.setFont(SMALL);
                g2.setColor(AeUiTheme.focusMuted());
                g2.drawString("baseline", l, b - 4);
                g2.drawString("moderate opening", l + w * 31 / 100, b - 4);
                g2.drawString("soft plateau", l + w * 72 / 100, b - 4);
            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * Rolling UI-only trace. It samples the already-published production Focus model;
     * these points are presentation history only and never become tuning evidence.
     */
    private static final class LiveMovementGraph extends JPanel {
        private static final int CAPACITY = 120;
        private final double[] tps = new double[CAPACITY];
        private final double[] delta = new double[CAPACITY];
        private final double[] threshold = new double[CAPACITY];
        private int size;
        private int next;
        private EngagementPassiveCapture.Snapshot passive;

        LiveMovementGraph() {
            setBackground(AeUiTheme.card());
            setPreferredSize(new Dimension(660, 260));
        }

        void update(EngagementFocusModel model, EngagementPassiveCapture.Snapshot snapshot) {
            passive = snapshot;
            if (model != null && (Double.isFinite(model.tps)
                    || Double.isFinite(model.productionDeltaTps)
                    || Double.isFinite(model.threshold))) {
                tps[next] = model.tps;
                delta[next] = model.productionDeltaTps;
                threshold[next] = model.threshold;
                next = (next + 1) % CAPACITY;
                if (size < CAPACITY) size++;
            }
            repaint();
        }

        int sampleCount() { return size; }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int l = 50, r = getWidth() - 16, t = 24, b = getHeight() - 28;
                if (r <= l || b <= t) return;
                int split = t + (b - t) * 58 / 100;
                int topT = t + 18, topB = split - 10;
                int detT = split + 22, detB = b - 8;

                drawGrid(g2, l, r, t, b, split);
                g2.setFont(new Font("Dialog", Font.BOLD, 10));
                g2.setColor(AeUiTheme.focusMuted());
                g2.drawString("TPS %", l, t + 11);
                g2.drawString("TPS AE change / AccelThreshold", l, split + 15);
                g2.drawString("old", l, b + 18);
                g2.drawString("now", r - 22, b + 18);

                if (size < 2) {
                    g2.setFont(SMALL);
                    g2.drawString("Waiting for rolling production data", l + 20, (t + b) / 2);
                    return;
                }

                Scale tpsScale = tpsScale();
                Scale detectorScale = detectorScale();
                drawSeries(g2, tps, l, r, topT, topB, tpsScale, AeUiTheme.focusBlue(), 2.8f, false);
                drawSeries(g2, delta, l, r, detT, detB, detectorScale, AeUiTheme.focusGreen(), 2.5f, false);
                drawSeries(g2, threshold, l, r, detT, detB, detectorScale, AeUiTheme.red(), 1.8f, true);

                if (passive != null && Double.isFinite(passive.referencePeakTps)) {
                    int y = yFor(passive.referencePeakTps, topT, topB, tpsScale);
                    g2.setColor(AeUiTheme.focusGreen());
                    g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT,
                            BasicStroke.JOIN_MITER, 10f, new float[]{5f, 5f}, 0f));
                    g2.drawLine(l, y, r, y);
                    g2.setFont(SMALL);
                    g2.drawString("first accepted reference " + fmt(passive.referencePeakTps), l + 8, Math.max(topT + 11, y - 4));
                }

                int last = index(size - 1);
                drawPoint(g2, xFor(size - 1, l, r), yFor(tps[last], topT, topB, tpsScale), AeUiTheme.focusBlue());
                Color detectorColor = Double.isFinite(delta[last]) && Double.isFinite(threshold[last])
                        && delta[last] >= threshold[last] ? AeUiTheme.focusGreen() : AeUiTheme.focusBlue();
                drawPoint(g2, xFor(size - 1, l, r), yFor(delta[last], detT, detB, detectorScale), detectorColor);

                g2.setFont(new Font("Dialog", Font.BOLD, 10));
                g2.setColor(AeUiTheme.focusBlue());
                g2.drawString("TPS " + fmt(tps[last]) + "%", l + 8, topT + 13);
                g2.setColor(AeUiTheme.focusGreen());
                g2.drawString("TPS AE change " + fmt(delta[last]), l + 8, detT + 13);
                g2.setColor(AeUiTheme.red());
                g2.drawString("AccelThreshold " + fmt(threshold[last]), r - 145, detT + 13);
                if (passive != null) {
                    g2.setColor(AeUiTheme.focusMuted());
                    g2.setFont(SMALL);
                    g2.drawString(trim(passive.lastEvent, 72), l, b + 6);
                }
            } finally {
                g2.dispose();
            }
        }

        private void drawGrid(Graphics2D g2, int l, int r, int t, int b, int split) {
            g2.setColor(AeUiTheme.chartGridSoft());
            for (int i = 0; i <= 6; i++) {
                int x = l + (r - l) * i / 6;
                g2.drawLine(x, t, x, b);
            }
            for (int i = 0; i <= 4; i++) {
                int y = t + (split - t) * i / 4;
                g2.drawLine(l, y, r, y);
            }
            for (int i = 0; i <= 3; i++) {
                int y = split + (b - split) * i / 3;
                g2.drawLine(l, y, r, y);
            }
            g2.setColor(AeUiTheme.border());
            g2.drawLine(l, split, r, split);
        }

        private Scale tpsScale() {
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < size; i++) {
                double v = tps[index(i)];
                if (!Double.isFinite(v)) continue;
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            if (passive != null && Double.isFinite(passive.referencePeakTps)) {
                min = Math.min(min, passive.referencePeakTps);
                max = Math.max(max, passive.referencePeakTps);
            }
            if (!Double.isFinite(min) || !Double.isFinite(max)) return new Scale(0.0, 10.0);
            double pad = Math.max(1.5, (max - min) * 0.18);
            return new Scale(Math.max(0.0, min - pad), max + pad);
        }

        private Scale detectorScale() {
            double max = 0.1;
            for (int i = 0; i < size; i++) {
                double d = delta[index(i)];
                double th = threshold[index(i)];
                if (Double.isFinite(d)) max = Math.max(max, Math.max(0.0, d));
                if (Double.isFinite(th)) max = Math.max(max, Math.max(0.0, th));
            }
            return new Scale(0.0, max * 1.25);
        }

        private void drawSeries(Graphics2D g2, double[] values, int l, int r,
                                int top, int bottom, Scale scale, Color color,
                                float width, boolean dashed) {
            Path2D.Double path = new Path2D.Double();
            boolean started = false;
            for (int i = 0; i < size; i++) {
                double value = values[index(i)];
                if (!Double.isFinite(value)) {
                    started = false;
                    continue;
                }
                double x = xFor(i, l, r);
                double y = yFor(value, top, bottom, scale);
                if (!started) {
                    path.moveTo(x, y);
                    started = true;
                } else {
                    path.lineTo(x, y);
                }
            }
            g2.setColor(color);
            g2.setStroke(dashed
                    ? new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                            10f, new float[]{6f, 5f}, 0f)
                    : new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(path);
        }

        private void drawPoint(Graphics2D g2, int x, int y, Color color) {
            if (y < 0) return;
            g2.setColor(color);
            g2.fillOval(x - 4, y - 4, 8, 8);
        }

        private int xFor(int ordinal, int l, int r) {
            return size <= 1 ? r : l + (r - l) * ordinal / (size - 1);
        }

        private int yFor(double value, int top, int bottom, Scale scale) {
            if (!Double.isFinite(value)) return -1;
            double span = Math.max(0.0001, scale.max - scale.min);
            double fraction = (value - scale.min) / span;
            fraction = Math.max(0.0, Math.min(1.0, fraction));
            return bottom - (int) Math.round(fraction * (bottom - top));
        }

        private int index(int ordinal) {
            int first = (next - size + CAPACITY) % CAPACITY;
            return (first + ordinal) % CAPACITY;
        }

        private static final class Scale {
            final double min;
            final double max;
            Scale(double min, double max) { this.min = min; this.max = max; }
        }
    }

    private static final class Diagnostics extends JPanel {
        private final JLabel status = new JLabel(), details = new JLabel();
        private final JTextArea reviewArea = new JTextArea();
        private final Timer timer;

        Diagnostics() {
            setLayout(new BorderLayout(0, 8));
            setBackground(AeUiTheme.focusBackground());
            setBorder(new EmptyBorder(8, 10, 8, 10));
            JPanel h = new JPanel(new BorderLayout());
            h.setOpaque(false);
            h.add(label("TPS Movement / Timing Diagnostics", 22, Font.BOLD, AeUiTheme.focusText()), BorderLayout.NORTH);
            h.add(label("Production timing evidence and driver-cue state for the current run.", 11, Font.PLAIN, AeUiTheme.focusMuted()), BorderLayout.SOUTH);
            add(h, BorderLayout.NORTH);
            JPanel body = new JPanel(new GridLayout(1, 2, 8, 0));
            body.setOpaque(false);
            JPanel left = card(new BorderLayout(0, 8));
            left.setBorder(cardBorder());
            status.setFont(new Font("Dialog", Font.BOLD, 17));
            status.setForeground(AeUiTheme.focusBlue());
            left.add(status, BorderLayout.NORTH);
            left.add(details, BorderLayout.CENTER);
            body.add(left);
            reviewArea.setEditable(false);
            reviewArea.setLineWrap(true);
            reviewArea.setWrapStyleWord(true);
            reviewArea.setFont(new Font("Dialog", Font.PLAIN, 11));
            reviewArea.setForeground(AeUiTheme.focusText());
            reviewArea.setBackground(AeUiTheme.card());
            JScrollPane rs = new JScrollPane(reviewArea);
            rs.setBorder(new LineBorder(AeUiTheme.border()));
            body.add(rs);
            add(body, BorderLayout.CENTER);
            JButton close = button("Close");
            close.addActionListener(e -> {
                Window w = SwingUtilities.getWindowAncestor(this);
                if (w != null) w.dispose();
            });
            JPanel f = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            f.setOpaque(false);
            f.add(close);
            add(f, BorderLayout.SOUTH);
            timer = new Timer(200, e -> refresh());
            addHierarchyListener(e -> {
                if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) == 0) return;
                if (isShowing()) { refresh(); timer.start(); }
                else timer.stop();
            });
            refresh();
        }

        private void refresh() {
            GuidedFocusHub.State s = GuidedFocusHub.snapshot();
            EngagementFocusModel m = s != null && s.engagement != null
                    ? s.engagement : EngagementFocusModel.setupFromWorkingTune(GuidedCaptureState.IDLE);
            EngagementPassiveCapture.Snapshot p = EngagementPassiveCapture.snapshot();
            status.setText(m.detectorStatusText());
            details.setText("<html><table cellpadding='4'>"
                    + "<tr><td>Detector</td><td><b>" + (m.workingModel == null ? "unknown" : m.workingModel.displayName()) + "</b></td></tr>"
                    + "<tr><td>Fuel: TPS AE change</td><td><b>" + fmt(m.productionDeltaTps) + "</b></td></tr>"
                    + "<tr><td>AccelThreshold</td><td><b>" + fmt(m.threshold) + "</b></td></tr>"
                    + "<tr><td>Delta Window</td><td><b>" + fmt0(m.timingStatus.currentDeltaWindowMs) + " ms</b></td></tr>"
                    + "<tr><td>Sample Length</td><td><b>" + fmt0(m.timingStatus.currentSampleLengthMs) + " ms</b></td></tr>"
                    + "<tr><td>Comparable</td><td><b>" + p.comparableEvents + " / " + p.targetComparable + "</b></td></tr>"
                    + "<tr><td>Rejected</td><td><b>" + (p.rejectedSmall + p.rejectedLarge + p.rejectedDuration) + "</b></td></tr>"
                    + "<tr><td>RPM span</td><td><b>" + fmt0(p.roadRpmSpan) + " RPM</b></td></tr>"
                    + "</table></html>");
            String text = EngagementPassiveCapture.reviewText(null);
            if (!reviewArea.getText().equals(text)) {
                reviewArea.setText(text);
                reviewArea.setCaretPosition(0);
            }
        }
    }
}