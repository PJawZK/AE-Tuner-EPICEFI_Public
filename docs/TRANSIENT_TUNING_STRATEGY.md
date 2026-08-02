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
- unvisited cells are not changed automatically;
- repeated-stab events must not define the base Blend Duration curve;
- effective MAP held well above measured MAP during repeated stabs is a rich-stumble risk.

Evidence required before accepting a MAP Estimate change:

- enough stable samples in the exact cell;
- acceptable MAP spread;
- exclusion of active prediction, rapid changes, DFCO, cuts, and other unsuitable conditions;
- review of the old value, proposal, count, standard deviation, and range.

Evidence required before accepting a Blend Duration change:

- one continuous TPS-change burst;
- no throttle release before catch-up;
- measured MAP catch-up is observed;
- event is not a repeated-stab/drift sequence;
- repeated evidence in the relevant RPM region.

## Stage 2 — Wall Wetting

Wall Wetting models fuel film storage and release and is the main continuous tip-in/tip-out correction in the intended stack.

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

DFCO and DFCO exit behavior can contaminate transient evidence. The plugin must classify or reject affected events.

Full-load pulls are not the primary way to tune MAP Predict. They are useful for safety context, boost behavior, and confirming that prediction has handed control back to measured MAP.

Never claim a full-load system is safe when required channels are missing.
