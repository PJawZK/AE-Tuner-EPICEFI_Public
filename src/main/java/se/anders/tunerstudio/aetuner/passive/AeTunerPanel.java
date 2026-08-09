package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import com.efiAnalytics.plugin.ecu.ControllerException;
import com.efiAnalytics.plugin.ecu.OutputChannelClient;
import com.efiAnalytics.plugin.ecu.servers.OutputChannelServer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.DefaultCaret;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.HierarchyEvent;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Read-only live AE capture panel. This first version intentionally performs
 * no ECU writes and no automatic recommendations.
 */
public final class AeTunerPanel extends JPanel implements OutputChannelClient {
    private static final DecimalFormat F1 = new DecimalFormat("0.0");
    private static final DecimalFormat F2 = new DecimalFormat("0.00");
    private static final DecimalFormat F3 = new DecimalFormat("0.000");
    private static final long MIN_SAMPLE_GAP_NS = 8000000L;
    private static final long STARTUP_IGNORE_NS = 3000000000L;

    private final JTextArea connectionLabel = createStatusText("Not connected", 2);
    private final JLabel sampleRateLabel = new JLabel("Sample rate: n/a");
    private final JTextArea calibrationLabel = createStatusText("TPS calibration: not run", 1);
    private final JTextArea snapshotLabel = createStatusText("AE project data: not read yet", 4);
    private final JTextArea eventCountLabel = createStatusText("Events: 0 TPS AE fuel proved / 0 diagnostic / 0 rejected", 1);
    private final JTextArea fuelPathStatusLabel = createStatusText("Fuel-path status: waiting for live data", 2);
    private final JTextArea sessionModeLabel = createStatusText("Session mode: read AE project data first", 4);
    private final JTextArea guidanceLabel = createStatusText("AE tuning guidance: read project data and collect events.", 4);
    private final JTextArea mapCollectionLabel = createStatusText("MAP Estimate collection: waiting for project data.", 2);
    // Session review is intentionally long-form diagnostic evidence. Its rows
    // contribute to the Technical-details page height, which has its own
    // scrollbar, rather than clipping the review into a compact status card.
    private final JTextArea sessionReviewLabel = createStatusText("Session review: no data yet.", 80);
    private final JTextArea recommendationHistoryText = createStatusText(
            "Session Guidance: waiting for the first recommendation.", 18);

    // Compact, color-coded overview. Detailed diagnostic text remains available
    // on the Technical details tab.
    private final JLabel overviewConnectionLabel = new JLabel("TunerStudio project not connected");
    private final JLabel overviewRateLabel = new JLabel("Sample rate: n/a");
    private final StatusCard workflowCard = new StatusCard("Current tuning stage", 190, 66);
    private final StatusCard tpsCycleCard = new StatusCard("TPS cycle AE", 180, 66);
    private final StatusCard mapPredictCard = new StatusCard("MAP Predict", 140, 66);
    private final StatusCard wallWettingCard = new StatusCard("Wall Wetting", 175, 66);
    private final StatusCard instantFuelCard = new StatusCard("Instant Fuel", 155, 66);
    private final StatusCard detectorCard = new StatusCard("TPS-change detector", 220, 66);
    private final StatusCard predictionLiveCard = new StatusCard("Prediction now", 165, 92);
    private final StatusCard mapValuesCard = new StatusCard("MAP values", 215, 100);
    private final StatusCard transientFuelCard = new StatusCard("Transient fuel now", 180, 92);
    private final StatusCard calibrationCard = new StatusCard("TPS calibration", 275, 78);
    private final StatusCard eventProgressCard = new StatusCard("Captured events", 235, 78);
    private final StatusCard mapCoverageCard = new StatusCard("MAP Estimate coverage", 235, 78);
    private final StatusCard nextActionCard = new StatusCard("Recommended next step", 305, 78);
    private final StatusCard contributionReviewCard = new StatusCard("Transient contribution", 285, 78);
    private final StatusCard lowRpmReviewCard = new StatusCard("Low-RPM MAP Predict", 285, 78);
    private final StatusCard fullLoadSafetyCard = new StatusCard("Session / full-load safety", 350, 78);

    {
        // Four MAP lines need slightly less vertical font space on high-DPI
        // laptop scaling. Keep the card readable without widening the row.
        mapValuesCard.setValueFontSize(11f);
    }
    private final JTextField thresholdField = new JTextField("1.50", 6);
    private final JSpinner calibrationSeconds = new JSpinner(new SpinnerNumberModel(30, 5, 120, 5));
    private final JButton reconnectButton = new JButton("Reconnect / resubscribe");
    private final JButton readProjectButton = new JButton("Read AE project data");
    private final JButton calibrateButton = new JButton("Start TPS noise calibration");
    private final JButton applyCalibrationButton = new JButton("Use recommended threshold");
    private final JButton resetButton = new JButton("Reset session");
    private final JButton saveCsvButton = new JButton("Save captured events CSV");
    private final JButton suggestTableButton = new JButton("Copy suggested TPS AE table");
    private final JButton suggestMapEstimateButton = new JButton("Copy MAP Estimate draft");
    private final JButton suggestBlendButton = new JButton("Copy Blend Duration draft");
    private final JButton sessionReviewButton = new JButton("Save MAP Predict report");
    private final JSpinner mapMinimumSamples = new JSpinner(new SpinnerNumberModel(20, 3, 500, 1));
    private final JTextField mapCapField = new JTextField("115.0", 6);
    private final JTextArea latestEventText = new JTextArea(7, 80);
    private final JTabbedPane lowerTabs = new StableTabbedPane();
    private final JScrollPane mainScroll = new JScrollPane();
    private final JScrollPane overviewScroll = new JScrollPane();
    private final JScrollPane technicalScroll = new JScrollPane();
    private final JScrollPane channelScroll = new JScrollPane();
    private final DefaultTableModel channelTableModel = new DefaultTableModel(new Object[]{"Role", "Channel", "Value", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable channelTable = new JTable(channelTableModel);
    private final EventPlotPanel plotPanel = new EventPlotPanel();
    private final UiRefreshPresenter uiPresenter = new UiRefreshPresenter(
            sampleRateLabel, calibrationLabel, eventCountLabel, fuelPathStatusLabel,
            sessionModeLabel, guidanceLabel, mapCollectionLabel, sessionReviewLabel,
            recommendationHistoryText, overviewConnectionLabel, overviewRateLabel,
            calibrationCard);
    private final PassiveAdvisoryActions advisoryActions;
    private final PassiveOverviewController overviewController;
    private final Timer refreshTimer;

    private final Object lock = new Object();
    /** Serializes the host callback path with reset, calibration, reconnect, and disconnect. */
    private final Object samplingLock = new Object();
    private final EnumMap<ChannelRole, String> channelNames = new EnumMap<ChannelRole, String>(ChannelRole.class);
    private final EnumMap<ChannelRole, Double> latestValues = new EnumMap<ChannelRole, Double>(ChannelRole.class);
    private final Map<String, ChannelRole> subscribedChannels = new HashMap<String, ChannelRole>();
    private final Set<String> availableOutputChannels = new HashSet<String>();
    private final AeEventDetector eventDetector = new AeEventDetector();
    private final TpsNoiseCalibration calibration = new TpsNoiseCalibration();
    private final MapEstimateCollector mapEstimateCollector = new MapEstimateCollector();
    private final SessionMonitor sessionMonitor = new SessionMonitor();
    private volatile GuidedSampleDispatcher guidedSampleDispatcher;
    private final RecommendationHistory recommendationHistory = new RecommendationHistory();
    private final List<TransientEvent> capturedEvents = new ArrayList<TransientEvent>();

    private ControllerAccess controllerAccess;
    private OutputChannelServer outputChannelServer;
    private volatile AeProjectSnapshot projectSnapshot;
    private String configurationName;
    private LiveSample previousSample;
    private long lastSampleNano;
    private long lastRateWindowNano;
    private volatile long detectionArmedNano;
    private int samplesInWindow;
    private volatile double sampleRateHz;
    private volatile int acceptedEvents;
    private volatile int tpsAeFuelProvedEvents;
    private volatile int rejectedEvents;
    private long eventRevision;
    private boolean calibrationWasRunning;
    private volatile double manualThreshold = 1.50;
    private volatile boolean sampleCaptureEnabled = true;
    private volatile long sessionStartedNano = System.nanoTime();
    private volatile double lastCsvExportMillis = Double.NaN;
    private volatile double lastReportExportMillis = Double.NaN;
    private Runnable recoveryDirtyAction = new Runnable() {
        @Override
        public void run() { }
    };

    public AeTunerPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        advisoryActions = new PassiveAdvisoryActions(
                this, latestEventText, lowerTabs, mapMinimumSamples, mapCapField,
                mapEstimateCollector, sessionMonitor, eventDetector,
                recommendationHistory);
        overviewController = new PassiveOverviewController(
                uiPresenter, workflowCard, tpsCycleCard, mapPredictCard,
                wallWettingCard, instantFuelCard, detectorCard, predictionLiveCard,
                mapValuesCard, transientFuelCard, eventProgressCard, mapCoverageCard,
                nextActionCard, contributionReviewCard, lowRpmReviewCard,
                fullLoadSafetyCard, mapMinimumSamples, mapEstimateCollector,
                sessionMonitor, recommendationHistory, calibration);
        buildLayout();
        installActions();
        installThresholdListener();
        refreshTimer = new Timer(500, event -> refreshUi());
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0L) {
                if (isShowing()) {
                    refreshUi();
                    refreshTimer.start();
                } else {
                    refreshTimer.stop();
                }
            }
        });
    }

    private static JTextArea createStatusText(String text, int rows) {
        // A one-column preferred width lets the surrounding technical card
        // determine wrapping. The explicit row count keeps the card height
        // stable while live values update, avoiding scrollbar-range jumps.
        JTextArea area = new JTextArea(text, rows, 1);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setBorder(null);
        area.setMargin(new Insets(0, 0, 0, 0));
        area.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        area.setFocusable(false);
        ((DefaultCaret) area.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        return area;
    }

    public void setRecoveryDirtyAction(Runnable action) {
        recoveryDirtyAction = action == null ? new Runnable() {
            @Override
            public void run() { }
        } : action;
    }

    public void setGuidedSampleDispatcher(GuidedSampleDispatcher dispatcher) {
        guidedSampleDispatcher = dispatcher;
    }

    public void connectController(ControllerAccess controllerAccess) {
        this.controllerAccess = controllerAccess;
        reconnect();
    }

    public void disconnectController() {
        OutputChannelServer serverToUnsubscribe;
        synchronized (samplingLock) {
            sampleCaptureEnabled = false;
            serverToUnsubscribe = outputChannelServer;
            outputChannelServer = null;
            controllerAccess = null;
            synchronized (lock) {
                subscribedChannels.clear();
                latestValues.clear();
                channelNames.clear();
                availableOutputChannels.clear();
            }
            previousSample = null;
            lastSampleNano = 0L;
            lastRateWindowNano = 0L;
            samplesInWindow = 0;
            sampleRateHz = 0.0;
            eventDetector.resetTracking();
        }
        // Do not call host code while holding the sampling-state lock. Some host
        // implementations may wait for an in-flight callback to finish here.
        if (serverToUnsubscribe != null) {
            serverToUnsubscribe.unsubscribe(this);
        }
        connectionLabel.setText("Disconnected");
    }

    public void disposePanel() {
        refreshTimer.stop();
        disconnectController();
    }

    boolean isRefreshTimerRunning() {
        return refreshTimer.isRunning();
    }

    @Override
    public void setCurrentOutputChannelValue(String outputChannelName, double value) {
        ChannelRole role;
        synchronized (lock) {
            role = subscribedChannels.get(outputChannelName);
            if (role != null) {
                latestValues.put(role, value);
            }
        }

        if (sampleCaptureEnabled
                && (role == ChannelRole.TIME || role == ChannelRole.TPS || role == ChannelRole.RPM)) {
            maybeRecordSample();
        }
    }

    private void buildLayout() {
        PassivePanelLayout.install(this,
                new PassivePanelLayout.Controls(
                        reconnectButton, readProjectButton, saveCsvButton,
                        suggestTableButton, suggestMapEstimateButton,
                        suggestBlendButton, sessionReviewButton, resetButton,
                        thresholdField, calibrationSeconds, calibrateButton,
                        applyCalibrationButton, mapMinimumSamples, mapCapField),
                new PassivePanelLayout.Content(
                        mainScroll, channelScroll, channelTable, latestEventText,
                        recommendationHistoryText, lowerTabs, plotPanel,
                        overviewScroll, technicalScroll),
                new PassivePanelLayout.Overview(
                        overviewConnectionLabel, overviewRateLabel, workflowCard,
                        tpsCycleCard, mapPredictCard, wallWettingCard,
                        instantFuelCard, detectorCard, predictionLiveCard,
                        mapValuesCard, transientFuelCard, calibrationCard,
                        eventProgressCard, mapCoverageCard, nextActionCard,
                        contributionReviewCard, lowRpmReviewCard,
                        fullLoadSafetyCard),
                new PassivePanelLayout.Technical(
                        connectionLabel, sampleRateLabel, calibrationLabel,
                        eventCountLabel, snapshotLabel, fuelPathStatusLabel,
                        sessionModeLabel, guidanceLabel, mapCollectionLabel,
                        sessionReviewLabel));
    }

    private void installActions() {
        reconnectButton.addActionListener(event -> reconnect());
        readProjectButton.addActionListener(event -> readProjectData());
        calibrateButton.addActionListener(event -> startCalibration());
        applyCalibrationButton.addActionListener(event -> applyRecommendedCalibration());
        resetButton.addActionListener(event -> resetSession());
        saveCsvButton.addActionListener(event -> saveCsv());
        suggestTableButton.addActionListener(event -> copySuggestedAeTable());
        suggestMapEstimateButton.addActionListener(event -> copySuggestedMapEstimate());
        suggestBlendButton.addActionListener(event -> copySuggestedBlendDuration());
        sessionReviewButton.addActionListener(event -> saveMapPredictReport());
        nextActionCard.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        nextActionCard.setToolTipText("Open temporary Session Guidance history");
        nextActionCard.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (lowerTabs.getTabCount() > 2) {
                    lowerTabs.setSelectedIndex(2);
                }
            }
        });
    }

    private void installThresholdListener() {
        thresholdField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                cacheManualThreshold();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                cacheManualThreshold();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                cacheManualThreshold();
            }
        });
        cacheManualThreshold();
    }

    private void cacheManualThreshold() {
        manualThreshold = parseNumber(thresholdField.getText(), Double.NaN);
    }

    private void reconnect() {
        sampleCaptureEnabled = false;
        if (controllerAccess == null) {
            connectionLabel.setText("No controller access yet");
            return;
        }

        try {
            if (outputChannelServer != null) {
                outputChannelServer.unsubscribe(this);
            }
            outputChannelServer = controllerAccess.getOutputChannelServer();
            if (outputChannelServer == null) {
                connectionLabel.setText("No output-channel server from TunerStudio");
                return;
            }

            configurationName = findConfigurationName();
            resolveOutputChannels();
            subscribeResolvedChannels();
            synchronized (samplingLock) {
                previousSample = null;
                lastSampleNano = 0L;
                lastRateWindowNano = 0L;
                eventDetector.resetTracking();
                detectionArmedNano = System.nanoTime() + STARTUP_IGNORE_NS;
            }
            connectionLabel.setText("TunerStudio project: " + configurationName + " | subscribed " + subscribedChannels.size() + " live channel(s); ECU online state not verified by plugin");
            readProjectData();
            sampleCaptureEnabled = true;
        } catch (ControllerException ex) {
            connectionLabel.setText("Connect failed: " + ex.getMessage());
        }
    }

    private String findConfigurationName() {
        if (controllerAccess != null) {
            String[] names = controllerAccess.getEcuConfigurationNames();
            if (names != null) {
                for (String name : names) {
                    if (name != null && name.length() > 0) {
                        return name;
                    }
                }
            }
        }
        return "Main Controller";
    }

    private void resolveOutputChannels() throws ControllerException {
        String[] channels = outputChannelServer.getOutputChannels(configurationName);
        synchronized (lock) {
            channelNames.clear();
            availableOutputChannels.clear();
            if (channels != null) {
                Collections.addAll(availableOutputChannels, channels);
            }

            for (ChannelRole role : ChannelRole.values()) {
                String resolved = findAvailableChannel(role);
                if (resolved != null) {
                    channelNames.put(role, resolved);
                }
            }
        }
    }

    private String findAvailableChannel(ChannelRole role) {
    return OutputChannelResolver.resolve(role, availableOutputChannels);
}

private void subscribeResolvedChannels() throws ControllerException {
        synchronized (lock) {
            subscribedChannels.clear();
        }
        for (Map.Entry<ChannelRole, String> entry : channelNames.entrySet()) {
            outputChannelServer.subscribe(configurationName, entry.getValue(), this);
            synchronized (lock) {
                subscribedChannels.put(entry.getValue(), entry.getKey());
            }
        }
    }

    private void readProjectData() {
        if (controllerAccess == null) {
            snapshotLabel.setText("AE project data: controller not connected");
            return;
        }
        try {
            AeProjectSnapshot nextSnapshot = new AeControllerBridge(controllerAccess).readSnapshot();
            if (projectSnapshot != null
                    && !projectSnapshot.getConfigurationName().equals(nextSnapshot.getConfigurationName())) {
                recommendationHistory.reset();
                recommendationHistoryText.setText(recommendationHistory.toDisplayText());
                recommendationHistoryText.setCaretPosition(0);
            }
            projectSnapshot = nextSnapshot;
            mapEstimateCollector.configure(projectSnapshot);
            snapshotLabel.setText(projectSnapshot.toDisplayText());
            updateModeButtons();
            double rpm = latest(ChannelRole.RPM);
            if (Double.isFinite(rpm)) {
                thresholdField.setText(F3.format(projectSnapshot.recommendThresholdForRpm(rpm)));
            }
        } catch (ControllerException ex) {
            snapshotLabel.setText("AE project data read failed: " + ex.getMessage());
        }
    }

    private void startCalibration() {
        Number seconds = (Number) calibrationSeconds.getValue();
        synchronized (samplingLock) {
            calibration.start(seconds.doubleValue());
            calibrationWasRunning = true;
            eventDetector.resetTracking();
            detectionArmedNano = System.nanoTime() + STARTUP_IGNORE_NS;
        }
        calibrationLabel.setText("TPS calibration running: hold idle/steady pedal, do not touch throttle. Event capture is paused.");
    }

    private void applyRecommendedCalibration() {
        TpsNoiseCalibration.Result result = calibration.getLastResult();
        if (result != null) {
            thresholdField.setText(F3.format(result.getRecommendedThreshold()));
        }
    }

    private void resetSession() {
        synchronized (samplingLock) {
            synchronized (lock) {
                capturedEvents.clear();
                latestValues.clear();
            }
            eventDetector.resetSession();
            mapEstimateCollector.clear();
            mapEstimateCollector.configure(projectSnapshot);
            sessionMonitor.reset();
            previousSample = null;
            lastSampleNano = 0L;
            lastRateWindowNano = 0L;
            samplesInWindow = 0;
            sampleRateHz = 0.0;
            detectionArmedNano = System.nanoTime() + STARTUP_IGNORE_NS;
            acceptedEvents = 0;
            tpsAeFuelProvedEvents = 0;
            rejectedEvents = 0;
            eventRevision++;
            sessionStartedNano = System.nanoTime();
            lastCsvExportMillis = Double.NaN;
            lastReportExportMillis = Double.NaN;
        }
        recommendationHistory.reset();
        recommendationHistoryText.setText(recommendationHistory.toDisplayText());
        recommendationHistoryText.setCaretPosition(0);
        setNotesText("Session reset. Plugin is still read-only and will not write to ECU RAM/flash.", true);
        plotPanel.setEvent(null);
        refreshUi();
    }

    private void saveCsv() {
        List<TransientEvent> snapshot = capturedEventSnapshot();
        lastCsvExportMillis = advisoryActions.saveCsv(
                snapshot, lastCsvExportMillis);
    }

    private void copySuggestedAeTable() {
        advisoryActions.copySuggestedAeTable(
                projectSnapshot, capturedEventSnapshot());
    }

    private void copySuggestedMapEstimate() {
        advisoryActions.copySuggestedMapEstimate(projectSnapshot);
    }

    private void copySuggestedBlendDuration() {
        advisoryActions.copySuggestedBlendDuration(
                projectSnapshot, capturedEventSnapshot());
    }

    private void saveMapPredictReport() {
        List<TransientEvent> snapshot;
        EnumMap<ChannelRole, String> selectedChannels;
        EnumMap<ChannelRole, Double> latestChannelValues;
        synchronized (lock) {
            snapshot = new ArrayList<TransientEvent>(capturedEvents);
            selectedChannels = new EnumMap<ChannelRole, String>(channelNames);
            latestChannelValues = new EnumMap<ChannelRole, Double>(latestValues);
        }
        lastReportExportMillis = advisoryActions.saveMapPredictReport(
                configurationName, projectSnapshot, snapshot, selectedChannels,
                latestChannelValues, sampleRateHz, sessionStartedNano,
                lastCsvExportMillis, lastReportExportMillis);
    }

    public EvidenceRecoverySnapshot.Passive recoverySnapshot() {
        List<TransientEvent> snapshot;
        EnumMap<ChannelRole, String> selectedChannels;
        EnumMap<ChannelRole, Double> latestChannelValues;
        long revision;
        synchronized (lock) {
            snapshot = new ArrayList<TransientEvent>(capturedEvents);
            selectedChannels = new EnumMap<ChannelRole, String>(channelNames);
            latestChannelValues = new EnumMap<ChannelRole, Double>(latestValues);
            revision = eventRevision;
        }
        return advisoryActions.recoverySnapshot(
                configurationName, projectSnapshot, snapshot, selectedChannels,
                latestChannelValues, revision, sampleRateHz, sessionStartedNano,
                lastCsvExportMillis, lastReportExportMillis);
    }

    private List<TransientEvent> capturedEventSnapshot() {
        synchronized (lock) {
            return new ArrayList<TransientEvent>(capturedEvents);
        }
    }

    private void setNotesText(String text, boolean showNotesTab) {
        latestEventText.setText(text == null ? "" : text);
        latestEventText.setCaretPosition(0);
        if (showNotesTab && lowerTabs.getTabCount() > 1) {
            lowerTabs.setSelectedIndex(1);
        }
    }

    private double parseMapCap() {
        return advisoryActions.mapCap();
    }

    private void updateModeButtons() {
        boolean hasSnapshot = projectSnapshot != null;
        boolean mapMode = hasSnapshot && projectSnapshot.isMapPredictWorkflow();
        suggestTableButton.setEnabled(hasSnapshot && projectSnapshot.isTpsAeEnabled());
        suggestTableButton.setVisible(!mapMode);
        suggestTableButton.getParent().revalidate();
        suggestMapEstimateButton.setEnabled(hasSnapshot && projectSnapshot.hasMapEstimateTable());
        suggestBlendButton.setEnabled(hasSnapshot && projectSnapshot.hasBlendDurationCurve());
        if (mapMode) {
            suggestTableButton.setToolTipText("Disabled because TPS Acceleration Enrichment cycle fuel is OFF in the MAP Predict workflow.");
        } else {
            suggestTableButton.setToolTipText("Generate a read-only TPS AE multiplier-table draft from suitable events.");
        }
    }

    private void maybeRecordSample() {
        synchronized (samplingLock) {
            maybeRecordSampleLocked();
        }
    }

    private void maybeRecordSampleLocked() {
        long now = System.nanoTime();
        if (now - lastSampleNano < MIN_SAMPLE_GAP_NS) {
            return;
        }
        lastSampleNano = now;

        LiveSample sample;
        synchronized (lock) {
            if (!latestValues.containsKey(ChannelRole.TPS)) {
                return;
            }
            EnumMap<ChannelRole, Double> snapshot = new EnumMap<ChannelRole, Double>(ChannelRole.class);
            snapshot.putAll(latestValues);
            // The EpicEFI `seconds` output channel is usually whole-second ECU uptime,
            // which is too coarse for TPSdot. Use the local receive timestamp for
            // live transient detection and keep the ECU time only as a displayed channel.
            double seconds = now / 1000000000.0;

            double tpsDot = 0.0;
            double mapDot = 0.0;
            if (previousSample != null) {
                double dt = Math.max(0.001, seconds - previousSample.getSeconds());
                double tps = value(snapshot, ChannelRole.TPS);
                double lastTps = previousSample.get(ChannelRole.TPS);
                if (Double.isFinite(tps) && Double.isFinite(lastTps)) {
                    tpsDot = (tps - lastTps) / dt;
                }
                double map = value(snapshot, ChannelRole.MAP);
                double lastMap = previousSample.get(ChannelRole.MAP);
                if (Double.isFinite(map) && Double.isFinite(lastMap)) {
                    mapDot = (map - lastMap) / dt;
                }
            }
            sample = new LiveSample(now, seconds, snapshot, tpsDot, mapDot);
            previousSample = sample;
        }

        samplesInWindow++;
        if (lastRateWindowNano == 0L) {
            lastRateWindowNano = now;
        } else if (now - lastRateWindowNano >= 1000000000L) {
            sampleRateHz = samplesInWindow / ((now - lastRateWindowNano) / 1000000000.0);
            samplesInWindow = 0;
            lastRateWindowNano = now;
        }

        mapEstimateCollector.addSample(sample);
        sessionMonitor.addSample(sample);
        GuidedSampleDispatcher guided = guidedSampleDispatcher;
        if (guided != null) {
            guided.offer(sample);
        }

        boolean wasCalibrationRunning = calibration.isRunning();
        calibration.addSample(sample);
        if (wasCalibrationRunning && !calibration.isRunning()) {
            calibrationWasRunning = false;
            eventDetector.resetTracking();
            detectionArmedNano = now + STARTUP_IGNORE_NS;
            eventDetector.addPassiveSample(sample);
            return;
        }

        if (calibration.isRunning()) {
            eventDetector.addPassiveSample(sample);
            return;
        }
        if (detectionArmedNano > 0L && now < detectionArmedNano) {
            eventDetector.addPassiveSample(sample);
            return;
        }

        TransientEvent summary = eventDetector.addSample(sample, currentThreshold(sample),
                projectSnapshot != null && projectSnapshot.isMapPredictWorkflow());
        if (summary != null) {
            synchronized (lock) {
                capturedEvents.add(summary);
                eventRevision++;
                if (summary.isAccepted()) {
                    acceptedEvents++;
                    if (summary.isTpsAeFuelProved()) {
                        tpsAeFuelProvedEvents++;
                    }
                } else {
                    rejectedEvents++;
                }
            }
            recoveryDirtyAction.run();
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    setNotesText(summary.toDisplayText(), false);
                    plotPanel.setEvent(summary);
                }
            });
        }
    }

    private double currentThreshold(LiveSample sample) {
        double manual = manualThreshold;
        if (manual > 0.0) {
            return manual;
        }
        if (projectSnapshot != null) {
            double rpm = sample.get(ChannelRole.RPM);
            if (Double.isFinite(rpm)) {
                return projectSnapshot.recommendThresholdForRpm(rpm);
            }
        }
        return 1.5;
    }

    private static double parseNumber(String text, double fallback) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void refreshUi() {
        EnumMap<ChannelRole, String> names;
        EnumMap<ChannelRole, Double> values;
        List<TransientEvent> events;
        long revision;
        int subscribed;
        synchronized (lock) {
            names = new EnumMap<ChannelRole, String>(channelNames);
            values = new EnumMap<ChannelRole, Double>(latestValues);
            events = new ArrayList<TransientEvent>(capturedEvents);
            revision = eventRevision;
            subscribed = subscribedChannels.size();
        }
        overviewController.refresh(
                projectSnapshot, configurationName, subscribed, sampleRateHz,
                detectionArmedNano, acceptedEvents, tpsAeFuelProvedEvents,
                rejectedEvents, revision, names, values, events);
        LiveChannelTableRenderer.update(channelTableModel, names, values);
    }

    static String buildFuelPathStatusText(boolean mapPredictWorkflow,
                                          EnumMap<ChannelRole, Double> values) {
        return PassiveOverviewController.fuelPathStatus(
                mapPredictWorkflow, values);
    }

    private double latest(ChannelRole role) {
        synchronized (lock) {
            Double value = latestValues.get(role);
            return value == null ? Double.NaN : value.doubleValue();
        }
    }

    private static double value(EnumMap<ChannelRole, Double> values, ChannelRole role) {
        Double value = values.get(role);
        return value == null ? Double.NaN : value.doubleValue();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

}
