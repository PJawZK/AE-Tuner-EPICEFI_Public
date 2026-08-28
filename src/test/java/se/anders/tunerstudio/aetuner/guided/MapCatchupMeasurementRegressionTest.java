package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class MapCatchupMeasurementRegressionTest {
    private MapCatchupMeasurementRegressionTest() { }

    public static void main(String[] args) {
        inactiveFallbackGapNeverDefinesAnchor();
        higherPredictionTargetReplacesAnchorAndUsesExactTarget();
        catchupBuffersPhysicalMeasurementBeforePlateauValidation();
        laterHigherTargetInvalidatesEarlierBufferedCatch();
        timeoutRestartsAtLastUpwardTargetUpdate();
        effectiveMapReplayUsesCurrentRpmCurveAndFirmwareFormula();
        System.out.println("MapCatchupMeasurementRegressionTest passed");
    }

    private static void inactiveFallbackGapNeverDefinesAnchor() {
        MapCatchupMeasurement measurement = configured();
        LiveSample stale = sample(1.00, 2000.0, 40.0, 101.63, 40.0, false);
        LiveSample active = sample(1.10, 2000.0, 55.0, 78.08, 78.08, true);
        measurement.observePredictionGap(stale);
        measurement.observePredictionGap(active);
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(stale);
        samples.add(active);
        measurement.beginCatchup(samples, 1.20);
        require(measurement.measurementAnchor() == active,
                "inactive stale fallback gap became measurement anchor");
        close(measurement.bestGap(), 23.08, 0.001,
                "prediction-active target-anchor gap changed");
        close(measurement.threshold(), 78.08, 0.001,
                "final target must be exact fallbackMap, not a 90-percent threshold");
    }

    private static void higherPredictionTargetReplacesAnchorAndUsesExactTarget() {
        MapCatchupMeasurement measurement = configured();
        LiveSample first = sample(1.00, 2000.0, 50.0, 70.0, 70.0, true);
        LiveSample higher = sample(1.20, 2000.0, 56.0, 82.0, 82.0, true);
        measurement.observePredictionGap(first);
        measurement.observePredictionGap(higher);
        measurement.beginCatchup(new ArrayList<LiveSample>(), 1.20);
        require(measurement.measurementAnchor() == higher,
                "higher prediction-active fallback target did not replace the anchor");
        close(measurement.finalPredictionTarget(), 82.0, 0.001,
                "final upward-latched target changed");
        close(measurement.bestGap(), 26.0, 0.001,
                "gap was not recomputed at the final target update");

        LiveSample below = sample(1.45, 2000.0, 81.9, 82.0, 81.95, false);
        measurement.observeCatchup(below);
        require(measurement.physicalCatchSample() == null,
                "MAP below the exact final prediction target incorrectly completed catch-up");
        LiveSample caught = sample(1.50, 2000.0, 82.0, 82.0, 82.0, false);
        measurement.observeCatchup(caught);
        require(measurement.physicalCatchSample() == caught,
                "exact final prediction target did not complete physical catch-up");
        require(measurement.catchSample() == caught,
                "live catch-up did not expose a completion sample");
        close(measurement.catchupDurationSeconds(), 0.30, 0.001,
                "catch-up duration no longer starts at the last upward target update");
    }

    private static void catchupBuffersPhysicalMeasurementBeforePlateauValidation() {
        MapCatchupMeasurement measurement = configured();
        LiveSample anchor = sample(2.00, 2000.0, 50.0, 70.0, 70.0, true);
        LiveSample alreadyAbove = sample(2.10, 2000.0, 72.0, 70.0, 72.0, false);
        measurement.observePredictionGap(anchor);
        List<LiveSample> prePlateau = new ArrayList<LiveSample>();
        prePlateau.add(anchor);
        prePlateau.add(alreadyAbove);
        measurement.beginCatchup(prePlateau, 1.20);

        require(measurement.physicalCatchSample() == alreadyAbove,
                "pre-hold physical catch-up was not preserved in the attempt buffer");
        require(measurement.catchSample() == null,
                "buffered physical catch became a Guided completion before hold validation");
        close(measurement.catchupDurationSeconds(), 0.10, 0.001,
                "buffered physical duration changed");

        LiveSample afterPlateau = sample(2.30, 2000.0, 71.0, 70.0, 71.0, false);
        measurement.observeCatchup(afterPlateau);
        require(measurement.catchSample() == afterPlateau,
                "later hold-validation flow did not expose a completion/cue sample");
        close(measurement.catchupDurationSeconds(), 0.10, 0.001,
                "later hold validation corrupted the earlier physical catch-up duration");
    }

    private static void laterHigherTargetInvalidatesEarlierBufferedCatch() {
        MapCatchupMeasurement measurement = configured();
        LiveSample first = sample(2.50, 2000.0, 50.0, 70.0, 70.0, true);
        LiveSample firstCatch = sample(2.56, 2000.0, 71.0, 70.0, 71.0, false);
        measurement.observePredictionGap(first);
        List<LiveSample> buffered = new ArrayList<LiveSample>();
        buffered.add(first);
        buffered.add(firstCatch);
        measurement.beginCatchup(buffered, 1.20);
        require(measurement.physicalCatchSample() == firstCatch,
                "first physical catch was not buffered");

        LiveSample higher = sample(2.60, 2000.0, 60.0, 82.0, 82.0, true);
        measurement.observePredictionGap(higher);
        require(measurement.measurementAnchor() == higher,
                "later upward firmware target did not replace the anchor");
        require(measurement.physicalCatchSample() == null,
                "catch against an obsolete lower target survived a later upward target reset");
        require(measurement.catchSample() == null,
                "completion against an obsolete lower target survived a later upward target reset");
    }

    private static void timeoutRestartsAtLastUpwardTargetUpdate() {
        MapCatchupMeasurement measurement = configured();
        LiveSample first = sample(3.00, 2000.0, 50.0, 75.0, 75.0, true);
        measurement.observePredictionGap(first);
        measurement.beginCatchup(new ArrayList<LiveSample>(), 1.20);
        require(!measurement.timedOut(sample(4.10, 2000.0, 60.0, 75.0, 70.0, false), 1.20),
                "catch-up timed out too early from first target");

        LiveSample higher = sample(4.15, 2000.0, 60.0, 90.0, 90.0, true);
        measurement.observePredictionGap(higher);
        require(!measurement.timedOut(sample(5.20, 2000.0, 70.0, 90.0, 80.0, false), 1.20),
                "timeout did not restart at the later firmware-shaped target update");
        require(measurement.timedOut(sample(5.40, 2000.0, 70.0, 90.0, 80.0, false), 1.20),
                "timeout no longer starts at the final target anchor");
    }

    private static void effectiveMapReplayUsesCurrentRpmCurveAndFirmwareFormula() {
        MapCatchupMeasurement good = new MapCatchupMeasurement();
        good.configure(new BlendDurationCaptureConfig(2000.0, 20.0, 5, 2, false,
                new double[]{1500.0, 2500.0},
                new double[]{0.40, 0.60}));

        // At 2000 RPM, interpolated D = 0.50 s.
        // t=0: E = 80.
        good.observePredictionGap(sample(6.00, 2000.0, 50.0, 80.0, 80.0, true));
        // t=0.05: blend=0.10; E = 80 + (55-80)*0.10 = 77.5.
        good.observePredictionGap(sample(6.05, 2000.0, 55.0, 80.0, 77.5, true));
        // At 2250 RPM D = 0.55; t=0.10 -> blend=.181818; E ~= 76.3636.
        good.observePredictionGap(sample(6.10, 2250.0, 60.0, 80.0, 76.3636, true));
        require(good.effectiveMapModelConsistent(),
                "firmware-equation Effective MAP replay rejected matching source data");
        require(good.modelSampleCount() == 3,
                "firmware-equation replay did not retain all usable source samples");
        require(good.modelMeanAbsoluteError() < 0.05,
                "matching Effective MAP source data produced material replay error");

        MapCatchupMeasurement bad = new MapCatchupMeasurement();
        bad.configure(new BlendDurationCaptureConfig(2000.0, 20.0, 5, 2, false,
                new double[]{1500.0, 2500.0},
                new double[]{0.40, 0.60}));
        bad.observePredictionGap(sample(7.00, 2000.0, 50.0, 80.0, 90.0, true));
        bad.observePredictionGap(sample(7.05, 2000.0, 55.0, 80.0, 90.0, true));
        bad.observePredictionGap(sample(7.10, 2250.0, 60.0, 80.0, 90.0, true));
        require(!bad.effectiveMapModelConsistent(),
                "source Effective MAP that contradicts the firmware equation was not flagged");
    }

    private static MapCatchupMeasurement configured() {
        MapCatchupMeasurement measurement = new MapCatchupMeasurement();
        measurement.configure(new BlendDurationCaptureConfig(2000.0, 20.0, 5, 2, false,
                new double[]{1500.0, 2500.0},
                new double[]{0.50, 0.50}));
        return measurement;
    }

    private static LiveSample sample(double seconds, double rpm, double map,
                                     double fallback, double effective,
                                     boolean prediction) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.MAP, map);
        values.put(ChannelRole.FALLBACK_MAP, fallback);
        values.put(ChannelRole.EFFECTIVE_MAP, effective);
        values.put(ChannelRole.MAP_PRED_ACTIVE, prediction ? 1.0 : 0.0);
        values.put(ChannelRole.MAP_PRED_RESET_CNT, 10.0);
        values.put(ChannelRole.MAP_PRED_EVENT_OVER, 4.0);
        long nano = Math.round(seconds * 1000000000.0);
        return new LiveSample(nano, seconds, values, 0.0, 0.0);
    }

    private static void close(double actual, double expected,
                              double tolerance, String message) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(message + ": " + actual + " vs " + expected);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
