package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
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

/**
 * v0.19 Blend Duration driving view.
 *
 * The teaching diagram and live attempt share one physical-response language:
 * pedal settles, measured MAP crosses the event-relative 20% and 80% levels,
 * and that central interval is the timing evidence. Prediction/fallback MAP is
 * retained only as faint diagnostic context and never sets the chart scale or
 * the physical timing markers.
 */
final class GuidedV019BlendDurationFocus extends GuidedV019FocusBase {
    private final DriverCue cue = new DriverCue();
    private final AttemptTraceChart actual = new AttemptTraceChart();
    private final JLabel liveTps = valueLabel(), liveRpm = valueLabel(), liveMap = valueLabel();
    private final JLabel rpmRegion = smallValue(), currentBlend = smallValue(),
            tpsStep = smallValue(), accepted = smallValue(), authority = smallValue();

    GuidedV019BlendDurationFocus(JDialog owner, Runnable done) {
        super(owner, done);
        build();
    }

    int refreshIntervalMillis() { return 50; }

    String taskTitle() { return "Blend Duration"; }
    String taskSubtitle() {
        return "One opening at a time — measure the car's real MAP response; prediction is diagnostic only";
    }

    JComponent drivingContent() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setOpaque(false);
        cue.setPreferredSize(new Dimension(100, 142));
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
        main.add(chartCard("Maneuver shape — what to do", new InstructionChart()), gc);

        gc.gridx = 1;
        gc.weightx = 0.62;
        gc.insets = new Insets(0, 0, 0, 0);
        main.add(chartCard("Actual attempt — physical MAP response", actual), gc);

        root.add(main, BorderLayout.CENTER);

        JPanel lower = new JPanel(new GridLayout(1, 2, 8, 0));
        lower.setOpaque(false);
        lower.add(liveTriple("Live context", "TPS", liveTps, "RPM", liveRpm, "MAP", liveMap));
        lower.add(summaryGrid("Capture context",
                new String[]{"Entry RPM region", "Existing Blend (context)", "TPS step", "Comparable", "Authority"},
                new JLabel[]{rpmRegion, currentBlend, tpsStep, accepted, authority}));
        lower.setPreferredSize(new Dimension(100, 96));
        root.add(lower, BorderLayout.SOUTH);
        return root;
    }

    JComponent diagnosticsContent() { return new Diagnostics(); }

    void refreshFromProduction() {
        BlendDurationFocusModel m = model();
        cue.update(m);
        actual.update(m);
        if (m == null) {
            liveTps.setText("n/a");
            liveRpm.setText("n/a");
            liveMap.setText("n/a");
            rpmRegion.setText("waiting");
            currentBlend.setText("n/a");
            tpsStep.setText("n/a");
            accepted.setText("0");
            authority.setText("Physical 20→80 only");
            return;
        }
        liveTps.setText(fmt(m.liveTps) + " %");
        liveRpm.setText(fmt0(m.liveRpm));
        liveMap.setText(fmt(m.liveMap) + " kPa");
        rpmRegion.setText(Double.isFinite(m.targetRpm)
                ? "~" + fmt0(m.targetRpm) + " ±" + fmt0(m.rpmTolerance) + " RPM (start only)"
                : "automatic");
        currentBlend.setText(Double.isFinite(m.currentBlendDuration)
                ? String.format(java.util.Locale.ROOT, "%.3f s", m.currentBlendDuration) : "n/a");
        double shownStep = Double.isFinite(m.liveTpsStep)
                ? m.liveTpsStep
                : (Double.isFinite(m.lastEventHeldTps) && Double.isFinite(m.lastEventBaseTps)
                    ? m.lastEventHeldTps - m.lastEventBaseTps : Double.NaN);
        tpsStep.setText((Double.isFinite(shownStep) ? "+" + fmt(shownStep) : "n/a")
                + " / usable +" + fmt0(m.tpsStepLow) + "…" + fmt0(m.tpsStepHigh)
                + " / group spread ≤" + fmt0(BlendDurationComparabilityGroups.TPS_STEP_LIMIT));
        accepted.setText(m.matchingEvents + " / " + m.targetEvents);
        authority.setText("Physical MAP 20→80");
        repaint();
    }

    private static BlendDurationFocusModel model() {
        GuidedFocusHub.State s = GuidedFocusHub.snapshot();
        return s != null && s.recipe == GuidedTuningRecipe.BLEND_DURATION ? s.blendDuration : null;
    }

    private static final class DriverCue extends JPanel {
        private static final String[] STEP_TEXT = {
                "1  STEADY", "2  OPEN ONCE", "3  HOLD / MEASURE", "4  RETURN", "5  REPEAT"
        };
        private final JLabel state = new JLabel();
        private final JLabel instruction = new JLabel();
        private final JLabel sub = new JLabel();
        private final JLabel context = new JLabel();
        private final JPanel steps = new JPanel(new GridLayout(1, 5, 6, 0));
        private final JLabel[] stepLabels = new JLabel[STEP_TEXT.length];

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
            for (int i = 0; i < stepLabels.length; i++) {
                JLabel label = new JLabel(STEP_TEXT[i], SwingConstants.CENTER);
                label.setFont(new Font("Dialog", Font.BOLD, 9));
                label.setOpaque(true);
                stepLabels[i] = label;
                steps.add(label);
            }
            updateSteps(-1);
            add(steps, BorderLayout.SOUTH);
        }

        void update(BlendDurationFocusModel m) {
            String title = "GET STEADY";
            String inst = "Hold a calm pedal and let the baseline settle.";
            String subtext = "When READY appears: one smooth opening, then stop moving your foot.";
            Color color = AeUiTheme.focusAmber();
            int active = 0;
            String ctx = "Waiting for production Blend state";
            if (m != null) {
                inst = m.instruction;
                subtext = m.status;
                switch (m.phase) {
                    case OPEN_AND_SETTLE:
                        title = "OPEN ONCE";
                        color = AeUiTheme.focusBlue();
                        active = 1;
                        break;
                    case HOLD_FOR_MAP:
                        title = "HOLD STILL";
                        color = AeUiTheme.focusGreen();
                        active = 2;
                        break;
                    case RESULT:
                        title = "RETURN / RE-ARM";
                        color = AeUiTheme.navy();
                        active = 3;
                        break;
                    case COMPLETE:
                        title = "SERIES COMPLETE";
                        color = AeUiTheme.navy();
                        active = 4;
                        break;
                    case PAUSED:
                        title = "PAUSED";
                        color = AeUiTheme.focusAmber();
                        active = -1;
                        break;
                    case GET_STEADY:
                        title = m.allValidEvents > 0 ? "RE-ARM" : "GET STEADY";
                        color = m.allValidEvents > 0 ? AeUiTheme.navy() : AeUiTheme.focusAmber();
                        active = m.allValidEvents > 0 ? 3 : 0;
                        break;
                    default:
                        break;
                }
                ctx = "<html><div style='text-align:right'>"
                        + (Double.isFinite(m.liveRpm) ? fmt0(m.liveRpm) + " RPM" : "RPM n/a")
                        + "<br>Comparable " + m.matchingEvents + " / " + m.targetEvents
                        + "<br>Trace " + (m.currentTrace.hasData()
                        ? (m.currentTrace.frozen ? "FROZEN" : "LIVE") : "waiting")
                        + "<br>Capture writes NONE</div></html>";
            }
            state.setText("● " + title);
            state.setForeground(color);
            instruction.setText("<html>" + escape(inst) + "</html>");
            sub.setText("<html>" + escape(subtext) + "</html>");
            context.setText(ctx);
            updateSteps(active);
            repaint();
        }

        private void updateSteps(int active) {
            Color[] accents = {
                    AeUiTheme.focusAmber(), AeUiTheme.focusBlue(), AeUiTheme.focusGreen(),
                    AeUiTheme.navy(), AeUiTheme.navy()
            };
            for (int i = 0; i < stepLabels.length; i++) {
                boolean on = i == active;
                Color accent = accents[i];
                JLabel label = stepLabels[i];
                label.setBackground(on ? alpha(accent, 35) : AeUiTheme.neutralSoft());
                label.setForeground(on ? accent : AeUiTheme.focusMuted());
                label.setBorder(new LineBorder(on ? accent : AeUiTheme.border()));
            }
        }

        int stepComponentCount() { return steps.getComponentCount(); }
    }

    /** Static teaching diagram for the primary physical 20→80 timing method. */
    private static final class InstructionChart extends JPanel {
        InstructionChart() {
            setBackground(AeUiTheme.card());
            setPreferredSize(new Dimension(430, 280));
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D)g.create();
            try {
                prepare(g2);
                PlotArea a = new PlotArea(getWidth(), getHeight());
                drawGrid(g2, a);
                drawLaneLabels(g2, a);

                int xOpen = a.x(0.20);
                int xSettle = a.x(0.39);
                int x20 = a.x(0.47);
                int x80 = a.x(0.71);

                Path2D.Double tps = new Path2D.Double();
                tps.moveTo(a.left, a.tpsY(0.78));
                tps.lineTo(xOpen, a.tpsY(0.78));
                tps.curveTo(xOpen + 18, a.tpsY(0.72),
                        xSettle - 24, a.tpsY(0.25), xSettle, a.tpsY(0.25));
                tps.lineTo(a.right, a.tpsY(0.25));
                drawPath(g2, tps, AeUiTheme.focusBlue(), 3.0f, false, 1.0f);

                Path2D.Double measured = new Path2D.Double();
                measured.moveTo(a.left, a.mapY(0.84));
                measured.lineTo(xOpen, a.mapY(0.84));
                measured.curveTo(xOpen + 32, a.mapY(0.80),
                        x20, a.mapY(0.70), x20, a.mapY(0.66));
                measured.curveTo(x20 + 40, a.mapY(0.57),
                        x80 - 35, a.mapY(0.33), x80, a.mapY(0.29));
                measured.curveTo(x80 + 30, a.mapY(0.24),
                        a.right - 30, a.mapY(0.22), a.right, a.mapY(0.22));
                drawPath(g2, measured, AeUiTheme.focusGreen(), 3.0f, false, 1.0f);

                Path2D.Double diagnostic = new Path2D.Double();
                diagnostic.moveTo(a.left, a.mapY(0.82));
                diagnostic.lineTo(xOpen, a.mapY(0.82));
                diagnostic.curveTo(xOpen + 30, a.mapY(0.70),
                        xSettle, a.mapY(0.35), a.right, a.mapY(0.18));
                drawPath(g2, diagnostic, AeUiTheme.focusMuted(), 1.5f, true, 0.55f);

                marker(g2, a, xSettle, "PEDAL STOPS", AeUiTheme.focusBlue());
                marker(g2, a, x20, "MAP 20%", AeUiTheme.focusGreen());
                marker(g2, a, x80, "MAP 80%", AeUiTheme.focusGreen());

                g2.setFont(SMALL);
                g2.setColor(AeUiTheme.focusMuted());
                g2.drawString("Blue: pedal/TPS", a.left + 8, a.bottom - 8);
                g2.drawString("Green: measured physical MAP — time 20% → 80%", a.left + 112, a.bottom - 8);
                g2.drawString("Dashed: prediction diagnostic only", a.left + 8, a.mapTop + 12);
            } finally {
                g2.dispose();
            }
        }
    }

    /** Actual current event. Live while moving; frozen after an outcome. */
    private static final class AttemptTraceChart extends JPanel {
        private BlendDurationFocusModel model;

        AttemptTraceChart() {
            setBackground(AeUiTheme.card());
            setPreferredSize(new Dimension(660, 280));
        }

        void update(BlendDurationFocusModel model) {
            this.model = model;
            repaint();
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D)g.create();
            try {
                prepare(g2);
                PlotArea a = new PlotArea(getWidth(), getHeight());
                drawGrid(g2, a);
                drawLaneLabels(g2, a);

                if (model == null || !model.currentTrace.hasData()) {
                    g2.setColor(AeUiTheme.focusMuted());
                    g2.setFont(new Font("Dialog", Font.BOLD, 11));
                    g2.drawString("Waiting for the next opening — the completed attempt will stay here until you start another.",
                            a.left + 18, (a.top + a.bottom) / 2);
                    return;
                }

                BlendDurationFocusTrace current = model.currentTrace;
                BlendDurationFocusTrace ghost = model.previousAcceptedTrace;
                double maxTime = maxTime(current, ghost);
                Scale tpsScale = tpsScale(current, ghost);
                Scale mapScale = physicalMapScale(current, ghost);

                if (ghost != null && ghost.hasData()) {
                    drawTrace(g2, a, ghost, maxTime, tpsScale, mapScale, true);
                }
                drawTrace(g2, a, current, maxTime, tpsScale, mapScale, false);

                g2.setFont(new Font("Dialog", Font.BOLD, 10));
                g2.setColor(current.frozen ? AeUiTheme.navy() : AeUiTheme.focusBlue());
                String state = current.frozen
                        ? "FROZEN RESULT" + (current.outcome.length() == 0 ? "" : " — " + current.outcome)
                        : "LIVE CURRENT ATTEMPT";
                g2.drawString(state, a.left + 8, a.top - 8);

                if (ghost != null && ghost.hasData()) {
                    g2.setColor(AeUiTheme.focusMuted());
                    g2.drawString("ghost = previous accepted physical response", a.right - 215, a.top - 8);
                }

                if (Double.isFinite(current.pedalSettledSeconds)) {
                    marker(g2, a, a.timeX(current.pedalSettledSeconds, maxTime),
                            "PEDAL STOPS", AeUiTheme.focusBlue());
                }
                if (Double.isFinite(current.responseLowSeconds)) {
                    marker(g2, a, a.timeX(current.responseLowSeconds, maxTime),
                            "MAP 20%", AeUiTheme.focusGreen());
                }
                if (Double.isFinite(current.responseHighSeconds)) {
                    marker(g2, a, a.timeX(current.responseHighSeconds, maxTime),
                            "MAP 80%", AeUiTheme.focusGreen());
                }

                g2.setFont(SMALL);
                g2.setColor(AeUiTheme.focusMuted());
                String duration = Double.isFinite(model.physicalResponseSeconds)
                        ? "Physical 20→80 " + fmtMillis(model.physicalResponseSeconds)
                            + (model.boundedLateWindow ? " — bounded late-window evidence" : "")
                        : "Physical 20→80 timing in progress";
                g2.drawString(duration, a.left + 8, a.bottom - 8);
            } finally {
                g2.dispose();
            }
        }

        private static void drawTrace(Graphics2D g2, PlotArea a,
                                      BlendDurationFocusTrace trace,
                                      double maxTime,
                                      Scale tpsScale,
                                      Scale mapScale,
                                      boolean ghost) {
            float alpha = ghost ? 0.28f : 1.0f;
            boolean dashed = ghost;
            drawSeries(g2, a, trace.seconds, trace.tps, maxTime, tpsScale,
                    true, ghost ? AeUiTheme.focusMuted() : AeUiTheme.focusBlue(),
                    ghost ? 1.8f : 3.0f, dashed, alpha);
            drawSeries(g2, a, trace.seconds, trace.map, maxTime, mapScale,
                    false, ghost ? AeUiTheme.focusMuted() : AeUiTheme.focusGreen(),
                    ghost ? 1.8f : 3.0f, dashed, alpha);
            if (!ghost) {
                // Prediction stays visible as diagnostic context but is deliberately
                // excluded from physicalMapScale so a biased target cannot compress
                // the real MAP response that owns the timing decision.
                drawSeries(g2, a, trace.seconds, trace.fallbackMap, maxTime, mapScale,
                        false, AeUiTheme.focusMuted(), 1.4f, true, 0.45f);
            }
        }

        private static double maxTime(BlendDurationFocusTrace current,
                                      BlendDurationFocusTrace ghost) {
            double max = current == null ? 0.0 : current.durationSeconds();
            if (ghost != null && ghost.hasData()) max = Math.max(max, ghost.durationSeconds());
            return Math.max(0.6, max);
        }

        private static Scale tpsScale(BlendDurationFocusTrace current,
                                      BlendDurationFocusTrace ghost) {
            Scale s = new Scale();
            s.add(current == null ? null : current.tps);
            if (ghost != null) s.add(ghost.tps);
            return s.finish(4.0);
        }

        private static Scale physicalMapScale(BlendDurationFocusTrace current,
                                              BlendDurationFocusTrace ghost) {
            Scale s = new Scale();
            if (current != null) {
                s.add(current.map);
                s.add(current.physicalLateMapKpa);
                s.add(current.responseLowMapKpa);
                s.add(current.responseHighMapKpa);
            }
            if (ghost != null) {
                s.add(ghost.map);
                s.add(ghost.physicalLateMapKpa);
                s.add(ghost.responseLowMapKpa);
                s.add(ghost.responseHighMapKpa);
            }
            return s.finish(6.0);
        }
    }

    private static final class PlotArea {
        final int left, right, top, bottom, laneGap;
        final int tpsTop, tpsBottom, mapTop, mapBottom;

        PlotArea(int width, int height) {
            left = 48;
            right = Math.max(left + 80, width - 18);
            top = 28;
            bottom = Math.max(top + 120, height - 28);
            laneGap = 20;
            int usable = bottom - top - laneGap - 24;
            int half = Math.max(45, usable / 2);
            tpsTop = top + 16;
            tpsBottom = tpsTop + half;
            mapTop = tpsBottom + laneGap;
            mapBottom = mapTop + half;
        }

        int x(double fraction) {
            return left + (int)Math.round((right - left) * fraction);
        }

        int timeX(double seconds, double maxTime) {
            double f = maxTime <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, seconds / maxTime));
            return x(f);
        }

        int tpsY(double normalized) {
            return tpsTop + (int)Math.round((tpsBottom - tpsTop) * normalized);
        }

        int mapY(double normalized) {
            return mapTop + (int)Math.round((mapBottom - mapTop) * normalized);
        }
    }

    private static final class Scale {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        void add(double[] values) {
            if (values == null) return;
            for (double v : values) add(v);
        }

        void add(double value) {
            if (!Double.isFinite(value)) return;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        Scale finish(double minimumSpan) {
            if (!Double.isFinite(min) || !Double.isFinite(max)) {
                min = 0.0;
                max = minimumSpan;
                return this;
            }
            double span = Math.max(minimumSpan, max - min);
            double center = (min + max) * 0.5;
            min = center - span * 0.62;
            max = center + span * 0.62;
            return this;
        }

        double normalize(double value) {
            if (!Double.isFinite(value) || !(max > min)) return Double.NaN;
            return Math.max(0.0, Math.min(1.0, 1.0 - (value - min) / (max - min)));
        }
    }

    private static void prepare(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private static void drawGrid(Graphics2D g2, PlotArea a) {
        g2.setColor(AeUiTheme.chartGridSoft());
        for (int i = 0; i <= 5; i++) {
            int x = a.left + (a.right - a.left) * i / 5;
            g2.drawLine(x, a.tpsTop, x, a.mapBottom);
        }
        g2.drawLine(a.left, a.tpsBottom, a.right, a.tpsBottom);
        g2.drawLine(a.left, a.mapBottom, a.right, a.mapBottom);
    }

    private static void drawLaneLabels(Graphics2D g2, PlotArea a) {
        g2.setFont(new Font("Dialog", Font.BOLD, 9));
        g2.setColor(AeUiTheme.focusMuted());
        g2.drawString("PEDAL / TPS", a.left, a.tpsTop - 4);
        g2.drawString("MEASURED MAP", a.left, a.mapTop - 4);
        g2.drawString("TIME →", a.right - 42, a.mapBottom + 15);
    }

    private static void drawSeries(Graphics2D g2, PlotArea a,
                                   double[] time, double[] values,
                                   double maxTime, Scale scale,
                                   boolean tpsLane,
                                   Color color, float width,
                                   boolean dashed, float alpha) {
        if (time == null || values == null) return;
        int n = Math.min(time.length, values.length);
        Path2D.Double path = new Path2D.Double();
        boolean started = false;
        for (int i = 0; i < n; i++) {
            if (!Double.isFinite(time[i]) || !Double.isFinite(values[i])) {
                started = false;
                continue;
            }
            int x = a.timeX(time[i], maxTime);
            double normalized = scale.normalize(values[i]);
            if (!Double.isFinite(normalized)) continue;
            int y = tpsLane ? a.tpsY(normalized) : a.mapY(normalized);
            if (!started) {
                path.moveTo(x, y);
                started = true;
            } else {
                path.lineTo(x, y);
            }
        }
        drawPath(g2, path, color, width, dashed, alpha);
    }

    private static void drawPath(Graphics2D g2, Path2D path, Color color,
                                 float width, boolean dashed, float alpha) {
        Color draw = new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.max(0, Math.min(255, Math.round(alpha * 255f))));
        g2.setColor(draw);
        g2.setStroke(dashed
                ? new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10f, new float[]{6f, 5f}, 0f)
                : new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(path);
    }

    private static void marker(Graphics2D g2, PlotArea a, int x, String text, Color color) {
        if (x < a.left || x > a.right) return;
        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER, 10f, new float[]{4f, 4f}, 0f));
        g2.drawLine(x, a.tpsTop, x, a.mapBottom);
        g2.setFont(new Font("Dialog", Font.BOLD, 8));
        g2.drawString(text, Math.min(x + 4, a.right - 82), a.tpsTop + 11);
    }

    private static final class Diagnostics extends JPanel {
        private final JLabel status = label("Waiting", 17, Font.BOLD, AeUiTheme.focusBlue());
        private final JTextArea details = new JTextArea();
        private final Timer timer;

        Diagnostics() {
            setLayout(new BorderLayout(0, 8));
            setBackground(AeUiTheme.focusBackground());
            setBorder(new EmptyBorder(8, 10, 8, 10));
            JPanel h = new JPanel(new BorderLayout());
            h.setOpaque(false);
            h.add(label("Blend Duration Diagnostics", 22, Font.BOLD, AeUiTheme.focusText()), BorderLayout.NORTH);
            h.add(label("Physical MAP response first; prediction/fallback evidence remains diagnostic only.",
                    11, Font.PLAIN, AeUiTheme.focusMuted()), BorderLayout.SOUTH);
            add(h, BorderLayout.NORTH);
            JPanel top = new JPanel(new GridLayout(1, 4, 8, 0));
            top.setOpaque(false);
            top.add(infoTable("Current Status", new String[][]{{"State", "CAPTURING / REVIEW"}}));
            top.add(infoTable("Current RPM Region", new String[][]{{"RPM", "automatic"}}));
            top.add(infoTable("Existing Blend Setting", new String[][]{{"Authority", "CONTEXT ONLY"}}));
            top.add(infoTable("Authority", new String[][]{{"Proposal", "WITHHELD"}}));
            JPanel center = new JPanel(new BorderLayout(0, 8));
            center.setOpaque(false);
            center.add(top, BorderLayout.NORTH);
            details.setEditable(false);
            details.setLineWrap(true);
            details.setWrapStyleWord(true);
            details.setFont(new Font("Dialog", Font.PLAIN, 11));
            details.setForeground(AeUiTheme.focusText());
            details.setBackground(AeUiTheme.card());
            JScrollPane s = new JScrollPane(details);
            s.setBorder(new LineBorder(AeUiTheme.border()));
            center.add(s, BorderLayout.CENTER);
            add(center, BorderLayout.CENTER);
            add(status, BorderLayout.SOUTH);
            timer = new Timer(200, e -> refresh());
            addHierarchyListener(e -> {
                if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) == 0) return;
                if (isShowing()) {
                    refresh();
                    timer.start();
                } else {
                    timer.stop();
                }
            });
            refresh();
        }

        private void refresh() {
            BlendDurationFocusModel m = model();
            if (m == null) {
                status.setText("Waiting for production Blend Focus state");
                details.setText("");
                return;
            }
            status.setText(m.status);
            String text = "PHYSICAL MAP RESPONSE — PRIMARY MEASUREMENT\n"
                    + "RPM target / live: " + fmt0(m.targetRpm) + " / " + fmt0(m.liveRpm) + " rpm\n"
                    + "Measured MAP: " + fmt(m.liveMap) + " kPa\n"
                    + "Physical baseline MAP: " + fmt(m.physicalBaselineMap) + " kPa\n"
                    + "Physical late MAP: " + fmt(m.physicalLateMap) + " kPa\n"
                    + "Physical MAP step: " + fmt(m.physicalMapStep) + " kPa\n"
                    + "20% / 80% levels: " + fmt(m.responseLowMap) + " / " + fmt(m.responseHighMap) + " kPa\n"
                    + "Physical 20→80 duration: " + fmtMillis(m.physicalResponseSeconds) + "\n"
                    + "Observation elapsed: " + fmtMillis(m.physicalObservationSeconds) + "\n"
                    + "Late-window confidence: " + (m.boundedLateWindow ? "BOUNDED / LOWER CONFIDENCE" : (m.physicalResponseComplete ? "SETTLED" : "OBSERVING")) + "\n"
                    + "Soft pedal plateau: " + (m.softPlateauAcquired ? "ACQUIRED" : "WAITING") + "\n"
                    + "Trace points: current " + m.currentTrace.seconds.length
                    + " / previous accepted " + m.previousAcceptedTrace.seconds.length + "\n\n"
                    + "PREDICTION / FALLBACK — DIAGNOSTIC ONLY\n"
                    + "Predicted / fallback MAP live: " + fmt(m.liveFallbackMap) + " kPa\n"
                    + "Latest timer-reset prediction target: " + fmt(m.predictionTarget) + " kPa\n"
                    + "Prediction target gap: " + fmt(m.targetGap) + " kPa\n"
                    + "Effective MAP: " + fmt(m.liveEffectiveMap) + " kPa\n"
                    + "Existing RPM-interpolated Blend Duration: " + fmtMillis(m.currentBlendDuration) + " (context only)\n"
                    + "Prediction counters: " + m.predictionCounterEvidence + "\n"
                    + "Effective-MAP replay samples: " + m.effectiveMapReplaySamples + "\n"
                    + "Mean |error|: " + fmt(m.effectiveMapMeanAbsoluteError) + " kPa\n"
                    + "Max |error|: " + fmt(m.effectiveMapMaxAbsoluteError) + " kPa\n"
                    + "Replay consistent: " + m.effectiveMapReplayConsistent + "\n\n"
                    + "COMPARABILITY\n"
                    + "Matching " + m.matchingEvents + " / " + m.targetEvents
                    + " | valid " + m.allValidEvents + " | excluded " + m.excludedEvents
                    + " | returned " + m.returnedEvents + "\n"
                    + m.repeatability + "\n" + m.comparabilityHint + "\n\n" + m.detail;
            if (!details.getText().equals(text)) {
                details.setText(text);
                details.setCaretPosition(0);
            }
        }
    }

    int driverStepComponentCountForTest() { return cue.stepComponentCount(); }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
