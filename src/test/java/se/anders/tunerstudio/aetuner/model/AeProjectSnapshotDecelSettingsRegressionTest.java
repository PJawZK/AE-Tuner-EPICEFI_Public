package se.anders.tunerstudio.aetuner.model;

import se.anders.tunerstudio.aetuner.guided.method.DecelDetectionMethodModuleRegressionTest;
import se.anders.tunerstudio.aetuner.guided.method.DecelDetectionRecommendationRegressionTest;

public final class AeProjectSnapshotDecelSettingsRegressionTest {
    private AeProjectSnapshotDecelSettingsRegressionTest() { }

    public static void main(String[] args) {
        backwardCompatibleSnapshotsFailClosed();
        runtimeSnapshotPreservesExactDecelBaseline();
        DecelDetectionMethodModuleRegressionTest.main(new String[0]);
        DecelDetectionRecommendationRegressionTest.main(new String[0]);
        System.out.println("AeProjectSnapshotDecelSettingsRegressionTest passed");
    }

    private static void backwardCompatibleSnapshotsFailClosed() {
        AeProjectSnapshot snapshot = new AeProjectSnapshot(
                "Main Controller",
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                0.0, 0.0, new double[0], new double[0],
                false, false, "fixed", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0]);

        require(!snapshot.hasDecelThresholdCurve(),
                "legacy snapshot constructor invented a decel threshold curve");
        require(!snapshot.hasDecelHoldCycles(),
                "legacy snapshot constructor invented decel hold cycles");
        require(!snapshot.hasDecelDetectionSettings(),
                "legacy snapshot constructor invented a complete decel baseline");
        require(snapshot.getDecelThresholdRpmBins().length == 0,
                "legacy snapshot did not expose an empty decel RPM axis");
        require(snapshot.getDecelThresholdValues().length == 0,
                "legacy snapshot did not expose an empty decel threshold curve");
        require(Double.isNaN(snapshot.getDecelHoldCycles()),
                "missing decel hold cycles were collapsed to a real numeric value");
        require(Double.isNaN(snapshot.decelThresholdForRpm(2000.0)),
                "missing decel curve returned a fabricated threshold");
    }

    private static void runtimeSnapshotPreservesExactDecelBaseline() {
        double[] rpmBins = new double[]{800.0, 1600.0, 3200.0};
        double[] thresholds = new double[]{0.0, 1.25, 2.50};

        AeProjectSnapshot snapshot = new AeProjectSnapshot(
                "Main Controller",
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                0.0, 0.0, new double[0], new double[0],
                false, false, "fixed", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                "Dual stride, newest", 30.0, 0.05,
                false, true, 0.35,
                rpmBins, thresholds, 12.0);

        rpmBins[0] = 9999.0;
        thresholds[0] = 9.0;

        require(snapshot.hasDecelThresholdCurve(),
                "runtime snapshot lost the captured decel threshold curve");
        require(snapshot.hasDecelHoldCycles(),
                "runtime snapshot lost the captured decel hold value");
        require(snapshot.hasDecelDetectionSettings(),
                "runtime snapshot did not expose a complete decel baseline");
        requireClose(12.0, snapshot.getDecelHoldCycles(),
                "decel hold-cycle baseline changed");

        double[] capturedBins = snapshot.getDecelThresholdRpmBins();
        double[] capturedThresholds = snapshot.getDecelThresholdValues();
        requireClose(800.0, capturedBins[0],
                "snapshot did not defensively copy decel RPM bins");
        requireClose(0.0, capturedThresholds[0],
                "snapshot did not preserve the real zero/disabled threshold cell");

        capturedBins[1] = 9999.0;
        capturedThresholds[1] = 9.0;
        requireClose(1600.0, snapshot.getDecelThresholdRpmBins()[1],
                "decel RPM getter leaked mutable snapshot storage");
        requireClose(1.25, snapshot.getDecelThresholdValues()[1],
                "decel threshold getter leaked mutable snapshot storage");

        requireClose(0.0, snapshot.decelThresholdForRpm(800.0),
                "zero/disabled firmware threshold was not preserved at its bin");
        requireClose(0.625, snapshot.decelThresholdForRpm(1200.0),
                "decel threshold interpolation changed firmware curve semantics");
        requireClose(2.50, snapshot.decelThresholdForRpm(5000.0),
                "decel threshold high-RPM clamp changed firmware curve semantics");
    }

    private static void requireClose(double expected, double actual, String message) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 0.000001) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
