package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.EnumMap;

/** Regression: the frozen vehicle-specific TPS quiet floor retains its own semantics. */
public final class EngagementCalibratedIntentFloorRegressionTest {
    private EngagementCalibratedIntentFloorRegressionTest() { }

    public static void main(String[] args) {
        quietVehicleAcceptsSlowDeliberateCrossing();
        noisyVehicleRejectsBelowCalibratedFloor();
        System.out.println("EngagementCalibratedIntentFloorRegressionTest passed");
    }

    private static void quietVehicleAcceptsSlowDeliberateCrossing() {
        Fixture f = new Fixture();
        f.calibrate(0.50);
        double floor = FoundationTpsNoiseGate.evaluate(null).intentRateFloor;
        require(floor > 1.0 && floor < 3.0,
                "quiet calibration did not produce the expected low vehicle-specific floor: " + floor);

        double deliberateRate = floor + 0.50;
        require(deliberateRate < 5.0,
                "fixture no longer exercises movement below the old universal 5 %TPS/s floor");
        FoundationTpsNoiseGate.Evaluation evaluation =
                f.feed(1.20, 1.0, deliberateRate, 0.04);
        require(evaluation.ecuThresholdCrossing && evaluation.intentQualified
                        && !evaluation.incidentalCrossing,
                "slow deliberate crossing above the frozen quiet floor was not intent-qualified");
    }

    private static void noisyVehicleRejectsBelowCalibratedFloor() {
        Fixture f = new Fixture();
        f.calibrate(3.00);
        double floor = FoundationTpsNoiseGate.evaluate(null).intentRateFloor;
        require(floor > 8.0 && floor < 10.0,
                "noisy calibration did not produce the expected elevated vehicle-specific floor: " + floor);

        double belowFloor = floor - 1.0;
        require(belowFloor > 5.0,
                "fixture no longer exercises movement above the old fixed floor but below calibration");
        FoundationTpsNoiseGate.Evaluation below =
                f.feed(1.20, 1.0, belowFloor, 0.04);
        require(below.ecuThresholdCrossing && !below.intentQualified
                        && below.incidentalCrossing,
                "crossing below the frozen vehicle-specific floor was not classified as incidental");

        FoundationTpsNoiseGate.Evaluation above =
                f.feed(1.20, 1.0, floor + 0.50, 0.04);
        require(above.ecuThresholdCrossing && above.intentQualified
                        && !above.incidentalCrossing,
                "crossing above the frozen vehicle-specific floor was not intent-qualified");
    }

    private static final class Fixture {
        double seconds;
        long index;

        void calibrate(double quietAbsRate) {
            FoundationTpsNoiseGate.reset();
            FoundationTpsNoiseGate.beginCalibration();
            double dt = 0.025;
            int samples = Math.max(FoundationTpsNoiseGate.minimumQuietSamples() + 8,
                    (int) Math.ceil(FoundationTpsNoiseGate.minimumCalibrationSeconds() / dt) + 10);
            for (int i = 0; i < samples; i++) {
                double sign = (i & 1) == 0 ? 1.0 : -1.0;
                feed(0.20, 1.0, sign * quietAbsRate, dt);
            }
            require(FoundationTpsNoiseGate.calibrationFrozen(),
                    "fixture did not freeze the TPS quiet baseline");
        }

        FoundationTpsNoiseGate.Evaluation feed(double detectorDelta,
                                                double threshold,
                                                double tpsRate,
                                                double dt) {
            seconds += dt;
            index++;
            return FoundationTpsNoiseGate.evaluate(
                    sample(index, seconds, detectorDelta, threshold, tpsRate));
        }
    }

    private static LiveSample sample(long index, double seconds,
                                     double detectorDelta, double threshold,
                                     double tpsRate) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, 1800.0);
        values.put(ChannelRole.TPS, 10.0);
        values.put(ChannelRole.DELTA_TPS, detectorDelta);
        values.put(ChannelRole.ACCEL_THRESHOLD, threshold);
        values.put(ChannelRole.AE_DELTA_NEWEST_PAIR, detectorDelta);
        values.put(ChannelRole.AE_WINDOW_MS, 50.0);
        values.put(ChannelRole.AE_WINDOW_SAMPLES, 10.0);
        values.put(ChannelRole.AE_DELTA_STRIDE, 5.0);
        values.put(ChannelRole.TPS_DECEL_ACTIVE, 0.0);
        return new LiveSample(index, seconds, values, tpsRate, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
