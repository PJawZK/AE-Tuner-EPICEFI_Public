package se.anders.tunerstudio.aetuner.guided;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;

/**
 * Driver-facing TPS Movement / Timing coach.
 *
 * Driver view has deliberately no root scroll container. Live refreshes update
 * values only and must not move the viewport. Engagement Model, Sample Length
 * and Fast Callback are read-only context; only Delta Window is available as a
 * secondary explicit A/B experiment.
 */
public final class EngagementDetectionGuidedFocusPanel extends JPanel {
    private final JLabel current = new JLabel("Working tune not read");
    private final JTextArea nextAction = text(
            "READ WORKING TUNE\nLoad the current TPS Movement / Timing baseline first.",
            3, 24f, Font.BOLD);
    private final JLabel detectorState = new JLabel("WAIT — live TPS movement data unavailable");
    private final JLabel liveTps = new JLabel("TPS: n/a");
    private final JLabel liveRpm = new JLabel("RPM: n/a");
    private final JLabel liveDelta = new JLabel("Fuel: TPS AE change: n/a");
    private final JLabel liveWindow = new JLabel("Window / stride: n/a");
    private final JLabel newestCheck = new JLabel("Dual Stride / Newest check: n/a");
    private final JLabel prerequisites = new JLabel("Read Working Tune for detector prerequisites");
    private final JProgressBar detectedSignal = signalBar();
    private final JProgressBar eventProgress = new JProgressBar();
    private final JProgressBar dataProgress = new JProgressBar(0, 100);
    private final JTextArea maneuverPlan = text(defaultManeuverPlan(), 7, 13f, Font.PLAIN);
    private final JTextArea audioPlan = text(defaultAudioPlan(), 7, 13f, Font.PLAIN);

    private final JSpinner requestedDeltaWindow = new JSpinner(
            new SpinnerNumberModel(Double.valueOf(25.0), Double.valueOf(1.0),
                    Double.valueOf(500.0), Double.valueOf(1.0)));
    private final JToggleButton settingsToggle =
            new JToggleButton("Show Delta Window A/B control");
    private final JPanel settingsPanel = new JPanel(new GridLayout(1, 3, 8, 6));
    private final JPanel settingsHost = new JPanel();
    private boolean updating;
    private boolean driverView = true;

    public EngagementDetectionGuidedFocusPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("AE Foundation — TPS Movement / Timing");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.add(title, BorderLayout.NORTH);
        header.add(current, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);

        JPanel coaching = new JPanel(new BorderLayout(8, 8));
        JPanel actionPanel = new JPanel(new BorderLayout());
        actionPanel.setBorder(BorderFactory.createTitledBorder("WHAT TO DO NOW"));
        actionPanel.add(nextAction, BorderLayout.CENTER);
        coaching.add(actionPanel, BorderLayout.NORTH);

        JPanel liveRow = new JPanel(new GridLayout(1, 2, 8, 8));
        liveRow.add(buildLivePanel());
        liveRow.add(buildContextPanel());
        coaching.add(liveRow, BorderLayout.CENTER);

        JPanel guideRow = new JPanel(new GridLayout(1, 2, 8, 8));
        guideRow.add(wrap("MANEUVER / DELTA WINDOW A-B", maneuverPlan));
        guideRow.add(wrap("AUDIO / EYES-UP CUES", audioPlan));
        coaching.add(guideRow, BorderLayout.SOUTH);
        add(coaching, BorderLayout.CENTER);

        settingsPanel.setBorder(BorderFactory.createTitledBorder(
                "SECONDARY — explicit Delta Window experiment"));
        settingsPanel.add(new JLabel("Delta Window (ms)"));
        settingsPanel.add(requestedDeltaWindow);
        settingsPanel.add(new JLabel("Apply/Restore is handled by the reviewed proposal controls"));
        settingsPanel.setVisible(false);

        settingsHost.setLayout(new BoxLayout(settingsHost, BoxLayout.Y_AXIS));
        settingsHost.add(settingsToggle);
        settingsHost.add(settingsPanel);
        add(settingsHost, BorderLayout.SOUTH);

        requestedDeltaWindow.addChangeListener(event -> {
            if (updating) return;
            EngagementDetectionWriteSelection.requestDeltaWindowMs(
                    ((Number) requestedDeltaWindow.getValue()).doubleValue());
            refreshFromSelection();
        });
        settingsToggle.addActionListener(event -> updateSettingsVisibility());
        refreshFromSelection();
        setDriverView(true);
    }

    private JPanel buildLivePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
                "TPS MOVEMENT -> DETECTED CHANGE -> THRESHOLD"));
        detectorState.setFont(detectorState.getFont().deriveFont(Font.BOLD, 17f));
        panel.add(detectorState);

        JPanel values = new JPanel(new GridLayout(2, 2, 6, 3));
        values.add(liveTps);
        values.add(liveRpm);
        values.add(liveDelta);
        values.add(liveWindow);
        panel.add(values);

        detectedSignal.setBorder(BorderFactory.createTitledBorder(
                "Fuel: TPS AE change / AccelThreshold — crossing = 100%"));
        detectedSignal.setPreferredSize(new Dimension(460, 56));
        panel.add(detectedSignal);
        panel.add(newestCheck);

        eventProgress.setStringPainted(true);
        eventProgress.setBorder(BorderFactory.createTitledBorder("Observed intentional events"));
        panel.add(eventProgress);
        return panel;
    }

    private JPanel buildContextPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("SETUP / EVIDENCE QUALITY"));
        JPanel top = new JPanel(new GridLayout(2, 1, 2, 2));
        top.add(prerequisites);
        dataProgress.setStringPainted(true);
        dataProgress.setBorder(BorderFactory.createTitledBorder("Required-channel completeness"));
        top.add(dataProgress);
        panel.add(top, BorderLayout.NORTH);

        JTextArea info = text(
                "Engagement Model: read-only controller context; AE Tuner does not select it.\n"
                + "Sample Length: read-only until independent tuning value is established.\n"
                + "Fast Callback: read-only prerequisite/info; ~200 Hz is the intended workflow.\n"
                + "Delta Window: physically qualified scalar Apply/readback/Restore and the current A/B timing setting.",
                7, 13f, Font.PLAIN);
        panel.add(info, BorderLayout.CENTER);
        return panel;
    }

    public void updateModel(EngagementFocusModel model) {
        refreshFromSelection();
        if (model == null) {
            nextAction.setText("READ WORKING TUNE\nLoad the current TPS Movement / Timing baseline, then connect live data.");
            detectorState.setText("WAIT — live TPS movement data unavailable");
            clearLive();
            maneuverPlan.setText(defaultManeuverPlan());
            audioPlan.setText(defaultAudioPlan());
            return;
        }

        nextAction.setText(model.nextActionText());
        detectorState.setText(model.detectorStatusText());
        liveTps.setText("TPS: " + fmt(model.tps) + " %");
        liveRpm.setText("RPM: " + fmt(model.rpm));
        liveDelta.setText("Fuel: TPS AE change: " + fmt(model.productionDeltaTps));
        liveWindow.setText("Window / stride: " + fmt(model.windowMs) + " ms / "
                + fmt(model.stride) + " sample(s)");
        newestCheck.setText("Dual Stride / Newest diagnostic: " + fmt(model.newestPair)
                + " | production difference: "
                + differenceText(model.productionDeltaTps, model.newestPair));
        prerequisites.setText(model.prerequisiteText());

        updateRatioBar(detectedSignal, model.productionDeltaTps, model.threshold);

        eventProgress.setMinimum(0);
        eventProgress.setMaximum(Math.max(1, model.targetEvents));
        eventProgress.setValue(Math.min(model.activityEvents, model.targetEvents));
        eventProgress.setString(model.activityEvents + " / " + model.targetEvents
                + " minimum representative events");

        int completeness = model.observedSamples <= 0 ? 0
                : (int) Math.round(100.0 * model.completeRequiredSamples
                        / Math.max(1, model.observedSamples));
        completeness = Math.max(0, Math.min(100, completeness));
        dataProgress.setValue(completeness);
        dataProgress.setString(model.observedSamples <= 0
                ? "No capture samples yet" : completeness + "% required-complete samples");

        maneuverPlan.setText(model.maneuverPlanText());
        audioPlan.setText(model.audioPlanText());
    }

    public void setDriverView(boolean driver) {
        driverView = driver;
        settingsToggle.setVisible(!driver);
        if (driver) settingsToggle.setSelected(false);
        updateSettingsVisibility();
        nextAction.setFont(nextAction.getFont().deriveFont(Font.BOLD, driver ? 27f : 23f));
        detectorState.setFont(detectorState.getFont().deriveFont(Font.BOLD,
                driver ? 19f : 17f));
    }

    private void updateSettingsVisibility() {
        settingsPanel.setVisible(!driverView && settingsToggle.isSelected());
        revalidate();
        repaint();
    }

    public void refreshFromSelection() {
        EngagementDetectionWriteSelection.Snapshot state =
                EngagementDetectionWriteSelection.snapshot();
        updating = true;
        try {
            if (state.baselineAvailable) {
                requestedDeltaWindow.setValue(Double.valueOf(state.requestedDeltaWindowMs));
                requestedDeltaWindow.setEnabled(true);
            } else {
                requestedDeltaWindow.setEnabled(false);
            }
            if (state.modelBaselineAvailable || state.baselineAvailable
                    || state.sampleLengthBaselineAvailable
                    || state.fastCallbackBaselineAvailable) {
                String fast = state.fastCallbackBaselineAvailable
                        ? (state.baselineFastCallback ? "ON (~200 Hz)" : "OFF") : "unknown";
                current.setText("Working tune: detector " + state.engagementModel
                        + " (read-only) | Delta Window " + format(state.baselineDeltaWindowMs) + " ms"
                        + " | Sample Length " + format(state.baselineSampleLengthSeconds) + " s (read-only)"
                        + " | Fast Callback " + fast + " (read-only)"
                        + pendingText(state));
            } else {
                current.setText("Working tune baseline unavailable. Use Read Working Tune.");
            }
        } finally {
            updating = false;
        }
    }

    private void clearLive() {
        liveTps.setText("TPS: n/a");
        liveRpm.setText("RPM: n/a");
        liveDelta.setText("Fuel: TPS AE change: n/a");
        liveWindow.setText("Window / stride: n/a");
        newestCheck.setText("Dual Stride / Newest check: n/a");
        detectedSignal.setValue(0);
        detectedSignal.setString("n/a");
        eventProgress.setMinimum(0);
        eventProgress.setMaximum(1);
        eventProgress.setValue(0);
        eventProgress.setString("No capture events yet");
        dataProgress.setValue(0);
        dataProgress.setString("No capture samples yet");
    }

    private static JProgressBar signalBar() {
        JProgressBar bar = new JProgressBar(0, 200);
        bar.setStringPainted(true);
        return bar;
    }

    private static void updateRatioBar(JProgressBar bar, double value, double threshold) {
        if (!Double.isFinite(value) || !Double.isFinite(threshold) || threshold <= 0.000001) {
            bar.setValue(0);
            bar.setString("n/a");
            return;
        }
        int percent = (int) Math.round(100.0 * value / threshold);
        bar.setValue(Math.max(0, Math.min(200, percent)));
        bar.setString(fmt(value) + " / " + fmt(threshold) + " = " + percent + "%");
    }

    private static JPanel wrap(String title, JTextArea area) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(area, BorderLayout.CENTER);
        return panel;
    }

    private static JTextArea text(String value, int rows, float size, int style) {
        JTextArea area = new JTextArea(value, rows, 1);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFocusable(false);
        area.setFont(area.getFont().deriveFont(style, size));
        area.setMargin(new java.awt.Insets(6, 7, 6, 7));
        return area;
    }

    private static String pendingText(EngagementDetectionWriteSelection.Snapshot state) {
        if (!state.hasRequestedDeltaWindowChange()) return " | no test change selected";
        return " | DELTA WINDOW TEST PENDING: " + format(state.baselineDeltaWindowMs)
                + " -> " + format(state.requestedDeltaWindowMs) + " ms";
    }

    private static String defaultManeuverPlan() {
        return "Baseline: normal opening -> quick stab/hold -> partial lift/reapply -> stacked short stabs. Review first. If evidence justifies it, change Delta Window only and repeat the same set at similar RPM/load.";
    }

    private static String defaultAudioPlan() {
        return "READY = data present/below threshold. TARGET = detected TPS change crossed AccelThreshold. RETURN = detected change cleared. COMPLETE = review set finished.";
    }

    private static String differenceText(double a, double b) {
        if (!Double.isFinite(a) || !Double.isFinite(b)) return "n/a";
        return fmt(Math.abs(a - b));
    }

    private static String fmt(double value) {
        return Double.isFinite(value)
                ? String.format(java.util.Locale.ROOT, "%.3f", value)
                : "n/a";
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) return "n/a";
        return String.format(java.util.Locale.ROOT, "%.3f", value)
                .replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    // Test helpers intentionally describe product behavior, not Swing internals.
    boolean settingsToggleVisibleForTest() { return settingsToggle.isVisible(); }
    boolean settingsPanelVisibleForTest() { return settingsPanel.isVisible(); }
    int selectedSignalPercentForTest() { return detectedSignal.getValue(); }
    String detectorStateForTest() { return detectorState.getText(); }
    String currentTextForTest() { return current.getText(); }
    String actionTextForTest() { return nextAction.getText(); }
    String maneuverTextForTest() { return maneuverPlan.getText(); }
    boolean hasRootScrollForTest() { return false; }
    String guidanceTextForTest() { return nextAction.getText() + "\n" + maneuverPlan.getText() + "\n" + audioPlan.getText(); }
    boolean deltaWindowEnabledForTest() { return requestedDeltaWindow.isEnabled(); }
    void setDeltaWindowForTest(double value) { requestedDeltaWindow.setValue(Double.valueOf(value)); }

    // Compatibility names retained for the Delta Window routing regression.
    boolean requestedDeltaWindowEnabledForTest() { return deltaWindowEnabledForTest(); }
    double requestedDeltaWindowForTest() {
        return ((Number) requestedDeltaWindow.getValue()).doubleValue();
    }
    void setRequestedDeltaWindowForTest(double value) { setDeltaWindowForTest(value); }
}
