# Guided coaching foundation

Status: **Dev20 provisional interaction baseline**

This document is an authoritative product rule for AE Tuner Guided work. It does not grant tuning/write authority to tasks whose evidence model is still planned.

## Strict product rule

**Guided Capture / Guided Focus is a coach first.**

Its primary job is to tell the operator:

1. what needs to be done now;
2. how to perform the maneuver/coverage task;
3. whether the requested condition/action is being achieved;
4. when useful evidence has been obtained or why an attempt must be retried;
5. what the captured evidence says after the event;
6. when a one-setting-at-a-time A/B experiment or coverage follow-up is justified.

The preferred communication methods are task-specific combinations of:

- large plain action text;
- animated target corridors / traces / heat maps / timing ribbons;
- event freeze and automatic transient zoom;
- ghosted previous-run or baseline traces/envelopes;
- progress/maturity maps;
- event cards and synchronized replay;
- auditory READY / ACT / HOLD / RELEASE / CAPTURED / RETRY transitions;
- eyes-up modes where the driver should not need to read fast-changing diagnostics.

A Guided screen that primarily exposes controller settings, channel lists or explanatory prose is **not a finished Guided implementation**. Settings may remain available as secondary experiment controls when the evidence model justifies changing them.

## Driver View contract

Driver View is for obtaining evidence safely and consistently:

- no root scroll bar;
- no long paragraphs;
- no constantly changing layout height/width;
- live updates repaint values/graphics but must not steal viewport position;
- one dominant current action;
- only the visual targets needed to perform that action;
- sound cues should carry fast state transitions where that reduces eyes-down time;
- post-event detail belongs in event feedback/review, not in the maneuver screen.

Review/detail panes may scroll. A live ECU refresh must preserve any manually selected review viewport.

## Interaction archetypes

The provisional task catalog uses eight interaction families:

1. **Detector / classifier** — separate intentional movement from noise/stale history.
2. **Coverage map** — guide the user toward missing trustworthy operating cells.
3. **Controlled timing experiment** — coach a physical event and measure state-transition timing.
4. **Response-shape experiment** — compare fuel/correction amount and decay against lambda response.
5. **Paired / bidirectional experiment** — compare opposite-direction behavior such as wall-film tip-in/tip-out.
6. **Condition mapping** — repeat a credible base event across RPM/temperature/load regions.
7. **Validation suite** — run scripted maneuvers and generate pass/withhold event cards.
8. **Ownership / system review** — determine which transient mechanism owns the remaining response/error.

These are reusable interaction primitives, not mandatory identical layouts.

## Evidence lifecycle

A mature task should generally move through:

`SETUP -> BASELINE/READY -> COACHED ACTION -> EVENT/COVERAGE ACQUISITION -> AUTOMATIC CLASSIFICATION/FREEZE -> REPEAT -> REVIEW -> PROPOSAL -> EXPLICIT APPLY -> SAME TEST AGAIN -> A/B REVIEW -> KEEP OR RESTORE`

Some tasks are coverage-oriented rather than A/B-oriented and may accumulate evidence over many drives.

Capture never writes. No automatic Apply. No Burn.

## A/B and accumulated memory

Before/after authority is layered:

1. **Direct A/B evidence** — same explicit test protocol around one reviewed calibration change. Strongest evidence.
2. **Same-calibration accumulated evidence** — prior sessions whose relevant setting/firmware fingerprint still matches. Supports generalization/confidence.
3. **Historical/reference evidence** — older/different tune states. Context only unless the relevant calibration fingerprint can be proven equivalent.

Do not silently pool old tune revisions into the current baseline.

## Future-condition planning

Condition-dependent tasks must tell the user what cannot be obtained on the current run.

For coolant-temperature work, practical capture bands should be derived from the **actual working-tune correction axes**, not generic hard-coded temperatures. Guided should report missing future ranges early enough for the user to plan a cold-start/warm-up drive before the engine passes through them.

The same principle applies to RPM/load/gear/boost regions that require a later run.

## Current decisions retained in the proposal catalog

### TPS Movement / Timing

Normal Guided focus is `TPS movement -> Fuel: TPS AE change -> AccelThreshold`.

- Dual Stride / Newest is the accepted detector mechanism and remains read-only controller context in normal Guided UX.
- The completed Engagement Model comparison/editing research is not a normal user-facing tuning control and must not be resurrected.
- Fast Callback is prerequisite/context, not a normal AE Tuner tuning target. The intended workflow uses the approximately 200 Hz path.
- Delta Window is the meaningful current guarded A/B timing experiment.
- Sample Length is read-only context until an independent tuning rationale is established.

### MAP Estimate Table

Keep the heat-map/coverage model: Direct, bounded Interpolated, Conflict/Recheck, current-run evidence, learned memory and target-cell guidance.

### Blend Duration

Keep the controlled state machine and firmware-faithful final/upward-latched target semantics. Driver View should emphasize RPM/TPS target corridors and audio; detailed MAP/Effective-MAP/replay traces belong in automatic post-event freeze/review.

## Implementation discipline

`GuidedCoachCatalog` is a provisional design baseline so the whole product can be browsed early. Its text is **not** a recommendation engine.

For each task, before activating real recommendation/write authority:

1. inspect current EpicEFI firmware semantics;
2. inspect current INI/controller representation;
3. identify the physical evidence that can determine the parameter;
4. define the safest/clearest visual and auditory acquisition method;
5. define exclusions/confounders and comparability;
6. validate the evidence model against real logs/vehicle tests;
7. only then implement numerical recommendation logic and an exact `ProposalWritePlan` where justified.

The proposal catalog is expected to change repeatedly as these audits are completed.
