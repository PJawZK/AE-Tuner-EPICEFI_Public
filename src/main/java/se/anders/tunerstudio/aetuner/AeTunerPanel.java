package se.anders.tunerstudio.aetuner;

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
final class AeTunerPanel extends JPanel implements OutputChannelClient {
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
    private final RecommendationHistory recommendationHistory = new RecommendationHistory();
    private final List<EventSummary> capturedEvents = new ArrayList<EventSummary>();

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
    private long cachedReviewEventRevision = Long.MIN_VALUE;
    private SessionReview cachedEventReview;
    private volatile long sessionStartedNano = System.nanoTime();
    private volatile double lastCsvExportMillis = Double.NaN;
    private volatile double lastReportExportMillis = Double.NaN;

    AeTunerPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
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

    void connectController(ControllerAccess controllerAccess) {
        this.controllerAccess = controllerAccess;
        reconnect();
    }

    void disconnectController() {
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

    void disposePanel() {
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
        // Keep the complete control strip fixed above one full-window vertical
        // scrollbar. The scrollable sections have stable heights and both
        // scroll positions are preserved during the 250 ms live refresh.
        add(ControlPanelBuilder.build(
                reconnectButton, readProjectButton, saveCsvButton, suggestTableButton,
                suggestMapEstimateButton, suggestBlendButton, sessionReviewButton, resetButton,
                thresholdField, calibrationSeconds, calibrateButton, applyCalibrationButton,
                mapMinimumSamples, mapCapField), BorderLayout.NORTH);

        MainContentBuilder.configure(mainScroll, channelScroll, channelTable,
                latestEventText, recommendationHistoryText, lowerTabs, plotPanel,
                buildStatusPanel());
        add(mainScroll, BorderLayout.CENTER);
    }

    private JComponent buildStatusPanel() {
        // Both tabs use exactly the same fixed height. Switching between
        // Overview and Technical details therefore cannot change the outer
        // scroll range or clamp the main scrollbar to a new position.
        JTabbedPane tabs = new StableTabbedPane();
        JComponent overview = buildOverviewPanel();
        JComponent technical = buildTechnicalStatusPanel();
        NestedScrollWheelHandoff.install(overviewScroll, mainScroll);
        NestedScrollWheelHandoff.install(technicalScroll, mainScroll);

        tabs.addTab("Overview", overview);
        tabs.addTab("Technical details", technical);
        tabs.setToolTipTextAt(0, "Clear summary of configuration, live state, progress, and next action.");
        tabs.setToolTipTextAt(1, "Project and diagnostic details. Scroll this tab for all wrapped text.");
        tabs.setFocusable(false);
        tabs.setRequestFocusEnabled(false);
        setStatusTabsHeight(tabs, 500);
        return tabs;
    }

    private static void setStatusTabsHeight(JTabbedPane tabs, int height) {
        tabs.setPreferredSize(new Dimension(1000, height));
        tabs.setMinimumSize(new Dimension(700, height));
        tabs.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }

    private JComponent buildOverviewPanel() {
        WrappingColumnPanel panel = new WrappingColumnPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JPanel header = new JPanel(new BorderLayout(8, 0));
        overviewConnectionLabel.setFont(overviewConnectionLabel.getFont().deriveFont(Font.BOLD));
        header.add(overviewConnectionLabel, BorderLayout.CENTER);
        header.add(overviewRateLabel, BorderLayout.EAST);
        header.setAlignmentX(LEFT_ALIGNMENT);
        setFixedHeight(header, 28);

        JPanel configuration = buildCardRow("Configuration and tuning stage",
                workflowCard, tpsCycleCard, mapPredictCard, wallWettingCard, instantFuelCard, detectorCard);
        JPanel live = buildCardRow("Live transient state",
                predictionLiveCard, mapValuesCard, transientFuelCard);
        JPanel progress = buildCardRow("Session progress",
                calibrationCard, eventProgressCard, mapCoverageCard, nextActionCard);
        JPanel review = buildCardRow("MAP Predict and safety review",
                contributionReviewCard, lowRpmReviewCard, fullLoadSafetyCard);

        panel.add(header);
        panel.add(configuration);
        panel.add(live);
        panel.add(progress);
        panel.add(review);

        overviewScroll.setViewportView(panel);
        overviewScroll.setBorder(null);
        overviewScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        overviewScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        overviewScroll.getVerticalScrollBar().setUnitIncrement(18);
        overviewScroll.getVerticalScrollBar().setBlockIncrement(90);
        return overviewScroll;
    }

    private JPanel buildCardRow(String title, StatusCard... cards) {
        JPanel row = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 3));
        row.setBorder(BorderFactory.createTitledBorder(title));
        for (StatusCard card : cards) {
            row.add(card);
        }
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return row;
    }

    private static void setFixedHeight(JComponent component, int height) {
        Dimension preferred = component.getPreferredSize();
        component.setPreferredSize(new Dimension(Math.max(1, preferred.width), height));
        component.setMinimumSize(new Dimension(1, height));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }

    private JComponent buildTechnicalStatusPanel() {
        // Long sections use the full tab width. Only the two genuinely short
        // sections share the first row. This keeps text left-aligned and gives
        // wrapping enough width without oversized empty frames. The page tracks
        // the viewport width while retaining its natural vertical size so high-
        // DPI text can be reached with this tab's own scrollbar.
        ViewportWidthPanel panel = new ViewportWidthPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Read-only v" + AeTunerPlugin.VERSION),
                BorderFactory.createEmptyBorder(2, 3, 2, 3)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 3, 2, 3);
        gbc.weightx = 0.5;
        gbc.weighty = 0.0;

        addTechnicalCard(panel, gbc, 0, 0, 1,
                buildTechnicalSection("Project and connection", 52, connectionLabel, sampleRateLabel));
        addTechnicalCard(panel, gbc, 1, 0, 1,
                buildTechnicalSection("Detector and calibration", 52, calibrationLabel, eventCountLabel));

        addTechnicalCard(panel, gbc, 0, 1, 2,
                buildTechnicalSection("Active configuration", 66, snapshotLabel));
        addTechnicalCard(panel, gbc, 0, 2, 2,
                buildTechnicalSection("Live transient paths", 48, fuelPathStatusLabel));
        addTechnicalCard(panel, gbc, 0, 3, 2,
                buildTechnicalSection("Session classification", 64, sessionModeLabel));
        addTechnicalCard(panel, gbc, 0, 4, 2,
                buildTechnicalSection("Guidance and MAP collection", 82, guidanceLabel, mapCollectionLabel));
        addTechnicalCard(panel, gbc, 0, 5, 2,
                buildTechnicalSection("Low-RPM and full-load review", 64, sessionReviewLabel));

        Dimension natural = panel.getPreferredSize();
        panel.setPreferredSize(new Dimension(1000, Math.max(560, natural.height)));
        panel.setMinimumSize(new Dimension(700, 560));

        technicalScroll.setViewportView(panel);
        technicalScroll.setBorder(null);
        technicalScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        technicalScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        technicalScroll.getVerticalScrollBar().setUnitIncrement(18);
        technicalScroll.getVerticalScrollBar().setBlockIncrement(90);
        return technicalScroll;
    }

    private static void addTechnicalCard(JPanel panel, GridBagConstraints template,
                                         int x, int y, int width, JComponent card) {
        GridBagConstraints gbc = (GridBagConstraints) template.clone();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = width;
        panel.add(card, gbc);
    }

    private JPanel buildTechnicalSection(String title, int height, JComponent... components) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                BorderFactory.createEmptyBorder(1, 5, 3, 5)));
        for (JComponent component : components) {
            component.setAlignmentX(LEFT_ALIGNMENT);
            component.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            section.add(component);
        }
        Dimension preferred = section.getPreferredSize();
        section.setPreferredSize(new Dimension(Math.max(1, preferred.width),
                Math.max(height, preferred.height)));
        section.setMinimumSize(new Dimension(1, height));
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return section;
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
        List<EventSummary> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<EventSummary>(capturedEvents);
        }
        if (snapshot.isEmpty()) {
            setNotesText("No events captured yet, nothing to save.", true);
            return;
        }

        File file = AdvisoryExportCoordinator.chooseCsvTarget(this);
        if (file == null) {
            return;
        }

        long exportStartedNano = System.nanoTime();
        try {
            AdvisoryExportCoordinator.writeCsv(file, snapshot);
            lastCsvExportMillis = AdvisoryExportCoordinator.elapsedMillis(exportStartedNano);
            setNotesText("Saved " + snapshot.size() + " event(s) to " + file.getAbsolutePath()
                    + "\nCSV write duration: " + F1.format(lastCsvExportMillis) + " ms", true);
        } catch (IOException ex) {
            setNotesText("CSV save failed: " + ex.getMessage(), true);
        }
    }

    private void copySuggestedAeTable() {
        List<EventSummary> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<EventSummary>(capturedEvents);
        }
        AeTableSuggestion suggestion = AeTableSuggestion.build(projectSnapshot, snapshot);
        setNotesText(suggestion.getDisplayText(), true);
        if (suggestion.isAvailable()) {
            copyToClipboard(suggestion.getCopyPasteBlock());
        }
    }

    private void copySuggestedMapEstimate() {
        int minimum = ((Number) mapMinimumSamples.getValue()).intValue();
        double cap = parseMapCap();
        MapEstimateSuggestion suggestion = MapEstimateSuggestion.build(projectSnapshot, mapEstimateCollector, minimum, cap);
        setNotesText(suggestion.getDisplayText(), true);
        if (suggestion.isAvailable()) {
            copyToClipboard(suggestion.getCopyPasteBlock());
        }
    }

    private void copySuggestedBlendDuration() {
        List<EventSummary> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<EventSummary>(capturedEvents);
        }
        MapBlendSuggestion suggestion = MapBlendSuggestion.build(projectSnapshot, snapshot);
        setNotesText(suggestion.getDisplayText(), true);
        if (suggestion.isAvailable()) {
            copyToClipboard(suggestion.getCopyPasteBlock());
        }
    }

    private void saveMapPredictReport() {
        File file = AdvisoryExportCoordinator.chooseReportTarget(this);
        if (file == null) {
            return;
        }

        long exportStartedNano = System.nanoTime();
        List<EventSummary> snapshot;
        EnumMap<ChannelRole, String> selectedChannels;
        EnumMap<ChannelRole, Double> latestChannelValues;
        synchronized (lock) {
            snapshot = new ArrayList<EventSummary>(capturedEvents);
            selectedChannels = new EnumMap<ChannelRole, String>(channelNames);
            latestChannelValues = new EnumMap<ChannelRole, Double>(latestValues);
        }
        SessionDiagnostics diagnostics = SessionDiagnostics.build(
                sessionStartedNano, System.nanoTime(), snapshot,
                eventDetector.getRingSampleCount(), eventDetector.getActiveSampleCount(),
                mapEstimateCollector.getAcceptedSamples(), recommendationHistory.size(),
                lastCsvExportMillis, lastReportExportMillis);
        int minimum = ((Number) mapMinimumSamples.getValue()).intValue();
        double cap = parseMapCap();
        MapEstimateSuggestion mapSuggestion = MapEstimateSuggestion.build(
                projectSnapshot, mapEstimateCollector, minimum, cap);
        MapBlendSuggestion blendSuggestion = MapBlendSuggestion.build(projectSnapshot, snapshot);
        SessionReview review = SessionReview.build(snapshot, sessionMonitor.snapshot());

        String text = MapPredictReportBuilder.build(
                configurationName, snapshot, sampleRateHz, minimum, cap, diagnostics,
                mapSuggestion, blendSuggestion, selectedChannels, latestChannelValues,
                review, exportStartedNano);

        try {
            AdvisoryExportCoordinator.writeReport(file, text);
            lastReportExportMillis = AdvisoryExportCoordinator.elapsedMillis(exportStartedNano);
            setNotesText("Saved combined MAP Estimate, Blend Duration, and session review report to\n"
                    + file.getAbsolutePath() + "\nReport generation and write duration: "
                    + F1.format(lastReportExportMillis) + " ms", true);
        } catch (IOException ex) {
            setNotesText("MAP Predict report save failed: " + ex.getMessage(), true);
        }
    }

    private void setNotesText(String text, boolean showNotesTab) {
        latestEventText.setText(text == null ? "" : text);
        latestEventText.setCaretPosition(0);
        if (showNotesTab && lowerTabs.getTabCount() > 1) {
            lowerTabs.setSelectedIndex(1);
        }
    }

    private void copyToClipboard(String text) {
        String error = AdvisoryExportCoordinator.copyToClipboard(text);
        if (error != null) {
            latestEventText.append("\n\nCould not copy to clipboard: " + error);
        }
    }

    private double parseMapCap() {
        try {
            double value = Double.parseDouble(mapCapField.getText().trim().replace(',', '.'));
            return Math.max(90.0, Math.min(180.0, value));
        } catch (NumberFormatException ex) {
            return 115.0;
        }
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

        EventSummary summary = eventDetector.addSample(sample, currentThreshold(sample),
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
        refreshOverview();
        String eventCountText;
        if (projectSnapshot != null && projectSnapshot.isMapPredictWorkflow()) {
            int predictionEvents = countPredictionEvents();
            eventCountText = "Events: " + predictionEvents + " MAP Predict / "
                    + (acceptedEvents - predictionEvents) + " other diagnostic / " + rejectedEvents + " rejected";
        } else {
            eventCountText = "Events: " + tpsAeFuelProvedEvents + " TPS AE fuel proved / "
                    + (acceptedEvents - tpsAeFuelProvedEvents) + " diagnostic / " + rejectedEvents + " rejected";
        }
        uiPresenter.refreshTechnicalStatus(sampleRateHz, eventCountText,
                buildFuelPathStatusText(), buildSessionModeText(), buildSessionGuidanceText(),
                mapEstimateCollector.statusText(((Number) mapMinimumSamples.getValue()).intValue()));

        updateChannelTable();
    }

    private void refreshOverview() {
        uiPresenter.refreshCalibration(calibration.isRunning(), calibration.secondsRemaining(),
                detectionArmedNano, System.nanoTime(), calibration.getLastResult());
        int subscribed;
        synchronized (lock) {
            subscribed = subscribedChannels.size();
        }
        uiPresenter.refreshOverviewHeader(configurationName, subscribed, sampleRateHz);

        if (projectSnapshot == null) {
            workflowCard.setValue("Read project data", CardState.WAITING);
            tpsCycleCard.setValue("Unknown", CardState.WAITING);
            mapPredictCard.setValue("Unknown", CardState.WAITING);
            wallWettingCard.setValue("Unknown", CardState.WAITING);
            instantFuelCard.setValue("Unknown", CardState.WAITING);
            detectorCard.setValue("Unknown", CardState.WAITING);
            predictionLiveCard.setValue("Waiting for project data", CardState.WAITING);
            mapValuesCard.setValue("MAP data not ready", CardState.WAITING);
            transientFuelCard.setValue("Fuel paths not ready", CardState.WAITING);
            eventProgressCard.setValue("No session data", CardState.WAITING);
            mapCoverageCard.setValue("No table axes", CardState.WAITING);
            contributionReviewCard.setValue("No transient data", CardState.WAITING);
            lowRpmReviewCard.setValue("No low-RPM data", CardState.WAITING);
            fullLoadSafetyCard.setValue("No full-load data", CardState.WAITING);
            nextActionCard.setValue("Press Read AE project data", CardState.INFO);
            return;
        }

        boolean mapMode = projectSnapshot.isMapPredictWorkflow();
        workflowCard.setValue(OverviewTextRenderer.stage(projectSnapshot), CardState.GOOD);

        if (mapMode) {
            tpsCycleCard.setValue(projectSnapshot.isTpsAeEnabled() ? "ON — unexpected" : "OFF — correct",
                    projectSnapshot.isTpsAeEnabled() ? CardState.WARNING : CardState.OFF);
        } else {
            tpsCycleCard.setValue(projectSnapshot.isTpsAeEnabled() ? "ON" : "OFF",
                    projectSnapshot.isTpsAeEnabled() ? CardState.GOOD : CardState.OFF);
        }
        mapPredictCard.setValue(projectSnapshot.isMapEstimateEnabled() ? "ON" : "OFF",
                projectSnapshot.isMapEstimateEnabled() ? CardState.GOOD : CardState.OFF);
        wallWettingCard.setValue(projectSnapshot.isWallWettingEnabled()
                        ? "ON — " + projectSnapshot.getWallWettingModel()
                        : "OFF — later stage",
                projectSnapshot.isWallWettingEnabled() ? CardState.GOOD : CardState.OFF);
        instantFuelCard.setValue(projectSnapshot.isExtraShotEnabled() ? "ON" : "OFF — later stage",
                projectSnapshot.isExtraShotEnabled() ? CardState.WARNING : CardState.OFF);
        if (projectSnapshot.isDynamicThresholdEnabled()) {
            detectorCard.setValue(projectSnapshot.isDynamicThresholdAverageStatic()
                            ? "Dynamic + static average" : "Dynamic threshold", CardState.GOOD);
        } else {
            detectorCard.setValue("Static threshold only", CardState.INFO);
        }

        boolean activeChannel = channelNames.containsKey(ChannelRole.MAP_PRED_ACTIVE);
        boolean fallbackChannel = channelNames.containsKey(ChannelRole.FALLBACK_MAP);
        boolean effectiveChannel = channelNames.containsKey(ChannelRole.EFFECTIVE_MAP);
        boolean resetChannel = channelNames.containsKey(ChannelRole.MAP_PRED_RESET_CNT);
        boolean predictionActive = valueOn(latest(ChannelRole.MAP_PRED_ACTIVE));
        if (!activeChannel || !fallbackChannel || !effectiveChannel || !resetChannel) {
            predictionLiveCard.setValue("Missing prediction channel(s)", CardState.ERROR);
        } else {
            predictionLiveCard.setValue(predictionActive ? "ACTIVE" : "Idle",
                    predictionActive ? CardState.ACTIVE : CardState.OFF);
        }

        double realMap = latest(ChannelRole.MAP);
        double fallback = latest(ChannelRole.FALLBACK_MAP);
        double effective = latest(ChannelRole.EFFECTIVE_MAP);
        mapValuesCard.setValue(OverviewTextRenderer.mapValues(realMap, fallback, effective),
                predictionActive ? CardState.ACTIVE : CardState.INFO);

        double wallPw = latest(ChannelRole.WALL_WETTING_PW);
        double instantPw = latest(ChannelRole.INSTANT_PULSE_PW);
        transientFuelCard.setValue(OverviewTextRenderer.transientFuel(wallPw, instantPw),
                absGreater(wallPw, 0.0001) || absGreater(instantPw, 0.0001) ? CardState.ACTIVE : CardState.OFF);

        int predictionEvents = countPredictionEvents();
        int repeatedResets = countRepeatedResetEvents();
        eventProgressCard.setValue(OverviewTextRenderer.eventProgress(predictionEvents, repeatedResets),
                predictionEvents > 0 ? (repeatedResets > 0 ? CardState.WARNING : CardState.GOOD) : CardState.WAITING);

        int minimum = ((Number) mapMinimumSamples.getValue()).intValue();
        int covered = mapEstimateCollector.getCoveredCells(minimum);
        int total = projectSnapshot.getMapEstimateRpmBins().length * projectSnapshot.getMapEstimateTpsBins().length;
        mapCoverageCard.setValue(OverviewTextRenderer.mapCoverage(
                        mapEstimateCollector.getAcceptedSamples(), covered, total),
                covered > 0 ? CardState.GOOD : CardState.WAITING);

        List<EventSummary> reviewEvents;
        long reviewEventRevision;
        synchronized (lock) {
            reviewEvents = new ArrayList<EventSummary>(capturedEvents);
            reviewEventRevision = eventRevision;
        }
        SessionMonitor.Snapshot reviewSnapshot = sessionMonitor.snapshot();
        SessionReview review;
        if (cachedEventReview == null || cachedReviewEventRevision != reviewEventRevision) {
            cachedEventReview = SessionReview.build(reviewEvents, reviewSnapshot);
            cachedReviewEventRevision = reviewEventRevision;
            review = cachedEventReview;
        } else {
            review = cachedEventReview.withFullLoad(reviewSnapshot);
        }
        EnumMap<ChannelRole, String> selectedForHistory;
        synchronized (lock) {
            selectedForHistory = new EnumMap<ChannelRole, String>(channelNames);
        }
        contributionReviewCard.setValue(review.contributionCardText(),
                predictionEvents > 0 ? CardState.INFO : CardState.WAITING);
        lowRpmReviewCard.setValue(review.lowRpmCardText(),
                review.lowRpmNeedsReview() ? CardState.WARNING
                        : (predictionEvents > 0 ? CardState.GOOD : CardState.WAITING));
        fullLoadSafetyCard.setValue(review.fullLoadCardText(),
                review.fullLoadNeedsReview() ? CardState.WARNING
                        : (reviewSnapshot.hasData() ? CardState.GOOD : CardState.WAITING));
        uiPresenter.refreshSessionReview(review.toDisplayText());

        String action;
        CardState actionState;
        if (!mapMode) {
            action = "Use the TPS cycle-AE workflow";
            actionState = CardState.INFO;
        } else if (!activeChannel || !fallbackChannel || !effectiveChannel || !resetChannel) {
            action = "Resolve missing MAP Predict channels";
            actionState = CardState.ERROR;
        } else if (review.triggerSyncNeedsReview()) {
            action = "Review running trigger/sync loss before more transient testing";
            actionState = CardState.WARNING;
        } else if (review.sessionFaultNeedsReview()) {
            action = "Review running fault/cut activity";
            actionState = CardState.WARNING;
        } else if (review.fullLoadNeedsReview()) {
            action = "Review full-load safety before more WOT testing";
            actionState = CardState.WARNING;
        } else if (review.lowRpmNeedsReview()) {
            action = "Review MAP Estimate / blend below 2200 RPM";
            actionState = CardState.WARNING;
        } else if (predictionEvents < 4) {
            action = "Collect deliberate loaded tip-ins";
            actionState = CardState.INFO;
        } else if (covered < 4) {
            action = "Add stable RPM/TPS/MAP coverage";
            actionState = CardState.INFO;
        } else {
            action = "Review MAP Estimate and Blend Duration drafts";
            actionState = CardState.GOOD;
        }
        if (recommendationHistory.observe(action, review, reviewEvents, reviewSnapshot,
                selectedForHistory, System.currentTimeMillis())) {
            uiPresenter.refreshRecommendationHistory(recommendationHistory.toDisplayText());
        }
        String historyBadge = recommendationHistory.latestBadgeText();
        nextActionCard.setValue(historyBadge.length() > 0
                ? action + "\n" + historyBadge : action, actionState);
    }

    private int countRepeatedResetEvents() {
        int count = 0;
        synchronized (lock) {
            for (EventSummary event : capturedEvents) {
                if (event.getPredictionResetMetrics().hasRepeatedResets()) {
                    count++;
                }
            }
        }
        return count;
    }

    private String buildSessionModeText() {
        Set<ChannelRole> resolvedRoles;
        synchronized (lock) {
            resolvedRoles = new HashSet<ChannelRole>(channelNames.keySet());
        }
        return TechnicalDetailsRenderer.sessionMode(projectSnapshot, resolvedRoles);
    }

    private String buildSessionGuidanceText() {
        List<EventSummary> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<EventSummary>(capturedEvents);
        }
        if (projectSnapshot != null && projectSnapshot.isMapPredictWorkflow()) {
            int predictionEvents = countPredictionEvents(snapshot);
            int repeatedResetEvents = 0;
            int wallActiveEvents = 0;
            int resetDiscontinuities = 0;
            for (EventSummary event : snapshot) {
                CounterMath.Result resets = event.getPredictionResetMetrics();
                if (resets.hasRepeatedResets()) repeatedResetEvents++;
                if (resets.hasDiscontinuity()) resetDiscontinuities++;
                if (event.hasWallWettingContribution()) wallActiveEvents++;
            }
            int minimum = ((Number) mapMinimumSamples.getValue()).intValue();
            SessionReview review = SessionReview.build(snapshot, sessionMonitor.snapshot());
            String nextStep;
            if (review.triggerSyncNeedsReview()) {
                nextStep = "Running trigger/sync loss review has priority before further transient testing.";
            } else if (review.sessionFaultNeedsReview()) {
                nextStep = "Running fault/cut review has priority before further transient testing.";
            } else if (review.fullLoadNeedsReview()) {
                nextStep = "Full-load safety review has priority before further WOT testing.";
            } else if (review.lowRpmNeedsReview()) {
                nextStep = "Low-RPM events indicate the exercised MAP Estimate cells and Blend Duration below 2200 RPM need review.";
            } else if (predictionEvents == 0) {
                nextStep = "Collect deliberate loaded tip-ins and confirm fallbackMap/effectiveMap/isMapPredictionActive are subscribed.";
            } else if (repeatedResetEvents > 0) {
                nextStep = "Repeated resets deserve review for drift-style pedal stabs: keep MAP Estimate conservative and avoid an unnecessarily long Predictive Map Blend Duration.";
            } else {
                nextStep = "Use Copy MAP Estimate draft for table coverage and Copy Blend Duration draft after several prediction events across the RPM range.";
            }
            return TechnicalDetailsRenderer.mapPredictGuidance(predictionEvents, repeatedResetEvents,
                    resetDiscontinuities, wallActiveEvents, mapEstimateCollector.statusText(minimum), nextStep);
        }

        int proved = 0;
        int nearMiss = 0;
        for (EventSummary summary : snapshot) {
            if (summary.isTpsAeFuelProved()) proved++;
            else if (summary.isTriggerNearMiss()) nearMiss++;
        }
        return TechnicalDetailsRenderer.tpsCycleGuidance(snapshot.size(), proved, nearMiss);
    }

    private static int countPredictionEvents(List<EventSummary> events) {
        int count = 0;
        for (EventSummary event : events) {
            if (event.hasMapPrediction()) count++;
        }
        return count;
    }

    private int countPredictionEvents() {
        synchronized (lock) {
            return countPredictionEvents(new ArrayList<EventSummary>(capturedEvents));
        }
    }

    private void updateChannelTable() {
        EnumMap<ChannelRole, String> names;
        EnumMap<ChannelRole, Double> values;
        synchronized (lock) {
            names = new EnumMap<ChannelRole, String>(channelNames);
            values = new EnumMap<ChannelRole, Double>(latestValues);
        }

        LiveChannelTableRenderer.update(channelTableModel, names, values);
    }

    private String buildFuelPathStatusText() {
        EnumMap<ChannelRole, Double> values;
        synchronized (lock) {
            values = new EnumMap<ChannelRole, Double>(latestValues);
        }
        return buildFuelPathStatusText(projectSnapshot != null && projectSnapshot.isMapPredictWorkflow(), values);
    }

    static String buildFuelPathStatusText(boolean mapPredictWorkflow,
                                          EnumMap<ChannelRole, Double> values) {
        return TechnicalDetailsRenderer.fuelPathStatus(mapPredictWorkflow, values);
    }

    private static boolean valueOn(double value) {
        return Double.isFinite(value) && value >= 0.5;
    }

    private static boolean absGreater(double value, double threshold) {
        return Double.isFinite(value) && Math.abs(value) > threshold;
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
