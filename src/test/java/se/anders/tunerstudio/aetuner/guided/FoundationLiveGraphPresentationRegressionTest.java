package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.FoundationThresholdFocusBridge;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;

/** Permanent presentation contract for the two Foundation v0.19 driving views. */
public final class FoundationLiveGraphPresentationRegressionTest {
    private static final Path GUIDED = Paths.get("src/main/java/se/anders/tunerstudio/aetuner/guided");

    private FoundationLiveGraphPresentationRegressionTest() { }

    public static void main(String[] args) throws Exception {
        tpsMovementUsesTwoColumnRollingGraph();
        thresholdUsesTwoColumnRollingGraph();
        rollingHistoriesAccumulatePublishedProductionModels();
        System.out.println("FoundationLiveGraphPresentationRegressionTest passed");
    }

    private static void tpsMovementUsesTwoColumnRollingGraph() throws Exception {
        String source = source("GuidedV019TpsTimingFocus.java");
        require(source.contains("private final LiveMovementGraph liveGraph")
                        && source.contains("TPS movement — what to do")
                        && source.contains("Live movement — TPS + detector")
                        && source.contains("gc.weightx = 0.38")
                        && source.contains("gc.weightx = 0.62")
                        && source.contains("private static final int CAPACITY = 120")
                        && source.contains("Path2D.Double path")
                        && source.contains("first accepted reference")
                        && !source.contains("class LatestMovement")
                        && !source.contains("class ReferenceMarkers")
                        && !source.contains("g2.drawLine(l + 20, b"),
                "TPS Movement / Timing lost the left-movement/right-live-graph contract or regained vertical live bars");
    }

    private static void thresholdUsesTwoColumnRollingGraph() throws Exception {
        String source = source("GuidedV019ThresholdFocus.java");
        require(source.contains("Threshold evidence — what it means")
                        && source.contains("Live detector activity — signal vs threshold")
                        && source.contains("gc.weightx = 0.38")
                        && source.contains("gc.weightx = 0.62")
                        && source.contains("private static final int CAPACITY = 120")
                        && source.contains("Path2D.Double path")
                        && source.contains("Rolling detector activity")
                        && source.contains("diagnosticActivity.update(m)")
                        && !source.contains("class ActivityChartProxy")
                        && !source.contains("g2.drawLine(l + (r - l) / 2, b")
                        && !source.contains("g2.fillRect(l + 40, ys, 20, b - ys)"),
                "Threshold / Sensitivity lost the Blend-style rolling-graph contract or regained vertical live bars");
    }

    /**
     * Prove that the bounded graph histories consume successive published Focus
     * snapshots. This does not feed those points back into either evidence model.
     */
    private static void rollingHistoriesAccumulatePublishedProductionModels() {
        EngagementPassiveCapture.reset();
        EngagementFocusModel.resetPresentationCacheForTest();
        FoundationThresholdFocusBridge.reset();
        GuidedFocusHub.clear();

        GuidedV019TpsTimingFocus tps = new GuidedV019TpsTimingFocus(null, null);
        AeProjectSnapshot timingTune = engagementSnapshot();
        for (int i = 0; i < 6; i++) {
            LiveSample sample = sample(i + 1L, (i + 1) * 0.20,
                    1800.0 + i * 80.0, 6.0 + i * 2.0,
                    0.35 + i * 0.25, 1.0, 4.0 + i, i >= 3);
            EngagementFocusModel model = EngagementFocusModel.build(
                    timingTune, sample, GuidedCaptureState.CAPTURING,
                    i, 6, 20 + i * 20, 140);
            GuidedFocusHub.publishEngagement(GuidedCaptureState.CAPTURING, model,
                    "rolling graph regression");
            tps.refreshFromProduction();
        }
        require(tps.liveGraphSamplesForTest() >= 6,
                "TPS Movement / Timing rolling graph did not accumulate successive production snapshots");

        FoundationThresholdFocusBridge.reset();
        GuidedFocusHub.clear();
        AeProjectSnapshot thresholdTune = thresholdSnapshot();
        FoundationThresholdFocusBridge.observeWorkingTune(thresholdTune);
        GuidedV019ThresholdFocus threshold = new GuidedV019ThresholdFocus(null, null);
        for (int i = 0; i < 6; i++) {
            LiveSample sample = sample(100L + i, 2.0 + i * 0.20,
                    1500.0 + i * 200.0, 11.0 + i,
                    0.25 + i * 0.22, 1.0, 3.0 + i, i >= 4);
            FoundationThresholdFocusBridge.observe(sample);
            GuidedFocusHub.publish(GuidedTuningRecipe.FOUNDATION_THRESHOLD,
                    GuidedCaptureState.CAPTURING, null,
                    "rolling graph regression");
            threshold.refreshFromProduction();
        }
        require(threshold.liveGraphSamplesForTest() >= 6,
                "Threshold / Sensitivity rolling graph did not accumulate successive production snapshots");

        FoundationThresholdFocusBridge.reset();
        GuidedFocusHub.clear();
        EngagementPassiveCapture.reset();
    }

    private static LiveSample sample(long index, double seconds,
                                     double rpm, double tps,
                                     double delta, double threshold,
                                     double tpsRate, boolean detectorActive) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.DELTA_TPS, delta);
        values.put(ChannelRole.ACCEL_THRESHOLD, threshold);
        values.put(ChannelRole.AE_DELTA_NEWEST_PAIR, delta);
        values.put(ChannelRole.AE_WINDOW_MS, 50.0);
        values.put(ChannelRole.AE_WINDOW_SAMPLES, 10.0);
        values.put(ChannelRole.AE_DELTA_STRIDE, 5.0);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, detectorActive ? 1.0 : 0.0);
        values.put(ChannelRole.TPS_DECEL_ACTIVE, 0.0);
        return new LiveSample(index * 200000000L, seconds, values, tpsRate, 0.0);
    }

    private static AeProjectSnapshot engagementSnapshot() {
        return new AeProjectSnapshot(
                "foundation-live-graph-timing",
                new double[]{2.0}, new double[]{20.0}, new double[][]{{1.0}},
                new double[]{1000.0}, new double[]{1.5},
                1.0, 0.0, new double[0], new double[0],
                false, false, "none", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                "Dual stride, newest", 25.0, 0.050, true, 0.10);
    }

    private static AeProjectSnapshot thresholdSnapshot() {
        return new AeProjectSnapshot(
                "foundation-live-graph-threshold",
                new double[0], new double[0], new double[0][0],
                new double[]{1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500},
                new double[]{0.30, 1.00, 0.30, 0.30, 1.00, 0.30, 0.30, 0.30},
                0.10, 0.0, new double[0], new double[0],
                true, false, "off", false, false,
                false, false, new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0]);
    }

    private static String source(String file) throws Exception {
        return new String(Files.readAllBytes(GUIDED.resolve(file)), StandardCharsets.UTF_8);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}