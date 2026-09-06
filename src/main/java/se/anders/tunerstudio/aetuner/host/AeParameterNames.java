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
    public static final String DELTA_TPS_AVERAGE_CURVE_RPM_BINS = "deltaTpsAverageCurveRpmBins";
    public static final String DELTA_TPS_AVERAGE_CURVE_MULTIPLIER = "deltaTpsAverageCurveMultiplier";
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

    // TPS AE compensation
    public static final String TPS_AE_RPM_CORRECTION_BINS = "tpsTspCorrValuesBins";
    public static final String TPS_AE_RPM_CORRECTION_VALUES = "tpsTspCorrValues";
    public static final String TPS_AE_SCALE_TPS_BINS = "tps_ae_scale_tps_bins";
    public static final String TPS_AE_SCALE_CLT_BINS = "tps_ae_scale_clt_bins";
    public static final String TPS_AE_SCALE_TABLE = "tps_ae_scale_multiplier";
    public static final String AE_CLT_CORR_BINS = "aeCltCorrBins";
    public static final String AE_CLT_CORR_VALUES = "aeCltCorr";

    // Instant Fuel
    public static final String TPS_ACCEL_EXTRA_SHOT = "tpsAccelExtraShot";
    public static final String TPS_EXTRA_SHOT_MULT = "tpsExtraShotMult";
    public static final String TPS_EXTRA_SHOT_TIMER = "tpsExtraShotTimer";
    public static final String TPS_AE_INSTANT_RPM_BINS = "tpsAeInstantRpmBins";
    public static final String TPS_AE_INSTANT_RPM_MULTIPLIER = "tpsAeInstantRpmMultiplier";
    public static final String TPS_AE_INSTANT_TPS_BINS = "tpsAeInstantTpsBins";
    public static final String TPS_AE_INSTANT_TPS_MULTIPLIER = "tpsAeInstantTpsMultiplier";
    public static final String TPS_AE_INSTANT_MAP_BINS = "tpsAeInstantMapBins";
    public static final String TPS_AE_INSTANT_MAP_MULTIPLIER = "tpsAeInstantMapMultiplier";
    public static final String TPS_AE_INSTANT_CLT_BINS = "tpsAeInstantCltBins";
    public static final String TPS_AE_INSTANT_CLT_MULTIPLIER = "tpsAeInstantCltMultiplier";
    public static final String TPS_AE_INSTANT_DELTA_TPS_BINS = "tpsAeInstantDeltaTpsBins";
    public static final String TPS_AE_INSTANT_DELTA_TPS_MULTIPLIER =
            "tpsAeInstantDeltaTpsMultiplier";

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
    public static final String WALL_CLT_BINS = "wwCltBins";
    public static final String WALL_TAU_CLT_VALUES = "wwTauCltValues";
    public static final String WALL_BETA_CLT_VALUES = "wwBetaCltValues";
    public static final String WALL_RPM_BINS = "wwRpmBins";
    public static final String WALL_MAP_BINS = "wwMapBins";
    public static final String WALL_TAU_TABLE = "wwTauMapTable";
    public static final String WALL_BETA_TABLE = "wwBetaMapTable";

    // Decel / tip-out
    public static final String TPS_DECEL_ENLEANMENT_ENABLED = "tpsDecelEnleanmentEnabled";
    public static final String TPS_DECEL_THRESHOLD_RPM_BINS = "tpsDecelThresholdRpmBins";
    public static final String TPS_DECEL_THRESHOLD_VALUES = "tpsDecelThresholdValue";
    public static final String TPS_DECEL_HOLD_CYCLES = "tpsDecelHoldCycles";
    public static final String TPS_DECEL_CYCLE_CYCLE_BINS = "tpsDecelCycleCycleBins";
    public static final String TPS_DECEL_CYCLE_TPS_TO_BINS = "tpsDecelCycleTpsToBins";
    public static final String TPS_DECEL_CYCLE_VALUES = "tpsDecelCycleValues";
    public static final String TPS_DECEL_CLT_BINS = "tpsDecelCltBins";
    public static final String TPS_DECEL_CLT_MULT = "tpsDecelCltMult";
    public static final String USE_MAP_ESTIMATE_DURING_DECEL = "useMapEstimateDuringDecel";
    public static final String DECEL_MAP_BLEND_DURATION_BINS = "decelMapBlendDurationBins";
    public static final String DECEL_MAP_BLEND_DURATION_VALUES = "decelMapBlendDurationValues";

    private AeParameterNames() {
    }
}
