# Transient tuning strategy

## Primary intended sequence

The current Volvo 940 Turbo / EpicEFI workflow is:

```text
MAP Predict → Wall Wetting → Instant Fuel
```

This is a tuning sequence, not a claim that the systems never overlap at runtime.

## Stage 1 — MAP Predict

Purpose:

- compensate the speed-density airmass calculation while measured MAP catches up after throttle movement;
- improve tip-in without treating full future boost as known.

Turbo-specific rules:

- MAP Estimate is an expected immediate/steady RPM-TPS MAP relationship, not a peak-boost forecast;
- high-TPS cells must be reviewed conservatively;
- unvisited cells must not be changed automatically;
- repeated-stab events must not define the base Blend Duration curve;
- effective MAP held well above measured MAP during repeated stabs is a rich-stumble risk.

Evidence required before accepting a MAP Estimate change:

- enough stable samples in the exact cell;
- acceptable MAP spread;
- exclusion of active prediction, rapid changes, DFCO, cuts and other unsuitable conditions;
- review of the old value, proposal, count, standard deviation and range.

Evidence required before accepting a Blend Duration change:

- a clean opening event rather than a repeated-stab sequence;
- no throttle release before measured catch-up;
- measured MAP catch-up observed;
- adequate repeated evidence in a comparable RPM/load region;
- acceptable event spread and confidence;
- unsupported RPM points preserved.

## Current Guided Capture method — vehicle-test.12

The current internal physical-test candidate uses one adaptive Guided Capture workflow rather than separate rigid road/dyno modes.

For road testing:

- establish a natural stable baseline;
- allow the baseline to follow reasonable gradual road-load change;
- make a clean pedal opening without chasing one absolute TPS target;
- freeze the pre-opening baseline only when the opening begins, then keep the advisory TPS marker fixed from that baseline;
- measure the actual relative TPS step and natural held plateau;
- tolerate bounded real pedal movement;
- retain clean events collected at different baseline road loads;
- automatically place materially different events into separate comparability groups;
- only combine events from one comparable group for proposal evidence;
- allow only `MAP_PRED_ACTIVE` samples to define Blend Duration MAP-gap measurement anchors; stale prediction-inactive fallback gaps remain raw trace evidence only.

Road versus dyno changes the observed conditions and comparability, not the user workflow.

Passive capture continues in the background and is not a separate selectable mode. In `.12`, active Guided computation is isolated from the Passive/TunerStudio sample callback through the bounded instance-local `GuidedSampleDispatcher`; physical validation must confirm that this architecture preserves timely live data and coherent Guided behavior.

During `.12` architecture/workflow validation, the objective is to validate capture, lifecycle, dispatcher, evidence and physical usability. Do **not** paste, apply or approve a generated Blend Duration proposal merely because a series completes.

Exact `.12` physical-test identity:

- source commit `70580ceb5ceda0847d7e847bcb2e7cc3e4ff1f05`;
- JAR SHA-256 `e3cf2b8d346dd8c02f59726783e9d8a3a85357afc67fe5097b15b5e255eaa306`;
- physical gate issue #28.

## Stage 2 — Wall Wetting

Wall Wetting models fuel-film storage and release and is the main continuous tip-in/tip-out correction in the intended stack.

It should be evaluated after MAP Predict is stable because otherwise a rich or lean transient can be misattributed.

Initial plugin role:

- identify Wall Wetting-only and combined events;
- report its injection-time contribution;
- compare MAP Predict-only and MAP Predict + Wall Wetting sessions;
- guide review rather than auto-changing Advanced Wall Wetting tables.

## Stage 3 — Instant Fuel

Instant Fuel is a targeted asynchronous pulse for a remaining very early delivery-latency hole.

Do not enable it simply because any lean response exists.

Candidate conditions:

- MAP Predict behaves correctly;
- Wall Wetting is understood;
- a sharp early lean hole remains on fast low-RPM or closely spaced stabs;
- the error occurs too early for scheduled injection correction;
- repeated events confirm the pattern.

When first enabled:

- keep the global amount modest;
- start sub-multiplier tables flat at `1.0`;
- monitor inhibit-cycle behavior;
- specifically test repeated drift-style inputs for rich accumulation.

## Legacy TPS cycle AE

The TPS cycle multiplier table is retained as a compatibility workflow. It is not the intended primary strategy when MAP Predict and Wall Wetting are used as the base transient model.

The plugin must not offer a TPS cycle-table proposal in a configuration where that fuel path is disabled.

## DFCO and full-load boundaries

DFCO and DFCO-exit behavior can contaminate transient evidence. The plugin must classify or reject affected events.

Full-load pulls are not the primary way to tune MAP Predict. They are useful for safety context, boost behavior and confirming that prediction has handed control back to measured MAP.

Never claim a full-load system is safe when required channels are missing.

## Acceptance principle

Automated validation proves deterministic software behavior; it does not approve a tuning value. Physical vehicle evidence and matched log review remain decisive before any manual tune change is accepted.
