package se.anders.tunerstudio.aetuner;

import java.util.List;

/** Counts distinct TPS-change detector bursts inside a captured event. */
final class PredictionBurstMath {
    private static final double MERGE_GAP_SECONDS = 0.08;

    private PredictionBurstMath() { }

    static int countTriggerBursts(List<LiveSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return 0;
        }
        int bursts = 0;
        boolean inBurst = false;
        LiveSample lastActive = null;
        for (LiveSample sample : samples) {
            boolean active = aboveThreshold(sample);
            if (active) {
                if (!inBurst) {
                    if (lastActive == null || secondsBetween(sample, lastActive) > MERGE_GAP_SECONDS) {
                        bursts++;
                    }
                    inBurst = true;
                }
                lastActive = sample;
            } else if (inBurst) {
                inBurst = false;
            }
        }
        return bursts;
    }

    private static boolean aboveThreshold(LiveSample sample) {
        if (sample.bool(ChannelRole.AE_ABOVE_THRESHOLD)) {
            return true;
        }
        double smoothed = sample.get(ChannelRole.SMOOTHED_DELTA_TPS);
        double threshold = sample.get(ChannelRole.ACCEL_THRESHOLD);
        return Double.isFinite(smoothed) && Double.isFinite(threshold)
                && threshold > 0.0 && smoothed >= threshold;
    }

    private static double secondsBetween(LiveSample newer, LiveSample older) {
        long newerNano = newer.getNanoTime();
        long olderNano = older.getNanoTime();
        if (newerNano > 0L && olderNano > 0L) {
            return Math.max(0.0, (newerNano - olderNano) / 1000000000.0);
        }
        return Math.max(0.0, newer.getSeconds() - older.getSeconds());
    }
}
