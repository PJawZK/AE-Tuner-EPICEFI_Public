package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.EngagementDetectionMethodModule;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Product regression for the coaching-first Detector Model / Timing Focus. */
public final class EngagementFocusCoachingRegressionTest {
    private EngagementFocusCoachingRegressionTest() { }

    public static void main(String[] args) {
        liveModelFollowsWorkingDetectorNotAnotherModel();
        driverViewKeepsSettingEditorSecondary();
        capturePublishesLiveFocusAndConservativeCues();
        System.out.println("EngagementFocusCoachingRegressionTest passed");
    }

    private static void liveModelFollowsWorkingDetectorNotAnotherModel() {
        AeProjectSnapshot snapshot = snapshot();
        LiveSample sample = sample(0.0,
                0.55, 1.30, 1.10, 0.80, 1.05,
                0.90, 1.05);
        EngagementFocusModel model = EngagementFocusModel.build(
                snapshot, sample, GuidedCaptureState.CAPTURING,
                2, 5, 100, 100);

        require(model.liveReady, "complete detector diagnostics did not become live-ready");
        require(model.workingModel != null && model.workingModel.controllerValue() == 4,
                "working Dual stride model was not resolved");
        requireClose(1.05, model.selectedOutput,
                "selected output must follow newest-pair for working model 4");
        require(model.selectedAboveThreshold,
                "working newest-pair output above threshold was not shown triggered");
        require(model.legacy < model.threshold && model.timed > model.threshold,
                "synthetic comparison fixture no longer distinguishes alternate models");
        require(model.nextActionText().contains("HOLD / OBSERVE"),
                "triggered state did not produce driver-facing hold/observe guidance");
        require(model.maneuverPlanText().contains("change ONE setting")
                        && model.maneuverPlanText().contains("stacked short stabs"),
                "A/B maneuver recipe lost one-setting repeat guidance");
    }

    private static void driverViewKeepsSettingEditorSecondary() {
        EngagementDetectionGuidedFocusPanel panel =
                new EngagementDetectionGuidedFocusPanel();
        EngagementFocusModel model = EngagementFocusModel.build(
                snapshot(), sample(0.0,
                        0.55, 1.30, 1.10, 0.80, 1.05,
                        0.90, 1.05),
                GuidedCaptureState.CAPTURING, 2, 5, 100, 100);
        panel.updateModel(model);
        panel.setDriverView(true);
        require(!panel.settingsToggleVisibleForTest(),
                "Driver view still exposes the setting-editor toggle");
        require(!panel.settingsPanelVisibleForTest(),
                "Driver view still exposes setting controls as primary UI");
        require(panel.selectedSignalPercentForTest() > 100,
                "selected detector visual did not cross the 100% threshold marker");
        require(panel.detectorStateForTest().contains("TRIGGERED"),
                "live detector state did not show threshold crossing");

        panel.setDriverView(false);
        require(panel.settingsToggleVisibleForTest(),
                "non-driver view lost access to secondary A/B setting controls");
        require(!panel.settingsPanelVisibleForTest(),
                "secondary setting controls should remain collapsed by default");
    }

    private static void capturePublishesLiveFocusAndConservativeCues() {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        final List<GuidedWorkflowEvent> events = new ArrayList<GuidedWorkflowEvent>();
        session.setWorkflowEventListener(new GuidedWorkflowEvent.Listener() {
            @Override public void onGuidedWorkflowEvent(GuidedWorkflowEvent event,
                                                        String detail,
                                                        long nanoTime) {
                events.add(event);
            }
        });
        session.start(new EngagementDetectionMethodModule(), snapshot(),
                5, 20, 115.0);

        session.accept(sample(0.00,
                0.40, 0.50, 0.45, 0.35, 0.40,
                0.90, 0.40));
        require(events.contains(GuidedWorkflowEvent.READY_ENTERED),
                "complete below-threshold detector data did not emit READY cue");

        session.accept(sample(0.02,
                0.55, 1.30, 1.10, 0.80, 1.05,
                0.90, 1.05));
        require(events.contains(GuidedWorkflowEvent.TARGET_ACQUIRED),
                "selected detector threshold crossing did not emit target cue");

        session.accept(sample(0.20,
                0.35, 0.40, 0.38, 0.30, 0.32,
                0.90, 0.32));
        require(events.contains(GuidedWorkflowEvent.RETURN_TO_BASELINE),
                "selected detector clear did not emit return cue");
        require(!events.contains(GuidedWorkflowEvent.EVENT_ACCEPTED),
                "detector threshold transition was falsely labeled as accepted evidence");

        GuidedFocusHub.State focus = GuidedFocusHub.snapshot();
        require(focus.recipe == GuidedTuningRecipe.ENGAGEMENT_DETECTION
                        && focus.engagement != null,
                "Engagement capture did not publish a live Guided Focus model");
        requireClose(0.32, focus.engagement.selectedOutput,
                "published Focus did not carry latest selected detector output");
        require(focus.engagement.activityEvents >= 1,
                "live Focus lost broad detector-activity coverage count");
        session.reset();
    }

    private static LiveSample sample(double seconds,
                                     double legacy,
                                     double timed,
                                     double span,
                                     double floor,
                                     double newest,
                                     double threshold,
                                     double productionDelta) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, Double.valueOf(1800.0));
        values.put(ChannelRole.TPS, Double.valueOf(18.0));
        values.put(ChannelRole.DELTA_TPS, Double.valueOf(productionDelta));
        values.put(ChannelRole.ACCEL_THRESHOLD, Double.valueOf(threshold));
        values.put(ChannelRole.AE_DELTA_MAX_STEP, Double.valueOf(legacy));
        values.put(ChannelRole.AE_DELTA_TIMED, Double.valueOf(timed));
        values.put(ChannelRole.AE_DELTA_SPAN, Double.valueOf(span));
        values.put(ChannelRole.AE_DELTA_FLOOR, Double.valueOf(floor));
        values.put(ChannelRole.AE_DELTA_NEWEST_PAIR, Double.valueOf(newest));
        values.put(ChannelRole.AE_WINDOW_MS, Double.valueOf(50.0));
        values.put(ChannelRole.AE_WINDOW_SAMPLES, Double.valueOf(10.0));
        values.put(ChannelRole.AE_DELTA_STRIDE, Double.valueOf(5.0));
        return new LiveSample((long) (seconds * 1.0e9), seconds, values, 0.0, 0.0);
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
