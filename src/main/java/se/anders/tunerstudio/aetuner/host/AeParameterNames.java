package se.anders.tunerstudio.aetuner.host;

/**
 * Canonical TunerStudio controller-parameter names used across AE Tuner.
 * Keeping AE targets here avoids method-specific string duplication and gives
 * the general AE parameter catalog one authoritative controller-name source.
 */
public final class AeParameterNames {
    // Engagement / detection
    public static final String TPS_AE_DETECT_MODE = "tpsAeDetectMode";
    public static final String TPS_AE_DELTA_WINDOW_MS = "tpsAeDeltaWindowMs";
    public static final String TPS_ACCEL_LOOKBACK = "tpsAccelLookback";
    public static final String TPS_AE_FAST_CALLBACK = "tpsAeFastCallback";
    public static final String DELTA_TPS_AVERAGE_ALPHA = "deltaTpsAverageAlpha";
    public static final String TPS_AE_USE_DYNAMIC_THRESHOLD = "tpsAeUseDynamicThreshold";
    public static final String TPS_AE_DYNAMIC_THRESHOLD_AVERAGE_STATIC_CURVE =
            "tpsAeDynamicTresholdAverageStaticCurve";
    public static final String TPS_AE_THRESHOLD_RPM_BINS = "tpsAeThresholdRpmBins";
    public static final String TPS_AE_THRESHOLD_VALUES = "tpsAeThresholdValue";

    // TPS AE fuel / duration
    public static final String TPS_ACCEL_AE_ENABLED = "tpsAccelAeEnabled";
    public static final String TPS_AE_CYCLE_CYCLE_BINS = "tpsAeCycleCycleBins";
    public static final String TPS_AE_CYCLE_TPS_TO_BINS = "tpsAeCycleTpsToBins";
    public static final String TPS_AE_CYCLE_VALUES = "tpsAeCycleValues";
    public static final String TPS_AE_BURN_SKIP_INITIAL = "tpsaeburnskipinitial";
    public static final String TPS_AE_RESETS_EGO = "tpsAeResetsEgo";
    public static final String NO_FUEL_TRIM_AFTER_ACCEL_TIME = "noFuelTrimAfterAccelTime";

    // Instant Fuel
    public static final String TPS_ACCEL_EXTRA_SHOT = "tpsAccelExtraShot";
    public static final String TPS_EXTRA_SHOT_MULT = "tpsExtraShotMult";
    public static final String TPS_EXTRA_SHOT_TIMER = "tpsExtraShotTimer";

    // MAP Predict
    public static final String USE_MAP_ESTIMATE_DURING_TRANSIENT =
            "useMapEstimateDuringTransient";
    public static final String PREDICTIVE_MAP_BLEND_DURATION_BINS =
            "predictiveMapBlendDurationBins";
    public static final String PREDICTIVE_MAP_BLEND_DURATION_VALUES =
            "predictiveMapBlendDurationValues";
    public static final String MAP_ESTIMATE_RPM_BINS = "mapEstimateRpmBins";
    public static final String MAP_ESTIMATE_TPS_BINS = "mapEstimateTpsBins";
    public static final String MAP_ESTIMATE_TABLE = "mapEstimateTable";

    // Wall Wetting
    public static final String WALL_WETTING_AE_ENABLED = "wallWettingAeEnabled";
    public static final String WALL_MODEL_TYPE = "complexWallModel";
    public static final String WALL_TAU = "wwaeTau";
    public static final String WALL_BETA = "wwaeBeta";
    public static final String WALL_TAU_TABLE = "wwTauMapTable";
    public static final String WALL_BETA_TABLE = "wwBetaMapTable";

    private AeParameterNames() {
    }
}
