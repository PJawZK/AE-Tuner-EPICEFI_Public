package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.model.TransientEvent;
import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

final class EventPlotPanel extends JPanel {
    private TransientEvent event;

    EventPlotPanel() {
        setPreferredSize(new Dimension(520, 260));
        setOpaque(true);
    }

    void setEvent(TransientEvent event) {
        this.event = event;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        setBackground(AeUiTheme.card());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AeUiTheme.border()),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int left = 45;
            int right = Math.max(left + 10, w - 15);
            int top = 25;
            int bottom = Math.max(top + 10, h - 30);

            g.setColor(AeUiTheme.chartGrid());
            g.drawRect(left, top, right - left, bottom - top);
            g.setColor(AeUiTheme.muted());
            g.drawString("TPS / MAP / lambda preview", left + 6, top + 15);

            if (event == null || event.getSamples().size() < 2) {
                g.drawString("Waiting for a Passive AE event...", left + 20, top + 45);
                return;
            }

            List<LiveSample> samples = event.getSamples();
            double start = samples.get(0).getSeconds();
            double end = samples.get(samples.size() - 1).getSeconds();
            if (end <= start) end = start + 1.0;

            drawTrace(g, samples, ChannelRole.TPS, start, end, left, right, top, bottom,
                    0.0, 100.0, AeUiTheme.blue());
            drawTrace(g, samples, ChannelRole.MAP, start, end, left, right, top, bottom,
                    0.0, 250.0, AeUiTheme.green());
            drawTrace(g, samples, ChannelRole.LAMBDA, start, end, left, right, top, bottom,
                    0.65, 1.35, AeUiTheme.amberDark());

            g.setColor(AeUiTheme.muted());
            g.drawString("TPS 0-100%, MAP 0-250 kPa, lambda 0.65-1.35", left, bottom + 18);
        } finally {
            g.dispose();
        }
    }

    private void drawTrace(Graphics2D g,
                           List<LiveSample> samples,
                           ChannelRole role,
                           double start,
                           double end,
                           int left,
                           int right,
                           int top,
                           int bottom,
                           double min,
                           double max,
                           Color color) {
        g.setColor(color);
        int lastX = -1;
        int lastY = -1;
        for (LiveSample sample : samples) {
            double value = sample.get(role);
            if (!Double.isFinite(value)) continue;
            double xRatio = (sample.getSeconds() - start) / (end - start);
            double yRatio = (value - min) / (max - min);
            xRatio = Math.max(0.0, Math.min(1.0, xRatio));
            yRatio = Math.max(0.0, Math.min(1.0, yRatio));
            int x = left + (int) Math.round(xRatio * (right - left));
            int y = bottom - (int) Math.round(yRatio * (bottom - top));
            if (lastX >= 0) g.drawLine(lastX, lastY, x, y);
            lastX = x;
            lastY = y;
        }
    }
}
