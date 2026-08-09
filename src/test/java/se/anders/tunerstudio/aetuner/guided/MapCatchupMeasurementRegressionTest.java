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
        retrospectiveAndLiveCatchupUseSameThreshold();
        timeoutStartsAtMeasurementAnchor();
        System.out.println("MapCatchupMeasurementRegressionTest passed");
    }

    private static void inactiveFallbackGapNeverDefinesAnchor() {
        MapCatchupMeasurement measurement = new MapCatchupMeasurement();
        LiveSample stale = sample(1.00, 40.0, 101.63, false);
        LiveSample active = sample(1.10, 55.0, 78.08, true);
        measurement.observePredictionGap(stale);
        measurement.observePredictionGap(active);
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(stale);
        samples.add(active);
        measurement.beginCatchup(samples, 1.20);
        require(measurement.measurementAnchor() == active,
                "inactive stale fallback gap became measurement anchor");
        close(measurement.bestGap(), 23.08, 0.001,
                "prediction-active peak gap changed");
    }

    private static void retrospectiveAndLiveCatchupUseSameThreshold() {
        MapCatchupMeasurement retrospective = new MapCatchupMeasurement();
        LiveSample anchor = sample(1.00, 50.0, 70.0, true);
        LiveSample below = sample(1.20, 66.0, 70.0, false);
        LiveSample catchup = sample(1.40, 68.0, 70.0, false);
        retrospective.observePredictionGap(anchor);
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(anchor);
        samples.add(below);
        samples.add(catchup);
        retrospective.beginCatchup(samples, 1.20);
        require(retrospective.catchSample() == catchup,
                "retrospective catch-up sample changed");
        close(retrospective.threshold(), 68.0, 0.001,
                "90% catch-up threshold changed");

        MapCatchupMeasurement live = new MapCatchupMeasurement();
        live.observePredictionGap(anchor);
        List<LiveSample> start = new ArrayList<LiveSample>();
        start.add(anchor);
        live.beginCatchup(start, 1.20);
        live.observeCatchup(below);
        require(live.catchSample() == null,
                "below-threshold MAP incorrectly completed catch-up");
        live.observeCatchup(catchup);
        require(live.catchSample() == catchup,
                "live catch-up did not use the frozen threshold");
        close(live.catchupDurationSeconds(), 0.40, 0.001,
                "catch-up duration changed");
    }

    private static void timeoutStartsAtMeasurementAnchor() {
        MapCatchupMeasurement measurement = new MapCatchupMeasurement();
        LiveSample anchor = sample(2.00, 50.0, 80.0, true);
        measurement.observePredictionGap(anchor);
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(anchor);
        measurement.beginCatchup(samples, 1.20);
        require(!measurement.timedOut(sample(3.10, 60.0, 80.0, false), 1.20),
                "catch-up timed out too early");
        require(measurement.timedOut(sample(3.25, 60.0, 80.0, false), 1.20),
                "catch-up timeout no longer starts at measurement anchor");
    }

    private static LiveSample sample(double seconds, double map,
                                     double fallback, boolean prediction) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.MAP, map);
        values.put(ChannelRole.FALLBACK_MAP, fallback);
        values.put(ChannelRole.MAP_PRED_ACTIVE, prediction ? 1.0 : 0.0);
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
