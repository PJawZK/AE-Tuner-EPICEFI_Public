package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.EnumMap;

/** Regression: one explicit quiet-TPS calibration basis must govern a comparison set. */
public final class FoundationTpsNoiseFreezeRegressionTest {
    private FoundationTpsNoiseFreezeRegressionTest() { }

    public static void main(String[] args) {
        calibrationFreezesAndOnlyExplicitRecalibrationRearmsLearning();
        System.out.println("FoundationTpsNoiseFreezeRegressionTest passed");
    }

    private static void calibrationFreezesAndOnlyExplicitRecalibrationRearmsLearning() {
        FoundationTpsNoiseGate.reset();
        require(FoundationTpsNoiseGate.calibrationState()
                        == FoundationTpsNoiseGate.CalibrationState.UNCALIBRATED,
                "reset did not return quiet calibration to UNCALIBRATED");

        FoundationTpsNoiseGate.beginCalibration();
        FoundationTpsNoiseGate.Evaluation ready = null;
        int count = calibrationSamples(0.025);
        for (int i = 0; i < count; i++) {
            ready = FoundationTpsNoiseGate.evaluate(
                    sample(i * 0.025, 12.0, 0.10, 1.0));
        }
        require(ready != null && ready.calibrated,
                "explicit quiet calibration did not become FROZEN after the time/sample window");
        require(FoundationTpsNoiseGate.calibrationFrozenForTest(),
                "quiet calibration was not frozen when READY was reached");
        require(ready.calibrationRevision == 1L,
                "first frozen calibration did not publish revision one");

        int frozenSamples = ready.quietSamples;
        double frozenP95 = ready.quietRateP95;
        double frozenP99 = ready.quietRateP99;
        double frozenFloor = ready.intentRateFloor;

        FoundationTpsNoiseGate.Evaluation after = ready;
        for (int i = 0; i < 120; i++) {
            after = FoundationTpsNoiseGate.evaluate(
                    sample(5.0 + i * 0.025, 12.0, 0.10, 20.0));
        }
        require(after.quietSamples == frozenSamples,
                "later candidate samples changed the frozen calibration population");
        requireClose(frozenP95, after.quietRateP95,
                "P95 drifted after calibration freeze");
        requireClose(frozenP99, after.quietRateP99,
                "P99 drifted after calibration freeze");
        requireClose(frozenFloor, after.intentRateFloor,
                "driver-intent floor drifted after calibration freeze");

        FoundationTpsNoiseGate.beginCalibration();
        require(!FoundationTpsNoiseGate.calibrationFrozenForTest(),
                "explicit recalibration did not re-arm quiet calibration");
        FoundationTpsNoiseGate.Evaluation fresh = FoundationTpsNoiseGate.evaluate(
                sample(20.0, 12.0, 0.10, 0.5));
        require(!fresh.calibrated && fresh.quietSamples == 1
                        && fresh.calibrationRevision == 1L,
                "explicit recalibration did not preserve revision history and restart from sample one");

        for (int i = 1; i < count; i++) {
            fresh = FoundationTpsNoiseGate.evaluate(
                    sample(20.0 + i * 0.025, 12.0, 0.10, 0.5));
        }
        require(fresh.calibrated && fresh.calibrationRevision == 2L,
                "second explicit calibration did not freeze as a new revision");
    }

    private static int calibrationSamples(double dt) {
        return Math.max(FoundationTpsNoiseGate.minimumQuietSamples(),
                (int) Math.ceil(FoundationTpsNoiseGate.minimumCalibrationSeconds() / dt) + 2);
    }

    private static LiveSample sample(double seconds, double tps,
                                     double delta, double tpsRate) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, 1800.0);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.DELTA_TPS, delta);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.0);
        return new LiveSample(Math.round(seconds * 1000000000.0), seconds,
                values, tpsRate, 0.0);
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
