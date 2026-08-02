package se.anders.tunerstudio.aetuner;

import java.util.List;

/** Utilities for small monotonic firmware counters which may wrap at 255 -> 0. */
final class CounterMath {
    private static final int MODULO = 256;
    private static final int MAX_REASONABLE_STEP = 32;

    private CounterMath() { }

    static Result analyze(List<LiveSample> samples, ChannelRole role) {
        int increments = 0;
        int wraps = 0;
        int anomalies = 0;
        Integer previous = null;
        if (samples != null) {
            for (LiveSample sample : samples) {
                double raw = sample.get(role);
                if (!Double.isFinite(raw)) {
                    continue;
                }
                int current = normalize(raw);
                if (previous != null && current != previous.intValue()) {
                    int direct = current - previous.intValue();
                    if (direct > 0 && direct <= MAX_REASONABLE_STEP) {
                        increments += direct;
                    } else if (direct < 0 && previous.intValue() >= 224 && current <= 32) {
                        int wrapped = MODULO - previous.intValue() + current;
                        if (wrapped > 0 && wrapped <= MAX_REASONABLE_STEP) {
                            increments += wrapped;
                            wraps++;
                        } else {
                            anomalies++;
                        }
                    } else {
                        // A jump such as 0 -> 254 is not interpreted as 254 resets.
                        // It is more likely a counter discontinuity, reconnect, or a
                        // sampled value on the opposite side of an 8-bit wrap.
                        anomalies++;
                    }
                }
                previous = Integer.valueOf(current);
            }
        }
        return new Result(increments, wraps, anomalies);
    }

    private static int normalize(double value) {
        int rounded = (int) Math.round(value);
        int normalized = rounded % MODULO;
        return normalized < 0 ? normalized + MODULO : normalized;
    }

    static final class Result {
        private final int increments;
        private final int wraps;
        private final int anomalies;

        Result(int increments, int wraps, int anomalies) {
            this.increments = increments;
            this.wraps = wraps;
            this.anomalies = anomalies;
        }

        int getIncrements() { return increments; }
        int getWraps() { return wraps; }
        int getAnomalies() { return anomalies; }
        boolean hasRepeatedResets() { return increments >= 2; }
        boolean hasDiscontinuity() { return anomalies > 0; }

        String shortText() {
            String text = Integer.toString(increments);
            if (wraps > 0) {
                text += " (" + wraps + " wrap" + (wraps == 1 ? "" : "s") + ")";
            }
            if (anomalies > 0) {
                text += " + " + anomalies + " discontinuity" + (anomalies == 1 ? "" : "ies");
            }
            return text;
        }
    }
}
