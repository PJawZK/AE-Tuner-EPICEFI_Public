package se.anders.tunerstudio.aetuner.guided;

import static se.anders.tunerstudio.aetuner.guided.GuidedCoachBlueprint.Archetype.*;

/** Provisional coaching interaction catalog for every Guided task. */
public final class GuidedCoachCatalog {
    private GuidedCoachCatalog() { }

    public static GuidedCoachBlueprint forRecipe(GuidedTuningRecipe recipe) {
        GuidedTuningRecipe r = recipe == null ? GuidedTuningRecipe.ENGAGEMENT_DETECTION : recipe;
        switch (r) {
            case ENGAGEMENT_DETECTION:
                return b(r, DETECTOR,
                        "Does real TPS movement cross AccelThreshold promptly, then clear when the pedal stops or reverses?",
                        "READY -> make the requested opening; when triggered HOLD, then release/reapply only when instructed.",
                        "One dominant live strip: TPS movement -> production detector output -> AccelThreshold. Freeze the event around onset/trigger/clear for review.",
                        "READY -> TRIGGER -> CLEAR/RE-ARM. Audio marks state transitions only; it never declares the calibration good.",
                        "Trigger latency, above-threshold tail after TPS settles, reversal sign, fresh reapply separation and repeated-event consistency.",
                        "Aligned frozen traces with landmarks for TPS onset, threshold crossing, pedal settle/reversal and detector clear.",
                        "Baseline maneuver set -> change only Delta Window when justified -> repeat the same maneuver set -> ghost/metric A/B comparison.",
                        "Fast callback is prerequisite/context, not a tuning target. For this vehicle ~200 Hz is intended. Engagement-model comparison is temporary research and should not be a normal user control. Sample Length requires firmware-semantics review before becoming a tuning experiment." );
            case FOUNDATION_THRESHOLD:
                return b(r, DETECTOR,
                        "Where should the acceleration threshold sit between ordinary/noisy pedal movement and deliberate acceleration intent?",
                        "First hold/drive normally; then perform small intentional openings when cued.",
                        "Separation view: ordinary TPS-change envelope on one side, intentional-opening envelope on the other, existing threshold between them; add RPM bands when enough evidence exists.",
                        "READY -> SMALL OPENING -> CAPTURED/RETRY. Prefer sound while driving so the distribution can be reviewed afterward.",
                        "Noise/incidental-movement distribution, deliberate small-opening distribution, false positives, misses and threshold margin versus RPM.",
                        "RPM-banded separation/confidence plot showing whether current threshold has useful margin rather than one raw peak.",
                        "Collect baseline classification evidence -> adjust one threshold/smoothing dimension -> repeat comparable small openings -> compare false-positive/miss margin.",
                        "Dynamic/static threshold behavior and smoothing must be interpreted from current firmware/INI before numerical proposals are enabled." );
            case FOUNDATION_VALIDATION:
                return b(r, VALIDATION,
                        "Does the finished detector follow driver intent through realistic holds, reversals, re-applies and stacked stabs?",
                        "Follow a scripted sequence: normal opening -> stab/hold -> partial lift/reapply -> stacked short stabs.",
                        "Large maneuver state plus compact timing ribbon/event card. No tuning controls in Driver View.",
                        "READY -> ACT -> HOLD/LIFT/REAPPLY -> EVENT CAPTURED or RETRY.",
                        "Correct trigger, clear, reversal sign, fresh rearm and absence of stale-positive history for each maneuver class.",
                        "One card per maneuver class with pass/withhold reasons and synchronized short traces for failed cases.",
                        "Validation only. Route failures back to Detector Timing or Threshold/Sensitivity; do not invent a new correction here.",
                        "Repeat at representative low/mid RPM if evidence shows detector behavior depends materially on operating speed." );

            case TPS_AE:
                return b(r, RESPONSE_SHAPE,
                        "For a given ending TPS row, is TPS AE fuel amount and engine-cycle decay shaped correctly through the lambda response?",
                        "From a quiet baseline, open to the requested TPS-to target and hold long enough for the AE cycle tail to complete.",
                        "TPS target corridor during driving; after each event auto-freeze TPS, AE fuel/cycle multiplier and lambda-minus-target on one aligned transient plot.",
                        "READY -> OPEN TO TARGET -> HOLD -> EVENT CAPTURED/RETRY.",
                        "TPS-from/to, detector state, AE fuel contribution, cycle count/multiplier, injector PW, lambda error and overlap with other AE methods.",
                        "Time/cycle-aligned event traces plus TPS-to row coverage. Separate early amount error from late duration/tail error.",
                        "Baseline events for one TPS-to region -> reviewed row/cycle change -> repeat same opening -> ghost old/new response envelopes.",
                        "Collect several TPS-to regions deliberately; compensation should be deferred until the base cycle shape is credible." );
            case TPS_AE_COMPENSATION:
                return b(r, CONDITION_MAP,
                        "Does the already-good TPS AE base response remain correct across RPM and coolant-temperature conditions?",
                        "Repeat a standardized opening in the requested RPM/temperature band; Guided should tell the user which future temperature bands are still missing.",
                        "Progressively filling RPM x temperature evidence map with live target band and normalized transient residual in each cell.",
                        "READY IN BAND -> STANDARD OPENING -> CAPTURED. Notify when a temperature band is complete before the engine warms past it.",
                        "Comparable base-response residuals across actual RPM/CLT correction axes, with IAT/load context and repeated-event confidence.",
                        "Condition map showing complete/weak/missing bands and whether residual error trends with RPM or temperature.",
                        "Only after the base TPS AE shape is credible: change one compensation surface/region -> repeat comparable events in affected bands.",
                        "Derive practical CLT capture ranges from the working tune's actual correction breakpoints. Show 'needed on a future cold/warm-up run' before capture so the user can plan engine temperature." );
            case TPS_AE_COMPLETION:
                return b(r, TIMING,
                        "Does TPS AE authority end at the right time, and does closed-loop control resume without a gap or overlap problem?",
                        "Perform one clean opening and hold steady through the complete transient/recovery period.",
                        "Post-event timing ribbon: AE cycle/count and fuel contribution, EGO reset/inhibit, closed-loop resume and lambda error landmarks.",
                        "READY -> OPEN/HOLD -> KEEP STEADY UNTIL RECOVERY COMPLETE.",
                        "AE tail end, lambda settling, EGO reset state, post-accel inhibit interval and first meaningful closed-loop correction.",
                        "Aligned handoff timeline highlighting gaps, excessive overlap or closed-loop resumption before/after lambda has settled.",
                        "Baseline handoff -> alter one completion/handoff setting -> repeat same event -> compare timing landmarks and residual lambda tail.",
                        "Test only after Fuel by Engine Cycle is credible; otherwise a bad fuel tail can masquerade as a handoff problem." );
            case TPS_AE_VALIDATION:
                return b(r, VALIDATION,
                        "Does the completed TPS AE setup survive representative openings, re-applies and stacked events without systematic early/late errors?",
                        "Follow scripted small/medium openings plus lift/reapply and stacked-stab cases.",
                        "Large action cue while driving; after each event create a card classifying early lean/rich, late tail, reapply and overlap behavior.",
                        "READY -> requested maneuver -> CAPTURED/RETRY.",
                        "Lambda error timing, TPS AE ownership, other-method overlap and repeatability across maneuver classes.",
                        "Event gallery clustered by error timing; route each recurring failure back to Fuel by Cycle, Compensation or Completion/Handoff.",
                        "Validation only; use before/after suites when a prior TPS AE task is changed.",
                        "Include more than one useful RPM/load region before calling the subsystem complete." );

            case MAP_ESTIMATE:
                return b(r, COVERAGE,
                        "Which TPS x RPM cells still need trustworthy steady measured-MAP evidence, and what value does that evidence support?",
                        "Drive into the highlighted target zone and hold a stable eligible operating point until the evidence cue completes.",
                        "Live TPS/RPM target tunnel plus progressively filling MAP Estimate heat map: Direct, bounded Interpolated, Conflict/Recheck, current-run and target states.",
                        "Directional/target-enter cue -> evidence-lock cue -> next-target cue; avoid continuous distracting tones.",
                        "Stable measured MAP micro-buckets, repeat-session maturity, local residual quality, no-extrapolation boundaries and conflict evidence.",
                        "Heat-map coverage/proposal review with current-run versus learned-memory provenance and conflict/recheck cells clearly separated.",
                        "Coverage acquisition rather than classic A/B. Proposed table cells are reviewed/applied, then future drives confirm maturity rather than treating one run as final truth.",
                        "Target reachable cells first; persistent memory may span sessions, but current-run evidence remains visibly distinct." );
            case BLEND_DURATION:
                return b(r, TIMING,
                        "At each RPM table point, how long does measured MAP physically need to catch the final/upward-latched predicted target after a controlled opening?",
                        "Acquire the requested RPM baseline -> make the requested TPS step -> settle in the corridor -> HOLD until measurement completes.",
                        "During driving show RPM and TPS target corridors only. After the event auto-zoom a frozen trace of TPS, fallback target, measured MAP, Effective MAP and firmware replay with target-relatch landmarks.",
                        "BASELINE READY -> OPEN -> PLATEAU ACQUIRED -> HOLD -> MEASUREMENT COMPLETE -> RELEASE.",
                        "Final-target catch-up duration, target relatches, physical MAP response, Effective-MAP replay coherence, gear/comparability and repeated-event spread.",
                        "Ghost/envelope comparable events at one RPM point plus median/range/confidence; show model-vs-measured residual separately.",
                        "Baseline event group -> one reviewed duration change when conversion authority is validated -> repeat the same table point/TPS step/gear -> A/B overlay.",
                        "Repeat at the actual Blend Duration RPM bins. Do not infer untested bins from one road event without validated interpolation logic." );
            case MAP_PREDICT:
                return b(r, VALIDATION,
                        "Does MAP Predict as a complete system trigger/rearm and hand back to measured MAP correctly during realistic transients?",
                        "Perform scripted opening, hold, lift/reapply and stacked-stab maneuvers after MAP Estimate and Blend Duration are credible.",
                        "Synchronized event replay with TPS, detector/rearm markers, fallback/predicted MAP, measured MAP and Effective MAP plus timing ribbons for detector and prediction-active state.",
                        "READY -> requested maneuver -> CAPTURED/RETRY; audio should not require watching fast MAP traces while driving.",
                        "Fresh rearm versus stale rearm, Effective-MAP continuity, target relatches, measured-MAP convergence and secondary lambda context.",
                        "Event cards and synchronized replay; failures route to MAP Estimate, Blend Duration or Foundation detector rather than a generic MAP Predict knob.",
                        "Validation suites can provide A/B proof after upstream changes, using only comparable event classes.",
                        "Include low-RPM/stacked events because those are particularly informative for prediction/rearm behavior." );

            case WALL_WETTING:
                return b(r, PAIRED,
                        "Do base tau/beta settings reproduce a balanced wall-film response on both tip-in and tip-out at a repeatable warm condition?",
                        "Capture a clean tip-in, recover, then a comparable tip-out when cued.",
                        "Paired/mirrored post-event traces of wall correction/pulse and lambda error with other AE contributions shown as context ribbons.",
                        "READY -> TIP-IN -> CAPTURED -> RECOVER -> TIP-OUT -> CAPTURED.",
                        "Wall correction/pulse shape, lambda-minus-target before/during/after both directions, decay and overlap attribution.",
                        "Paired tip-in/tip-out shape comparison across repeated events; avoid reducing the result to one AFR peak.",
                        "Baseline paired set -> one justified tau/beta change after firmware-equation review -> repeat paired set under similar conditions.",
                        "Establish base behavior fully warm first; temperature mapping belongs to the Advanced task." );
            case WALL_WETTING_ADVANCED:
                return b(r, CONDITION_MAP,
                        "How must a credible base wall-film model vary with coolant temperature and RPM/MAP operating region?",
                        "Repeat the standardized paired event in the requested condition band; Guided should announce future cold/warm-up bands before they are missed.",
                        "Condition map using actual tau/beta CLT axes and RPM x MAP regions, filled with residual paired-response evidence rather than raw AFR peaks.",
                        "IN TARGET BAND -> TIP-IN/TIP-OUT sequence -> BAND COMPLETE/NEEDS MORE.",
                        "Normalized residual wall-film response versus base model, repeated-event confidence and condition provenance.",
                        "Missing/complete/conflicting condition cells with before/after residual shape for changed regions.",
                        "Alter only the affected condition region after enough evidence -> revisit the same band on a later comparable run.",
                        "Temperature ranges must come from the working tune's actual correction axes and be shown as future-run requirements while the engine is still cold enough." );
            case WALL_WETTING_VALIDATION:
                return b(r, VALIDATION,
                        "Does the complete wall-film calibration remain balanced in both directions and across the conditions it claims to cover?",
                        "Follow paired tip-in/tip-out plus repeated transition sequences in requested condition bands.",
                        "Event cards plus paired overlays and ownership ribbons showing TPS AE/MAP Predict/Instant Fuel context.",
                        "READY -> requested paired maneuver -> CAPTURED/RETRY.",
                        "Correction sign/decay, lambda shape, condition consistency and method overlap.",
                        "Validation matrix by maneuver and condition; failures route to Base Tau/Beta or Advanced Mapping.",
                        "Use current-session A/B when validating a change and same-calibration memory to show whether improvement generalizes.",
                        "Do not call warm validation complete if cold/transition bands with active compensation remain untested." );

            case DECEL_DETECTION:
                return b(r, DETECTOR,
                        "Does closing-throttle movement cross the decel threshold intentionally, clear correctly and avoid chatter through holds/re-applies?",
                        "Hold steady -> make the requested small/medium lift -> hold -> reapply when cued.",
                        "Negative TPS-change versus decel threshold separation with hold-cycle timing ribbon and frozen lift event.",
                        "READY -> LIFT -> DETECTED -> HOLD -> CLEAR/REAPPLY.",
                        "Closing-rate distribution, threshold crossing, hold-cycle behavior, false detections and reapply separation versus RPM.",
                        "Frozen negative-direction traces and RPM-banded threshold margin.",
                        "Baseline lift set -> one threshold/hold change -> repeat same lift classes -> A/B false/miss/tail comparison.",
                        "Detection remains meaningful with fuel enleanment disabled; keep DFCO as context rather than tuning authority." );
            case DECEL_FUEL:
                return b(r, RESPONSE_SHAPE,
                        "For a given ending TPS, is decel enleanment amount and cycle shape correct before DFCO/normal fueling takes over?",
                        "From a repeatable load, lift to the requested ending-TPS target and hold through the decel-fuel event.",
                        "Ending-TPS target corridor while driving; post-event decel multiplier/cycle timeline with lambda error and DFCO context.",
                        "READY -> LIFT TO TARGET -> HOLD -> EVENT COMPLETE.",
                        "Decel multiplier/cycle progression, lambda response, ending TPS, CLT authority and DFCO/fuel-cut participation.",
                        "Ending-TPS row coverage plus time-aligned response shape; DFCO cases classified separately.",
                        "Baseline row/cycle events -> one reviewed enleanment change -> repeat same lift -> ghost A/B response.",
                        "Temperature compensation should be mapped only after the base warm decel shape is credible." );
            case DECEL_MAP_PREDICT:
                return b(r, TIMING,
                        "How quickly should lower predicted MAP hand back to measured MAP after a controlled closing-throttle event?",
                        "Acquire steady baseline -> perform requested lift -> HOLD until MAP handoff completes.",
                        "Mirror Blend Duration: live target corridor during driving; frozen post-event trace of TPS, predicted lower MAP, measured MAP and Effective MAP with blend landmarks.",
                        "BASELINE READY -> LIFT -> HOLD -> MAP HANDOFF COMPLETE.",
                        "Measured MAP fall, predicted target, blend timing, relatches/restarts if present and decel-fuel overlap context.",
                        "Comparable lift-event overlays and per-RPM duration evidence.",
                        "Baseline duration group -> one reviewed duration change -> repeat same lift/gear/RPM -> A/B overlay.",
                        "Test actual decel blend RPM bins and separate DFCO cases from ordinary tip-out behavior." );
            case DECEL_VALIDATION:
                return b(r, VALIDATION,
                        "Does the complete tip-out/overrun stack behave correctly on partial lift, full lift and rapid reapply?",
                        "Follow scripted small lift -> medium/full lift -> lift/reapply maneuvers.",
                        "Large maneuver cue plus post-event cards showing decel detector, decel fuel, decel MAP prediction and DFCO ownership ribbons.",
                        "READY -> requested lift/reapply -> CAPTURED/RETRY.",
                        "Lambda/MAP handoff, recovery, reapply quality and which closing-throttle mechanisms actually participated.",
                        "Event gallery by maneuver class; route failures to Detection, Fuel Shape or Decel MAP Prediction.",
                        "Validation-only A/B suite after upstream decel changes.",
                        "Include both DFCO and non-DFCO tip-out classes where the working tune uses both." );

            case INSTANT_FUEL_SETUP:
                return b(r, TIMING,
                        "Is there a repeatable very-early residual fuel deficit that justifies a global Instant Fuel pulse/inhibit at all?",
                        "Perform the residual-error maneuver requested by Guided only after upstream AE methods are credible.",
                        "Aggressively auto-zoom the first few hundred milliseconds after pedal onset: TPS, upstream AE contributions, injector PW and lambda residual with instant-pulse timing marker.",
                        "READY -> SHARP OPENING -> CAPTURED. No enable/change cue until residual need is proven.",
                        "Residual early lean timing and magnitude before slower methods can respond, plus proof that MAP Predict/TPS AE/WW are already credible.",
                        "Clustered onset traces showing whether the residual is repeatable and truly early enough for Instant Fuel ownership.",
                        "Only after residual proof: one pulse/inhibit change -> repeat identical event class -> A/B onset comparison.",
                        "If no residual early lean hole exists, the correct result is 'Instant Fuel not justified'." );
            case INSTANT_FUEL_EVENT_STRENGTH:
                return b(r, CONDITION_MAP,
                        "How should Instant Fuel strength scale with the latched Delta TPS severity of the event?",
                        "Perform SMALL, MEDIUM and LARGE openings when cued rather than chasing raw Delta TPS numbers on-screen.",
                        "Event-strength map/curve: latched Delta TPS on X, early residual/requested correction on Y, with repeatability envelopes.",
                        "READY -> SMALL/MEDIUM/LARGE OPENING -> CAPTURED.",
                        "Latched Delta TPS, instant-pulse contribution, early lambda residual and upstream-method overlap.",
                        "Strength-response scatter/envelope and curve coverage showing weak/missing severity regions.",
                        "Collect severity coverage -> change only supported curve region -> repeat same severity classes -> A/B residual comparison.",
                        "Do this only after Global Pulse/Inhibit is justified; otherwise the curve is fitting a correction that may not be needed." );
            case INSTANT_FUEL_CONDITIONS:
                return b(r, CONDITION_MAP,
                        "Does a proven Instant Fuel event-strength relation need systematic correction across RPM, ending TPS, MAP or CLT?",
                        "Repeat a standardized event in the requested operating-condition band.",
                        "Condition coverage maps for the four current multiplier dimensions, showing normalized residual after the base strength curve.",
                        "IN TARGET CONDITION -> STANDARD OPENING -> CAPTURED/BAND COMPLETE.",
                        "Residual early error versus RPM/TPS/MAP/CLT after event strength is normalized.",
                        "Condition-dependent residual maps with confidence and missing-band prompts.",
                        "Alter one condition multiplier region -> revisit the same band -> compare residual distribution.",
                        "CLT bands should be planned from actual tune axes and announced early enough for future cold/warm-up runs." );
            case INSTANT_FUEL:
                return b(r, VALIDATION,
                        "After all upstream methods are credible, does any repeatable early lean hole remain, and does Instant Fuel remove it without creating rich overshoot?",
                        "Perform the specific sharp event classes that previously exposed the residual.",
                        "Tight synchronized onset replay with ownership ribbons for MAP Predict, TPS AE, Wall Wetting and Instant Fuel plus lambda residual.",
                        "READY -> requested sharp opening -> CAPTURED/RETRY.",
                        "Residual onset timing, pulse ownership, lambda improvement/overshoot and repeatability.",
                        "Before/after event gallery; no residual means Instant Fuel should remain unused rather than being tuned for activity.",
                        "Direct A/B is preferred: upstream-credible baseline -> Instant Fuel change -> same event class. Historical memory only supports generalization.",
                        "Validate more than one event severity/condition before accepting a residual correction globally." );

            case OPTIMIZATION:
                return b(r, OWNERSHIP,
                        "Which transient method owns each part of the current response, and where are two methods correcting the same time region?",
                        "No special driving maneuver unless missing evidence is identified; this is primarily a synchronized review task.",
                        "Ownership ribbons aligned to TPS/lambda/MAP: detector, MAP Predict, TPS AE, Wall Wetting, Instant Fuel, decel and closed-loop authority.",
                        "Audio normally off during review; if a new capture is requested, route to the owning Guided coach instead.",
                        "Contribution timing/magnitude, overlap windows and residual error timing across retained comparable events.",
                        "Synchronized scrub/replay with selectable event groups and contribution ribbons.",
                        "Use direct session A/B for changes; use same-calibration accumulated memory as supporting evidence, never as an unlabeled replacement for the baseline.",
                        "Older/different-calibration memory is reference-only unless relevant setting fingerprints match." );
            case RESIDUAL_ERROR_REVIEW:
                return b(r, OWNERSHIP,
                        "What error remains after each enabled method is individually credible, and which Guided task most likely owns it?",
                        "No eyes-down driving required; collect more events only when a cluster lacks enough comparable examples.",
                        "Event gallery clustered by timing/shape: early lean, early rich, late rich/lean tail, reapply, tip-out and load-prediction mismatch.",
                        "No routine audio in review; new capture requests should use the destination coach's cue grammar.",
                        "Residual lambda/MAP timing, method ownership ribbons, condition grouping and repeated-event confidence.",
                        "Select a cluster to open synchronized events; route recurring clusters back to the most relevant Guided task with reasons.",
                        "Compare current-session A/B first, then ask whether same-calibration memory shows the same residual pattern across sessions.",
                        "Do not pool old tune revisions into current residual statistics without matching relevant calibration fingerprints." );
            case FINAL_SIMPLIFICATION:
                return b(r, VALIDATION,
                        "Can the transient stack be simpler without measurably degrading the response across the required maneuver/condition envelope?",
                        "Run the standardized mixed-event validation suite for the current stack and any explicit simplification candidate.",
                        "Before/after distribution summary plus ownership ribbons and event-class pass cards; drill into synchronized traces only where results differ.",
                        "READY -> requested validation maneuver -> CAPTURED/RETRY; review happens after the suite.",
                        "Error distributions, event-class failures, method authority/overlap and condition coverage before versus after simplification.",
                        "Primary authority: explicit same-protocol A/B session. Secondary support: same-calibration accumulated memory. Older-calibration evidence is reference only.",
                        "Baseline suite A -> explicit reviewed simplification -> suite B -> keep or Restore. Never auto-disable a method from a score alone.",
                        "Require enough condition/maneuver coverage to prove that 'no degradation' is not merely one warm-session result." );
            default:
                return b(r, VALIDATION,
                        r.guidance,
                        "Follow the task-specific capture instructions.",
                        "Task-specific visual coach to be refined from firmware and physical evidence.",
                        "Task-specific transition cues only where they reduce eyes-down driving.",
                        "Collect only evidence that can be attributed to this task.",
                        "Review comparable events and withhold conclusions when evidence is incomplete.",
                        "Use explicit A/B when a setting change can be tested repeatably.",
                        "Future operating-condition needs must be shown before they are missed." );
        }
    }

    private static GuidedCoachBlueprint b(GuidedTuningRecipe recipe,
                                           GuidedCoachBlueprint.Archetype archetype,
                                           String question,
                                           String driverCue,
                                           String primaryVisual,
                                           String audio,
                                           String evidence,
                                           String review,
                                           String experiment,
                                           String futureConditions) {
        return new GuidedCoachBlueprint(recipe, archetype, question, driverCue,
                primaryVisual, audio, evidence, review, experiment, futureConditions);
    }
}
