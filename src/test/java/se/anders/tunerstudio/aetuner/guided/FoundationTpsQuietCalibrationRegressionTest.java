package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.EnumMap;

/**
 * Portability regression for explicit pedal-untouched TPS quiet calibration.
 * No universal 6 %TPS/s quiet/intent assumption is permitted.
 */
public final class FoundationTpsQuietCalibrationRegressionTest {
    private FoundationTpsQuietCalibrationRegressionTest() { }

    public static void main(String[] args) {
        calibrationDoesNotLearnUntilExplicitlyStarted();
        dbwLikeBackgroundAboveOldSixRateCanFreeze();
        slowDeliberateOpeningBelowOldSixRateCanQualify();
        sustainedDirectionalPedalTrendForcesRetry();
        transientTwitchIsTrimmedFromFrozenEnvelope();
        System.out.println("FoundationTpsQuietCalibrationRegressionTest passed");
    }

    private static void calibrationDoesNotLearnUntilExplicitlyStarted() {
        FoundationTpsNoiseGate.reset();
        FoundationTpsNoiseGate.Evaluation idle = FoundationTpsNoiseGate.evaluate(
                sample(0.0, 12.0, 0.10, 1.0, 1.0));
        require(idle.calibrationState == FoundationTpsNoiseGate.CalibrationState.UNCALIBRATED
                        && idle.quietSamples == 0 && !idle.calibrated,
                "quiet baseline silently learned before the operator/capture started calibration");
    }

    private static void dbwLikeBackgroundAboveOldSixRateCanFreeze() {
        FoundationTpsNoiseGate.reset();
        FoundationTpsNoiseGate.beginCalibration();
        FoundationTpsNoiseGate.Evaluation evaluation = null;
        int count = calibrationSamples(0.025);
        for (int i = 0; i < count; i++) {
            double phase = i * 0.31;
            double tps = 12.0 + Math.sin(phase) * 0.45;
            double rate = Math.abs(Math.cos(phase)) * 12.0;
            evaluation = FoundationTpsNoiseGate.evaluate(
                    sample(i * 0.025, tps, 0.20, 1.0, rate));
        }
        require(evaluation != null && evaluation.calibrated,
                "DBW-like pedal-untouched background above the old 6 %TPS/s cap did not freeze");
        require(evaluation.quietRateP99 > 6.0,
                "high-rate background was still effectively clipped by the removed 6 %TPS/s assumption");
        require(evaluation.intentRateFloor > evaluation.quietRateP99,
                "intent floor was not derived above the measured vehicle-specific background envelope");
    }

    private static void slowDeliberateOpeningBelowOldSixRateCanQualify() {
        FoundationTpsNoiseGate.reset();
        FoundationTpsNoiseGate.beginCalibration();
        FoundationTpsNoiseGate.Evaluation evaluation = null;
        int count = calibrationSamples(0.025);
        for (int i = 0; i < count; i++) {
            double tps = 12.0 + Math.sin(i * 0.40) * 0.02;
            double rate = 0.15 + Math.abs(Math.cos(i * 0.40)) * 0.15;
            evaluation = FoundationTpsNoiseGate.evaluate(
                    sample(i * 0.025, tps, 0.10, 1.0, rate));
        }
        require(evaluation != null && evaluation.calibrated,
                "low-noise explicit calibration did not freeze");
        require(evaluation.intentRateFloor < 2.0,
                "derived low-noise intent floor retained an arbitrary high minimum");

        FoundationTpsNoiseGate.Evaluation opening = FoundationTpsNoiseGate.evaluate(
                sample(5.0, 13.0, 1.20, 1.0, 2.0));
        require(opening.ecuThresholdCrossing && opening.intentQualified,
                "deliberate 2 %TPS/s opening below the old 6 %TPS/s rule did not qualify");
    }

    private static void sustainedDirectionalPedalTrendForcesRetry() {
        FoundationTpsNoiseGate.reset();
        FoundationTpsNoiseGate.beginCalibration();
        FoundationTpsNoiseGate.Evaluation evaluation = null;
        int count = calibrationSamples(0.025);
        for (int i = 0; i < count; i++) {
            double tps = 10.0 + i * 0.03;
            evaluation = FoundationTpsNoiseGate.evaluate(
                    sample(i * 0.025, tps, 0.20, 1.0, 1.2));
        }
        require(evaluation != null
                        && evaluation.calibrationState == FoundationTpsNoiseGate.CalibrationState.CALIBRATING
                        && !evaluation.calibrated
                        && evaluation.calibrationRetries >= 1,
                "sustained one-way pedal movement was accepted as a quiet baseline instead of retrying");
    }

    private static void transientTwitchIsTrimmedFromFrozenEnvelope() {
        FoundationTpsNoiseGate.reset();
        FoundationTpsNoiseGate.beginCalibration();
        FoundationTpsNoiseGate.Evaluation evaluation = null;
        int count = calibrationSamples(0.025);
        for (int i = 0; i < count; i++) {
            double tps = 12.0 + Math.sin(i * 0.35) * 0.03;
            double rate = i == 35 ? 45.0
                    : 0.20 + Math.abs(Math.cos(i * 0.35)) * 0.20;
            evaluation = FoundationTpsNoiseGate.evaluate(
                    sample(i * 0.025, tps, 0.10, 1.0, rate));
        }
        require(evaluation != null && evaluation.calibrated,
                "brief transient twitch prevented an otherwise valid quiet baseline");
        require(evaluation.trimmedOutliers >= 1,
                "brief transient twitch was not removed by robust calibration statistics");
        require(evaluation.intentRateFloor < 5.0,
                "trimmed transient twitch still inflated the frozen intent floor");
    }

    private static int calibrationSamples(double dt) {
        return Math.max(FoundationTpsNoiseGate.minimumQuietSamples(),
                (int) Math.ceil(FoundationTpsNoiseGate.minimumCalibrationSeconds() / dt) + 2);
    }

    private static LiveSample sample(double seconds, double tps,
                                     double delta, double threshold,
                                     double tpsRate) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, 1800.0);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.DELTA_TPS, delta);
        values.put(ChannelRole.ACCEL_THRESHOLD, threshold);
        return new LiveSample(Math.round(seconds * 1000000000.0), seconds,
                values, tpsRate, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
