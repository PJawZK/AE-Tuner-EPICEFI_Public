# vehicle-test.16 — Archive46 Foundation 2 physical acceptance

Date: 2026-09-06

This note records the physical evidence that motivated the reporting-only `.16` polish. The Foundation 2 estimator/validator behavior remains the `.15` method; `.16` does not change threshold math, event-quality constants, Apply/Restore behavior, or same-event counterfactual equations.

## Physical authority

Archive46 was captured with `0.4.3-vehicle-test.15` after CI run #1108 passed.

The complete lifecycle succeeded in the car:

`fresh A -> reviewed Apply -> verified Read Working Tune -> fresh B -> method evaluation -> KEEP -> KEEP_ACCEPTED`

Validation state:
- Evaluation performed: YES
- Method verdict: KEEP
- Resolution: KEEP_ACCEPTED
- no Restore
- no Burn

The physically accepted static threshold step was:

| Curve bin | Before | Applied / retained |
| --- | ---: | ---: |
| 800 RPM | 0.760 | 0.570 |
| 1600 RPM | 0.796 | 0.597 |
| 2400 RPM | 0.831 | 0.623 |
| 3200 RPM | 0.867 | 0.650 |

All four changed broad regions obtained fresh quiet lock, 3 Normal Corrections, 3 Acceleration Openings, valid effective separation, and zero newly triggered Normal Corrections.

## Same-event physical result

The same B maneuvers moved in the intended direction under the APPLIED threshold relative to the OLD counterfactual:

| Region | Normal median OLD -> APPLIED | Acceleration median OLD -> APPLIED | Separation gap |
| --- | ---: | ---: | ---: |
| <=1200 RPM | 0.036 -> 0.048 | 1.118 -> 1.257 | +0.584 |
| 1200-2000 RPM | 0.033 -> 0.043 | 1.243 -> 1.373 | +0.836 |
| 2000-2800 RPM | 0.049 -> 0.065 | 1.187 -> 1.321 | +0.696 |
| 2800-3650 RPM | 0.069 -> 0.091 | 0.821 -> 0.963 | +0.331 |

Across the changed regions:
- newly triggered Normal Corrections: 0
- lost Acceleration Opening threshold hits: 0
- Normal hit -> safe conversions: 0
- Acceleration miss -> hit conversions: 0

Archive46 also reproduced the driver-amplitude mismatch that invalidated `.14` as a causal A/B model. The 3200-region B Acceleration Openings were materially weaker than A, yet the `.15` same-event OLD-vs-APPLIED comparison still correctly measured the applied threshold's direction on those exact B maneuvers. This is retained as physical evidence for the same-event validation model.

## Quiet calibration evidence

Archive46 A quiet calibration:
- p95 1.063 %/s
- p99 1.474 %/s
- continuous quiet duration 1.930 s

Archive46 B quiet calibration:
- p95 1.354 %/s
- p99 2.235 %/s
- continuous quiet duration 1.501 s

Both satisfied the `.15` requirement of at least 80 qualifying samples and 1.5 continuous seconds. The different A/B quiet environments did not defeat same-event validation.

## Interpretation boundary

KEEP means the reviewed incremental step passed. It does **not** mean the region or the whole threshold curve is fully converged.

The B evidence produced a separate next incremental proposal for 800, 2400 and 3200 RPM while the 1600 value was already inside its event-backed separation window. Any such B-derived proposal is **NEXT INCREMENTAL PROPOSAL — NOT YET APPLIED** until another explicit guarded Apply occurs.

For multi-cell Apply, each per-region result is a **region verdict under the complete applied curve**. The run validates the combined reviewed Apply in those sampled regions; it does not claim isolated single-cell causality.

## `.16` reporting polish

`.16` therefore changes only operator/report semantics:
- remove legacy Ordinary/Deliberate terms from Foundation 2 user-facing method guidance
- use Normal Correction / Acceleration Opening consistently
- state quiet lock as 80 qualifying samples **and** 1.5 continuous seconds
- distinguish APPLIED PLAN from NEXT INCREMENTAL PROPOSAL — NOT YET APPLIED
- show per-region B Normal crossings, Acceleration hits, Acceleration misses and near-threshold openings
- state explicitly that KEEP is incremental acceptance, not convergence
- state multi-cell results as region verdicts under the complete applied curve

PR #38 remains open and unmerged unless explicitly instructed otherwise.
