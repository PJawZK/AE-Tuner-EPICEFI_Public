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

/** Pure natural-pedal plateau analysis for adaptive Guided capture. */
final class PedalPlateauDetector {
    static final double WINDOW_SECONDS = 0.20;
    static final double RANGE_LIMIT = 5.0;
    static final double END_TO_END_LIMIT = 3.0;
    static final double MIN_USABLE_STEP = 10.0;
    static final double MAX_USABLE_STEP = 40.0;

    private PedalPlateauDetector() { }

    static Result evaluate(List<LiveSample> source, double baselineTps,
                           long nowNano) {
        List<LiveSample> recent = new ArrayList<LiveSample>();
        long cutoff = nowNano - (long) (WINDOW_SECONDS * 1000000000.0);
        if (source != null) {
            for (LiveSample sample : source) {
                if (sample.getNanoTime() >= cutoff
                        && Double.isFinite(sample.get(ChannelRole.TPS))) {
                    recent.add(sample);
                }
            }
        }
        if (recent.size() < 3) {
            double current = recent.isEmpty() ? baselineTps
                    : recent.get(recent.size() - 1).get(ChannelRole.TPS);
            return new Result(false, current,
                    current - baselineTps, Double.POSITIVE_INFINITY,
                    recent.isEmpty() ? null : recent.get(recent.size() - 1));
        }
        double duration = seconds(recent.get(0).getNanoTime(),
                recent.get(recent.size() - 1).getNanoTime());
        List<Double> values = new ArrayList<Double>();
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (LiveSample sample : recent) {
            double value = sample.get(ChannelRole.TPS);
            values.add(value);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        Collections.sort(values);
        double median = percentile(values, 0.5);
        double step = median - baselineTps;
        double endToEnd = Math.abs(recent.get(recent.size() - 1)
                .get(ChannelRole.TPS) - recent.get(0).get(ChannelRole.TPS));
        boolean usable = duration >= WINDOW_SECONDS * 0.75
                && max - min <= RANGE_LIMIT
                && endToEnd <= END_TO_END_LIMIT
                && step >= MIN_USABLE_STEP && step <= MAX_USABLE_STEP;
        LiveSample anchor = recent.get(0);
        double distance = Double.POSITIVE_INFINITY;
        for (LiveSample sample : recent) {
            double next = Math.abs(sample.get(ChannelRole.TPS) - median);
            if (next < distance) {
                distance = next;
                anchor = sample;
            }
        }
        return new Result(usable, median, step, max - min, anchor);
    }

    static final class Result {
        final boolean usable;
        final double medianTps;
        final double step;
        final double range;
        final LiveSample anchor;

        Result(boolean usable, double medianTps, double step,
               double range, LiveSample anchor) {
            this.usable = usable;
            this.medianTps = medianTps;
            this.step = step;
            this.range = range;
            this.anchor = anchor;
        }
    }

    private static double percentile(List<Double> sorted, double fraction) {
        if (sorted.isEmpty()) return Double.NaN;
        if (sorted.size() == 1) return sorted.get(0);
        double position = fraction * (sorted.size() - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) return sorted.get(lower);
        double weight = position - lower;
        return sorted.get(lower) * (1.0 - weight)
                + sorted.get(upper) * weight;
    }

    private static double seconds(long earlier, long later) {
        return Math.max(0.0, (later - earlier) / 1000000000.0);
    }
}
