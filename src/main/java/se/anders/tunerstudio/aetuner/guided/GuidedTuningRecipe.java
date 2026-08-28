package se.anders.tunerstudio.aetuner.guided;

/**
 * User-facing Guided Tuning task catalog.
 *
 * Display order is owned by GuidedTuningArea, not enum declaration order.
 * Tasks marked implemented=false are intentional UX/product scaffolds derived
 * from the current EpicEFI AE controls. They make the intended Guided product
 * shape visible without claiming tuning math, capture qualification or write
 * support that has not been implemented yet.
 *
 * Guarded working-tune Apply is a common product capability: whenever a task's
 * reviewed tuning logic or explicit operator setting choice produces an exact
 * ProposalWritePlan, it may use the shared stale-check/readback/Restore gateway.
 * Burn is excluded.
 */
public enum GuidedTuningRecipe {
    ENGAGEMENT_DETECTION(
            "1. Detector Model / Timing",
            "Detector setting review + evidence available",
            true,
            "Choose and characterize the shared throttle-opening detector. Current controls include Engagement Model, Delta Window, Sample Length and callback rate. Delta Window already has direct reviewed Apply/Restore; other representations are qualified separately."),
    FOUNDATION_THRESHOLD(
            "2. Threshold / Sensitivity",
            "Planned Guided scaffold",
            false,
            "Tune how much throttle movement is required to count as an acceleration event. Current firmware exposes the RPM threshold curve, dynamic-threshold enable/mixing and delta-TPS smoothing/averaging controls."),
    FOUNDATION_VALIDATION(
            "3. Engagement Validation",
            "Planned Guided validation coach",
            false,
            "Validate the completed detector configuration on holds, reversals, partial lifts/re-applies and stacked pedal stabs. This task should prove driver-intent timing rather than introduce another fuel correction."),

    TPS_AE(
            "1. Fuel by Engine Cycle",
            "Guided table evidence available",
            true,
            "Tune the TPS-to versus Engine Cycle fuel multiplier table. Shared event detection belongs to AE Foundation; this task owns the amount/decay shape of TPS AE fuel after an accepted event."),
    TPS_AE_COMPENSATION(
            "2. RPM / Temperature Compensation",
            "Planned Guided scaffold",
            false,
            "Shape TPS AE across operating conditions using the current Transient RPM correction, TPS-vs-CLT AE scale and CLT correction controls without changing the underlying detector."),
    TPS_AE_COMPLETION(
            "3. Completion / Closed-Loop Handoff",
            "Planned Guided scaffold",
            false,
            "Review event completion and closed-loop interaction: cycle-table tail length, TPS AE burn-skip behavior, EGO reset behavior and the post-accel closed-loop inhibit interval."),
    TPS_AE_VALIDATION(
            "4. TPS AE Validation",
            "Planned Guided validation coach",
            false,
            "Validate the resulting TPS AE contribution over repeatable openings and stacked events while separating MAP Predict, Wall Wetting and Instant Fuel overlap."),

    MAP_ESTIMATE(
            "1. MAP Estimate Table",
            "Guided calibration available",
            true,
            "First MAP Predict task: calibrate the predicted MAP surface with persistent evidence, bounded interpolation and targeted Direct Fine Tune. Guarded working-tune Apply is available after explicit Review; no burn."),
    BLEND_DURATION(
            "2. Blend Duration",
            "Correction validation active",
            true,
            "Second MAP Predict task: tune the handover duration between predicted and measured MAP after the MAP Estimate surface is credible. Supported changes use the common guarded Apply path. No burn."),
    MAP_PREDICT(
            "3. Transient Validation",
            "Combined-behavior validation available",
            true,
            "Final MAP Predict task: validate the combined result of MAP Estimate Table, engagement behavior and Blend Duration during real transients. This is outcome validation, not a prerequisite before Blend Duration."),

    WALL_WETTING(
            "1. Model / Base Tau-Beta",
            "Diagnostic evidence capture available",
            true,
            "Establish the Wall Wetting model and basic tau/beta behavior from balanced tip-in/tip-out evidence. Basic mode uses fixed evaporation time (tau) and wall-stick fraction (beta)."),
    WALL_WETTING_ADVANCED(
            "2. Advanced Tau/Beta Mapping",
            "Planned Guided scaffold",
            false,
            "Shape advanced Wall Wetting across coolant temperature and RPM/MAP using the current tau/beta CLT curves and RPM-vs-MAP tables."),
    WALL_WETTING_VALIDATION(
            "3. Film Validation",
            "Planned Guided validation coach",
            false,
            "Validate the complete wall-film response in both directions and across temperatures, checking lambda shape and interaction with other AE methods rather than one AFR peak."),

    DECEL_DETECTION(
            "1. Decel Detection / Threshold",
            "Planned Guided scaffold",
            false,
            "Tune the dedicated throttle-fall threshold and hold-cycle deadband used by current EpicEFI decel detection. Detection remains visible even when the fuel enleanment itself is disabled."),
    DECEL_FUEL(
            "2. Enleanment / Cycle Shape",
            "Planned Guided scaffold",
            false,
            "Tune the decel fuel multiplier versus ending TPS and engine cycles, together with CLT authority. This shapes entry/exit from overrun and does not replace DFCO."),
    DECEL_MAP_PREDICT(
            "3. Decel MAP Prediction",
            "Planned Guided scaffold",
            false,
            "Tune the closing-throttle mirror of MAP Predict: use a lower predicted MAP while the sensor lags, then blend back toward measured MAP. It may stack with the decel fuel multiplier."),
    DECEL_VALIDATION(
            "4. Tip-out / Overrun Validation",
            "Planned Guided validation coach",
            false,
            "Validate tip-out lambda, MAP handoff, recovery and re-application behavior while treating DFCO as observed context rather than an AE Tuner tuning target."),

    INSTANT_FUEL_SETUP(
            "1. Global Pulse / Inhibit",
            "Planned Guided scaffold",
            false,
            "Configure Instant Fuel enable state, global pulse multiplier and inhibit-cycle spacing only after a residual early fuel need has been established."),
    INSTANT_FUEL_EVENT_STRENGTH(
            "2. Event Strength (Delta TPS)",
            "Planned Guided scaffold",
            false,
            "Shape Instant Fuel by the latched throttle-change severity using the current Delta TPS multiplier curve. This separates small pedal corrections from genuinely sharp events."),
    INSTANT_FUEL_CONDITIONS(
            "3. Operating-Condition Multipliers",
            "Planned Guided scaffold",
            false,
            "Shape Instant Fuel across RPM, ending TPS, MAP and coolant temperature with the four current condition multiplier curves."),
    INSTANT_FUEL(
            "4. Residual Lean-Hole Validation",
            "Residual-correction evidence capture available",
            true,
            "Prove that a repeatable early lean hole remains after the primary AE strategy is credible. Instant Fuel should stay a residual correction rather than mask MAP Estimate, Blend Duration, TPS AE or Wall Wetting errors."),

    OPTIMIZATION(
            "1. Stack Interaction Review",
            "Guided product review scaffold",
            false,
            "Review which transient methods are enabled, where they overlap and whether the combination still matches the intended strategy. No tuning authority is implied by this review task."),
    RESIDUAL_ERROR_REVIEW(
            "2. Residual Error Review",
            "Planned Guided review scaffold",
            false,
            "Classify what transient error remains after each enabled method is individually credible: early lean, late rich, tip-out rich, repeated-stab error or load-prediction mismatch."),
    FINAL_SIMPLIFICATION(
            "3. Simplification / Final Validation",
            "Planned Guided review scaffold",
            false,
            "Perform a final mixed-driving validation and remove unnecessary overlap where simpler settings achieve the same transient result. This is review, not another enrichment method." );

    public final String displayName;
    public final String status;
    public final boolean implemented;
    public final String guidance;

    GuidedTuningRecipe(String displayName, String status,
                       boolean implemented, String guidance) {
        this.displayName = displayName;
        this.status = status;
        this.implemented = implemented;
        this.guidance = guidance;
    }

    @Override
    public String toString() {
        return displayName + " — " + status;
    }
}
