# Roadmap

Use `docs/CURRENT_STATE.md` for exact current authority.

## Completed foundation

The following foundations are established and regression-covered:

- deterministic Java 8-compatible build;
- one authoritative private `main` branch;
- one permanent full-validation workflow;
- full regression/static-write-safety validation foundation;
- real synthetic Swing/plugin integration;
- Passive/runtime architecture decomposition;
- bounded Guided sample dispatch;
- evidence/session export foundation;
- physically isolated guarded Apply/Restore mechanism;
- MAP Estimate and Blend Duration Guided foundations;
- general AE task taxonomy and coaching proposal base;
- Delta Window scalar physical Apply/readback/Restore qualification;
- MAP Estimate indexed-cell physical Apply/readback/Restore qualification;
- detector research conclusion selecting Dual Stride / Newest for the current controller setup;
- corrected `v0.4.2-rc.2` public test release published, with RC1 retained as superseded provenance.

Historical accepted releases 0.3.18, 0.4.0 and 0.4.1 remain documented in acceptance records and the changelog.

## Current stage — AE Foundation Threshold / Sensitivity

Status: `GUIDED EVIDENCE IMPLEMENTATION`

Current version line: `0.4.2-rc.2` post-release development on private `main`.

Immediate goals:

1. activate Threshold / Sensitivity as a real read-only Guided evidence route;
2. collect production `Fuel: TPS AE change` versus `AccelThreshold` evidence during quiet/incidental movement and deliberate openings across RPM;
3. characterize false-trigger margin, near-threshold misses and RPM dependence before defining tuning math;
4. keep threshold-setting write authority disabled until evidence/recommendation logic is credible and the required representation has been physically qualified;
5. then continue to Engagement Validation.

## AE Foundation — current authority

The first Foundation task is **TPS Movement / Timing**.

Normal tuning question:

`TPS movement -> Fuel: TPS AE change -> AccelThreshold`

Current controller context:

- Dual Stride / Newest detector;
- Delta Window 25 ms;
- Sample Length 50 ms;
- Fast TPS callback approximately 200 Hz.

Current AE Tuner authority:

- Delta Window is the current guarded timing A/B setting;
- Engagement Model is read-only context; alternate detector models are not exposed;
- Sample Length is read-only context;
- Fast Callback is read-only prerequisite/information;
- the temporary five-model comparison is not normal Guided functionality.

Delta Window physical qualification is complete:

`25 ms -> temporary value -> Apply/readback PASS -> Restore 25 ms PASS`

Do not repeat that representation test unless the host/controller representation changes materially.

### Threshold / Sensitivity

Threshold / Sensitivity is the next active Foundation task. Its first implementation is evidence-only:

- required: RPM, TPS, production detected TPS change and live AccelThreshold;
- context: smoothed Delta TPS, TPS AE Active, Dual Stride/Newest diagnostic, actual AE window/sample/stride and downstream AE activity;
- operator protocol: quiet holds/incidental movement plus deliberate near-threshold openings across multiple RPM regions;
- output: threshold-margin evidence and false-trigger/missed-intent review;
- no automatic recommendation and no threshold-setting write plan yet.

## Guided implementation order

The exact task map remains provisional, but current development preference is to complete a coherent Guided base before expanding Passive Analysis.

### AE Foundation

- **TPS Movement / Timing** — current coached production-signal timing task with qualified Delta Window A/B.
- **Threshold / Sensitivity** — active evidence route; next add conservative noise-versus-intent review metrics, then recommendation logic only after validation.
- **Engagement Validation** — build repeatable hold/reversal/reapply/stacked-event validation after threshold sensitivity is credible.

### MAP Predict

- preserve/audit MAP Estimate coverage coach;
- preserve/audit controlled Blend Duration measurement;
- mature combined Transient Validation.

### TPS AE

- Fuel by Engine Cycle;
- RPM / Temperature Compensation;
- Completion / Closed-Loop Handoff;
- TPS AE Validation.

### Wall Wetting

- Model / Base Tau-Beta;
- Advanced Tau/Beta Mapping;
- Film Validation.

### Decel / Tip-out

- Detection / Threshold;
- Enleanment / Cycle Shape;
- Decel MAP Prediction;
- Tip-out / Overrun Validation.

### Optional / Residual Correction

Instant Fuel should remain residual correction rather than a default first-line strategy. Establish that an early residual lean hole exists before adding pulse authority.

### Review / Simplification

Use direct same-protocol A/B evidence first, same-calibration accumulated evidence second, and older tune history as reference only unless relevant fingerprints match.

## Passive Analysis

Status: `DEFERRED UNTIL GUIDED BASE IS COHERENT`

Passive remains useful for observation, diagnostics and future automatic pattern discovery, but it should not distract from completing the Guided workflow first unless a specific Passive defect blocks Guided evidence.

## Automatic Apply / Burn

Status: `DEFERRED / NOT AUTHORIZED`

Automatic tune application and ECU Burn require a separate future safety decision. Do not infer authorization from the existence of guarded explicit working-tune Apply/Restore.
