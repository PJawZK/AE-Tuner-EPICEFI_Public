package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.FoundationThresholdFocusModel;
import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

/** v0.19 Threshold / Sensitivity driving view over the production threshold learner. */
final class GuidedV019ThresholdFocus extends GuidedV019FocusBase {
    private final DriverCue cue = new DriverCue();
    private final ActivityChart activity = new ActivityChart();
    private final JLabel liveTps = valueLabel(), liveRpm = valueLabel(), liveDelta = valueLabel();
    private final JLabel effectiveThreshold = smallValue(), quiet = smallValue(), accel = smallValue(), normal = smallValue();

    GuidedV019ThresholdFocus(JDialog owner, Runnable done) { super(owner, done); build(); }
    int refreshIntervalMillis() { return 50; }
    String taskTitle() { return "Threshold / Sensitivity"; }
    String taskSubtitle() { return "Driver-guided passive threshold learning — evidence shape left, rolling live trace right"; }

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
        main.add(chartCard("Threshold evidence — what it means", new InstructionChart()), gc);

        gc.gridx = 1;
        gc.weightx = 0.62;
        gc.insets = new Insets(0, 0, 0, 0);
        main.add(chartCard("Live detector activity — signal vs threshold", activity), gc);
        root.add(main, BorderLayout.CENTER);

        JPanel lower = new JPanel(new GridLayout(1, 2, 8, 0));
        lower.setOpaque(false);
        lower.add(liveTriple("Live context", "TPS", liveTps, "RPM", liveRpm, "TPS AE change", liveDelta));
        lower.add(summaryGrid("Evidence status",
                new String[]{"Effective AccelThreshold", "Quiet calibration", "Acceleration Openings", "Normal Corrections"},
                new JLabel[]{effectiveThreshold, quiet, accel, normal}));
        lower.setPreferredSize(new Dimension(100, 82));
        root.add(lower, BorderLayout.SOUTH);
        return root;
    }

    JComponent diagnosticsContent() { return new Diagnostics(); }

    void refreshFromProduction() {
        FoundationThresholdFocusModel m = model();
        cue.update(m);
        activity.update(m);
        if (m == null) {
            liveTps.setText("n/a");
            liveRpm.setText("n/a");
            liveDelta.setText("n/a");
            effectiveThreshold.setText("n/a");
            quiet.setText("waiting");
            accel.setText("0");
            normal.setText("0");
            review.setEnabled(false);
            return;
        }
        liveTps.setText(fmt(m.liveTps) + " %");
        liveRpm.setText(fmt0(m.liveRpm));
        liveDelta.setText(fmt(m.liveDelta));
        effectiveThreshold.setText(fmt(m.liveThreshold));
        quiet.setText(m.quietSamples + " / " + m.quietTarget + " • " + fmt(m.quietDurationSeconds) + " s");
        int a = 0, n = 0;
        for (FoundationThresholdFocusModel.Bin b : m.bins) {
            a += b.accelerationOpeningEvents;
            n += b.normalCorrectionEvents;
        }
        accel.setText(String.valueOf(a));
        normal.setText(String.valueOf(n));
        review.setEnabled("COMPLETE".equals(m.captureState));
        repaint();
    }

    int liveGraphSamplesForTest() { return activity.sampleCount(); }

    private static FoundationThresholdFocusModel model() {
        GuidedFocusHub.State s = GuidedFocusHub.snapshot();
        return s != null && s.recipe == GuidedTuningRecipe.FOUNDATION_THRESHOLD ? s.foundationThreshold : null;
    }

    private static final class DriverCue extends JPanel {
        final JLabel state = new JLabel(), instruction = new JLabel(), sub = new JLabel(), context = new JLabel();
        final JPanel steps = new JPanel(new GridLayout(1, 4, 6, 0));

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

        void update(FoundationThresholdFocusModel m) {
            String title = "READ WORKING TUNE";
            String action = "Load the production threshold context before capture.";
            String detail = "No threshold evidence loaded.";
            Color color = AeUiTheme.focusAmber();
            int active = 0;
            String region = "RPM n/a";
            int ae = 0, nc = 0;
            if (m != null) {
                action = m.driverInstruction();
                detail = m.recommendationStatus();
                region = m.liveBin() == null
                        ? (Double.isFinite(m.liveRpm) ? fmt0(m.liveRpm) + " RPM" : "RPM n/a")
                        : m.liveBin().regionLabel;
                for (FoundationThresholdFocusModel.Bin b : m.bins) {
                    ae += b.accelerationOpeningEvents;
                    nc += b.normalCorrectionEvents;
                }
                if ("PAUSED".equals(m.captureState)) {
                    title = "PAUSED"; color = AeUiTheme.focusAmber(); active = -1;
                } else if (!m.calibrationFrozen) {
                    title = "DRIVE NORMALLY"; color = AeUiTheme.focusBlue(); active = 0;
                } else if (Double.isFinite(m.liveRatio) && m.liveRatio >= 1.0) {
                    title = "ACCELERATION OPENING"; color = AeUiTheme.focusGreen(); active = 2;
                } else {
                    FoundationThresholdFocusModel.Bin b = m.liveBin();
                    if (b != null && "ACCELERATION OPENING".equals(b.requestedClassLabel())) {
                        title = "MAKE ONE NORMAL OPENING"; color = AeUiTheme.focusAmber(); active = 1;
                    } else {
                        title = "KEEP DRIVING"; color = AeUiTheme.navy(); active = 3;
                    }
                }
            }
            state.setText("● " + title);
            state.setForeground(color);
            instruction.setText("<html>" + escape(action) + "</html>");
            sub.setText("<html>" + escape(detail) + "</html>");
            context.setText("<html><div style='text-align:right'>" + region
                    + "<br>Accel openings " + ae
                    + "<br>Normal corrections " + nc
                    + "<br>Capture writes NONE</div></html>");
            steps.removeAll();
            addStep("1", "DRIVE", active == 0, AeUiTheme.focusBlue());
            addStep("2", "OPEN", active == 1, AeUiTheme.focusAmber());
            addStep("3", "DETECTED", active == 2, AeUiTheme.focusGreen());
            addStep("4", "BUILD", active == 3, AeUiTheme.navy());
            revalidate();
            repaint();
        }

        void addStep(String n, String text, boolean on, Color accent) {
            JLabel l = new JLabel(n + "  " + text, SwingConstants.CENTER);
            l.setFont(new Font("Dialog", Font.BOLD, 10));
            l.setOpaque(true);
            l.setBackground(on ? alpha(accent, 35) : AeUiTheme.neutralSoft());
            l.setForeground(on ? accent : AeUiTheme.focusMuted());
            l.setBorder(new LineBorder(on ? accent : AeUiTheme.border()));
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
                int l = 35, r = getWidth() - 18, t = 28, b = getHeight() - 34;
                g2.setColor(AeUiTheme.chartGridSoft());
                for (int i = 0; i < 5; i++) {
                    int y = t + (b - t) * i / 4;
                    g2.drawLine(l, y, r, y);
                }
                int th = t + (b - t) * 42 / 100;
                g2.setColor(AeUiTheme.thresholdFill());
                g2.fillRect(l, th + 22, r - l, b - th - 22);
                g2.setColor(AeUiTheme.red());
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10f, new float[]{7, 5}, 0));
                g2.drawLine(l, th, r, th);
                int[] xs = {l, l + 45, l + 80, l + 115, l + 150, l + 185, l + 220, l + 255, l + 290, r};
                int[] ys = {b - 22, b - 28, b - 18, b - 26, b - 22, t + 42, b - 24, b - 20, b - 29, b - 21};
                draw(g2, xs, ys, AeUiTheme.focusBlue(), 3f, false);
                g2.setFont(new Font("Dialog", Font.BOLD, 10));
                g2.setColor(AeUiTheme.focusMuted());
                g2.drawString("NORMAL CORRECTION — useful", l + 12, b - 45);
                g2.setColor(AeUiTheme.red());
                g2.drawString("EFFECTIVE AccelThreshold", Math.max(l + 10, r - 130), th - 7);
                g2.setColor(AeUiTheme.focusGreen());
                g2.drawString("ACCELERATION OPENING", l + 165, t + 28);
                g2.setFont(SMALL);
                g2.setColor(AeUiTheme.focusMuted());
                g2.drawString("stays below threshold", l + 12, b - 31);
                g2.drawString("crosses clearly", l + 165, t + 42);
            } finally {
                g2.dispose();
            }
        }
    }

    /** Rolling UI-only signal history; it never participates in threshold evidence or recommendation. */
    private static final class ActivityChart extends JPanel {
        private static final int CAPACITY = 120;
        private final double[] signal = new double[CAPACITY];
        private final double[] threshold = new double[CAPACITY];
        private int size;
        private int next;
        private FoundationThresholdFocusModel current;

        ActivityChart() {
            setBackground(AeUiTheme.card());
            setPreferredSize(new Dimension(660, 260));
        }

        void update(FoundationThresholdFocusModel m) {
            current = m;
            if (m != null && (Double.isFinite(m.liveDelta) || Double.isFinite(m.liveThreshold))) {
                signal[next] = m.liveDelta;
                threshold[next] = m.liveThreshold;
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
                int l = 48, r = getWidth() - 16, t = 28, b = getHeight() - 30;
                if (r <= l || b <= t) return;
                drawGrid(g2, l, r, t, b);
                g2.setFont(SMALL);
                g2.setColor(AeUiTheme.focusMuted());
                g2.drawString("old", l, b + 18);
                g2.drawString("now", r - 22, b + 18);

                if (size < 2) {
                    g2.drawString("Waiting for rolling production detector data", l + 18, (t + b) / 2);
                    return;
                }

                Scale scale = scale();
                drawSeries(g2, signal, l, r, t + 10, b - 10, scale,
                        AeUiTheme.focusBlue(), 2.8f, false);
                drawSeries(g2, threshold, l, r, t + 10, b - 10, scale,
                        AeUiTheme.red(), 1.8f, true);

                int last = index(size - 1);
                int x = xFor(size - 1, l, r);
                int y = yFor(signal[last], t + 10, b - 10, scale);
                boolean crossing = Double.isFinite(signal[last]) && Double.isFinite(threshold[last])
                        && signal[last] >= threshold[last];
                if (y >= 0) {
                    g2.setColor(crossing ? AeUiTheme.focusGreen() : AeUiTheme.focusBlue());
                    g2.fillOval(x - 5, y - 5, 10, 10);
                }

                g2.setFont(new Font("Dialog", Font.BOLD, 10));
                g2.setColor(crossing ? AeUiTheme.focusGreen() : AeUiTheme.focusBlue());
                g2.drawString("TPS AE change " + fmt(signal[last]), l + 8, t + 14);
                g2.setColor(AeUiTheme.red());
                g2.drawString("AccelThreshold " + fmt(threshold[last]), r - 150, t + 14);
                if (current != null) {
                    FoundationThresholdFocusModel.Bin bin = current.liveBin();
                    if (bin != null) {
                        g2.setColor(AeUiTheme.focusMuted());
                        g2.setFont(SMALL);
                        g2.drawString(bin.regionLabel + " • " + bin.requestedClassLabel(), l + 8, b - 2);
                    }
                }
            } finally {
                g2.dispose();
            }
        }

        private void drawGrid(Graphics2D g2, int l, int r, int t, int b) {
            g2.setColor(AeUiTheme.chartGridSoft());
            for (int i = 0; i <= 6; i++) {
                int x = l + (r - l) * i / 6;
                g2.drawLine(x, t, x, b);
            }
            for (int i = 0; i <= 5; i++) {
                int y = t + (b - t) * i / 5;
                g2.drawLine(l, y, r, y);
            }
        }

        private Scale scale() {
            double max = 0.1;
            for (int i = 0; i < size; i++) {
                double sig = signal[index(i)];
                double thr = threshold[index(i)];
                if (Double.isFinite(sig)) max = Math.max(max, Math.max(0.0, sig));
                if (Double.isFinite(thr)) max = Math.max(max, Math.max(0.0, thr));
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
        private final JTable table = new JTable();
        private final JTextArea reviewArea = new JTextArea();
        private final JLabel status = label("Waiting", 17, Font.BOLD, AeUiTheme.focusBlue());
        private final ActivityChart diagnosticActivity = new ActivityChart();
        private final Timer timer;

        Diagnostics() {
            setLayout(new BorderLayout(0, 8));
            setBackground(AeUiTheme.focusBackground());
            setBorder(new EmptyBorder(8, 10, 8, 10));
            JPanel h = new JPanel(new BorderLayout());
            h.setOpaque(false);
            h.add(label("Threshold / Sensitivity Diagnostics", 22, Font.BOLD, AeUiTheme.focusText()), BorderLayout.NORTH);
            h.add(label("Normal Correction / Acceleration Opening separation and real production semantics.", 11, Font.PLAIN, AeUiTheme.focusMuted()), BorderLayout.SOUTH);
            add(h, BorderLayout.NORTH);
            JPanel center = new JPanel(new BorderLayout(8, 8));
            center.setOpaque(false);
            JPanel top = new JPanel(new GridLayout(1, 2, 8, 0));
            top.setOpaque(false);
            top.add(chartCard("Rolling detector activity", diagnosticActivity));
            top.add(infoCard());
            center.add(top, BorderLayout.NORTH);
            center.add(themedTableScroll(table), BorderLayout.CENTER);
            reviewArea.setEditable(false);
            reviewArea.setLineWrap(true);
            reviewArea.setWrapStyleWord(true);
            reviewArea.setBackground(AeUiTheme.card());
            reviewArea.setForeground(AeUiTheme.focusText());
            reviewArea.setFont(new Font("Dialog", Font.PLAIN, 10));
            JScrollPane rs = new JScrollPane(reviewArea);
            rs.setPreferredSize(new Dimension(100, 110));
            center.add(rs, BorderLayout.SOUTH);
            add(center, BorderLayout.CENTER);
            timer = new Timer(200, e -> refresh());
            addHierarchyListener(e -> {
                if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) == 0) return;
                if (isShowing()) { refresh(); timer.start(); }
                else timer.stop();
            });
            refresh();
        }

        private JComponent infoCard() {
            JPanel p = card(new BorderLayout(0, 5));
            p.setBorder(cardBorder());
            p.add(label("Algorithm / Evidence Details", 13, Font.BOLD, AeUiTheme.focusText()), BorderLayout.NORTH);
            p.add(status, BorderLayout.CENTER);
            return p;
        }

        private void refresh() {
            FoundationThresholdFocusModel m = model();
            diagnosticActivity.update(m);
            if (m == null) {
                table.setModel(new DefaultTableModel(new Object[0][0], new String[]{"RPM", "State"}));
                status.setText("Waiting for production threshold state");
                return;
            }
            status.setText(m.recommendationStatus());
            String[] cols = {"RPM region", "Current", "Normal Corr.", "Accel Opening", "P95", "P25", "Gap", "Proposed", "State"};
            Object[][] rows = new Object[m.bins.size()][cols.length];
            for (int i = 0; i < m.bins.size(); i++) {
                FoundationThresholdFocusModel.Bin b = m.bins.get(i);
                rows[i] = new Object[]{b.regionLabel, fmt(b.currentThreshold), b.normalCorrectionEvents,
                        b.accelerationOpeningEvents, fmt(b.normalCorrectionP95), fmt(b.accelerationOpeningP25),
                        fmt(b.separationGap), b.changed ? fmt(b.proposedThreshold) : "—", b.status};
            }
            table.setModel(new DefaultTableModel(rows, cols) {
                public boolean isCellEditable(int r, int c) { return false; }
            });
            styleTable(table);
            String tx = m.reviewText == null ? "" : m.reviewText;
            if (!reviewArea.getText().equals(tx)) {
                reviewArea.setText(tx);
                reviewArea.setCaretPosition(0);
            }
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}