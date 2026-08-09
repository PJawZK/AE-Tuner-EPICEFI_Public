package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.text.DecimalFormat;
import java.util.EnumMap;
import java.util.Set;

/** Pure text rendering for the Technical details tab. */
final class TechnicalDetailsRenderer {
    private static final DecimalFormat F2 = new DecimalFormat("0.00");
    private static final DecimalFormat F3 = new DecimalFormat("0.000");

    private TechnicalDetailsRenderer() { }

    static String sessionMode(AeProjectSnapshot snapshot, Set<ChannelRole> resolvedRoles) {
        if (snapshot == null) {
            return "Session mode: project data not available yet. Press Read AE project data.";
        }
        boolean fallback = resolvedRoles.contains(ChannelRole.FALLBACK_MAP);
        boolean effective = resolvedRoles.contains(ChannelRole.EFFECTIVE_MAP);
        boolean active = resolvedRoles.contains(ChannelRole.MAP_PRED_ACTIVE);
        boolean reset = resolvedRoles.contains(ChannelRole.MAP_PRED_RESET_CNT);
        boolean wallPw = resolvedRoles.contains(ChannelRole.WALL_WETTING_PW);

        StringBuilder text = new StringBuilder("Session mode: ");
        text.append(snapshot.expectedSessionModeText()).append(". ");
        if (snapshot.isMapPredictWorkflow()) {
            text.append("Current tuning stage: MAP Predict. TPS cycle multiplier-table suggestions are disabled. ");
            text.append("Prediction channels: fallbackMap ").append(fallback ? "OK" : "MISSING")
                    .append(", effectiveMap ").append(effective ? "OK" : "MISSING")
                    .append(", isMapPredictionActive ").append(active ? "OK" : "MISSING")
                    .append(", predTimerResetCnt ").append(reset ? "OK" : "MISSING").append(". ");
            if (snapshot.isWallWettingEnabled()) {
                text.append("Wall Wetting is enabled; fuel wallwetting injection time ")
                        .append(wallPw ? "is available for later contribution analysis" : "is MISSING").append(". ");
            }
            if (snapshot.isExtraShotEnabled()) {
                text.append("Instant Fuel Pulse is ON; for the planned staged workflow, tune MAP Predict + Wall Wetting before evaluating it. ");
            } else {
                text.append("Instant Fuel Pulse is OFF, which matches the MAP Predict-first tuning stage. ");
            }
        } else {
            text.append("Legacy TPS cycle-AE analysis remains available. ");
        }
        if (snapshot.isDynamicThresholdEnabled()) {
            text.append("Dynamic TPS AE threshold is ON");
            if (snapshot.isDynamicThresholdAverageStatic()) {
                text.append(" and averaged with TPS AE Rate of change vs RPM");
            }
            text.append(".");
        }
        return text.toString();
    }

    static String fuelPathStatus(boolean mapPredictWorkflow,
                                 EnumMap<ChannelRole, Double> values) {
        if (mapPredictWorkflow) {
            double active = value(values, ChannelRole.MAP_PRED_ACTIVE);
            double realMap = value(values, ChannelRole.MAP);
            double fallback = value(values, ChannelRole.FALLBACK_MAP);
            double effective = value(values, ChannelRole.EFFECTIVE_MAP);
            double wallPw = value(values, ChannelRole.WALL_WETTING_PW);
            double instant = value(values, ChannelRole.INSTANT_PULSE_PW);
            StringBuilder text = new StringBuilder("Transient status: MAP Predict ")
                    .append(valueOn(active) ? "ACTIVE" : "inactive")
                    .append(" | MAP ").append(formatLiveMap(realMap))
                    .append(" | fallbackMap ").append(Double.isFinite(fallback) ? F2.format(fallback) : "n/a")
                    .append(" | effectiveMap ").append(Double.isFinite(effective) ? F2.format(effective) : "n/a");
            if (Double.isFinite(realMap) && Double.isFinite(effective)) {
                text.append(" | effective-real gap ").append(F2.format(effective - realMap)).append(" kPa");
            }
            text.append(" | fuel wallwetting injection time ")
                    .append(Double.isFinite(wallPw) ? F3.format(wallPw) + " ms" : "n/a")
                    .append(" | aeInstantPulsePw ")
                    .append(Double.isFinite(instant) ? F3.format(instant) + " ms" : "n/a");
            return text.toString();
        }

        double aeActive = value(values, ChannelRole.AE_ABOVE_THRESHOLD);
        double aeMs = value(values, ChannelRole.AE_ADD_MS);
        double extraFuel = value(values, ChannelRole.EXTRA_FUEL);
        double wall = value(values, ChannelRole.WALL_CORRECTION);
        double wallPw = value(values, ChannelRole.WALL_WETTING_PW);
        double extraShot = value(values, ChannelRole.AE_EXTRA_SHOT);
        double dfco = value(values, ChannelRole.DFCO);
        boolean tpsFuelVisible = absGreater(aeMs, 0.002) || absGreater(extraFuel, 0.0001);
        boolean wallVisible = absGreater(wall, 0.0001) || absGreater(wallPw, 0.0001);
        boolean aeState = valueOn(aeActive);
        boolean extraShotState = valueOn(extraShot);
        if (tpsFuelVisible) {
            return "Fuel-path status: TPS AE fuel visible now (Fuel: TPS AE add fuel ms " + formatLiveFuelValue(aeMs)
                    + ", Fuel: TPS extraFuel " + formatLiveFuelValue(extraFuel) + ")";
        }
        if (aeState) {
            return "Fuel-path status: Fuel: TPS AE Active is on, but TPS AE fuel is not visible right now"
                    + suffixOtherPaths(wallVisible, extraShotState, dfco);
        }
        if (wallVisible || extraShotState || valueOn(dfco)) {
            return "Fuel-path status: TPS AE inactive; other path/state visible"
                    + suffixOtherPaths(wallVisible, extraShotState, dfco);
        }
        return "Fuel-path status: no TPS AE fuel visible in current live sample";
    }

    static String mapPredictGuidance(int predictionEvents,
                                     int repeatedResetEvents,
                                     int resetDiscontinuities,
                                     int wallActiveEvents,
                                     String mapEstimateStatus,
                                     String nextStep) {
        return new StringBuilder("MAP Predict guidance: ")
                .append(predictionEvents).append(" captured prediction event(s), ")
                .append(repeatedResetEvents).append(" event(s) with repeated timer resets, ")
                .append(resetDiscontinuities).append(" reset-counter discontinuity event(s), ")
                .append(wallActiveEvents).append(" event(s) with visible Wall Wetting contribution. ")
                .append(mapEstimateStatus).append(" ")
                .append(nextStep)
                .toString();
    }

    static String tpsCycleGuidance(int eventCount, int proved, int nearMiss) {
        if (eventCount == 0) {
            return "TPS cycle-AE guidance: collect TPS AE fuel-proved events first. The plugin is read-only and will not change the ECU.";
        }
        return "TPS cycle-AE guidance: " + proved + " fuel-proved event(s), " + nearMiss
                + " trigger near miss(es). Use the TPS AE table draft only when this is the intended strategy.";
    }

    private static String suffixOtherPaths(boolean wallVisible, boolean extraShotState, double dfco) {
        String text = "";
        if (wallVisible) text += " | Fuel: wall correction / fuel wallwetting injection time active";
        if (extraShotState) text += " | Fuel: TPSAE ExtraShot active";
        if (valueOn(dfco)) text += " | dfcoActive";
        return text;
    }

    private static boolean absGreater(double value, double threshold) {
        return Double.isFinite(value) && Math.abs(value) > threshold;
    }

    private static boolean valueOn(double value) {
        return Double.isFinite(value) && value >= 0.5;
    }

    private static String formatLiveMap(double value) {
        return Double.isFinite(value) ? F2.format(value) : "n/a";
    }

    private static String formatLiveFuelValue(double value) {
        return Double.isFinite(value) ? F3.format(value) : "n/a";
    }

    private static double value(EnumMap<ChannelRole, Double> values, ChannelRole role) {
        Double value = values.get(role);
        return value == null ? Double.NaN : value.doubleValue();
    }
}
