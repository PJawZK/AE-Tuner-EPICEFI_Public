package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class DecelSignalSamplesRegressionTest {
    private DecelSignalSamplesRegressionTest() { }

    public static void main(String[] args) {
        modeTwinWinsOverContradictoryDirectOutput();
        directOutputFallsBackWhenTwinMissing();
        positiveOnlyEvidenceIsNotSilentlyAccepted();
        directAndModeTwinAgreementIsAudited();
        realReportFailureShapeStillDetectsClosingEdge();
        engineStateQualificationIsExplicitAndBackwardCompatible();
        DecelHistoricalChannelIdentityRegressionTest.main(new String[0]);
        DecelHistoricalRunningContextRegressionTest.main(new String[0]);
        DecelDetectionMethodModuleRegressionTest.main(new String[0]);
        System.out.println("DecelSignalSamplesRegressionTest passed");
    }

    private static void modeTwinWinsOverContradictoryDirectOutput() {
        LiveSample sample = sample(-0.08, 0.00, -1.25, Double.NaN);
        double value = DecelSignalSamples.value(sample, snapshot("Dual stride, newest"));
        require(Math.abs(value + 1.25) < 0.000001,
                "configured-mode newest-fall twin did not override contradictory direct decelTps");
        List<LiveSample> adapted = DecelSignalSamples.asPrimaryDelta(
                singleton(sample), snapshot("Dual stride, newest"));
        require(Math.abs(adapted.get(0).get(ChannelRole.DELTA_TPS) + 1.25) < 0.000001,
                "Decel adapter did not feed mode-matched falling twin into analyzer boundary");
    }

    private static void directOutputFallsBackWhenTwinMissing() {
        LiveSample sample = sample(-0.05, -0.92, Double.NaN, Double.NaN);
        double value = DecelSignalSamples.value(sample, snapshot("Dual stride, newest"));
        require(Math.abs(value + 0.92) < 0.000001,
                "direct decelTps did not remain a fallback when mode twin was absent");
    }

    private static void positiveOnlyEvidenceIsNotSilentlyAccepted() {
        LiveSample sample = sample(-2.50, Double.NaN, Double.NaN, Double.NaN);
        require(!Double.isFinite(DecelSignalSamples.value(sample,
                        snapshot("Dual stride, newest"))),
                "positive Fuel: TPS AE change was silently accepted as Decel evidence");
        require(DecelSignalSamples.usableCount(singleton(sample),
                        snapshot("Dual stride, newest")) == 0,
                "positive-only evidence unexpectedly became Decel-usable");
    }

    private static void directAndModeTwinAgreementIsAudited() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(sample(-0.02, -0.80, -0.80, Double.NaN));
        samples.add(sample(-0.03, -1.20, -1.20, Double.NaN));
        DecelSignalSamples.Agreement good = DecelSignalSamples.agreement(
                samples, snapshot("Dual stride, newest"), 0.0005);
        require(good.verified() && good.comparedSamples == 2
                        && good.mismatchedSamples == 0,
                "matching decelTps/newest-fall channels were not verified");

        samples.add(sample(-0.04, 0.00, -0.90, Double.NaN));
        DecelSignalSamples.Agreement bad = DecelSignalSamples.agreement(
                samples, snapshot("Dual stride, newest"), 0.0005);
        require(!bad.verified() && bad.directUntrusted() && bad.mismatchedSamples == 1,
                "direct/twin detector mismatch was not exposed as an untrusted direct output");
    }

    private static void realReportFailureShapeStillDetectsClosingEdge() {
        LiveSample sample = sample(0.0, 0.0, -1.40, Double.NaN);
        double resolved = DecelSignalSamples.value(sample, "\"Dual stride, newest\"");
        require(resolved < -0.831,
                "real-report failure shape lost a valid Decel edge because direct output was zero");
        require(DecelSignalSamples.sourceLabel(singleton(sample), "Dual stride, newest")
                        .contains("mode-matched firmware twin"),
                "signal report did not identify the twin as analysis authority");
    }

    private static void engineStateQualificationIsExplicitAndBackwardCompatible() {
        LiveSample running = sampleWithState(-0.80, true, false);
        LiveSample cranking = sampleWithState(-1.20, false, true);
        LiveSample stopped = sampleWithState(-1.40, false, false);
        LiveSample legacyWithoutState = sample(-0.01, -0.70, -0.70, Double.NaN);

        require(DecelSignalSamples.physicalEvidenceAllowed(running),
                "running Decel evidence was incorrectly rejected");
        require(!DecelSignalSamples.physicalEvidenceAllowed(cranking),
                "cranking Decel evidence remained eligible for physical classification");
        require(!DecelSignalSamples.physicalEvidenceAllowed(stopped),
                "explicitly non-running Decel evidence remained eligible for physical classification");
        require(DecelSignalSamples.physicalEvidenceAllowed(legacyWithoutState),
                "legacy evidence without engine-state channels lost backward compatibility");

        List<LiveSample> adapted = DecelSignalSamples.asPrimaryDelta(
                singleton(cranking), "Dual stride, newest");
        require(Math.abs(adapted.get(0).get(ChannelRole.DELTA_TPS) + 1.20) < 0.000001,
                "mode-twin adaptation unexpectedly erased signal data before event qualification");
    }

    private static LiveSample sampleWithState(double newestFall, boolean running,
                                              boolean cranking) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, cranking ? 195.0 : 2200.0);
        values.put(ChannelRole.TPS, 20.0);
        values.put(ChannelRole.DECEL_DELTA_TPS, newestFall);
        values.put(ChannelRole.AE_DELTA_NEWEST_FALL, newestFall);
        values.put(ChannelRole.ENGINE_RUNNING, running ? 1.0 : 0.0);
        values.put(ChannelRole.ENGINE_CRANKING, cranking ? 1.0 : 0.0);
        return new LiveSample(0L, 0.0, values, -10.0, 0.0);
    }

    private static LiveSample sample(double accel, double decel,
                                     double newestFall, double timedFall) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, 2200.0);
        values.put(ChannelRole.TPS, 20.0);
        if (Double.isFinite(accel)) values.put(ChannelRole.DELTA_TPS, accel);
        if (Double.isFinite(decel)) values.put(ChannelRole.DECEL_DELTA_TPS, decel);
        if (Double.isFinite(newestFall)) values.put(ChannelRole.AE_DELTA_NEWEST_FALL, newestFall);
        if (Double.isFinite(timedFall)) values.put(ChannelRole.AE_DELTA_TIMED_FALL, timedFall);
        return new LiveSample(0L, 0.0, values, -10.0, 0.0);
    }

    private static List<LiveSample> singleton(LiveSample sample) {
        List<LiveSample> out = new ArrayList<LiveSample>();
        out.add(sample);
        return out;
    }

    private static AeProjectSnapshot snapshot(String model) {
        double[] rpm = new double[]{1000, 2000, 3000, 4000};
        double[] threshold = new double[]{0.5, 0.6, 0.7, 0.8};
        return new AeProjectSnapshot(
                "Main Controller",
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                0.0, 0.0, new double[0], new double[0],
                false, false, "fixed", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                model, 40.0, 0.06, true, true, 1.0,
                rpm, threshold, 4.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
