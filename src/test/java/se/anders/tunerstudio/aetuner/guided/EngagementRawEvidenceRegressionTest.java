package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.EngagementDetectionMethodModule;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.EnumMap;

/** Regression: coaching may transform presentation, never retained detector evidence. */
public final class EngagementRawEvidenceRegressionTest {
    private EngagementRawEvidenceRegressionTest() { }

    public static void main(String[] args) {
        incidentalCoachingSuppressionDoesNotRewriteRawReviewMetrics();
        System.out.println("EngagementRawEvidenceRegressionTest passed");
    }

    private static void incidentalCoachingSuppressionDoesNotRewriteRawReviewMetrics() {
        FoundationTpsNoiseGate.reset();
        EngagementDeltaWindowSweepRuntime.resetForTest();
        AeProjectSnapshot snapshot = snapshot();
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        session.start(new EngagementDetectionMethodModule(), snapshot, 5, 20, 115.0);

        for (int i = 0; i < FoundationTpsNoiseGate.minimumQuietSamples(); i++) {
            session.accept(sample(i * 0.025, 0.20, 0.20, 1.0));
        }

        LiveSample incidental = sample(0.700, 1.20, 1.35, 2.0);
        session.accept(incidental);

        double coached = EngagementFocusModel.selectedDetectorOutput(snapshot, incidental);
        double raw = EngagementDetectorEvidence.rawSelectedOutput(snapshot, incidental);
        requireClose(1.0, coached,
                "incidental crossing was not suppressed to threshold on the coaching path");
        requireClose(1.35, raw,
                "raw evidence selector did not preserve the actual Dual Stride/Newest value");
        require(session.activityEventCount() == 0,
                "incidental low-rate crossing incorrectly advanced physical maneuver progress");

        String liveRecovery = session.reportText("test");
        require(liveRecovery.contains("Full retained-window metrics and reviewed proposal output are generated after Finish/Review")
                        && !liveRecovery.contains("Raw selected-detector above-threshold samples:"),
                "active recovery report still performed full retained-history review work");

        require(session.finish(), "Engagement session did not finish for raw evidence review");
        String reviewed = session.resultText();
        require(reviewed.contains("Raw selected-detector above-threshold samples: 1"),
                "raw above-threshold crossing disappeared from reviewed evidence after coaching suppression");
        require(reviewed.contains("Maximum |Fuel: TPS AE change - raw selected detector output|: 0.150"),
                "review metrics did not compare production delta against the raw selected detector channel");
        require(reviewed.contains("Raw review metrics never use the coached/latching detector presentation signal"),
                "review does not state the raw/coached evidence boundary");
    }

    private static LiveSample sample(double seconds, double productionDelta,
                                     double newestPair, double tpsRate) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, 1800.0);
        values.put(ChannelRole.TPS, 12.0);
        values.put(ChannelRole.DELTA_TPS, productionDelta);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.0);
        values.put(ChannelRole.AE_DELTA_NEWEST_PAIR, newestPair);
        values.put(ChannelRole.AE_WINDOW_MS, 50.0);
        values.put(ChannelRole.AE_WINDOW_SAMPLES, 10.0);
        values.put(ChannelRole.AE_DELTA_STRIDE, 5.0);
        return new LiveSample(Math.round(seconds * 1000000000.0), seconds,
                values, tpsRate, 0.0);
    }

    private static AeProjectSnapshot snapshot() {
        return new AeProjectSnapshot(
                "raw-evidence",
                new double[]{2.0}, new double[]{20.0}, new double[][]{{1.0}},
                new double[]{1000.0}, new double[]{1.5},
                1.0, 0.0, new double[0], new double[0],
                false, false, "none", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                "Dual stride, newest", 25.0, 0.050, true, 0.10);
    }

    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.000001) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
