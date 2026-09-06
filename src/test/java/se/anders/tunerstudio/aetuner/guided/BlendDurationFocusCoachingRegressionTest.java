package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.EnumMap;

/** Permanent contract for Foundation-grade Blend Duration Driver Focus coaching. */
public final class BlendDurationFocusCoachingRegressionTest {
    private BlendDurationFocusCoachingRegressionTest() { }

    public static void main(String[] args) {
        sessionPublishesDriverFirstFocus();
        System.out.println("BlendDurationFocusCoachingRegressionTest passed");
    }

    private static void sessionPublishesDriverFirstFocus() {
        GuidedFocusHub.clear();
        BlendDurationGuidedSession session = new BlendDurationGuidedSession();
        session.start(new BlendDurationCaptureConfig(
                2600.0, 20.0, 5, 0, false,
                new double[]{1500.0, 2600.0, 3800.0, 5000.0},
                new double[]{0.08, 0.26, 0.24, 0.18}));

        GuidedFocusHub.State started = GuidedFocusHub.snapshot();
        require(started.recipe == GuidedTuningRecipe.BLEND_DURATION,
                "Blend Duration did not own Guided Focus when the session started");
        require(started.blendDuration != null
                        && started.blendDuration.phase == BlendDurationFocusModel.DriverPhase.GET_STEADY,
                "Blend Duration start did not publish the simple GET STEADY driver phase");
        require(started.blendDuration.instruction.contains("GET STEADY")
                        && started.blendDuration.numericalApplyWithheld,
                "Blend Duration Driver Focus lost the physical-action/no-Apply boundary");
        require(Math.abs(started.blendDuration.currentBlendDuration - 0.26) < 1.0e-9,
                "Driver Focus did not retain the current working Blend Duration context");

        for (int i = 0; i < 24; i++) {
            double seconds = i * 0.05;
            session.accept(sample(seconds,
                    2580.0 + i * 1.5,
                    50.0 + i * 0.02,
                    8.0 + i * 0.005,
                    false, false, 50.0));
        }

        GuidedFocusHub.State ready = GuidedFocusHub.snapshot();
        require(ready.recipe == GuidedTuningRecipe.BLEND_DURATION
                        && ready.blendDuration != null,
                "live Blend Duration samples stopped feeding Guided Focus");
        require(ready.blendDuration.phase == BlendDurationFocusModel.DriverPhase.OPEN_AND_SETTLE,
                "settled road baseline did not transition Driver Focus to the opening action");
        require(ready.blendDuration.instruction.contains("OPEN SMOOTHLY")
                        && ready.blendDuration.status.contains("Matching events 0/5")
                        && ready.blendDuration.rpmInRange,
                "READY Focus does not give the driver one clear opening/progress instruction");

        BlendDurationGuidedFocusPanel panel = new BlendDurationGuidedFocusPanel();
        panel.updateModel(ready.blendDuration, "engineering fallback");
        require(panel.instructionForTest().contains("OPEN SMOOTHLY")
                        && panel.eventProgressForTest().contains("MATCHING EVENTS 0/5")
                        && panel.rpmTextForTest().contains("target 2600")
                        && panel.tpsTextForTest().contains("accepted +10 to +30")
                        && panel.detailsForTest().contains("Numerical Blend Duration Apply remains intentionally withheld"),
                "dedicated Blend Duration Focus card lost driver progress or engineering boundary");
    }

    private static LiveSample sample(double seconds, double rpm,
                                     double map, double tps,
                                     boolean detector, boolean prediction,
                                     double fallbackMap) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.MAP, map);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.FALLBACK_MAP, fallbackMap);
        values.put(ChannelRole.EFFECTIVE_MAP, map);
        values.put(ChannelRole.ENGINE_RUNNING, 1.0);
        values.put(ChannelRole.ENGINE_CRANKING, 0.0);
        values.put(ChannelRole.FUEL_CUT, 0.0);
        values.put(ChannelRole.TOTAL_SPARK_CUT, 0.0);
        values.put(ChannelRole.TRIGGER_ERROR, 0.0);
        values.put(ChannelRole.MAP_PRED_ACTIVE, prediction ? 1.0 : 0.0);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, detector ? 1.0 : 0.0);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, detector ? 3.0 : 0.0);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.0);
        long nano = Math.round(seconds * 1000000000.0);
        return new LiveSample(nano, seconds, values,
                detector ? 60.0 : 0.0, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
