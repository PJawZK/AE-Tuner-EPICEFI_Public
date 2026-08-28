package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.EngagementModelOption;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;

/**
 * Driver-facing detector coach. Live evidence guidance is primary; explicit
 * setting controls remain available only as a secondary A/B experiment tool.
 */
public final class EngagementDetectionGuidedFocusPanel extends JPanel {
    private final JLabel current = new JLabel("Working tune not read");
    private final JTextArea nextAction = text("READ WORKING TUNE\nLoad the current detector baseline first.", 4, 23f, Font.BOLD);
    private final JLabel detectorState = new JLabel("WAIT — live detector data unavailable");
    private final JLabel liveTps = new JLabel("TPS: n/a");
    private final JLabel liveRpm = new JLabel("RPM: n/a");
    private final JLabel liveDelta = new JLabel("Fuel: TPS AE change: n/a");
    private final JLabel liveWindow = new JLabel("Window / stride: n/a");
    private final JProgressBar selectedSignal = signalBar();
    private final JProgressBar legacySignal = signalBar();
    private final JProgressBar timedSignal = signalBar();
    private final JProgressBar spanSignal = signalBar();
    private final JProgressBar floorSignal = signalBar();
    private final JProgressBar newestSignal = signalBar();
    private final JProgressBar eventProgress = new JProgressBar();
    private final JProgressBar dataProgress = new JProgressBar(0, 100);
    private final JTextArea maneuverPlan = text(
            "Start Capture to build a detector comparison set.", 10, 14f, Font.PLAIN);
    private final JTextArea audioPlan = text(
            "Sound-cue meanings will appear with live detector guidance.", 7, 13f, Font.PLAIN);

    private final JComboBox<EngagementModelOption> requestedModel =
            new JComboBox<EngagementModelOption>(EngagementModelOption.values());
    private final JSpinner requestedDeltaWindow = new JSpinner(
            new SpinnerNumberModel(Double.valueOf(25.0), Double.valueOf(1.0),
                    Double.valueOf(500.0), Double.valueOf(1.0)));
    private final JSpinner requestedSampleLength = new JSpinner(
            new SpinnerNumberModel(Double.valueOf(0.050), Double.valueOf(0.001),
                    Double.valueOf(5.000), Double.valueOf(0.005)));
    private final JCheckBox requestedFastCallback = new JCheckBox("Enabled (~200 Hz)");
    private final JToggleButton settingsToggle =
            new JToggleButton("Show setting experiment controls");
    private final JPanel settingsPanel = new JPanel(new GridLayout(2, 4, 8, 6));
    private final JPanel settingsHost = new JPanel();
    private boolean updating;
    private boolean driverView = true;
    private EngagementFocusModel liveModel;

    public EngagementDetectionGuidedFocusPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel title = new JLabel("AE Foundation — Detector Model / Timing");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JPanel header = new JPanel(new BorderLayout(0, 5));
        header.add(title, BorderLayout.NORTH);
        header.add(current, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);

        JPanel coaching = new JPanel();
        coaching.setLayout(new BoxLayout(coaching, BoxLayout.Y_AXIS));

        JPanel actionPanel = new JPanel(new BorderLayout());
        actionPanel.setBorder(BorderFactory.createTitledBorder("WHAT TO DO NOW"));
        actionPanel.add(nextAction, BorderLayout.CENTER);
        coaching.add(actionPanel);

        JPanel liveRow = new JPanel(new GridLayout(1, 2, 8, 8));
        liveRow.add(buildLiveDetectorPanel());
        liveRow.add(buildComparisonPanel());
        coaching.add(liveRow);

        JPanel guideRow = new JPanel(new GridLayout(1, 2, 8, 8));
        guideRow.add(wrap("MANEUVER / A-B COMPARISON RECIPE", maneuverPlan));
        guideRow.add(wrap("AUDITABLE AUDIO CUES", audioPlan));
        coaching.add(guideRow);

        JScrollPane coachingScroll = new JScrollPane(coaching);
        coachingScroll.setBorder(null);
        coachingScroll.getVerticalScrollBar().setUnitIncrement(18);
        add(coachingScroll, BorderLayout.CENTER);

        buildSettingsPanel();
        settingsHost.setLayout(new BoxLayout(settingsHost, BoxLayout.Y_AXIS));
        settingsHost.add(settingsToggle);
        settingsHost.add(settingsPanel);
        add(settingsHost, BorderLayout.SOUTH);

        installSettingActions();
        settingsToggle.addActionListener(event -> updateSettingsVisibility());
        refreshFromSelection();
        setDriverView(true);
    }

    private JPanel buildLiveDetectorPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("LIVE SELECTED DETECTOR"));

        detectorState.setFont(detectorState.getFont().deriveFont(Font.BOLD, 17f));
        panel.add(detectorState);

        JPanel values = new JPanel(new GridLayout(2, 2, 6, 3));
        values.add(liveTps);
        values.add(liveRpm);
        values.add(liveDelta);
        values.add(liveWindow);
        panel.add(values);

        selectedSignal.setBorder(BorderFactory.createTitledBorder(
                "Selected output / AccelThreshold — threshold crossing = 100%"));
        selectedSignal.setPreferredSize(new Dimension(460, 52));
        panel.add(selectedSignal);

        eventProgress.setStringPainted(true);
        eventProgress.setBorder(BorderFactory.createTitledBorder(
                "Observed detector-activity events"));
        panel.add(eventProgress);

        dataProgress.setStringPainted(true);
        dataProgress.setBorder(BorderFactory.createTitledBorder(
                "Required-channel completeness"));
        panel.add(dataProgress);
        return panel;
    }

    private JPanel buildComparisonPanel() {
        JPanel panel = new JPanel(new GridLayout(5, 1, 2, 2));
        panel.setBorder(BorderFactory.createTitledBorder(
                "ALL FIVE DETECTOR OUTPUTS / AccelThreshold"));
        prepareModelBar(legacySignal, "Max step (legacy)");
        prepareModelBar(timedSignal, "Max step, timed");
        prepareModelBar(spanSignal, "Window span");
        prepareModelBar(floorSignal, "Rise from floor");
        prepareModelBar(newestSignal, "Dual stride, newest");
        panel.add(legacySignal);
        panel.add(timedSignal);
        panel.add(spanSignal);
        panel.add(floorSignal);
        panel.add(newestSignal);
        return panel;
    }

    private void buildSettingsPanel() {
        settingsPanel.setBorder(BorderFactory.createTitledBorder(
                "SECONDARY — one-setting-at-a-time experiment"));
        settingsPanel.add(new JLabel("Engagement Model"));
        settingsPanel.add(requestedModel);
        settingsPanel.add(new JLabel("Delta Window (ms)"));
        settingsPanel.add(requestedDeltaWindow);
        settingsPanel.add(new JLabel("Sample Length (s)"));
        settingsPanel.add(requestedSampleLength);
        settingsPanel.add(new JLabel("Fast Callback"));
        settingsPanel.add(requestedFastCallback);
        settingsPanel.setVisible(false);
    }

    private void installSettingActions() {
        requestedModel.addActionListener(event -> {
            if (updating) return;
            Object selected = requestedModel.getSelectedItem();
            if (selected instanceof EngagementModelOption) {
                EngagementDetectionWriteSelection.requestEngagementModel(
                        (EngagementModelOption) selected);
                refreshFromSelection();
            }
        });
        requestedDeltaWindow.addChangeListener(event -> {
            if (updating) return;
            EngagementDetectionWriteSelection.requestDeltaWindowMs(
                    ((Number) requestedDeltaWindow.getValue()).doubleValue());
            refreshFromSelection();
        });
        requestedSampleLength.addChangeListener(event -> {
            if (updating) return;
            EngagementDetectionWriteSelection.requestSampleLengthSeconds(
                    ((Number) requestedSampleLength.getValue()).doubleValue());
            refreshFromSelection();
        });
        requestedFastCallback.addActionListener(event -> {
            if (updating) return;
            EngagementDetectionWriteSelection.requestFastCallback(
                    requestedFastCallback.isSelected());
            refreshFromSelection();
        });
    }

    public void updateModel(EngagementFocusModel model) {
        liveModel = model;
        refreshFromSelection();
        if (model == null) {
            nextAction.setText("READ WORKING TUNE\nLoad the current Detector Model / Timing baseline, then connect live data.");
            detectorState.setText("WAIT — live detector data unavailable");
            clearLiveBars();
            maneuverPlan.setText(defaultManeuverPlan());
            audioPlan.setText(defaultAudioPlan());
            return;
        }

        nextAction.setText(model.nextActionText());
        nextAction.setCaretPosition(0);
        detectorState.setText(model.detectorStatusText());
        liveTps.setText("TPS: " + fmt(model.tps) + " %");
        liveRpm.setText("RPM: " + fmt(model.rpm));
        liveDelta.setText("Fuel: TPS AE change: " + fmt(model.productionDeltaTps));
        liveWindow.setText("Window / stride: " + fmt(model.windowMs) + " ms / "
                + fmt(model.stride) + " sample(s)");

        updateRatioBar(selectedSignal, model.selectedOutput, model.threshold,
                "Selected " + modelName(model) + ": ");
        updateRatioBar(legacySignal, model.legacy, model.threshold, "");
        updateRatioBar(timedSignal, model.timed, model.threshold, "");
        updateRatioBar(spanSignal, model.span, model.threshold, "");
        updateRatioBar(floorSignal, model.floor, model.threshold, "");
        updateRatioBar(newestSignal, model.newest, model.threshold, "");

        eventProgress.setMinimum(0);
        eventProgress.setMaximum(Math.max(1, model.targetEvents));
        eventProgress.setValue(Math.min(model.activityEvents, model.targetEvents));
        eventProgress.setString(model.activityEvents + " / " + model.targetEvents
                + " minimum activity events");

        int completeness = model.observedSamples <= 0 ? 0
                : (int) Math.round(100.0 * model.completeRequiredSamples
                        / Math.max(1, model.observedSamples));
        completeness = Math.max(0, Math.min(100, completeness));
        dataProgress.setValue(completeness);
        dataProgress.setString(model.observedSamples <= 0
                ? "No capture samples yet"
                : completeness + "% required-complete samples");

        maneuverPlan.setText(model.maneuverPlanText());
        maneuverPlan.setCaretPosition(0);
        audioPlan.setText(model.audioPlanText());
        audioPlan.setCaretPosition(0);
    }

    public void setDriverView(boolean driver) {
        driverView = driver;
        settingsToggle.setVisible(!driver);
        if (driver) settingsToggle.setSelected(false);
        updateSettingsVisibility();
        float actionSize = driver ? 26f : 23f;
        nextAction.setFont(nextAction.getFont().deriveFont(Font.BOLD, actionSize));
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
            if (state.modelBaselineAvailable) {
                requestedModel.setSelectedItem(state.requestedEngagementModel);
                requestedModel.setEnabled(true);
            } else {
                requestedModel.setEnabled(false);
            }
            if (state.baselineAvailable) {
                requestedDeltaWindow.setValue(Double.valueOf(state.requestedDeltaWindowMs));
                requestedDeltaWindow.setEnabled(true);
            } else {
                requestedDeltaWindow.setEnabled(false);
            }
            if (state.sampleLengthBaselineAvailable) {
                requestedSampleLength.setValue(
                        Double.valueOf(state.requestedSampleLengthSeconds));
                requestedSampleLength.setEnabled(true);
            } else {
                requestedSampleLength.setEnabled(false);
            }
            if (state.fastCallbackBaselineAvailable) {
                requestedFastCallback.setSelected(state.requestedFastCallback);
                requestedFastCallback.setEnabled(true);
            } else {
                requestedFastCallback.setSelected(false);
                requestedFastCallback.setEnabled(false);
            }

            if (state.modelBaselineAvailable || state.baselineAvailable
                    || state.sampleLengthBaselineAvailable
                    || state.fastCallbackBaselineAvailable) {
                current.setText("Working tune: " + state.engagementModel
                        + " | Delta Window " + format(state.baselineDeltaWindowMs) + " ms"
                        + " | Sample Length " + format(state.baselineSampleLengthSeconds) + " s"
                        + " | Fast callback " + (state.baselineFastCallback ? "ON" : "OFF")
                        + pendingText(state));
            } else {
                current.setText("Working tune does not yet expose recognized Detector Model / Timing baselines. Use Read Working Tune.");
            }
        } finally {
            updating = false;
        }
    }

    private void clearLiveBars() {
        liveTps.setText("TPS: n/a");
        liveRpm.setText("RPM: n/a");
        liveDelta.setText("Fuel: TPS AE change: n/a");
        liveWindow.setText("Window / stride: n/a");
        for (JProgressBar bar : new JProgressBar[]{
                selectedSignal, legacySignal, timedSignal, spanSignal,
                floorSignal, newestSignal}) {
            bar.setValue(0);
            bar.setString("n/a");
        }
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

    private static void prepareModelBar(JProgressBar bar, String name) {
        bar.setBorder(BorderFactory.createTitledBorder(name));
        bar.setPreferredSize(new Dimension(420, 48));
    }

    private static void updateRatioBar(JProgressBar bar,
                                       double value,
                                       double threshold,
                                       String prefix) {
        if (!Double.isFinite(value) || !Double.isFinite(threshold)
                || threshold <= 0.000001) {
            bar.setValue(0);
            bar.setString(prefix + "n/a");
            return;
        }
        double ratio = value / threshold;
        int percent = (int) Math.round(ratio * 100.0);
        bar.setValue(Math.max(0, Math.min(200, percent)));
        bar.setString(prefix + fmt(value) + " / " + fmt(threshold)
                + " = " + percent + "%");
    }

    private static JPanel wrap(String title, JTextArea area) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        JScrollPane scroll = new JScrollPane(area);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private static JTextArea text(String value, int rows, float size, int style) {
        JTextArea area = new JTextArea(value, rows, 1);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFocusable(false);
        area.setFont(area.getFont().deriveFont(style, size));
        area.setMargin(new java.awt.Insets(7, 8, 7, 8));
        return area;
    }

    private static String pendingText(EngagementDetectionWriteSelection.Snapshot state) {
        if (!state.hasRequestedChange()) return " | no test change selected";
        StringBuilder text = new StringBuilder(" | TEST CHANGE PENDING: ");
        boolean wrote = false;
        if (state.hasRequestedModelChange()) {
            text.append("Model ").append(state.baselineEngagementModel.displayName())
                    .append(" -> ").append(state.requestedEngagementModel.displayName());
            wrote = true;
        }
        if (state.hasRequestedDeltaWindowChange()) {
            if (wrote) text.append("; ");
            text.append("Delta Window ").append(format(state.baselineDeltaWindowMs))
                    .append(" -> ").append(format(state.requestedDeltaWindowMs)).append(" ms");
            wrote = true;
        }
        if (state.hasRequestedSampleLengthChange()) {
            if (wrote) text.append("; ");
            text.append("Sample Length ").append(format(state.baselineSampleLengthSeconds))
                    .append(" -> ").append(format(state.requestedSampleLengthSeconds)).append(" s");
            wrote = true;
        }
        if (state.hasRequestedFastCallbackChange()) {
            if (wrote) text.append("; ");
            text.append("Fast Callback ")
                    .append(state.baselineFastCallback ? "ON" : "OFF")
                    .append(" -> ")
                    .append(state.requestedFastCallback ? "ON" : "OFF");
        }
        return text.toString();
    }

    private static String modelName(EngagementFocusModel model) {
        return model == null || model.workingModel == null
                ? "detector" : model.workingModel.displayName();
    }

    private static String defaultManeuverPlan() {
        return "BASELINE / A-B REPEAT SET\n"
                + "Normal opening -> quick stab/hold -> partial lift/reapply -> stacked short stabs.\n"
                + "Finish/Review before changing anything. Then change ONE setting, Apply/verify, Read Working Tune and repeat the same set.";
    }

    private static String defaultAudioPlan() {
        return "READY = make one opening when safe. TARGET = selected detector crossed AccelThreshold. RETURN = detector cleared below threshold. Audio guides the driver; recorded channels remain the evidence.";
    }

    void setRequestedEngagementModelForTest(EngagementModelOption value) {
        requestedModel.setSelectedItem(value);
    }
    boolean requestedEngagementModelEnabledForTest() { return requestedModel.isEnabled(); }
    EngagementModelOption requestedEngagementModelForTest() {
        return (EngagementModelOption) requestedModel.getSelectedItem();
    }
    void setRequestedDeltaWindowForTest(double value) {
        requestedDeltaWindow.setValue(Double.valueOf(value));
    }
    boolean requestedDeltaWindowEnabledForTest() { return requestedDeltaWindow.isEnabled(); }
    double requestedDeltaWindowForTest() {
        return ((Number) requestedDeltaWindow.getValue()).doubleValue();
    }
    void setRequestedSampleLengthForTest(double value) {
        requestedSampleLength.setValue(Double.valueOf(value));
    }
    boolean requestedSampleLengthEnabledForTest() { return requestedSampleLength.isEnabled(); }
    double requestedSampleLengthForTest() {
        return ((Number) requestedSampleLength.getValue()).doubleValue();
    }
    void setRequestedFastCallbackForTest(boolean value) {
        requestedFastCallback.setSelected(value);
        EngagementDetectionWriteSelection.requestFastCallback(value);
        refreshFromSelection();
    }
    boolean requestedFastCallbackEnabledForTest() { return requestedFastCallback.isEnabled(); }
    boolean requestedFastCallbackForTest() { return requestedFastCallback.isSelected(); }
    String currentTextForTest() { return current.getText(); }
    String guidanceTextForTest() {
        return nextAction.getText() + "\n" + maneuverPlan.getText()
                + "\n" + audioPlan.getText();
    }
    boolean settingsPanelVisibleForTest() { return settingsPanel.isVisible(); }
    boolean settingsToggleVisibleForTest() { return settingsToggle.isVisible(); }
    String detectorStateForTest() { return detectorState.getText(); }
    String selectedSignalTextForTest() { return selectedSignal.getString(); }
    int selectedSignalPercentForTest() { return selectedSignal.getValue(); }
    String eventProgressTextForTest() { return eventProgress.getString(); }

    private static String format(double value) {
        if (!Double.isFinite(value)) return "unknown";
        return String.format(java.util.Locale.ROOT, "%.3f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    private static String fmt(double value) {
        return Double.isFinite(value) ? format(value) : "n/a";
    }
}
