package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.guided.method.*;
import se.anders.tunerstudio.aetuner.guided.mapestimate.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import com.efiAnalytics.plugin.ecu.ControllerException;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.text.DefaultCaret;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.List;
import java.util.function.Supplier;

/**
 * Shared task-driven Guided Tuning presentation shell.
 *
 * The panel owns common workflow/navigation/export/apply surfaces. Method-
 * specific capture rules and tuning evidence are routed through isolated
 * GuidedAeMethodModule implementations so one AE method can fail or evolve
 * without contaminating another method's capture or tuning math.
 *
 * Guarded working-tune Apply/Restore is common infrastructure for every method.
 * Capture never writes. A completed method may expose an exact ProposalWritePlan,
 * which is applied only through ProposalApplyCoordinator. Burn is not exposed.
 */
public final class GuidedCapturePanel extends JPanel {
    private static final long STARTUP_IGNORE_NS = 3000000000L;
    private static final String CARD_BLEND = "blend";
    private static final String CARD_PROBE = "probe";
    private static final String CARD_ARCH = "architecture";

    private final BlendDurationGuidedSession session = new BlendDurationGuidedSession();
    private final GuidedMethodProbeSession probeSession = new GuidedMethodProbeSession();
    private final GuidedEvidenceRecorder evidence = new GuidedEvidenceRecorder();
    private final GuidedBlendProposal.Tracker proposalTracker =
            new GuidedBlendProposal.Tracker();
    private final JLabel connection =
            new JLabel("Guided tuning: sample worker not connected");
    private final JLabel rate = new JLabel("Sample rate: n/a");
    private final JLabel workflowStage = new JLabel(
            "SETTING / EVIDENCE workflow will follow the selected task");
    private final JComboBox<GuidedTuningArea> tuningArea =
            new JComboBox<GuidedTuningArea>(GuidedTuningArea.values());
    private final JComboBox<GuidedTuningRecipe> tuningTask =
            new JComboBox<GuidedTuningRecipe>();
    private final JTextArea tuningPathGuidance =
            text("", 2, 12f, Font.PLAIN);
    private final GuidedTargetGauge liveTps =
            new GuidedTargetGauge(GuidedTargetGauge.Mode.TPS);
    private final GuidedTargetGauge liveRpm =
            new GuidedTargetGauge(GuidedTargetGauge.Mode.RPM);
    private final JPanel liveValuesPanel = new JPanel(new GridLayout(1, 2, 10, 0));
    private final JTextArea headline =
            text("SETUP\nChoose a tuning task, read the working tune, then start capture.",
                    4, 24f, Font.BOLD);
    private final JTextArea checks =
            text("Prerequisite and baseline checks will appear here.", 10, 13f, Font.PLAIN);
    private final JScrollPane checksScroll = scroll(checks);
    private final JTextArea result =
            text("Guided evidence is retained until reset or plugin close.", 7, 13f, Font.PLAIN);
    private final JTextArea proposal =
            text("No reviewed proposal is available yet.", 8, 13f, Font.PLAIN);

    private final JSpinner startRpm =
            new JSpinner(new SpinnerNumberModel(2000, 0, 10000, 1));
    private final JSpinner heldTps =
            new JSpinner(new SpinnerNumberModel(20.0, 10.0, 40.0, 1.0));
    private final JSpinner targetCount =
            new JSpinner(new SpinnerNumberModel(5, 3, 20, 1));
    private final JComboBox<GuidedBlendProposal.PointChoice> tablePoint =
            new JComboBox<GuidedBlendProposal.PointChoice>();
    private final JComboBox<String> gear = new JComboBox<String>(new String[]{
            "Ignore gear", "Manual 1st", "Manual 2nd", "Manual 3rd",
            "Manual 4th", "Manual 5th", "Automatic detected"
    });

    private final JSpinner probeTargetCount =
            new JSpinner(new SpinnerNumberModel(5, 1, 50, 1));
    private final JSpinner mapMinimumSamples =
            new JSpinner(new SpinnerNumberModel(20, 3, 500, 1));
    private final JSpinner mapCapKpa =
            new JSpinner(new SpinnerNumberModel(115.0, 90.0, 180.0, 1.0));
    private final JPanel probeEventInputs = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 4));
    private final JPanel mapEstimateInputs = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 4));

    private final CardLayout setupCardLayout = new CardLayout();
    private final JPanel setupCards = new JPanel(setupCardLayout);
    private final JTextArea probeSetupText = text("", 5, 12f, Font.PLAIN);
    private final JTextArea architectureSetupText = text("", 3, 12f, Font.PLAIN);
    private final JButton start = new JButton("Start Capture");
    private final JButton pause = new JButton("Pause");
    private final JButton discard = new JButton("Discard Last Event");
    private final JButton finish = new JButton("Finish and Review");
    private final JButton reset = new JButton("Reset Session");
    private final JButton saveReport = new JButton("Export Evidence");
    private final JButton copyReviewedDraft = new JButton("Copy Reviewed Draft");
    private final JButton applyProposal = new JButton("Apply Current Proposal");
    private final JButton restoreProposal = new JButton("Restore Previous Apply");
    private final JButton readProject = new JButton("Read Working Tune");
    private final JButton reconnect = new JButton("Reconnect");
    private final Timer refreshTimer;

    private final GuidedSampleDispatcher sampleDispatcher =
            new GuidedSampleDispatcher(new GuidedSampleDispatcher.Listener() {
                @Override
                public GuidedCaptureState onGuidedSample(LiveSample sample) {
                    return acceptDispatchedSample(sample);
                }
            });
    private ControllerAccess controllerAccess;
    private ProposalApplyCoordinator applyCoordinator;
    private AeProjectSnapshot projectSnapshot;
    private long rateWindowNano;
    private long armedNano;
    private int samples;
    private volatile double sampleRate;
    private volatile boolean enabled;
    private volatile boolean exportInProgress;
    private volatile LiveSample latestSharedSample;
    private volatile GuidedTuningRecipe activeRecipe = GuidedTuningRecipe.ENGAGEMENT_DETECTION;
    private boolean updatingTuningNavigation;
    private volatile boolean probeEvidenceExported = true;
    private volatile String applyStatus =
            "No proposal has been applied during this plugin session.";
    private volatile String lastApplyManifestJson = "";
    private volatile boolean currentProposalApplied;
    private volatile boolean workingTuneReadRequiredAfterApply;
    private volatile String appliedProposalSummary = "";
    private Runnable pauseAudioAction = new Runnable() {
        @Override
        public void run() { }
    };
    private Supplier<String> audioAuditText = new Supplier<String>() {
        @Override public String get() { return "No audio audit connected."; }
    };
    private Supplier<String> audioAuditCsv = new Supplier<String>() {
        @Override public String get() { return "audio_sequence,audio_timestamp,audio_stage,audio_cue,audio_detail\n"; }
    };
    private Runnable recoveryDirtyAction = new Runnable() {
        @Override public void run() { }
    };

    public GuidedCapturePanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        saveReport.setToolTipText(
                "Export retained evidence. When browsing another method, this button exports the previous unexported method first so switching can continue safely.");
        copyReviewedDraft.setToolTipText(
                "Copy a reviewed paste-ready draft when the selected method produces one. Clipboard copy never writes the working tune; Apply is a separate explicit action. No burn.");
        applyProposal.setToolTipText(
                "Explicitly apply only the exact values declared by the selected method's reviewed ProposalWritePlan. Any Guided method may expose a plan when its tuning logic supports a change. No burn.");
        restoreProposal.setToolTipText(
                "Restore the most recent AE Tuner apply only when the working tune still matches that applied state. No mutation is permitted during active capture. No burn.");
        tuningArea.setToolTipText(
                "Start with AE Foundation, then choose only the AE strategy/strategies actually used by this tune. Areas after Foundation are not mandatory stages.");
        tuningTask.setToolTipText(
                "Choose a task inside the selected tuning area. MAP Predict tasks have a local dependency order; other AE areas are independent/combinable strategies.");
        startRpm.setToolTipText(
                "Blend Duration capture RPM is locked to the selected actual table bin; choose another Table point to change it.");
        gear.setToolTipText(
                "Manual gear is authoritative operator metadata; ECU detected gear is informational only. Automatic detected latches stable session evidence and records sustained event-local mismatches separately.");
        probeTargetCount.setToolTipText(
                "Target number of distinct method-activity events to accumulate before review. You may continue capture afterward.");
        mapMinimumSamples.setToolTipText(
                "Minimum stable samples required in a MAP Estimate TPS/RPM cell before that cell may be considered for the draft.");
        mapCapKpa.setToolTipText(
                "Safety cap applied only to observed eligible MAP Estimate cells at high TPS. Unvisited cells are never rewritten just because they exceed the cap.");
        buildUi();
        installActions();
        initializeTuningNavigation();
        refreshTimer = new Timer(150, event -> refresh());
        refresh();
        publishSelectedGuidedFocus();
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

        JPanel task = new JPanel(new BorderLayout(12, 2));
        task.setBorder(BorderFactory.createTitledBorder("Guided Tuning workflow"));
        JPanel taskChoice = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        taskChoice.add(new JLabel("Tuning area"));
        tuningArea.setPreferredSize(new Dimension(
                230, tuningArea.getPreferredSize().height));
        taskChoice.add(tuningArea);
        taskChoice.add(new JLabel("Task"));
        tuningTask.setPreferredSize(new Dimension(
                430, tuningTask.getPreferredSize().height));
        taskChoice.add(tuningTask);
        task.add(taskChoice, BorderLayout.WEST);
        task.add(workflowStage, BorderLayout.CENTER);
        tuningPathGuidance.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        task.add(tuningPathGuidance, BorderLayout.SOUTH);

        liveValuesPanel.setBorder(BorderFactory.createTitledBorder("Live Blend Duration driver targets"));
        liveValuesPanel.add(liveTps);
        liveValuesPanel.add(liveRpm);

        setupCards.setBorder(BorderFactory.createTitledBorder("Selected method setup"));
        setupCards.add(buildBlendSetup(), CARD_BLEND);
        setupCards.add(buildProbeSetup(), CARD_PROBE);
        JPanel architecture = new JPanel(new BorderLayout());
        architecture.add(architectureSetupText, BorderLayout.CENTER);
        setupCards.add(architecture, CARD_ARCH);

        JPanel controls = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 4));
        controls.add(readProject);
        controls.add(start);
        controls.add(pause);
        controls.add(finish);
        controls.add(discard);
        controls.add(saveReport);
        controls.add(copyReviewedDraft);
        controls.add(reset);
        controls.add(reconnect);
        controls.add(applyProposal);
        controls.add(restoreProposal);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.add(header);
        north.add(task);
        north.add(liveValuesPanel);
        north.add(setupCards);
        north.add(controls);
        add(north, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 2, 8, 8));
        center.add(wrap("What to do now", headline));
        center.add(wrap("Required data / live readiness", checksScroll));
        add(center, BorderLayout.CENTER);

        JPanel south = new JPanel(new GridLayout(1, 2, 8, 8));
        south.add(wrap("Accumulated evidence / quality", result));
        south.add(wrap("Review / generated output", proposal));
        south.setPreferredSize(new Dimension(900, 260));
        add(south, BorderLayout.SOUTH);
    }

    private JPanel buildBlendSetup() {
        JPanel setup = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 4));
        setup.add(new JLabel("Table point"));
        tablePoint.setPreferredSize(new Dimension(
                330, tablePoint.getPreferredSize().height));
        setup.add(tablePoint);
        setup.add(new JLabel("Capture RPM (from table)"));
        setup.add(startRpm);
        setup.add(new JLabel("Desired TPS step"));
        setup.add(heldTps);
        setup.add(new JLabel("Comparable events"));
        setup.add(targetCount);
        setup.add(new JLabel("Gear"));
        setup.add(gear);
        return setup;
    }

    private JPanel buildProbeSetup() {
        JPanel panel = new JPanel(new BorderLayout(0, 3));
        probeEventInputs.add(new JLabel("Target activity events"));
        probeEventInputs.add(probeTargetCount);
        mapEstimateInputs.add(new JLabel("Stable samples / cell"));
        mapEstimateInputs.add(mapMinimumSamples);
        mapEstimateInputs.add(new JLabel("High-TPS MAP cap (kPa)"));
        mapEstimateInputs.add(mapCapKpa);
        JPanel inputs = new JPanel();
        inputs.setLayout(new BoxLayout(inputs, BoxLayout.Y_AXIS));
        inputs.add(probeEventInputs);
        inputs.add(mapEstimateInputs);
        panel.add(inputs, BorderLayout.NORTH);
        panel.add(probeSetupText, BorderLayout.CENTER);
        return panel;
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
            if (activeCaptureMode() == GuidedAeMethodModule.CaptureMode.BLEND_DURATION) {
                session.togglePause();
                if (session.snapshot().state == GuidedCaptureState.PAUSED) pauseAudioAction.run();
            } else if (activeCaptureMode() == GuidedAeMethodModule.CaptureMode.READ_ONLY_PROBE) {
                probeSession.togglePause();
                if (probeSession.state() == GuidedCaptureState.PAUSED) pauseAudioAction.run();
            }
            refresh();
        });
        discard.addActionListener(event -> {
            if (activeCaptureMode() == GuidedAeMethodModule.CaptureMode.BLEND_DURATION) {
                session.discardLast();
                evidence.discardLastAccepted();
                proposalTracker.discardLastAccepted();
            }
            refresh();
        });
        finish.addActionListener(event -> {
            if (activeCaptureMode() == GuidedAeMethodModule.CaptureMode.BLEND_DURATION) {
                session.finish();
                evidence.finish(session.snapshot());
            } else if (activeCaptureMode() == GuidedAeMethodModule.CaptureMode.READ_ONLY_PROBE) {
                probeSession.finish();
            }
            recoveryDirtyAction.run();
            refresh();
        });
        reset.addActionListener(event -> resetGuidedSession());
        readProject.addActionListener(event -> readProjectAxes());
        saveReport.addActionListener(event -> exportSession());
        copyReviewedDraft.addActionListener(event -> copyReviewedDraft());
        applyProposal.addActionListener(event -> applyCurrentProposal());
        restoreProposal.addActionListener(event -> restorePreviousApply());
        reconnect.addActionListener(event -> connectSampleDispatcher());
        tuningArea.addActionListener(event -> {
            if (updatingTuningNavigation) return;
            populateTasksForArea(selectedArea(), null);
            onTuningSelectionChanged();
        });
        tuningTask.addActionListener(event -> {
            if (updatingTuningNavigation) return;
            onTuningSelectionChanged();
        });
        tablePoint.addActionListener(event -> {
            syncStartRpmToSelectedPoint();
            refresh();
        });
        heldTps.addChangeListener(event -> refreshLiveValues());
        startRpm.addChangeListener(event -> refreshLiveValues());
        probeTargetCount.addChangeListener(event -> refresh());
        mapMinimumSamples.addChangeListener(event -> {
            probeSession.updateMapEstimateReviewSettings(
                    ((Number) mapMinimumSamples.getValue()).intValue(),
                    ((Number) mapCapKpa.getValue()).doubleValue());
            refresh();
        });
        mapCapKpa.addChangeListener(event -> {
            probeSession.updateMapEstimateReviewSettings(
                    ((Number) mapMinimumSamples.getValue()).intValue(),
                    ((Number) mapCapKpa.getValue()).doubleValue());
            refresh();
        });
    }

    private void initializeTuningNavigation() {
        updatingTuningNavigation = true;
        try {
            tuningArea.setSelectedItem(GuidedTuningArea.FOUNDATION);
            populateTasksForArea(GuidedTuningArea.FOUNDATION,
                    GuidedTuningRecipe.ENGAGEMENT_DETECTION);
        } finally {
            updatingTuningNavigation = false;
        }
        updateTuningPathGuidance();
    }

    private GuidedTuningArea selectedArea() {
        Object selected = tuningArea.getSelectedItem();
        return selected instanceof GuidedTuningArea
                ? (GuidedTuningArea) selected
                : GuidedTuningArea.FOUNDATION;
    }

    private void populateTasksForArea(GuidedTuningArea area,
                                      GuidedTuningRecipe preferred) {
        GuidedTuningArea safeArea = area == null
                ? GuidedTuningArea.FOUNDATION : area;
        updatingTuningNavigation = true;
        try {
            tuningTask.removeAllItems();
            for (GuidedTuningRecipe recipe : safeArea.tasks()) {
                tuningTask.addItem(recipe);
            }
            GuidedTuningRecipe desired = preferred != null && safeArea.contains(preferred)
                    ? preferred
                    : (tuningTask.getItemCount() > 0
                        ? tuningTask.getItemAt(0)
                        : GuidedTuningRecipe.ENGAGEMENT_DETECTION);
            tuningTask.setSelectedItem(desired);
        } finally {
            updatingTuningNavigation = false;
        }
        updateTuningPathGuidance();
    }

    private void updateTuningPathGuidance() {
        GuidedTuningArea area = selectedArea();
        tuningPathGuidance.setText(area.displayName + ": " + area.guidance);
        tuningPathGuidance.setCaretPosition(0);
    }

    private void onTuningSelectionChanged() {
        updateTuningPathGuidance();
        refresh();
        refreshSelectedMethodStatus();
        publishSelectedGuidedFocus();
        resetMethodViewToTop();
    }

    private void publishSelectedGuidedFocus() {
        GuidedAeMethodModule module = selectedModule();
        GuidedTuningRecipe recipe = module.recipe();
        String guidance = module.currentTuneContext(projectSnapshot);

        if (recipe == GuidedTuningRecipe.MAP_ESTIMATE
                && projectSnapshot != null
                && projectSnapshot.hasMapEstimateTable()) {
            probeSession.configureMapEstimateFocus(projectSnapshot,
                    ((Number) mapMinimumSamples.getValue()).intValue(),
                    ((Number) mapCapKpa.getValue()).doubleValue());
            return;
        }

        GuidedCaptureState state = GuidedCaptureState.IDLE;
        if (recipe == activeRecipe && anyCaptureActive()) {
            state = activeSnapshot().state;
        } else if (recipe == activeRecipe
                && module.captureMode() == GuidedAeMethodModule.CaptureMode.READ_ONLY_PROBE
                && probeSession.module() == module) {
            state = probeSession.state();
        } else if (recipe == activeRecipe
                && module.captureMode() == GuidedAeMethodModule.CaptureMode.BLEND_DURATION) {
            state = session.snapshot().state;
        }

        GuidedFocusHub.publish(recipe, state,
                (se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateFocusModel) null,
                guidance);
    }

    private void resetMethodViewToTop() {
        headline.setCaretPosition(0);
        checks.setCaretPosition(0);
        result.setCaretPosition(0);
        proposal.setCaretPosition(0);
        probeSetupText.setCaretPosition(0);
        SwingUtilities.invokeLater(() -> checksScroll.getVerticalScrollBar().setValue(0));
    }

    private void resetGuidedSession() {
        session.reset();
        probeSession.reset();
        evidence.reset();
        proposalTracker.reset();
        activeRecipe = selectedRecipe();
        probeEvidenceExported = true;
        lastApplyManifestJson = "";
        currentProposalApplied = false;
        workingTuneReadRequiredAfterApply = false;
        appliedProposalSummary = "";
        applyStatus = "Guided session reset; no apply manifest is attached to the new session.";
        pauseAudioAction.run();
        recoveryDirtyAction.run();
        refresh();
        resetMethodViewToTop();
    }

    public void setRecoveryDirtyAction(Runnable action) {
        recoveryDirtyAction = action == null ? new Runnable() {
            @Override public void run() { }
        } : action;
    }

    public void setWorkflowEventListener(GuidedWorkflowEvent.Listener listener) {
        session.setWorkflowEventListener(listener);
        probeSession.setWorkflowEventListener(listener);
    }

    public void setAudioAuditSuppliers(Supplier<String> textSupplier,
                                Supplier<String> csvSupplier) {
        audioAuditText = textSupplier == null ? audioAuditText : textSupplier;
        audioAuditCsv = csvSupplier == null ? audioAuditCsv : csvSupplier;
    }

    public void setPauseAudioAction(Runnable action) {
        pauseAudioAction = action == null ? new Runnable() {
            @Override public void run() { }
        } : action;
    }

    private GuidedTuningRecipe selectedRecipe() {
        Object selected = tuningTask.getSelectedItem();
        return selected instanceof GuidedTuningRecipe
                ? (GuidedTuningRecipe) selected
                : GuidedTuningRecipe.ENGAGEMENT_DETECTION;
    }

    private GuidedAeMethodModule selectedModule() {
        return GuidedAeMethodModules.forRecipe(selectedRecipe());
    }

    private static boolean hasTpsAeTable(AeProjectSnapshot snapshot) {
        if (snapshot == null) return false;
        double[] cycles = snapshot.getCycleBins();
        double[] rows = snapshot.getTpsToBins();
        double[][] values = snapshot.getCycleValues();
        if (cycles.length == 0 || rows.length == 0 || values.length != rows.length) return false;
        for (double[] row : values) {
            if (row == null || row.length != cycles.length) return false;
        }
        return true;
    }

    private boolean blendCaptureActive() {
        GuidedCaptureState state = session.snapshot().state;
        return state != GuidedCaptureState.IDLE && state != GuidedCaptureState.COMPLETE;
    }

    private boolean probeCaptureActive() {
        GuidedCaptureState state = probeSession.state();
        return state != GuidedCaptureState.IDLE && state != GuidedCaptureState.COMPLETE;
    }

    private boolean anyCaptureActive() {
        return blendCaptureActive() || probeCaptureActive();
    }

    private GuidedAeMethodModule.CaptureMode activeCaptureMode() {
        if (blendCaptureActive()) return GuidedAeMethodModule.CaptureMode.BLEND_DURATION;
        if (probeCaptureActive()) return GuidedAeMethodModule.CaptureMode.READ_ONLY_PROBE;
        return selectedModule().captureMode();
    }

    private GuidedSessionSnapshot idleSnapshot(GuidedAeMethodModule module) {
        return new GuidedSessionSnapshot(GuidedCaptureState.IDLE,
                "SETUP", module.captureGoal(), methodBaseSetupText(module),
                "No capture has been started for this selected method.", 0, "");
    }

    private GuidedSessionSnapshot activeSnapshot() {
        if (blendCaptureActive()) return session.snapshot();
        if (probeCaptureActive()) return probeSession.snapshot();
        GuidedAeMethodModule module = selectedModule();
        if (module.captureMode() == GuidedAeMethodModule.CaptureMode.BLEND_DURATION) {
            return session.snapshot();
        }
        if (module.captureMode() == GuidedAeMethodModule.CaptureMode.READ_ONLY_PROBE
                && probeSession.module() == module) {
            return probeSession.snapshot();
        }
        return idleSnapshot(module);
    }

    private boolean hasUnexportedProbeEvidence() {
        return probeSession.hasEvidence() && probeSession.module() != null
                && !probeEvidenceExported;
    }

    private boolean hasUnexportedProbeEvidenceForAnotherMethod(GuidedAeMethodModule selected) {
        return hasUnexportedProbeEvidence() && probeSession.module() != selected;
    }

    private String retainedProbeName() {
        GuidedAeMethodModule retained = probeSession.module();
        return retained == null ? "previous method" : retained.recipe().displayName;
    }

    private boolean selectedProbeBaselineReady(GuidedAeMethodModule module) {
        if (module == null || module.captureMode() != GuidedAeMethodModule.CaptureMode.READ_ONLY_PROBE) {
            return false;
        }
        if (module.recipe() == GuidedTuningRecipe.MAP_ESTIMATE) {
            return projectSnapshot != null && projectSnapshot.hasMapEstimateTable();
        }
        if (module.recipe() == GuidedTuningRecipe.TPS_AE) {
            return hasTpsAeTable(projectSnapshot);
        }
        return true;
    }

    private boolean selectedProbeActivityReady(GuidedAeMethodModule module) {
        if (module == null || module.captureMode() != GuidedAeMethodModule.CaptureMode.READ_ONLY_PROBE) {
            return false;
        }
        if (projectSnapshot == null) return true;
        if (module.recipe() == GuidedTuningRecipe.MAP_PREDICT) {
            return projectSnapshot.isMapEstimateEnabled();
        }
        if (module.recipe() == GuidedTuningRecipe.WALL_WETTING) {
            return projectSnapshot.isWallWettingEnabled();
        }
        if (module.recipe() == GuidedTuningRecipe.TPS_AE) {
            return projectSnapshot.isTpsAeEnabled();
        }
        if (module.recipe() == GuidedTuningRecipe.INSTANT_FUEL) {
            return projectSnapshot.isExtraShotEnabled();
        }
        return true;
    }

    private boolean selectedProbeReady(GuidedAeMethodModule module) {
        return selectedProbeBaselineReady(module) && selectedProbeActivityReady(module);
    }

    private String selectedProbeDisabledReason(GuidedAeMethodModule module) {
        if (module == null || projectSnapshot == null || selectedProbeActivityReady(module)) return "";
        if (module.recipe() == GuidedTuningRecipe.MAP_PREDICT) {
            return "Use MAP estimate during transient is DISABLED in the working tune, so MAP Predict activity cannot occur.";
        }
        if (module.recipe() == GuidedTuningRecipe.WALL_WETTING) {
            return "Wall Wetting is DISABLED in the working tune, so Wall Wetting activity cannot occur.";
        }
        if (module.recipe() == GuidedTuningRecipe.TPS_AE) {
            return "TPS Acceleration Enrichment is DISABLED in the working tune, so fuel-proved TPS AE events cannot occur.";
        }
        if (module.recipe() == GuidedTuningRecipe.INSTANT_FUEL) {
            return "Instant Fuel Pulse is DISABLED in the working tune, so Instant Fuel activity cannot occur.";
        }
        return "The selected method is DISABLED in the working tune, so its required activity cannot occur.";
    }

    private void refreshSelectedMethodStatus() {
        GuidedAeMethodModule module = selectedModule();
        if (workingTuneReadRequiredAfterApply) {
            connection.setText("Guided " + module.recipe().displayName
                    + " selected — Read Working Tune after the latest Apply/Restore before starting another capture.");
        } else if (hasUnexportedProbeEvidenceForAnotherMethod(module)) {
            connection.setText("Guided " + module.recipe().displayName
                    + " selected — export retained " + retainedProbeName()
                    + " evidence or Reset Session before starting this method.");
        } else if (!module.recipe().implemented
                || module.captureMode() == GuidedAeMethodModule.CaptureMode.ARCHITECTURE_ONLY) {
            connection.setText("Guided " + module.recipe().displayName
                    + " selected — no vehicle capture path.");
        } else if (module.recipe() == GuidedTuningRecipe.MAP_ESTIMATE
                && !selectedProbeBaselineReady(module)) {
            connection.setText("Guided " + module.recipe().displayName
                    + " selected — Read Working Tune first so the current MAP Estimate table is the draft baseline.");
        } else if (module.recipe() == GuidedTuningRecipe.TPS_AE
                && !selectedProbeBaselineReady(module)) {
            connection.setText("Guided " + module.recipe().displayName
                    + " selected — Read Working Tune first so the current TPS-to/cycle table is the draft baseline.");
        } else if (module.captureMode() == GuidedAeMethodModule.CaptureMode.READ_ONLY_PROBE
                && !selectedProbeActivityReady(module)) {
            connection.setText("Guided " + module.recipe().displayName + " selected — "
                    + selectedProbeDisabledReason(module) + " Start Capture is blocked.");
        } else {
            connection.setText("Guided " + module.recipe().displayName
                    + " selected — ready for setup/evidence capture.");
        }
    }

    private void startSession() {
        GuidedAeMethodModule module = selectedModule();
        if (!module.recipe().implemented
                || module.captureMode() == GuidedAeMethodModule.CaptureMode.ARCHITECTURE_ONLY) {
            headline.setText("SETUP\n\n" + module.recipe().displayName + " is "
                    + module.recipe().status + ".\n" + module.setupGuidance());
            connection.setText("Guided tuning not started: selected entry has no vehicle capture path");
            return;
        }
        if (anyCaptureActive()) {
            connection.setText("Finish or pause/finish the active Guided capture before starting another AE method.");
            return;
        }
        if (workingTuneReadRequiredAfterApply) {
            connection.setText("Guided capture blocked: Read Working Tune after the latest Apply/Restore so this capture uses the current working-tune baseline.");
            return;
        }
        if (hasUnexportedProbeEvidenceForAnotherMethod(module)) {
            connection.setText("Guided method switch blocked: export retained " + retainedProbeName()
                    + " evidence or Reset Session before starting "
                    + module.recipe().displayName + ".");
            return;
        }
        if (probeSession.hasEvidence() && probeSession.module() != null
                && probeSession.module() != module && probeEvidenceExported) {
            probeSession.reset();
        }

        activeRecipe = module.recipe();
        lastApplyManifestJson = "";
        currentProposalApplied = false;
        appliedProposalSummary = "";

        if (module.captureMode() == GuidedAeMethodModule.CaptureMode.READ_ONLY_PROBE) {
            if (controllerAccess != null && projectSnapshot == null) readProjectAxes();
            if (module.recipe() == GuidedTuningRecipe.MAP_ESTIMATE
                    && !selectedProbeBaselineReady(module)) {
                connection.setText("MAP Estimate capture requires the current working tune and its MAP Estimate TPS/RPM table. Read Working Tune first.");
                return;
            }
            if (module.recipe() == GuidedTuningRecipe.TPS_AE
                    && !selectedProbeBaselineReady(module)) {
                connection.setText("TPS AE capture requires the current working tune and a valid TPS-to/cycle table baseline. Read Working Tune first.");
                return;
            }
            if (!selectedProbeActivityReady(module)) {
                connection.setText("Guided " + module.recipe().displayName + " capture blocked — "
                        + selectedProbeDisabledReason(module));
                return;
            }
            if (module.recipe() == GuidedTuningRecipe.MAP_ESTIMATE
                    && probeSession.mapEstimateWorkingTuneReadRequired()) {
                connection.setText("MAP Estimate Table capture blocked — Read Working Tune after the previous Apply/Restore so the new table is the capture baseline.");
                return;
            }
            applyStatus = module.recipe().displayName
                    + " capture active. Capture itself never writes; a reviewed changed value may expose guarded Apply after Finish/Review. No burn.";
            probeSession.start(module, projectSnapshot,
                    ((Number) probeTargetCount.getValue()).intValue(),
                    ((Number) mapMinimumSamples.getValue()).intValue(),
                    ((Number) mapCapKpa.getValue()).doubleValue());
            connection.setText("Guided " + module.recipe().displayName
                    + " capture started — capture does not write the working tune");
            recoveryDirtyAction.run();
            refresh();
            return;
        }

        syncStartRpmToSelectedPoint();
        if (controllerAccess != null && projectSnapshot == null) readProjectAxes();
        GuidedBlendProposal.PointChoice point = selectedPoint();
        if (projectSnapshot == null || point == null) {
            connection.setText("Guided tuning: read the current working tune first");
            return;
        }

        double configuredRpm = point.rpm;
        syncStartRpmToSelectedPoint();
        int selected = gear.getSelectedIndex();
        int manualGear = selected >= 1 && selected <= 5 ? selected : 0;
        boolean automatic = selected == 6;
        double configuredStep = ((Number) heldTps.getValue()).doubleValue();
        int configuredCount = ((Number) targetCount.getValue()).intValue();
        String gearMode = selected >= 0
                ? String.valueOf(gear.getItemAt(selected)) : "unknown";

        applyStatus = "New Guided capture started; guarded Apply is available whenever the reviewed Blend Duration logic produces a supported changed value. No burn.";
        evidence.startAdaptive(configuredRpm, configuredStep, configuredCount,
                gearMode + " | actual table point " + Math.round(point.rpm) + " RPM",
                System.nanoTime());
        proposalTracker.start(projectSnapshot, point.index);
        session.start(new BlendDurationCaptureConfig(
                configuredRpm, configuredStep, configuredCount,
                manualGear, automatic,
                projectSnapshot.getBlendDurationRpmBins(),
                projectSnapshot.getBlendDurationValues()));
        recoveryDirtyAction.run();
        refresh();
    }

    public void connectController(ControllerAccess access) {
        controllerAccess = access;
        applyCoordinator = access == null ? null : new ProposalApplyCoordinator(access);
        readProjectAxes();
        resumePanel();
    }

    private void readProjectAxes() {
        if (controllerAccess == null) {
            connection.setText("Guided working tune unavailable: controller not connected");
            return;
        }
        if (anyCaptureActive()) {
            connection.setText("Finish the active Guided capture before re-reading the working tune");
            return;
        }
        GuidedAeMethodModule selected = selectedModule();
        if (probeSession.hasEvidence() && probeSession.module() == selected && !probeEvidenceExported
                && !workingTuneReadRequiredAfterApply) {
            connection.setText("Working-tune re-read blocked: export or reset the current method evidence first so one capture cannot span two tune baselines.");
            return;
        }
        try {
            double preferredRpm = preferredTablePointRpm();
            AeProjectSnapshot next = new AeControllerBridge(controllerAccess).readSnapshot();
            projectSnapshot = next;
            workingTuneReadRequiredAfterApply = false;
            probeSession.noteWorkingTuneRead(next,
                    ((Number) mapMinimumSamples.getValue()).intValue(),
                    ((Number) mapCapKpa.getValue()).doubleValue());
            List<GuidedBlendProposal.PointChoice> points = GuidedBlendProposal.points(next);
            replaceTablePoints(points, preferredRpm);
            proposalTracker.reset();
            currentProposalApplied = false;
            appliedProposalSummary = "";
            GuidedAeMethodModule module = selectedModule();
            if (module.captureMode() == GuidedAeMethodModule.CaptureMode.BLEND_DURATION) {
                if (points.isEmpty()) {
                    connection.setText("Guided working tune loaded; Predictive MAP Blend Duration curve not found");
                } else {
                    connection.setText("Guided working tune loaded: "
                            + points.size() + " actual Blend Duration point(s); capture RPM follows selected table bin");
                }
            } else {
                refreshSelectedMethodStatus();
            }
        } catch (ControllerException ex) {
            projectSnapshot = null;
            tablePoint.removeAllItems();
            proposalTracker.reset();
            connection.setText("Guided working tune read failed: " + ex.getMessage());
        }
        refresh();
        publishSelectedGuidedFocus();
    }

    private double preferredTablePointRpm() {
        GuidedBlendProposal.PointChoice point = selectedPoint();
        if (point != null && Double.isFinite(point.rpm)) return point.rpm;
        return ((Number) startRpm.getValue()).doubleValue();
    }

    private void replaceTablePoints(List<GuidedBlendProposal.PointChoice> points,
                                    double preferredRpm) {
        tablePoint.removeAllItems();
        if (points != null) {
            for (GuidedBlendProposal.PointChoice point : points) tablePoint.addItem(point);
        }
        selectNearestPoint(preferredRpm);
        syncStartRpmToSelectedPoint();
    }

    private void selectNearestPoint(double rpm) {
        if (tablePoint.getItemCount() == 0) return;
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

    private void syncStartRpmToSelectedPoint() {
        GuidedBlendProposal.PointChoice point = selectedPoint();
        if (point == null || !Double.isFinite(point.rpm)) return;
        int value = (int) Math.round(point.rpm);
        if (((Number) startRpm.getValue()).intValue() != value) startRpm.setValue(Integer.valueOf(value));
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
                ? "Guided tuning: sample worker connected; working tune not loaded"
                : "Guided tuning: sample worker and working tune ready");
    }

    private synchronized void disconnectSampleDispatcher() {
        enabled = false;
        sampleDispatcher.suspend();
        latestSharedSample = null;
    }

    public void resumePanel() {
        if (!refreshTimer.isRunning()) refreshTimer.start();
        if (!isSampleDispatcherActiveForTest()) connectSampleDispatcher();
    }

    public void suspendPanel() {
        refreshTimer.stop();
        pauseAudioAction.run();
        disconnectSampleDispatcher();
        connection.setText("Guided tuning: suspended — no live samples are being processed");
    }

    public void terminateForClose() {
        session.terminateForClose();
        evidence.finish(session.snapshot());
        probeSession.finish();
        recoveryDirtyAction.run();
    }

    public void releaseAfterClose() {
        sampleDispatcher.close();
        controllerAccess = null;
        applyCoordinator = null;
        projectSnapshot = null;
        proposalTracker.reset();
        probeSession.reset();
        probeEvidenceExported = true;
    }

    public void disposePanel() {
        suspendPanel();
        terminateForClose();
        releaseAfterClose();
    }

    private GuidedCaptureState acceptDispatchedSample(LiveSample sample) {
        if (!enabled || sample == null) return activeSnapshot().state;
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
            GuidedAeMethodModule.CaptureMode mode = activeCaptureMode();
            if (mode == GuidedAeMethodModule.CaptureMode.READ_ONLY_PROBE && probeCaptureActive()) {
                int before = probeSession.sampleCount();
                probeSession.accept(sample);
                if (probeSession.sampleCount() != before) {
                    probeEvidenceExported = false;
                    recoveryDirtyAction.run();
                }
            } else if (mode == GuidedAeMethodModule.CaptureMode.BLEND_DURATION && blendCaptureActive()) {
                session.accept(sample);
                GuidedOutcome outcome = session.drainOutcome();
                GuidedSessionSnapshot snapshot = session.snapshot();
                if (outcome != null) {
                    evidence.record(outcome, snapshot);
                    proposalTracker.observe(outcome);
                    if (snapshot.state == GuidedCaptureState.COMPLETE) evidence.finish(snapshot);
                    recoveryDirtyAction.run();
                }
            }
        }
        return activeSnapshot().state;
    }

    public EvidenceRecoverySnapshot.Guided recoverySnapshot() {
        if (probeSession.hasEvidence()) {
            GuidedAeMethodModule captured = probeSession.module();
            String recipe = captured == null ? "unknown"
                    : captured.recipe().name().toLowerCase(java.util.Locale.ROOT);
            String id = "guided-probe-" + recipe;
            return new EvidenceRecoverySnapshot.Guided(id, probeSession.sampleCount(),
                    probeSession.reportText(AeTunerPlugin.VERSION), probeSession.csvText());
        }
        int count = evidence.recordCount();
        if (count <= 0) return null;
        GuidedSessionSnapshot snapshot = session.snapshot();
        GuidedBlendProposal guidedProposal = proposalTracker.evaluate();
        String reportText = evidence.reportText(
                AeTunerPlugin.VERSION, snapshot,
                guidedProposal.getDisplayText(), guidedProposal.getCopyPasteBlock())
                + "\n\n" + evidence.diagnosticText()
                + "\nAUDIO / WORKFLOW DIAGNOSTICS\n"
                + "============================\n" + audioAuditText.get();
        String csvText = evidence.csvText(AeTunerPlugin.VERSION);
        return new EvidenceRecoverySnapshot.Guided(
                evidence.sessionId(), count, reportText, csvText);
    }

    private void exportSession() {
        if (hasUnexportedProbeEvidence()) {
            exportProbeSession();
            return;
        }
        GuidedAeMethodModule module = selectedModule();
        if (module.captureMode() == GuidedAeMethodModule.CaptureMode.READ_ONLY_PROBE) {
            exportProbeSession();
            return;
        }
        if (evidence.recordCount() <= 0 || exportInProgress) return;
        final File parentFolder = SessionExportSupport.chooseParent(this, "Guided");
        if (parentFolder == null) return;

        final GuidedSessionSnapshot snapshot = session.snapshot();
        final GuidedBlendProposal guidedProposal = proposalTracker.evaluate();
        final String reportText = evidence.reportText(
                AeTunerPlugin.VERSION, snapshot,
                guidedProposal.getDisplayText(), guidedProposal.getCopyPasteBlock())
                + "\n\n" + evidence.diagnosticText()
                + "\nAUDIO / WORKFLOW DIAGNOSTICS\n"
                + "============================\n" + audioAuditText.get();
        final String eventsCsv = evidence.csvText(AeTunerPlugin.VERSION);
        final String diagnosticsCsv = evidence.diagnosticsCsvText(audioAuditCsv.get());
        final String applyManifest = lastApplyManifestJson;
        final int eventCount = evidence.recordCount();
        final int fileCount = applyManifest.length() == 0 ? 3 : 4;

        exportInProgress = true;
        saveReport.setText("Exporting Guided Session...");
        saveReport.setEnabled(false);
        connection.setText("Guided Session export in progress — do not close TunerStudio until completion is reported");

        new SwingWorker<GuidedExportResult, Void>() {
            @Override protected GuidedExportResult doInBackground() throws Exception {
                long started = System.nanoTime();
                SessionExportSupport.StagedFolder staged = null;
                try {
                    staged = SessionExportSupport.stageSessionFolder(parentFolder, "guided");
                    SessionExportSupport.writeTextAtomic(staged.file("guided-report.txt"), reportText);
                    SessionExportSupport.writeTextAtomic(staged.file("guided-events.csv"), eventsCsv);
                    SessionExportSupport.writeTextAtomic(staged.file("guided-diagnostics.csv"), diagnosticsCsv);
                    if (applyManifest.length() > 0) {
                        SessionExportSupport.writeTextAtomic(staged.file("guided-apply-manifest.json"), applyManifest);
                    }
                    File finalFolder = staged.finish();
                    return new GuidedExportResult(finalFolder, eventCount, fileCount,
                            SessionExportSupport.elapsedMillis(started));
                } catch (Exception ex) {
                    if (staged != null) staged.cleanup();
                    throw ex;
                }
            }

            @Override protected void done() {
                exportInProgress = false;
                saveReport.setText("Export Guided Session");
                try {
                    GuidedExportResult export = get();
                    connection.setText("Guided Session export complete: "
                            + export.evidenceCount + " outcome(s), " + export.fileCount
                            + " file(s) — " + export.folder.getAbsolutePath());
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    connection.setText("Guided Session export failed: "
                            + cause.getMessage()
                            + " — incomplete data was not promoted to a final session folder");
                }
                refresh();
            }
        }.execute();
    }

    private void exportProbeSession() {
        final GuidedAeMethodModule captured = probeSession.module();
        if (!probeSession.hasEvidence() || captured == null || exportInProgress) return;
        final File parentFolder = SessionExportSupport.chooseParent(this, "Guided method evidence");
        if (parentFolder == null) return;
        final String reportText = probeSession.reportText(AeTunerPlugin.VERSION);
        final String samplesCsv = probeSession.csvText();
        final String draft = probeSession.copyPasteBlock();
        final String applyManifest = lastApplyManifestJson;
        final int sampleCount = probeSession.sampleCount();
        final int fileCount = 2 + (draft.length() > 0 ? 1 : 0)
                + (applyManifest.length() > 0 ? 1 : 0);
        final String methodName = captured.recipe().displayName;

        exportInProgress = true;
        saveReport.setText("Exporting " + methodName + "...");
        saveReport.setEnabled(false);
        connection.setText("Guided " + methodName
                + " export in progress — do not close TunerStudio until completion is reported");

        new SwingWorker<GuidedExportResult, Void>() {
            @Override protected GuidedExportResult doInBackground() throws Exception {
                long started = System.nanoTime();
                SessionExportSupport.StagedFolder staged = null;
                try {
                    staged = SessionExportSupport.stageSessionFolder(parentFolder, "guided-method");
                    SessionExportSupport.writeTextAtomic(staged.file("guided-method-report.txt"), reportText);
                    SessionExportSupport.writeTextAtomic(staged.file("guided-method-samples.csv"), samplesCsv);
                    if (draft.length() > 0) {
                        SessionExportSupport.writeTextAtomic(staged.file("guided-method-draft.tsv"), draft);
                    }
                    if (applyManifest.length() > 0) {
                        SessionExportSupport.writeTextAtomic(
                                staged.file("guided-method-apply-manifest.json"), applyManifest);
                    }
                    File finalFolder = staged.finish();
                    return new GuidedExportResult(finalFolder, sampleCount, fileCount,
                            SessionExportSupport.elapsedMillis(started));
                } catch (Exception ex) {
                    if (staged != null) staged.cleanup();
                    throw ex;
                }
            }

            @Override protected void done() {
                exportInProgress = false;
                try {
                    GuidedExportResult export = get();
                    probeEvidenceExported = true;
                    connection.setText("Guided " + methodName + " export complete: "
                            + export.evidenceCount + " coherent sample(s), "
                            + export.fileCount + " file(s) — " + export.folder.getAbsolutePath()
                            + ". A different AE method may now start without discarding this evidence.");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    connection.setText("Guided " + methodName + " export failed: "
                            + cause.getMessage()
                            + " — incomplete data was not promoted to a final session folder");
                }
                refresh();
            }
        }.execute();
    }

    private void copyReviewedDraft() {
        GuidedAeMethodModule module = selectedModule();
        if (module.captureMode() != GuidedAeMethodModule.CaptureMode.READ_ONLY_PROBE
                || probeSession.module() != module) {
            connection.setText("No reviewed draft belongs to the currently selected method.");
            return;
        }
        String draft = probeSession.copyPasteBlock();
        if (draft.length() == 0) {
            connection.setText("No paste-ready draft yet; continue accumulating the required method evidence.");
            return;
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(draft), null);
            connection.setText("Reviewed " + module.recipe().displayName
                    + " draft copied to clipboard — no working-tune write and no burn performed.");
        } catch (RuntimeException ex) {
            connection.setText("Could not copy reviewed draft to clipboard: " + ex.getMessage());
        }
    }

    private void applyCurrentProposal() {
        GuidedAeMethodModule module = selectedModule();
        if (anyCaptureActive()) {
            applyStatus = "Apply blocked: finish or reset the active capture first.";
            refresh();
            return;
        }

        ProposalWritePlan plan = module.explicitSettingWritePlan(projectSnapshot);
        if (plan == null) {
            if (module.captureMode() == GuidedAeMethodModule.CaptureMode.READ_ONLY_PROBE) {
                if (probeSession.module() != module) {
                    applyStatus = "Apply unavailable: no direct setting change is selected and this method has no completed reviewed evidence.";
                    refresh();
                    return;
                }
                if (probeSession.state() != GuidedCaptureState.COMPLETE) {
                    applyStatus = "Apply blocked: evidence-derived changes require Finish and Review first. Direct setting changes do not require capture.";
                    refresh();
                    return;
                }
                plan = probeSession.reviewedWritePlan();
                if (plan == null) {
                    applyStatus = "Apply unavailable: the reviewed " + module.recipe().displayName
                            + " currently proposes no supported changed setting/value.";
                    refresh();
                    return;
                }
            } else if (module.captureMode() == GuidedAeMethodModule.CaptureMode.BLEND_DURATION) {
                GuidedSessionSnapshot snapshot = session.snapshot();
                if (snapshot.state != GuidedCaptureState.COMPLETE) {
                    applyStatus = "Apply blocked: finish the capture and review the evidence first.";
                    refresh();
                    return;
                }
                plan = proposalTracker.evaluate().getWritePlan();
                if (plan == null) {
                    applyStatus = "Apply unavailable: the current reviewed Blend Duration result proposes no supported changed value.";
                    refresh();
                    return;
                }
            } else {
                applyStatus = "Apply unavailable: " + module.recipe().displayName
                        + " declares no direct or evidence-derived write plan.";
                refresh();
                return;
            }
        }

        if (currentProposalApplied) {
            applyStatus = "Apply blocked: this reviewed proposal is already applied and verified. Restore it or re-read for a new proposal.";
            refresh();
            return;
        }
        if (applyCoordinator == null) {
            applyStatus = "Apply unavailable: TunerStudio controller/working-tune access is not available.";
            refresh();
            return;
        }

        ProposalApplyCoordinator.ApplyResult applied = applyCoordinator.apply(plan);
        applyStatus = applied.message;
        if (applied.success) {
            lastApplyManifestJson = plan.verificationManifestJson();
            currentProposalApplied = true;
            workingTuneReadRequiredAfterApply = true;
            appliedProposalSummary = appliedPlanText(plan);
            applyStatus = "APPLIED / VERIFIED\n" + applied.message;
            if ("map-estimate-table".equals(plan.getRecipeId())) {
                probeSession.markMapEstimateWorkingTuneChanged();
            }
        }
        connection.setText(applied.success
                ? "Working-tune Apply verified — no burn performed. Read Working Tune before another Guided capture."
                : "Working-tune Apply blocked/failed — " + compactApplyFailure(applied.message));
        connection.setToolTipText(applied.success ? null : applied.message);
        refresh();
        publishSelectedGuidedFocus();
    }

    private static String compactApplyFailure(String message) {
        if (message == null || message.trim().length() == 0) return "unknown reason";
        String oneLine = message.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() <= 180 ? oneLine : oneLine.substring(0, 177) + "...";
    }

    private void restorePreviousApply() {
        GuidedSessionSnapshot snapshot = activeSnapshot();
        if (snapshot.state != GuidedCaptureState.IDLE
                && snapshot.state != GuidedCaptureState.COMPLETE) {
            applyStatus = "Restore blocked: finish or reset the active capture first.";
            refresh();
            return;
        }
        if (applyCoordinator == null) {
            applyStatus = "Restore unavailable: TunerStudio controller/working-tune access is not available.";
            refresh();
            return;
        }
        ProposalWritePlan restoring = applyCoordinator.previousApplyPlan();
        ProposalApplyCoordinator.ApplyResult restored = applyCoordinator.restorePreviousApply();
        applyStatus = restored.message;
        if (restored.success && restoring != null) {
            ProposalWritePlan reversed = restoring.reversed("Restore " + restoring.getContext());
            lastApplyManifestJson = reversed.verificationManifestJson();
            currentProposalApplied = false;
            workingTuneReadRequiredAfterApply = true;
            appliedProposalSummary = "RESTORED / VERIFIED\n" + appliedPlanText(reversed);
            applyStatus = "RESTORED / VERIFIED\n" + restored.message;
            if ("map-estimate-table".equals(restoring.getRecipeId())) {
                probeSession.markMapEstimateWorkingTuneChanged();
            }
        }
        connection.setText(restored.success
                ? "Previous AE Tuner Apply restored and read back — no burn performed. Read Working Tune before another Guided capture."
                : "Restore blocked/failed — current working tune was not overwritten");
        refresh();
    }

    private static String appliedPlanText(ProposalWritePlan plan) {
        if (plan == null) return "No applied plan available.";
        StringBuilder out = new StringBuilder();
        out.append(plan.getDisplayName());
        if (plan.getContext().length() > 0) out.append(" — ").append(plan.getContext());
        for (ProposalWritePlan.Change change : plan.getChanges()) {
            out.append("\n  ").append(change.displayLabel)
                    .append(": ").append(formatValue(change.expectedValue))
                    .append(change.unit.length() == 0 ? "" : " " + change.unit)
                    .append(" -> ").append(formatValue(change.proposedValue))
                    .append(change.unit.length() == 0 ? "" : " " + change.unit);
        }
        return out.toString();
    }

    private static String formatValue(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    public String applyAuditStatusForDiagnostics() {
        StringBuilder out = new StringBuilder();
        out.append(applyStatus)
                .append("\nCurrent reviewed proposal already applied: ")
                .append(currentProposalApplied ? "YES" : "NO")
                .append("\nFresh working-tune read required before capture: ")
                .append(workingTuneReadRequiredAfterApply ? "YES" : "NO")
                .append("\nApply/restore manifest attached to next Guided export: ")
                .append(lastApplyManifestJson.length() > 0 ? "YES" : "NO");
        if (applyCoordinator != null && applyCoordinator.canRestorePreviousApply()) {
            out.append("\nRestore stack depth: ").append(applyCoordinator.applyDepth())
                    .append("\nNext restore: ").append(applyCoordinator.previousApplyText());
        } else {
            out.append("\nRestore stack: empty");
        }
        if (appliedProposalSummary.length() > 0) {
            out.append("\n\nLatest verified operation\n").append(appliedProposalSummary);
        }
        return out.toString();
    }

    private void refresh() {
        GuidedAeMethodModule module = selectedModule();
        GuidedSessionSnapshot snapshot = activeSnapshot();
        GuidedBlendProposal.PointChoice point = selectedPoint();
        GuidedBlendProposal guidedProposal = proposalTracker.evaluate();
        boolean blend = module.captureMode() == GuidedAeMethodModule.CaptureMode.BLEND_DURATION;
        boolean probe = module.captureMode() == GuidedAeMethodModule.CaptureMode.READ_ONLY_PROBE;
        boolean selectedProbeHasEvidence = probe && probeSession.module() == module
                && probeSession.state() != GuidedCaptureState.IDLE;
        boolean switchBlocked = hasUnexportedProbeEvidenceForAnotherMethod(module);
        ProposalWritePlan explicitSettingPlan =
                module.explicitSettingWritePlan(projectSnapshot);
        ProposalWritePlan evidenceWritePlan = probe && probeSession.module() == module
                && probeSession.state() == GuidedCaptureState.COMPLETE
                ? probeSession.reviewedWritePlan() : null;
        ProposalWritePlan probeWritePlan = explicitSettingPlan != null
                ? explicitSettingPlan : evidenceWritePlan;

        routeSetupCard(module);

        if (blend) {
            headline.setText(snapshot.headline + "\n\n" + snapshot.instruction);
            String decoratedChecks = GuidedChannelValidity.decorate(snapshot.checks, latestSharedSample);
            setStableText(checks, checksScroll, decoratedChecks);
            result.setText(snapshot.result
                    + "\n\nGuided evidence outcomes recorded: " + evidence.recordCount()
                    + "\nComparable final-target duration samples: " + proposalTracker.durationCount());
        } else if (probe) {
            if (selectedProbeHasEvidence) {
                GuidedSessionSnapshot probeSnapshot = probeSession.snapshot();
                headline.setText(probeSnapshot.headline + "\n\n" + probeSnapshot.instruction);
                setStableText(checks, checksScroll, probeSnapshot.checks);
                result.setText(probeSnapshot.result);
            } else {
                headline.setText("SETUP — " + module.recipe().status + "\n"
                        + module.recipe().displayName + "\n"
                        + (explicitSettingPlan != null
                        ? "A direct working-tune setting change is ready for review. Capture is optional and only needed for evidence-based tuning."
                        : module.captureGoal()));
                setStableText(checks, checksScroll, methodBaseSetupText(module));
                result.setText(explicitSettingPlan != null
                        ? "Direct setting proposal selected. No sample capture is required to review/apply this explicit working-tune change."
                        : "No " + module.recipe().displayName
                            + " evidence collected for this selected method yet. Start Capture only when evidence is needed.");
            }
        } else {
            headline.setText("SETUP — " + module.recipe().status + "\n"
                    + module.recipe().displayName + "\n" + module.setupGuidance());
            setStableText(checks, checksScroll, module.setupGuidance());
            result.setText("No vehicle capture is attached to this development choice.");
        }

        if (switchBlocked && !anyCaptureActive()) {
            headline.setText(headline.getText()
                    + "\n\nMETHOD SWITCH WAITING\nExport retained " + retainedProbeName()
                    + " evidence, or Reset Session, before starting "
                    + module.recipe().displayName + ".");
        }
        if (workingTuneReadRequiredAfterApply && !anyCaptureActive()) {
            headline.setText(headline.getText()
                    + "\n\nWORKING-TUNE BASELINE CHANGED\nRead Working Tune before another capture. Apply/Restore itself remains available independently of engine-running state.");
        }

        double configuredRpm = point == null
                ? ((Number) startRpm.getValue()).doubleValue() : point.rpm;
        if (blend) {
            String axisGuidance = point == null
                    ? "No actual Blend Duration table point selected."
                    : (currentProposalApplied ? "Capture baseline (pre-Apply): " : "Selected: ")
                        + point.toString() + "\n" + point.startGuidance(configuredRpm);
            String writeReview = guidedProposal.getWritePlan() == null
                    ? "No guarded Blend Duration value change is currently proposed."
                    : guidedProposal.getWritePlan().reviewText();
            proposal.setText(axisGuidance + "\n\n"
                    + guidedProposal.getDisplayText()
                    + "\n\n" + writeReview
                    + (appliedProposalSummary.length() == 0 ? ""
                    : "\n\nVERIFIED WORKING-TUNE OPERATION\n" + appliedProposalSummary)
                    + "\n\nAPPLY / RESTORE STATUS\n" + applyStatus
                    + (applyCoordinator == null || !applyCoordinator.canRestorePreviousApply()
                    ? "" : "\nRestore stack: " + applyCoordinator.applyDepth()
                    + " explicit apply record(s); next: " + applyCoordinator.previousApplyText())
                    + (lastApplyManifestJson.length() == 0 ? ""
                    : "\n\nMSQ VERIFICATION\nThe next Guided Session export will include guided-apply-manifest.json for the most recent successful Apply/Restore operation in this Guided session."));
        } else if (probe) {
            String review = selectedProbeHasEvidence
                    ? probeSession.reviewText()
                    : "METHOD PLAN\n" + module.reviewOutputs();
            String proposalOrigin = explicitSettingPlan != null
                    ? "DIRECT SETTING REVIEW\nNo capture is required for this explicit operator-selected working-tune change.\n\n"
                    : "";
            proposal.setText("CURRENT TUNING TASK\n"
                    + module.recipe().displayName + " — " + module.recipe().status + "\n\n"
                    + "WORKING-TUNE CONTEXT\n" + module.currentTuneContext(projectSnapshot)
                    + "\n\n" + proposalOrigin + review
                    + "\n\nSAFETY\n"
                    + "Capture never writes. Explicit setting changes may go directly from Read Working Tune -> Review Change -> guarded Apply/Restore. Evidence-derived proposals still require the method's capture/review rules. No automatic Apply and no burn."
                    + (appliedProposalSummary.length() == 0 ? ""
                    : "\n\nVERIFIED WORKING-TUNE OPERATION\n" + appliedProposalSummary)
                    + "\n\nAPPLY / RESTORE STATUS\n" + applyStatus
                    + (probeWritePlan == null ? ""
                    : "\n\n" + probeWritePlan.reviewText())
                    + (lastApplyManifestJson.length() == 0 ? ""
                    : "\n\nMSQ VERIFICATION\nThe next Guided method export will include guided-method-apply-manifest.json for the most recent successful Apply/Restore operation."));
        } else {
            proposal.setText("CURRENT TUNING TASK\n"
                    + module.recipe().displayName + "\nStatus: " + module.recipe().status + "\n\n"
                    + module.setupGuidance());
        }

        refreshWorkflowStage(snapshot, module, probeWritePlan);
        refreshLiveValues();
        pause.setText(snapshot.state == GuidedCaptureState.PAUSED ? "Resume" : "Pause");
        boolean captureActive = anyCaptureActive();
        pause.setEnabled(captureActive);
        finish.setEnabled(captureActive);
        discard.setEnabled(blendCaptureActive() && snapshot.accepted > 0);

        boolean setupEnabled = !captureActive;
        tuningArea.setEnabled(setupEnabled);
        tuningTask.setEnabled(setupEnabled);
        startRpm.setEnabled(false);
        heldTps.setEnabled(setupEnabled && blend);
        targetCount.setEnabled(setupEnabled && blend);
        gear.setEnabled(setupEnabled && blend);
        tablePoint.setEnabled(setupEnabled && blend && tablePoint.getItemCount() > 0);
        probeTargetCount.setEnabled(setupEnabled && probe
                && module.recipe() != GuidedTuningRecipe.MAP_ESTIMATE);
        mapMinimumSamples.setEnabled(setupEnabled && module.recipe() == GuidedTuningRecipe.MAP_ESTIMATE);
        mapCapKpa.setEnabled(setupEnabled && module.recipe() == GuidedTuningRecipe.MAP_ESTIMATE);
        readProject.setEnabled(setupEnabled && controllerAccess != null);

        boolean sameProbeComplete = probe && probeSession.module() == module
                && probeSession.state() == GuidedCaptureState.COMPLETE;
        start.setText(sameProbeComplete ? "Continue Capture" : "Start Capture");
        boolean methodReady = module.recipe().implemented
                && !workingTuneReadRequiredAfterApply
                && ((probe && selectedProbeReady(module))
                || (blend && point != null));
        start.setEnabled(setupEnabled && methodReady && !switchBlocked);

        boolean selectedProbeEvidence = probe && probeSession.module() == module
                && probeSession.hasEvidence();
        if (hasUnexportedProbeEvidence()) {
            saveReport.setText("Export " + retainedProbeName() + " Evidence");
            saveReport.setEnabled(!exportInProgress);
        } else {
            saveReport.setText(blend ? "Export Guided Session" : "Export Evidence");
            saveReport.setEnabled(!exportInProgress
                    && (blend ? evidence.recordCount() > 0 : selectedProbeEvidence));
        }
        copyReviewedDraft.setEnabled(probe && probeSession.module() == module
                && probeSession.copyPasteBlock().length() > 0);
        applyProposal.setText(currentProposalApplied ? "Applied / Verified" : "Apply Current Proposal");
        boolean probeApplyReady = probeWritePlan != null;
        applyProposal.setEnabled(setupEnabled
                && ((blend
                    && snapshot.state == GuidedCaptureState.COMPLETE
                    && guidedProposal.hasWritePlan()) || probeApplyReady)
                && applyCoordinator != null
                && !currentProposalApplied);
        restoreProposal.setEnabled(setupEnabled && applyCoordinator != null
                && applyCoordinator.canRestorePreviousApply());

        GuidedSampleDispatcher.Diagnostics dispatch = sampleDispatcher.diagnostics();
        String deliveredRate = sampleRate > 0.0
                ? "Guided delivered rate: " + Math.round(sampleRate) + " Hz"
                : "Guided delivered rate: n/a";
        rate.setText(deliveredRate + " | q " + dispatch.queueDepth + "/"
                + GuidedSampleDispatcher.CAPACITY + " | coalesced "
                + dispatch.coalesced + " | dropped " + dispatch.dropped);
    }

    private void routeSetupCard(GuidedAeMethodModule module) {
        if (module.captureMode() == GuidedAeMethodModule.CaptureMode.BLEND_DURATION) {
            setupCardLayout.show(setupCards, CARD_BLEND);
            liveValuesPanel.setVisible(true);
        } else if (module.captureMode() == GuidedAeMethodModule.CaptureMode.READ_ONLY_PROBE) {
            boolean mapEstimate = module.recipe() == GuidedTuningRecipe.MAP_ESTIMATE;
            probeEventInputs.setVisible(!mapEstimate);
            mapEstimateInputs.setVisible(mapEstimate);
            probeSetupText.setText(module.setupTitle()
                    + "\nACTION: " + module.operatorInputs(projectSnapshot)
                    + "\nTUNE: " + module.currentTuneContext(projectSnapshot));
            probeSetupText.setCaretPosition(0);
            setupCardLayout.show(setupCards, CARD_PROBE);
            liveValuesPanel.setVisible(false);
        } else {
            architectureSetupText.setText(module.setupTitle() + "\n\n" + module.setupGuidance());
            setupCardLayout.show(setupCards, CARD_ARCH);
            liveValuesPanel.setVisible(false);
        }
        setupCards.revalidate();
    }

    private String methodBaseSetupText(GuidedAeMethodModule module) {
        StringBuilder out = new StringBuilder();
        out.append("ACCUMULATION GOAL\n")
                .append(module.captureGoal()).append("\n\n")
                .append("HOW DATA IS ACCEPTED / GROUPED\n")
                .append(module.accumulationPlan()).append("\n\n");
        ChannelRole[] required = module.requiredRoles();
        out.append("REQUIRED CHANNELS (").append(required.length)
                .append(") — all are needed for complete method evidence\n");
        appendRoleSummary(out, required);
        ChannelRole[] context = module.contextRoles();
        out.append("\nCONTEXT / ATTRIBUTION CHANNELS (").append(context.length)
                .append(") — used to separate overlapping AE effects\n");
        appendRoleSummary(out, context);
        out.append("\nREVIEW OUTPUTS\n").append(module.reviewOutputs())
                .append("\n\nSafety: capture never writes. A reviewed changed value may use explicit guarded Apply/readback/Restore; no automatic Apply and no burn.");
        return out.toString();
    }

    private static void appendRoleSummary(StringBuilder out, ChannelRole[] roles) {
        if (roles == null || roles.length == 0) {
            out.append("none\n");
            return;
        }
        for (int i = 0; i < roles.length; i++) {
            if (i > 0) out.append(", ");
            out.append(roles[i].getLabel());
        }
        out.append('\n');
    }

    private void refreshWorkflowStage(GuidedSessionSnapshot snapshot,
                                      GuidedAeMethodModule module,
                                      ProposalWritePlan probeWritePlan) {
        ProposalWritePlan direct = module.explicitSettingWritePlan(projectSnapshot);
        if (direct != null && !anyCaptureActive()) {
            workflowStage.setText(currentProposalApplied
                    ? "1 SETTING ✓   2 REVIEW CHANGE ✓   3 APPLY / VERIFY ✓"
                    : "1 SETTING ✓   2 REVIEW CHANGE ✓   3 APPLY / VERIFY ●");
            return;
        }

        if (!module.recipe().implemented
                || module.captureMode() == GuidedAeMethodModule.CaptureMode.ARCHITECTURE_ONLY) {
            workflowStage.setText("REVIEW / SIMPLIFY — no mandatory capture/apply sequence");
            return;
        }

        boolean probe = module.captureMode() == GuidedAeMethodModule.CaptureMode.READ_ONLY_PROBE;
        if (snapshot.state == GuidedCaptureState.IDLE) {
            workflowStage.setText(probe
                    ? "1 SETUP ●   2 CAPTURE (when evidence is needed) -> 3 REVIEW -> 4 APPLY if a change is proposed"
                    : "1 SETUP ●   2 CAPTURE -> 3 REVIEW -> 4 APPLY / VERIFY");
        } else if (snapshot.state == GuidedCaptureState.COMPLETE) {
            if (probe) {
                workflowStage.setText(probeWritePlan != null
                        ? "1 SETUP ✓   2 CAPTURE ✓   3 REVIEW ✓   4 APPLY / VERIFY ●"
                        : "1 SETUP ✓   2 CAPTURE ✓   3 REVIEW ●   4 APPLY — no change proposed");
            } else {
                boolean applied = applyCoordinator != null && applyCoordinator.canRestorePreviousApply();
                workflowStage.setText(applied
                        ? "1 SETUP ✓   2 CAPTURE ✓   3 REVIEW ✓   4 APPLY / VERIFY ✓"
                        : "1 SETUP ✓   2 CAPTURE ✓   3 REVIEW ●   4 APPLY / VERIFY ->");
            }
        } else {
            workflowStage.setText(probe
                    ? "1 SETUP ✓   2 CAPTURE ●   3 REVIEW -> 4 APPLY / VERIFY"
                    : "1 SETUP ✓   2 CAPTURE ●   3 REVIEW -> 4 APPLY / VERIFY");
        }
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
        if (next.equals(area.getText())) return;
        final JScrollBar bar = scroll.getVerticalScrollBar();
        final int previous = bar.getValue();
        final boolean atBottom = previous + bar.getVisibleAmount()
                >= bar.getMaximum() - 2;
        area.setText(next);
        SwingUtilities.invokeLater(() -> {
            int maximum = Math.max(0, bar.getMaximum() - bar.getVisibleAmount());
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

    int tuningAreaCountForTest() { return tuningArea.getItemCount(); }
    String selectedTuningAreaForTest() { return String.valueOf(tuningArea.getSelectedItem()); }
    int tuningTaskCountForTest() { return tuningTask.getItemCount(); }
    String selectedTuningTaskForTest() { return String.valueOf(tuningTask.getSelectedItem()); }
    String connectionTextForTest() { return connection.getText(); }
    String workflowStageTextForTest() { return workflowStage.getText(); }
    boolean applyCurrentProposalEnabledForTest() { return applyProposal.isEnabled(); }
    boolean restorePreviousApplyEnabledForTest() { return restoreProposal.isEnabled(); }
    String lastApplyManifestForTest() { return lastApplyManifestJson; }
    boolean startCaptureEnabledForTest() { return start.isEnabled(); }
    String startCaptureTextForTest() { return start.getText(); }
    String headlineTextForTest() { return headline.getText(); }
    String checksTextForTest() { return checks.getText(); }
    String proposalTextForTest() { return proposal.getText(); }
    String probeSetupTextForTest() { return probeSetupText.getText(); }
    String saveReportTextForTest() { return saveReport.getText(); }
    boolean saveReportEnabledForTest() { return saveReport.isEnabled(); }
    boolean liveDriverTargetsVisibleForTest() { return liveValuesPanel.isVisible(); }
    boolean probeEventInputsVisibleForTest() { return probeEventInputs.isVisible(); }
    boolean mapEstimateInputsVisibleForTest() { return mapEstimateInputs.isVisible(); }
    boolean copyReviewedDraftEnabledForTest() { return copyReviewedDraft.isEnabled(); }
    int probeSampleCountForTest() { return probeSession.sampleCount(); }
    int probeActivityEventCountForTest() { return probeSession.activityEventCount(); }
    boolean tuningAreaEnabledForTest() { return tuningArea.isEnabled(); }
    boolean tuningTaskEnabledForTest() { return tuningTask.isEnabled(); }
    boolean workingTuneReadRequiredAfterApplyForTest() { return workingTuneReadRequiredAfterApply; }

    void selectTuningTaskForTest(GuidedTuningRecipe recipe) {
        GuidedTuningArea area = GuidedTuningArea.forRecipe(recipe);
        updatingTuningNavigation = true;
        try {
            tuningArea.setSelectedItem(area);
            populateTasksForArea(area, recipe);
        } finally {
            updatingTuningNavigation = false;
        }
        onTuningSelectionChanged();
    }

    void selectTuningAreaForTest(GuidedTuningArea area) {
        updatingTuningNavigation = true;
        try {
            tuningArea.setSelectedItem(area);
            populateTasksForArea(area, null);
        } finally {
            updatingTuningNavigation = false;
        }
        onTuningSelectionChanged();
    }

    void setProjectSnapshotForTest(AeProjectSnapshot snapshot) {
        projectSnapshot = snapshot;
        replaceTablePoints(GuidedBlendProposal.points(snapshot), preferredTablePointRpm());
        refresh();
        refreshSelectedMethodStatus();
        publishSelectedGuidedFocus();
    }

    void startSelectedTaskForTest() { startSession(); }

    void finishSelectedTaskForTest() {
        if (probeCaptureActive()) probeSession.finish();
        else if (blendCaptureActive()) session.finish();
        refresh();
    }

    void acceptProbeSampleForTest(LiveSample sample) {
        if (probeSession.state() == GuidedCaptureState.CAPTURING) {
            probeSession.accept(sample);
            probeEvidenceExported = false;
        }
        refresh();
    }

    void markProbeEvidenceExportedForTest() {
        probeEvidenceExported = true;
        refresh();
    }

    void showLiveSampleForTest(LiveSample sample) {
        latestSharedSample = sample;
        refreshLiveValues();
    }

    String liveTpsTextForTest() { return liveTps.labelTextForTest(); }
    String liveRpmTextForTest() { return liveRpm.labelTextForTest(); }
    double liveTpsTargetForTest() { return liveTps.targetForTest(); }
    double liveRpmTargetForTest() { return liveRpm.targetForTest(); }
    double liveTpsBandLowForTest() { return liveTps.innerLowForTest(); }
    double liveTpsBandHighForTest() { return liveTps.innerHighForTest(); }
    double liveRpmAcquireLowForTest() { return liveRpm.innerLowForTest(); }
    double liveRpmAcquireHighForTest() { return liveRpm.innerHighForTest(); }
    double liveRpmRetainLowForTest() { return liveRpm.outerLowForTest(); }
    double liveRpmRetainHighForTest() { return liveRpm.outerHighForTest(); }
    double configuredStartRpmForTest() { return ((Number) startRpm.getValue()).doubleValue(); }
    boolean captureRpmEditableForTest() { return startRpm.isEnabled(); }

    void selectTablePointForTest(int index) {
        tablePoint.setSelectedIndex(index);
        syncStartRpmToSelectedPoint();
    }

    void replaceTablePointsForTest(List<GuidedBlendProposal.PointChoice> points) {
        replaceTablePoints(points, preferredTablePointRpm());
    }

    double selectedTablePointRpmForTest() {
        GuidedBlendProposal.PointChoice point = selectedPoint();
        return point == null ? Double.NaN : point.rpm;
    }

    int checksCaretPolicyForTest() {
        return ((DefaultCaret) checks.getCaret()).getUpdatePolicy();
    }

    private static final class GuidedExportResult {
        final File folder;
        final int evidenceCount;
        final int fileCount;
        final double elapsedMillis;

        GuidedExportResult(File folder, int evidenceCount, int fileCount,
                           double elapsedMillis) {
            this.folder = folder;
            this.evidenceCount = evidenceCount;
            this.fileCount = fileCount;
            this.elapsedMillis = elapsedMillis;
        }
    }
}
