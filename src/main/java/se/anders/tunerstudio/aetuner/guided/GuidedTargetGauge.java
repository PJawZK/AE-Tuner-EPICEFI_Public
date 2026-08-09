package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.util.Locale;

/**
 * Compact live driver gauge with text above the bar, a moving current marker,
 * a target marker, and optional inner/outer target bands.
 */
final class GuidedTargetGauge extends JPanel {
    enum Mode { TPS, RPM }

    private final Mode mode;
    private final JLabel label = new JLabel();
    private final TargetTrack track = new TargetTrack();

    private double value = Double.NaN;
    private double target;
    private double minimum;
    private double maximum;
    private double innerLow;
    private double innerHigh;
    private double outerLow;
    private double outerHigh;

    GuidedTargetGauge(Mode mode) {
        super(new BorderLayout(0, 3));
        this.mode = mode;
        setOpaque(false);
        add(label, BorderLayout.NORTH);
        add(track, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
        setPreferredSize(new Dimension(440, 48));
        setMinimumSize(new Dimension(260, 46));
        if (mode == Mode.TPS) {
            setTps(Double.NaN, 40.0);
        } else {
            setRpm(Double.NaN, 2000.0);
        }
    }

    void setTps(double nextValue, double nextTarget) {
        GuidedVehicleTestLimits.Snapshot limits =
                GuidedVehicleTestLimits.current();
        double tolerance = limits.tpsTolerance;
        value = nextValue;
        target = nextTarget;
        minimum = 0.0;
        maximum = 100.0;
        innerLow = clamp(nextTarget - tolerance, minimum, maximum);
        innerHigh = clamp(nextTarget + tolerance, minimum, maximum);
        outerLow = innerLow;
        outerHigh = innerHigh;
        label.setText("TPS " + format(nextValue, 1) + "% — target "
                + format(nextTarget, 1) + "% — accepted "
                + format(innerLow, 1) + "–" + format(innerHigh, 1) + "%"
                + (limits.enabled ? " — TEST OVERRIDE" : ""));
        track.repaint();
    }

    void setTpsAdaptive(double nextValue, double baselineTps,
                        double desiredStep) {
        value = nextValue;
        minimum = 0.0;
        maximum = 100.0;
        boolean hasFrozenBaseline = Double.isFinite(baselineTps);
        target = hasFrozenBaseline
                ? clamp(baselineTps + desiredStep, minimum, maximum)
                : Double.NaN;
        innerLow = hasFrozenBaseline
                ? clamp(target - 5.0, minimum, maximum) : Double.NaN;
        innerHigh = hasFrozenBaseline
                ? clamp(target + 5.0, minimum, maximum) : Double.NaN;
        outerLow = innerLow;
        outerHigh = innerHigh;
        double actualStep = Double.isFinite(nextValue) && hasFrozenBaseline
                ? nextValue - baselineTps : Double.NaN;
        if (hasFrozenBaseline) {
            label.setText("TPS " + format(nextValue, 1) + "% — frozen baseline "
                    + format(baselineTps, 1) + "% — actual step "
                    + (Double.isFinite(actualStep) && actualStep >= 0.0 ? "+" : "")
                    + format(actualStep, 1) + " — guide +" + format(desiredStep, 1)
                    + " (not an absolute acceptance band)");
        } else {
            label.setText("TPS " + format(nextValue, 1) + "% — guide +"
                    + format(desiredStep, 1)
                    + " (not an absolute acceptance band) — target marker waits for frozen opening baseline");
        }
        track.repaint();
    }

    void setRpmAdaptive(double nextValue, double nextTarget) {
        value = nextValue;
        target = nextTarget;
        minimum = Math.max(0.0, nextTarget - 600.0);
        maximum = nextTarget + 600.0;
        innerLow = nextTarget - 300.0;
        innerHigh = nextTarget + 300.0;
        outerLow = nextTarget - 450.0;
        outerHigh = nextTarget + 450.0;
        label.setText("RPM " + format(nextValue, 0) + " — road region "
                + format(nextTarget, 0) + " ±300 — READY retain ±450");
        track.repaint();
    }

    void setRpm(double nextValue, double nextTarget) {
        value = nextValue;
        target = nextTarget;
        minimum = Math.max(0.0, nextTarget - 400.0);
        maximum = nextTarget + 400.0;
        innerLow = nextTarget - 100.0;
        innerHigh = nextTarget + 100.0;
        outerLow = nextTarget - 200.0;
        outerHigh = nextTarget + 200.0;
        label.setText("RPM " + format(nextValue, 0) + " — target "
                + format(nextTarget, 0) + " — acquire "
                + format(innerLow, 0) + "–" + format(innerHigh, 0)
                + "; retain " + format(outerLow, 0) + "–"
                + format(outerHigh, 0));
        track.repaint();
    }

    String labelTextForTest() {
        return label.getText();
    }

    double targetForTest() {
        return target;
    }

    double innerLowForTest() {
        return innerLow;
    }

    double innerHighForTest() {
        return innerHigh;
    }

    double outerLowForTest() {
        return outerLow;
    }

    double outerHighForTest() {
        return outerHigh;
    }

    int targetPixelForTest(int width) {
        return pixel(target, Math.max(1, width));
    }

    private int pixel(double point, int width) {
        double span = Math.max(0.0001, maximum - minimum);
        double fraction = (clamp(point, minimum, maximum) - minimum) / span;
        return (int) Math.round(fraction * Math.max(0, width - 1));
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    private static String format(double number, int decimals) {
        if (!Double.isFinite(number)) {
            return "n/a";
        }
        return String.format(Locale.US, "%." + decimals + "f", number);
    }

    private final class TargetTrack extends JComponent {
        TargetTrack() {
            setPreferredSize(new Dimension(400, 23));
            setMinimumSize(new Dimension(180, 21));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Insets insets = getInsets();
                int x = insets.left + 1;
                int y = insets.top + 4;
                int width = Math.max(1, getWidth() - insets.left - insets.right - 2);
                int height = Math.max(8, getHeight() - insets.top - insets.bottom - 8);

                Color foreground = UIManager.getColor("ProgressBar.foreground");
                Color background = UIManager.getColor("ProgressBar.background");
                Color text = UIManager.getColor("Label.foreground");
                if (foreground == null) foreground = new Color(70, 120, 180);
                if (background == null) background = new Color(220, 220, 220);
                if (text == null) text = Color.DARK_GRAY;

                g.setColor(background);
                g.fillRoundRect(x, y, width, height, 8, 8);

                if (mode == Mode.RPM) {
                    paintBand(g, x, y, width, height, outerLow, outerHigh,
                            withAlpha(foreground, 55));
                    paintBand(g, x, y, width, height, innerLow, innerHigh,
                            withAlpha(foreground, 100));
                } else if (Double.isFinite(innerLow) && Double.isFinite(innerHigh)) {
                    paintBand(g, x, y, width, height, innerLow, innerHigh,
                            withAlpha(foreground, 100));
                }

                if (Double.isFinite(value)) {
                    int current = x + pixel(value, width);
                    int fillWidth = Math.max(0, Math.min(width, current - x));
                    g.setColor(withAlpha(foreground, 125));
                    g.fillRoundRect(x, y, fillWidth, height, 8, 8);
                    g.setColor(text);
                    g.setStroke(new BasicStroke(2.0f));
                    g.drawLine(current, y - 2, current, y + height + 2);
                    Polygon marker = new Polygon();
                    marker.addPoint(current, y - 3);
                    marker.addPoint(current - 5, y - 9);
                    marker.addPoint(current + 5, y - 9);
                    g.fillPolygon(marker);
                }

                if (Double.isFinite(target)) {
                    int targetX = x + pixel(target, width);
                    g.setColor(text);
                    g.setStroke(new BasicStroke(3.0f));
                    g.drawLine(targetX, y, targetX, y + height);
                }
                g.setColor(text);
                g.setStroke(new BasicStroke(1.0f));
                g.drawRoundRect(x, y, width, height, 8, 8);
            } finally {
                g.dispose();
            }
        }

        private void paintBand(Graphics2D g, int x, int y, int width, int height,
                               double low, double high, Color color) {
            int left = x + pixel(low, width);
            int right = x + pixel(high, width);
            g.setColor(color);
            g.fillRect(Math.min(left, right), y,
                    Math.max(1, Math.abs(right - left)), height);
        }

        private Color withAlpha(Color base, int alpha) {
            return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
        }
    }
}
