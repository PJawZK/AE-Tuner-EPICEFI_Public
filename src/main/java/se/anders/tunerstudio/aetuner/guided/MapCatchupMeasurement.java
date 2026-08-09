package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.List;

/** Prediction-active MAP-gap anchor and catch-up timing for Guided Blend Duration. */
final class MapCatchupMeasurement {
    private LiveSample bestGapSample;
    private LiveSample measurementAnchor;
    private LiveSample catchSample;
    private double bestGap = Double.NaN;
    private double threshold = Double.NaN;

    void reset() {
        bestGapSample = null;
        measurementAnchor = null;
        catchSample = null;
        bestGap = Double.NaN;
        threshold = Double.NaN;
    }

    void observePredictionGap(LiveSample sample) {
        if (sample == null || !sample.bool(ChannelRole.MAP_PRED_ACTIVE)) return;
        double map = sample.get(ChannelRole.MAP);
        double fallback = sample.get(ChannelRole.FALLBACK_MAP);
        if (!Double.isFinite(map) || !Double.isFinite(fallback)) return;
        double gap = fallback - map;
        if (!Double.isFinite(bestGap) || gap >= bestGap) {
            bestGap = gap;
            bestGapSample = sample;
        }
    }

    void beginCatchup(List<LiveSample> attemptSamples, double mapCatchupSeconds) {
        measurementAnchor = bestGapSample;
        catchSample = null;
        threshold = measurementAnchor == null || !Double.isFinite(bestGap)
                ? Double.NaN
                : measurementAnchor.get(ChannelRole.MAP) + 0.90 * bestGap;
        if (measurementAnchor == null || !Double.isFinite(threshold)
                || attemptSamples == null) {
            return;
        }
        for (LiveSample candidate : attemptSamples) {
            if (candidate.getNanoTime() < measurementAnchor.getNanoTime()) continue;
            if (seconds(measurementAnchor.getNanoTime(), candidate.getNanoTime())
                    > mapCatchupSeconds) break;
            double map = candidate.get(ChannelRole.MAP);
            if (Double.isFinite(map) && map >= threshold) {
                catchSample = candidate;
                return;
            }
        }
    }

    void observeCatchup(LiveSample sample) {
        if (sample == null || catchSample != null || !Double.isFinite(threshold)) return;
        double map = sample.get(ChannelRole.MAP);
        if (Double.isFinite(map) && map >= threshold) {
            catchSample = sample;
        }
    }

    boolean timedOut(LiveSample sample, double mapCatchupSeconds) {
        return catchSample == null && measurementAnchor != null && sample != null
                && seconds(measurementAnchor.getNanoTime(), sample.getNanoTime())
                > mapCatchupSeconds;
    }

    LiveSample measurementAnchor() {
        return measurementAnchor;
    }

    LiveSample catchSample() {
        return catchSample;
    }

    double bestGap() {
        return bestGap;
    }

    double threshold() {
        return threshold;
    }

    double catchupDurationSeconds() {
        return measurementAnchor == null || catchSample == null
                ? Double.NaN
                : seconds(measurementAnchor.getNanoTime(), catchSample.getNanoTime());
    }

    private static double seconds(long earlier, long later) {
        return Math.max(0.0, (later - earlier) / 1000000000.0);
    }
}
