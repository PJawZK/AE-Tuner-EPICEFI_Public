package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Replays EpicEFI/rusEFI Predictive MAP blending from exported Guided samples.
 *
 * This is deliberately a validation layer, not a proposal rule. It verifies that
 * AE Tuner is interpreting the ECU's blend equation, rising fallback target latch,
 * timer resets and current-RPM curve interpolation consistently with logged
 * Effective MAP before numerical Blend Duration tuning is allowed again.
 */
final class BlendDurationFirmwareReplay {
    private static final double TARGET_EPSILON_KPA = 0.05;
    private static final double MAX_MEAN_ERROR_KPA = 1.50;
    private static final double MAX_PEAK_ERROR_KPA = 3.00;
    private static final int MIN_REPLAY_SAMPLES = 3;

    static final class Result {
        final boolean available;
        final boolean passed;
        final int comparedSamples;
        final int targetResets;
        final double meanAbsError;
        final double peakAbsError;
        final double finalSegmentSeconds;
        final double finalTargetKpa;
        final String reason;

        Result(boolean available, boolean passed, int comparedSamples,
               int targetResets, double meanAbsError, double peakAbsError,
               double finalSegmentSeconds, double finalTargetKpa, String reason) {
            this.available = available;
            this.passed = passed;
            this.comparedSamples = comparedSamples;
            this.targetResets = targetResets;
            this.meanAbsError = meanAbsError;
            this.peakAbsError = peakAbsError;
            this.finalSegmentSeconds = finalSegmentSeconds;
            this.finalTargetKpa = finalTargetKpa;
            this.reason = reason == null ? "" : reason;
        }

        String summary() {
            if (!available) return "REPLAY UNAVAILABLE — " + reason;
            return (passed ? "REPLAY PASS" : "REPLAY FAIL")
                    + " | samples " + comparedSamples
                    + " | target raises " + targetResets
                    + " | mean |Effective MAP error| " + f2(meanAbsError) + " kPa"
                    + " | peak " + f2(peakAbsError) + " kPa"
                    + (Double.isFinite(finalSegmentSeconds)
                        ? " | final prediction segment " + f3(finalSegmentSeconds) + " s"
                        : "")
                    + (Double.isFinite(finalTargetKpa)
                        ? " | final target " + f1(finalTargetKpa) + " kPa"
                        : "")
                    + (reason.length() == 0 ? "" : " | " + reason);
        }
    }

    private static final class Row {
        double time;
        double rpm;
        double map;
        double fallback;
        double effective;
        boolean prediction;
    }

    private BlendDurationFirmwareReplay() { }

    static Result evaluate(String trace, AeProjectSnapshot snapshot) {
        if (snapshot == null || !snapshot.hasBlendDurationCurve()) {
            return unavailable("working Blend Duration curve is unavailable");
        }
        List<Row> rows = parse(trace);
        if (rows.isEmpty()) {
            return unavailable("trace has no firmware-replay samples");
        }

        double[] bins = snapshot.getBlendDurationRpmBins();
        double[] values = snapshot.getBlendDurationValues();
        boolean active = false;
        double latched = Double.NaN;
        double resetTime = Double.NaN;
        double lastActiveTime = Double.NaN;
        double errorSum = 0.0;
        double errorPeak = 0.0;
        int compared = 0;
        int resets = 0;

        for (Row row : rows) {
            if (!row.prediction) {
                if (active) {
                    active = false;
                }
                continue;
            }
            if (!finite(row.rpm, row.map, row.fallback, row.effective)) continue;

            if (!active) {
                active = true;
                latched = row.fallback;
                resetTime = row.time;
                resets++;
            } else if (row.fallback > latched + TARGET_EPSILON_KPA) {
                latched = row.fallback;
                resetTime = row.time;
                resets++;
            }

            double duration = interpolate(row.rpm, bins, values);
            if (!Double.isFinite(duration) || duration <= 0.0) continue;
            double elapsed = Math.max(0.0, row.time - resetTime);
            double factor = Math.max(0.0, Math.min(1.0, elapsed / duration));
            double reconstructed = latched + (row.map - latched) * factor;
            double error = Math.abs(row.effective - reconstructed);
            errorSum += error;
            errorPeak = Math.max(errorPeak, error);
            compared++;
            lastActiveTime = row.time;
        }

        if (compared < MIN_REPLAY_SAMPLES) {
            return unavailable("fewer than " + MIN_REPLAY_SAMPLES
                    + " comparable Effective MAP samples were captured");
        }
        double mean = errorSum / compared;
        boolean passed = mean <= MAX_MEAN_ERROR_KPA && errorPeak <= MAX_PEAK_ERROR_KPA;
        double finalSegment = Double.isFinite(lastActiveTime) && Double.isFinite(resetTime)
                ? Math.max(0.0, lastActiveTime - resetTime) : Double.NaN;
        String reason = passed ? "firmware interpretation agrees with logged Effective MAP"
                : "firmware interpretation does not agree closely enough with logged Effective MAP; numerical tuning is withheld";
        return new Result(true, passed, compared, resets, mean, errorPeak,
                finalSegment, latched, reason);
    }

    private static Result unavailable(String reason) {
        return new Result(false, false, 0, 0, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, reason);
    }

    private static List<Row> parse(String trace) {
        List<Row> rows = new ArrayList<Row>();
        if (trace == null || trace.length() == 0) return rows;
        String[] lines = trace.replace("\r", "").split("\n");
        boolean sampleRows = false;
        for (String line : lines) {
            if (line.startsWith("dt_s,rpm,tps,map,fallbackMap,effectiveMap,")) {
                sampleRows = true;
                continue;
            }
            if (!sampleRows || line.length() == 0 || line.indexOf('=') >= 0) continue;
            String[] fields = line.split(",", -1);
            if (fields.length < 10) continue;
            double time = number(fields[0]);
            double rpm = number(fields[1]);
            double map = number(fields[3]);
            double fallback = number(fields[4]);
            double effective = number(fields[5]);
            double prediction = number(fields[9]);
            if (!Double.isFinite(time)) continue;
            Row row = new Row();
            row.time = time;
            row.rpm = rpm;
            row.map = map;
            row.fallback = fallback;
            row.effective = effective;
            row.prediction = Double.isFinite(prediction) && prediction >= 0.5;
            rows.add(row);
        }
        return rows;
    }

    private static double interpolate(double rpm, double[] bins, double[] values) {
        if (bins == null || values == null || bins.length == 0
                || bins.length != values.length) return Double.NaN;
        if (rpm <= bins[0]) return values[0];
        int last = bins.length - 1;
        if (rpm >= bins[last]) return values[last];
        for (int i = 1; i < bins.length; i++) {
            if (rpm <= bins[i]) {
                double span = bins[i] - bins[i - 1];
                if (span <= 0.0) return values[i];
                double fraction = (rpm - bins[i - 1]) / span;
                return values[i - 1] + (values[i] - values[i - 1]) * fraction;
            }
        }
        return values[last];
    }

    private static double number(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (RuntimeException ex) {
            return Double.NaN;
        }
    }

    private static boolean finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) return false;
        }
        return true;
    }

    private static String f1(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private static String f2(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String f3(double value) {
        return String.format(Locale.US, "%.3f", value);
    }
}
