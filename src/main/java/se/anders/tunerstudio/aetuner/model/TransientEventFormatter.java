package se.anders.tunerstudio.aetuner.model;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/** Human-readable and CSV presentation for analyzed passive transient events. */
final class TransientEventFormatter {
    private static final DecimalFormat F1 = new DecimalFormat("0.0");
    private static final DecimalFormat F2 = new DecimalFormat("0.00");
    private static final DecimalFormat F3 = new DecimalFormat("0.000");

    private TransientEventFormatter() { }

    static String displayText(int index,
                              boolean accepted,
                              String eventClass,
                              String reason,
                              List<LiveSample> samples,
                              boolean mapPredictWorkflow,
                              TransientEventAnalyzer.Result analysis,
                              TransientEventAssessment assessment) {
        String status = accepted ? eventClass : "Rejected / " + eventClass;
        return status + " event #" + index
                + " | " + F1.format(durationSeconds(samples, analysis)) + " s"
                + " | TPS " + fmt1(analysis.startTps) + " -> " + fmt1(analysis.endTps) + " %"
                + " | MAP " + fmt1(analysis.startMap) + " -> " + fmt1(analysis.endMap) + " kPa"
                + " | RPM med " + fmt1(analysis.medianRpm)
                + " | max TPSdot " + fmt1(analysis.maxTpsDot) + " %/s"
                + " | smoothedDeltaTps " + fmt1(analysis.maxSmoothedDeltaTps)
                + " | AccelThreshold " + fmt1(analysis.maxAccelThreshold) + "\n"
                + "Trigger check: smoothedDeltaTps/AccelThreshold "
                + fmt1(analysis.maxTriggerRatio * 100.0) + "%"
                + " | margin " + fmt2(analysis.triggerMargin)
                + " | TPS rise " + fmt1(analysis.tpsRise) + "%"
                + " | MAP rise " + fmt1(analysis.mapRise) + " kPa\n"
                + mapPredictVerdict(samples, analysis) + "\n"
                + (mapPredictWorkflow
                    ? "Low-RPM review: " + assessment.lowRpmMapPredictReview + "\n" : "")
                + assessment.fuelPathVerdict + "\n"
                + "Peaks: Fuel: TPS AE add fuel ms " + F3.format(analysis.maxAeMs)
                + " | Fuel: TPS extraFuel " + F3.format(analysis.maxExtraFuel)
                + " | tpsAeCycleMult " + F3.format(analysis.maxCycleMult)
                + " | Fuel: Last inj pulse width " + F3.format(analysis.maxPw) + " ms"
                + " | fuel bursts " + analysis.fuelBurstCount + "\n"
                + "States: Fuel: TPS AE Active "
                + (analysis.tpsAeStateSeen ? "seen" : "not seen")
                + " | Fuel: TPSAE ExtraShot " + (analysis.extraShotSeen ? "seen" : "not seen")
                + " | dfcoActive " + (analysis.dfcoSeen ? "seen" : "not seen") + "\n"
                + "Other transient paths: Fuel: wall correction "
                + availableRange(analysis.wallCorrectionAvailable,
                        analysis.minWallCorrection, analysis.maxWallCorrection)
                + " | fuel wallwetting injection time "
                + availableRange(analysis.wallWettingPwAvailable,
                        analysis.minWallWettingPw, analysis.maxWallWettingPw) + " ms\n"
                + "Transient contribution: " + assessment.transientMixClass
                + " | Wall Wetting/TPS AE ratio "
                + ratioText(assessment.wallWettingToTpsAeRatio) + "\n"
                + "Lambda error: lean max +" + F2.format(analysis.maxLeanLambdaError)
                + " λ, rich max " + F2.format(analysis.maxRichLambdaError)
                + " λ | lean area " + F2.format(analysis.leanArea)
                + " | rich area " + F2.format(analysis.richArea) + " | " + reason + "\n"
                + (mapPredictWorkflow ? "Workflow guidance: " : "AE fuel table guidance: ")
                + assessment.aeFuelTableGuidance;
    }

    private static String mapPredictVerdict(List<LiveSample> samples,
                                            TransientEventAnalyzer.Result analysis) {
        boolean active = false;
        double maxGap = 0.0;
        double maxFallbackGap = 0.0;
        double activeSeconds = 0.0;
        LiveSample previous = null;
        for (LiveSample sample : samples) {
            boolean activeNow = sample.bool(ChannelRole.MAP_PRED_ACTIVE);
            active = active || activeNow;
            double map = sample.get(ChannelRole.MAP);
            double effective = sample.get(ChannelRole.EFFECTIVE_MAP);
            double fallback = sample.get(ChannelRole.FALLBACK_MAP);
            if (Double.isFinite(map) && Double.isFinite(effective)) {
                maxGap = Math.max(maxGap, effective - map);
            }
            if (Double.isFinite(map) && Double.isFinite(fallback)) {
                maxFallbackGap = Math.max(maxFallbackGap, fallback - map);
            }
            if (previous != null && activeNow) {
                activeSeconds += Math.max(0.0, sample.getSeconds() - previous.getSeconds());
            }
            previous = sample;
        }
        return "MAP Predict: " + (active ? "active" : "not active")
                + " | active " + F2.format(activeSeconds) + " s"
                + " | max effectiveMap-MAP gap " + F2.format(maxGap) + " kPa"
                + " | max fallbackMap-MAP gap " + F2.format(maxFallbackGap) + " kPa"
                + " | trigger bursts " + analysis.predictionTriggerBurstCount
                + " | timer counter increments " + analysis.predictionResetMetrics.shortText();
    }

    static String csvHeader() {
        return quote("event_index") + "," + quote("event_status") + "," + quote("event_class") + "," + quote("event_reason")
                + "," + quote("event_duration_seconds")
                + "," + quote("event_sample_count")
                + "," + quote("event_start_seconds")
                + "," + quote("event_end_seconds")
                + "," + quote("tps_ae_fuel_proved")
                + "," + quote("tps_ae_state_seen")
                + "," + quote("other_transient_path_seen")
                + "," + quote("fuel_burst_count")
                + "," + quote("event_trigger_ratio_pct")
                + "," + quote("event_trigger_margin")
                + "," + quote("event_tps_rise")
                + "," + quote("event_map_rise")
                + "," + quote("event_ae_fuel_table_guidance")
                + "," + quote("event_early_lean_lambda_error")
                + "," + quote("event_early_rich_lambda_error")
                + "," + quote("event_mid_lean_lambda_error")
                + "," + quote("event_mid_rich_lambda_error")
                + "," + quote("event_late_lean_lambda_error")
                + "," + quote("event_late_rich_lambda_error")
                + "," + quote("event_transient_mix_class")
                + "," + quote("event_wall_wetting_to_tps_ae_ratio_pct")
                + "," + quote("event_wall_correction_channel_available")
                + "," + quote("event_wall_wetting_pw_channel_available")
                + "," + quote("event_workflow")
                + "," + quote("event_map_predict_seen")
                + "," + quote("event_prediction_active_seconds")
                + "," + quote("event_max_effective_map_gap_kpa")
                + "," + quote("event_max_fallback_map_gap_kpa")
                + "," + quote("event_prediction_timer_resets")
                + "," + quote("event_prediction_trigger_bursts")
                + "," + quote("event_prediction_counter_wraps")
                + "," + quote("event_prediction_counter_discontinuities")
                + "," + quote("event_low_rpm_map_predict_review")
                + "," + quote("sample_seconds")
                + "," + quote("RPM")
                + "," + quote("TPS")
                + "," + quote("TPSdot")
                + "," + quote("smoothedDeltaTps")
                + "," + quote("AccelThreshold")
                + "," + quote("MAP")
                + "," + quote("baroPressure")
                + "," + quote("fallbackMap")
                + "," + quote("effectiveMap")
                + "," + quote("isMapPredictionActive")
                + "," + quote("predTimerResetCnt")
                + "," + quote("mapPredEventOver")
                + "," + quote("MAPdot")
                + "," + quote("Lambda")
                + "," + quote("Target lambda")
                + "," + quote("AFR")
                + "," + quote("Target AFR")
                + "," + quote("Fuel: Last inj pulse width")
                + "," + quote("Injector duty cycle")
                + "," + quote("Timing: ignition")
                + "," + quote("Boost: Target")
                + "," + quote("Fuel: TPS AE add fuel ms")
                + "," + quote("Fuel: TPS extraFuel")
                + "," + quote("Engine cycles AE duration")
                + "," + quote("tpsAeCycleMult")
                + "," + quote("Fuel: TPS AE Active")
                + "," + quote("Fuel: TPSAE ExtraShot")
                + "," + quote("Fuel: wall correction")
                + "," + quote("fuel wallwetting injection time")
                + "," + quote("aeInstantPulsePw")
                + "," + quote("aeInstantPulseCnt")
                + "," + quote("m_tpsExtraShotTimer")
                + "," + quote("dfcoActive")
                + "," + quote("Total fuel cut")
                + "," + quote("totalSparkCut")
                + "," + quote("Ign: Cut Code")
                + "," + quote("Fuel: Cut Code")
                + "," + quote("stopEngineCode")
                + "," + quote("ignitionFault")
                + "," + quote("injectorFault")
                + "," + quote("Error: Trigger")
                + "," + quote("Trigger Error Counter")
                + "," + quote("Ignition: overDwellNotScheduled")
                + "," + quote("Ignition: overcharge warnings")
                + "," + quote("Ignition: undecharge warnings")
                + "," + quote("Ignition: sparkOutOfOrder")
                + "," + quote("Fuel pressure _high")
                + "," + quote("Fuel pressure _low")
                + "," + quote("STFT: Bank 1")
                + "," + quote("CLT")
                + "," + quote("MAT")
                + "," + quote("Batt V");
    }

    static List<String> csvRows(int index,
                                boolean accepted,
                                String eventClass,
                                String reason,
                                List<LiveSample> samples,
                                boolean mapPredictWorkflow,
                                TransientEventAnalyzer.Result analysis,
                                TransientEventAssessment assessment) {
        List<String> rows = new ArrayList<String>();
        CounterMath.Result resetMetrics = analysis.predictionResetMetrics;
        boolean predictionSeen = analysis.predictionMetrics.predictionSeen;
        double predictionSeconds = analysis.predictionMetrics.activeSeconds;
        double effectiveGap = analysis.predictionMetrics.maxEffectiveGap;
        double fallbackGap = analysis.predictionMetrics.maxFallbackGap;
        String lowRpmReview = assessment.lowRpmMapPredictReview;
        for (LiveSample sample : samples) {
            rows.add(num(index)
                    + "," + quote(accepted ? "accepted" : "rejected")
                    + "," + quote(eventClass)
                    + "," + quote(reason)
                    + "," + num(durationSeconds(samples, analysis))
                    + "," + num(samples.size())
                    + "," + num(analysis.startSeconds)
                    + "," + num(analysis.endSeconds)
                    + "," + (analysis.tpsAeFuelProved ? "1" : "0")
                    + "," + (analysis.tpsAeStateSeen ? "1" : "0")
                    + "," + (analysis.otherTransientPathSeen ? "1" : "0")
                    + "," + num(analysis.fuelBurstCount)
                    + "," + num(analysis.maxTriggerRatio * 100.0)
                    + "," + num(analysis.triggerMargin)
                    + "," + num(analysis.tpsRise)
                    + "," + num(analysis.mapRise)
                    + "," + quote(assessment.aeFuelTableGuidance)
                    + "," + num(analysis.earlyLeanLambdaError)
                    + "," + num(analysis.earlyRichLambdaError)
                    + "," + num(analysis.midLeanLambdaError)
                    + "," + num(analysis.midRichLambdaError)
                    + "," + num(analysis.lateLeanLambdaError)
                    + "," + num(analysis.lateRichLambdaError)
                    + "," + quote(assessment.transientMixClass)
                    + "," + num(Double.isFinite(assessment.wallWettingToTpsAeRatio)
                            ? assessment.wallWettingToTpsAeRatio * 100.0 : Double.NaN)
                    + "," + (analysis.wallCorrectionAvailable ? "1" : "0")
                    + "," + (analysis.wallWettingPwAvailable ? "1" : "0")
                    + "," + quote(mapPredictWorkflow
                            ? "MAP Predict workflow" : "TPS cycle AE workflow")
                    + "," + (predictionSeen ? "1" : "0")
                    + "," + num(predictionSeconds)
                    + "," + num(effectiveGap)
                    + "," + num(fallbackGap)
                    + "," + num(resetMetrics.getIncrements())
                    + "," + num(analysis.predictionTriggerBurstCount)
                    + "," + num(resetMetrics.getWraps())
                    + "," + num(resetMetrics.getAnomalies())
                    + "," + quote(lowRpmReview)
                    + "," + num(sample.getSeconds())
                    + "," + num(sample.get(ChannelRole.RPM))
                    + "," + num(sample.get(ChannelRole.TPS))
                    + "," + num(sample.getTpsDot())
                    + "," + num(sample.get(ChannelRole.SMOOTHED_DELTA_TPS))
                    + "," + num(sample.get(ChannelRole.ACCEL_THRESHOLD))
                    + "," + num(sample.get(ChannelRole.MAP))
                    + "," + num(sample.get(ChannelRole.BARO))
                    + "," + num(sample.get(ChannelRole.FALLBACK_MAP))
                    + "," + num(sample.get(ChannelRole.EFFECTIVE_MAP))
                    + "," + (sample.bool(ChannelRole.MAP_PRED_ACTIVE) ? "1" : "0")
                    + "," + num(sample.get(ChannelRole.MAP_PRED_RESET_CNT))
                    + "," + (sample.bool(ChannelRole.MAP_PRED_EVENT_OVER) ? "1" : "0")
                    + "," + num(sample.getMapDot())
                    + "," + num(sample.get(ChannelRole.LAMBDA))
                    + "," + num(sample.get(ChannelRole.TARGET_LAMBDA))
                    + "," + num(sample.get(ChannelRole.AFR))
                    + "," + num(sample.get(ChannelRole.TARGET_AFR))
                    + "," + num(sample.get(ChannelRole.PW))
                    + "," + num(sample.get(ChannelRole.INJ_DUTY))
                    + "," + num(sample.get(ChannelRole.IGNITION_TIMING))
                    + "," + num(sample.get(ChannelRole.BOOST_TARGET))
                    + "," + num(sample.get(ChannelRole.AE_ADD_MS))
                    + "," + num(sample.get(ChannelRole.EXTRA_FUEL))
                    + "," + num(sample.get(ChannelRole.TPS_AE_CYCLE_CNT))
                    + "," + num(sample.get(ChannelRole.TPS_AE_CYCLE_MULT))
                    + "," + (sample.bool(ChannelRole.AE_ABOVE_THRESHOLD) ? "1" : "0")
                    + "," + (sample.bool(ChannelRole.AE_EXTRA_SHOT) ? "1" : "0")
                    + "," + num(sample.get(ChannelRole.WALL_CORRECTION))
                    + "," + num(sample.get(ChannelRole.WALL_WETTING_PW))
                    + "," + num(sample.get(ChannelRole.INSTANT_PULSE_PW))
                    + "," + num(sample.get(ChannelRole.INSTANT_PULSE_CNT))
                    + "," + num(sample.get(ChannelRole.EXTRA_SHOT_TIMER))
                    + "," + (sample.bool(ChannelRole.DFCO) ? "1" : "0")
                    + "," + (sample.bool(ChannelRole.FUEL_CUT) ? "1" : "0")
                    + "," + num(sample.get(ChannelRole.TOTAL_SPARK_CUT))
                    + "," + num(sample.get(ChannelRole.IGN_CUT_CODE))
                    + "," + num(sample.get(ChannelRole.FUEL_CUT_CODE))
                    + "," + num(sample.get(ChannelRole.STOP_ENGINE_CODE))
                    + "," + (sample.bool(ChannelRole.IGNITION_FAULT) ? "1" : "0")
                    + "," + (sample.bool(ChannelRole.INJECTOR_FAULT) ? "1" : "0")
                    + "," + (sample.bool(ChannelRole.TRIGGER_ERROR) ? "1" : "0")
                    + "," + num(sample.get(ChannelRole.TRIGGER_ERROR_COUNT))
                    + "," + num(sample.get(ChannelRole.IGN_OVERDWELL))
                    + "," + num(sample.get(ChannelRole.IGN_OVERCHARGE_WARNINGS))
                    + "," + num(sample.get(ChannelRole.IGN_UNDERCHARGE_WARNINGS))
                    + "," + num(sample.get(ChannelRole.IGN_SPARK_OUT_OF_ORDER))
                    + "," + num(sample.get(ChannelRole.FUEL_PRESSURE_HIGH))
                    + "," + num(sample.get(ChannelRole.FUEL_PRESSURE_LOW))
                    + "," + num(sample.get(ChannelRole.STFT1))
                    + "," + num(sample.get(ChannelRole.COOLANT))
                    + "," + num(sample.get(ChannelRole.IAT))
                    + "," + num(sample.get(ChannelRole.BATTERY)));
        }
        return rows;
    }

    private static String availableRange(boolean available, double min, double max) {
        return available ? F3.format(min) + ".." + F3.format(max) : "channel unavailable";
    }

    private static String ratioText(double ratio) {
        return Double.isFinite(ratio) ? F1.format(ratio * 100.0) + "%" : "n/a";
    }

    private static double durationSeconds(List<LiveSample> samples,
                                          TransientEventAnalyzer.Result analysis) {
        if (!Double.isFinite(analysis.startSeconds) || !Double.isFinite(analysis.endSeconds)) {
            return Double.NaN;
        }
        if (samples.size() >= 2) {
            long firstNano = samples.get(0).getNanoTime();
            long lastNano = samples.get(samples.size() - 1).getNanoTime();
            if (firstNano > 0L && lastNano >= firstNano) {
                return (lastNano - firstNano) / 1000000000.0;
            }
        }
        return Math.max(0.0, analysis.endSeconds - analysis.startSeconds);
    }

    private static String fmt1(double value) {
        return Double.isFinite(value) ? F1.format(value) : "n/a";
    }

    private static String fmt2(double value) {
        return Double.isFinite(value) ? F2.format(value) : "n/a";
    }

    private static String num(double value) {
        return Double.isFinite(value) ? Double.toString(value) : "";
    }

    private static String num(int value) {
        return Integer.toString(value);
    }

    private static String quote(String value) {
        String safe = value == null ? "" : value.replace("\"", "'");
        return "\"" + safe + "\"";
    }
}
