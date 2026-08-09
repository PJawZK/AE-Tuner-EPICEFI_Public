package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import com.efiAnalytics.plugin.ecu.ControllerException;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.DefaultCaret;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Additive, read-only Guided Capture presentation.
 *
 * Samples arrive from the passive AE Tuner callback through an instance-owned
 * bounded GuidedSampleDispatcher; this panel never opens its own output-channel
 * subscription. Project axes are read separately through ControllerAccess.
 */
public final class GuidedCapturePanel extends JPanel {
    private static final long STARTUP_IGNORE_NS = 3000000000L;

    private final BlendDurationGuidedSession session = new BlendDurationGuidedSession();
    private final GuidedEvidenceRecorder evidence = new GuidedEvidenceRecorder();
    private final GuidedBlendProposal.Tracker proposalTracker =
            new GuidedBlendProposal.Tracker();
    private final JLabel connection =
            new JLabel("Guided capture: sample worker not connected");
    private final JLabel rate = new JLabel("Sample rate: n/a");
    private final GuidedTargetGauge liveTps =
            new GuidedTargetGauge(GuidedTargetGauge.Mode.TPS);
    private final GuidedTargetGauge liveRpm =
            new GuidedTargetGauge(GuidedTargetGauge.Mode.RPM);
    private final JTextArea headline =
            text("IDLE\nRead project axes and start a guided session.", 5, 24f, Font.BOLD);
    private final JTextArea checks =
            text("Baseline checks will appear here.", 13, 13f, Font.PLAIN);
    private final JScrollPane checksScroll = scroll(checks);
    private final JTextArea result =
            text("Passive capture and exports remain unchanged.", 8, 13f, Font.PLAIN);
    private final JTextArea proposal =
            text("Read the current project Blend Duration curve.", 8, 13f, Font.PLAIN);
    private final JSpinner startRpm =
            new JSpinner(new SpinnerNumberModel(2000, 600, 6500, 50));
    private final JSpinner heldTps =
            new JSpinner(new SpinnerNumberModel(20.0, 10.0, 40.0, 1.0));
    private final JSpinner targetCount =
            new JSpinner(new SpinnerNumberModel(5, 3, 20, 1));
    private final JComboBox<GuidedBlendProposal.PointChoice> tablePoint =
            new JComboBox<GuidedBlendProposal.PointChoice>();
    private final JComboBox<String> gear = new JComboBox<String>(new String[]{
            "Ignore gear", "Manual 1st", "Manual 2nd", "Manual 3rd",
            "Manual 4th", "Manual 5th", "Automatic advisory"
    });
    private final JButton start = new JButton("Start Guided Session");
    private final JButton pause = new JButton("Pause");
    private final JButton discard = new JButton("Discard Last Guided Event");
    private final JButton finish = new JButton("Finish and Review");
    private final JButton reset = new JButton("Reset Guided Session");
    private final JButton saveReport = new JButton("Save Guided Report");
    private final JButton saveCsv = new JButton("Save Guided CSV");
    private final JButton copyProposal = new JButton("Copy Guided Blend Proposal");
    private final JButton readProject = new JButton("Read Project Blend Axis");
    private final JButton reconnect = new JButton("Reconnect Guided worker");
    private final Timer refreshTimer;

    private final GuidedSampleDispatcher sampleDispatcher =
            new GuidedSampleDispatcher(new GuidedSampleDispatcher.Listener() {
                @Override
                public GuidedCaptureState onGuidedSample(LiveSample sample) {
                    return acceptDispatchedSample(sample);
                }
            });
    private ControllerAccess controllerAccess;
    private AeProjectSnapshot projectSnapshot;
    private long rateWindowNano;
    private long armedNano;
    private int samples;
    private volatile double sampleRate;
    private volatile boolean enabled;
    private volatile LiveSample latestSharedSample;
    private Runnable pauseAudioAction = new Runnable() {
        @Override
        public void run() { }
    };
    private Supplier<String> audioAuditText = new Supplier<String>() {
        @Override
        public String get() { return "No audio audit connected."; }
    };
    private Supplier<String> audioAuditCsv = new Supplier<String>() {
        @Override
        public String get() { return "audio_sequence,audio_timestamp,audio_stage,audio_cue,audio_detail\n"; }
    };
    private Runnable recoveryDirtyAction = new Runnable() {
        @Override
        public void run() { }
    };

    public GuidedCapturePanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        buildUi();
        installActions();
        refreshTimer = new Timer(150, event -> refresh());
        // Lifecycle resources start in initialize()/resumePanel(), not construction.
    }

    private static JTextArea text(String value, int rows, float size, int style) {
        JTextArea area = new JTextArea(value, rows, 1);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.SANS_SERIF, style, Math.round(size)));
        area.setMargin(new Insets(8, 10, 8, 10));
        area.setFocusable(false);
        ((DefaultCaret) area.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        return area;
    }

    private static JScrollPane scroll(JTextArea area) {
        JScrollPane scroll = new JScrollPane(area);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setBlockIncrement(80);
        return scroll;
    }

    private void buildUi() {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.add(connection, BorderLayout.CENTER);
        header.add(rate, BorderLayout.EAST);

        JPanel liveValues = new JPanel(new GridLayout(1, 2, 10, 0));
        liveValues.setBorder(BorderFactory.createTitledBorder("Live driver targets"));
        liveValues.add(liveTps);
        liveValues.add(liveRpm);

        JPanel setup = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        setup.setBorder(BorderFactory.createTitledBorder(
                "Adaptive Blend Duration guided setup — actual project axis"));
        setup.add(new JLabel("Table point"));
        tablePoint.setPreferredSize(new Dimension(
                330, tablePoint.getPreferredSize().height));
        setup.add(tablePoint);
        setup.add(new JLabel("Start RPM"));
        setup.add(startRpm);
        setup.add(new JLabel("Desired TPS step"));
        setup.add(heldTps);
        setup.add(new JLabel("Comparable events"));
        setup.add(targetCount);
        setup.add(new JLabel("Gear"));
        setup.add(gear);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.add(start);
        controls.add(pause);
        controls.add(discard);
        controls.add(finish);
        controls.add(reset);
        controls.add(readProject);
        controls.add(saveReport);
        controls.add(saveCsv);
        controls.add(copyProposal);
        controls.add(reconnect);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.add(header);
        north.add(liveValues);
        north.add(setup);
        north.add(controls);
        add(north, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 2, 8, 8));
        center.add(wrap("Prominent instruction", headline));
        center.add(wrap("Live baseline and capture checks", checksScroll));
        add(center, BorderLayout.CENTER);

        JPanel south = new JPanel(new GridLayout(1, 2, 8, 8));
        south.add(wrap("Latest guided result and series quality", result));
        south.add(wrap("Selected table point and read-only proposal", proposal));
        south.setPreferredSize(new Dimension(900, 250));
        add(south, BorderLayout.SOUTH);
    }

    private static JPanel wrap(String title, JTextArea area) {
        return wrap(title, scroll(area));
    }

    private static JPanel wrap(String title, JScrollPane scroll) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private void installActions() {
        start.addActionListener(event -> startSession());
        pause.addActionListener(event -> {
            session.togglePause();
            if (session.snapshot().state == GuidedCaptureState.PAUSED) {
                pauseAudioAction.run();
            }
            refresh();
        });
        discard.addActionListener(event -> {
            session.discardLast();
            evidence.discardLastAccepted();
            proposalTracker.discardLastAccepted();
            refresh();
        });
        finish.addActionListener(event -> {
            session.finish();
            evidence.finish(session.snapshot());
            refresh();
        });
        reset.addActionListener(event -> {
            session.reset();
            evidence.reset();
            proposalTracker.reset();
            pauseAudioAction.run();
            refresh();
        });
        readProject.addActionListener(event -> readProjectAxes());
        saveReport.addActionListener(event -> saveEvidence(false));
        saveCsv.addActionListener(event -> saveEvidence(true));
        copyProposal.addActionListener(event -> copyGuidedProposal());
        reconnect.addActionListener(event -> connectSampleDispatcher());
        tablePoint.addActionListener(event -> refresh());
        heldTps.addChangeListener(event -> refreshLiveValues());
        startRpm.addChangeListener(event -> refreshLiveValues());
    }

    public void setRecoveryDirtyAction(Runnable action) {
        recoveryDirtyAction = action == null ? new Runnable() {
            @Override
            public void run() { }
        } : action;
    }

    public void setWorkflowEventListener(GuidedWorkflowEvent.Listener listener) {
        session.setWorkflowEventListener(listener);
    }

    public void setAudioAuditSuppliers(Supplier<String> textSupplier,
                                Supplier<String> csvSupplier) {
        audioAuditText = textSupplier == null ? audioAuditText : textSupplier;
        audioAuditCsv = csvSupplier == null ? audioAuditCsv : csvSupplier;
    }

    public void setPauseAudioAction(Runnable action) {
        pauseAudioAction = action == null ? new Runnable() {
            @Override
            public void run() { }
        } : action;
    }

    private void startSession() {
        GuidedBlendProposal.PointChoice point = selectedPoint();
        if (projectSnapshot == null || point == null) {
            connection.setText(
                    "Guided capture: read the current project Blend axis first");
            return;
        }

        double configuredRpm = ((Number) startRpm.getValue()).doubleValue();
        if (!point.contains(configuredRpm)) {
            headline.setText("SETUP REQUIRED\n\n"
                    + point.startGuidance(configuredRpm));
            connection.setText("Guided capture not started: start RPM is outside "
                    + point.regionText());
            return;
        }

        int selected = gear.getSelectedIndex();
        int manualGear = selected >= 1 && selected <= 5 ? selected : 0;
        boolean automatic = selected == 6;
        double configuredStep = ((Number) heldTps.getValue()).doubleValue();
        int configuredCount = ((Number) targetCount.getValue()).intValue();
        String gearMode = selected >= 0
                ? String.valueOf(gear.getItemAt(selected)) : "unknown";

        evidence.startAdaptive(configuredRpm, configuredStep, configuredCount,
                gearMode + " | table point " + Math.round(point.rpm) + " RPM",
                System.nanoTime());
        proposalTracker.start(projectSnapshot, point.index);
        session.start(new BlendDurationCaptureConfig(
                configuredRpm, configuredStep, configuredCount,
                manualGear, automatic));
        recoveryDirtyAction.run();
        refresh();
    }

    public void connectController(ControllerAccess access) {
        controllerAccess = access;
        readProjectAxes();
        resumePanel();
    }

    private void readProjectAxes() {
        if (controllerAccess == null) {
            connection.setText(
                    "Guided project axis unavailable: controller not connected");
            return;
        }
        GuidedSessionSnapshot guided = session.snapshot();
        if (guided.state != GuidedCaptureState.IDLE
                && guided.state != GuidedCaptureState.COMPLETE) {
            connection.setText(
                    "Finish or reset the guided session before re-reading axes");
            return;
        }
        try {
            AeProjectSnapshot next =
                    new AeControllerBridge(controllerAccess).readSnapshot();
            projectSnapshot = next;
            List<GuidedBlendProposal.PointChoice> points =
                    GuidedBlendProposal.points(next);
            tablePoint.removeAllItems();
            for (GuidedBlendProposal.PointChoice point : points) {
                tablePoint.addItem(point);
            }
            selectNearestPoint();
            proposalTracker.reset();
            if (points.isEmpty()) {
                connection.setText("Guided project axis unavailable: "
                        + "Predictive Map Blend Duration curve not found");
            } else {
                connection.setText("Guided project axis loaded: "
                        + points.size() + " actual Blend Duration point(s)");
            }
        } catch (ControllerException ex) {
            projectSnapshot = null;
            tablePoint.removeAllItems();
            proposalTracker.reset();
            connection.setText(
                    "Guided project axis read failed: " + ex.getMessage());
        }
        refresh();
    }

    private void selectNearestPoint() {
        if (tablePoint.getItemCount() == 0) {
            return;
        }
        double rpm = ((Number) startRpm.getValue()).doubleValue();
        int best = 0;
        double distance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < tablePoint.getItemCount(); i++) {
            GuidedBlendProposal.PointChoice point = tablePoint.getItemAt(i);
            double next = Math.abs(point.rpm - rpm);
            if (next < distance) {
                distance = next;
                best = i;
            }
        }
        tablePoint.setSelectedIndex(best);
    }

    private GuidedBlendProposal.PointChoice selectedPoint() {
        Object selected = tablePoint.getSelectedItem();
        return selected instanceof GuidedBlendProposal.PointChoice
                ? (GuidedBlendProposal.PointChoice) selected : null;
    }

    private synchronized void connectSampleDispatcher() {
        sampleDispatcher.suspend();
        rateWindowNano = 0L;
        samples = 0;
        sampleRate = 0.0;
        latestSharedSample = null;
        armedNano = System.nanoTime() + STARTUP_IGNORE_NS;
        enabled = true;
        sampleDispatcher.resume();
        connection.setText(projectSnapshot == null
                ? "Guided capture: sample worker connected; project axis not loaded"
                : "Guided capture: sample worker and project axis ready");
    }

    private synchronized void disconnectSampleDispatcher() {
        enabled = false;
        sampleDispatcher.suspend();
        latestSharedSample = null;
    }

    public void resumePanel() {
        if (!refreshTimer.isRunning()) {
            refreshTimer.start();
        }
        if (!isSampleDispatcherActiveForTest()) {
            connectSampleDispatcher();
        }
    }

    public void suspendPanel() {
        refreshTimer.stop();
        pauseAudioAction.run();
        disconnectSampleDispatcher();
        connection.setText("Guided capture: suspended — no live samples are being processed");
    }

    public void terminateForClose() {
        session.terminateForClose();
        evidence.finish(session.snapshot());
        recoveryDirtyAction.run();
    }

    public void releaseAfterClose() {
        sampleDispatcher.close();
        controllerAccess = null;
        projectSnapshot = null;
        proposalTracker.reset();
    }

    public void disposePanel() {
        suspendPanel();
        terminateForClose();
        releaseAfterClose();
    }

    private GuidedCaptureState acceptDispatchedSample(LiveSample sample) {
        if (!enabled || sample == null) {
            return session.snapshot().state;
        }
        latestSharedSample = sample;
        long now = System.nanoTime();
        synchronized (this) {
            samples++;
            if (rateWindowNano == 0L) {
                rateWindowNano = now;
            } else if (now - rateWindowNano >= 1000000000L) {
                sampleRate = samples / ((now - rateWindowNano) / 1000000000.0);
                samples = 0;
                rateWindowNano = now;
            }
        }
        if (enabled && now >= armedNano) {
            session.accept(sample);
            GuidedOutcome outcome = session.drainOutcome();
            GuidedSessionSnapshot snapshot = session.snapshot();
            if (outcome != null) {
                evidence.record(outcome, snapshot);
                proposalTracker.observe(outcome);
                if (snapshot.state == GuidedCaptureState.COMPLETE) {
                    evidence.finish(snapshot);
                }
                recoveryDirtyAction.run();
            }
        }
        return session.snapshot().state;
    }

    public EvidenceRecoverySnapshot.Guided recoverySnapshot() {
        int count = evidence.recordCount();
        if (count <= 0) {
            return null;
        }
        GuidedSessionSnapshot snapshot = session.snapshot();
        GuidedBlendProposal guidedProposal = proposalTracker.evaluate();
        String reportText = evidence.reportText(AeTunerPlugin.VERSION, snapshot)
                + "\n\nAudio cue audit\n"
                + "===============\n" + audioAuditText.get()
                + "\n\nControlled selected-point proposal\n"
                + "==================================\n"
                + guidedProposal.getDisplayText() + "\n";
        String csvText = evidence.csvText(AeTunerPlugin.VERSION)
                + "\n# Audio cue audit\n" + audioAuditCsv.get();
        return new EvidenceRecoverySnapshot.Guided(
                evidence.sessionId(), count, reportText, csvText);
    }

    private void saveEvidence(boolean csv) {
        GuidedSessionSnapshot snapshot = session.snapshot();
        String timestamp = new SimpleDateFormat(
                "yyyyMMdd-HHmmss", Locale.US).format(new Date());
        String suffix = csv ? ".csv" : ".txt";
        String name = "ae-tuner-guided-blend-" + timestamp + suffix;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(csv
                ? "Save Guided Capture CSV" : "Save Guided Capture report");
        chooser.setSelectedFile(new File(name));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        GuidedBlendProposal guidedProposal = proposalTracker.evaluate();
        String content = csv
                ? evidence.csvText(AeTunerPlugin.VERSION)
                    + "\n# Audio cue audit\n" + audioAuditCsv.get()
                : evidence.reportText(AeTunerPlugin.VERSION, snapshot)
                    + "\n\nAudio cue audit\n"
                    + "===============\n" + audioAuditText.get()
                    + "\n\nControlled selected-point proposal\n"
                    + "==================================\n"
                    + guidedProposal.getDisplayText() + "\n";
        try {
            Files.write(chooser.getSelectedFile().toPath(),
                    content.getBytes(StandardCharsets.UTF_8));
            connection.setText("Guided evidence saved: "
                    + chooser.getSelectedFile().getAbsolutePath());
        } catch (IOException ex) {
            connection.setText(
                    "Guided evidence save failed: " + ex.getMessage());
        }
    }

    private void copyGuidedProposal() {
        GuidedBlendProposal guidedProposal = proposalTracker.evaluate();
        if (!guidedProposal.isAvailable()) {
            connection.setText(
                    "Guided proposal withheld: review the proposal panel");
            return;
        }
        String error = AdvisoryExportCoordinator.copyToClipboard(
                guidedProposal.getCopyPasteBlock());
        connection.setText(error == null
                ? "Guided full-curve proposal copied; no ECU value was written"
                : "Guided proposal copy failed: " + error);
    }

    private void refresh() {
        GuidedSessionSnapshot snapshot = session.snapshot();
        headline.setText(snapshot.headline + "\n\n" + snapshot.instruction);
        String decoratedChecks = GuidedChannelValidity.decorate(
                snapshot.checks, latestSharedSample);
        setStableText(checks, checksScroll, decoratedChecks);
        result.setText(snapshot.result
                + "\n\nGuided evidence outcomes recorded: "
                + evidence.recordCount()
                + "\nProposal duration samples: "
                + proposalTracker.durationCount());

        GuidedBlendProposal.PointChoice point = selectedPoint();
        GuidedBlendProposal guidedProposal = proposalTracker.evaluate();
        double configuredRpm = ((Number) startRpm.getValue()).doubleValue();
        String axisGuidance = point == null
                ? "No actual Blend Duration table point selected."
                : "Selected: " + point.toString() + "\n"
                    + point.startGuidance(configuredRpm);
        proposal.setText(axisGuidance + "\n\n"
                + guidedProposal.getDisplayText());

        refreshLiveValues();
        pause.setText(snapshot.state == GuidedCaptureState.PAUSED
                ? "Resume" : "Pause");
        pause.setEnabled(snapshot.state != GuidedCaptureState.IDLE
                && snapshot.state != GuidedCaptureState.COMPLETE);
        finish.setEnabled(snapshot.state != GuidedCaptureState.IDLE
                && snapshot.state != GuidedCaptureState.COMPLETE);
        discard.setEnabled(snapshot.accepted > 0);

        boolean setupEnabled = snapshot.state == GuidedCaptureState.IDLE
                || snapshot.state == GuidedCaptureState.COMPLETE;
        startRpm.setEnabled(setupEnabled);
        heldTps.setEnabled(setupEnabled);
        targetCount.setEnabled(setupEnabled);
        gear.setEnabled(setupEnabled);
        tablePoint.setEnabled(
                setupEnabled && tablePoint.getItemCount() > 0);
        readProject.setEnabled(setupEnabled && controllerAccess != null);
        start.setEnabled(setupEnabled && point != null
                && point.contains(configuredRpm));
        saveReport.setEnabled(evidence.recordCount() > 0);
        saveCsv.setEnabled(evidence.recordCount() > 0);
        copyProposal.setEnabled(guidedProposal.isAvailable());
        GuidedSampleDispatcher.Diagnostics dispatch = sampleDispatcher.diagnostics();
        String deliveredRate = sampleRate > 0.0
                ? "Guided delivered rate: " + Math.round(sampleRate) + " Hz"
                : "Guided delivered rate: n/a";
        rate.setText(deliveredRate + " | q " + dispatch.queueDepth + "/"
                + GuidedSampleDispatcher.CAPACITY + " | coalesced "
                + dispatch.coalesced + " | dropped " + dispatch.dropped);
    }

    private void refreshLiveValues() {
        LiveSample sample = latestSharedSample;
        double desiredStep = ((Number) heldTps.getValue()).doubleValue();
        double rpmTarget = ((Number) startRpm.getValue()).doubleValue();
        double tps = sample == null ? Double.NaN : sample.get(ChannelRole.TPS);
        double rpm = sample == null ? Double.NaN : sample.get(ChannelRole.RPM);
        liveTps.setTpsAdaptive(tps, session.baselineTpsForDisplay(), desiredStep);
        liveRpm.setRpmAdaptive(rpm, rpmTarget);
    }

    private static void setStableText(JTextArea area, JScrollPane scroll, String value) {
        String next = value == null ? "" : value;
        if (next.equals(area.getText())) {
            return;
        }
        final JScrollBar bar = scroll.getVerticalScrollBar();
        final int previous = bar.getValue();
        final boolean atBottom = previous + bar.getVisibleAmount()
                >= bar.getMaximum() - 2;
        area.setText(next);
        SwingUtilities.invokeLater(() -> {
            int maximum = Math.max(0,
                    bar.getMaximum() - bar.getVisibleAmount());
            bar.setValue(atBottom ? maximum : Math.min(previous, maximum));
        });
    }

    public GuidedSampleDispatcher sampleDispatcherForPassivePanel() {
        return sampleDispatcher;
    }

    boolean isSampleDispatcherActiveForTest() {
        return enabled && sampleDispatcher.diagnostics().accepting;
    }

    GuidedSampleDispatcher.Diagnostics sampleDispatcherDiagnosticsForTest() {
        return sampleDispatcher.diagnostics();
    }

    boolean hasProjectBlendAxisForTest() {
        return projectSnapshot != null && tablePoint.getItemCount() > 0;
    }

    void showLiveSampleForTest(LiveSample sample) {
        latestSharedSample = sample;
        refreshLiveValues();
    }

    String liveTpsTextForTest() {
        return liveTps.labelTextForTest();
    }

    String liveRpmTextForTest() {
        return liveRpm.labelTextForTest();
    }

    double liveTpsTargetForTest() {
        return liveTps.targetForTest();
    }

    double liveRpmTargetForTest() {
        return liveRpm.targetForTest();
    }

    double liveTpsBandLowForTest() {
        return liveTps.innerLowForTest();
    }

    double liveTpsBandHighForTest() {
        return liveTps.innerHighForTest();
    }

    double liveRpmAcquireLowForTest() {
        return liveRpm.innerLowForTest();
    }

    double liveRpmAcquireHighForTest() {
        return liveRpm.innerHighForTest();
    }

    double liveRpmRetainLowForTest() {
        return liveRpm.outerLowForTest();
    }

    double liveRpmRetainHighForTest() {
        return liveRpm.outerHighForTest();
    }

    int checksCaretPolicyForTest() {
        return ((DefaultCaret) checks.getCaret()).getUpdatePolicy();
    }
}
