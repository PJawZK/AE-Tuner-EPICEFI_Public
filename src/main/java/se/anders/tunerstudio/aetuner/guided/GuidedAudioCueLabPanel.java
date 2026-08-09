package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Stationary, test-only generated-tone editor and workflow demo. */
public final class GuidedAudioCueLabPanel extends JPanel {
    private final GuidedAudioCueController controller;
    private final EnumMap<GuidedAudioCueController.Cue, CueRow> rows =
            new EnumMap<GuidedAudioCueController.Cue, CueRow>(
                    GuidedAudioCueController.Cue.class);
    private final JLabel demoHeadline = new JLabel(
            "Choose a cue or start a stationary demonstration.");
    private final JLabel demoTrigger = new JLabel(
            "No ECU value is read or written by the Audio Cue Lab.");
    private final JLabel status = new JLabel();
    private final Timer refreshTimer;
    private final Timer demoTimer;
    private final List<GuidedAudioCueController.Cue> demoSequence =
            new ArrayList<GuidedAudioCueController.Cue>();
    private int demoIndex;

    public GuidedAudioCueLabPanel(GuidedAudioCueController controller) {
        super(new BorderLayout(8, 8));
        this.controller = controller;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        buildUi();
        demoTimer = new Timer(1900, event -> playNextDemoStep());
        demoTimer.setRepeats(true);
        refreshTimer = new Timer(300, event -> refreshState());
        refreshState();
    }

    private void buildUi() {
        JPanel intro = new JPanel(new BorderLayout(4, 4));
        JLabel warning = new JLabel(
                "AUDIO CUE LAB — STATIONARY TESTING ONLY — session-local generated tones; no ECU interaction");
        warning.setFont(warning.getFont().deriveFont(Font.BOLD));
        intro.add(warning, BorderLayout.NORTH);
        demoHeadline.setFont(demoHeadline.getFont().deriveFont(Font.BOLD, 18f));
        intro.add(demoHeadline, BorderLayout.CENTER);
        intro.add(demoTrigger, BorderLayout.SOUTH);
        add(intro, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridBagLayout());
        GridBagConstraints c = baseConstraints();
        String[] headings = {"Event", "On", "Pattern", "Start Hz",
                "End Hz", "ms", "Repeats", "Gap ms", "Volume %", "Preview"};
        for (int i = 0; i < headings.length; i++) {
            c.gridx = i;
            c.gridy = 0;
            JLabel label = new JLabel(headings[i], SwingConstants.CENTER);
            label.setFont(label.getFont().deriveFont(Font.BOLD));
            grid.add(label, c);
        }

        int row = 1;
        for (GuidedAudioCueController.Cue cue
                : GuidedAudioCueController.Cue.values()) {
            CueRow cueRow = new CueRow(cue);
            rows.put(cue, cueRow);
            cueRow.addTo(grid, row++);
        }

        JScrollPane scroll = new JScrollPane(grid);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setBorder(BorderFactory.createTitledBorder(
                "Pending audio profile — frozen when a Guided Session starts"));
        add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton testAll = new JButton("Test all cues");
        JButton success = new JButton("Demo successful event sequence");
        JButton failure = new JButton("Demo excluded / return sequence");
        JButton stop = new JButton("Stop audio / demo");
        JButton restore = new JButton("Restore candidate audio defaults");
        testAll.addActionListener(event -> startDemo(allCues()));
        success.addActionListener(event -> startDemo(successSequence()));
        failure.addActionListener(event -> startDemo(failureSequence()));
        stop.addActionListener(event -> stopDemo());
        restore.addActionListener(event -> {
            if (controller.restoreDefaults()) {
                refreshRows();
            }
        });
        buttons.add(testAll);
        buttons.add(success);
        buttons.add(failure);
        buttons.add(stop);
        buttons.add(restore);
        buttons.add(status);
        add(buttons, BorderLayout.SOUTH);
    }

    private static GridBagConstraints baseConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0.0;
        return c;
    }

    private List<GuidedAudioCueController.Cue> allCues() {
        List<GuidedAudioCueController.Cue> values =
                new ArrayList<GuidedAudioCueController.Cue>();
        for (GuidedAudioCueController.Cue cue
                : GuidedAudioCueController.Cue.values()) {
            values.add(cue);
        }
        return values;
    }

    private List<GuidedAudioCueController.Cue> successSequence() {
        List<GuidedAudioCueController.Cue> values =
                new ArrayList<GuidedAudioCueController.Cue>();
        values.add(GuidedAudioCueController.Cue.SESSION_STARTED);
        values.add(GuidedAudioCueController.Cue.READY);
        values.add(GuidedAudioCueController.Cue.OPENING_PENDING);
        values.add(GuidedAudioCueController.Cue.TARGET_ACQUIRED);
        values.add(GuidedAudioCueController.Cue.ACCEPTED);
        values.add(GuidedAudioCueController.Cue.COMPLETE);
        return values;
    }

    private List<GuidedAudioCueController.Cue> failureSequence() {
        List<GuidedAudioCueController.Cue> values =
                new ArrayList<GuidedAudioCueController.Cue>();
        values.add(GuidedAudioCueController.Cue.READY);
        values.add(GuidedAudioCueController.Cue.OPENING_PENDING);
        values.add(GuidedAudioCueController.Cue.EXCLUDED);
        values.add(GuidedAudioCueController.Cue.RETURN_TO_BASELINE);
        return values;
    }

    private void startDemo(List<GuidedAudioCueController.Cue> sequence) {
        if (controller.isSessionActive()) {
            demoHeadline.setText("DEMO BLOCKED — finish or reset the Guided Session first");
            demoTrigger.setText("Audio settings and demonstrations are stationary-only.");
            return;
        }
        stopDemo();
        demoSequence.addAll(sequence);
        demoIndex = 0;
        playNextDemoStep();
        if (!demoSequence.isEmpty()) {
            demoTimer.start();
        }
    }

    private void playNextDemoStep() {
        if (demoIndex >= demoSequence.size()) {
            demoTimer.stop();
            demoHeadline.setText("Audio demonstration complete");
            demoTrigger.setText("Adjust any cue and preview it again before driving.");
            return;
        }
        GuidedAudioCueController.Cue cue = demoSequence.get(demoIndex++);
        showCue(cue);
        controller.previewCue(cue);
    }

    private void showCue(GuidedAudioCueController.Cue cue) {
        demoHeadline.setText(cue.label.toUpperCase(java.util.Locale.US));
        demoTrigger.setText("Triggers when: " + cue.triggerDescription);
    }

    private void stopDemo() {
        demoTimer.stop();
        demoSequence.clear();
        demoIndex = 0;
        controller.stopNow();
    }

    private void refreshState() {
        boolean editable = !controller.isSessionActive();
        for (CueRow row : rows.values()) {
            row.setEditingEnabled(editable);
        }
        status.setText(controller.statusText()
                + (editable ? "" : " — profile locked for active Guided Session"));
    }

    private void refreshRows() {
        for (CueRow row : rows.values()) {
            row.load(controller.pendingSetting(row.cue));
        }
    }

    public void resumePanel() {
        if (!refreshTimer.isRunning()) refreshTimer.start();
        refreshState();
    }

    public void disposePanel() {
        demoTimer.stop();
        refreshTimer.stop();
        controller.stopNow();
    }

    public int cueRowCountForTest() {
        return rows.size();
    }

    String triggerDescriptionForTest(GuidedAudioCueController.Cue cue) {
        return cue.triggerDescription;
    }

    private final class CueRow {
        final GuidedAudioCueController.Cue cue;
        final JLabel event;
        final JCheckBox enabled = new JCheckBox();
        final JComboBox<GuidedAudioProfile.Pattern> pattern =
                new JComboBox<GuidedAudioProfile.Pattern>(
                        GuidedAudioProfile.Pattern.values());
        final JSpinner startHz = new JSpinner(
                new SpinnerNumberModel(800.0, 250.0, 2000.0, 25.0));
        final JSpinner endHz = new JSpinner(
                new SpinnerNumberModel(800.0, 250.0, 2000.0, 25.0));
        final JSpinner duration = new JSpinner(
                new SpinnerNumberModel(100, 50, 500, 10));
        final JSpinner repeats = new JSpinner(
                new SpinnerNumberModel(1, 1, 3, 1));
        final JSpinner gap = new JSpinner(
                new SpinnerNumberModel(70, 40, 300, 10));
        final JSpinner volume = new JSpinner(
                new SpinnerNumberModel(50, 5, 100, 5));
        final JButton preview = new JButton("Play");
        boolean loading;

        CueRow(GuidedAudioCueController.Cue cue) {
            this.cue = cue;
            this.event = new JLabel(cue.label);
            this.event.setToolTipText(cue.triggerDescription);
            load(controller.pendingSetting(cue));
            enabled.addActionListener(event -> apply());
            pattern.addActionListener(event -> apply());
            startHz.addChangeListener(event -> apply());
            endHz.addChangeListener(event -> apply());
            duration.addChangeListener(event -> apply());
            repeats.addChangeListener(event -> apply());
            gap.addChangeListener(event -> apply());
            volume.addChangeListener(event -> apply());
            preview.addActionListener(event -> {
                apply();
                showCue(cue);
                controller.previewCue(cue);
            });
        }

        void addTo(JPanel grid, int row) {
            Component[] components = {event, enabled, pattern, startHz,
                    endHz, duration, repeats, gap, volume, preview};
            for (int i = 0; i < components.length; i++) {
                GridBagConstraints c = baseConstraints();
                c.gridx = i;
                c.gridy = row;
                c.weightx = i == 0 || i == 2 ? 1.0 : 0.0;
                grid.add(components[i], c);
            }
        }

        void load(GuidedAudioProfile.Setting setting) {
            loading = true;
            enabled.setSelected(setting.enabled);
            pattern.setSelectedItem(setting.pattern);
            startHz.setValue(setting.startHz);
            endHz.setValue(setting.endHz);
            duration.setValue(setting.durationMs);
            repeats.setValue(setting.repeats);
            gap.setValue(setting.gapMs);
            volume.setValue((int) Math.round(setting.volume * 100.0));
            loading = false;
        }

        void apply() {
            if (loading) return;
            GuidedAudioProfile.Setting setting =
                    new GuidedAudioProfile.Setting(
                            enabled.isSelected(),
                            (GuidedAudioProfile.Pattern) pattern.getSelectedItem(),
                            ((Number) startHz.getValue()).doubleValue(),
                            ((Number) endHz.getValue()).doubleValue(),
                            ((Number) duration.getValue()).intValue(),
                            ((Number) repeats.getValue()).intValue(),
                            ((Number) gap.getValue()).intValue(),
                            ((Number) volume.getValue()).doubleValue() / 100.0);
            if (!controller.updatePendingSetting(cue, setting)) {
                load(controller.pendingSetting(cue));
            }
        }

        void setEditingEnabled(boolean value) {
            enabled.setEnabled(value);
            pattern.setEnabled(value);
            startHz.setEnabled(value);
            endHz.setEnabled(value);
            duration.setEnabled(value);
            repeats.setEnabled(value);
            gap.setEnabled(value);
            volume.setEnabled(value);
            preview.setEnabled(value);
        }
    }
}
