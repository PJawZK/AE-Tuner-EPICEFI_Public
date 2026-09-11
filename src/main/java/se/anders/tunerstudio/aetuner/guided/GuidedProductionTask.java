package se.anders.tunerstudio.aetuner.guided;

/**
 * v0.19 presentation catalog mapped onto the production Guided recipes.
 * The mapping is presentation ownership only. It does not create tuning authority.
 */
public enum GuidedProductionTask {
    TPS_MOVEMENT_TIMING("TPS Movement / Timing", Group.AE_FOUNDATION,
            "Passive TPS movement timing evidence", GuidedTuningRecipe.ENGAGEMENT_DETECTION, true),
    THRESHOLD_SENSITIVITY("Threshold / Sensitivity", Group.AE_FOUNDATION,
            "Passive detector sensitivity evidence", GuidedTuningRecipe.FOUNDATION_THRESHOLD, true),
    DECEL_DETECTION("Decel Detection", Group.AE_FOUNDATION,
            "Shared TPS-fall threshold, hold and re-arm behavior", GuidedTuningRecipe.DECEL_DETECTION, true),

    TPS_FUEL_RESPONSE("Fuel Response / Decay", Group.TPS_AE,
            "TPS AE engine-cycle x destination-TPS fuel response", GuidedTuningRecipe.TPS_AE, true),
    TPS_SCALING("Scaling / Compensation", Group.TPS_AE,
            "RPM correction, TPS x CLT scale and AE-vs-CLT compensation", GuidedTuningRecipe.TPS_AE_COMPENSATION, true),
    TPS_COMPLETION("Completion / Handoff", Group.TPS_AE,
            "AE tail, Burn Skip, EGO reset and closed-loop inhibit", GuidedTuningRecipe.TPS_AE_COMPLETION, true),
    TPS_VALIDATION("TPS AE Validation", Group.TPS_AE,
            "Early/mid/tail/re-apply outcome validation", GuidedTuningRecipe.TPS_AE_VALIDATION, true),
    DECEL_FUEL_RESPONSE("Decel Fuel Response", Group.TPS_AE,
            "TPS-based cycle x ending-TPS enleanment with CLT authority", GuidedTuningRecipe.DECEL_FUEL, false),

    MAP_ESTIMATE("MAP Estimate", Group.MAP_PREDICT,
            "Stable-cell MAP estimate evidence", GuidedTuningRecipe.MAP_ESTIMATE, true),
    BLEND_DURATION("Blend Duration", Group.MAP_PREDICT,
            "Passive manifold response timing", GuidedTuningRecipe.BLEND_DURATION, true),
    DECEL_MAP_PREDICTION("Decel MAP Prediction", Group.MAP_PREDICT,
            "Tip-out MAP prediction and decel blend duration", GuidedTuningRecipe.DECEL_MAP_PREDICT, false),

    WALL_FILM("Wall Film / Tau & Beta", Group.WALL_WETTING,
            "Basic wall-film amplitude and persistence", GuidedTuningRecipe.WALL_WETTING, true),
    WALL_ADVANCED("Advanced Wall Model", Group.WALL_WETTING,
            "CLT and RPM x MAP Tau/Beta mapping", GuidedTuningRecipe.WALL_WETTING_ADVANCED, true),
    WALL_VALIDATION("Film Validation", Group.WALL_WETTING,
            "Bidirectional completed-model validation", GuidedTuningRecipe.WALL_WETTING_VALIDATION, true),

    // Preserve INSTANT_PULSE as the existing presentation identity, but give it
    // the correct controller ownership: complete global setup belongs to Task 1.
    INSTANT_PULSE("Global Pulse / Inhibit", Group.INSTANT_FUEL,
            "Enable, global multiplier and inhibit-cycle setup", GuidedTuningRecipe.INSTANT_FUEL_SETUP, true),
    INSTANT_EVENT_STRENGTH("Event Strength", Group.INSTANT_FUEL,
            "Delta-TPS pulse-strength curve", GuidedTuningRecipe.INSTANT_FUEL_EVENT_STRENGTH, true),
    INSTANT_CONDITIONS("Operating Conditions", Group.INSTANT_FUEL,
            "RPM, ending-TPS, MAP and CLT multiplier curves", GuidedTuningRecipe.INSTANT_FUEL_CONDITIONS, true),
    INSTANT_VALIDATION("Residual Validation", Group.INSTANT_FUEL,
            "Final first-moment lean-hole / rich-spike validation", GuidedTuningRecipe.INSTANT_FUEL, true),

    IGNITION_RETARD("Ignition Retard", Group.OPTIONAL_TRANSIENT,
            "Optional AE-edge transient ignition retard", null, false);

    public enum Group {
        AE_FOUNDATION("AE Foundation"),
        TPS_AE("TPS AE"),
        MAP_PREDICT("MAP Predict"),
        WALL_WETTING("Wall Wetting"),
        INSTANT_FUEL("Instant Fuel"),
        OPTIONAL_TRANSIENT("Optional Transient");

        public final String displayName;
        Group(String displayName) { this.displayName = displayName; }
    }

    public final String displayName;
    public final Group group;
    public final String description;
    public final GuidedTuningRecipe productionRecipe;
    public final boolean firstSliceBound;

    GuidedProductionTask(String displayName, Group group, String description,
                         GuidedTuningRecipe productionRecipe, boolean firstSliceBound) {
        this.displayName = displayName;
        this.group = group;
        this.description = description;
        this.productionRecipe = productionRecipe;
        this.firstSliceBound = firstSliceBound;
    }
}
