package se.anders.tunerstudio.aetuner.model;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class MapPredictionMetricsRegressionTest {
    private MapPredictionMetricsRegressionTest() { }

    public static void main(String[] args) {
        extractedMetricsMatchEventSummaryContract();
        System.out.println("MapPredictionMetricsRegressionTest passed");
    }

    private static void extractedMetricsMatchEventSummaryContract() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(sample(1000000000L, 1800.0, 55.0, 55.0, 70.0, false, 0.0, 0.0));
        samples.add(sample(1050000000L, 1900.0, 58.0, 80.0, 82.0, true, 0.25, 0.0));
        samples.add(sample(1100000000L, 2100.0, 63.0, 88.0, 90.0, true, 0.20, 0.12));
        samples.add(sample(1180000000L, 2200.0, 72.0, 86.0, 87.0, false, 0.0, 0.0));

        MapPredictionMetrics metrics = MapPredictionMetrics.build(samples);
        TransientEvent summary = new TransientEvent(1, true, "MAP Predict event", "synthetic", samples, true);

        require(metrics.predictionSeen == summary.hasMapPrediction(), "Prediction-visible result changed");
        require(metrics.wallSeen == summary.hasWallWettingContribution(), "Wall-Wetting result changed");
        require(metrics.instantSeen == summary.hasInstantFuelContribution(), "Instant-fuel result changed");
        require(bits(metrics.maxEffectiveGap) == bits(summary.getMaxEffectiveMapGap()),
                "Effective-MAP gap changed");
        require(bits(metrics.maxFallbackGap) == bits(summary.getMaxFallbackMapGap()),
                "Fallback-MAP gap changed");
        require(bits(metrics.activeSeconds) == bits(summary.getPredictionActiveSeconds()),
                "Prediction-active duration changed");
        require(bits(metrics.medianPredictionRpm) == bits(summary.getMedianPredictionRpm()),
                "Prediction RPM median changed");
    }

    private static LiveSample sample(long nano,
                                     double rpm,
                                     double map,
                                     double effective,
                                     double fallback,
                                     boolean predictionActive,
                                     double wallPw,
                                     double instantPw) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, Double.valueOf(rpm));
        values.put(ChannelRole.TPS, Double.valueOf(20.0));
        values.put(ChannelRole.MAP, Double.valueOf(map));
        values.put(ChannelRole.EFFECTIVE_MAP, Double.valueOf(effective));
        values.put(ChannelRole.FALLBACK_MAP, Double.valueOf(fallback));
        values.put(ChannelRole.MAP_PRED_ACTIVE, Double.valueOf(predictionActive ? 1.0 : 0.0));
        values.put(ChannelRole.WALL_WETTING_PW, Double.valueOf(wallPw));
        values.put(ChannelRole.INSTANT_PULSE_PW, Double.valueOf(instantPw));
        values.put(ChannelRole.MAP_PRED_RESET_CNT, Double.valueOf(0.0));
        values.put(ChannelRole.ENGINE_RUNNING, Double.valueOf(1.0));
        values.put(ChannelRole.IGNITION_ON, Double.valueOf(1.0));
        values.put(ChannelRole.MAIN_RELAY_HAS_IGN, Double.valueOf(1.0));
        return new LiveSample(nano, nano / 1000000000.0, values, 0.0, 0.0);
    }

    private static long bits(double value) {
        return Double.doubleToLongBits(value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
