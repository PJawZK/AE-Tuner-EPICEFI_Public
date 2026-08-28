package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.List;
import java.util.Locale;

/**
 * Event-local automatic-gear evidence.
 *
 * The session latch remains immutable. This helper only asks whether one
 * retained event contains sustained, plausible evidence of a different actual
 * gear, so that event cannot contaminate the latched-gear comparison group.
 */
final class GuidedEventGearEvidence {
    static final double MAX_VSS_KPH = 300.0;
    static final double MAX_VSS_JUMP_KPH = 25.0;
    static final double VSS_JUMP_WINDOW_SECONDS = 0.35;
    static final int MIN_TRUSTED_SAMPLES = 6;
    static final double MIN_DOMINANT_FRACTION = 0.75;
    static final double MIN_SUPPORT_SPAN_SECONDS = 0.08;

    static final class Result {
        final int sessionGear;
        final int dominantGear;
        final int trustedSamples;
        final int dominantSamples;
        final int rejectedVssSamples;
        final double dominantFraction;
        final double supportSpanSeconds;
        final boolean mismatch;

        Result(int sessionGear, int dominantGear, int trustedSamples,
               int dominantSamples, int rejectedVssSamples,
               double dominantFraction, double supportSpanSeconds,
               boolean mismatch) {
            this.sessionGear = sessionGear;
            this.dominantGear = dominantGear;
            this.trustedSamples = trustedSamples;
            this.dominantSamples = dominantSamples;
            this.rejectedVssSamples = rejectedVssSamples;
            this.dominantFraction = dominantFraction;
            this.supportSpanSeconds = supportSpanSeconds;
            this.mismatch = mismatch;
        }

        static Result unavailable(int sessionGear) {
            return new Result(sessionGear, 0, 0, 0, 0,
                    Double.NaN, Double.NaN, false);
        }

        String text() {
            if (dominantGear <= 0 || trustedSamples == 0) {
                return "event gear evidence unavailable";
            }
            return "event gear " + dominantGear
                    + " from " + dominantSamples + "/" + trustedSamples
                    + " trusted samples (" + pct(dominantFraction)
                    + ", span " + f2(supportSpanSeconds) + " s"
                    + (rejectedVssSamples > 0
                        ? ", rejected VSS " + rejectedVssSamples : "")
                    + ")"
                    + (mismatch ? " — SUSTAINED SESSION-GEAR MISMATCH" : "");
        }
    }

    private GuidedEventGearEvidence() { }

    static Result evaluate(List<LiveSample> samples, int sessionGear) {
        if (samples == null || samples.isEmpty()
                || sessionGear < 1 || sessionGear > 8) {
            return Result.unavailable(sessionGear);
        }

        int[] counts = new int[9];
        long[] firstNano = new long[9];
        long[] lastNano = new long[9];
        int trusted = 0;
        int rejectedVss = 0;
        double previousSaneVss = Double.NaN;
        long previousSaneNano = 0L;

        for (LiveSample sample : samples) {
            if (sample == null) continue;
            double rawVss = sample.get(ChannelRole.VSS);
            if (!Double.isFinite(rawVss) || rawVss < 0.0 || rawVss > MAX_VSS_KPH) {
                rejectedVss++;
                continue;
            }
            if (Double.isFinite(previousSaneVss) && previousSaneNano > 0L) {
                double dt = seconds(previousSaneNano, sample.getNanoTime());
                if (dt <= VSS_JUMP_WINDOW_SECONDS
                        && Math.abs(rawVss - previousSaneVss) > MAX_VSS_JUMP_KPH) {
                    rejectedVss++;
                    // Rejected corruption must not become the next reference.
                    continue;
                }
            }
            previousSaneVss = rawVss;
            previousSaneNano = sample.getNanoTime();

            int gear = integralGear(sample.get(ChannelRole.GEAR));
            if (gear == 0) continue;
            trusted++;
            counts[gear]++;
            if (firstNano[gear] == 0L) firstNano[gear] = sample.getNanoTime();
            lastNano[gear] = sample.getNanoTime();
        }

        if (trusted == 0) return new Result(sessionGear, 0, 0, 0,
                rejectedVss, Double.NaN, Double.NaN, false);

        int dominant = 0;
        for (int gear = 1; gear <= 8; gear++) {
            if (counts[gear] > counts[dominant]) dominant = gear;
        }
        int dominantCount = counts[dominant];
        double fraction = dominantCount / (double) trusted;
        double span = firstNano[dominant] > 0L
                ? seconds(firstNano[dominant], lastNano[dominant])
                : Double.NaN;
        boolean mismatch = dominant != sessionGear
                && dominantCount >= MIN_TRUSTED_SAMPLES
                && fraction >= MIN_DOMINANT_FRACTION
                && Double.isFinite(span)
                && span >= MIN_SUPPORT_SPAN_SECONDS;
        return new Result(sessionGear, dominant, trusted, dominantCount,
                rejectedVss, fraction, span, mismatch);
    }

    private static int integralGear(double value) {
        if (!Double.isFinite(value)) return 0;
        int rounded = (int) Math.round(value);
        return rounded >= 1 && rounded <= 8
                && Math.abs(value - rounded) <= 0.25 ? rounded : 0;
    }

    private static double seconds(long earlier, long later) {
        return Math.max(0.0, (later - earlier) / 1000000000.0);
    }

    private static String f2(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.US, "%.2f", value) : "n/a";
    }

    private static String pct(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.US, "%.0f%%", value * 100.0) : "n/a";
    }
}
