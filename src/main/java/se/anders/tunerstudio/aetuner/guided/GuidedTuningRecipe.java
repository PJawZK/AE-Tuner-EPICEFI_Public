package se.anders.tunerstudio.aetuner.guided;

/**
 * User-facing Guided Tuning task catalog.
 *
 * Display order is owned by GuidedTuningArea, not enum declaration order.
 * implemented=true means a real vehicle evidence route exists. Recommendation
 * maturity remains independent: a baseline heuristic may be deliberately marked
 * as confirmation/refinement work while every physically validated setting is
 * already accessible through the common guarded Task Settings / Apply path.
 *
 * Guarded working-tune Apply is a common product capability: whenever a task's
 * reviewed tuning logic or explicit operator setting choice produces an exact
 * ProposalWritePlan, it may use the shared stale-check/readback/Restore gateway.
 * Burn is excluded.
 */
public enum GuidedTuningRecipe {
    ENGAGEMENT_DETECTION(
            "1. TPS Movement / Timing",
            "Two-stage timing vehicle validation available",
            true,
            "Coach repeatable TPS movement against production detected TPS change and AccelThreshold. Sample Length is controlled Stage 1 and Delta Window is controlled Stage 2; live ECU window/sample/stride timing must qualify before maneuver evidence counts. Dual Stride / Newest and Fast Callback remain read-only controller context/prerequisite."),
    FOUNDATION_THRESHOLD(
            "2. Threshold / Sensitivity",
            "Guided sensitivity evidence + guarded recommendation available",
            true,
            "Measure event-level separation between ordinary tiny pedal corrections and deliberate openings in Fuel: TPS AE change / AccelThreshold space across broad RPM regions. The implemented recommendation is limited to evidence-backed tpsAeThresholdValue bins. Dynamic Threshold OFF uses the static curve directly; Dynamic ON with static averaging ON uses verified Effective=(Static+Dynamic)/2 inversion; Dynamic ON with averaging OFF leaves the static curve at zero runtime authority."),
    FOUNDATION_VALIDATION(
            "3. Engagement Validation",
            "Planned Guided validation coach",
            false,
            "Validate the completed detector configuration on holds, reversals, partial lifts/re-applies and stacked pedal stabs. This task should prove driver-intent timing rather than introduce another fuel correction."),

    TPS_AE(
            "1. Fuel by Engine Cycle",
            "Guided table recommendation + guarded Apply available",
            true,
            "Tune the TPS-to versus Engine Cycle fuel multiplier table. Shared event detection belongs to AE Foundation; this task owns the amount/decay shape of TPS AE fuel after an accepted event."),
    TPS_AE_COMPENSATION(
            "2. RPM / Temperature Compensation",
            "Complete baseline evidence + guarded recommendation available",
            true,
            "Shape TPS AE across operating conditions using the Transient RPM correction, TPS-vs-CLT AE scale and CLT correction controls. All validated axes/tables/curves are present; baseline evidence moves only covered attributable regions and is intended for new vehicle confirmation/refinement."),
    TPS_AE_COMPLETION(
            "3. Completion / Closed-Loop Handoff",
            "Complete baseline handoff evidence + guarded recommendation available",
            true,
            "Review event completion and closed-loop interaction: cycle-table tail length, TPS AE burn-skip behavior, EGO reset behavior and the post-accel closed-loop inhibit interval. The whole validated completion surface remains directly review/apply-capable even when one capture supports only part of it."),
    TPS_AE_VALIDATION(
            "4. TPS AE Validation",
            "TPS AE outcome validation active",
            true,
            "Validate early amount, mid-event shape, late tail and rapid re-apply over repeatable openings while separating MAP Predict, Wall Wetting and Instant Fuel overlap. This final validation is evidence-only and routes failures back to the owning TPS AE subtask."),

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
            "Base Tau/Beta evidence + guarded recommendation available",
            true,
            "Establish the Wall Wetting model and basic tau/beta behavior from paired tip-in/tip-out evidence. Basic mode uses fixed evaporation time (tau) and wall-stick fraction (beta); both belong to the working baseline and are confirmed against measured film amplitude/persistence."),
    WALL_WETTING_ADVANCED(
            "2. Advanced Tau/Beta Mapping",
            "Complete advanced mapping baseline + guarded recommendation available",
            true,
            "Shape advanced Wall Wetting across coolant temperature and RPM/MAP using the complete tau/beta CLT curves and RPM-vs-MAP tables. All surfaces are captured from Working Tune; automatic movement requires covered attributable regions rather than hiding unmeasured cells."),
    WALL_WETTING_VALIDATION(
            "3. Film Validation",
            "Bidirectional film validation active",
            true,
            "Validate the complete wall-film response in both directions and across temperatures, checking lambda shape, correction decay and interaction with other AE methods rather than one AFR peak."),

    DECEL_DETECTION(
            "1. Decel Detection / Threshold",
            "Guided falling-TPS threshold evidence + guarded recommendation available",
            true,
            "Tune the dedicated throttle-fall threshold used by current EpicEFI decel detection from signed falling-TPS event evidence. Automatic recommendation is limited to evidence-backed tpsDecelThresholdValue cells; threshold 0 remains a firmware disable command and is never auto-enabled. tpsDecelHoldCycles is captured but remains read-only until threshold behavior is validated independently."),
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
            "Complete three-setting setup baseline + guarded recommendation available",
            true,
            "With Instant Fuel enabled in the Working Tune, tune its complete setup surface: enable state, global pulse multiplier and inhibit-cycle spacing from repeatable first-moment and re-apply evidence. If Instant Fuel is OFF, all Instant Fuel tasks are unavailable and collect no Guided evidence until it is enabled and Working Tune is read again."),
    INSTANT_FUEL_EVENT_STRENGTH(
            "2. Event Strength (Delta TPS)",
            "Delta-TPS strength curve baseline + guarded recommendation available",
            true,
            "Shape Instant Fuel by the latched throttle-change severity using the complete Delta TPS axis/multiplier curve. Covered event-strength bins may move from first-moment residual evidence; unmeasured bins remain at Working Tune."),
    INSTANT_FUEL_CONDITIONS(
            "3. Operating-Condition Multipliers",
            "Four condition-curve baseline + guarded recommendation available",
            true,
            "Shape Instant Fuel across RPM, ending TPS, MAP and coolant temperature with all four current condition multiplier curves. Baseline logic distributes correction authority across the multiplicative surfaces and is intended for vehicle confirmation/refinement."),
    INSTANT_FUEL(
            "4. Residual Lean-Hole Validation",
            "Final Instant Fuel validation active",
            true,
            "Validate that the completed Instant Fuel setup removes only a repeatable first-moment residual without masking sustained MAP Estimate, Blend Duration, TPS AE or Wall Wetting errors. This final task is evidence-only; writable Instant settings belong to the three tuning tasks above."),

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
