package se.anders.tunerstudio.aetuner.model;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.text.DecimalFormat;
import java.util.Arrays;

public final class AeProjectSnapshot {
    private static final DecimalFormat F2 = new DecimalFormat("0.##");

    private final String configurationName;
    private final double[] cycleBins;
    private final double[] tpsToBins;
    private final double[][] cycleValues;
    private final double[] thresholdRpmBins;
    private final double[] thresholdValues;
    private final double extraShotMultiplier;
    private final double extraShotTimer;
    private final double[] cltCorrBins;
    private final double[] cltCorr;
    private final boolean tpsAeEnabled;
    private final boolean wallWettingEnabled;
    private final String wallWettingModel;
    private final boolean extraShotEnabled;
    private final boolean mapEstimateEnabled;
    private final boolean dynamicThresholdEnabled;
    private final boolean dynamicThresholdAverageStatic;
    private final int wallTauRows;
    private final int wallTauCols;
    private final int wallBetaRows;
    private final int wallBetaCols;
    private final double[] mapEstimateRpmBins;
    private final double[] mapEstimateTpsBins;
    private final double[][] mapEstimateTable;
    private final double[] blendDurationRpmBins;
    private final double[] blendDurationValues;

    public AeProjectSnapshot(String configurationName,
                      double[] cycleBins,
                      double[] tpsToBins,
                      double[][] cycleValues,
                      double[] thresholdRpmBins,
                      double[] thresholdValues,
                      double extraShotMultiplier,
                      double extraShotTimer,
                      double[] cltCorrBins,
                      double[] cltCorr,
                      boolean tpsAeEnabled,
                      boolean wallWettingEnabled,
                      String wallWettingModel,
                      boolean extraShotEnabled,
                      boolean mapEstimateEnabled,
                      boolean dynamicThresholdEnabled,
                      boolean dynamicThresholdAverageStatic,
                      double[][] wallTauTable,
                      double[][] wallBetaTable,
                      double[] mapEstimateRpmBins,
                      double[] mapEstimateTpsBins,
                      double[][] mapEstimateTable,
                      double[] blendDurationRpmBins,
                      double[] blendDurationValues) {
        this.configurationName = configurationName;
        this.cycleBins = cloneArray(cycleBins);
        this.tpsToBins = cloneArray(tpsToBins);
        this.cycleValues = cloneTable(cycleValues);
        this.thresholdRpmBins = cloneArray(thresholdRpmBins);
        this.thresholdValues = cloneArray(thresholdValues);
        this.extraShotMultiplier = extraShotMultiplier;
        this.extraShotTimer = extraShotTimer;
        this.cltCorrBins = cloneArray(cltCorrBins);
        this.cltCorr = cloneArray(cltCorr);
        this.tpsAeEnabled = tpsAeEnabled;
        this.wallWettingEnabled = wallWettingEnabled;
        this.wallWettingModel = wallWettingModel == null ? "unknown" : wallWettingModel;
        this.extraShotEnabled = extraShotEnabled;
        this.mapEstimateEnabled = mapEstimateEnabled;
        this.dynamicThresholdEnabled = dynamicThresholdEnabled;
        this.dynamicThresholdAverageStatic = dynamicThresholdAverageStatic;
        this.wallTauRows = wallTauTable == null ? 0 : wallTauTable.length;
        this.wallTauCols = maxColumns(wallTauTable);
        this.wallBetaRows = wallBetaTable == null ? 0 : wallBetaTable.length;
        this.wallBetaCols = maxColumns(wallBetaTable);
        this.mapEstimateRpmBins = cloneArray(mapEstimateRpmBins);
        this.mapEstimateTpsBins = cloneArray(mapEstimateTpsBins);
        this.mapEstimateTable = cloneTable(mapEstimateTable);
        this.blendDurationRpmBins = cloneArray(blendDurationRpmBins);
        this.blendDurationValues = cloneArray(blendDurationValues);
    }

    public String getConfigurationName() { return configurationName; }
    public double[] getCycleBins() { return cloneArray(cycleBins); }
    public double[] getTpsToBins() { return cloneArray(tpsToBins); }
    public double[][] getCycleValues() { return cloneTable(cycleValues); }
    public double[] getMapEstimateRpmBins() { return cloneArray(mapEstimateRpmBins); }
    public double[] getMapEstimateTpsBins() { return cloneArray(mapEstimateTpsBins); }
    public double[][] getMapEstimateTable() { return cloneTable(mapEstimateTable); }
    public double[] getBlendDurationRpmBins() { return cloneArray(blendDurationRpmBins); }
    public double[] getBlendDurationValues() { return cloneArray(blendDurationValues); }

    public boolean isTpsAeEnabled() { return tpsAeEnabled; }
    public boolean isWallWettingEnabled() { return wallWettingEnabled; }
    public String getWallWettingModel() { return wallWettingModel; }
    public boolean isExtraShotEnabled() { return extraShotEnabled; }
    public boolean isMapEstimateEnabled() { return mapEstimateEnabled; }
    public boolean isDynamicThresholdEnabled() { return dynamicThresholdEnabled; }
    public boolean isDynamicThresholdAverageStatic() { return dynamicThresholdAverageStatic; }

    boolean hasAdvancedWallTables() {
        return wallTauRows > 0 && wallTauCols > 0 && wallBetaRows > 0 && wallBetaCols > 0;
    }

    public boolean hasMapEstimateTable() {
        return mapEstimateRpmBins.length > 0 && mapEstimateTpsBins.length > 0 && mapEstimateTable.length > 0;
    }

    public boolean hasBlendDurationCurve() {
        return blendDurationRpmBins.length > 0 && blendDurationValues.length == blendDurationRpmBins.length;
    }

    public boolean isMapPredictWorkflow() {
        return mapEstimateEnabled && !tpsAeEnabled;
    }

    public double recommendThresholdForRpm(double rpm) {
        return interpolate(thresholdRpmBins, thresholdValues, rpm, 1.5);
    }

    public String expectedSessionModeText() {
        if (mapEstimateEnabled && wallWettingEnabled && extraShotEnabled && !tpsAeEnabled) {
            return "Project mode: MAP Predict + Wall Wetting AE + Instant Fuel Pulse";
        }
        if (mapEstimateEnabled && wallWettingEnabled && !tpsAeEnabled) {
            return "Project mode: MAP Predict + Wall Wetting AE (" + wallWettingModel + ")";
        }
        if (mapEstimateEnabled && !tpsAeEnabled) {
            return "Project mode: MAP Predict; TPS cycle AE disabled";
        }
        if (!tpsAeEnabled) {
            return "Project mode: TPS cycle AE disabled";
        }
        if (wallWettingEnabled) {
            return "Project mode: combined TPS cycle AE + Wall Wetting AE (" + wallWettingModel + ")";
        }
        return "Project mode: isolated TPS cycle AE";
    }

    public String toDisplayText() {
        String dynamicText = dynamicThresholdEnabled
                ? "Dynamic TPS AE threshold ON" + (dynamicThresholdAverageStatic ? " (averaged with TPS AE Rate of change vs RPM)" : "")
                : "Dynamic TPS AE threshold OFF";
        String wallText = wallWettingEnabled
                ? "Wall Wetting AE ON / " + wallWettingModel
                        + (hasAdvancedWallTables() ? " / tables " + wallTauRows + "x" + wallTauCols : "")
                : "Wall Wetting AE OFF";
        String mapText = mapEstimateEnabled
                ? "Use MAP estimate during transient ON / MAP Estimate " + dimensions(mapEstimateTable)
                        + " / Predictive Map Blend Duration " + summarize(blendDurationValues)
                : "Use MAP estimate during transient OFF";
        return "Config " + configurationName
                + " | TPS Acceleration Enrichment " + (tpsAeEnabled ? "ON" : "OFF")
                + " | " + dynamicText
                + " | " + wallText
                + " | Instant Fuel Pulse " + (extraShotEnabled ? "ON" : "OFF")
                + " | " + mapText
                + " | TPS AE Rate of change vs RPM " + summarize(thresholdRpmBins) + " -> " + summarize(thresholdValues)
                + " | MAP Estimate RPM bins " + summarize(mapEstimateRpmBins)
                + " | MAP Estimate TPS bins " + summarize(mapEstimateTpsBins)
                + " | ExtraShot x" + F2.format(extraShotMultiplier)
                + " for " + F2.format(extraShotTimer) + " cycle(s)"
                + " | CLT AE correction " + summarize(cltCorrBins) + " -> " + summarize(cltCorr);
    }

    private static String dimensions(double[][] table) {
        return table == null ? "n/a" : table.length + "x" + maxColumns(table);
    }

    private static int maxColumns(double[][] table) {
        int max = 0;
        if (table != null) {
            for (double[] row : table) {
                if (row != null) {
                    max = Math.max(max, row.length);
                }
            }
        }
        return max;
    }

    private static String summarize(double[] values) {
        if (values == null || values.length == 0) {
            return "n/a";
        }
        if (values.length <= 4) {
            return Arrays.toString(values);
        }
        return "[" + F2.format(values[0]) + ", " + F2.format(values[1]) + ", ... "
                + F2.format(values[values.length - 2]) + ", "
                + F2.format(values[values.length - 1]) + "]";
    }

    private static double interpolate(double[] x, double[] y, double value, double fallback) {
        if (x == null || y == null || x.length == 0 || y.length == 0 || x.length != y.length) {
            return fallback;
        }
        if (value <= x[0]) {
            return y[0];
        }
        for (int i = 1; i < x.length; i++) {
            if (value <= x[i]) {
                double span = x[i] - x[i - 1];
                if (Math.abs(span) < 1.0e-9) {
                    return y[i];
                }
                double ratio = (value - x[i - 1]) / span;
                return y[i - 1] + ratio * (y[i] - y[i - 1]);
            }
        }
        return y[y.length - 1];
    }

    private static double[] cloneArray(double[] values) {
        return values == null ? new double[0] : values.clone();
    }

    private static double[][] cloneTable(double[][] values) {
        if (values == null) {
            return new double[0][0];
        }
        double[][] copy = new double[values.length][];
        for (int i = 0; i < values.length; i++) {
            copy[i] = values[i] == null ? new double[0] : values[i].clone();
        }
        return copy;
    }
}
