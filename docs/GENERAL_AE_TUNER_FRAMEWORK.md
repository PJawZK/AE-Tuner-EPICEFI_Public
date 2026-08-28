# General AE Tuner framework — RC2 authority

Status: **ACTIVE RC2 PRODUCT FRAMEWORK**

This document records the current product boundary for AE Tuner. Earlier Dev20 detector-research decisions remain available in Git history but are not current product authority.

## Product boundary

AE Tuner is a general transient-fuelling / acceleration-enrichment tuner. It is not a MAP Predict-only tool.

Current Guided Tuning areas are:

1. AE Foundation
2. TPS AE
3. MAP Predict
4. Wall Wetting
5. Decel / Tip-out
6. Optional / Residual Correction
7. Review / Simplification

VE and ignition remain outside AE Tuner tuning authority. Out-of-scope systems may be observed as evidence or confounder context, but AE Tuner must not turn that observation into write authority.

Passive Analysis is parked while Guided develops credible coaching foundations across the major tuning areas.

## Guided is a coach first

Guided Capture / Guided Focus should primarily tell the operator:

- what to do;
- how to perform the maneuver or coverage task;
- whether the requested condition is being achieved;
- when useful evidence has been obtained;
- what the evidence shows after the event;
- when a bounded A/B experiment or follow-up capture is justified.

Preferred interaction uses task-appropriate combinations of strong animated visuals, concise action text, audio/eyes-up cues, target corridors, timing/state ribbons, automatic event freeze/replay, ghosted A/B traces, evidence maps and event cards.

Editable settings are secondary to obtaining and understanding evidence.

Driver View must not use a root scrollbar. Live ECU/UI refreshes must not move the viewport or steal the operator's position.

## Write and safety authority

Capture and analysis never write automatically.

A Guided task may use the shared working-tune Apply/Restore mechanism only for a setting that the task currently owns and for which it has an explicit reviewed change. The write path remains centralized through `ProposalApplyCoordinator` and requires the existing stale-baseline and readback protections.

Current invariants:

- no automatic Apply;
- no Burn button or production Burn authority;
- no hidden tune changes;
- exact declared `ProposalWritePlan` target scope;
- stale-baseline checks before mutation;
- readback verification after mutation;
- explicit verified Restore;
- rollback attempt after partial failure;
- fresh Read Working Tune after successful Apply/Restore before new evidence capture;
- VE and ignition are never AE Tuner write targets.

The generic working-tune mutation mechanism has already been physically proven. A newly supported controller representation needs one bounded Apply/readback/Restore qualification when it first becomes a real product write target; it does not need repeated qualification afterward unless the representation or host contract changes.

## AE Foundation — TPS Movement / Timing

The first Foundation task is **TPS Movement / Timing**.

Its normal tuning question is:

`TPS movement -> Fuel: TPS AE change -> AccelThreshold`

Authoritative current detector state:

- detector mechanism: **Dual Stride / Newest**;
- Delta Window: **25 ms**;
- Sample Length: **50 ms**;
- fast TPS callback: **approximately 200 Hz**.

Product authority is intentionally narrower than the earlier detector research surface:

- **Engagement Model** — read-only controller context. AE Tuner does not ask the normal user to choose or tune alternate detector models.
- **Dual Stride / Newest** — the accepted detector mechanism for the current controller setup, shown as context rather than as a tuning choice.
- **Delta Window** — the current guarded timing A/B setting.
- **Sample Length** — read-only context until an independent tuning rationale is established.
- **Fast Callback** — read-only prerequisite/information. Approximately 200 Hz is the intended setup; AE Tuner explains this requirement but does not tune controller scheduling.
- **Five-model comparison** — completed research tooling, not normal Guided functionality.

The newest-pair diagnostic may remain available as read-only verification/sanity evidence. It is not a competing user-selectable detector algorithm in normal Guided UX.

## Delta Window physical qualification — PASS

The Delta Window scalar route has already been physically qualified in the real TunerStudio working tune:

`25 ms -> temporary 24 ms -> Apply/readback PASS -> Restore 25 ms PASS`

No engine-running capture and no Burn were required. The 24 ms value was only a nearby representation-check value, not a tuning recommendation.

Do not repeat this qualification unless the controller/host representation changes materially.

## Parameter catalog and task ownership

`host/AeTuningParameterCatalog` records controller settings that belong to the broader AE/transient-fuelling domain and their dependency relationships.

Catalog membership does **not** by itself mean the current Guided task is allowed to propose or write the setting. Product/task authority remains explicit and evidence-driven.

A task becomes a credible functional tuner only when it has:

1. understood controller/firmware semantics;
2. mapped the real TunerStudio representation;
3. defined required/context evidence and rejection rules;
4. implemented suitable coaching and evidence acquisition;
5. implemented bounded recommendation/A-B logic where justified;
6. exposed an exact reviewed `ProposalWritePlan` only for settings it owns;
7. qualified any newly used write representation once;
8. validated the behavior against real evidence.

Planned task scaffolds must not fabricate recommendation logic, evidence authority or write plans.

## Evidence compatibility

Tuning evidence should be ranked as:

1. direct same-protocol A/B evidence under the current relevant calibration;
2. accumulated evidence from the same relevant calibration state;
3. older/different tune evidence as reference unless the relevant calibration fingerprint is demonstrably equivalent.

Changes to materially upstream AE settings can invalidate dependent downstream evidence. Evidence from incompatible configurations must not be silently pooled.

## Current Foundation evidence contract

TPS Movement / Timing should retain enough evidence to understand intentional pedal movement, detector response and threshold behavior, including as available:

- TPS;
- production `Fuel: TPS AE change`;
- `AccelThreshold`;
- Dual Stride/Newest verification diagnostics;
- actual AE window and stride;
- threshold-active state;
- downstream AE/MAP Predict activity as context.

The coached/review questions include:

- does intentional TPS movement produce a prompt detector response?
- does the detector clear when movement stops?
- does it clear correctly through reversal/partial lift?
- does a fresh reapply re-arm cleanly?
- are stacked short events separated rather than retained as stale history?
- does detector output cross `AccelThreshold` when intended without excessive false activation?

Delta Window may be tested as a one-setting-at-a-time baseline/change/repeated-maneuver A/B experiment. There is no automatic recommendation or automatic Apply.

## Validation boundary

RC2 validation must preserve:

- the seven-area general-AE product model;
- coaching-first Guided behavior;
- TPS Movement / Timing terminology and signal chain;
- Dual Stride / Newest as read-only detector context;
- Delta Window as the only current editable Foundation timing A/B setting;
- Sample Length and Fast Callback as read-only context/prerequisite;
- no normal five-model comparison or Engagement Model editor;
- completed Delta Window physical qualification as PASS;
- centralized guarded Apply/Restore only;
- no automatic Apply and no Burn;
- VE and ignition outside tuning authority;
- deterministic full regression and synthetic real-plugin/Swing validation.

Historical Dev20 experiments remain useful engineering provenance in Git history, but they must not be mistaken for the current RC2 product contract.
