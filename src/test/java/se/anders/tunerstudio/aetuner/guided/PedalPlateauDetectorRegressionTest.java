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

public final class PedalPlateauDetectorRegressionTest {
    private PedalPlateauDetectorRegressionTest() { }

    public static void main(String[] args) {
        acceptsNaturalModeratePlateau();
        rejectsTinyAndOversizedSteps();
        rejectsMovingPedalWindow();
        System.out.println("PedalPlateauDetectorRegressionTest passed");
    }

    private static void acceptsNaturalModeratePlateau() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(sample(1.00, 29.0));
        samples.add(sample(1.05, 30.5));
        samples.add(sample(1.10, 29.8));
        samples.add(sample(1.15, 30.2));
        samples.add(sample(1.20, 30.0));
        PedalPlateauDetector.Result result =
                PedalPlateauDetector.evaluate(samples, 8.0,
                        samples.get(samples.size() - 1).getNanoTime());
        require(result.usable, "moderate natural plateau was not accepted");
        close(result.medianTps, 30.0, 0.001, "median TPS changed");
        close(result.step, 22.0, 0.001, "relative TPS step changed");
        require(result.anchor != null, "plateau anchor missing");
    }

    private static void rejectsTinyAndOversizedSteps() {
        PedalPlateauDetector.Result tiny =
                PedalPlateauDetector.evaluate(flat(12.0), 8.0,
                        nanos(1.20));
        require(!tiny.usable && tiny.step < PedalPlateauDetector.MIN_USABLE_STEP,
                "tiny step became usable");

        PedalPlateauDetector.Result large =
                PedalPlateauDetector.evaluate(flat(50.0), 8.0,
                        nanos(1.20));
        require(!large.usable && large.step > PedalPlateauDetector.MAX_USABLE_STEP,
                "oversized step became usable");
    }

    private static void rejectsMovingPedalWindow() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(sample(1.00, 25.0));
        samples.add(sample(1.05, 28.0));
        samples.add(sample(1.10, 31.0));
        samples.add(sample(1.15, 34.0));
        samples.add(sample(1.20, 37.0));
        PedalPlateauDetector.Result result =
                PedalPlateauDetector.evaluate(samples, 8.0, nanos(1.20));
        require(!result.usable, "moving pedal window was treated as a plateau");
        require(result.range > PedalPlateauDetector.RANGE_LIMIT,
                "moving-window range evidence changed");
    }

    private static List<LiveSample> flat(double tps) {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(sample(1.00, tps));
        samples.add(sample(1.05, tps + 0.2));
        samples.add(sample(1.10, tps - 0.1));
        samples.add(sample(1.15, tps + 0.1));
        samples.add(sample(1.20, tps));
        return samples;
    }

    private static LiveSample sample(double seconds, double tps) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.TPS, tps);
        long nano = nanos(seconds);
        return new LiveSample(nano, seconds, values, 0.0, 0.0);
    }

    private static long nanos(double seconds) {
        return Math.round(seconds * 1000000000.0);
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
