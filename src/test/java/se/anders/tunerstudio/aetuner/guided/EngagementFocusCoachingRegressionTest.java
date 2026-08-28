package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.EngagementDetectionMethodModule;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Product regression for the coaching-first TPS Movement / Timing Focus. */
public final class EngagementFocusCoachingRegressionTest {
    private EngagementFocusCoachingRegressionTest() { }

    public static void main(String[] args) {
        liveModelFollowsProductionTpsChangeAndThreshold();
        driverViewHasNoRootScrollAndOnlySecondaryDeltaWindowEdit();
        capturePublishesConservativeProductionSignalCues();
        System.out.println("EngagementFocusCoachingRegressionTest passed");
    }

    private static void liveModelFollowsProductionTpsChangeAndThreshold() {
        EngagementFocusModel model = EngagementFocusModel.build(
                snapshot(), sample(0.0, 1.05, 0.90, 1.05),
                GuidedCaptureState.CAPTURING, 2, 5, 100, 100);
        require(model.liveReady, "complete TPS movement diagnostics did not become live-ready");
        require(model.expectedModel, "Dual Stride / Newest working context was not recognized");
        requireClose(1.05, model.productionDeltaTps,
                "production TPS change was not retained as coached signal");
        requireClose(1.05, model.selectedOutput,
                "compatibility selected-output alias must follow production TPS change");
        require(model.selectedAboveThreshold,
                "production detected TPS change above threshold was not shown triggered");
        require(model.nextActionText().contains("HOLD / OBSERVE"),
                "triggered state did not produce hold/observe guidance");
        require(model.maneuverPlanText().contains("Delta Window")
                        && model.maneuverPlanText().contains("stacked short stabs"),
                "maneuver plan lost Delta Window A/B or stacked-event guidance");
        require(model.prerequisiteText().contains("read-only")
                        && model.prerequisiteText().contains("~200 Hz"),
                "read-only detector/Fast Callback prerequisites are not visible");
    }

    private static void driverViewHasNoRootScrollAndOnlySecondaryDeltaWindowEdit() {
        EngagementDetectionWriteSelection.resetForTest();
        EngagementDetectionWriteSelection.observeWorkingTune(snapshot());
        EngagementDetectionGuidedFocusPanel panel = new EngagementDetectionGuidedFocusPanel();
        panel.updateModel(EngagementFocusModel.build(
                snapshot(), sample(0.0, 1.05, 0.90, 1.05),
                GuidedCaptureState.CAPTURING, 2, 5, 100, 100));
        panel.setDriverView(true);
        require(!panel.settingsToggleVisibleForTest(),
                "Driver view still exposes the Delta Window editor toggle");
        require(!panel.settingsPanelVisibleForTest(),
                "Driver view still exposes setting controls");
        require(!panel.hasRootScrollForTest(),
                "Driver Focus regained a root scroll container");
        require(panel.selectedSignalPercentForTest() > 100,
                "detected-change visual did not cross the 100% threshold marker");
        require(panel.detectorStateForTest().contains("TRIGGERED"),
                "live state did not show production threshold crossing");
        require(panel.currentTextForTest().contains("detector Dual stride, newest (read-only)")
                        && panel.currentTextForTest().contains("Sample Length 0.05 s (read-only)")
                        && panel.currentTextForTest().contains("Fast Callback ON (~200 Hz) (read-only)"),
                "scrapped/read-only settings are not clearly distinguished from Delta Window");

        panel.setDriverView(false);
        require(panel.settingsToggleVisibleForTest(),
                "non-driver view lost the secondary Delta Window A/B control");
        require(!panel.settingsPanelVisibleForTest(),
                "Delta Window experiment should remain collapsed by default");
        require(panel.deltaWindowEnabledForTest(),
                "qualified Delta Window A/B control did not enable from working tune");
    }

    private static void capturePublishesConservativeProductionSignalCues() {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        final List<GuidedWorkflowEvent> events = new ArrayList<GuidedWorkflowEvent>();
        session.setWorkflowEventListener(new GuidedWorkflowEvent.Listener() {
            @Override public void onGuidedWorkflowEvent(GuidedWorkflowEvent event,
                                                        String detail,
                                                        long nanoTime) {
                events.add(event);
            }
        });
        session.start(new EngagementDetectionMethodModule(), snapshot(), 5, 20, 115.0);

        session.accept(sample(0.00, 0.40, 0.90, 0.40));
        require(events.contains(GuidedWorkflowEvent.READY_ENTERED),
                "complete below-threshold data did not emit READY cue");

        session.accept(sample(0.02, 1.05, 0.90, 1.05));
        require(events.contains(GuidedWorkflowEvent.TARGET_ACQUIRED),
                "production TPS change threshold crossing did not emit TARGET cue");

        session.accept(sample(0.20, 0.32, 0.90, 0.32));
        require(events.contains(GuidedWorkflowEvent.RETURN_TO_BASELINE),
                "production TPS change clear did not emit RETURN cue");
        require(!events.contains(GuidedWorkflowEvent.EVENT_ACCEPTED),
                "threshold transition was falsely labeled as accepted evidence");

        GuidedFocusHub.State focus = GuidedFocusHub.snapshot();
        require(focus.recipe == GuidedTuningRecipe.ENGAGEMENT_DETECTION
                        && focus.engagement != null,
                "TPS Movement capture did not publish a live Guided Focus model");
        requireClose(0.32, focus.engagement.productionDeltaTps,
                "published Focus did not carry latest production detected TPS change");
        session.reset();
    }

    private static LiveSample sample(double seconds,
                                     double productionDelta,
                                     double threshold,
                                     double newest) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, Double.valueOf(1800.0));
        values.put(ChannelRole.TPS, Double.valueOf(18.0));
        values.put(ChannelRole.DELTA_TPS, Double.valueOf(productionDelta));
        values.put(ChannelRole.ACCEL_THRESHOLD, Double.valueOf(threshold));
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
