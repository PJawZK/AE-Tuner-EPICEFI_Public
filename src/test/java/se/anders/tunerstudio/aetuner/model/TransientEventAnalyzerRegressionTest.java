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

public final class TransientEventAnalyzerRegressionTest {
    private TransientEventAnalyzerRegressionTest() { }

    public static void main(String[] args) {
        analyzerFeedsTransientEventContract();
        System.out.println("TransientEventAnalyzerRegressionTest passed");
    }

    private static void analyzerFeedsTransientEventContract() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(sample(1000000000L, 350.0, 2.0, 98.0, 0.0, 0.2, 1.5,
                2.5, 0.0, 0.0, 0.0, 1.00, 1.00, 0.0, 0.0, false, false));
        samples.add(sample(1050000000L, 1800.0, 12.0, 60.0, 4.0, 2.0, 1.5,
                3.1, 0.45, 0.20, 0.0, 1.13, 1.00, 0.12, 0.0, true, false));
        samples.add(sample(1120000000L, 1950.0, 18.0, 72.0, 7.0, 2.6, 1.5,
                3.5, 0.30, -0.08, 0.10, 0.92, 1.00, 0.18, 0.03, true, true));
        samples.add(sample(1450000000L, 2050.0, 20.0, 82.0, 12.0, 1.2, 1.5,
                2.9, 0.00, -0.12, 0.00, 0.86, 1.00, 0.00, 0.00, false, false));

        TransientEventAnalyzer.Result result = TransientEventAnalyzer.analyze(samples);
        TransientEvent event = new TransientEvent(7, true, "synthetic", "equivalence", samples, true);

        require(event.isTpsAeFuelProved() == result.tpsAeFuelProved,
                "TPS-AE fuel proof was not sourced from analyzer result");
        require(event.hasMapPrediction() == result.predictionMetrics.predictionSeen,
                "Prediction visibility changed");
        require(event.hasWallWettingContribution() == result.predictionMetrics.wallSeen,
                "Wall-Wetting visibility changed");
        require(event.hasInstantFuelContribution() == result.predictionMetrics.instantSeen,
                "Instant-fuel visibility changed");
        require(bits(event.getMaxEffectiveMapGap()) == bits(result.predictionMetrics.maxEffectiveGap),
                "Effective MAP gap changed");
        require(bits(event.getMaxFallbackMapGap()) == bits(result.predictionMetrics.maxFallbackGap),
                "Fallback MAP gap changed");
        require(bits(event.getPredictionActiveSeconds()) == bits(result.predictionMetrics.activeSeconds),
                "Prediction-active time changed");
        require(bits(event.getMedianPredictionRpm()) == bits(result.predictionMetrics.medianPredictionRpm),
                "Prediction RPM median changed");
        require(event.getPredictionTriggerBurstCount() == result.predictionTriggerBurstCount,
                "Prediction trigger burst count changed");
        require(event.getPredictionResetMetrics().getIncrements()
                        == result.predictionResetMetrics.getIncrements(),
                "Prediction reset increments changed");
        require(event.getPredictionResetMetrics().getWraps()
                        == result.predictionResetMetrics.getWraps(),
                "Prediction reset wraps changed");
        require(event.getPredictionResetMetrics().getAnomalies()
                        == result.predictionResetMetrics.getAnomalies(),
                "Prediction reset discontinuities changed");
        require(bits(event.getMaxTriggerRatio()) == bits(result.maxTriggerRatio),
                "Trigger ratio changed");
        require(event.isTriggerNearMiss() == result.triggerNearMiss,
                "Trigger near-miss classification changed");
        require(event.isTinyTriggerCandidate() == result.tinyTriggerCandidate,
                "Tiny-trigger classification changed");
        require(bits(event.getTpsRise()) == bits(result.tpsRise), "TPS rise changed");
        require(bits(event.getMapRise()) == bits(result.mapRise), "MAP rise changed");
        require(bits(event.getMaxTps()) == bits(result.maxTps), "Maximum TPS changed");
        require(bits(event.getMaxTpsAeTo()) == bits(result.maxTpsAeTo), "TPS-to maximum changed");
        require(bits(event.getMaxLeanLambdaError()) == bits(result.maxLeanLambdaError),
                "Lean lambda error changed");
        require(bits(event.getMaxRichLambdaError()) == bits(result.maxRichLambdaError),
                "Rich lambda error changed");
        require(bits(event.getEarlyLeanLambdaError()) == bits(result.earlyLeanLambdaError),
                "Early lean lambda error changed");
        require(bits(event.getLateRichLambdaError()) == bits(result.lateRichLambdaError),
                "Late rich lambda error changed");
        require(event.isWallCorrectionAvailable() == result.wallCorrectionAvailable,
                "Wall-correction availability changed");
        require(event.isWallWettingPwAvailable() == result.wallWettingPwAvailable,
                "Wall-Wetting PW availability changed");
        require(event.isDfcoSeen() == result.dfcoSeen, "DFCO evidence changed");
    }

    private static LiveSample sample(long nano,
                                     double rpm,
                                     double tps,
                                     double map,
                                     double cycle,
                                     double smoothed,
                                     double threshold,
                                     double pw,
                                     double aeMs,
                                     double wallCorrection,
                                     double instantPw,
                                     double lambda,
                                     double targetLambda,
                                     double wallPw,
                                     double extraFuel,
                                     boolean predictionActive,
                                     boolean extraShot) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, Double.valueOf(rpm));
        values.put(ChannelRole.TPS, Double.valueOf(tps));
        values.put(ChannelRole.MAP, Double.valueOf(map));
        values.put(ChannelRole.FALLBACK_MAP, Double.valueOf(map + 20.0));
        values.put(ChannelRole.EFFECTIVE_MAP, Double.valueOf(map + (predictionActive ? 15.0 : 0.0)));
        values.put(ChannelRole.MAP_PRED_ACTIVE, Double.valueOf(predictionActive ? 1.0 : 0.0));
        values.put(ChannelRole.MAP_PRED_RESET_CNT, Double.valueOf(predictionActive ? 1.0 : 0.0));
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, Double.valueOf(smoothed));
        values.put(ChannelRole.ACCEL_THRESHOLD, Double.valueOf(threshold));
        values.put(ChannelRole.PW, Double.valueOf(pw));
        values.put(ChannelRole.AE_ADD_MS, Double.valueOf(aeMs));
        values.put(ChannelRole.EXTRA_FUEL, Double.valueOf(extraFuel));
        values.put(ChannelRole.TPS_AE_CYCLE_CNT, Double.valueOf(cycle));
        values.put(ChannelRole.TPS_AE_CYCLE_MULT, Double.valueOf(1.0 + cycle * 0.01));
        values.put(ChannelRole.TPS_TO, Double.valueOf(tps));
        values.put(ChannelRole.WALL_CORRECTION, Double.valueOf(wallCorrection));
        values.put(ChannelRole.WALL_WETTING_PW, Double.valueOf(wallPw));
        values.put(ChannelRole.INSTANT_PULSE_PW, Double.valueOf(instantPw));
        values.put(ChannelRole.AE_EXTRA_SHOT, Double.valueOf(extraShot ? 1.0 : 0.0));
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, Double.valueOf(aeMs > 0.0 ? 1.0 : 0.0));
        values.put(ChannelRole.DFCO, Double.valueOf(0.0));
        values.put(ChannelRole.FUEL_CUT, Double.valueOf(0.0));
        values.put(ChannelRole.LAMBDA, Double.valueOf(lambda));
        values.put(ChannelRole.TARGET_LAMBDA, Double.valueOf(targetLambda));
        values.put(ChannelRole.ENGINE_RUNNING, Double.valueOf(rpm >= 400.0 ? 1.0 : 0.0));
        return new LiveSample(nano, nano / 1000000000.0, values,
                Math.max(0.0, smoothed * 10.0), Math.max(0.0, (map - 50.0) * 2.0));
    }

    private static long bits(double value) {
        return Double.doubleToLongBits(value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
