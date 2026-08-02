package se.anders.tunerstudio.aetuner;

import java.text.DecimalFormat;

/** Pure text formatting for Overview status cards. */
final class OverviewTextRenderer {
    private static final DecimalFormat F1 = new DecimalFormat("0.0");
    private static final DecimalFormat F3 = new DecimalFormat("0.000");

    private OverviewTextRenderer() { }

    static String stage(AeProjectSnapshot snapshot) {
        boolean mapMode = snapshot.isMapPredictWorkflow();
        if (mapMode && !snapshot.isWallWettingEnabled()) return "Stage 1: MAP Predict";
        if (mapMode && snapshot.isWallWettingEnabled() && !snapshot.isExtraShotEnabled()) {
            return "Stage 2: Wall Wetting";
        }
        if (mapMode && snapshot.isWallWettingEnabled() && snapshot.isExtraShotEnabled()) {
            return "Stage 3: Instant Fuel";
        }
        return "Legacy TPS cycle AE";
    }

    static String mapValues(double realMap, double fallback, double effective) {
        String text = "Real: " + finiteOrNa(realMap, F1) + " kPa"
                + "\nEstimate: " + finiteOrNa(fallback, F1) + " kPa"
                + "\nEffective: " + finiteOrNa(effective, F1) + " kPa";
        return text + (Double.isFinite(realMap) && Double.isFinite(effective)
                ? "\nGap: " + F1.format(effective - realMap) + " kPa"
                : "\nGap: n/a");
    }

    static String transientFuel(double wallPw, double instantPw) {
        return "Wall: " + finiteOrNa(wallPw, F3) + " ms"
                + "\nInstant: " + finiteOrNa(instantPw, F3) + " ms";
    }

    static String eventProgress(int predictionEvents, int repeatedResets) {
        return predictionEvents + " prediction event(s)"
                + "  •  " + repeatedResets + " repeated-reset event(s)";
    }

    static String mapCoverage(long acceptedSamples, int covered, int total) {
        return acceptedSamples + " stable samples"
                + "  •  " + covered + "/" + total + " cells ready";
    }

    private static String finiteOrNa(double value, DecimalFormat format) {
        return Double.isFinite(value) ? format.format(value) : "n/a";
    }
}
