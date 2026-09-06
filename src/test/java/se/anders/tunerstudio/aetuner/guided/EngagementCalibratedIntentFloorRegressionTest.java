package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.EnumMap;

/** Regression: controlled-sweep intent must follow the frozen vehicle-specific TPS floor. */
public final class EngagementCalibratedIntentFloorRegressionTest {
    private EngagementCalibratedIntentFloorRegressionTest() { }

    public static void main(String[] args) {
        quietVehicleAcceptsSlowDeliberateOpening();
        noisyVehicleRejectsBelowCalibratedFloor();
        System.out.println("EngagementCalibratedIntentFloorRegressionTest passed");
    }

    private static void quietVehicleAcceptsSlowDeliberateOpening() {
        Fixture f = new Fixture();
        f.startAndCalibrate(0.50);
        double floor = FoundationTpsNoiseGate.evaluate(null).intentRateFloor;
        require(floor > 1.0 && floor < 3.0,
                "quiet calibration did not produce the expected low vehicle-specific floor: " + floor);

        f.reacquireReady();
        double deliberateRate = floor + 0.50;
        require(deliberateRate < 5.0,
                "fixture no longer exercises movement below the old universal 5 %TPS/s floor");
        f.feed(10.10, 0.20, deliberateRate, 0.04);
        require(EngagementDeltaWindowSweepRuntime.snapshot().phase
                        == EngagementDeltaWindowSweepRuntime.Phase.MOVING,
                "slow deliberate opening above the frozen quiet floor did not start the maneuver");
    }

    private static void noisyVehicleRejectsBelowCalibratedFloor() {
        Fixture f = new Fixture();
        f.startAndCalibrate(3.00);
        double floor = FoundationTpsNoiseGate.evaluate(null).intentRateFloor;
        require(floor > 8.0 && floor < 10.0,
                "noisy calibration did not produce the expected elevated vehicle-specific floor: " + floor);

        f.reacquireReady();
        double belowFloor = floor - 1.0;
        require(belowFloor > 5.0,
                "fixture no longer exercises movement above the old fixed floor but below calibration");
        f.feed(10.10, 0.20, belowFloor, 0.04);
        require(EngagementDeltaWindowSweepRuntime.snapshot().phase
                        == EngagementDeltaWindowSweepRuntime.Phase.READY,
                "movement below the frozen vehicle-specific floor incorrectly started a maneuver");

        f.feed(10.20, 0.20, floor + 0.50, 0.04);
        require(EngagementDeltaWindowSweepRuntime.snapshot().phase
                        == EngagementDeltaWindowSweepRuntime.Phase.MOVING,
                "movement above the frozen vehicle-specific floor did not start the maneuver");
    }

    private static final class Fixture {
        final AeProjectSnapshot snapshot = snapshot();
        final FakeWriter writer = new FakeWriter();
        double seconds;
        long index;

        void startAndCalibrate(double quietAbsRate) {
            EngagementDeltaWindowSweepRuntime.resetForTest();
            FoundationTpsNoiseGate.reset();
            EngagementDeltaWindowSweepRuntime.setWriterForTest(writer);
            EngagementDeltaWindowSweepRuntime.updatePendingConfig(
                    new EngagementDeltaWindowSweepRuntime.Config(
                            1800.0, 200.0, 10.0, -2.0, 2.0,
                            2, 5.0, false));
            EngagementDeltaWindowSweepRuntime.observeWorkingTune(snapshot);
            FoundationTpsNoiseGate.beginCalibration();

            double dt = 0.025;
            int samples = Math.max(FoundationTpsNoiseGate.minimumQuietSamples() + 8,
                    (int) Math.ceil(FoundationTpsNoiseGate.minimumCalibrationSeconds() / dt) + 10);
            for (int i = 0; i < samples; i++) {
                double sign = (i & 1) == 0 ? 1.0 : -1.0;
                feed(10.0 + sign * 0.03, 0.20, sign * quietAbsRate, dt);
            }
            require(FoundationTpsNoiseGate.calibrationFrozen(),
                    "fixture did not freeze the TPS quiet baseline");
        }

        void reacquireReady() {
            for (int i = 0; i < 10; i++) {
                feed(10.0, 0.20, 0.20, 0.05);
            }
            require(EngagementDeltaWindowSweepRuntime.snapshot().phase
                            == EngagementDeltaWindowSweepRuntime.Phase.READY,
                    "fixture did not reacquire READY");
        }

        void feed(double tps, double detectorDelta, double tpsRate, double dt) {
            seconds += dt;
            index++;
            EngagementDeltaWindowSweepRuntime.accept(snapshot,
                    sample(index, seconds, 1800.0, tps,
                            detectorDelta, 1.0, tpsRate));
        }
    }

    private static final class FakeWriter
            implements EngagementDeltaWindowSweepRuntime.TemporaryWriter {
        @Override public EngagementDeltaWindowSweepRuntime.WriteResult apply(double candidateMs) {
            return EngagementDeltaWindowSweepRuntime.WriteResult.ok("candidate applied");
        }

        @Override public EngagementDeltaWindowSweepRuntime.WriteResult restore() {
            return EngagementDeltaWindowSweepRuntime.WriteResult.ok("baseline restored");
        }

        @Override public boolean temporaryActive() { return false; }
    }

    private static LiveSample sample(long index, double seconds,
                                     double rpm, double tps,
                                     double detectorDelta, double threshold,
                                     double tpsRate) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.DELTA_TPS, detectorDelta);
        values.put(ChannelRole.ACCEL_THRESHOLD, threshold);
        values.put(ChannelRole.AE_DELTA_NEWEST_PAIR, detectorDelta);
        values.put(ChannelRole.AE_WINDOW_MS, 50.0);
        values.put(ChannelRole.AE_WINDOW_SAMPLES, 10.0);
        values.put(ChannelRole.AE_DELTA_STRIDE, 5.0);
        values.put(ChannelRole.TPS_DECEL_ACTIVE, 0.0);
        return new LiveSample(index, seconds, values, tpsRate, 0.0);
    }

    private static AeProjectSnapshot snapshot() {
        return new AeProjectSnapshot(
                "calibrated-intent-floor",
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
