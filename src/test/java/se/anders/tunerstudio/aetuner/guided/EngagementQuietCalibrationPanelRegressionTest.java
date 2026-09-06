package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.EnumMap;

/** Presentation regression for the explicit TPS quiet-baseline coach. */
public final class EngagementQuietCalibrationPanelRegressionTest {
    private EngagementQuietCalibrationPanelRegressionTest() { }

    public static void main(String[] args) {
        driverCoachMakesCalibrationDominant();
        detailsExposeFrozenStatisticsAndSafeRecalibration();
        System.out.println("EngagementQuietCalibrationPanelRegressionTest passed");
    }

    private static void driverCoachMakesCalibrationDominant() {
        EngagementDeltaWindowSweepRuntime.resetForTest();
        FoundationTpsNoiseGate.reset();
        FoundationTpsNoiseGate.beginCalibration();
        for (int i = 0; i < 20; i++) {
            FoundationTpsNoiseGate.evaluate(sample(i * 0.025, 10.0, 0.4));
        }

        EngagementQuietCalibrationPanel panel =
                new EngagementQuietCalibrationPanel(true);
        panel.refreshFromGate();
        require(panel.stateTextForTest().contains("CALIBRATING"),
                "Driver calibration coach did not expose CALIBRATING state");
        require(panel.instructionTextForTest().contains("ENGINE IDLING")
                        && panel.instructionTextForTest().contains("PEDAL UNTOUCHED"),
                "Driver calibration coach did not make the pedal-untouched instruction dominant");
    }

    private static void detailsExposeFrozenStatisticsAndSafeRecalibration() {
        EngagementDeltaWindowSweepRuntime.resetForTest();
        FoundationTpsNoiseGate.reset();
        FoundationTpsNoiseGate.beginCalibration();
        double dt = 0.025;
        int samples = Math.max(FoundationTpsNoiseGate.minimumQuietSamples(),
                (int) Math.ceil(FoundationTpsNoiseGate.minimumCalibrationSeconds() / dt) + 2);
        for (int i = 0; i < samples; i++) {
            double rate = (i % 4 == 0) ? 0.8 : 0.4;
            FoundationTpsNoiseGate.evaluate(sample(i * dt, 10.0, rate));
        }
        require(FoundationTpsNoiseGate.calibrationFrozen(),
                "fixture did not freeze explicit quiet calibration");

        EngagementQuietCalibrationPanel panel =
                new EngagementQuietCalibrationPanel(false);
        panel.refreshFromGate();
        require(panel.stateTextForTest().contains("FROZEN")
                        && panel.stateTextForTest().contains("revision 1"),
                "Details did not expose frozen calibration state/revision");
        require(panel.timingTextForTest().contains("samples"),
                "Details did not expose calibration duration/sample count");
        require(panel.ratesTextForTest().contains("P95")
                        && panel.ratesTextForTest().contains("P99")
                        && panel.ratesTextForTest().contains("derived intent floor"),
                "Details did not expose P95/P99/derived intent floor");
        require(panel.qualityTextForTest().contains("Revision 1")
                        && panel.qualityTextForTest().contains("retries")
                        && panel.qualityTextForTest().contains("trimmed outliers"),
                "Details did not expose revision/retry/outlier diagnostics");
        require(panel.recalibrateEnabledForTest()
                        && panel.recalibrateTextForTest().contains("Recalibrate / Update"),
                "Details did not expose the safe recalibration control after freeze");

        panel.clickRecalibrateForTest();
        require(FoundationTpsNoiseGate.calibrationState()
                        == FoundationTpsNoiseGate.CalibrationState.CALIBRATING,
                "Details recalibration control did not start a new explicit baseline");
        panel.refreshFromGate();
        require(!panel.recalibrateEnabledForTest()
                        && panel.stateTextForTest().contains("CALIBRATING"),
                "Details recalibration control did not disable while calibration is in progress");
    }

    private static LiveSample sample(double seconds, double tps, double tpsRate) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, 1800.0);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.DELTA_TPS, 0.10);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.0);
        values.put(ChannelRole.AE_DELTA_NEWEST_PAIR, 0.10);
        values.put(ChannelRole.AE_WINDOW_MS, 50.0);
        values.put(ChannelRole.AE_WINDOW_SAMPLES, 10.0);
        values.put(ChannelRole.AE_DELTA_STRIDE, 5.0);
        return new LiveSample(Math.round(seconds * 1000000000.0), seconds,
                values, tpsRate, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
