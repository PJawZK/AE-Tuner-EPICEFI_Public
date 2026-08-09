package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;

/**
 * Pure Swing composition for the passive AE Tuner surface.
 *
 * AeTunerPanel deliberately remains the owner of every component and listener;
 * this collaborator only arranges those existing objects. That keeps the host
 * lifecycle and synthetic integration ownership contract unchanged while the
 * large layout implementation no longer lives beside sampling/runtime logic.
 */
final class PassivePanelLayout {
    private PassivePanelLayout() { }

    static final class Controls {
        final JButton reconnect;
        final JButton readProject;
        final JButton saveCsv;
        final JButton suggestTable;
        final JButton suggestMapEstimate;
        final JButton suggestBlend;
        final JButton sessionReview;
        final JButton reset;
        final JTextField threshold;
        final JSpinner calibrationSeconds;
        final JButton calibrate;
        final JButton applyCalibration;
        final JSpinner mapMinimumSamples;
        final JTextField mapCap;

        Controls(JButton reconnect,
                 JButton readProject,
                 JButton saveCsv,
                 JButton suggestTable,
                 JButton suggestMapEstimate,
                 JButton suggestBlend,
                 JButton sessionReview,
                 JButton reset,
                 JTextField threshold,
                 JSpinner calibrationSeconds,
                 JButton calibrate,
                 JButton applyCalibration,
                 JSpinner mapMinimumSamples,
                 JTextField mapCap) {
            this.reconnect = reconnect;
            this.readProject = readProject;
            this.saveCsv = saveCsv;
            this.suggestTable = suggestTable;
            this.suggestMapEstimate = suggestMapEstimate;
            this.suggestBlend = suggestBlend;
            this.sessionReview = sessionReview;
            this.reset = reset;
            this.threshold = threshold;
            this.calibrationSeconds = calibrationSeconds;
            this.calibrate = calibrate;
            this.applyCalibration = applyCalibration;
            this.mapMinimumSamples = mapMinimumSamples;
            this.mapCap = mapCap;
        }
    }

    static final class Content {
        final JScrollPane mainScroll;
        final JScrollPane channelScroll;
        final JTable channelTable;
        final JTextArea latestEventText;
        final JTextArea recommendationHistoryText;
        final JTabbedPane lowerTabs;
        final EventPlotPanel plotPanel;
        final JScrollPane overviewScroll;
        final JScrollPane technicalScroll;

        Content(JScrollPane mainScroll,
                JScrollPane channelScroll,
                JTable channelTable,
                JTextArea latestEventText,
                JTextArea recommendationHistoryText,
                JTabbedPane lowerTabs,
                EventPlotPanel plotPanel,
                JScrollPane overviewScroll,
                JScrollPane technicalScroll) {
            this.mainScroll = mainScroll;
            this.channelScroll = channelScroll;
            this.channelTable = channelTable;
            this.latestEventText = latestEventText;
            this.recommendationHistoryText = recommendationHistoryText;
            this.lowerTabs = lowerTabs;
            this.plotPanel = plotPanel;
            this.overviewScroll = overviewScroll;
            this.technicalScroll = technicalScroll;
        }
    }

    static final class Overview {
        final JLabel connection;
        final JLabel rate;
        final StatusCard workflow;
        final StatusCard tpsCycle;
        final StatusCard mapPredict;
        final StatusCard wallWetting;
        final StatusCard instantFuel;
        final StatusCard detector;
        final StatusCard predictionLive;
        final StatusCard mapValues;
        final StatusCard transientFuel;
        final StatusCard calibration;
        final StatusCard eventProgress;
        final StatusCard mapCoverage;
        final StatusCard nextAction;
        final StatusCard contributionReview;
        final StatusCard lowRpmReview;
        final StatusCard fullLoadSafety;

        Overview(JLabel connection,
                 JLabel rate,
                 StatusCard workflow,
                 StatusCard tpsCycle,
                 StatusCard mapPredict,
                 StatusCard wallWetting,
                 StatusCard instantFuel,
                 StatusCard detector,
                 StatusCard predictionLive,
                 StatusCard mapValues,
                 StatusCard transientFuel,
                 StatusCard calibration,
                 StatusCard eventProgress,
                 StatusCard mapCoverage,
                 StatusCard nextAction,
                 StatusCard contributionReview,
                 StatusCard lowRpmReview,
                 StatusCard fullLoadSafety) {
            this.connection = connection;
            this.rate = rate;
            this.workflow = workflow;
            this.tpsCycle = tpsCycle;
            this.mapPredict = mapPredict;
            this.wallWetting = wallWetting;
            this.instantFuel = instantFuel;
            this.detector = detector;
            this.predictionLive = predictionLive;
            this.mapValues = mapValues;
            this.transientFuel = transientFuel;
            this.calibration = calibration;
            this.eventProgress = eventProgress;
            this.mapCoverage = mapCoverage;
            this.nextAction = nextAction;
            this.contributionReview = contributionReview;
            this.lowRpmReview = lowRpmReview;
            this.fullLoadSafety = fullLoadSafety;
        }
    }

    static final class Technical {
        final JTextArea connection;
        final JLabel sampleRate;
        final JTextArea calibration;
        final JTextArea eventCount;
        final JTextArea snapshot;
        final JTextArea fuelPathStatus;
        final JTextArea sessionMode;
        final JTextArea guidance;
        final JTextArea mapCollection;
        final JTextArea sessionReview;

        Technical(JTextArea connection,
                  JLabel sampleRate,
                  JTextArea calibration,
                  JTextArea eventCount,
                  JTextArea snapshot,
                  JTextArea fuelPathStatus,
                  JTextArea sessionMode,
                  JTextArea guidance,
                  JTextArea mapCollection,
                  JTextArea sessionReview) {
            this.connection = connection;
            this.sampleRate = sampleRate;
            this.calibration = calibration;
            this.eventCount = eventCount;
            this.snapshot = snapshot;
            this.fuelPathStatus = fuelPathStatus;
            this.sessionMode = sessionMode;
            this.guidance = guidance;
            this.mapCollection = mapCollection;
            this.sessionReview = sessionReview;
        }
    }

    static void install(JPanel host,
                        Controls controls,
                        Content content,
                        Overview overview,
                        Technical technical) {
        host.add(ControlPanelBuilder.build(
                controls.reconnect, controls.readProject, controls.saveCsv,
                controls.suggestTable, controls.suggestMapEstimate,
                controls.suggestBlend, controls.sessionReview, controls.reset,
                controls.threshold, controls.calibrationSeconds,
                controls.calibrate, controls.applyCalibration,
                controls.mapMinimumSamples, controls.mapCap), BorderLayout.NORTH);

        JComponent status = buildStatusPanel(content, overview, technical);
        MainContentBuilder.configure(content.mainScroll, content.channelScroll,
                content.channelTable, content.latestEventText,
                content.recommendationHistoryText, content.lowerTabs,
                content.plotPanel, status);
        host.add(content.mainScroll, BorderLayout.CENTER);
    }

    private static JComponent buildStatusPanel(Content content,
                                               Overview overview,
                                               Technical technical) {
        JTabbedPane tabs = new StableTabbedPane();
        JComponent overviewPanel = buildOverviewPanel(content, overview);
        JComponent technicalPanel = buildTechnicalStatusPanel(content, technical);
        NestedScrollWheelHandoff.install(content.overviewScroll, content.mainScroll);
        NestedScrollWheelHandoff.install(content.technicalScroll, content.mainScroll);

        tabs.addTab("Overview", overviewPanel);
        tabs.addTab("Technical details", technicalPanel);
        tabs.setToolTipTextAt(0,
                "Clear summary of configuration, live state, progress, and next action.");
        tabs.setToolTipTextAt(1,
                "Project and diagnostic details. Scroll this tab for all wrapped text.");
        tabs.setFocusable(false);
        tabs.setRequestFocusEnabled(false);
        setStatusTabsHeight(tabs, 500);
        return tabs;
    }

    private static JComponent buildOverviewPanel(Content content, Overview overview) {
        WrappingColumnPanel panel = new WrappingColumnPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JPanel header = new JPanel(new BorderLayout(8, 0));
        overview.connection.setFont(
                overview.connection.getFont().deriveFont(Font.BOLD));
        header.add(overview.connection, BorderLayout.CENTER);
        header.add(overview.rate, BorderLayout.EAST);
        header.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        setFixedHeight(header, 28);

        JPanel configuration = buildCardRow("Configuration and tuning stage",
                overview.workflow, overview.tpsCycle, overview.mapPredict,
                overview.wallWetting, overview.instantFuel, overview.detector);
        JPanel live = buildCardRow("Live transient state",
                overview.predictionLive, overview.mapValues, overview.transientFuel);
        JPanel progress = buildCardRow("Session progress",
                overview.calibration, overview.eventProgress,
                overview.mapCoverage, overview.nextAction);
        JPanel review = buildCardRow("MAP Predict and safety review",
                overview.contributionReview, overview.lowRpmReview,
                overview.fullLoadSafety);

        panel.add(header);
        panel.add(configuration);
        panel.add(live);
        panel.add(progress);
        panel.add(review);

        content.overviewScroll.setViewportView(panel);
        content.overviewScroll.setBorder(null);
        content.overviewScroll.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        content.overviewScroll.setVerticalScrollBarPolicy(
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        content.overviewScroll.getVerticalScrollBar().setUnitIncrement(18);
        content.overviewScroll.getVerticalScrollBar().setBlockIncrement(90);
        return content.overviewScroll;
    }

    private static JPanel buildCardRow(String title, StatusCard... cards) {
        JPanel row = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 3));
        row.setBorder(BorderFactory.createTitledBorder(title));
        for (StatusCard card : cards) {
            row.add(card);
        }
        row.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return row;
    }

    private static JComponent buildTechnicalStatusPanel(Content content,
                                                        Technical technical) {
        ViewportWidthPanel panel = new ViewportWidthPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        "Read-only v" + AeTunerPlugin.VERSION),
                BorderFactory.createEmptyBorder(2, 3, 2, 3)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 3, 2, 3);
        gbc.weightx = 0.5;
        gbc.weighty = 0.0;

        addTechnicalCard(panel, gbc, 0, 0, 1,
                buildTechnicalSection("Project and connection", 52,
                        technical.connection, technical.sampleRate));
        addTechnicalCard(panel, gbc, 1, 0, 1,
                buildTechnicalSection("Detector and calibration", 52,
                        technical.calibration, technical.eventCount));
        addTechnicalCard(panel, gbc, 0, 1, 2,
                buildTechnicalSection("Active configuration", 66,
                        technical.snapshot));
        addTechnicalCard(panel, gbc, 0, 2, 2,
                buildTechnicalSection("Live transient paths", 48,
                        technical.fuelPathStatus));
        addTechnicalCard(panel, gbc, 0, 3, 2,
                buildTechnicalSection("Session classification", 64,
                        technical.sessionMode));
        addTechnicalCard(panel, gbc, 0, 4, 2,
                buildTechnicalSection("Guidance and MAP collection", 82,
                        technical.guidance, technical.mapCollection));
        addTechnicalCard(panel, gbc, 0, 5, 2,
                buildTechnicalSection("Low-RPM and full-load review", 64,
                        technical.sessionReview));

        Dimension natural = panel.getPreferredSize();
        panel.setPreferredSize(new Dimension(1000, Math.max(560, natural.height)));
        panel.setMinimumSize(new Dimension(700, 560));

        content.technicalScroll.setViewportView(panel);
        content.technicalScroll.setBorder(null);
        content.technicalScroll.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        content.technicalScroll.setVerticalScrollBarPolicy(
                ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        content.technicalScroll.getVerticalScrollBar().setUnitIncrement(18);
        content.technicalScroll.getVerticalScrollBar().setBlockIncrement(90);
        return content.technicalScroll;
    }

    private static void addTechnicalCard(JPanel panel,
                                         GridBagConstraints template,
                                         int x, int y, int width,
                                         JComponent card) {
        GridBagConstraints gbc = (GridBagConstraints) template.clone();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = width;
        panel.add(card, gbc);
    }

    private static JPanel buildTechnicalSection(String title,
                                                int height,
                                                JComponent... components) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                BorderFactory.createEmptyBorder(1, 5, 3, 5)));
        for (JComponent component : components) {
            component.setAlignmentX(JPanel.LEFT_ALIGNMENT);
            component.setMaximumSize(
                    new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            section.add(component);
        }
        Dimension preferred = section.getPreferredSize();
        section.setPreferredSize(new Dimension(Math.max(1, preferred.width),
                Math.max(height, preferred.height)));
        section.setMinimumSize(new Dimension(1, height));
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return section;
    }

    private static void setStatusTabsHeight(JTabbedPane tabs, int height) {
        tabs.setPreferredSize(new Dimension(1000, height));
        tabs.setMinimumSize(new Dimension(700, height));
        tabs.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }

    private static void setFixedHeight(JComponent component, int height) {
        Dimension preferred = component.getPreferredSize();
        component.setPreferredSize(new Dimension(Math.max(1, preferred.width), height));
        component.setMinimumSize(new Dimension(1, height));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }
}
