package se.anders.tunerstudio.aetuner.guided;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Locale;

/** Lightweight presentation/control surface for the explicit TPS quiet baseline. */
public final class EngagementQuietCalibrationPanel extends JPanel {
    private final boolean driverMode;
    private final JLabel title = new JLabel("TPS QUIET BASELINE", SwingConstants.CENTER);
    private final JLabel state = new JLabel("State: UNCALIBRATED", SwingConstants.CENTER);
    private final JLabel timing = new JLabel("0.0 s | 0 samples", SwingConstants.CENTER);
    private final JLabel rates = new JLabel("P95 n/a | P99 n/a | intent floor n/a", SwingConstants.CENTER);
    private final JLabel quality = new JLabel("Revision 0 | retries 0 | trimmed outliers 0", SwingConstants.CENTER);
    private final JTextArea instruction = textArea("Start Capture to begin TPS quiet calibration.", 2);
    private final JButton recalibrate = new JButton("Calibrate TPS Quiet Baseline");
    private final JTextArea action = textArea("", 2);

    public EngagementQuietCalibrationPanel(boolean driverMode) {
        super(new BorderLayout(6, 6));
        this.driverMode = driverMode;
        setBorder(driverMode
                ? BorderFactory.createEmptyBorder(24, 28, 24, 28)
                : BorderFactory.createTitledBorder("TPS QUIET BASELINE"));
        buildUi();
        refreshFromGate();
    }

    private void buildUi() {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        title.setAlignmentX(CENTER_ALIGNMENT);
        state.setAlignmentX(CENTER_ALIGNMENT);
        timing.setAlignmentX(CENTER_ALIGNMENT);
        rates.setAlignmentX(CENTER_ALIGNMENT);
        quality.setAlignmentX(CENTER_ALIGNMENT);
        instruction.setAlignmentX(CENTER_ALIGNMENT);
        action.setAlignmentX(CENTER_ALIGNMENT);
        recalibrate.setAlignmentX(CENTER_ALIGNMENT);

        if (driverMode) {
            title.setText("CALIBRATE TPS QUIET BASELINE");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 34f));
            state.setFont(state.getFont().deriveFont(Font.BOLD, 19f));
            instruction.setFont(instruction.getFont().deriveFont(Font.BOLD, 24f));
            instruction.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
            body.add(Box.createVerticalGlue());
            body.add(title);
            body.add(Box.createVerticalStrut(18));
            body.add(instruction);
            body.add(Box.createVerticalStrut(16));
            body.add(state);
            body.add(Box.createVerticalStrut(8));
            body.add(timing);
            body.add(Box.createVerticalStrut(5));
            body.add(rates);
            body.add(Box.createVerticalGlue());
        } else {
            title.setVisible(false);
            state.setFont(state.getFont().deriveFont(Font.BOLD, 11f));
            timing.setFont(timing.getFont().deriveFont(10f));
            rates.setFont(rates.getFont().deriveFont(10f));
            quality.setFont(quality.getFont().deriveFont(10f));
            instruction.setFont(instruction.getFont().deriveFont(Font.PLAIN, 10f));
            instruction.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            action.setFont(action.getFont().deriveFont(Font.PLAIN, 10f));
            action.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            recalibrate.setFont(recalibrate.getFont().deriveFont(Font.BOLD, 10f));

            body.add(state);
            body.add(timing);
            body.add(rates);
            body.add(quality);
            body.add(Box.createVerticalStrut(2));
            body.add(instruction);
            body.add(Box.createVerticalStrut(2));
            body.add(recalibrate);
            body.add(action);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));

            recalibrate.addActionListener(event -> {
                EngagementQuietCalibrationControl.Result result =
                        EngagementQuietCalibrationControl.recalibrate();
                action.setText(result.message);
                refreshFromGate();
            });
        }
        add(body, BorderLayout.CENTER);
    }

    public void refreshFromGate() {
        FoundationTpsNoiseGate.Evaluation gate = FoundationTpsNoiseGate.evaluate(null);
        update(gate);
    }

    public void update(FoundationTpsNoiseGate.Evaluation gate) {
        FoundationTpsNoiseGate.Evaluation safe = gate == null
                ? FoundationTpsNoiseGate.evaluate(null) : gate;
        FoundationTpsNoiseGate.CalibrationState calibrationState = safe.calibrationState;

        state.setText("State: " + calibrationState.name()
                + " | revision " + safe.calibrationRevision);
        timing.setText(fmt1(safe.calibrationDurationSeconds) + " s | "
                + safe.quietSamples + " samples");
        rates.setText("P95 " + rate(safe.quietRateP95)
                + " | P99 " + rate(safe.quietRateP99)
                + " | derived intent floor " + rate(safe.intentRateFloor));
        quality.setText("Revision " + safe.calibrationRevision
                + " | retries " + safe.calibrationRetries
                + " | trimmed outliers " + safe.trimmedOutliers);

        if (calibrationState == FoundationTpsNoiseGate.CalibrationState.FROZEN) {
            instruction.setText("Baseline frozen for this comparison set. Recalibrating restores any temporary Delta Window first and restarts comparable sweep evidence.");
        } else if (calibrationState == FoundationTpsNoiseGate.CalibrationState.CALIBRATING) {
            instruction.setText(driverMode
                    ? "ENGINE IDLING\nPEDAL UNTOUCHED — hold steady until FROZEN"
                    : "Engine idling; pedal untouched. Hold steady until the baseline reaches FROZEN.");
        } else {
            instruction.setText(driverMode
                    ? "START CAPTURE\nThen keep the engine idling and pedal untouched"
                    : "Start Capture to begin the explicit pedal-untouched quiet baseline.");
        }

        if (!driverMode) {
            if (calibrationState == FoundationTpsNoiseGate.CalibrationState.FROZEN) {
                recalibrate.setText("Recalibrate / Update TPS Quiet Baseline");
            } else if (calibrationState == FoundationTpsNoiseGate.CalibrationState.CALIBRATING) {
                recalibrate.setText("TPS Quiet Calibration in progress");
            } else {
                recalibrate.setText("Calibrate TPS Quiet Baseline");
            }
            recalibrate.setEnabled(
                    calibrationState != FoundationTpsNoiseGate.CalibrationState.CALIBRATING
                    && EngagementQuietCalibrationControl.canRecalibrate());
            if (action.getText().length() == 0) {
                action.setText(safe.calibrationStatus);
            }
        }
    }

    private static JTextArea textArea(String text, int rows) {
        JTextArea area = new JTextArea(text, rows, 1);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private static String rate(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%.2f %%TPS/s", value) : "n/a";
    }

    private static String fmt1(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%.1f", value) : "0.0";
    }

    String stateTextForTest() { return state.getText(); }
    String timingTextForTest() { return timing.getText(); }
    String ratesTextForTest() { return rates.getText(); }
    String qualityTextForTest() { return quality.getText(); }
    String instructionTextForTest() { return instruction.getText(); }
    boolean recalibrateEnabledForTest() { return !driverMode && recalibrate.isEnabled(); }
    String recalibrateTextForTest() { return driverMode ? "" : recalibrate.getText(); }
    void clickRecalibrateForTest() { if (!driverMode) recalibrate.doClick(); }
}
