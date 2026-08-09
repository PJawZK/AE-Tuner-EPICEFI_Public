package se.anders.tunerstudio.aetuner.model;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.Arrays;
import java.util.List;

/** Immutable MAP-prediction analysis for one retained transient event. */
final class MapPredictionMetrics {
    final boolean predictionSeen;
    final boolean wallSeen;
    final boolean instantSeen;
    final double maxEffectiveGap;
    final double maxFallbackGap;
    final double activeSeconds;
    final double medianPredictionRpm;

    private MapPredictionMetrics(boolean predictionSeen,
                                 boolean wallSeen,
                                 boolean instantSeen,
                                 double maxEffectiveGap,
                                 double maxFallbackGap,
                                 double activeSeconds,
                                 double medianPredictionRpm) {
        this.predictionSeen = predictionSeen;
        this.wallSeen = wallSeen;
        this.instantSeen = instantSeen;
        this.maxEffectiveGap = maxEffectiveGap;
        this.maxFallbackGap = maxFallbackGap;
        this.activeSeconds = activeSeconds;
        this.medianPredictionRpm = medianPredictionRpm;
    }

    static MapPredictionMetrics build(List<LiveSample> samples) {
        boolean prediction = false;
        boolean wall = false;
        boolean instant = false;
        double maxEffectiveGap = 0.0;
        double maxFallbackGap = 0.0;
        double activeSeconds = 0.0;
        double[] rpmValues = new double[samples.size()];
        int rpmCount = 0;
        LiveSample previous = null;
        for (LiveSample sample : samples) {
            boolean activeNow = TransientSignals.mapPredictionVisible(sample);
            prediction = prediction || activeNow;
            wall = wall || TransientSignals.wallWettingVisible(sample);
            instant = instant || TransientSignals.instantFuelVisible(sample);
            double map = sample.get(ChannelRole.MAP);
            double effective = sample.get(ChannelRole.EFFECTIVE_MAP);
            double fallback = sample.get(ChannelRole.FALLBACK_MAP);
            if (Double.isFinite(map) && Double.isFinite(effective)) {
                maxEffectiveGap = Math.max(maxEffectiveGap, effective - map);
            }
            if (Double.isFinite(map) && Double.isFinite(fallback)) {
                maxFallbackGap = Math.max(maxFallbackGap, fallback - map);
            }
            if (previous != null && sample.bool(ChannelRole.MAP_PRED_ACTIVE)) {
                activeSeconds += Math.max(0.0, sample.getSeconds() - previous.getSeconds());
            }
            if (sample.bool(ChannelRole.MAP_PRED_ACTIVE)) {
                double rpm = sample.get(ChannelRole.RPM);
                if (Double.isFinite(rpm) && rpm >= 400.0) {
                    rpmValues[rpmCount++] = rpm;
                }
            }
            previous = sample;
        }
        double medianRpm = Double.NaN;
        if (rpmCount > 0) {
            Arrays.sort(rpmValues, 0, rpmCount);
            medianRpm = (rpmCount & 1) == 1
                    ? rpmValues[rpmCount / 2]
                    : (rpmValues[rpmCount / 2 - 1] + rpmValues[rpmCount / 2]) / 2.0;
        }
        return new MapPredictionMetrics(prediction, wall, instant, maxEffectiveGap,
                maxFallbackGap, activeSeconds, medianRpm);
    }
}
