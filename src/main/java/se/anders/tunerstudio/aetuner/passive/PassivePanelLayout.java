package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.ui.AeUiTheme;
import se.anders.tunerstudio.aetuner.ui.AeUtilityWorkspacePanel;
import se.anders.tunerstudio.aetuner.ui.WrapLayout;
import se.anders.tunerstudio.aetuner.ui.WrappingColumnPanel;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
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
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;

/**
 * Pure Swing composition for the passive AE Tuner surface.
 *
 * AeTunerPanel remains the owner of every component and listener. This class
 * only arranges those production-owned objects into the v0.19 utility shell.
 */
final class PassivePanelLayout {
    private static final String OVERVIEW = "overview";
    private static final String EVENT_PREVIEW = "event-preview";
    private static final String NOTES = "notes";
    private static final String GUIDANCE = "guidance";
    private static final String SETUP = "setup";

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
        // Retained component ownership fields. mainScroll/lowerTabs/technicalScroll
        // are no longer part of the visible v0.19 Passive hierarchy.
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

    static final class Navigation {
        private final AeUtilityWorkspacePanel workspace;
        Navigation(AeUtilityWorkspacePanel workspace) { this.workspace = workspace; }
        void showOverview() { workspace.selectSection(OVERVIEW); }
        void showEventPreview() { workspace.selectSection(EVENT_PREVIEW); }
        void showNotes() { workspace.selectSection(NOTES); }
        void showGuidance() { workspace.selectSection(GUIDANCE); }
        void showSetup() { workspace.selectSection(SETUP); }
        String selectedSectionForTest() { return workspace.selectedSectionId(); }
        int sectionCountForTest() { return workspace.sectionCount(); }
        String sectionTitleForTest(int index) { return workspace.sectionTitleAt(index); }
        AeUtilityWorkspacePanel workspaceForTest() { return workspace; }
    }

    static Navigation install(JPanel host,
                              Controls controls,
                              Content content,
                              Overview overview,
                              Technical technical) {
        host.removeAll();
        host.setBorder(BorderFactory.createEmptyBorder());

        AeUtilityWorkspacePanel workspace = new AeUtilityWorkspacePanel(
                "Passive Analysis",
                "Read-only transient observation, Passive detector calibration and retained session evidence. No ECU writes.");
        workspace.setToolbar(ControlPanelBuilder.build(
                controls.reconnect, controls.readProject, controls.saveCsv,
                controls.suggestTable, controls.suggestMapEstimate,
                controls.suggestBlend, controls.sessionReview, controls.reset,
                controls.threshold, controls.calibrationSeconds,
                controls.calibrate, controls.applyCalibration,
                controls.mapMinimumSamples, controls.mapCap));

        MainContentBuilder.Sections eventSections = MainContentBuilder.build(
                content.channelScroll, content.channelTable,
                content.latestEventText, content.recommendationHistoryText,
                content.plotPanel);

        workspace.addSection(OVERVIEW, "Overview",
                "Current Passive state, method activity and session progress",
                buildOverviewPanel(content, overview));
        workspace.addSection(EVENT_PREVIEW, "Event Preview",
                "Resolved Passive inputs and the latest captured transient plot",
                eventSections.eventPreview);
        workspace.addSection(NOTES, "Session Notes",
                "Latest event detail, export status and generated advisory text",
                eventSections.notes);
        workspace.addSection(GUIDANCE, "Session Guidance",
                "Session-local recommendation transitions and next-step history",
                eventSections.guidance);
        workspace.addSection(SETUP, "Setup / Calibration",
                "Passive TPS noise calibration and analysis-only parameters",
                buildSetupCalibrationPanel(technical, controls));
        workspace.selectSection(OVERVIEW);
        workspace.applyTheme();

        host.add(workspace, BorderLayout.CENTER);
        return new Navigation(workspace);
    }

    private static JComponent buildOverviewPanel(Content content, Overview overview) {
        WrappingColumnPanel panel = new WrappingColumnPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBorder(new CompoundBorder(
                new LineBorder(AeUiTheme.border()),
                BorderFactory.createEmptyBorder(7, 9, 7, 9)));
        overview.connection.setFont(overview.connection.getFont().deriveFont(Font.BOLD));
        header.add(overview.connection, BorderLayout.CENTER);
        header.add(overview.rate, BorderLayout.EAST);
        header.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        setFixedHeight(header, 34);

        panel.add(header);
        panel.add(buildCardRow("Configuration and tuning stage",
                overview.workflow, overview.tpsCycle, overview.mapPredict,
                overview.wallWetting, overview.instantFuel, overview.detector));
        panel.add(buildCardRow("Live transient state",
                overview.predictionLive, overview.mapValues, overview.transientFuel));
        panel.add(buildCardRow("Session progress",
                overview.calibration, overview.eventProgress,
                overview.mapCoverage, overview.nextAction));
        panel.add(buildCardRow("MAP Predict and safety review",
                overview.contributionReview, overview.lowRpmReview,
                overview.fullLoadSafety));

        content.overviewScroll.setViewportView(panel);
        content.overviewScroll.setBorder(new LineBorder(AeUiTheme.border()));
        content.overviewScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        content.overviewScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        content.overviewScroll.getVerticalScrollBar().setUnitIncrement(18);
        content.overviewScroll.getVerticalScrollBar().setBlockIncrement(90);
        return content.overviewScroll;
    }

    private static JComponent buildSetupCalibrationPanel(Technical technical,
                                                         Controls controls) {
        WrappingColumnPanel panel = new WrappingColumnPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JTextArea scope = new JTextArea(
                "TPS noise calibration belongs only to Passive event detection. "
                + "It calibrates AE Tuner's Passive TPS-movement threshold; it does not alter ECU settings and does not set Guided Tuning opening thresholds.");
        scope.setEditable(false);
        scope.setLineWrap(true);
        scope.setWrapStyleWord(true);
        scope.setOpaque(false);
        scope.setFocusable(false);
        scope.setFont(scope.getFont().deriveFont(12f));

        JPanel calibration = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 5));
        calibration.add(settingGroup("Manual TPSdot threshold %/s", controls.threshold));
        calibration.add(settingGroup("Calibration seconds", controls.calibrationSeconds));
        calibration.add(controls.calibrate);
        calibration.add(controls.applyCalibration);

        JPanel analysis = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 5));
        analysis.add(settingGroup("MAP draft minimum samples/cell", controls.mapMinimumSamples));
        analysis.add(settingGroup("Turbo MAP cap kPa (TPS >=33.5%)", controls.mapCap));

        JPanel status = new JPanel(new BorderLayout());
        technical.calibration.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        status.add(technical.calibration, BorderLayout.CENTER);

        panel.add(sectionCard("Scope", "What these settings can and cannot change", scope));
        panel.add(sectionCard("Passive TPS noise calibration",
                "Stationary calibration for Passive event classification only", calibration));
        panel.add(sectionCard("Passive analysis parameters",
                "Evidence/draft parameters; not Guided controller settings", analysis));
        panel.add(sectionCard("Calibration status",
                "Current calibration state and recommendation", status));
        return panel;
    }

    private static JPanel settingGroup(String label, Component editor) {
        JPanel group = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JLabel text = new JLabel(label + ":");
        text.setFont(text.getFont().deriveFont(Font.PLAIN, 11f));
        group.add(text);
        group.add(editor);
        return group;
    }

    private static JPanel buildCardRow(String title, StatusCard... cards) {
        JPanel body = new JPanel(new WrapLayout(FlowLayout.LEFT, 7, 5));
        for (StatusCard card : cards) body.add(card);
        return sectionCard(title, "", body);
    }

    private static JPanel sectionCard(String title, String detail, JComponent body) {
        JPanel card = new JPanel(new BorderLayout(0, 7));
        card.setBorder(new CompoundBorder(
                new LineBorder(AeUiTheme.border()),
                BorderFactory.createEmptyBorder(8, 9, 8, 9)));
        JPanel head = new JPanel(new BorderLayout(0, 1));
        JLabel heading = new JLabel(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 13f));
        head.add(heading, BorderLayout.NORTH);
        if (detail != null && !detail.isEmpty()) {
            JLabel sub = new JLabel(detail);
            sub.setFont(sub.getFont().deriveFont(Font.PLAIN, 10f));
            head.add(sub, BorderLayout.SOUTH);
        }
        card.add(head, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        card.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return card;
    }

    private static void setFixedHeight(JComponent component, int height) {
        Dimension preferred = component.getPreferredSize();
        component.setPreferredSize(new Dimension(Math.max(1, preferred.width), height));
        component.setMinimumSize(new Dimension(1, height));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }
}
