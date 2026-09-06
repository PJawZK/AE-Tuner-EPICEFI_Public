package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.EnumMap;

/** Product regression for the lightweight passive TPS Movement / Timing Focus. */
public final class EngagementFocusCoachingRegressionTest {
    private EngagementFocusCoachingRegressionTest() { }

    public static void main(String[] args) {
        liveModelRetainsDiagnosticsWithoutTargetChoreography();
        driverViewShowsReferenceFirstPassiveFlow();
        presentationBuildIsThrottledWithoutSweepDependency();
        System.out.println("EngagementFocusCoachingRegressionTest passed");
    }

    private static void liveModelRetainsDiagnosticsWithoutTargetChoreography() {
        EngagementPassiveCapture.reset();
        EngagementFocusModel model = EngagementFocusModel.build(
                snapshot(), sample(5.0, 18.0, 1.05, 0.90, 1.05, 12.0, true),
                GuidedCaptureState.CAPTURING, 0, 6, 100, 100);
        require(model.liveReady, "complete passive TPS channels did not become live-ready");
        require(model.expectedModel, "Dual Stride / Newest working context was not recognized");
        requireClose(1.05, model.productionDeltaTps,
                "production TPS change was not retained as diagnostic ECU evidence");
        require(model.selectedAboveThreshold,
                "explicit ECU detector state was not retained diagnostically");
        require(!model.nextActionText().contains("HOLD / OBSERVE"),
                "passive Foundation 1 still turns detector state into hold choreography");
        require(model.maneuverPlanText().contains("presentation-only")
                        && model.maneuverPlanText().contains("SETTLING/READY only separates physical events")
                        && model.maneuverPlanText().contains("No controller writes are performed while driving"),
                "passive maneuver plan lost reference/re-arm/no-write boundaries");
        require(model.prerequisiteText().contains("capture writes: NONE"),
                "passive prerequisite text does not expose the no-write road boundary");
    }

    private static void driverViewShowsReferenceFirstPassiveFlow() {
        EngagementPassiveCapture.reset();
        EngagementPassiveCapture.configureTarget(6);
        EngagementDetectionGuidedFocusPanel panel = new EngagementDetectionGuidedFocusPanel();
        panel.updateModel(EngagementFocusModel.build(
                snapshot(), sample(5.0, 18.0, 0.20, 0.90, 0.20, 0.0, false),
                GuidedCaptureState.CAPTURING, 0, 6, 100, 100));
        panel.setDriverView(true);
        require(!panel.settingsToggleVisibleForTest()
                        && !panel.settingsPanelVisibleForTest()
                        && !panel.requestedDeltaWindowEnabledForTest(),
                "passive driver view still exposes retired sweep/manual controls");
        require(!panel.hasRootScrollForTest(),
                "Driver Focus regained a root scroll container");
        require(panel.driverInstructionForTest().contains("ONE COMFORTABLE PEDAL OPENING"),
                "driver view does not establish the first-event visual reference flow");
        require(panel.sweepTargetTextForTest().contains("not a hard target"),
                "driver/details contract implies the visual marker is an acceptance target");
        require(panel.currentTextForTest().contains("capture is read-only"),
                "Focus does not expose its read-only capture boundary");
        require(!Double.isFinite(panel.driverReferenceMarkerForTest())
                        && panel.driverRepeatMarkerCountForTest() == 0,
                "fresh passive Focus unexpectedly inherited old visual markers");
    }

    private static void presentationBuildIsThrottledWithoutSweepDependency() {
        EngagementPassiveCapture.reset();
        EngagementFocusModel.resetPresentationCacheForTest();
        AeProjectSnapshot snapshot = snapshot();
        EngagementFocusModel first = EngagementFocusModel.build(snapshot,
                sample(1.000, 10.0, 0.20, 1.0, 0.20, 0.0, false),
                GuidedCaptureState.CAPTURING, 0, 6, 1, 1);
        EngagementFocusModel second = EngagementFocusModel.build(snapshot,
                sample(1.025, 10.1, 0.20, 1.0, 0.20, 0.1, false),
                GuidedCaptureState.CAPTURING, 0, 6, 2, 2);
        require(first == second,
                "presentation path rebuilt inside its 100 ms throttle without a passive-event revision");
        require(EngagementFocusModel.presentationBuildCountForTest() == 1,
                "passive presentation throttle did not suppress redundant builds");
        require(first.sweep == null,
                "passive Focus still carries a production controlled-sweep snapshot");
    }

    private static LiveSample sample(double seconds, double tps,
                                     double productionDelta, double threshold,
                                     double newest, double tpsRate,
                                     boolean ecuDetectorActive) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, 1800.0);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.VSS, 45.0);
        values.put(ChannelRole.DELTA_TPS, productionDelta);
        values.put(ChannelRole.ACCEL_THRESHOLD, threshold);
        values.put(ChannelRole.AE_DELTA_NEWEST_PAIR, newest);
        values.put(ChannelRole.AE_WINDOW_MS, 50.0);
        values.put(ChannelRole.AE_WINDOW_SAMPLES, 10.0);
        values.put(ChannelRole.AE_DELTA_STRIDE, 5.0);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, ecuDetectorActive ? 1.0 : 0.0);
        return new LiveSample((long) (seconds * 1.0e9), seconds, values, tpsRate, 0.0);
    }

    private static AeProjectSnapshot snapshot() {
        return new AeProjectSnapshot(
                "cfg",
                new double[]{2.0}, new double[]{20.0}, new double[][]{{1.0}},
                new double[]{1000.0}, new double[]{1.5},
                1.0, 0.0,
                new double[0], new double[0],
                false, false, "none", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                "Dual stride, newest", 25.0, 0.050, true, 0.10);
    }

    private static void requireClose(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > 0.000001) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
