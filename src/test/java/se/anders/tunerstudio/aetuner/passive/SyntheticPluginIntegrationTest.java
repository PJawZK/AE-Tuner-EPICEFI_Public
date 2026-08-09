package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.AbstractDocument;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * End-to-end synthetic integration scenario for the real plugin panel.
 *
 * This deliberately avoids ControllerAccess mocks. It installs a deterministic
 * project/channel snapshot, then feeds the same OutputChannelClient callback
 * used by TunerStudio. The real Swing save actions are exercised under Xvfb.
 */
public final class SyntheticPluginIntegrationTest {
    private static final long SAMPLE_PAUSE_MS = 14L;

    private SyntheticPluginIntegrationTest() { }

    public static void main(String[] args) {
        int exit = 1;
        JFrame frame = null;
        AeTunerPlugin plugin = null;
        try {
            File out = outputDirectory();
            Files.createDirectories(out.toPath());

            pluginCloseMustStopPanelRefresh();
            keyOffOnlyActivityMustRemainDiagnostic();
            asynchronousPhysicalShutdownMustRemainDiagnostic(out);

            PluginFixture fixture = createFixture("Synthetic EPICEFI integration");
            plugin = fixture.plugin;
            frame = showPanel(fixture.panel);

            assertPluginMetadata(plugin);
            assertRefactorOwnershipAndListeners(fixture.panel);
            assertOverviewCardEquivalence(fixture.panel);
            assertClipboardCoordinatorEquivalence();
            assertTechnicalDetailsPresentation(fixture.panel);
            assertResponsiveWrapping(frame, fixture.panel);
            captureMapPredictEvent(fixture);
            assertCapturedPredictionEvent(fixture.panel);

            int historyBeforeFault = historySize(fixture.panel);
            injectRunningTriggerFault(fixture);
            refresh(fixture.panel);
            String action = cardText(fixture.panel, "nextActionCard");
            require(action.contains("running trigger/sync loss"),
                    "Running trigger fault did not become the highest-priority recommendation: " + action);
            require(action.contains("CRITICAL / HIGH"),
                    "Recommended next step card omitted severity/confidence badge: " + action);
            require(historySize(fixture.panel) == historyBeforeFault + 1,
                    "Running trigger recommendation did not create exactly one history transition");
            require(historyText(fixture.panel).contains("RUNNING_TRIGGER_SYNC"),
                    "Session Guidance did not record the running trigger transition");
            openSessionGuidance(fixture.panel);
            require(selectedLowerTabTitle(fixture.panel).equals("Session Guidance"),
                    "Clicking Recommended next step did not open Session Guidance");

            int historyBeforeKeyOff = historySize(fixture.panel);
            injectNormalKeyOff(fixture);
            refresh(fixture.panel);
            String actionAfterKeyOff = cardText(fixture.panel, "nextActionCard");
            require(actionAfterKeyOff.contains("running trigger/sync loss"),
                    "Normal key-off displaced the real running fault recommendation: " + actionAfterKeyOff);
            require(historySize(fixture.panel) == historyBeforeKeyOff,
                    "Normal key-off duplicated an unchanged running-fault recommendation");

            File csv = new File(out, "synthetic-events.csv");
            File report = new File(out, "synthetic-map-predict-report.txt");
            require(Double.isNaN(doubleField(fixture.panel, "lastCsvExportMillis"))
                            && Double.isNaN(doubleField(fixture.panel, "lastReportExportMillis")),
                    "Export timing must start unavailable");
            cancelRealChooser(fixture.panel, "saveCsv");
            cancelRealChooser(fixture.panel, "saveMapPredictReport");
            require(Double.isNaN(doubleField(fixture.panel, "lastCsvExportMillis"))
                            && Double.isNaN(doubleField(fixture.panel, "lastReportExportMillis")),
                    "Cancelled export recorded a successful duration");

            File failedTarget = new File(new File(out, "missing-export-parent"), "evidence.txt");
            approveRealChooser(fixture.panel, "saveCsv", failedTarget);
            approveRealChooser(fixture.panel, "saveMapPredictReport", failedTarget);
            require(Double.isNaN(doubleField(fixture.panel, "lastCsvExportMillis"))
                            && Double.isNaN(doubleField(fixture.panel, "lastReportExportMillis")),
                    "Failed export recorded a successful duration");
            saveThroughRealChooser(fixture.panel, "saveCsv", csv);
            saveThroughRealChooser(fixture.panel, "saveMapPredictReport", report);
            require(doubleField(fixture.panel, "lastCsvExportMillis") >= 0.0
                            && doubleField(fixture.panel, "lastReportExportMillis") >= 0.0,
                    "Successful exports did not record completion timing");
            File guidance = new File(out, "synthetic-session-guidance.txt");
            Files.write(guidance.toPath(), historyText(fixture.panel).getBytes(StandardCharsets.UTF_8));
            openSessionGuidance(fixture.panel);
            renderPanel(fixture.panel, new File(out, "synthetic-plugin-panel.png"));
            renderFrameAtSize(frame, fixture.panel, fixture.panel,
                    new File(out, "synthetic-plugin-panel-narrow.png"), 820, 1000);
            scrollOverviewToBottomAndRender(fixture.panel,
                    new File(out, "synthetic-plugin-overview-narrow-bottom.png"));

            String csvText = read(csv);
            String reportText = read(report);
            require(csvText.contains("MAP Predict + Wall Wetting event"),
                    "Generated CSV did not contain the synthetic prediction event");
            require(reportText.contains("CRITICAL OUTPUT-CHANNEL RESOLUTION"),
                    "Generated report omitted channel-resolution evidence");
            require(reportText.contains("running: selected `ready`; latest raw 0.0"),
                    "Generated report did not expose the selected running channel after key-off");
            require(reportText.contains("Ign: Cut Code: selected `sparkCutReason`; latest raw 14.0"),
                    "Generated report did not retain key-off ignition cut context");
            require(reportText.contains("running trigger/sync loss"),
                    "Generated report did not retain the synthetic running trigger fault");
            require(reportText.contains("Read-only report: no ECU values were changed."),
                    "Generated report lost the read-only guarantee");
            require(reportText.contains("SESSION DIAGNOSTICS")
                            && reportText.contains("Retained event samples:")
                            && reportText.contains("Detector buffers now:")
                            && reportText.contains("MAP Estimate accepted samples:")
                            && reportText.contains("Session Guidance entries:")
                            && reportText.contains("Java runtime:")
                            && reportText.contains("Report preparation duration before file write:"),
                    "Generated report omitted long-session diagnostic evidence");

            List<String> result = new ArrayList<String>();
            result.add("Synthetic plugin integration: passed");
            result.add("Plugin: " + plugin.getDisplayName() + " " + plugin.getVersion());
            result.add("Captured events: " + capturedEvents(fixture.panel).size());
            result.add("Recommendation: " + actionAfterKeyOff);
            result.add("Session Guidance entries: " + historySize(fixture.panel));
            result.add("CSV: " + csv.getAbsolutePath());
            result.add("Report: " + report.getAbsolutePath());
            result.add("Session Guidance: " + guidance.getAbsolutePath());
            result.add("Screenshot: " + new File(out, "synthetic-plugin-panel.png").getAbsolutePath());
            result.add("Narrow screenshot: "
                    + new File(out, "synthetic-plugin-panel-narrow.png").getAbsolutePath());
            result.add("Narrow Overview bottom screenshot: "
                    + new File(out, "synthetic-plugin-overview-narrow-bottom.png").getAbsolutePath());
            Files.write(new File(out, "result.txt").toPath(), result, StandardCharsets.UTF_8);

            System.out.println("SyntheticPluginIntegrationTest passed");
            exit = 0;
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
        } finally {
            final JFrame frameToDispose = frame;
            final AeTunerPlugin pluginToClose = plugin;
            try {
                onEdt(new Runnable() {
                    @Override
                    public void run() {
                        if (pluginToClose != null) pluginToClose.close();
                        if (frameToDispose != null) frameToDispose.dispose();
                        for (Window window : Window.getWindows()) {
                            window.dispose();
                        }
                    }
                });
            } catch (Throwable ignored) {
                // Preserve the original test result.
            }
        }
        System.exit(exit);
    }

    private static void pluginCloseMustStopPanelRefresh() throws Exception {
        final PluginFixture fixture = createFixture("Synthetic plugin lifecycle");
        require(!fixture.panel.isRefreshTimerRunning(),
                "Hidden plugin panel must not consume periodic refresh work");
        final JFrame frame = showPanel(fixture.panel);
        require(fixture.panel.isRefreshTimerRunning(),
                "Visible panel refresh timer must be running");
        onEdt(new Runnable() {
            @Override
            public void run() {
                frame.setVisible(false);
            }
        });
        require(!fixture.panel.isRefreshTimerRunning(),
                "Hiding the plugin panel must stop periodic refresh work");
        onEdt(new Runnable() {
            @Override
            public void run() {
                frame.setVisible(true);
            }
        });
        require(fixture.panel.isRefreshTimerRunning(),
                "Reopening the plugin panel must restart periodic refresh work");
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.plugin.close();
                frame.dispose();
            }
        });
        require(!fixture.panel.isRefreshTimerRunning(),
                "Plugin close must stop the panel refresh timer");

        AeTunerPlugin replacement = new AeTunerPlugin();
        AeTunerPanel replacementPanel = (AeTunerPanel) replacement.getPluginPanel();
        require(replacementPanel != fixture.panel,
                "Plugin re-instantiation must create a fresh panel instance");
        require(capturedEvents(replacementPanel).isEmpty(),
                "Plugin re-instantiation retained captured events from the closed panel");
        require(!replacementPanel.isRefreshTimerRunning(),
                "Fresh hidden plugin panel unexpectedly started its refresh timer");
        replacement.close();
    }

    private static void keyOffOnlyActivityMustRemainDiagnostic() throws Exception {
        PluginFixture fixture = createFixture("Synthetic key-off classification");
        try {
            for (int i = 0; i < 5; i++) {
                feed(fixture, runningValues(900.0, 2.0, 50.0), SAMPLE_PAUSE_MS);
            }
            EnumMap<ChannelRole, Double> keyOff = keyOffValues();
            keyOff.put(ChannelRole.TRIGGER_ERROR, 1.0);
            keyOff.put(ChannelRole.TRIGGER_ERROR_COUNT, 1.0);
            keyOff.put(ChannelRole.IGN_CUT_CODE, 14.0);
            keyOff.put(ChannelRole.FUEL_CUT_CODE, 14.0);
            feed(fixture, keyOff, SAMPLE_PAUSE_MS);
            refresh(fixture.panel);

            SessionMonitor monitor = (SessionMonitor) field(fixture.panel, "sessionMonitor");
            SessionReview review = SessionReview.build(new ArrayList<TransientEvent>(), monitor.snapshot());
            require(!review.triggerSyncNeedsReview(),
                    "Key-off-only trigger activity was incorrectly classified as a running fault");
            require(!review.sessionFaultNeedsReview(),
                    "Key-off-only cut activity was incorrectly classified as a running fault");
            require(review.toDisplayText().contains("Key-off/coast-down trigger activity: seen and excluded"),
                    "Key-off trigger activity was not retained as diagnostic evidence");
            require(review.toDisplayText().contains("Key-off fault/cut activity: seen and excluded"),
                    "Key-off cut activity was not retained as diagnostic evidence");

            String action = cardText(fixture.panel, "nextActionCard");
            require(!action.contains("running trigger/sync loss") && !action.contains("running fault/cut"),
                    "Key-off-only activity created a running-engine recommendation: " + action);
        } finally {
            fixture.plugin.close();
        }
    }


    private static void asynchronousPhysicalShutdownMustRemainDiagnostic(File out) throws Exception {
        PluginFixture fixture = createFixture("Synthetic v0.3.17 physical shutdown");
        try {
            EnumMap<ChannelRole, Double> baseline = runningValues(900.0, 2.0, 50.0);
            setDiagnosticCounters(baseline, 0.0, 5.0, 10.0, 20.0, 0.0);
            feed(fixture, baseline, SAMPLE_PAUSE_MS);

            EnumMap<ChannelRole, Double> runningIncrement = runningValues(950.0, 2.5, 52.0);
            setDiagnosticCounters(runningIncrement, 0.0, 5.0, 12.0, 22.0, 0.0);
            feed(fixture, runningIncrement, SAMPLE_PAUSE_MS);

            EnumMap<ChannelRole, Double> crankingIncrement = baseValues();
            crankingIncrement.put(ChannelRole.RPM, 210.0);
            crankingIncrement.put(ChannelRole.TPS, 0.0);
            crankingIncrement.put(ChannelRole.MAP, 100.0);
            crankingIncrement.put(ChannelRole.FALLBACK_MAP, 100.0);
            crankingIncrement.put(ChannelRole.EFFECTIVE_MAP, 100.0);
            crankingIncrement.put(ChannelRole.BATTERY, 12.1);
            crankingIncrement.put(ChannelRole.ENGINE_RUNNING, 0.0);
            crankingIncrement.put(ChannelRole.ENGINE_CRANKING, 1.0);
            crankingIncrement.put(ChannelRole.IGNITION_ON, 1.0);
            crankingIncrement.put(ChannelRole.MAIN_RELAY_HAS_IGN, 1.0);
            setDiagnosticCounters(crankingIncrement, 0.0, 5.0, 13.0, 23.0, 0.0);
            feed(fixture, crankingIncrement, SAMPLE_PAUSE_MS);

            EnumMap<ChannelRole, Double> coherentRunning = runningValues(900.0, 2.0, 50.0);
            setDiagnosticCounters(coherentRunning, 0.0, 5.0, 13.0, 23.0, 0.0);
            feed(fixture, coherentRunning, SAMPLE_PAUSE_MS);
            refresh(fixture.panel);
            int historyBeforePhysicalShutdown = historySize(fixture.panel);

            // Exact physical ordering: relay off first while the running flag still lags high.
            EnumMap<ChannelRole, Double> relayOff = runningValues(720.0, 0.0, 82.0);
            relayOff.put(ChannelRole.MAIN_RELAY_HAS_IGN, 0.0);
            setDiagnosticCounters(relayOff, 0.0, 7.0, 14.0, 23.0, 0.0);
            feed(fixture, relayOff, SAMPLE_PAUSE_MS);

            // ignitionOn then goes low and both reason codes become 14; actual outputs stay zero.
            EnumMap<ChannelRole, Double> code14 = new EnumMap<ChannelRole, Double>(relayOff);
            code14.put(ChannelRole.IGNITION_ON, 0.0);
            code14.put(ChannelRole.IGN_CUT_CODE, 14.0);
            code14.put(ChannelRole.FUEL_CUT_CODE, 14.0);
            code14.put(ChannelRole.TOTAL_SPARK_CUT, 0.0);
            code14.put(ChannelRole.FUEL_CUT, 0.0);
            feed(fixture, code14, SAMPLE_PAUSE_MS);

            // The trigger pulse/counter increment also arrives before the lagging running flag clears.
            EnumMap<ChannelRole, Double> triggerPulse = new EnumMap<ChannelRole, Double>(code14);
            triggerPulse.put(ChannelRole.TRIGGER_ERROR, 1.0);
            triggerPulse.put(ChannelRole.TRIGGER_ERROR_COUNT, 1.0);
            feed(fixture, triggerPulse, SAMPLE_PAUSE_MS);

            EnumMap<ChannelRole, Double> finalKeyOff = new EnumMap<ChannelRole, Double>(triggerPulse);
            finalKeyOff.put(ChannelRole.RPM, 0.0);
            finalKeyOff.put(ChannelRole.ENGINE_RUNNING, 0.0);
            feed(fixture, finalKeyOff, SAMPLE_PAUSE_MS);
            refresh(fixture.panel);
            require(historySize(fixture.panel) == historyBeforePhysicalShutdown,
                    "Archive 4 key-off sequence created a false or duplicate Session Guidance entry");

            SessionMonitor monitor = (SessionMonitor) field(fixture.panel, "sessionMonitor");
            SessionMonitor.Snapshot snapshot = monitor.snapshot();
            SessionReview review = SessionReview.build(new ArrayList<TransientEvent>(), snapshot);

            require(!review.triggerSyncNeedsReview(),
                    "Physical key-off trigger pulse became a running trigger/sync fault");
            require(!review.sessionFaultNeedsReview(),
                    "Physical key-off code 14 became a running fault/cut recommendation");
            require(snapshot.keyOffTriggerError,
                    "Physical key-off trigger pulse was not retained diagnostically");
            require(snapshot.keyOffFaultOrCut,
                    "Physical key-off reason codes were not retained diagnostically");
            require(snapshot.triggerErrorCountDelta == 0.0
                            && snapshot.crankingTriggerErrorCountDelta == 0.0
                            && snapshot.keyOffTriggerErrorCountDelta == 1.0
                            && snapshot.unknownTriggerErrorCountDelta == 0.0,
                    "Trigger Error Counter state attribution did not match the physical sequence");

            require(!snapshot.cutEvidence.runningActualSparkCut
                            && !snapshot.cutEvidence.runningActualFuelCut,
                    "Inactive actual cut outputs became running cuts");
            require(!snapshot.cutEvidence.runningIgnitionCutReason
                            && !snapshot.cutEvidence.runningFuelCutReason,
                    "Key-off reason code 14 survived the coherent-running guard");

            require(snapshot.overDwellCounter.increase == 2.0
                            && snapshot.overDwellCounter.runningIncrease == 0.0
                            && snapshot.overDwellCounter.crankingIncrease == 0.0
                            && snapshot.overDwellCounter.keyOffIncrease == 2.0,
                    "Over-dwell state attribution mismatch");
            require(snapshot.overchargeCounter.increase == 4.0
                            && snapshot.overchargeCounter.runningIncrease == 2.0
                            && snapshot.overchargeCounter.crankingIncrease == 1.0
                            && snapshot.overchargeCounter.keyOffIncrease == 1.0,
                    "Overcharge state attribution mismatch");
            require(snapshot.underchargeCounter.increase == 3.0
                            && snapshot.underchargeCounter.runningIncrease == 2.0
                            && snapshot.underchargeCounter.crankingIncrease == 1.0
                            && snapshot.underchargeCounter.keyOffIncrease == 0.0,
                    "Undercharge state attribution mismatch");
            require(snapshot.sparkOutOfOrderCounter.increase == 0.0,
                    "Spark-out-of-order unexpectedly increased");
            require(snapshot.overDwellCounter.resets == 0L
                            && snapshot.overchargeCounter.resets == 0L
                            && snapshot.underchargeCounter.resets == 0L
                            && snapshot.sparkOutOfOrderCounter.resets == 0L,
                    "Synthetic monotonic counters reported a reset");

            String action = cardText(fixture.panel, "nextActionCard");
            require(!action.contains("running trigger/sync loss")
                            && !action.contains("running fault/cut"),
                    "Physical shutdown sequence created a running-engine recommendation: " + action);

            List<String> evidence = new ArrayList<String>();
            evidence.add("v0.3.17 physical shutdown synthetic fixture: passed");
            evidence.add("Recommendation: " + action);
            evidence.add("Trigger Error Counter: running +0.0, cranking +0.0, key-off +1.0, unknown +0.0");
            evidence.add("Over-dwell: total +2.0, running +0.0, cranking +0.0, key-off +2.0");
            evidence.add("Overcharge: total +4.0, running +2.0, cranking +1.0, key-off +1.0");
            evidence.add("Undercharge: total +3.0, running +2.0, cranking +1.0, key-off +0.0");
            evidence.add("Actual spark/fuel cut outputs: inactive");
            evidence.add("Reason code 14: retained as key-off diagnostic context");
            evidence.add("Session Guidance entries added by shutdown: 0");
            Files.write(new File(out, "synthetic-v0317-shutdown.txt").toPath(),
                    evidence, StandardCharsets.UTF_8);
        } finally {
            fixture.plugin.close();
        }
    }

    private static void setDiagnosticCounters(EnumMap<ChannelRole, Double> values,
                                              double trigger,
                                              double overDwell,
                                              double overcharge,
                                              double undercharge,
                                              double outOfOrder) {
        values.put(ChannelRole.TRIGGER_ERROR_COUNT, trigger);
        values.put(ChannelRole.IGN_OVERDWELL, overDwell);
        values.put(ChannelRole.IGN_OVERCHARGE_WARNINGS, overcharge);
        values.put(ChannelRole.IGN_UNDERCHARGE_WARNINGS, undercharge);
        values.put(ChannelRole.IGN_SPARK_OUT_OF_ORDER, outOfOrder);
    }

    private static PluginFixture createFixture(final String configurationName) throws Exception {
        final AtomicReference<PluginFixture> result = new AtomicReference<PluginFixture>();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        onEdt(new Runnable() {
            @Override
            public void run() {
                try {
                    AeTunerPlugin plugin = new AeTunerPlugin();
                    AeTunerPanel panel = (AeTunerPanel) plugin.getPluginPanel();
                    AeProjectSnapshot snapshot = syntheticProject(configurationName);

                    setField(panel, "configurationName", configurationName);
                    setField(panel, "projectSnapshot", snapshot);
                    setField(panel, "detectionArmedNano", 0L);
                    setField(panel, "lastSampleNano", 0L);

                    @SuppressWarnings("unchecked")
                    EnumMap<ChannelRole, String> names =
                            (EnumMap<ChannelRole, String>) field(panel, "channelNames");
                    @SuppressWarnings("unchecked")
                    Map<String, ChannelRole> subscribed =
                            (Map<String, ChannelRole>) field(panel, "subscribedChannels");
                    @SuppressWarnings("unchecked")
                    Set<String> available = (Set<String>) field(panel, "availableOutputChannels");

                    names.clear();
                    subscribed.clear();
                    available.clear();
                    for (ChannelRole role : ChannelRole.values()) {
                        String selected = selectedName(role);
                        names.put(role, selected);
                        subscribed.put(selected, role);
                        available.add(selected);
                    }

                    MapEstimateCollector collector =
                            (MapEstimateCollector) field(panel, "mapEstimateCollector");
                    collector.configure(snapshot);
                    invoke(panel, "updateModeButtons");
                    invoke(panel, "refreshUi");
                    result.set(new PluginFixture(plugin, panel, names));
                } catch (Throwable ex) {
                    failure.set(ex);
                }
            }
        });
        if (failure.get() != null) throwAsException(failure.get());
        return result.get();
    }

    private static AeProjectSnapshot syntheticProject(String configurationName) {
        double[] cycleBins = new double[]{0.0, 20.0, 40.0, 60.0, 80.0, 100.0};
        double[] tpsToBins = new double[]{0.0, 10.0, 20.0, 35.0, 55.0, 100.0};
        double[][] cycleValues = new double[][]{
                {1.0, 1.0, 1.0, 1.0, 1.0, 1.0},
                {1.0, 1.0, 1.0, 1.0, 1.0, 1.0},
                {1.0, 1.0, 1.0, 1.0, 1.0, 1.0},
                {1.0, 1.0, 1.0, 1.0, 1.0, 1.0},
                {1.0, 1.0, 1.0, 1.0, 1.0, 1.0},
                {1.0, 1.0, 1.0, 1.0, 1.0, 1.0}
        };
        double[] rpmBins = new double[]{600.0, 1300.0, 1700.0, 2450.0};
        double[] threshold = new double[]{1.5, 1.5, 1.5, 1.5};
        double[] tpsBins = new double[]{0.0, 13.5, 20.0, 33.5};
        double[][] mapEstimate = new double[][]{
                {35.0, 40.0, 45.0, 50.0},
                {70.0, 75.0, 80.0, 85.0},
                {80.0, 85.0, 90.0, 95.0},
                {95.0, 100.0, 105.0, 110.0}
        };
        double[] blendRpm = new double[]{600.0, 2450.0, 4350.0, 6200.0};
        double[] blend = new double[]{0.26, 0.26, 0.18, 0.18};
        double[][] wallTable = new double[][]{{0.8, 0.8}, {0.8, 0.8}};

        return new AeProjectSnapshot(configurationName,
                cycleBins, tpsToBins, cycleValues,
                rpmBins, threshold,
                0.0, 0.0,
                new double[]{-20.0, 20.0, 80.0, 110.0},
                new double[]{1.0, 1.0, 1.0, 1.0},
                false, true, "Synthetic wall model", false,
                true, true, true,
                wallTable, wallTable,
                rpmBins, tpsBins, mapEstimate,
                blendRpm, blend);
    }

    private static void captureMapPredictEvent(PluginFixture fixture) throws Exception {
        for (int i = 0; i < 12; i++) {
            feed(fixture, runningValues(1800.0, 2.0, 50.0), SAMPLE_PAUSE_MS);
        }

        EnumMap<ChannelRole, Double> opening = runningValues(1800.0, 18.0, 62.0);
        opening.put(ChannelRole.FALLBACK_MAP, 92.0);
        opening.put(ChannelRole.EFFECTIVE_MAP, 92.0);
        opening.put(ChannelRole.MAP_PRED_ACTIVE, 1.0);
        opening.put(ChannelRole.MAP_PRED_RESET_CNT, 1.0);
        opening.put(ChannelRole.AE_ABOVE_THRESHOLD, 1.0);
        opening.put(ChannelRole.DELTA_TPS, 16.0);
        opening.put(ChannelRole.SMOOTHED_DELTA_TPS, 5.0);
        opening.put(ChannelRole.ACCEL_THRESHOLD, 1.5);
        opening.put(ChannelRole.WALL_WETTING_PW, 0.35);
        feed(fixture, opening, SAMPLE_PAUSE_MS);

        for (int i = 0; i < 15; i++) {
            EnumMap<ChannelRole, Double> active = runningValues(1800.0 + i * 8.0, 18.0, 64.0 + i);
            active.put(ChannelRole.FALLBACK_MAP, 92.0);
            active.put(ChannelRole.EFFECTIVE_MAP, 92.0);
            active.put(ChannelRole.MAP_PRED_ACTIVE, 1.0);
            active.put(ChannelRole.MAP_PRED_RESET_CNT, 1.0);
            active.put(ChannelRole.AE_ABOVE_THRESHOLD, 1.0);
            active.put(ChannelRole.WALL_WETTING_PW, 0.25);
            active.put(ChannelRole.LAMBDA, 0.92 + i * 0.002);
            feed(fixture, active, SAMPLE_PAUSE_MS);
        }

        for (int i = 0; i < 8; i++) {
            EnumMap<ChannelRole, Double> quiet = runningValues(1950.0, 18.0, 72.0);
            quiet.put(ChannelRole.FALLBACK_MAP, 72.0);
            quiet.put(ChannelRole.EFFECTIVE_MAP, 72.0);
            quiet.put(ChannelRole.MAP_PRED_ACTIVE, 0.0);
            quiet.put(ChannelRole.MAP_PRED_EVENT_OVER, 1.0);
            feed(fixture, quiet, SAMPLE_PAUSE_MS);
        }
        Thread.sleep(620L);
        feed(fixture, runningValues(1950.0, 18.0, 70.0), SAMPLE_PAUSE_MS);
        flushEdt();
    }

    private static void assertCapturedPredictionEvent(AeTunerPanel panel) throws Exception {
        List<TransientEvent> events = capturedEvents(panel);
        require(!events.isEmpty(), "Synthetic opening did not produce a captured event");
        TransientEvent event = events.get(events.size() - 1);
        require(event.isAccepted(), "Synthetic prediction event was rejected: " + event.toDisplayText());
        require(event.hasMapPrediction(), "Synthetic event did not retain MAP Predict activity");
        require(event.hasWallWettingContribution(), "Synthetic event did not retain Wall Wetting activity");
    }

    private static void injectRunningTriggerFault(PluginFixture fixture) throws Exception {
        EnumMap<ChannelRole, Double> fault = runningValues(1050.0, 3.0, 55.0);
        fault.put(ChannelRole.TRIGGER_ERROR, 1.0);
        fault.put(ChannelRole.TRIGGER_ERROR_COUNT, 1.0);
        feed(fixture, fault, SAMPLE_PAUSE_MS);
    }

    private static void injectNormalKeyOff(PluginFixture fixture) throws Exception {
        EnumMap<ChannelRole, Double> keyOff = keyOffValues();
        keyOff.put(ChannelRole.TRIGGER_ERROR, 1.0);
        keyOff.put(ChannelRole.TRIGGER_ERROR_COUNT, 2.0);
        keyOff.put(ChannelRole.IGN_CUT_CODE, 14.0);
        keyOff.put(ChannelRole.FUEL_CUT_CODE, 14.0);
        feed(fixture, keyOff, SAMPLE_PAUSE_MS);
    }

    private static EnumMap<ChannelRole, Double> runningValues(double rpm, double tps, double map) {
        EnumMap<ChannelRole, Double> values = baseValues();
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.MAP, map);
        values.put(ChannelRole.FALLBACK_MAP, map);
        values.put(ChannelRole.EFFECTIVE_MAP, map);
        values.put(ChannelRole.BATTERY, 13.8);
        values.put(ChannelRole.ENGINE_RUNNING, 1.0);
        values.put(ChannelRole.ENGINE_CRANKING, 0.0);
        values.put(ChannelRole.IGNITION_ON, 1.0);
        values.put(ChannelRole.MAIN_RELAY_HAS_IGN, 1.0);
        values.put(ChannelRole.IGNITION_TIMING, 16.0);
        values.put(ChannelRole.BOOST_TARGET, 0.0);
        values.put(ChannelRole.FUEL_PRESSURE_HIGH, 0.0);
        values.put(ChannelRole.FUEL_PRESSURE_LOW, 0.0);
        return values;
    }

    private static EnumMap<ChannelRole, Double> keyOffValues() {
        EnumMap<ChannelRole, Double> values = baseValues();
        values.put(ChannelRole.RPM, 250.0);
        values.put(ChannelRole.TPS, 0.0);
        values.put(ChannelRole.MAP, 100.0);
        values.put(ChannelRole.FALLBACK_MAP, 100.0);
        values.put(ChannelRole.EFFECTIVE_MAP, 100.0);
        values.put(ChannelRole.BATTERY, 0.0);
        values.put(ChannelRole.ENGINE_RUNNING, 0.0);
        values.put(ChannelRole.ENGINE_CRANKING, 0.0);
        values.put(ChannelRole.IGNITION_ON, 0.0);
        values.put(ChannelRole.MAIN_RELAY_HAS_IGN, 0.0);
        values.put(ChannelRole.IGNITION_TIMING, 0.0);
        return values;
    }

    private static EnumMap<ChannelRole, Double> baseValues() {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        for (ChannelRole role : ChannelRole.values()) values.put(role, 0.0);
        values.put(ChannelRole.TIME, 0.0);
        values.put(ChannelRole.BARO, 100.0);
        values.put(ChannelRole.LAMBDA, 1.0);
        values.put(ChannelRole.TARGET_LAMBDA, 1.0);
        values.put(ChannelRole.PW, 2.5);
        values.put(ChannelRole.INJ_DUTY, 8.0);
        values.put(ChannelRole.COOLANT, 80.0);
        values.put(ChannelRole.IAT, 25.0);
        return values;
    }

    private static void feed(PluginFixture fixture, EnumMap<ChannelRole, Double> values,
                             long pauseMs) throws Exception {
        @SuppressWarnings("unchecked")
        final EnumMap<ChannelRole, Double> latest =
                (EnumMap<ChannelRole, Double>) field(fixture.panel, "latestValues");
        final Object lock = field(fixture.panel, "lock");
        synchronized (lock) {
            latest.clear();
            latest.putAll(values);
        }
        setField(fixture.panel, "lastSampleNano", 0L);
        String tpsChannel = fixture.channelNames.get(ChannelRole.TPS);
        fixture.panel.setCurrentOutputChannelValue(tpsChannel, values.get(ChannelRole.TPS));
        if (pauseMs > 0L) Thread.sleep(pauseMs);
    }

    private static void saveThroughRealChooser(final AeTunerPanel panel,
                                               final String methodName,
                                               final File target) throws Exception {
        if (target.exists() && !target.delete()) {
            throw new IllegalStateException("Could not remove old test output: " + target);
        }
        final AtomicReference<Throwable> approverFailure = new AtomicReference<Throwable>();
        Thread approver = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    long deadline = System.currentTimeMillis() + 10000L;
                    while (System.currentTimeMillis() < deadline) {
                        final JFileChooser chooser = findVisibleFileChooser();
                        if (chooser != null) {
                            SwingUtilities.invokeAndWait(new Runnable() {
                                @Override
                                public void run() {
                                    chooser.setSelectedFile(target);
                                    chooser.approveSelection();
                                }
                            });
                            return;
                        }
                        Thread.sleep(50L);
                    }
                    throw new AssertionError("Timed out waiting for JFileChooser from " + methodName);
                } catch (Throwable ex) {
                    approverFailure.set(ex);
                }
            }
        }, "synthetic-file-chooser-approver");
        approver.setDaemon(true);
        approver.start();

        onEdt(new Runnable() {
            @Override
            public void run() {
                try {
                    invoke(panel, methodName);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        approver.join(11000L);
        if (approverFailure.get() != null) throwAsException(approverFailure.get());
        require(target.isFile() && target.length() > 0L,
                methodName + " did not create the expected file: " + target);
    }

    private static void cancelRealChooser(final AeTunerPanel panel,
                                          final String methodName) throws Exception {
        completeRealChooser(panel, methodName, null, false);
    }

    private static void approveRealChooser(final AeTunerPanel panel,
                                           final String methodName,
                                           final File target) throws Exception {
        completeRealChooser(panel, methodName, target, true);
    }

    private static void completeRealChooser(final AeTunerPanel panel,
                                            final String methodName,
                                            final File target,
                                            final boolean approve) throws Exception {
        final AtomicReference<Throwable> chooserFailure = new AtomicReference<Throwable>();
        Thread chooserThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    long deadline = System.currentTimeMillis() + 10000L;
                    while (System.currentTimeMillis() < deadline) {
                        final JFileChooser chooser = findVisibleFileChooser();
                        if (chooser != null) {
                            SwingUtilities.invokeAndWait(new Runnable() {
                                @Override
                                public void run() {
                                    if (approve) {
                                        chooser.setSelectedFile(target);
                                        chooser.approveSelection();
                                    } else {
                                        chooser.cancelSelection();
                                    }
                                }
                            });
                            return;
                        }
                        Thread.sleep(50L);
                    }
                    throw new AssertionError("Timed out waiting for JFileChooser from " + methodName);
                } catch (Throwable ex) {
                    chooserFailure.set(ex);
                }
            }
        }, "synthetic-file-chooser-completer");
        chooserThread.setDaemon(true);
        chooserThread.start();
        onEdt(new Runnable() {
            @Override
            public void run() {
                try {
                    invoke(panel, methodName);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        chooserThread.join(11000L);
        if (chooserFailure.get() != null) throwAsException(chooserFailure.get());
    }

    private static double doubleField(Object target, String name) throws Exception {
        return ((Double) field(target, name)).doubleValue();
    }

    private static JFileChooser findVisibleFileChooser() {
        for (Window window : Window.getWindows()) {
            if (!window.isShowing()) continue;
            JFileChooser chooser = findChooser(window);
            if (chooser != null) return chooser;
        }
        return null;
    }

    private static JFileChooser findChooser(Component component) {
        if (component instanceof JFileChooser) return (JFileChooser) component;
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JFileChooser chooser = findChooser(child);
                if (chooser != null) return chooser;
            }
        }
        return null;
    }

    private static JFrame showPanel(final AeTunerPanel panel) throws Exception {
        final AtomicReference<JFrame> result = new AtomicReference<JFrame>();
        onEdt(new Runnable() {
            @Override
            public void run() {
                JFrame frame = new JFrame("AE Tuner synthetic integration");
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setContentPane(panel);
                frame.setSize(new Dimension(1400, 1000));
                frame.setLocation(0, 0);
                frame.setVisible(true);
                result.set(frame);
            }
        });
        return result.get();
    }

    private static void renderPanel(final JComponent panel, final File file) throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                try {
                    int width = Math.max(1200, panel.getWidth());
                    int height = Math.max(900, panel.getHeight());
                    panel.setSize(width, height);
                    panel.doLayout();
                    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        panel.printAll(graphics);
                    } finally {
                        graphics.dispose();
                    }
                    ImageIO.write(image, "png", file);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        require(file.isFile() && file.length() > 0L, "Synthetic panel screenshot was not created");
    }

    private static void renderFrameAtSize(final JFrame frame,
                                          final JComponent panel,
                                          final AeTunerPanel aePanel,
                                          final File file,
                                          final int width,
                                          final int height) throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                try {
                    JScrollPane overviewScroll = (JScrollPane) field(aePanel, "overviewScroll");
                    JTabbedPane tabs = (JTabbedPane) overviewScroll.getParent();
                    tabs.setSelectedComponent(overviewScroll);
                    frame.setSize(new Dimension(width, height));
                    frame.validate();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        // Let width-change revalidation run before capturing the settled host layout.
        onEdt(new Runnable() {
            @Override
            public void run() {
                try {
                    frame.validate();
                    BufferedImage image = new BufferedImage(
                            panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        panel.printAll(graphics);
                    } finally {
                        graphics.dispose();
                    }
                    ImageIO.write(image, "png", file);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        require(file.isFile() && file.length() > 0L,
                "Narrow synthetic panel screenshot was not created");
    }

    private static void scrollOverviewToBottomAndRender(final AeTunerPanel panel,
                                                        final File file) throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                try {
                    JScrollPane overviewScroll = (JScrollPane) field(panel, "overviewScroll");
                    overviewScroll.getVerticalScrollBar().setValue(
                            overviewScroll.getVerticalScrollBar().getMaximum());
                    overviewScroll.doLayout();
                    BufferedImage image = new BufferedImage(
                            panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        panel.printAll(graphics);
                    } finally {
                        graphics.dispose();
                    }
                    ImageIO.write(image, "png", file);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        require(file.isFile() && file.length() > 0L,
                "Narrow Overview bottom screenshot was not created");
    }

    private static void assertPluginMetadata(AeTunerPlugin plugin) {
        require("aeTunerEpicefi".equals(plugin.getIdName()), "Unexpected plugin ID");
        require("AE Tuner (EPICEFI)".equals(plugin.getDisplayName()), "Unexpected plugin display name");
        require(AeTunerPlugin.VERSION.equals(plugin.getVersion()), "Plugin version mismatch");
        require(plugin.isMenuEnabled(), "Plugin menu unexpectedly disabled");
        require(plugin.displayPlugin("synthetic"), "Plugin refused a synthetic controller signature");
        require(plugin.getPluginPanel() instanceof AeTunerPanel, "Plugin did not return the real AE Tuner panel");
    }

    private static void assertRefactorOwnershipAndListeners(AeTunerPanel panel) throws Exception {
        String[] buttons = new String[]{
                "reconnectButton", "readProjectButton", "calibrateButton", "applyCalibrationButton",
                "resetButton", "saveCsvButton", "suggestTableButton", "suggestMapEstimateButton",
                "suggestBlendButton", "sessionReviewButton"
        };
        for (String name : buttons) {
            javax.swing.JButton button = (javax.swing.JButton) field(panel, name);
            require(button.getActionListeners().length == 1,
                    name + " must retain exactly one action listener after extraction");
            require(componentOccurrences(panel, button) == 1,
                    name + " must be owned by exactly one component container");
        }

        AbstractDocument thresholdDocument = (AbstractDocument)
                ((javax.swing.JTextField) field(panel, "thresholdField")).getDocument();
        int panelDocumentListeners = 0;
        for (javax.swing.event.DocumentListener listener : thresholdDocument.getDocumentListeners()) {
            if (listener.getClass().getName().startsWith(AeTunerPanel.class.getName())) {
                panelDocumentListeners++;
            }
        }
        require(panelDocumentListeners == 1,
                "Manual threshold must retain exactly one panel document listener");
        require(panel.getHierarchyListeners().length == 1,
                "Panel must retain exactly one refresh lifecycle hierarchy listener");

        Timer timer = (Timer) field(panel, "refreshTimer");
        require(timer.getActionListeners().length == 1,
                "Panel must own exactly one periodic refresh timer callback");

        Object presenter = field(panel, "uiPresenter");
        String[] presenterBindings = new String[]{
                "sampleRateLabel", "calibrationLabel", "eventCountLabel", "fuelPathStatusLabel",
                "sessionModeLabel", "guidanceLabel", "mapCollectionLabel", "sessionReviewLabel",
                "recommendationHistoryText", "overviewConnectionLabel", "overviewRateLabel",
                "calibrationCard"
        };
        for (String name : presenterBindings) {
            require(field(presenter, name) == field(panel, name),
                    "UI presenter received a duplicate component instead of panel-owned " + name);
        }

        require(componentOccurrences(panel, (Component) field(panel, "channelTable")) == 1,
                "Live-channel table must have one owner");
        require(componentOccurrences(panel, (Component) field(panel, "plotPanel")) == 1,
                "Event preview must have one owner");
        require(componentOccurrences(panel, (Component) field(panel, "lowerTabs")) == 1,
                "Lower tabs must have one owner");
    }

    private static void assertOverviewCardEquivalence(AeTunerPanel panel) throws Exception {
        refresh(panel);
        require("Stage 2: Wall Wetting".equals(cardText(panel, "workflowCard")),
                "Refactor changed Overview tuning-stage text");
        require("OFF — correct".equals(cardText(panel, "tpsCycleCard")),
                "Refactor changed disabled TPS cycle AE text");
        require(cardState(panel, "tpsCycleCard") == CardState.OFF,
                "Disabled TPS cycle AE must use the neutral OFF state");
        require(cardState(panel, "mapPredictCard") == CardState.GOOD,
                "Enabled MAP Predict must remain positive");
        require(cardState(panel, "wallWettingCard") == CardState.GOOD,
                "Enabled Wall Wetting must remain positive");
        require(cardState(panel, "instantFuelCard") == CardState.OFF,
                "Disabled Instant Fuel must remain neutral");
    }

    private static void assertClipboardCoordinatorEquivalence() throws Exception {
        String expected = "AE Tuner structural-refactor clipboard evidence";
        String error = AdvisoryExportCoordinator.copyToClipboard(expected);
        require(error == null, "Clipboard coordinator reported an unexpected failure: " + error);
        Object actual = Toolkit.getDefaultToolkit().getSystemClipboard()
                .getData(DataFlavor.stringFlavor);
        require(expected.equals(actual), "Clipboard coordinator changed copied text");
    }

    private static int componentOccurrences(Component root, Component target) {
        int count = root == target ? 1 : 0;
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                count += componentOccurrences(child, target);
            }
        }
        return count;
    }

    private static String selectedName(ChannelRole role) {
        switch (role) {
            case ENGINE_RUNNING: return "ready";
            case ENGINE_CRANKING: return "crank";
            case IGN_CUT_CODE: return "sparkCutReason";
            case FUEL_CUT_CODE: return "fuelCutReason";
            case IGN_OVERDWELL: return "overDwellNotScheduledCounter";
            case IGN_OVERCHARGE_WARNINGS: return "dwellOverChargeCounter";
            case IGN_UNDERCHARGE_WARNINGS: return "dwellUnderChargeCounter";
            case IGN_SPARK_OUT_OF_ORDER: return "sparkOutOfOrderCounter";
            default: return role.getCandidates()[0];
        }
    }

    private static List<TransientEvent> capturedEvents(AeTunerPanel panel) throws Exception {
        @SuppressWarnings("unchecked")
        List<TransientEvent> events = (List<TransientEvent>) field(panel, "capturedEvents");
        return new ArrayList<TransientEvent>(events);
    }

    private static void assertTechnicalDetailsPresentation(AeTunerPanel panel) throws Exception {
        final JScrollPane technicalScroll = (JScrollPane) field(panel, "technicalScroll");
        JScrollPane mainScroll = (JScrollPane) field(panel, "mainScroll");
        require(mainScroll.getVerticalScrollBarPolicy()
                        == ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                "Adding Technical-details scrolling must preserve the outer plugin scrollbar");
        require(technicalScroll.getVerticalScrollBarPolicy()
                        == ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                "Technical details must retain an independent vertical scrollbar");
        require(technicalScroll.getHorizontalScrollBarPolicy()
                        == ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
                "Technical details must wrap instead of requiring horizontal scrolling");
        require(technicalScroll.getViewport().getView() instanceof ViewportWidthPanel,
                "Technical details content must track its own viewport width");
        onEdt(new Runnable() {
            @Override
            public void run() {
                JTabbedPane tabs = (JTabbedPane) technicalScroll.getParent();
                tabs.setSelectedComponent(technicalScroll);
                tabs.doLayout();
                technicalScroll.doLayout();
            }
        });
        require(technicalScroll.getViewport().getView().getPreferredSize().height
                        > technicalScroll.getViewport().getExtentSize().height,
                "Technical details must provide a scrollable viewport for scaled or wrapped text");

        setField(panel, "detectionArmedNano", System.nanoTime() + 1000000000L);
        refresh(panel);
        String arming = ((javax.swing.JTextArea) field(panel, "calibrationLabel")).getText();
        require(arming.contains("event detection arming"),
                "Technical calibration status did not show the arming transition: " + arming);

        setField(panel, "detectionArmedNano", 0L);
        refresh(panel);
        String idle = ((javax.swing.JTextArea) field(panel, "calibrationLabel")).getText();
        require(idle.equals("TPS calibration: not run"),
                "Technical calibration status remained stale after arming: " + idle);
    }

    private static void assertResponsiveWrapping(final JFrame frame,
                                                 final AeTunerPanel panel) throws Exception {
        JScrollPane mainScroll = (JScrollPane) field(panel, "mainScroll");
        JScrollPane overviewScroll = (JScrollPane) field(panel, "overviewScroll");
        JScrollPane technicalScroll = (JScrollPane) field(panel, "technicalScroll");
        require(mainScroll.getVerticalScrollBarPolicy()
                        == ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                "Responsive layout must preserve the outer plugin scrollbar");
        require(overviewScroll.getVerticalScrollBarPolicy()
                        == ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                "Overview must provide independent overflow scrolling when cards wrap");
        require(technicalScroll.getVerticalScrollBarPolicy()
                        == ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                "Responsive layout must preserve Technical-details scrolling");

        JPanel controls = (JPanel) panel.getComponent(0);
        require(controls instanceof WrappingColumnPanel,
                "Control rows must be hosted by a width-aware vertical column");
        for (int i = 0; i < controls.getComponentCount(); i++) {
            Component row = controls.getComponent(i);
            require(row instanceof JPanel && ((JPanel) row).getLayout() instanceof WrapLayout,
                    "Control row " + i + " must use width-aware wrapping");
        }

        JPanel overview = (JPanel) overviewScroll.getViewport().getView();
        require(overview instanceof WrappingColumnPanel,
                "Overview rows must be hosted by a width-aware scrollable column");
        JPanel configurationRow = (JPanel) overview.getComponent(1);
        require(configurationRow.getLayout() instanceof WrapLayout,
                "Overview status cards must use width-aware wrapping");
        require(configurationRow.getComponentCount() == 6,
                "Configuration Overview row lost a status card");
        require(((JPanel) overview.getComponent(2)).getComponentCount() == 3,
                "Live-state Overview row lost a status card");
        require(((JPanel) overview.getComponent(3)).getComponentCount() == 4,
                "Session-progress Overview row lost a status card");
        require(((JPanel) overview.getComponent(4)).getComponentCount() == 3,
                "Safety-review Overview row lost a status card");

        final int[][] heights = new int[controls.getComponentCount()][2];
        final Dimension[] originalSizes = new Dimension[controls.getComponentCount()];
        onEdt(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < controls.getComponentCount(); i++) {
                    JPanel row = (JPanel) controls.getComponent(i);
                    originalSizes[i] = row.getSize();
                    row.setSize(1400, 1);
                    heights[i][0] = row.getPreferredSize().height;
                    row.setSize(420, 1);
                    heights[i][1] = row.getPreferredSize().height;
                    row.setSize(originalSizes[i]);
                }
                controls.doLayout();
            }
        });
        for (int i = 0; i < heights.length; i++) {
            require(heights[i][1] > heights[i][0],
                    "Control row " + i + " did not gain height when narrowed: wide="
                            + heights[i][0] + ", narrow=" + heights[i][1]);
        }

        int[] widths = new int[]{1400, 1024, 820, 700, 620};
        for (int width : widths) {
            assertResponsiveReachability(frame, panel, width);
        }
        characterizeNestedWheel(panel, technicalScroll, "Technical details");
        characterizeNestedWheel(panel, overviewScroll, "Overview");
        resizeFrame(frame, 1400, 1000);
    }

    private static void assertResponsiveReachability(final JFrame frame,
                                                      final AeTunerPanel panel,
                                                      final int width) throws Exception {
        resizeFrame(frame, width, 1000);
        onEdt(new Runnable() {
            @Override
            public void run() {
                try {
                    JScrollPane main = (JScrollPane) field(panel, "mainScroll");
                    JScrollPane technical = (JScrollPane) field(panel, "technicalScroll");
                    JScrollPane overview = (JScrollPane) field(panel, "overviewScroll");
                    JTabbedPane statusTabs = (JTabbedPane) technical.getParent();

                    require(main.getHorizontalScrollBarPolicy()
                                    == ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
                            "Outer page gained a horizontal scrollbar at " + width + " px");
                    require(technical.getHorizontalScrollBarPolicy()
                                    == ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
                            "Technical details gained a horizontal scrollbar at " + width + " px");
                    require(overview.getHorizontalScrollBarPolicy()
                                    == ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
                            "Overview gained a horizontal scrollbar at " + width + " px");

                    Container controls = (Container) panel.getComponent(0);
                    for (Component rowComponent : controls.getComponents()) {
                        Container row = (Container) rowComponent;
                        require(row.getHeight() > 0 && row.getWidth() <= controls.getWidth(),
                                "Control row was clipped or oversized at " + width + " px");
                    }

                    statusTabs.setSelectedComponent(technical);
                    frame.validate();
                    technical.getVerticalScrollBar().setValue(
                            technical.getVerticalScrollBar().getMaximum());
                    technical.doLayout();
                    Component technicalView = technical.getViewport().getView();
                    Component finalReview = (Component) field(panel, "sessionReviewLabel");
                    assertBottomReachable(technical, technicalView, finalReview,
                            "final Technical-details review", width);
                    require(technicalView.getWidth() <= technical.getViewport().getExtentSize().width,
                            "Technical content exceeded its viewport width at " + width + " px");

                    statusTabs.setSelectedComponent(overview);
                    frame.validate();
                    overview.getVerticalScrollBar().setValue(
                            overview.getVerticalScrollBar().getMaximum());
                    overview.doLayout();
                    Container overviewView = (Container) overview.getViewport().getView();
                    Component finalRow = overviewView.getComponent(overviewView.getComponentCount() - 1);
                    assertBottomReachable(overview, overviewView, finalRow,
                            "final Overview row", width);
                    for (Component row : overviewView.getComponents()) {
                        require(row.getBounds().x >= 0
                                        && row.getBounds().x + row.getBounds().width <= overviewView.getWidth(),
                                "Overview row exceeded the viewport width at " + width + " px");
                        if (row instanceof Container) {
                            for (Component card : ((Container) row).getComponents()) {
                                require(card.getWidth() > 0 && card.getHeight() > 0
                                                && card.getX() + card.getWidth() <= row.getWidth()
                                                && card.getY() + card.getHeight() <= row.getHeight(),
                                        "Overview card was unreachable at " + width + " px");
                            }
                        }
                    }

                    main.getVerticalScrollBar().setValue(main.getVerticalScrollBar().getMaximum());
                    main.doLayout();
                    Component mainView = main.getViewport().getView();
                    Component lowerTabs = (Component) field(panel, "lowerTabs");
                    assertBottomReachable(main, mainView, lowerTabs,
                            "lower tab region", width);
                    System.out.println("RESPONSIVE_WIDTH " + width + " passed");
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    private static void resizeFrame(final JFrame frame, final int width, final int height)
            throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                frame.setSize(new Dimension(width, height));
                frame.validate();
            }
        });
        // WrappingColumnPanel schedules revalidation after a width change.
        onEdt(new Runnable() {
            @Override
            public void run() {
                frame.validate();
            }
        });
    }

    private static void assertBottomReachable(JScrollPane scroll,
                                              Component view,
                                              Component target,
                                              String description,
                                              int width) {
        Rectangle targetBounds = SwingUtilities.convertRectangle(
                target.getParent(), target.getBounds(), view);
        Rectangle visible = scroll.getViewport().getViewRect();
        require(targetBounds.y + targetBounds.height <= visible.y + visible.height,
                description + " was not reachable at " + width + " px: target="
                        + targetBounds + ", visible=" + visible);
    }

    private static void characterizeNestedWheel(final AeTunerPanel panel,
                                                final JScrollPane inner,
                                                final String name) throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                try {
                    JScrollPane outer = (JScrollPane) field(panel, "mainScroll");
                    inner.getVerticalScrollBar().setValue(0);
                    outer.getVerticalScrollBar().setValue(0);
                    int innerBefore = inner.getVerticalScrollBar().getValue();
                    int outerBefore = outer.getVerticalScrollBar().getValue();
                    MouseWheelEvent inside = wheelEvent(inner, 1);
                    inner.dispatchEvent(inside);
                    int innerAfter = inner.getVerticalScrollBar().getValue();
                    require(innerAfter > innerBefore,
                            name + " did not scroll within its available range");
                    require(outer.getVerticalScrollBar().getValue() == outerBefore,
                            name + " moved the outer page while inner scrolling was available");
                    System.out.println("NESTED_WHEEL " + name + " inside consumed="
                            + inside.isConsumed() + ",innerBefore=" + innerBefore
                            + ",innerAfter=" + innerAfter);

                    int outerBeforeUpInside = outer.getVerticalScrollBar().getValue();
                    MouseWheelEvent insideUp = wheelEvent(inner, -1);
                    inner.dispatchEvent(insideUp);
                    require(inner.getVerticalScrollBar().getValue() < innerAfter,
                            name + " did not scroll upward within its available range");
                    require(outer.getVerticalScrollBar().getValue() == outerBeforeUpInside,
                            name + " moved the outer page during in-range upward scrolling");

                    inner.getVerticalScrollBar().setValue(0);
                    outer.getVerticalScrollBar().setValue(
                            outer.getVerticalScrollBar().getMaximum());
                    int upperOuterBefore = outer.getVerticalScrollBar().getValue();
                    MouseWheelEvent upper = wheelEvent(inner, -1);
                    inner.dispatchEvent(upper);
                    int upperOuterAfter = outer.getVerticalScrollBar().getValue();
                    require(inner.getVerticalScrollBar().getValue() == 0,
                            name + " moved above its upper boundary during handoff");
                    require(upperOuterAfter < upperOuterBefore,
                            name + " did not hand an upper-boundary wheel event to the outer page");
                    System.out.println("NESTED_WHEEL " + name + " upperBoundary consumed="
                            + upper.isConsumed() + ",outerBefore=" + upperOuterBefore
                            + ",outerAfter=" + upperOuterAfter);

                    inner.getVerticalScrollBar().setValue(inner.getVerticalScrollBar().getMaximum());
                    int innerLower = inner.getVerticalScrollBar().getValue();
                    outer.getVerticalScrollBar().setValue(0);
                    int before = outer.getVerticalScrollBar().getValue();
                    MouseWheelEvent event = wheelEvent(inner, 1);
                    inner.dispatchEvent(event);
                    int after = outer.getVerticalScrollBar().getValue();
                    require(inner.getVerticalScrollBar().getValue() == innerLower,
                            name + " moved below its lower boundary during handoff");
                    require(after > before,
                            name + " did not hand a lower-boundary wheel event to the outer page");
                    System.out.println("NESTED_WHEEL " + name + " lowerBoundary consumed="
                            + event.isConsumed() + ",outerBefore=" + before + ",outerAfter=" + after);

                    inner.getVerticalScrollBar().setValue(0);
                    outer.getVerticalScrollBar().setValue(0);
                    MouseWheelEvent bothUpper = wheelEvent(inner, -1);
                    inner.dispatchEvent(bothUpper);
                    require(inner.getVerticalScrollBar().getValue() == 0
                                    && outer.getVerticalScrollBar().getValue() == 0,
                            name + " moved when both panes were at their upper boundary");

                    inner.getVerticalScrollBar().setValue(inner.getVerticalScrollBar().getMaximum());
                    outer.getVerticalScrollBar().setValue(outer.getVerticalScrollBar().getMaximum());
                    int bothInnerLower = inner.getVerticalScrollBar().getValue();
                    int bothOuterLower = outer.getVerticalScrollBar().getValue();
                    MouseWheelEvent bothLower = wheelEvent(inner, 1);
                    inner.dispatchEvent(bothLower);
                    require(inner.getVerticalScrollBar().getValue() == bothInnerLower
                                    && outer.getVerticalScrollBar().getValue() == bothOuterLower,
                            name + " moved when both panes were at their lower boundary");
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    private static MouseWheelEvent wheelEvent(Component target, int rotation) {
        return new MouseWheelEvent(target, MouseEvent.MOUSE_WHEEL,
                System.currentTimeMillis(), 0, 10, 10, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, rotation);
    }

    private static void refresh(final AeTunerPanel panel) throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                try {
                    invoke(panel, "refreshUi");
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }


    private static int historySize(AeTunerPanel panel) throws Exception {
        RecommendationHistory history = (RecommendationHistory) field(panel, "recommendationHistory");
        return history.size();
    }

    private static String historyText(AeTunerPanel panel) throws Exception {
        RecommendationHistory history = (RecommendationHistory) field(panel, "recommendationHistory");
        return history.toDisplayText();
    }

    private static void openSessionGuidance(final AeTunerPanel panel) throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() {
                try {
                    Component card = (Component) field(panel, "nextActionCard");
                    card.dispatchEvent(new MouseEvent(card, MouseEvent.MOUSE_CLICKED,
                            System.currentTimeMillis(), 0, 5, 5, 1, false));
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    private static String selectedLowerTabTitle(AeTunerPanel panel) throws Exception {
        JTabbedPane tabs = (JTabbedPane) field(panel, "lowerTabs");
        return tabs.getTitleAt(tabs.getSelectedIndex());
    }

    private static String cardText(AeTunerPanel panel, String fieldName) throws Exception {
        Object card = field(panel, fieldName);
        Object text = field(card, "lastText");
        return text == null ? "" : text.toString();
    }

    private static CardState cardState(AeTunerPanel panel, String fieldName) throws Exception {
        Object card = field(panel, fieldName);
        return (CardState) field(card, "lastState");
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static File outputDirectory() {
        String configured = System.getenv("SYNTHETIC_INTEGRATION_OUT");
        return new File(configured == null || configured.trim().isEmpty()
                ? "target/synthetic-plugin-integration" : configured);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        try {
            return method.invoke(target);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw ex;
        }
    }

    private static void onEdt(Runnable runnable) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
            return;
        }
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    runnable.run();
                } catch (Throwable ex) {
                    failure.set(ex);
                }
            }
        });
        if (failure.get() != null) throwAsException(failure.get());
    }

    private static void flushEdt() throws Exception {
        onEdt(new Runnable() {
            @Override
            public void run() { }
        });
    }

    private static void throwAsException(Throwable failure) throws Exception {
        if (failure instanceof Exception) throw (Exception) failure;
        if (failure instanceof Error) throw (Error) failure;
        throw new RuntimeException(failure);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class PluginFixture {
        final AeTunerPlugin plugin;
        final AeTunerPanel panel;
        final EnumMap<ChannelRole, String> channelNames;

        PluginFixture(AeTunerPlugin plugin, AeTunerPanel panel,
                      EnumMap<ChannelRole, String> channelNames) {
            this.plugin = plugin;
            this.panel = panel;
            this.channelNames = channelNames;
        }
    }
}
