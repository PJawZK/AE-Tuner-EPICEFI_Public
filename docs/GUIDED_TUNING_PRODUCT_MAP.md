# Guided Tuning product map — current EpicEFI AE surface

Status: **Dev20 UX/product scaffold; not all tasks are functional tuners**

This document records the intended Guided Tuning shape derived from the current EpicEFI/TunerStudio AE controls. It is deliberately allowed to evolve as tuning logic is implemented and vehicle evidence teaches us that tasks should be merged, split, renamed, removed or reordered.

## Product rules

- Start with **AE Foundation** because downstream accel strategies depend on shared event detection.
- After Foundation, TPS AE, MAP Predict and Wall Wetting are **alternative/combinable strategies**, not one mandatory global sequence.
- Task numbering is **local to each selected Tuning Area**. It expresses dependencies inside that area and must not be interpreted as one global 1..N checklist across all AE strategies.
- Selector maturity text describes the task's **capability/maturity**, not whether a capture is currently running. Live capture state is communicated separately by the Guided workflow/status UI.
- Instant Fuel is an **optional residual correction**, not a default next step.
- Decel / Tip-out is a distinct closing-throttle transient family in current EpicEFI and therefore gets its own area.
- DFCO is observed context, not an AE Tuner tuning target.
- VE and ignition are outside AE Tuner tuning authority.
- Current EpicEFI transient ignition retard may alter transient evidence; Guided may warn/report it as a confounder but must not edit it.
- Planned task entries are UX/product scaffolds only. They have no fake capture path, tuning recommendation or ProposalWritePlan until their own implementation is validated.
- No automatic Apply and no Burn.

## Area and task map

### AE Foundation

1. **TPS Movement / Timing** — active Dev20 route
   - normal tuning question: `TPS movement -> Fuel: TPS AE change -> AccelThreshold`
   - Dual Stride / Newest: read-only controller context
   - Delta Window (`tpsAeDeltaWindowMs`): current guarded timing A/B setting
   - Sample Length (`tpsAccelLookback`): read-only context
   - Fast Callback (`tpsAeFastCallback`): read-only prerequisite/information; approximately 200 Hz intended
   - alternate detector models and Engagement Model editing are not exposed in normal Guided UX
2. **Threshold / Sensitivity** — planned
   - `deltaTpsAverageAlpha`
   - `tpsAeUseDynamicThreshold`
   - `tpsAeDynamicTresholdAverageStaticCurve`
   - RPM threshold bins/values
   - dynamic-threshold multiplier curve
3. **Engagement Validation** — planned dedicated coach
   - opening/hold
   - reversal sign
   - partial lift/reapply
   - stacked short stabs
   - stale-positive tails

### TPS AE

1. **Fuel by Engine Cycle** — active evidence/table route
   - Engine Cycle x TPS-to fuel multiplier table
2. **RPM / Temperature Compensation** — planned
   - TPS AE RPM correction
   - TPS vs CLT AE scale
   - TPS AE CLT correction
3. **Completion / Closed-Loop Handoff** — planned
   - cycle-table tail/rightmost duration
   - `tpsaeburnskipinitial`
   - `tpsAeResetsEgo`
   - `noFuelTrimAfterAccelTime`
4. **TPS AE Validation** — planned dedicated coach

### MAP Predict

Local dependency order is authoritative:

1. **MAP Estimate Table** — active
2. **Blend Duration** — active
3. **Transient Validation** — active

Transient Validation is the outcome check for the table + duration + detector behavior; it is not a prerequisite before Blend Duration.

### Wall Wetting

1. **Model / Base Tau-Beta** — active evidence route
   - enable/model type
   - basic `wwaeTau`
   - basic `wwaeBeta`
2. **Advanced Tau/Beta Mapping** — planned
   - tau/beta versus CLT
   - RPM x MAP tau/beta tables (`wwTauMapTable`, `wwBetaMapTable`)
3. **Film Validation** — planned dedicated coach
   - balanced tip-in/tip-out
   - correction sign/decay
   - condition dependence
   - overlap attribution

### Decel / Tip-out

Current EpicEFI exposes this as a real closing-throttle transient family.

1. **Decel Detection / Threshold** — planned
   - throttle-fall threshold by RPM
   - hold-cycle deadband (`tpsDecelHoldCycles`)
   - detection status remains meaningful independent of fuel enable
2. **Enleanment / Cycle Shape** — planned
   - Engine Cycle x ending-TPS fuel multiplier
   - CLT authority
   - enable controls fuel authority, not the existence of detection
3. **Decel MAP Prediction** — planned
   - closing-throttle predicted lower MAP
   - RPM-based decel blend duration
   - may stack with explicit decel fuel multiplier
4. **Tip-out / Overrun Validation** — planned
   - DFCO/fuel-cut kept as context
   - partial/full lift and rapid reapply

### Optional / Residual Correction — Instant Fuel

1. **Global Pulse / Inhibit** — planned
   - `tpsAccelExtraShot`
   - `tpsExtraShotMult`
   - `tpsExtraShotTimer`
2. **Event Strength (Delta TPS)** — planned
   - `tpsAeInstantDeltaTpsBins`
   - `tpsAeInstantDeltaTpsMultiplier`
   - Delta TPS is latched when the shot is armed
3. **Operating-Condition Multipliers** — planned
   - RPM
   - ending TPS
   - MAP
   - CLT
4. **Residual Lean-Hole Validation** — active evidence route
   - only justify Instant Fuel after upstream methods are credible

### Review / Simplification

1. **Stack Interaction Review** — product scaffold
2. **Residual Error Review** — planned
3. **Simplification / Final Validation** — planned

These tasks should eventually answer "which method owns this remaining error?" before proposing another setting change.

## Guided Focus information architecture

Every non-specialized task should expose the same predictable sections:

1. **Status / maturity** — available vs planned scaffold vs review.
2. **Purpose** — what physical/algorithmic behavior this task owns.
3. **Current EpicEFI controls** — settings already known from the current firmware/INI.
4. **Current working-tune context** — values/status once mapped into `AeProjectSnapshot`.
5. **What to do** — operator procedure, not generic prose.
6. **Watch / measure** — required and attribution channels.
7. **What good evidence looks like** — positive completion criteria.
8. **When AE Tuner should withhold** — explicit uncertainty/quality gates.
9. **Next** — local dependency or which area to choose next.
10. **Product boundary** — no automatic Apply/Burn; VE/ignition observational only.

Dedicated visual coaches may replace the text scaffold where a task benefits from stronger visualization (MAP Estimate heat map is the current example). The shared information structure should remain recognizable even when the presentation becomes graphical.

## Implementation principle

A task becomes functional only when its own bounded implementation is ready:

1. map its exact controller settings/representations;
2. add required working-tune snapshot fields;
3. define required/context evidence and rejection rules;
4. implement tuning/recommendation math;
5. expose exact reviewed `ProposalWritePlan` changes when justified;
6. qualify each new controller representation once via Apply/readback/Restore;
7. validate on real evidence;
8. update this product map if implementation shows the initial grouping was wrong.

This lets Guided Tuning approach a near-final UX shape early without confusing UX completeness with tuning-algorithm completeness.
