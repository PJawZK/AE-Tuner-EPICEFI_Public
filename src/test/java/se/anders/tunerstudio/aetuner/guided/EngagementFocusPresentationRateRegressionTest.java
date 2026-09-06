package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.EnumMap;

/** Keeps high-rate capture work separate from the human-rate Focus presentation. */
public final class EngagementFocusPresentationRateRegressionTest {
    private EngagementFocusPresentationRateRegressionTest() { }

    public static void main(String[] args) {
        liveFocusBuildsAreCappedNearTenHz();
        System.out.println("EngagementFocusPresentationRateRegressionTest passed");
    }

    private static void liveFocusBuildsAreCappedNearTenHz() {
        EngagementDeltaWindowSweepRuntime.resetForTest();
        FoundationTpsNoiseGate.reset();
        EngagementFocusModel.resetPresentationCacheForTest();
        AeProjectSnapshot snapshot = snapshot();

        // Prime the gate independently at full input rate, as the real method path does.
        for (int i = 0; i < FoundationTpsNoiseGate.minimumQuietSamples() + 2; i++) {
            FoundationTpsNoiseGate.evaluate(sample(i + 1,
                    i * 0.005, 1800.0, 6.0, 0.20, 1.0, 0.5));
        }

        for (int i = 0; i < 100; i++) {
            double seconds = 1.0 + i * 0.005; // 200 Hz for 0.5 s
            EngagementFocusModel.build(snapshot,
                    sample(1000 + i, seconds, 1800.0,
                            6.0 + (i % 4) * 0.02, 0.25, 1.0, 0.8),
                    GuidedCaptureState.CAPTURING,
                    0, 5, i + 1, i + 1);
        }

        long builds = EngagementFocusModel.presentationBuildCountForTest();
        require(builds >= 4,
                "Focus presentation throttle stopped updating entirely: " + builds);
        require(builds <= 7,
                "Focus presentation rebuilt too close to sample rate: " + builds
                        + " builds for 100 samples / 0.5 s");
    }

    private static LiveSample sample(long nano, double seconds,
                                     double rpm, double tps,
                                     double delta, double threshold,
                                     double tpsRate) {
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
        long realNano = Math.round(seconds * 1000000000.0) + nano;
        return new LiveSample(realNano, seconds, values, tpsRate, 0.0);
    }

    private static AeProjectSnapshot snapshot() {
        return new AeProjectSnapshot(
                "presentation-rate-regression",
                new double[]{2.0}, new double[]{20.0}, new double[][]{{1.0}},
                new double[]{1000.0}, new double[]{1.5},
                1.0, 0.0, new double[0], new double[0],
                false, false, "none", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                "Dual stride, newest", 25.0, 0.050, true, 0.10);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
