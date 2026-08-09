package se.anders.tunerstudio.aetuner.proposal;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Single authoritative statistical policy for passive and guided Predictive
 * MAP Blend Duration proposals.
 *
 * Raw measured durations are filtered and summarized before any table bounds
 * are applied. The conservative margin, clamp and rounding are used only when
 * an eligible final proposal is produced.
 */
public final class BlendDurationPolicy {
    static final double MIN_DURATION = 0.08;
    static final double MAX_DURATION = 0.80;
    static final double PROPOSAL_MARGIN_SECONDS = 0.02;
    public static final int MIN_EVENTS_FOR_PROPOSAL = 3;
    static final int MIN_EVENTS_HIGH_CONFIDENCE = 5;
    public static final double MAX_ELIGIBLE_RANGE_SECONDS = 0.18;
    public static final double MAX_ELIGIBLE_IQR_SECONDS = 0.10;
    public static final double MAX_ELIGIBLE_STDDEV_SECONDS = 0.08;
    static final double HIGH_CONFIDENCE_RANGE_SECONDS = 0.10;
    static final double HIGH_CONFIDENCE_IQR_SECONDS = 0.05;
    static final double HIGH_CONFIDENCE_STDDEV_SECONDS = 0.04;
    static final double OUTLIER_IQR_MULTIPLIER = 1.5;

    private BlendDurationPolicy() {
    }

    public enum Confidence {
        INSUFFICIENT("INSUFFICIENT"),
        LOW("LOW"),
        MEDIUM("MEDIUM"),
        HIGH("HIGH");

        public final String label;

        Confidence(String label) {
            this.label = label;
        }
    }

    public static final class Stats {
        public final int retainedCount;
        public final int outlierCount;
        public final double median;
        public final double mean;
        public final double minimum;
        public final double maximum;
        public final double range;
        public final double iqr;
        public final double standardDeviation;

        Stats(int retainedCount, int outlierCount, double median,
              double mean, double minimum, double maximum,
              double range, double iqr, double standardDeviation) {
            this.retainedCount = retainedCount;
            this.outlierCount = outlierCount;
            this.median = median;
            this.mean = mean;
            this.minimum = minimum;
            this.maximum = maximum;
            this.range = range;
            this.iqr = iqr;
            this.standardDeviation = standardDeviation;
        }
    }

    public static final class Evaluation {
        public final Stats stats;
        public final Confidence confidence;
        public final boolean eligible;
        public final double proposedValue;

        Evaluation(Stats stats, Confidence confidence,
                   boolean eligible, double proposedValue) {
            this.stats = stats;
            this.confidence = confidence;
            this.eligible = eligible;
            this.proposedValue = proposedValue;
        }
    }

    public static Evaluation evaluate(List<Double> rawValues) {
        Stats stats = buildStats(rawValues);
        Confidence confidence = confidence(stats);
        boolean eligible = confidence == Confidence.MEDIUM
                || confidence == Confidence.HIGH;
        double proposal = eligible
                ? finalProposal(stats.median) : Double.NaN;
        return new Evaluation(stats, confidence, eligible, proposal);
    }

    static double finalProposal(double retainedMedian) {
        return round2(clamp(retainedMedian + PROPOSAL_MARGIN_SECONDS,
                MIN_DURATION, MAX_DURATION));
    }

    private static Confidence confidence(Stats stats) {
        if (stats.retainedCount < MIN_EVENTS_FOR_PROPOSAL) {
            return Confidence.INSUFFICIENT;
        }
        if (stats.range > MAX_ELIGIBLE_RANGE_SECONDS
                || stats.iqr > MAX_ELIGIBLE_IQR_SECONDS
                || stats.standardDeviation > MAX_ELIGIBLE_STDDEV_SECONDS) {
            return Confidence.LOW;
        }
        if (stats.retainedCount >= MIN_EVENTS_HIGH_CONFIDENCE
                && stats.range <= HIGH_CONFIDENCE_RANGE_SECONDS
                && stats.iqr <= HIGH_CONFIDENCE_IQR_SECONDS
                && stats.standardDeviation <= HIGH_CONFIDENCE_STDDEV_SECONDS) {
            return Confidence.HIGH;
        }
        return Confidence.MEDIUM;
    }

    private static Stats buildStats(List<Double> rawValues) {
        List<Double> sorted = new ArrayList<Double>();
        if (rawValues != null) {
            for (Double value : rawValues) {
                if (value != null && Double.isFinite(value.doubleValue())
                        && value.doubleValue() > 0.0) {
                    sorted.add(value);
                }
            }
        }
        if (sorted.isEmpty()) {
            return empty(0);
        }

        Collections.sort(sorted);
        List<Double> retained = new ArrayList<Double>(sorted);
        int outliers = 0;
        if (sorted.size() >= 4) {
            double q1 = percentile(sorted, 0.25);
            double q3 = percentile(sorted, 0.75);
            double rawIqr = q3 - q1;
            double lower = q1 - OUTLIER_IQR_MULTIPLIER * rawIqr;
            double upper = q3 + OUTLIER_IQR_MULTIPLIER * rawIqr;
            retained.clear();
            for (Double value : sorted) {
                if (value.doubleValue() >= lower
                        && value.doubleValue() <= upper) {
                    retained.add(value);
                } else {
                    outliers++;
                }
            }
        }
        if (retained.isEmpty()) {
            return empty(outliers);
        }

        double sum = 0.0;
        for (Double value : retained) {
            sum += value.doubleValue();
        }
        double mean = sum / retained.size();
        double squared = 0.0;
        for (Double value : retained) {
            double delta = value.doubleValue() - mean;
            squared += delta * delta;
        }
        double minimum = retained.get(0).doubleValue();
        double maximum = retained.get(retained.size() - 1).doubleValue();
        return new Stats(retained.size(), outliers,
                percentile(retained, 0.50), mean, minimum, maximum,
                maximum - minimum,
                percentile(retained, 0.75) - percentile(retained, 0.25),
                Math.sqrt(squared / retained.size()));
    }

    private static Stats empty(int outliers) {
        return new Stats(0, outliers, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN);
    }

    private static double percentile(List<Double> sorted, double fraction) {
        if (sorted.size() == 1) {
            return sorted.get(0).doubleValue();
        }
        double position = fraction * (sorted.size() - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) {
            return sorted.get(lower).doubleValue();
        }
        double weight = position - lower;
        return sorted.get(lower).doubleValue() * (1.0 - weight)
                + sorted.get(upper).doubleValue() * weight;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
