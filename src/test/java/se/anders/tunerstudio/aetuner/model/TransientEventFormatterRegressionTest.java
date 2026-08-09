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

public final class TransientEventFormatterRegressionTest {
    private TransientEventFormatterRegressionTest() { }

    public static void main(String[] args) {
        formatterMatchesTpsCycleEvent();
        formatterMatchesMapPredictEvent();
        System.out.println("TransientEventFormatterRegressionTest passed");
    }

    private static void formatterMatchesTpsCycleEvent() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(sample(1000000000L, 1750.0, 8.0, 55.0, 0.35, 0.08,
                1.12, true, false, 2.0, 1.0));
        samples.add(sample(1120000000L, 1850.0, 18.0, 70.0, 0.30, 0.07,
                0.96, true, false, 7.0, 1.0));
        samples.add(sample(1450000000L, 1900.0, 20.0, 78.0, 0.10, 0.04,
                0.87, false, false, 12.0, 1.0));
        assertEquivalent(3, false, "TPS AE", "formatter 'quote' test", samples, false);
    }

    private static void formatterMatchesMapPredictEvent() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        samples.add(sample(2000000000L, 1800.0, 12.0, 54.0, 0.0, 0.0,
                1.00, false, false, 0.0, 0.0));
        samples.add(sample(2050000000L, 1900.0, 20.0, 60.0, 0.0, 0.18,
                0.89, false, true, 0.0, 1.0));
        samples.add(sample(2150000000L, 2050.0, 21.0, 68.0, 0.0, 0.15,
                0.86, false, true, 0.0, 1.0));
        samples.add(sample(2400000000L, 2080.0, 21.0, 82.0, 0.0, 0.0,
                0.94, false, false, 0.0, 1.0));
        assertEquivalent(9, true, "MAP Predict event", "formatter map test", samples, true);
    }

    private static void assertEquivalent(int index,
                                         boolean accepted,
                                         String eventClass,
                                         String reason,
                                         List<LiveSample> samples,
                                         boolean mapPredictWorkflow) {
        TransientEvent summary = new TransientEvent(index, accepted, eventClass, reason, samples,
                mapPredictWorkflow);
        TransientEventAnalyzer.Result analysis = TransientEventAnalyzer.analyze(samples);
        TransientEventAssessment assessment = TransientEventAssessment.build(
                mapPredictWorkflow, analysis);

        String display = TransientEventFormatter.displayText(index, accepted, eventClass, reason,
                samples, mapPredictWorkflow, analysis, assessment);
        require(summary.toDisplayText().equals(display),
                "Display text changed during formatter extraction\nEXPECTED:\n"
                        + summary.toDisplayText() + "\nACTUAL:\n" + display);
        require(summary.toCsvHeader().equals(TransientEventFormatter.csvHeader()),
                "CSV header changed during formatter extraction");
        List<String> expectedRows = summary.toCsvRows();
        List<String> actualRows = TransientEventFormatter.csvRows(index, accepted, eventClass,
                reason, samples, mapPredictWorkflow, analysis, assessment);
        require(expectedRows.equals(actualRows),
                "CSV rows changed during formatter extraction\nEXPECTED:\n"
                        + expectedRows + "\nACTUAL:\n" + actualRows);
    }

    private static LiveSample sample(long nano,
                                     double rpm,
                                     double tps,
                                     double map,
                                     double aeMs,
                                     double wallPw,
                                     double lambda,
                                     boolean aeActive,
                                     boolean predictionActive,
                                     double cycle,
                                     double resetCounter) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        for (ChannelRole role : ChannelRole.values()) {
            values.put(role, Double.valueOf(0.0));
        }
        values.put(ChannelRole.RPM, Double.valueOf(rpm));
        values.put(ChannelRole.TPS, Double.valueOf(tps));
        values.put(ChannelRole.MAP, Double.valueOf(map));
        values.put(ChannelRole.BARO, Double.valueOf(100.0));
        values.put(ChannelRole.FALLBACK_MAP, Double.valueOf(map + 22.0));
        values.put(ChannelRole.EFFECTIVE_MAP,
                Double.valueOf(map + (predictionActive ? 18.0 : 0.0)));
        values.put(ChannelRole.MAP_PRED_ACTIVE, Double.valueOf(predictionActive ? 1.0 : 0.0));
        values.put(ChannelRole.MAP_PRED_RESET_CNT, Double.valueOf(resetCounter));
        values.put(ChannelRole.MAP_PRED_EVENT_OVER, Double.valueOf(predictionActive ? 0.0 : 1.0));
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, Double.valueOf(aeActive ? 2.0 : 0.2));
        values.put(ChannelRole.ACCEL_THRESHOLD, Double.valueOf(1.5));
        values.put(ChannelRole.LAMBDA, Double.valueOf(lambda));
        values.put(ChannelRole.TARGET_LAMBDA, Double.valueOf(1.0));
        values.put(ChannelRole.AFR, Double.valueOf(lambda * 14.1));
        values.put(ChannelRole.TARGET_AFR, Double.valueOf(14.1));
        values.put(ChannelRole.PW, Double.valueOf(3.2));
        values.put(ChannelRole.INJ_DUTY, Double.valueOf(11.0));
        values.put(ChannelRole.IGNITION_TIMING, Double.valueOf(15.0));
        values.put(ChannelRole.BOOST_TARGET, Double.valueOf(0.0));
        values.put(ChannelRole.AE_ADD_MS, Double.valueOf(aeMs));
        values.put(ChannelRole.EXTRA_FUEL, Double.valueOf(0.0));
        values.put(ChannelRole.TPS_AE_CYCLE_CNT, Double.valueOf(cycle));
        values.put(ChannelRole.TPS_AE_CYCLE_MULT, Double.valueOf(1.0));
        values.put(ChannelRole.TPS_TO, Double.valueOf(tps));
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, Double.valueOf(aeActive ? 1.0 : 0.0));
        values.put(ChannelRole.AE_EXTRA_SHOT, Double.valueOf(0.0));
        values.put(ChannelRole.WALL_CORRECTION, Double.valueOf(wallPw == 0.0 ? 0.0 : -0.03));
        values.put(ChannelRole.WALL_WETTING_PW, Double.valueOf(wallPw));
        values.put(ChannelRole.INSTANT_PULSE_PW, Double.valueOf(0.0));
        values.put(ChannelRole.INSTANT_PULSE_CNT, Double.valueOf(0.0));
        values.put(ChannelRole.EXTRA_SHOT_TIMER, Double.valueOf(0.0));
        values.put(ChannelRole.DFCO, Double.valueOf(0.0));
        values.put(ChannelRole.FUEL_CUT, Double.valueOf(0.0));
        values.put(ChannelRole.TOTAL_SPARK_CUT, Double.valueOf(0.0));
        values.put(ChannelRole.ENGINE_RUNNING, Double.valueOf(1.0));
        values.put(ChannelRole.IGNITION_ON, Double.valueOf(1.0));
        values.put(ChannelRole.MAIN_RELAY_HAS_IGN, Double.valueOf(1.0));
        return new LiveSample(nano, nano / 1000000000.0, values,
                aeActive ? 22.0 : 1.0, Math.max(0.0, map - 50.0));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
