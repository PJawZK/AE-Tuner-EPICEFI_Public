package se.anders.tunerstudio.aetuner.guided;

import java.util.Locale;

/**
 * Classifies one physical positive-TPS maneuver by peak amplitude.
 *
 * The requested target band is the strict controlled-comparison gate. Only a
 * maneuver whose peak lands inside that band may advance candidate progress.
 * Good/Usable/off-target physical maneuvers remain retained as diagnostic
 * quality evidence so the operator can see what happened without contaminating
 * the requested like-for-like comparison. Rejects are too small, non-positive,
 * non-finite, or implausibly large even for diagnostic comparison.
 */
public final class EngagementManeuverQualityPolicy {
    public enum Quality {
        EXCELLENT(true),
        GOOD(false),
        USABLE(false),
        DIAGNOSTIC_ONLY(false),
        REJECT(false);

        public final boolean comparable;
        Quality(boolean comparable) { this.comparable = comparable; }
    }

    public static final class Assessment {
        public final Quality quality;
        public final double peakDeltaTps;
        public final double targetErrorTps;
        public final String reason;

        private Assessment(Quality quality,
                           double peakDeltaTps,
                           double targetErrorTps,
                           String reason) {
            this.quality = quality == null ? Quality.REJECT : quality;
            this.peakDeltaTps = peakDeltaTps;
            this.targetErrorTps = targetErrorTps;
            this.reason = reason == null ? "" : reason;
        }

        public boolean comparable() { return quality.comparable; }
    }

    private EngagementManeuverQualityPolicy() { }

    public static Assessment assess(double startTps,
                                    double peakTps,
                                    double targetTps,
                                    double targetMin,
                                    double targetMax,
                                    double requestedStep) {
        if (!finite(startTps, peakTps, targetTps, targetMin, targetMax, requestedStep)
                || requestedStep <= 0.0) {
            return rejected(Double.NaN, Double.NaN,
                    "non-finite maneuver geometry or invalid TPS Step");
        }

        double delta = peakTps - startTps;
        double targetError = Math.abs(peakTps - targetTps);
        if (delta <= 0.0) {
            return rejected(delta, targetError,
                    "peak did not produce a positive TPS opening");
        }

        double ratio = delta / requestedStep;
        double minimumPhysical = Math.max(0.35, requestedStep * 0.25);
        if (delta < minimumPhysical) {
            return rejected(delta, targetError,
                    "opening was too small to separate from start-condition motion");
        }
        if (ratio > 3.50) {
            return rejected(delta, targetError,
                    "opening exceeded 3.5x the requested TPS Step and is not comparable");
        }

        if (peakTps >= targetMin && peakTps <= targetMax) {
            return accepted(Quality.EXCELLENT, delta, targetError,
                    "peak landed inside the strict counting target band");
        }
        if (ratio >= 0.70 && ratio <= 1.50) {
            return accepted(Quality.GOOD, delta, targetError,
                    "peak missed the strict target band; retained diagnostically with no candidate progress");
        }
        if (ratio >= 0.50 && ratio <= 1.75) {
            return accepted(Quality.USABLE, delta, targetError,
                    "physical opening is usable diagnostically but outside the strict counting target band");
        }
        return accepted(Quality.DIAGNOSTIC_ONLY, delta, targetError,
                "physical opening is retained diagnostically but does not advance candidate comparison");
    }

    private static Assessment accepted(Quality quality,
                                       double delta,
                                       double targetError,
                                       String reason) {
        return new Assessment(quality, delta, targetError,
                quality.name().replace('_', ' ') + " — " + reason
                        + " (peak change " + fmt(delta) + " %TPS)");
    }

    private static Assessment rejected(double delta,
                                       double targetError,
                                       String reason) {
        return new Assessment(Quality.REJECT, delta, targetError,
                "REJECT — " + reason
                        + (Double.isFinite(delta)
                        ? " (peak change " + fmt(delta) + " %TPS)" : ""));
    }

    private static boolean finite(double... values) {
        if (values == null) return false;
        for (double value : values) {
            if (!Double.isFinite(value)) return false;
        }
        return true;
    }

    private static String fmt(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%.2f", value) : "n/a";
    }
}
