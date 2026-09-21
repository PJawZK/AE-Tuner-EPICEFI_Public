package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Prediction-independent physical MAP response timing for Guided Blend Duration.
 *
 * The measurement deliberately avoids treating fallback/predicted MAP as the
 * physical endpoint. The driver establishes a stable pre-opening road baseline,
 * makes one clear opening, then holds the pedal. Once the pedal plateau is
 * proven, this class observes the real MAP response until a short late window is
 * stable enough (or the bounded observation horizon is reached), derives a
 * robust late MAP level from that event itself, and measures the central 20->80%
 * portion of the observed MAP rise.
 *
 * Absolute MAP values may move with weather/engine state. Using a normalized
 * fraction of each event's own physical MAP step keeps the timing evidence tied
 * to response shape rather than one predicted pressure target. Precision is
 * intentionally modest: comparable repeated events, not one millisecond-exact
 * transient, provide the useful calibration evidence.
 */
final class PhysicalMapResponseMeasurement {
    static final double LOW_FRACTION = 0.20;
    static final double HIGH_FRACTION = 0.80;
    static final double MIN_PHYSICAL_STEP_KPA = 4.0;
    static final double LATE_WINDOW_SECONDS = 0.20;
    static final double MIN_OBSERVATION_SECONDS = 0.35;
    static final double ABS_LATE_RANGE_KPA = 2.0;
    static final double REL_LATE_RANGE_FRACTION = 0.12;

    private double baselineMap = Double.NaN;
    private long plateauAcquiredNano;
    private LiveSample lowCrossSample;
    private LiveSample highCrossSample;
    private LiveSample completionSample;
    private double lateMap = Double.NaN;
    private double mapStep = Double.NaN;
    private double lowThreshold = Double.NaN;
    private double highThreshold = Double.NaN;
    private double durationSeconds = Double.NaN;
    private boolean complete;
    private boolean failed;
    private boolean boundedLateWindow;
    private String failureReason = "";
    private double lastLateRange = Double.NaN;

    void reset() {
        baselineMap = Double.NaN;
        plateauAcquiredNano = 0L;
        lowCrossSample = null;
        highCrossSample = null;
        completionSample = null;
        lateMap = Double.NaN;
        mapStep = Double.NaN;
        lowThreshold = Double.NaN;
        highThreshold = Double.NaN;
        durationSeconds = Double.NaN;
        complete = false;
        failed = false;
        boundedLateWindow = false;
        failureReason = "";
        lastLateRange = Double.NaN;
    }

    void begin(double nextBaselineMap, long nextPlateauAcquiredNano) {
        reset();
        baselineMap = nextBaselineMap;
        plateauAcquiredNano = nextPlateauAcquiredNano;
    }

    void observe(List<LiveSample> attemptSamples,
                 LiveSample latest,
                 double maxObservationSeconds) {
        if (complete || failed || latest == null
                || !Double.isFinite(baselineMap)
                || plateauAcquiredNano <= 0L) {
            return;
        }
        double elapsed = seconds(plateauAcquiredNano, latest.getNanoTime());
        if (elapsed < MIN_OBSERVATION_SECONDS) return;

        Window late = lateWindow(attemptSamples, latest.getNanoTime());
        if (!late.usable()) {
            if (elapsed >= boundedMax(maxObservationSeconds)) {
                fail("Not enough valid MAP samples were retained in the late physical-response window.");
            }
            return;
        }

        lateMap = late.median;
        mapStep = lateMap - baselineMap;
        lastLateRange = late.range;
        double allowedRange = Math.max(ABS_LATE_RANGE_KPA,
                Math.abs(mapStep) * REL_LATE_RANGE_FRACTION);
        boolean stableEnough = mapStep >= MIN_PHYSICAL_STEP_KPA
                && late.range <= allowedRange
                && Math.abs(late.endToEnd) <= allowedRange;
        boolean atLimit = elapsed >= boundedMax(maxObservationSeconds);

        if (!stableEnough && !atLimit) return;
        if (mapStep < MIN_PHYSICAL_STEP_KPA) {
            fail("Observed physical MAP movement was only " + f2(mapStep)
                    + " kPa; at least " + f2(MIN_PHYSICAL_STEP_KPA)
                    + " kPa is needed for a useful normalized response measurement.");
            return;
        }

        boundedLateWindow = !stableEnough;
        lowThreshold = baselineMap + mapStep * LOW_FRACTION;
        highThreshold = baselineMap + mapStep * HIGH_FRACTION;
        lowCrossSample = firstCrossing(attemptSamples, lowThreshold, null);
        highCrossSample = firstCrossing(attemptSamples, highThreshold, lowCrossSample);
        if (lowCrossSample == null || highCrossSample == null
                || highCrossSample.getNanoTime() < lowCrossSample.getNanoTime()) {
            fail("The retained physical MAP trace did not contain a clean 20->80% crossing for the observed response step.");
            return;
        }

        durationSeconds = seconds(lowCrossSample.getNanoTime(), highCrossSample.getNanoTime());
        if (!(durationSeconds > 0.0)) {
            fail("The normalized physical MAP response duration was not positive.");
            return;
        }
        completionSample = latest;
        complete = true;
    }

    boolean isComplete() { return complete; }
    boolean isFailed() { return failed; }
    boolean usedBoundedLateWindow() { return boundedLateWindow; }
    String failureReason() { return failureReason; }
    double baselineMap() { return baselineMap; }
    double lateMap() { return lateMap; }
    double mapStep() { return mapStep; }
    double lowThreshold() { return lowThreshold; }
    double highThreshold() { return highThreshold; }
    double durationSeconds() { return durationSeconds; }
    double lateWindowRange() { return lastLateRange; }
    LiveSample lowCrossSample() { return lowCrossSample; }
    LiveSample highCrossSample() { return highCrossSample; }
    LiveSample completionSample() { return completionSample; }

    double observationElapsedSeconds(LiveSample latest) {
        return latest == null || plateauAcquiredNano <= 0L
                ? Double.NaN
                : seconds(plateauAcquiredNano, latest.getNanoTime());
    }

    String evidenceText() {
        if (failed) return "Physical MAP response: unavailable — " + failureReason;
        if (!complete) {
            return "Physical MAP response: observing real MAP; endpoint is derived from the event, not fallbackMap.";
        }
        return "Physical MAP response: " + f2(baselineMap) + " -> " + f2(lateMap)
                + " kPa (step " + f2(mapStep) + "), 20% " + f2(lowThreshold)
                + " -> 80% " + f2(highThreshold) + " in " + f3(durationSeconds)
                + " s | late-window range " + f2(lastLateRange) + " kPa"
                + (boundedLateWindow
                    ? " | bounded late-window estimate (not fully settled; keep as lower-confidence physical evidence)"
                    : " | late window settled");
    }

    private void fail(String reason) {
        failed = true;
        failureReason = reason == null ? "Physical MAP response could not be measured." : reason;
    }

    private static Window lateWindow(List<LiveSample> source, long nowNano) {
        if (source == null || source.isEmpty()) return Window.empty();
        long cutoff = nowNano - (long)(LATE_WINDOW_SECONDS * 1000000000.0);
        List<LiveSample> samples = new ArrayList<LiveSample>();
        List<Double> maps = new ArrayList<Double>();
        for (LiveSample sample : source) {
            if (sample == null || sample.getNanoTime() < cutoff) continue;
            double map = sample.get(ChannelRole.MAP);
            if (!Double.isFinite(map)) continue;
            samples.add(sample);
            maps.add(map);
        }
        if (samples.size() < 3) return Window.empty();
        double span = seconds(samples.get(0).getNanoTime(),
                samples.get(samples.size() - 1).getNanoTime());
        if (span < LATE_WINDOW_SECONDS * 0.70) return Window.empty();
        Collections.sort(maps);
        double median = percentile(maps, 0.5);
        double min = maps.get(0);
        double max = maps.get(maps.size() - 1);
        double first = samples.get(0).get(ChannelRole.MAP);
        double last = samples.get(samples.size() - 1).get(ChannelRole.MAP);
        return new Window(median, max - min, last - first, samples.size());
    }

    private static LiveSample firstCrossing(List<LiveSample> source,
                                            double threshold,
                                            LiveSample notBefore) {
        if (source == null || !Double.isFinite(threshold)) return null;
        long minimumNano = notBefore == null ? Long.MIN_VALUE : notBefore.getNanoTime();
        for (LiveSample sample : source) {
            if (sample == null || sample.getNanoTime() < minimumNano) continue;
            double map = sample.get(ChannelRole.MAP);
            if (Double.isFinite(map) && map >= threshold) return sample;
        }
        return null;
    }

    private static double boundedMax(double configured) {
        return Double.isFinite(configured) && configured >= MIN_OBSERVATION_SECONDS
                ? configured : 1.20;
    }

    private static double percentile(List<Double> sorted, double fraction) {
        if (sorted.isEmpty()) return Double.NaN;
        if (sorted.size() == 1) return sorted.get(0);
        double position = fraction * (sorted.size() - 1);
        int lower = (int)Math.floor(position);
        int upper = (int)Math.ceil(position);
        if (lower == upper) return sorted.get(lower);
        double weight = position - lower;
        return sorted.get(lower) * (1.0 - weight)
                + sorted.get(upper) * weight;
    }

    private static double seconds(long earlier, long later) {
        return Math.max(0.0, (later - earlier) / 1000000000.0);
    }

    private static String f2(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.US, "%.2f", value) : "n/a";
    }

    private static String f3(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.US, "%.3f", value) : "n/a";
    }

    private static final class Window {
        final double median;
        final double range;
        final double endToEnd;
        final int samples;

        Window(double median, double range, double endToEnd, int samples) {
            this.median = median;
            this.range = range;
            this.endToEnd = endToEnd;
            this.samples = samples;
        }

        static Window empty() {
            return new Window(Double.NaN, Double.NaN, Double.NaN, 0);
        }

        boolean usable() {
            return samples >= 3 && Double.isFinite(median)
                    && Double.isFinite(range) && Double.isFinite(endToEnd);
        }
    }
}
