package se.anders.tunerstudio.aetuner.guided;

/**
 * Product/UX guidance for Guided tasks that do not yet own a dedicated visual
 * coach. The catalog is intentionally descriptive rather than algorithmic: it
 * records what the current EpicEFI controls mean, what an operator should do,
 * what evidence a future tuner should require, and what AE Tuner must not claim
 * before the corresponding tuning logic is implemented and validated.
 */
public final class GuidedTaskFocusCatalog {
    private GuidedTaskFocusCatalog() { }

    public static String focusText(GuidedTuningRecipe recipe, String workingTuneContext) {
        GuidedTuningRecipe safe = recipe == null
                ? GuidedTuningRecipe.ENGAGEMENT_DETECTION : recipe;
        return maturity(safe)
                + "\n\nPURPOSE\n" + purpose(safe)
                + "\n\nCURRENT EPICEFI CONTROLS\n" + controls(safe)
                + "\n\nCURRENT WORKING-TUNE CONTEXT\n"
                + (workingTuneContext == null || workingTuneContext.trim().length() == 0
                    ? "Read Working Tune to populate the context available to this task."
                    : workingTuneContext.trim())
                + "\n\nWHAT TO DO\n" + action(safe)
                + "\n\nWATCH / MEASURE\n" + watch(safe)
                + "\n\nWHAT GOOD EVIDENCE LOOKS LIKE\n" + goodEvidence(safe)
                + "\n\nWHEN AE TUNER SHOULD WITHHOLD\n" + withhold(safe)
                + "\n\nNEXT\n" + next(safe)
                + "\n\nPRODUCT BOUNDARY\n"
                + "No automatic Apply and no Burn. Planned tasks describe the intended Guided workflow only; they do not yet create tuning recommendations or write plans. VE and ignition remain observation/quality context, not AE Tuner tuning targets. Transient ignition retard may affect the same events, but AE Tuner must only report it as a confounder.";
    }

    public static String controlsText(GuidedTuningRecipe recipe) {
        return controls(recipe);
    }

    private static String maturity(GuidedTuningRecipe recipe) {
        if (recipe.implemented) {
            return "STATUS — AVAILABLE / CURRENT GUIDED ROUTE\n"
                    + recipe.status + ". The task may already own evidence or setting functionality, but only supported reviewed changes may reach guarded Apply/Restore.";
        }
        if (recipe == GuidedTuningRecipe.OPTIMIZATION
                || recipe == GuidedTuningRecipe.RESIDUAL_ERROR_REVIEW
                || recipe == GuidedTuningRecipe.FINAL_SIMPLIFICATION) {
            return "STATUS — PRODUCT REVIEW SCAFFOLD\n"
                    + "This task is present now to show the intended final workflow. Review logic will be added incrementally.";
        }
        return "STATUS — PLANNED GUIDED SCAFFOLD\n"
                + "The controls and workflow are known from the current EpicEFI definition, but AE Tuner does not yet claim validated tuning math for this task.";
    }

    private static String purpose(GuidedTuningRecipe recipe) {
        switch (recipe) {
            case ENGAGEMENT_DETECTION:
                return "Establish the detector model and time horizon that define a real throttle-opening event before any downstream AE strategy is judged.";
            case FOUNDATION_THRESHOLD:
                return "Set event sensitivity high enough to reject TPS noise and holds, but low enough to catch intentional pedal movement across RPM.";
            case FOUNDATION_VALIDATION:
                return "Prove the final detector follows current driver intent through opening, hold, reversal, partial lift/reapply and stacked stabs.";
            case TPS_AE:
                return "Shape the amount and decay of cycle-based TPS AE fuel versus where the throttle opening ends.";
            case TPS_AE_COMPENSATION:
                return "Correct TPS AE response for RPM and temperature only after the base cycle-fuel shape is credible.";
            case TPS_AE_COMPLETION:
                return "Make the end of the TPS AE event and the handoff back to closed-loop fueling predictable rather than leaving a long rich/lean tail.";
            case TPS_AE_VALIDATION:
                return "Verify the completed TPS AE calibration across representative openings without attributing other AE methods to TPS AE.";
            case MAP_ESTIMATE:
                return "Build a credible steady-state TPS x RPM estimate of manifold pressure before asking it to predict transient load.";
            case BLEND_DURATION:
                return "Set how long Effective MAP should transition from predicted MAP back toward the live MAP sensor after prediction engages.";
            case MAP_PREDICT:
                return "Validate the combined MAP Estimate + Blend Duration result against actual transient MAP/load behavior.";
            case WALL_WETTING:
                return "Establish the wall-film model and basic evaporation/stick behavior so tip-in and tip-out correction have the right overall shape.";
            case WALL_WETTING_ADVANCED:
                return "Add coolant- and load-dependent tau/beta detail only where a single basic pair cannot describe the observed film behavior.";
            case WALL_WETTING_VALIDATION:
                return "Verify wall-film behavior in both transient directions and across temperature/load after the selected model is tuned.";
            case DECEL_DETECTION:
                return "Define a deliberate closing-throttle event separately from accel detection and reject steady-throttle noise.";
            case DECEL_FUEL:
                return "Shape the amount and decay of explicit decel enleanment while protecting cold driveability.";
            case DECEL_MAP_PREDICT:
                return "Compensate the MAP sensor's closing-throttle lag by temporarily using a lower predicted MAP and blending back to measured MAP.";
            case DECEL_VALIDATION:
                return "Verify tip-out, overrun entry, recovery and pedal re-application without confusing DFCO with the decel transient model.";
            case INSTANT_FUEL_SETUP:
                return "Set the basic size and re-trigger spacing of the asynchronous Instant Fuel pulse after a genuine residual need has been demonstrated.";
            case INSTANT_FUEL_EVENT_STRENGTH:
                return "Scale Instant Fuel by the severity of the latched throttle-change event so small corrections and sharp stabs do not receive the same pulse.";
            case INSTANT_FUEL_CONDITIONS:
                return "Shape an already-justified Instant Fuel pulse across RPM, TPS, MAP and coolant temperature.";
            case INSTANT_FUEL:
                return "Prove a repeatable early lean hole remains after the primary AE methods are credible, and verify Instant Fuel fixes that residual error without creating overlap.";
            case OPTIMIZATION:
                return "Understand which enabled transient methods contribute to the same event and where their authority overlaps.";
            case RESIDUAL_ERROR_REVIEW:
                return "Classify the remaining transient error before deciding which method, if any, should be changed next.";
            case FINAL_SIMPLIFICATION:
                return "Confirm the finished transient calibration works in mixed driving and remove unnecessary compensation layers where possible.";
            default:
                return recipe.guidance;
        }
    }

    private static String controls(GuidedTuningRecipe recipe) {
        switch (recipe) {
            case ENGAGEMENT_DETECTION:
                return "Engagement Model (tpsAeDetectMode); Delta Window (tpsAeDeltaWindowMs); Sample Length (tpsAccelLookback); fast callback (tpsAeFastCallback).";
            case FOUNDATION_THRESHOLD:
                return "Delta TPS smoothing (deltaTpsAverageAlpha); dynamic threshold enable (tpsAeUseDynamicThreshold); static/dynamic averaging (tpsAeDynamicTresholdAverageStaticCurve); TPS change threshold by RPM (tpsAeThresholdRpmBins / tpsAeThresholdValue); dynamic-threshold multiplier curve.";
            case FOUNDATION_VALIDATION:
                return "No new calibration surface. Validate the selected detector with Fuel: TPS AE change, AccelThreshold, all five comparator deltas, AE window/sample count/stride and ACCEL_TPS.";
            case TPS_AE:
                return "TPS AE enable plus Engine Cycle x TPS-to multiplier table (tpsAeCycleCycleBins, tpsAeCycleTpsToBins, tpsAeCycleValues).";
            case TPS_AE_COMPENSATION:
                return "TunerStudio: TPS AE RPM correction; TPS vs CLT AE SCALE; TPS AE CLT correction. Exact write mappings will be catalogued before this task becomes active.";
            case TPS_AE_COMPLETION:
                return "Cycle-table rightmost duration/tail; TPS AE Burn Skip count (tpsaeburnskipinitial); TPS Accel resets EGO (tpsAeResetsEgo); Inhibit closed loop fuel after accel (noFuelTrimAfterAccelTime).";
            case TPS_AE_VALIDATION:
                return "No additional setting. Review TPS AE cycle contribution, injector PW and lambda response with overlap from MAP Predict, Wall Wetting and Instant Fuel.";
            case MAP_ESTIMATE:
                return "MAP Estimate TPS bins, RPM bins and table (mapEstimateTpsBins, mapEstimateRpmBins, mapEstimateTable).";
            case BLEND_DURATION:
                return "Predictive MAP Blend Duration RPM bins/values (predictiveMapBlendDurationBins / predictiveMapBlendDurationValues).";
            case MAP_PREDICT:
                return "Use MAP estimate during transient plus the already-tuned MAP Estimate table and Blend Duration; this task primarily validates their combined behavior.";
            case WALL_WETTING:
                return "Wall Wetting enable (wallWettingAeEnabled); model type (complexWallModel); basic tau (wwaeTau); basic beta (wwaeBeta).";
            case WALL_WETTING_ADVANCED:
                return "Tau and beta versus coolant temperature plus RPM x MAP tau/beta tables (wwTauMapTable / wwBetaMapTable). Advanced mode replaces the fixed basic pair with condition-dependent behavior.";
            case WALL_WETTING_VALIDATION:
                return "No new setting. Review wallFuelAmount/wallFuelCorrection, Wall Wetting PW, lambda/target and overlap with the other transient paths.";
            case DECEL_DETECTION:
                return "Enable decel enleanment (fuel authority only), dedicated TPS fall threshold by RPM, and Decel hold in engine cycles (tpsDecelHoldCycles). Detection itself continues to run for status/trim-pause behavior.";
            case DECEL_FUEL:
                return "TPS Decel fuel multiplier by Engine Cycle x ending TPS plus CLT authority. 1.00 means no fuel change; CLT authority 0 leaves fuel alone and 1 applies the table fully.";
            case DECEL_MAP_PREDICT:
                return "Decel MAP Prediction blend-duration RPM bins/values (decelMapBlendDurationBins / decelMapBlendDurationValues) using the existing MAP Estimate surface as the lower predicted load.";
            case DECEL_VALIDATION:
                return "No new setting. Review decelTps, DecelThreshold, decelFuelMult, tpsDecelCnt, decelTo, decelEventTriggeredCnt, Effective MAP and DFCO/fuel-cut context.";
            case INSTANT_FUEL_SETUP:
                return "Instant Fuel Pulse enable (tpsAccelExtraShot); global multiplier (tpsExtraShotMult); inhibit cycles (tpsExtraShotTimer).";
            case INSTANT_FUEL_EVENT_STRENGTH:
                return "Throttle-change bins and pulse multipliers (tpsAeInstantDeltaTpsBins / tpsAeInstantDeltaTpsMultiplier). The Delta TPS value is latched when the shot is armed.";
            case INSTANT_FUEL_CONDITIONS:
                return "Instant pulse multipliers versus RPM, TPS, MAP and CLT (tpsAeInstantRpm*, tpsAeInstantTps*, tpsAeInstantMap*, tpsAeInstantClt* curves).";
            case INSTANT_FUEL:
                return "No additional calibration surface beyond the Instant Fuel tasks above; this task decides whether the pulse is justified and whether the resulting early response is correct.";
            case OPTIMIZATION:
                return "Enabled-method state and each method's observed contribution. Method toggles remain explicit operator choices; review does not automatically enable/disable them.";
            case RESIDUAL_ERROR_REVIEW:
                return "No direct setting. Classify residual timing/direction: early lean, late rich, tip-out rich, repeated-stab error, MAP handoff error or condition-dependent mismatch.";
            case FINAL_SIMPLIFICATION:
                return "No direct setting. Review the final chosen AE stack and only expose exact proposed removals/changes after evidence-backed simplification logic exists.";
            default:
                return "Task-specific controller mapping is still being catalogued.";
        }
    }

    private static String action(GuidedTuningRecipe recipe) {
        switch (recipe) {
            case FOUNDATION_THRESHOLD:
                return "Log quiet holds and deliberate throttle openings at several RPM points. Establish the TPS noise floor first, then compare that margin with missed intentional events. Do not tune sensitivity from one stab.";
            case FOUNDATION_VALIDATION:
                return "Repeat opening -> hold, opening -> partial lift, lift -> reapply and short stacked stabs. The selected detector should change sign/drop out when driver intent changes, not when old samples finally age out.";
            case TPS_AE_COMPENSATION:
                return "First prove the base cycle table at one warm operating region. Then repeat comparable openings at different RPM and temperatures so only the correction dimension changes.";
            case TPS_AE_COMPLETION:
                return "Inspect the tail after pedal motion stops: when does TPS AE contribution return to zero, when does lambda settle, and when are closed-loop trims allowed back in? Shorten/lengthen only for repeatable tail errors.";
            case TPS_AE_VALIDATION:
                return "Run repeatable small, medium and fast openings plus stacked events across the intended RPM range. Keep method-overlap channels visible.";
            case WALL_WETTING_ADVANCED:
                return "Collect the same kind of tip-in/tip-out event at meaningfully different CLT and load regions. Add table detail only where the basic model shows a systematic condition-dependent error.";
            case WALL_WETTING_VALIDATION:
                return "Compare matched tip-in and tip-out events when warm, then repeat at other temperatures. Verify correction direction, magnitude and decay rather than chasing one lambda sample.";
            case DECEL_DETECTION:
                return "Measure throttle-fall noise during steady cruise/holds, then perform deliberate lifts. Start the threshold comfortably above noise; use hold cycles only to suppress chatter without making the event sticky.";
            case DECEL_FUEL:
                return "With decel detection credible, repeat clean lifts to several ending TPS values. Tune depth at the first cycle columns and recovery with later columns; keep cold CLT authority conservative.";
            case DECEL_MAP_PREDICT:
                return "Use clean closing-throttle events where live MAP visibly lags the actual load change. Compare predicted/effective/measured MAP and shorten the blend when the sensor catches up sooner.";
            case DECEL_VALIDATION:
                return "Test partial lifts, full lifts, overrun, and rapid re-application. Separate events where DFCO activates from events where transient decel fueling remains in authority.";
            case INSTANT_FUEL_SETUP:
                return "Do not begin here unless repeated data shows a very early lean deficit. Use the smallest global pulse that addresses the deficit and enough inhibit spacing to prevent pulse pile-up on noisy/repeated triggers.";
            case INSTANT_FUEL_EVENT_STRENGTH:
                return "Collect comparable events spanning small to large Delta TPS. The desired curve should give little/no authority to small corrections and progressively more only where event severity demands it.";
            case INSTANT_FUEL_CONDITIONS:
                return "After global/Delta TPS behavior is credible, repeat the same event across RPM, TPS, MAP and CLT regions. Change only a condition curve when the residual error follows that dimension.";
            case OPTIMIZATION:
                return "Select the intended final AE strategy, then inspect events where multiple methods contribute simultaneously. Identify overlap before deciding whether it is complementary or redundant.";
            case RESIDUAL_ERROR_REVIEW:
                return "Group remaining bad events by timing and direction before changing anything. A correct classification should point to one upstream cause or one method-specific task.";
            case FINAL_SIMPLIFICATION:
                return "Run mixed real driving, including repeated stabs and tip-out/reapply. Prefer fewer active corrections when they produce the same lambda/load response and driver feel.";
            default:
                return recipe.guidance;
        }
    }

    private static String watch(GuidedTuningRecipe recipe) {
        switch (recipe) {
            case FOUNDATION_THRESHOLD:
            case FOUNDATION_VALIDATION:
                return "TPS, Fuel: TPS AE change, AccelThreshold, smoothedDeltaTps, AE comparator deltas, Fuel: AE window, Fuel: AE window samples, Fuel: AE delta stride, ACCEL_TPS; downstream AE outputs only as context.";
            case TPS_AE:
            case TPS_AE_COMPENSATION:
            case TPS_AE_COMPLETION:
            case TPS_AE_VALIDATION:
                return "TPS from/to/change, TPS AE cycle count/multiplier, TPS AE added fuel, injector PW, Lambda and Target lambda, RPM/CLT; MAP Predict, Wall Wetting and Instant Fuel as attribution context.";
            case MAP_ESTIMATE:
                return "RPM, TPS, MAP, fallbackMap; reject transient/AE/fuel-cut samples so the table represents stable real MAP.";
            case BLEND_DURATION:
            case MAP_PREDICT:
                return "MAPValue, fallbackMap, effectiveMap, isMapPredictionActive, mapPredEventOver, TPS detector state, RPM and lambda context.";
            case WALL_WETTING:
            case WALL_WETTING_ADVANCED:
            case WALL_WETTING_VALIDATION:
                return "wallFuelAmount, wallFuelCorrection / Wall Wetting PW, Lambda, Target lambda, injector PW, TPS/MAP/RPM/CLT plus TPS AE, MAP Predict and Instant Fuel overlap.";
            case DECEL_DETECTION:
            case DECEL_FUEL:
            case DECEL_VALIDATION:
                return "decelTps, DecelThreshold, decelFuelMult, tpsDecelCnt, decelTo, decelEventTriggeredCnt, TPS/MAP/RPM/Lambda/Target lambda, DFCO and fuel cut.";
            case DECEL_MAP_PREDICT:
                return "Measured MAP, fallback/predicted MAP, Effective MAP, decel trigger/counter, RPM/TPS and DFCO/fuel-cut context.";
            case INSTANT_FUEL_SETUP:
            case INSTANT_FUEL_EVENT_STRENGTH:
            case INSTANT_FUEL_CONDITIONS:
            case INSTANT_FUEL:
                return "Instant pulse PW/count, latched Delta TPS, shared detector ratio, RPM/TPS/MAP/CLT, injector PW, Lambda/Target lambda and all other transient-fuel contributions.";
            case OPTIMIZATION:
            case RESIDUAL_ERROR_REVIEW:
            case FINAL_SIMPLIFICATION:
                return "A common event timeline containing detector state, Effective/Measured/Predicted MAP, every enabled AE fuel contribution, injector PW, Lambda/Target lambda, DFCO/fuel cut and transient ignition-retard status as read-only context.";
            default:
                return "Use the task's required and attribution channels; do not infer a recommendation from incomplete data.";
        }
    }

    private static String goodEvidence(GuidedTuningRecipe recipe) {
        switch (recipe) {
            case FOUNDATION_THRESHOLD:
                return "Quiet holds remain below threshold with margin; intentional openings cross it promptly at multiple RPM points; sensitivity does not depend on stale history.";
            case FOUNDATION_VALIDATION:
                return "Detector output follows current pedal direction, drops promptly on hold/reversal and re-arms cleanly on a new opening without long stale-positive tails.";
            case TPS_AE_COMPENSATION:
                return "Matched events show the same lambda response after correction across RPM/temperature rather than needing different base-table shapes.";
            case TPS_AE_COMPLETION:
                return "Added TPS AE fuel reaches zero when the transient is actually over, lambda recovers without a late rich/lean tail, and closed-loop resumes after the transient rather than fighting it.";
            case TPS_AE_VALIDATION:
                return "Repeatable lambda-minus-target shape with no unexplained row/cycle discontinuities and consistent behavior on repeated openings.";
            case WALL_WETTING_ADVANCED:
                return "A repeatable error changes with CLT/load in a way the advanced tau/beta surfaces can explain; added complexity improves matched events rather than fitting noise.";
            case WALL_WETTING_VALIDATION:
                return "Correct sign and smooth decay on both tip-in and tip-out, with similar residual lambda error across the tested operating envelope.";
            case DECEL_DETECTION:
                return "No flicker at steady throttle, prompt trigger on deliberate lifts and stable hold behavior without masking a rapid re-application.";
            case DECEL_FUEL:
                return "Tip-out lambda moves toward target without cold stalls, abrupt steps or a long lean/rich recovery tail; the cycle table returns smoothly to 1.00.";
            case DECEL_MAP_PREDICT:
                return "Effective MAP moves toward the lower actual-load trajectory before the sensor catches up, then hands back smoothly without overshoot or prolonged authority.";
            case DECEL_VALIDATION:
                return "Predictable tip-out and reapply behavior with clean separation between transient decel logic and later DFCO authority.";
            case INSTANT_FUEL_SETUP:
            case INSTANT_FUEL_EVENT_STRENGTH:
            case INSTANT_FUEL_CONDITIONS:
            case INSTANT_FUEL:
                return "The early lean deficit is repeatable and time-aligned with the pulse; the minimum required pulse fixes it without a subsequent rich spike or unnecessary firing on mild events.";
            case OPTIMIZATION:
                return "Each enabled method has a clear reason to exist and its authority occurs where expected; overlap is intentional rather than accidental.";
            case RESIDUAL_ERROR_REVIEW:
                return "Bad events fall into a stable category with a repeatable timing signature instead of a random mixture of unrelated causes.";
            case FINAL_SIMPLIFICATION:
                return "Mixed driving remains correct with the smallest practical correction stack and no regression in stacked-stab or tip-out/reapply behavior.";
            default:
                return "Repeated comparable events agree on direction and magnitude, required channels are complete, and changes in other AE paths do not explain the result.";
        }
    }

    private static String withhold(GuidedTuningRecipe recipe) {
        if (!recipe.implemented) {
            return "This task is currently a UX/product scaffold, so AE Tuner must withhold numerical recommendations and Apply plans until its parameter mapping, evidence rules and tuning logic are implemented and validated.";
        }
        if (recipe == GuidedTuningRecipe.INSTANT_FUEL) {
            return "Withhold when the early error is not repeatable, another AE method is clearly wrong, pulse activity is not visible, or method overlap prevents attribution.";
        }
        if (recipe == GuidedTuningRecipe.WALL_WETTING) {
            return "Withhold tau/beta changes when tip-in/tip-out evidence is not repeatable, temperature/load is uncontrolled, or other AE contributions dominate the lambda response.";
        }
        return "Withhold when required channels are missing, events are not comparable, the working-tune baseline is stale, or another subsystem plausibly explains the observed transient.";
    }

    private static String next(GuidedTuningRecipe recipe) {
        switch (recipe) {
            case ENGAGEMENT_DETECTION: return "Continue to Threshold / Sensitivity, then return to Engagement Validation after sensitivity is established.";
            case FOUNDATION_THRESHOLD: return "Continue to Engagement Validation. If validation fails, return to Model / Timing or Threshold / Sensitivity according to the failure mode.";
            case FOUNDATION_VALIDATION: return "Choose only the downstream AE strategy/strategies actually used by the tune; there is no requirement to complete every area.";
            case TPS_AE: return "If the base cycle table is credible, continue to RPM / Temperature Compensation.";
            case TPS_AE_COMPENSATION: return "Continue to Completion / Closed-Loop Handoff.";
            case TPS_AE_COMPLETION: return "Continue to TPS AE Validation.";
            case TPS_AE_VALIDATION: return "If another AE strategy is enabled, tune/validate that area independently; otherwise continue to Review / Simplification.";
            case MAP_ESTIMATE: return "Continue to Blend Duration only after the relevant MAP Estimate cells are credible.";
            case BLEND_DURATION: return "Continue to Transient Validation of the combined MAP Predict behavior.";
            case MAP_PREDICT: return "If MAP Predict is satisfactory, continue with any other enabled AE strategy or Review / Simplification.";
            case WALL_WETTING: return "Use Advanced Tau/Beta Mapping only if the basic model shows systematic condition dependence; otherwise go directly to Film Validation.";
            case WALL_WETTING_ADVANCED: return "Continue to Film Validation.";
            case WALL_WETTING_VALIDATION: return "Continue with another enabled strategy or Review / Simplification.";
            case DECEL_DETECTION: return "Continue to Enleanment / Cycle Shape if explicit decel fuel is enabled, and/or Decel MAP Prediction if that strategy is used.";
            case DECEL_FUEL: return "Continue to Decel MAP Prediction if enabled, then Tip-out / Overrun Validation.";
            case DECEL_MAP_PREDICT: return "Continue to Tip-out / Overrun Validation.";
            case DECEL_VALIDATION: return "Continue to Review / Simplification once tip-out and re-application are credible.";
            case INSTANT_FUEL_SETUP: return "Continue to Event Strength (Delta TPS).";
            case INSTANT_FUEL_EVENT_STRENGTH: return "Continue to Operating-Condition Multipliers only where condition dependence is demonstrated.";
            case INSTANT_FUEL_CONDITIONS: return "Continue to Residual Lean-Hole Validation.";
            case INSTANT_FUEL: return "If the pulse is justified and clean, continue to Review / Simplification; otherwise remove/reduce it and fix the upstream method responsible for the error.";
            case OPTIMIZATION: return "Continue to Residual Error Review.";
            case RESIDUAL_ERROR_REVIEW: return "Return to the specific task implicated by the residual error, or continue to Simplification / Final Validation if no material error remains.";
            case FINAL_SIMPLIFICATION: return "Guided tuning is complete when the final stack is intentional, repeatable and no remaining task has an evidence-backed unresolved issue.";
            default: return "Follow the local task order only where the area has a real dependency.";
        }
    }
}
