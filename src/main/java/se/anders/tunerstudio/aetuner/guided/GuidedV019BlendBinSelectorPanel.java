package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.ui.AeUiTheme;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Prominent v0.19 session selector for 1..4 actual Blend Duration RPM bins. */
final class GuidedV019BlendBinSelectorPanel extends JPanel {
    interface Listener { void selectionChanged(double[] selectedBins); }

    private final Listener listener;
    private final JLabel title = new JLabel("BLEND RPM BINS — SELECT 1–4 FOR THIS SESSION");
    private final JLabel status = new JLabel("Read Working Tune, then choose the bins that need testing.");
    private final JPanel choices = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
    private final List<JToggleButton> buttons = new ArrayList<JToggleButton>();
    private double[] available = new double[0];
    private boolean locked;

    GuidedV019BlendBinSelectorPanel(Listener listener) {
        super(new BorderLayout(0, 3));
        this.listener = listener;
        title.setFont(new Font("Dialog", Font.BOLD, 11));
        status.setFont(new Font("Dialog", Font.BOLD, 10));
        choices.setOpaque(false);
        add(title, BorderLayout.NORTH);
        add(choices, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        setBorder(new CompoundBorder(new LineBorder(AeUiTheme.blue()),
                new EmptyBorder(5, 6, 5, 6)));
        setOpaque(false);
        applyTheme();
    }

    void setAvailableBins(double[] bins, double[] selected) {
        double[] next = bins == null ? new double[0] : bins.clone();
        if (sameArray(next, available)) {
            syncSelected(selected);
            refreshState();
            return;
        }
        available = next;
        choices.removeAll();
        buttons.clear();
        for (final double rpm : available) {
            final JToggleButton button = new JToggleButton(f0(rpm));
            button.setFont(new Font("Dialog", Font.BOLD, 10));
            button.setFocusPainted(false);
            button.setToolTipText("Arm actual Blend Duration table bin " + f0(rpm)
                    + " RPM for automatic stable-RPM latching in this session.");
            button.addActionListener(event -> {
                if (locked) return;
                if (button.isSelected() && selectedCount() > BlendDurationCaptureConfig.MAX_ARMED_RPM_BINS) {
                    button.setSelected(false);
                    status.setText("Maximum 4 bins per session. Deselect one before adding another.");
                    return;
                }
                notifyListener();
                refreshState();
            });
            buttons.add(button);
            choices.add(button);
        }
        syncSelected(selected);
        refreshState();
        revalidate();
        repaint();
    }

    void setLocked(boolean locked) {
        this.locked = locked;
        for (JToggleButton button : buttons) button.setEnabled(!locked);
        refreshState();
    }

    boolean hasValidSelection() {
        int count = selectedCount();
        return count >= 1 && count <= BlendDurationCaptureConfig.MAX_ARMED_RPM_BINS;
    }

    int selectedCount() {
        int count = 0;
        for (JToggleButton button : buttons) if (button.isSelected()) count++;
        return count;
    }

    double[] selectedBins() {
        double[] result = new double[selectedCount()];
        int index = 0;
        for (int i = 0; i < buttons.size(); i++) {
            if (buttons.get(i).isSelected()) result[index++] = available[i];
        }
        return result;
    }

    String summaryText() {
        double[] selected = selectedBins();
        if (selected.length == 0) return "SELECT 1–4";
        StringBuilder out = new StringBuilder();
        for (double rpm : selected) {
            if (out.length() > 0) out.append(" / ");
            out.append(f0(rpm));
        }
        return out + " RPM";
    }

    void applyTheme() {
        title.setForeground(AeUiTheme.text());
        status.setForeground(AeUiTheme.blue());
        setBorder(new CompoundBorder(new LineBorder(AeUiTheme.blue()),
                new EmptyBorder(5, 6, 5, 6)));
    }

    private void syncSelected(double[] selected) {
        for (int i = 0; i < buttons.size(); i++) {
            boolean on = contains(selected, available[i]);
            if (buttons.get(i).isSelected() != on) buttons.get(i).setSelected(on);
            buttons.get(i).setEnabled(!locked);
        }
    }

    private void notifyListener() {
        if (listener != null) listener.selectionChanged(selectedBins());
    }

    private void refreshState() {
        int count = selectedCount();
        if (locked) {
            status.setText("SESSION LOCKED — armed bins " + summaryText()
                    + "; automatic latch chooses one bin per event.");
        } else if (available.length == 0) {
            status.setText("Read Working Tune to load the actual Blend Duration RPM bins.");
        } else if (count == 0) {
            status.setText("Select at least one actual table bin before Start Capture.");
        } else {
            status.setText(count + " bin" + (count == 1 ? "" : "s")
                    + " armed — each event auto-latches after stable RPM dwell; click to add/remove.");
        }
    }

    private static boolean contains(double[] values, double target) {
        if (values == null) return false;
        for (double value : values) if (Math.abs(value - target) <= 0.5) return true;
        return false;
    }

    private static boolean sameArray(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > 0.5) return false;
        }
        return true;
    }

    private static String f0(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.0f", value) : "n/a";
    }
}
