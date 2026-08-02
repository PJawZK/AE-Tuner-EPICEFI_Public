package se.anders.tunerstudio.aetuner;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class TpsNoiseCalibration {
    private static final DecimalFormat F3 = new DecimalFormat("0.000");
    private final List<Double> absoluteTpsDot = new ArrayList<Double>();
    private boolean running;
    private long startNano;
    private long durationNano;
    private Result lastResult;

    void start(double seconds) {
        absoluteTpsDot.clear();
        running = true;
        startNano = System.nanoTime();
        durationNano = (long) Math.max(1.0, seconds) * 1000000000L;
        lastResult = null;
    }

    boolean isRunning() {
        return running;
    }

    void addSample(LiveSample sample) {
        if (!running) {
            return;
        }
        if (Double.isFinite(sample.getTpsDot())) {
            absoluteTpsDot.add(Math.abs(sample.getTpsDot()));
        }
        if (System.nanoTime() - startNano >= durationNano) {
            finish();
        }
    }

    Result finish() {
        running = false;
        List<Double> sorted = new ArrayList<Double>(absoluteTpsDot);
        Collections.sort(sorted);

        double p95 = percentile(sorted, 0.95);
        double p99 = percentile(sorted, 0.99);
        double p999 = percentile(sorted, 0.999);
        double max = sorted.isEmpty() ? Double.NaN : sorted.get(sorted.size() - 1).doubleValue();
        double recommended = Math.max(0.25, Math.max(p99 * 3.0, p999 * 1.5));
        lastResult = new Result(sorted.size(), p95, p99, p999, max, recommended);
        return lastResult;
    }

    Result getLastResult() {
        return lastResult;
    }

    double secondsRemaining() {
        if (!running) {
            return 0.0;
        }
        return Math.max(0.0, (durationNano - (System.nanoTime() - startNano)) / 1000000000.0);
    }

    private static double percentile(List<Double> sorted, double fraction) {
        if (sorted.isEmpty()) {
            return Double.NaN;
        }
        int index = (int) Math.ceil(fraction * sorted.size()) - 1;
        index = Math.max(0, Math.min(sorted.size() - 1, index));
        return sorted.get(index).doubleValue();
    }

    static final class Result {
        private final int samples;
        private final double p95;
        private final double p99;
        private final double p999;
        private final double max;
        private final double recommendedThreshold;

        Result(int samples, double p95, double p99, double p999, double max, double recommendedThreshold) {
            this.samples = samples;
            this.p95 = p95;
            this.p99 = p99;
            this.p999 = p999;
            this.max = max;
            this.recommendedThreshold = recommendedThreshold;
        }

        int getSamples() {
            return samples;
        }

        double getRecommendedThreshold() {
            return recommendedThreshold;
        }

        String toDisplayText() {
            return "Samples " + samples
                    + " | p95 " + fmt(p95)
                    + " %/s | p99 " + fmt(p99)
                    + " %/s | p99.9 " + fmt(p999)
                    + " %/s | max " + fmt(max)
                    + " %/s | recommended start threshold " + fmt(recommendedThreshold)
                    + " %/s";
        }

        private static String fmt(double value) {
            return Double.isFinite(value) ? F3.format(value) : "n/a";
        }
    }
}
