package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Behavior-preserving duration spread statistics for one comparable Guided group. */
final class BlendDurationSeriesStats {
    final int count;
    final double median;
    final double min;
    final double max;
    final double range;
    final double iqr;
    final double sd;

    BlendDurationSeriesStats(int count, double median, double min,
                             double max, double range, double iqr,
                             double sd) {
        this.count = count;
        this.median = median;
        this.min = min;
        this.max = max;
        this.range = range;
        this.iqr = iqr;
        this.sd = sd;
    }

    static BlendDurationSeriesStats from(List<BlendDurationAttempt> attempts) {
        if (attempts.isEmpty()) {
            return new BlendDurationSeriesStats(0, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }
        List<Double> values = new ArrayList<Double>();
        double sum = 0.0;
        for (BlendDurationAttempt attempt : attempts) {
            values.add(attempt.duration);
            sum += attempt.duration;
        }
        Collections.sort(values);
        double mean = sum / values.size();
        double square = 0.0;
        for (Double value : values) {
            double delta = value - mean;
            square += delta * delta;
        }
        double min = values.get(0);
        double max = values.get(values.size() - 1);
        return new BlendDurationSeriesStats(values.size(),
                percentile(values, 0.5), min, max, max - min,
                percentile(values, 0.75) - percentile(values, 0.25),
                Math.sqrt(square / values.size()));
    }

    private static double percentile(List<Double> sorted, double fraction) {
        if (sorted.size() == 1) return sorted.get(0);
        double position = fraction * (sorted.size() - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) return sorted.get(lower);
        double weight = position - lower;
        return sorted.get(lower) * (1.0 - weight)
                + sorted.get(upper) * weight;
    }
}
