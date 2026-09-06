package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.EnumMap;

/** Regression from the 2026-08-31 real test: peak in range then immediate release is valid. */
public final class EngagementPeakManeuverRegressionTest {
    private EngagementPeakManeuverRegressionTest() { }

    public static void main(String[] args) {
        peakInsideBandThenImmediateReleaseCountsAsOneManeuver();
        System.out.println("EngagementPeakManeuverRegressionTest passed");
    }

    private static void peakInsideBandThenImmediateReleaseCountsAsOneManeuver() {
        EngagementDeltaWindowSweepRuntime.resetForTest();
        FoundationTpsNoiseGate.reset();
        FakeWriter writer = new FakeWriter(25.0);
        EngagementDeltaWindowSweepRuntime.setWriterForTest(writer);
        EngagementDeltaWindowSweepRuntime.updatePendingConfig(
                new EngagementDeltaWindowSweepRuntime.Config(
                        1800.0, 200.0, 10.0, -2.0, 2.0,
                        2, 5.0, false));
        AeProjectSnapshot snapshot = snapshot();
        EngagementDeltaWindowSweepRuntime.observeWorkingTune(snapshot);

        Feed feed = new Feed(snapshot);
        FoundationTpsNoiseGate.beginCalibration();
        double quietDt = 0.025;
        int quietSamples = Math.max(FoundationTpsNoiseGate.minimumQuietSamples() + 8,
                (int) Math.ceil(FoundationTpsNoiseGate.minimumCalibrationSeconds() / quietDt) + 10);
        for (int i = 0; i < quietSamples; i++) {
            feed.sample(1780.0, 6.20, 0.20, 0.0, quietDt);
        }
        require(FoundationTpsNoiseGate.calibrationFrozen(),
                "realistic stable start did not freeze the explicit quiet baseline");
        for (int i = 0; i < 8; i++) {
            feed.sample(1780.0, 6.20, 0.20, 0.0, 0.05);
        }
        require(EngagementDeltaWindowSweepRuntime.snapshot().phase
                        == EngagementDeltaWindowSweepRuntime.Phase.READY,
                "sweep did not reach READY from a calibrated realistic stable start");

        feed.sample(1790.0, 7.60, 1.20, 35.0, 0.04);
        feed.sample(1810.0, 9.80, 1.30, 55.0, 0.04);
        feed.sample(1840.0, 11.50, 1.40, 42.0, 0.04);
        feed.sample(1870.0, 13.50, 1.45, 50.0, 0.04);
        feed.sample(1900.0, 16.10, 1.50, 65.0, 0.04);
        feed.sample(1910.0, 16.85, 1.20, 18.0, 0.04);
        feed.sample(1870.0, 14.80, 0.70, -51.0, 0.04);

        EngagementDeltaWindowSweepRuntime.Snapshot peak = EngagementDeltaWindowSweepRuntime.snapshot();
        require(peak.phase == EngagementDeltaWindowSweepRuntime.Phase.POST_STOP,
                "immediate release after an in-range peak did not close the commanded movement");
        require(peak.targetReached, "in-range peak was not marked target-reached");
        requireClose(16.85, peak.peakTps, "captured peak did not preserve the physical max TPS");

        feed.sample(1820.0, 10.70, 0.60, -100.0, 0.05);
        feed.sample(1760.0, 3.65, 1.50, -140.0, 0.05);
        feed.sample(1740.0, 2.50, 2.00, -23.0, 0.05);
        feed.sample(1730.0, 2.20, 0.40, -6.0, 0.05);
        feed.sample(1725.0, 2.15, 0.20, -1.0, 0.05);
        feed.sample(1725.0, 2.15, 0.20, 0.0, 0.05);
        // One extra row takes the synthetic timeline unambiguously beyond the
        // 0.30 s post-peak observation boundary despite binary FP accumulation.
        feed.sample(1725.0, 2.15, 0.20, 0.0, 0.05);

        EngagementDeltaWindowSweepRuntime.Snapshot after = EngagementDeltaWindowSweepRuntime.snapshot();
        require(after.eventsThisCandidate == 1,
                "peak + immediate release was not counted as exactly one physical maneuver");
        require(after.phase == EngagementDeltaWindowSweepRuntime.Phase.SEEK_START,
                "accepted peak maneuver did not return to start-condition acquisition");
        requireClose(25.0, writer.current,
                "baseline candidate changed unexpectedly during first event");
    }

    private static final class Feed {
        final AeProjectSnapshot snapshot;
        double seconds;
        long index;
        Feed(AeProjectSnapshot snapshot) { this.snapshot = snapshot; }
        void sample(double rpm, double tps, double delta, double tpsRate, double dt) {
            seconds += dt;
            index++;
            EngagementDeltaWindowSweepRuntime.accept(snapshot,
                    EngagementPeakManeuverRegressionTest.sample(index, seconds, rpm, tps, delta, 1.0, tpsRate));
        }
    }

    private static final class FakeWriter implements EngagementDeltaWindowSweepRuntime.TemporaryWriter {
        final double baseline;
        double current;
        boolean temporary;
        FakeWriter(double baseline) { this.baseline = baseline; this.current = baseline; }
        @Override public EngagementDeltaWindowSweepRuntime.WriteResult apply(double candidateMs) {
            current = candidateMs;
            temporary = Math.abs(candidateMs - baseline) > 0.000001;
            return EngagementDeltaWindowSweepRuntime.WriteResult.ok("test candidate applied");
        }
        @Override public EngagementDeltaWindowSweepRuntime.WriteResult restore() {
            current = baseline;
            temporary = false;
            return EngagementDeltaWindowSweepRuntime.WriteResult.ok("test baseline restored");
        }
        @Override public boolean temporaryActive() { return temporary; }
    }

    private static LiveSample sample(long index, double seconds, double rpm, double tps,
                                     double delta, double threshold, double tpsRate) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.DELTA_TPS, delta);
        values.put(ChannelRole.ACCEL_THRESHOLD, threshold);
        values.put(ChannelRole.AE_DELTA_NEWEST_PAIR, delta);
        values.put(ChannelRole.AE_WINDOW_MS, 50.0);
        values.put(ChannelRole.AE_WINDOW_SAMPLES, 10.0);
        values.put(ChannelRole.AE_DELTA_STRIDE, 5.0);
        values.put(ChannelRole.TPS_DECEL_ACTIVE, 0.0);
        return new LiveSample(index, seconds, values, tpsRate, 0.0);
    }

    private static AeProjectSnapshot snapshot() {
        return new AeProjectSnapshot(
                "peak-release-regression",
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
