package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Permanent contract for prediction-independent physical Blend timing evidence. */
public final class PhysicalMapResponseMeasurementRegressionTest {
    private PhysicalMapResponseMeasurementRegressionTest() { }

    public static void main(String[] args) {
        predictionTargetCannotGatePhysicalResponse();
        absoluteMapShiftKeepsNormalizedDuration();
        boundedLateWindowRemainsUsableButLowerConfidence();
        tooSmallPhysicalMapStepIsRejected();
        System.out.println("PhysicalMapResponseMeasurementRegressionTest passed");
    }

    private static void predictionTargetCannotGatePhysicalResponse() {
        List<LiveSample> samples = response(0.0);
        PhysicalMapResponseMeasurement m = new PhysicalMapResponseMeasurement();
        m.begin(50.0, nano(0.20));
        for (LiveSample sample : samples) {
            if (sample.getNanoTime() >= nano(0.20)) {
                m.observe(samples, sample, 1.20);
            }
        }
        require(m.isComplete(),
                "physical response was rejected because an unrelated prediction target was never reached");
        require(!m.isFailed(), "valid physical response entered failed state");
        require(!m.usedBoundedLateWindow(), "clean late MAP window was not recognized as settled");
        close(m.baselineMap(), 50.0, 0.001, "physical baseline changed");
        close(m.lateMap(), 78.6, 0.2, "late physical MAP level changed");
        require(m.mapStep() > 28.0 && m.mapStep() < 29.0,
                "physical MAP step was not derived from the event itself");
        close(m.durationSeconds(), 0.30, 0.051,
                "20->80 physical response duration changed");
    }

    private static void absoluteMapShiftKeepsNormalizedDuration() {
        List<LiveSample> warm = response(0.0);
        List<LiveSample> weatherShifted = response(-6.0);
        PhysicalMapResponseMeasurement a = new PhysicalMapResponseMeasurement();
        PhysicalMapResponseMeasurement b = new PhysicalMapResponseMeasurement();
        a.begin(50.0, nano(0.20));
        b.begin(44.0, nano(0.20));
        for (int i = 0; i < warm.size(); i++) {
            LiveSample sa = warm.get(i);
            LiveSample sb = weatherShifted.get(i);
            if (sa.getNanoTime() >= nano(0.20)) {
                a.observe(warm, sa, 1.20);
                b.observe(weatherShifted, sb, 1.20);
            }
        }
        require(a.isComplete() && b.isComplete(),
                "weather-shifted physical traces did not both complete");
        close(a.durationSeconds(), b.durationSeconds(), 0.001,
                "absolute MAP offset changed normalized response timing");
        close(a.mapStep(), b.mapStep(), 0.001,
                "absolute MAP offset changed event-relative physical step");
    }

    private static void boundedLateWindowRemainsUsableButLowerConfidence() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        double[] maps = new double[]{50, 56, 63, 69, 74, 77, 79, 81, 83, 85, 87, 89, 91};
        for (int i = 0; i < maps.length; i++) {
            samples.add(sample(i * 0.05, maps[i]));
        }
        PhysicalMapResponseMeasurement m = new PhysicalMapResponseMeasurement();
        m.begin(50.0, nano(0.20));
        for (LiveSample sample : samples) {
            if (sample.getNanoTime() >= nano(0.20)) {
                m.observe(samples, sample, 0.40);
            }
        }
        require(m.isComplete(),
                "bounded observation horizon discarded an otherwise useful physical response");
        require(m.usedBoundedLateWindow(),
                "non-settled bounded physical response was not marked lower-confidence");
        require(m.durationSeconds() > 0.0,
                "bounded physical response did not produce usable normalized timing");
    }

    private static void tooSmallPhysicalMapStepIsRejected() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        double[] maps = new double[]{50.0, 50.8, 51.5, 52.0, 52.4, 52.6, 52.7, 52.7, 52.8};
        for (int i = 0; i < maps.length; i++) {
            samples.add(sample(i * 0.05, maps[i]));
        }
        PhysicalMapResponseMeasurement m = new PhysicalMapResponseMeasurement();
        m.begin(50.0, nano(0.10));
        for (LiveSample sample : samples) {
            if (sample.getNanoTime() >= nano(0.10)) {
                m.observe(samples, sample, 0.30);
            }
        }
        require(m.isFailed(), "sub-4 kPa physical response was not rejected");
        require(m.failureReason().contains("at least 4.00 kPa"),
                "small physical response rejection did not explain the useful-step gate");
    }

    private static List<LiveSample> response(double offset) {
        double[] maps = new double[]{50.0, 52.0, 56.0, 60.0, 64.0, 69.0, 72.0,
                75.0, 78.0, 78.5, 78.6, 78.6, 78.7};
        List<LiveSample> out = new ArrayList<LiveSample>();
        for (int i = 0; i < maps.length; i++) {
            out.add(sample(i * 0.05, maps[i] + offset));
        }
        return out;
    }

    private static LiveSample sample(double seconds, double map) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.MAP, map);
        // Deliberately impossible/high prediction context: physical validity must
        // not depend on reaching this value.
        values.put(ChannelRole.FALLBACK_MAP, 110.0);
        long nano = nano(seconds);
        return new LiveSample(nano, seconds, values, 0.0, 0.0);
    }

    private static long nano(double seconds) {
        return Math.round(seconds * 1000000000.0);
    }

    private static void close(double actual, double expected,
                              double tolerance, String message) {
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(message + ": " + actual + " vs " + expected);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
