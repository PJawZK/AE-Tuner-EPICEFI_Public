# AE Tuner public roadmap

This roadmap is the public user/contributor view of AE Tuner (EPICEFI). Detailed engineering evidence and release authority remain in the private development repository.

## Current release state

- Previous public stable release: `v0.4.0`
- Superseded public candidate: `v0.4.2-rc.1`
- Current corrected candidate: **`v0.4.2-rc.2`**
- Candidate status: **full release candidate / public test**
- Validated private product source: `49b8e4cfad84050e31bf829e9006282b7f6fb960`
- RC2 JAR SHA-256: `243f79de8fbfc93f1ef90754a9fc24d5e1c768338f3e75dc9e62a0a2855c6ea6`
- Java target: Java 8 bytecode

RC1 remains available as provenance, but RC2 supersedes it because RC1 retained temporary detector-research/editing surfaces and described the already-completed Delta Window physical qualification as pending.

## R0 — Foundation and validation

Status: **DONE**

Established:

- deterministic Java 8-compatible builds;
- regression validation and synthetic TunerStudio/Swing integration;
- channel/runtime diagnostics;
- Passive event evidence and exports;
- Guided evidence architecture, audio cues and recovery;
- guarded working-tune Apply/Restore infrastructure;
- public Apache-2.0 source distribution without the separately licensed TunerStudio Plugin API binary.

## R1 — General Guided AE foundation

Status: **RC / PUBLIC TEST**

`0.4.2-rc.2` exposes seven Guided areas:

1. AE Foundation
2. TPS AE
3. MAP Predict
4. Wall Wetting
5. Decel / Tip-out
6. Optional / Residual Correction
7. Review / Simplification

The product rule is **Guided is a coach first**. Driver-facing work should prioritize maneuver guidance, condition/coverage feedback, visual/audio cues and evidence review over walls of settings or diagnostics.

Planned tasks remain honest scaffolds until real evidence/recommendation/write authority exists.

## R2 — TPS Movement / Timing foundation

Status: **RC / ACTIVE**

The first Foundation task is **TPS Movement / Timing**.

Normal tuning question:

`TPS movement -> Fuel: TPS AE change -> AccelThreshold`

Current authority:

- Dual Stride / Newest — read-only controller context;
- Delta Window — current guarded timing A/B setting;
- Sample Length — read-only context;
- Fast Callback — read-only prerequisite/information; approximately 200 Hz intended;
- alternate detector models / five-model comparison — research only;
- Engagement Model editing — removed from product authority.

Delta Window physical qualification is complete:

`25 ms -> temporary 24 ms -> Apply/readback PASS -> Restore 25 ms PASS`

## R3 — Finish real Guided implementations

Status: **ACTIVE AFTER RC FEEDBACK**

Continue replacing planned task scaffolds with real coaching/evidence/recommendation logic, prioritizing upstream dependencies and avoiding duplicated correction authority.

Likely priorities include:

- Foundation threshold/sensitivity behavior;
- Engagement Validation / repeated-stab and reversal behavior;
- TPS AE compensation/completion/validation;
- broader MAP Predict validation;
- advanced Wall Wetting tau/beta mapping;
- Decel / Tip-out detection, fuel shape and MAP prediction;
- Instant Fuel setup/event-strength/condition multipliers only where residual error justifies it;
- stack interaction review and simplification.

## R4 — MAP Estimate maturity

Status: **ACTIVE FOUNDATION / MORE WORK PLANNED**

Continue improving:

- per-cell evidence quality and coverage;
- changed/excluded-cell transparency;
- calibration-state comparison;
- preservation of unvisited cells;
- proposal safety and applicability.

## R5 — Passive Analysis expansion

Status: **PARKED UNTIL GUIDED BASE MATURES**

Passive Analysis is intentionally parked while Guided receives credible coaching foundations across the major tuning areas. Passive can then reuse the mature shared evidence/algorithm foundations instead of duplicating partially developed logic.

## R6 — Public RC feedback and compatibility

Status: **ACTIVE**

Use `v0.4.2-rc.2` to collect broader EpicEFI/TunerStudio feedback on:

- plugin loading and Java/TunerStudio compatibility;
- project-setting resolution;
- output-channel resolution;
- UI layout across different screen/window sizes;
- Guided workflow clarity;
- lifecycle/reconnect stability;
- reports/evidence quality;
- deliberately tested supported Apply/readback/Restore paths.

Public issue attachments are public. Review logs/tunes/exports before posting.

## R7 — Stable 0.4.2 disposition

Status: **PLANNED**

After RC2 feedback:

1. fix demonstrated RC defects;
2. rerun deterministic and synthetic validation;
3. repeat physical/write-contract checks only if a relevant representation changed;
4. decide whether another RC is warranted or whether the line is ready for stable `0.4.2`;
5. publish exact source/JAR/checksum provenance.

## Permanent boundaries

Automatic tune application and ECU Burn remain outside the current product scope. VE and ignition remain outside AE Tuner tuning authority.
