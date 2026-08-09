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

public final class TransientEventAssessmentRegressionTest {
    private TransientEventAssessmentRegressionTest() { }

    public static void main(String[] args) {
        tpsCycleAssessmentFeedsTransientEvent();
        mapPredictAssessmentFeedsTransientEvent();
        System.out.println("TransientEventAssessmentRegressionTest passed");
    }

    private static void tpsCycleAssessmentFeedsTransientEvent() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(sample(1000000000L, 1800.0, 10.0, 55.0, 2.2, 1.5,
                0.45, 0.10, 0.0, 1.16, 1.0, false, false, 2.0));
        samples.add(sample(1120000000L, 1880.0, 18.0, 70.0, 2.0, 1.5,
                0.40, 0.08, 0.0, 0.98, 1.0, false, false, 7.0));
        samples.add(sample(1450000000L, 1900.0, 20.0, 78.0, 0.5, 1.5,
                0.20, 0.05, 0.0, 0.86, 1.0, false, false, 12.0));
        assertAssessmentFeedsEvent(samples, false);
    }

    private static void mapPredictAssessmentFeedsTransientEvent() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(sample(2000000000L, 1750.0, 12.0, 55.0, 2.1, 1.5,
                0.0, 0.0, 0.0, 1.00, 1.0, false, false, 0.0));
        samples.add(sample(2050000000L, 1850.0, 20.0, 60.0, 2.4, 1.5,
                0.0, 0.22, 0.0, 0.88, 1.0, true, false, 0.0));
        samples.add(sample(2150000000L, 1950.0, 21.0, 68.0, 1.0, 1.5,
                0.0, 0.18, 0.08, 0.86, 1.0, true, false, 0.0));
        samples.add(sample(2350000000L, 1980.0, 21.0, 82.0, 0.0, 1.5,
                0.0, 0.00, 0.00, 0.94, 1.0, false, false, 0.0));
        assertAssessmentFeedsEvent(samples, true);
    }

    private static void assertAssessmentFeedsEvent(List<LiveSample> samples,
                                                   boolean mapPredictWorkflow) {
        TransientEventAnalyzer.Result analysis = TransientEventAnalyzer.analyze(samples);
        TransientEventAssessment assessment = TransientEventAssessment.build(
                mapPredictWorkflow, analysis);
        TransientEvent event = new TransientEvent(4, true, "synthetic", "assessment", samples,
                mapPredictWorkflow);

        require(bits(event.getWallWettingToTpsAeRatio())
                        == bits(assessment.wallWettingToTpsAeRatio),
                "Wall-Wetting/TPS-AE ratio changed");
        require(event.getTransientMixClass().equals(assessment.transientMixClass),
                "Transient mix classification changed");
        require(event.getAeFuelTableGuidance().equals(assessment.aeFuelTableGuidance),
                "Tuning guidance changed");
        require(event.toDisplayText().contains(assessment.fuelPathVerdict),
                "Display no longer consumes assessment fuel-path verdict");
        if (mapPredictWorkflow) {
            require(event.toDisplayText().contains(
                            "Low-RPM review: " + assessment.lowRpmMapPredictReview),
                    "Display no longer consumes low-RPM assessment");
        }
        require(bits(event.multiplierSuggestionWeight(null))
                        == bits(assessment.multiplierSuggestionWeight(null)),
                "Null-project proposal weight changed");
        require(bits(event.multiplierSuggestionWeight(snapshot(false)))
                        == bits(assessment.multiplierSuggestionWeight(snapshot(false))),
                "Isolated TPS-AE proposal weight changed");
        require(bits(event.multiplierSuggestionWeight(snapshot(true)))
                        == bits(assessment.multiplierSuggestionWeight(snapshot(true))),
                "Wall-Wetting project proposal weight changed");
    }

    private static AeProjectSnapshot snapshot(boolean wallWettingEnabled) {
        return new AeProjectSnapshot("assessment-test",
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0], 0.0, 0.0,
                new double[0], new double[0],
                true, wallWettingEnabled, "Synthetic wall", false, false,
                false, false, new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0]);
    }

    private static LiveSample sample(long nano,
                                     double rpm,
                                     double tps,
                                     double map,
                                     double smoothed,
                                     double threshold,
                                     double aeMs,
                                     double wallPw,
                                     double instantPw,
                                     double lambda,
                                     double targetLambda,
                                     boolean predictionActive,
                                     boolean extraShot,
                                     double cycle) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, Double.valueOf(rpm));
        values.put(ChannelRole.TPS, Double.valueOf(tps));
        values.put(ChannelRole.MAP, Double.valueOf(map));
        values.put(ChannelRole.FALLBACK_MAP, Double.valueOf(map + 24.0));
        values.put(ChannelRole.EFFECTIVE_MAP,
                Double.valueOf(map + (predictionActive ? 20.0 : 0.0)));
        values.put(ChannelRole.MAP_PRED_ACTIVE, Double.valueOf(predictionActive ? 1.0 : 0.0));
        values.put(ChannelRole.MAP_PRED_RESET_CNT, Double.valueOf(predictionActive ? 1.0 : 0.0));
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, Double.valueOf(smoothed));
        values.put(ChannelRole.ACCEL_THRESHOLD, Double.valueOf(threshold));
        values.put(ChannelRole.AE_ADD_MS, Double.valueOf(aeMs));
        values.put(ChannelRole.EXTRA_FUEL, Double.valueOf(0.0));
        values.put(ChannelRole.TPS_AE_CYCLE_CNT, Double.valueOf(cycle));
        values.put(ChannelRole.TPS_AE_CYCLE_MULT, Double.valueOf(1.0));
        values.put(ChannelRole.TPS_TO, Double.valueOf(tps));
        values.put(ChannelRole.WALL_CORRECTION, Double.valueOf(0.0));
        values.put(ChannelRole.WALL_WETTING_PW, Double.valueOf(wallPw));
        values.put(ChannelRole.INSTANT_PULSE_PW, Double.valueOf(instantPw));
        values.put(ChannelRole.AE_EXTRA_SHOT, Double.valueOf(extraShot ? 1.0 : 0.0));
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, Double.valueOf(aeMs > 0.0 ? 1.0 : 0.0));
        values.put(ChannelRole.DFCO, Double.valueOf(0.0));
        values.put(ChannelRole.FUEL_CUT, Double.valueOf(0.0));
        values.put(ChannelRole.LAMBDA, Double.valueOf(lambda));
        values.put(ChannelRole.TARGET_LAMBDA, Double.valueOf(targetLambda));
        values.put(ChannelRole.PW, Double.valueOf(3.0));
        values.put(ChannelRole.ENGINE_RUNNING, Double.valueOf(1.0));
        return new LiveSample(nano, nano / 1000000000.0, values,
                smoothed * 10.0, Math.max(0.0, (map - 50.0) * 2.0));
    }

    private static long bits(double value) {
        return Double.doubleToLongBits(value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
