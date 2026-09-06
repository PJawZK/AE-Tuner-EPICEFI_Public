# Guided Tuning proposal-apply foundation — historical Dev3 record

Status: **HISTORICAL FOUNDATION + CURRENT VEHICLE-TEST VALIDATION ADDENDUM**

This document originated with the first guarded working-tune Apply/Restore implementation during the `0.4.2-dev.2/dev.3` development phase. The historical write-contract details remain engineering provenance; the current Foundation validation authority is summarized below.

## Guarded working-tune mutation architecture

The generic mutation architecture remains authoritative:

- reviewed immutable `ProposalWritePlan` objects declare exact targets, baselines and proposed values;
- all production working-tune mutation is centralized through `host/ProposalApplyCoordinator`;
- stale-baseline checks happen before mutation;
- only declared targets may change;
- readback verification is required;
- successful changes can be explicitly restored;
- partial failure triggers best-effort rollback/verification;
- capture itself never writes;
- automatic Apply and Burn remain prohibited.

The original M6B physical write contract remains PASS: Predictive MAP Blend Duration Apply `0.30 -> 0.08 s` and Restore `0.08 -> 0.30 s`, with one intended semantic change in each direction, verified readback and no Burn. The `0.08 s` value was a write-isolation test value, not a tuning recommendation.

## Foundation post-Apply validation — vehicle-test.15

Archive44 and Archive45 physically proved the `.14` Apply/readback/Restore lifecycle but exposed weaknesses in the Foundation 2 B validator and Driver UI. `0.4.3-vehicle-test.15` keeps the guarded write path and replaces the fragile parts of the experiment.

Lifecycle:

`A EVIDENCE / PROPOSAL -> EXPLICIT APPLY / READBACK -> READ WORKING TUNE -> FRESH B -> KEEP / RESTORE / INCONCLUSIVE`

The validation layer still performs no controller writes. KEEP records an operator decision only; RESTORE uses the existing verified Restore path; no Burn exists.

### Foundation 2 physical terminology

User-facing Foundation 2 terminology is physical rather than abstract:

- **Normal Correction** — a small natural pedal adjustment without trying to accelerate. AE should stay inactive.
- **Acceleration Opening** — a clear normal quick throttle opening intended to accelerate. AE should respond. A slow roll-in is not a representative acceleration-opening test.

Internal historical field names may remain for compatibility, but Driver View, reports and coaching use the physical terms.

### Quiet calibration

Quiet calibration must satisfy both a sample-count requirement and a continuous-time requirement. `vehicle-test.15` requires at least 80 quiet samples spanning at least 1.5 continuous seconds before the baseline freezes. A gap or non-quiet sample restarts the continuous quiet window.

This prevents a high callback rate from turning roughly one second of data into a supposedly mature baseline.

### Maneuver-quality rejection

Guided phase remains the semantic authority; event shape never silently relabels an event. Instead, obviously wrong physical attempts are rejected and the requested class is repeated.

Examples:

- a Normal Correction that is large and fast enough to resemble an acceleration request is rejected;
- an Acceleration Opening that is a slow roll-in is rejected;
- an extremely tiny weak Acceleration Opening is rejected.

Rejected attempts report the physical reason, peak TPS rate and TPS excursion so the operator knows what to change on the next attempt.

### Event counts are not separation PASS

`3 Normal Corrections + 3 Acceleration Openings` means only that the minimum event counts are complete. It is not a PASS by itself.

Effective separation remains a separate requirement. Driver View presents the two event counts separately from the separation state and reports the current separation gap versus the required gap. Important guidance is no longer painted inside small progress bars.

### Full A/B controller-context guard

The post-Apply Working Tune must match the A baseline everywhere relevant to Foundation 2 except the exact reviewed target cells. Validation is blocked if any non-target context changes, including:

- another `tpsAeThresholdValue` cell;
- threshold RPM axis;
- Dynamic Threshold enabled state;
- static/dynamic averaging state;
- Delta TPS smoothing alpha;
- Delta Window;
- Sample Length;
- controller configuration.

This prevents a different neighboring threshold cell or timing/context change from contaminating the experiment.

### Same-event OLD versus APPLIED counterfactual

A remains the proposal/evidence authority, but B no longer has to reproduce A pedal amplitudes closely enough for aggregate A-versus-B ratios to be meaningful.

For every accepted B maneuver, the validator calculates what the **old** effective threshold would have been for that exact same B event and compares it with the verified **applied** threshold.

With Dynamic Threshold off, the old effective threshold is the old interpolated static threshold.

With firmware static/dynamic averaging enabled:

`Effective = (Static + Dynamic) / 2`

so for the exact B event:

`OldEffective = NewEffective + (OldStatic - NewStatic) / 2`

This holds the event's inferred Dynamic component constant and isolates the reviewed static-cell step on the same pedal movement, RPM and operating event.

For a threshold-lowering step:

- a Normal Correction that was safe under OLD but crosses under APPLIED is a regression -> **RESTORE**;
- an Acceleration Opening that was a threshold hit under OLD but becomes a miss under APPLIED is a regression -> **RESTORE**;
- otherwise, if same-event Acceleration Opening ratios move toward AE activation with meaningful applied-cell authority -> **KEEP**.

For a threshold-raising step the mirror rule is used: same-event Normal Correction ratios must move away from AE activation without causing a previously detected Acceleration Opening to become a miss.

A fixed aggregate `+0.05` A/B ratio-improvement requirement is no longer used. The validator judges the actual effective-threshold authority of the applied cell at the sampled RPM.

### Restore provenance

A manual/safety Restore before a method evaluator produces RESTORE is not an algorithmic RESTORE verdict.

Validation exports distinguish:

- whether evaluation was performed;
- method verdict: `KEEP / RESTORE / INCONCLUSIVE / NOT_EVALUATED`;
- resolution: `KEEP_ACCEPTED / ALGORITHM_RESTORE / OPERATOR_ABORT_OR_OVERRIDE_RESTORE / UNRESOLVED`.

This prevents an operator abort from being recorded as if the method had proven the applied value wrong.

### Foundation 1 timing validation

Foundation 1 retains its passive post-Apply comparison. A fresh operating-range-confirmed timing result that retains the applied timing pair supports KEEP; a fresh result that explicitly resolves back to the previous pair recommends RESTORE; unresolved/different evidence remains INCONCLUSIVE.

## Vehicle-test boundary

`0.4.3-vehicle-test.15` is an internal vehicle-test candidate. Software/CI validation does not by itself claim physical acceptance. PR #38 remains open and unmerged until the revised Foundation validation behavior is accepted in the car.
