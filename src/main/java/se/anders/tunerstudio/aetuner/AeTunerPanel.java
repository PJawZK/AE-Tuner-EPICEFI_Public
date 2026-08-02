package se.anders.tunerstudio.aetuner;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import com.efiAnalytics.plugin.ecu.ControllerException;
import com.efiAnalytics.plugin.ecu.OutputChannelClient;
import com.efiAnalytics.plugin.ecu.servers.OutputChannelServer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.DefaultCaret;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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
    private final JTextArea sessionReviewLabel = createStatusText("Session review: no data yet.", 4);
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
    private final JScrollPane channelScroll = new JScrollPane();
    private final DefaultTableModel channelTableModel = new DefaultTableModel(new Object[]{"Role", "Channel", "Value", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable channelTable = new JTable(channelTableModel);
    private final EventPlotPanel plotPanel = new EventPlotPanel();

    private final Object lock = new Object();
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
    private AeProjectSnapshot projectSnapshot;
    private String configurationName;
    private LiveSample previousSample;
    private long lastSampleNano;
    private long lastRateWindowNano;
    private long detectionArmedNano;
    private int samplesInWindow;
    private double sampleRateHz;
    private int acceptedEvents;
    private int tpsAeFuelProvedEvents;
    private int rejectedEvents;
    private boolean calibrationWasRunning;

    AeTunerPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        buildLayout();
        installActions();
        new Timer(500, event -> refreshUi()).start();
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
        return area;
    }

    void connectController(ControllerAccess controllerAccess) {
        this.controllerAccess = controllerAccess;
        reconnect();
    }

    void disconnectController() {
        if (outputChannelServer != null) {
            outputChannelServer.unsubscribe(this);
        }
        outputChannelServer = null;
        controllerAccess = null;
        synchronized (lock) {
            subscribedChannels.clear();
            latestValues.clear();
            channelNames.clear();
            availableOutputChannels.clear();
        }
        connectionLabel.setText("Disconnected");
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

        if (role == ChannelRole.TIME || role == ChannelRole.TPS || role == ChannelRole.RPM) {
            maybeRecordSample();
        }
    }

    private void buildLayout() {
        // Keep the complete control strip fixed above one full-window vertical
        // scrollbar. The scrollable sections have stable heights and both
        // scroll positions are preserved during the 250 ms live refresh.
        add(buildControlPanel(), BorderLayout.NORTH);

        configureChannelTable();
        channelTable.setFillsViewportHeight(true);
        channelTable.setAutoCreateRowSorter(false);
        channelTable.setFocusable(false);
        channelTable.setPreferredScrollableViewportSize(new Dimension(420, 250));
        channelScroll.setViewportView(channelTable);
        channelScroll.setBorder(BorderFactory.createTitledBorder("Resolved live channels"));
        channelScroll.setPreferredSize(new Dimension(450, 265));
        channelScroll.setMinimumSize(new Dimension(400, 180));
        channelScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        channelScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        channelScroll.getVerticalScrollBar().setUnitIncrement(18);
        channelScroll.getVerticalScrollBar().setBlockIncrement(90);

        latestEventText.setEditable(false);
        latestEventText.setLineWrap(true);
        latestEventText.setWrapStyleWord(true);
        latestEventText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        latestEventText.setFocusable(false);
        DefaultCaret notesCaret = (DefaultCaret) latestEventText.getCaret();
        notesCaret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        JScrollPane eventScroll = new JScrollPane(latestEventText);
        eventScroll.setBorder(BorderFactory.createEmptyBorder());
        eventScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        eventScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        eventScroll.getVerticalScrollBar().setUnitIncrement(16);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, channelScroll, plotPanel);
        split.setResizeWeight(0.0);
        split.setDividerLocation(455);
        split.setOneTouchExpandable(true);
        split.setContinuousLayout(true);
        split.setBorder(null);

        JPanel liveDataPanel = new JPanel(new BorderLayout());
        liveDataPanel.add(split, BorderLayout.CENTER);

        lowerTabs.addTab("Live channels & event preview", liveDataPanel);
        lowerTabs.addTab("Latest event / session notes", eventScroll);
        JScrollPane guidanceScroll = new JScrollPane(recommendationHistoryText);
        guidanceScroll.setBorder(BorderFactory.createEmptyBorder());
        guidanceScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        guidanceScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        guidanceScroll.getVerticalScrollBar().setUnitIncrement(16);
        lowerTabs.addTab("Session Guidance", guidanceScroll);
        lowerTabs.setToolTipTextAt(0, "Live channel values and the latest captured transient plot.");
        lowerTabs.setToolTipTextAt(1, "Detailed event text, draft reports, CSV status, and session notes.");
        lowerTabs.setToolTipTextAt(2, "Temporary recommendation transitions for this plugin session only.");
        lowerTabs.setFocusable(false);
        lowerTabs.setRequestFocusEnabled(false);
        lowerTabs.setPreferredSize(new Dimension(1000, 330));
        lowerTabs.setMinimumSize(new Dimension(650, 260));
        lowerTabs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 330));

        ViewportWidthPanel scrollContent = new ViewportWidthPanel();
        scrollContent.setLayout(new BoxLayout(scrollContent, BoxLayout.Y_AXIS));
        JComponent statusPanel = buildStatusPanel();
        statusPanel.setAlignmentX(LEFT_ALIGNMENT);
        lowerTabs.setAlignmentX(LEFT_ALIGNMENT);
        scrollContent.add(statusPanel);
        scrollContent.add(lowerTabs);

        mainScroll.setViewportView(scrollContent);
        mainScroll.setBorder(null);
        mainScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        mainScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        mainScroll.getVerticalScrollBar().setUnitIncrement(18);
        mainScroll.getVerticalScrollBar().setBlockIncrement(90);
        add(mainScroll, BorderLayout.CENTER);
    }

    private void configureChannelTable() {
        channelTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        channelTable.setRowHeight(19);
        channelTable.getColumnModel().getColumn(0).setPreferredWidth(125);
        channelTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        channelTable.getColumnModel().getColumn(2).setPreferredWidth(65);
        channelTable.getColumnModel().getColumn(3).setPreferredWidth(78);
    }

    private JComponent buildStatusPanel() {
        // Both tabs use exactly the same fixed height. Switching between
        // Overview and Technical details therefore cannot change the outer
        // scroll range or clamp the main scrollbar to a new position.
        JTabbedPane tabs = new StableTabbedPane();
        JPanel overview = buildOverviewPanel();
        JPanel technical = buildTechnicalStatusPanel();

        tabs.addTab("Overview", overview);
        tabs.addTab("Technical details", technical);
        tabs.setToolTipTextAt(0, "Clear summary of configuration, live state, progress, and next action.");
        tabs.setToolTipTextAt(1, "Compact project and diagnostic details. Uses the main window scrollbar.");
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

    private JPanel buildOverviewPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
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
        return panel;
    }

    private JPanel buildCardRow(String title, StatusCard... cards) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBorder(BorderFactory.createTitledBorder(title));
        JPanel cardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        for (StatusCard card : cards) {
            cardsPanel.add(card);
        }
        row.add(cardsPanel, BorderLayout.CENTER);
        row.setAlignmentX(LEFT_ALIGNMENT);
        int height = 34;
        for (StatusCard card : cards) {
            height = Math.max(height, card.getPreferredSize().height + 31);
        }
        setFixedHeight(row, height);
        return row;
    }

    private static void setFixedHeight(JComponent component, int height) {
        Dimension preferred = component.getPreferredSize();
        component.setPreferredSize(new Dimension(Math.max(1, preferred.width), height));
        component.setMinimumSize(new Dimension(1, height));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }

    private JPanel buildTechnicalStatusPanel() {
        // Long sections use the full tab width. Only the two genuinely short
        // sections share the first row. This keeps text left-aligned and gives
        // wrapping enough width without oversized empty frames.
        JPanel panel = new JPanel(new GridBagLayout());
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

        // Keep surplus space outside the framed sections rather than stretching
        // their borders. The tab itself has the same fixed height as Overview.
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JPanel(), gbc);

        panel.setPreferredSize(new Dimension(1000, 455));
        panel.setMinimumSize(new Dimension(700, 455));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 455));
        return panel;
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
            Dimension preferred = component.getPreferredSize();
            component.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
            section.add(component);
        }
        setFixedHeight(section, height);
        return section;
    }

    private JPanel buildControlPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel rowOne = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        rowOne.setAlignmentX(LEFT_ALIGNMENT);
        rowOne.add(reconnectButton);
        rowOne.add(readProjectButton);
        rowOne.add(saveCsvButton);
        rowOne.add(suggestTableButton);
        rowOne.add(suggestMapEstimateButton);
        rowOne.add(suggestBlendButton);
        rowOne.add(sessionReviewButton);
        rowOne.add(resetButton);

        JPanel rowTwo = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        rowTwo.setAlignmentX(LEFT_ALIGNMENT);
        rowTwo.add(new JLabel("Manual TPSdot threshold %/s:"));
        rowTwo.add(thresholdField);
        rowTwo.add(new JLabel("Calibration seconds:"));
        rowTwo.add(calibrationSeconds);
        rowTwo.add(calibrateButton);
        rowTwo.add(applyCalibrationButton);

        JPanel rowThree = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        rowThree.setAlignmentX(LEFT_ALIGNMENT);
        rowThree.add(new JLabel("MAP draft minimum samples/cell:"));
        rowThree.add(mapMinimumSamples);
        rowThree.add(new JLabel("Turbo MAP cap kPa (TPS >=33.5%):"));
        rowThree.add(mapCapField);

        panel.add(rowOne);
        panel.add(rowTwo);
        panel.add(rowThree);
        return panel;
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

    private void reconnect() {
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
            previousSample = null;
            eventDetector.resetTracking();
            detectionArmedNano = System.nanoTime() + STARTUP_IGNORE_NS;
            connectionLabel.setText("TunerStudio project: " + configurationName + " | subscribed " + subscribedChannels.size() + " live channel(s); ECU online state not verified by plugin");
            readProjectData();
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
        calibration.start(seconds.doubleValue());
        calibrationWasRunning = true;
        eventDetector.resetTracking();
        detectionArmedNano = System.nanoTime() + STARTUP_IGNORE_NS;
        calibrationLabel.setText("TPS calibration running: hold idle/steady pedal, do not touch throttle. Event capture is paused.");
    }

    private void applyRecommendedCalibration() {
        TpsNoiseCalibration.Result result = calibration.getLastResult();
        if (result != null) {
            thresholdField.setText(F3.format(result.getRecommendedThreshold()));
        }
    }

    private void resetSession() {
        synchronized (lock) {
            capturedEvents.clear();
            latestValues.clear();
        }
        eventDetector.resetSession();
        mapEstimateCollector.clear();
        mapEstimateCollector.configure(projectSnapshot);
        sessionMonitor.reset();
        recommendationHistory.reset();
        recommendationHistoryText.setText(recommendationHistory.toDisplayText());
        recommendationHistoryText.setCaretPosition(0);
        previousSample = null;
        detectionArmedNano = System.nanoTime() + STARTUP_IGNORE_NS;
        acceptedEvents = 0;
        tpsAeFuelProvedEvents = 0;
        rejectedEvents = 0;
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

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("ae-tuner-epicefi-events-" + AeTunerPlugin.VERSION + "-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".csv"));
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();
        try {
            writeEventsCsv(file, snapshot);
            setNotesText("Saved " + snapshot.size() + " event(s) to " + file.getAbsolutePath(), true);
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
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(suggestion.getCopyPasteBlock()), null);
            } catch (IllegalStateException ex) {
                latestEventText.append("\n\nCould not copy to clipboard: " + ex.getMessage());
            }
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
    List<EventSummary> snapshot;
    EnumMap<ChannelRole, String> selectedChannels;
    EnumMap<ChannelRole, Double> latestChannelValues;
    synchronized (lock) {
        snapshot = new ArrayList<EventSummary>(capturedEvents);
        selectedChannels = new EnumMap<ChannelRole, String>(channelNames);
        latestChannelValues = new EnumMap<ChannelRole, Double>(latestValues);
    }
        int minimum = ((Number) mapMinimumSamples.getValue()).intValue();
        double cap = parseMapCap();
        MapEstimateSuggestion mapSuggestion = MapEstimateSuggestion.build(
                projectSnapshot, mapEstimateCollector, minimum, cap);
        MapBlendSuggestion blendSuggestion = MapBlendSuggestion.build(projectSnapshot, snapshot);
        SessionReview review = SessionReview.build(snapshot, sessionMonitor.snapshot());

        StringBuilder text = new StringBuilder();
        text.append("AE Tuner (EPICEFI) MAP Predict report\n")
                .append("Plugin version: ").append(AeTunerPlugin.VERSION).append("\n")
                .append("Created: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n")
                .append("Project: ").append(configurationName == null ? "unknown" : configurationName).append("\n")
                .append("Captured events: ").append(snapshot.size()).append("\n")
                .append("Live sample rate at save: ")
                .append(sampleRateHz > 0.0 ? F1.format(sampleRateHz) + " Hz" : "n/a").append("\n")
                .append("MAP draft minimum samples/cell: ").append(minimum).append("\n")
                .append("Turbo MAP cap: ").append(F1.format(cap)).append(" kPa from 33.5% TPS\n")
                .append("Read-only report: no ECU values were changed.\n")
                .append("\n============================================================\n")
                .append("MAP ESTIMATE DRAFT\n")
                .append("============================================================\n")
                .append(mapSuggestion.getDisplayText()).append("\n\n")
                .append("TunerStudio copy/paste block (descending TPS row order):\n")
                .append(mapSuggestion.isAvailable() ? mapSuggestion.getCopyPasteBlock() : "Unavailable").append("\n")
                .append("\n============================================================\n")
                .append("PREDICTIVE MAP BLEND DURATION DRAFT\n")
                .append("============================================================\n")
                .append(blendSuggestion.getDisplayText()).append("\n\n")
                .append("TunerStudio copy/paste block (ascending RPM order):\n")
                .append(blendSuggestion.isAvailable() ? blendSuggestion.getCopyPasteBlock() : "Unavailable").append("\n")
                .append("\n============================================================\n")
                .append("CRITICAL OUTPUT-CHANNEL RESOLUTION\n")
        .append("============================================================\n")
        .append(ChannelResolutionEvidence.build(selectedChannels, latestChannelValues)).append("\n")
        .append("============================================================\n")
                .append("SESSION REVIEW\n")
                .append("============================================================\n")
                .append(review.toDisplayText()).append("\n");

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("ae-tuner-map-predict-report-" + AeTunerPlugin.VERSION + "-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".txt"));
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            try {
                writer.write(text.toString());
            } finally {
                writer.close();
            }
            setNotesText("Saved combined MAP Estimate, Blend Duration, and session review report to\n"
                    + file.getAbsolutePath(), true);
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
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        } catch (IllegalStateException ex) {
            latestEventText.append("\n\nCould not copy to clipboard: " + ex.getMessage());
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

    private static void writeEventsCsv(File file, List<EventSummary> events) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        try {
            writer.write(events.get(0).toCsvHeader());
            writer.newLine();
            for (EventSummary summary : events) {
                for (String row : summary.toCsvRows()) {
                    writer.write(row);
                    writer.newLine();
                }
            }
        } finally {
            writer.close();
        }
    }

    private void maybeRecordSample() {
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
        double manual = parseThreshold();
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

    private double parseThreshold() {
        try {
            return Double.parseDouble(thresholdField.getText().trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    private void refreshUi() {
        setLabelTextIfChanged(sampleRateLabel, "Sample rate: " + (sampleRateHz > 0.0 ? F1.format(sampleRateHz) + " Hz" : "n/a"));
        refreshOverview();
        if (projectSnapshot != null && projectSnapshot.isMapPredictWorkflow()) {
            int predictionEvents = countPredictionEvents();
            setTextIfChanged(eventCountLabel, "Events: " + predictionEvents + " MAP Predict / "
                    + (acceptedEvents - predictionEvents) + " other diagnostic / " + rejectedEvents + " rejected");
        } else {
            setTextIfChanged(eventCountLabel, "Events: " + tpsAeFuelProvedEvents + " TPS AE fuel proved / "
                    + (acceptedEvents - tpsAeFuelProvedEvents) + " diagnostic / " + rejectedEvents + " rejected");
        }
        setTextIfChanged(fuelPathStatusLabel, buildFuelPathStatusText());
        setTextIfChanged(sessionModeLabel, buildSessionModeText());
        setTextIfChanged(guidanceLabel, buildSessionGuidanceText());
        setTextIfChanged(mapCollectionLabel, mapEstimateCollector.statusText(((Number) mapMinimumSamples.getValue()).intValue()));

        if (calibration.isRunning()) {
            setTextIfChanged(calibrationLabel, "TPS calibration running: " + F1.format(calibration.secondsRemaining()) + " s remaining. Event capture paused.");
        } else if (detectionArmedNano > 0L && System.nanoTime() < detectionArmedNano) {
            double seconds = (detectionArmedNano - System.nanoTime()) / 1000000000.0;
            setTextIfChanged(calibrationLabel, "TPS calibration: event detection arming in " + F1.format(Math.max(0.0, seconds)) + " s");
        } else {
            TpsNoiseCalibration.Result result = calibration.getLastResult();
            if (result != null) {
                setTextIfChanged(calibrationLabel, "TPS calibration: " + result.toDisplayText());
            }
        }

        updateChannelTable();
    }

    private void refreshOverview() {
        refreshCalibrationOverview();
        int subscribed;
        synchronized (lock) {
            subscribed = subscribedChannels.size();
        }
        setLabelTextIfChanged(overviewConnectionLabel, "TunerStudio project: "
                + (configurationName == null ? "not connected" : configurationName)
                + "  •  " + subscribed + " live channels");
        setLabelTextIfChanged(overviewRateLabel, "Sample rate: " + (sampleRateHz > 0.0 ? F1.format(sampleRateHz) + " Hz" : "n/a"));

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
        String stage;
        if (mapMode && !projectSnapshot.isWallWettingEnabled()) {
            stage = "Stage 1: MAP Predict";
        } else if (mapMode && projectSnapshot.isWallWettingEnabled() && !projectSnapshot.isExtraShotEnabled()) {
            stage = "Stage 2: Wall Wetting";
        } else if (mapMode && projectSnapshot.isWallWettingEnabled() && projectSnapshot.isExtraShotEnabled()) {
            stage = "Stage 3: Instant Fuel";
        } else {
            stage = "Legacy TPS cycle AE";
        }
        workflowCard.setValue(stage, CardState.GOOD);

        if (mapMode) {
            tpsCycleCard.setValue(projectSnapshot.isTpsAeEnabled() ? "ON — unexpected" : "OFF — correct",
                    projectSnapshot.isTpsAeEnabled() ? CardState.WARNING : CardState.GOOD);
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
        String mapText = "Real: " + finiteOrNa(realMap, F1) + " kPa"
                + "\nEstimate: " + finiteOrNa(fallback, F1) + " kPa"
                + "\nEffective: " + finiteOrNa(effective, F1) + " kPa";
        if (Double.isFinite(realMap) && Double.isFinite(effective)) {
            mapText += "\nGap: " + F1.format(effective - realMap) + " kPa";
        } else {
            mapText += "\nGap: n/a";
        }
        mapValuesCard.setValue(mapText, predictionActive ? CardState.ACTIVE : CardState.INFO);

        double wallPw = latest(ChannelRole.WALL_WETTING_PW);
        double instantPw = latest(ChannelRole.INSTANT_PULSE_PW);
        transientFuelCard.setValue("Wall: " + finiteOrNa(wallPw, F3) + " ms"
                        + "\nInstant: " + finiteOrNa(instantPw, F3) + " ms",
                absGreater(wallPw, 0.0001) || absGreater(instantPw, 0.0001) ? CardState.ACTIVE : CardState.OFF);

        int predictionEvents = countPredictionEvents();
        int repeatedResets = countRepeatedResetEvents();
        eventProgressCard.setValue(predictionEvents + " prediction event(s)"
                        + "  •  " + repeatedResets + " repeated-reset event(s)",
                predictionEvents > 0 ? (repeatedResets > 0 ? CardState.WARNING : CardState.GOOD) : CardState.WAITING);

        int minimum = ((Number) mapMinimumSamples.getValue()).intValue();
        int covered = mapEstimateCollector.getCoveredCells(minimum);
        int total = projectSnapshot.getMapEstimateRpmBins().length * projectSnapshot.getMapEstimateTpsBins().length;
        mapCoverageCard.setValue(mapEstimateCollector.getAcceptedSamples() + " stable samples"
                        + "  •  " + covered + "/" + total + " cells ready",
                covered > 0 ? CardState.GOOD : CardState.WAITING);

        List<EventSummary> reviewEvents;
        synchronized (lock) {
            reviewEvents = new ArrayList<EventSummary>(capturedEvents);
        }
        SessionMonitor.Snapshot reviewSnapshot = sessionMonitor.snapshot();
        SessionReview review = SessionReview.build(reviewEvents, reviewSnapshot);
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
        setTextIfChanged(sessionReviewLabel, review.toDisplayText());

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
            setTextIfChanged(recommendationHistoryText, recommendationHistory.toDisplayText());
            recommendationHistoryText.setCaretPosition(0);
        }
        String historyBadge = recommendationHistory.latestBadgeText();
        nextActionCard.setValue(historyBadge.length() > 0
                ? action + "\n" + historyBadge : action, actionState);
    }

    private void refreshCalibrationOverview() {
        if (calibration.isRunning()) {
            calibrationCard.setValue("RUNNING  •  " + F1.format(calibration.secondsRemaining())
                    + " s remaining  •  Do not touch throttle", CardState.ACTIVE);
            return;
        }
        if (detectionArmedNano > 0L && System.nanoTime() < detectionArmedNano) {
            double seconds = Math.max(0.0, (detectionArmedNano - System.nanoTime()) / 1000000000.0);
            calibrationCard.setValue("Arming event detection  •  " + F1.format(seconds)
                    + " s remaining", CardState.INFO);
            return;
        }
        TpsNoiseCalibration.Result result = calibration.getLastResult();
        if (result != null) {
            calibrationCard.setValue("Complete  •  Recommended "
                    + F3.format(result.getRecommendedThreshold()) + " %/s", CardState.GOOD);
        } else {
            calibrationCard.setValue("Not run  •  Press Start TPS noise calibration", CardState.WAITING);
        }
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

    private static String finiteOrNa(double value, DecimalFormat format) {
        return Double.isFinite(value) ? format.format(value) : "n/a";
    }

    private String buildSessionModeText() {
        if (projectSnapshot == null) {
            return "Session mode: project data not available yet. Press Read AE project data.";
        }
        boolean fallback = channelNames.containsKey(ChannelRole.FALLBACK_MAP);
        boolean effective = channelNames.containsKey(ChannelRole.EFFECTIVE_MAP);
        boolean active = channelNames.containsKey(ChannelRole.MAP_PRED_ACTIVE);
        boolean reset = channelNames.containsKey(ChannelRole.MAP_PRED_RESET_CNT);
        boolean wallPw = channelNames.containsKey(ChannelRole.WALL_WETTING_PW);

        StringBuilder text = new StringBuilder("Session mode: ");
        text.append(projectSnapshot.expectedSessionModeText()).append(". ");
        if (projectSnapshot.isMapPredictWorkflow()) {
            text.append("Current tuning stage: MAP Predict. TPS cycle multiplier-table suggestions are disabled. ");
            text.append("Prediction channels: fallbackMap ").append(fallback ? "OK" : "MISSING")
                    .append(", effectiveMap ").append(effective ? "OK" : "MISSING")
                    .append(", isMapPredictionActive ").append(active ? "OK" : "MISSING")
                    .append(", predTimerResetCnt ").append(reset ? "OK" : "MISSING").append(". ");
            if (projectSnapshot.isWallWettingEnabled()) {
                text.append("Wall Wetting is enabled; fuel wallwetting injection time ")
                        .append(wallPw ? "is available for later contribution analysis" : "is MISSING").append(". ");
            }
            if (projectSnapshot.isExtraShotEnabled()) {
                text.append("Instant Fuel Pulse is ON; for the planned staged workflow, tune MAP Predict + Wall Wetting before evaluating it. ");
            } else {
                text.append("Instant Fuel Pulse is OFF, which matches the MAP Predict-first tuning stage. ");
            }
        } else {
            text.append("Legacy TPS cycle-AE analysis remains available. ");
        }
        if (projectSnapshot.isDynamicThresholdEnabled()) {
            text.append("Dynamic TPS AE threshold is ON");
            if (projectSnapshot.isDynamicThresholdAverageStatic()) {
                text.append(" and averaged with TPS AE Rate of change vs RPM");
            }
            text.append(".");
        }
        return text.toString();
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
            StringBuilder text = new StringBuilder("MAP Predict guidance: ");
            text.append(predictionEvents).append(" captured prediction event(s), ")
                    .append(repeatedResetEvents).append(" event(s) with repeated timer resets, ")
                    .append(resetDiscontinuities).append(" reset-counter discontinuity event(s), ")
                    .append(wallActiveEvents).append(" event(s) with visible Wall Wetting contribution. ")
                    .append(mapEstimateCollector.statusText(minimum)).append(" ");
            if (review.triggerSyncNeedsReview()) {
                text.append("Running trigger/sync loss review has priority before further transient testing.");
            } else if (review.sessionFaultNeedsReview()) {
                text.append("Running fault/cut review has priority before further transient testing.");
            } else if (review.fullLoadNeedsReview()) {
                text.append("Full-load safety review has priority before further WOT testing.");
            } else if (review.lowRpmNeedsReview()) {
                text.append("Low-RPM events indicate the exercised MAP Estimate cells and Blend Duration below 2200 RPM need review.");
            } else if (predictionEvents == 0) {
                text.append("Collect deliberate loaded tip-ins and confirm fallbackMap/effectiveMap/isMapPredictionActive are subscribed.");
            } else if (repeatedResetEvents > 0) {
                text.append("Repeated resets deserve review for drift-style pedal stabs: keep MAP Estimate conservative and avoid an unnecessarily long Predictive Map Blend Duration.");
            } else {
                text.append("Use Copy MAP Estimate draft for table coverage and Copy Blend Duration draft after several prediction events across the RPM range.");
            }
            return text.toString();
        }

        if (snapshot.isEmpty()) {
            return "TPS cycle-AE guidance: collect TPS AE fuel-proved events first. The plugin is read-only and will not change the ECU.";
        }
        int proved = 0;
        int nearMiss = 0;
        for (EventSummary summary : snapshot) {
            if (summary.isTpsAeFuelProved()) proved++;
            else if (summary.isTriggerNearMiss()) nearMiss++;
        }
        return "TPS cycle-AE guidance: " + proved + " fuel-proved event(s), " + nearMiss
                + " trigger near miss(es). Use the TPS AE table draft only when this is the intended strategy.";
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

        ChannelRole[] roles = ChannelRole.values();
        if (channelTableModel.getRowCount() != roles.length) {
            channelTableModel.setRowCount(0);
            for (ChannelRole role : roles) {
                channelTableModel.addRow(new Object[]{role.getLabel(), "", "", ""});
            }
        }

        // Update existing rows in place. Clearing and rebuilding the table on
        // every 250 ms refresh caused avoidable revalidation/layout churn.
        for (int row = 0; row < roles.length; row++) {
            ChannelRole role = roles[row];
            String channel = names.get(role);
            Double value = values.get(role);
            setTableValueIfChanged(row, 0, role.getLabel());
            setTableValueIfChanged(row, 1, channel == null ? "not found" : channel);
            setTableValueIfChanged(row, 2, value == null ? "" : formatValue(role, value.doubleValue()));
            setTableValueIfChanged(row, 3, channel == null ? "missing" : "subscribed");
        }
    }

    private String formatValue(ChannelRole role, double value) {
        if (role == ChannelRole.LAMBDA || role == ChannelRole.TARGET_LAMBDA) {
            return F3.format(value);
        }
        if (role == ChannelRole.PW || role == ChannelRole.AE_ADD_MS || role == ChannelRole.WALL_WETTING_PW || role == ChannelRole.INSTANT_PULSE_PW) {
            return F3.format(value);
        }
        return F2.format(value);
    }

    private String buildFuelPathStatusText() {
        if (projectSnapshot != null && projectSnapshot.isMapPredictWorkflow()) {
            double active = latest(ChannelRole.MAP_PRED_ACTIVE);
            double realMap = latest(ChannelRole.MAP);
            double fallback = latest(ChannelRole.FALLBACK_MAP);
            double effective = latest(ChannelRole.EFFECTIVE_MAP);
            double wallPw = latest(ChannelRole.WALL_WETTING_PW);
            double instant = latest(ChannelRole.INSTANT_PULSE_PW);
            StringBuilder text = new StringBuilder("Transient status: MAP Predict ")
                    .append(valueOn(active) ? "ACTIVE" : "inactive")
                    .append(" | MAP ").append(F2.format(zeroIfNaN(realMap)))
                    .append(" | fallbackMap ").append(Double.isFinite(fallback) ? F2.format(fallback) : "n/a")
                    .append(" | effectiveMap ").append(Double.isFinite(effective) ? F2.format(effective) : "n/a");
            if (Double.isFinite(realMap) && Double.isFinite(effective)) {
                text.append(" | effective-real gap ").append(F2.format(effective - realMap)).append(" kPa");
            }
            text.append(" | fuel wallwetting injection time ")
                    .append(Double.isFinite(wallPw) ? F3.format(wallPw) + " ms" : "n/a")
                    .append(" | aeInstantPulsePw ")
                    .append(Double.isFinite(instant) ? F3.format(instant) + " ms" : "n/a");
            return text.toString();
        }

        double aeActive = latest(ChannelRole.AE_ABOVE_THRESHOLD);
        double aeMs = latest(ChannelRole.AE_ADD_MS);
        double extraFuel = latest(ChannelRole.EXTRA_FUEL);
        double wall = latest(ChannelRole.WALL_CORRECTION);
        double wallPw = latest(ChannelRole.WALL_WETTING_PW);
        double extraShot = latest(ChannelRole.AE_EXTRA_SHOT);
        double dfco = latest(ChannelRole.DFCO);
        boolean tpsFuelVisible = absGreater(aeMs, 0.002) || absGreater(extraFuel, 0.0001);
        boolean wallVisible = absGreater(wall, 0.0001) || absGreater(wallPw, 0.0001);
        boolean aeState = valueOn(aeActive);
        boolean extraShotState = valueOn(extraShot);
        if (tpsFuelVisible) {
            return "Fuel-path status: TPS AE fuel visible now (Fuel: TPS AE add fuel ms " + F3.format(zeroIfNaN(aeMs))
                    + ", Fuel: TPS extraFuel " + F3.format(zeroIfNaN(extraFuel)) + ")";
        }
        if (aeState) {
            return "Fuel-path status: Fuel: TPS AE Active is on, but TPS AE fuel is not visible right now"
                    + suffixOtherPaths(wallVisible, extraShotState, dfco);
        }
        if (wallVisible || extraShotState || valueOn(dfco)) {
            return "Fuel-path status: TPS AE inactive; other path/state visible" + suffixOtherPaths(wallVisible, extraShotState, dfco);
        }
        return "Fuel-path status: no TPS AE fuel visible in current live sample";
    }

    private static String suffixOtherPaths(boolean wallVisible, boolean extraShotState, double dfco) {
        String text = "";
        if (wallVisible) {
            text += " | Fuel: wall correction / fuel wallwetting injection time active";
        }
        if (extraShotState) {
            text += " | Fuel: TPSAE ExtraShot active";
        }
        if (valueOn(dfco)) {
            text += " | dfcoActive";
        }
        return text;
    }

    private static boolean absGreater(double value, double threshold) {
        return Double.isFinite(value) && Math.abs(value) > threshold;
    }

    private static boolean valueOn(double value) {
        return Double.isFinite(value) && value >= 0.5;
    }

    private static double zeroIfNaN(double value) {
        return Double.isFinite(value) ? value : 0.0;
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

    private static void setLabelTextIfChanged(JLabel label, String text) {
        if (!text.equals(label.getText())) {
            label.setText(text);
        }
    }

    private static void setTextIfChanged(JTextArea area, String text) {
        String normalized = text == null ? "" : text;
        if (!normalized.equals(area.getText())) {
            area.setText(normalized);
        }
    }

    private void setTableValueIfChanged(int row, int column, Object value) {
        Object old = channelTableModel.getValueAt(row, column);
        if (old == null ? value != null : !old.equals(value)) {
            channelTableModel.setValueAt(value, row, column);
        }
    }

    /**
     * A tabbed pane embedded in the main scroll page must not ask its parent
     * JScrollPane to scroll the selected tab into view. Swing's default
     * scrollRectToVisible call was the remaining source of the visible jump
     * when clicking Overview/Technical details.
     */
    private static final class StableTabbedPane extends JTabbedPane {
        @Override
        public void scrollRectToVisible(Rectangle aRect) {
            // Intentionally ignored. The user owns the main scrollbar.
        }
    }

    private static final class ViewportWidthPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(90, visibleRect.height - 36);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private enum CardState { GOOD, ACTIVE, INFO, OFF, WAITING, WARNING, ERROR }

    private static final class StatusCard extends JPanel {
        private final JLabel value = new JLabel();
        private String lastText;
        private CardState lastState;

        StatusCard(String title, int width, int height) {
            super(new BorderLayout(4, 3));
            Dimension fixedSize = new Dimension(width, height);
            setPreferredSize(fixedSize);
            setMinimumSize(fixedSize);
            setMaximumSize(fixedSize);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(180, 180, 180)),
                    BorderFactory.createEmptyBorder(5, 7, 5, 7)));
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 11f));
            value.setFocusable(false);
            value.setVerticalAlignment(JLabel.TOP);
            value.setHorizontalAlignment(JLabel.LEFT);
            value.setFont(value.getFont().deriveFont(Font.BOLD, 12f));
            add(titleLabel, BorderLayout.NORTH);
            add(value, BorderLayout.CENTER);
            setValue("Waiting", CardState.WAITING);
        }

        void setValueFontSize(float size) {
            value.setFont(value.getFont().deriveFont(Font.BOLD, size));
        }

        void setValue(String text, CardState state) {
            String normalized = text == null ? "" : text.replace("  •  ", "\n");
            if (normalized.equals(lastText) && state == lastState) {
                return;
            }
            lastText = normalized;
            lastState = state;
            value.setText(toHtml(normalized));
            Color background;
            Color foreground = Color.BLACK;
            switch (state) {
                case GOOD: background = new Color(220, 242, 220); break;
                case ACTIVE: background = new Color(196, 235, 255); break;
                case INFO: background = new Color(226, 235, 248); break;
                case OFF: background = new Color(236, 236, 236); break;
                case WARNING: background = new Color(255, 238, 190); break;
                case ERROR: background = new Color(255, 210, 210); break;
                default: background = new Color(245, 245, 245); break;
            }
            if (!background.equals(getBackground())) {
                setBackground(background);
            }
            value.setForeground(foreground);
            setOpaque(true);
            repaint();
        }

        private static String toHtml(String text) {
            String escaped = text.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\n", "<br>");
            return "<html>" + escaped + "</html>";
        }
    }

    private static final class EventPlotPanel extends JPanel {
        private EventSummary event;

        EventPlotPanel() {
            setBorder(BorderFactory.createTitledBorder("Latest transient event preview"));
            setPreferredSize(new Dimension(520, 260));
            setBackground(Color.WHITE);
        }

        void setEvent(EventSummary event) {
            this.event = event;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
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

                g.setColor(Color.LIGHT_GRAY);
                g.drawRect(left, top, right - left, bottom - top);
                g.setColor(Color.DARK_GRAY);
                g.drawString("TPS / MAP / λ preview", left + 6, top + 15);

                if (event == null || event.getSamples().size() < 2) {
                    g.drawString("Waiting for an AE event...", left + 20, top + 45);
                    return;
                }

                List<LiveSample> samples = event.getSamples();
                double start = samples.get(0).getSeconds();
                double end = samples.get(samples.size() - 1).getSeconds();
                if (end <= start) {
                    end = start + 1.0;
                }

                drawTrace(g, samples, ChannelRole.TPS, start, end, left, right, top, bottom, 0.0, 100.0);
                drawTrace(g, samples, ChannelRole.MAP, start, end, left, right, top, bottom, 0.0, 250.0);
                drawTrace(g, samples, ChannelRole.LAMBDA, start, end, left, right, top, bottom, 0.65, 1.35);

                g.setColor(Color.BLACK);
                g.drawString("TPS 0-100%, MAP 0-250 kPa, λ 0.65-1.35", left, bottom + 18);
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
                               double max) {
            int lastX = -1;
            int lastY = -1;
            for (LiveSample sample : samples) {
                double value = sample.get(role);
                if (!Double.isFinite(value)) {
                    continue;
                }
                double xRatio = (sample.getSeconds() - start) / (end - start);
                double yRatio = (value - min) / (max - min);
                xRatio = Math.max(0.0, Math.min(1.0, xRatio));
                yRatio = Math.max(0.0, Math.min(1.0, yRatio));
                int x = left + (int) Math.round(xRatio * (right - left));
                int y = bottom - (int) Math.round(yRatio * (bottom - top));
                if (lastX >= 0) {
                    g.drawLine(lastX, lastY, x, y);
                }
                lastX = x;
                lastY = y;
            }
        }
    }
}
