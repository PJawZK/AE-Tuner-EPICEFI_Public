# AE Tuner public roadmap

This roadmap is the public user/contributor view of AE Tuner (EPICEFI). Detailed engineering evidence and release authority remain in the private development repository.

## Current release state

- Previous public stable release: `v0.4.0`
- Current public candidate: **`v0.4.2-rc.1`**
- Candidate status: **full release candidate / public test release**
- RC JAR SHA-256: `500fb9f7b5f7cf79701b61af48c49c5ec58c0427a82758446e5d18dc61f219e6`
- Java target: Java 8 bytecode

A release candidate is not automatically promoted to stable because automated validation passes. External compatibility/usability feedback and any relevant additional physical evidence are part of the final disposition.

## R0 — Foundation and validation

Status: **DONE**

Established:

- deterministic Java 8-compatible builds;
- regression validation and synthetic TunerStudio/Swing integration;
- channel/runtime diagnostics;
- Passive event evidence and exports;
- Guided evidence architecture, audio cues and recovery;
- public Apache-2.0 source distribution without the separately licensed TunerStudio Plugin API binary.

## R1 — General Guided AE foundation

Status: **RC / PUBLIC TEST**

`0.4.2-rc.1` expands AE Tuner into a general transient-fuelling tuner with seven Guided areas:

1. AE Foundation
2. TPS AE
3. MAP Predict
4. Wall Wetting
5. Decel / Tip-out
6. Optional / Residual Correction
7. Review / Simplification

The full task map and coaching foundation are now exposed for public evaluation. Implemented tasks retain real evidence/review logic; planned tasks remain honest scaffolds and do not fabricate recommendations or write plans.

Public feedback is especially useful for navigation, Guided Focus clarity, controller/channel compatibility, layout, lifecycle/reconnect behaviour and evidence classification.

## R2 — Guarded proposal Apply / Restore

Status: **RC / BOUNDED**

The product supports explicit reviewed working-tune Apply/Restore only when a real `ProposalWritePlan` exists.

Current permanent boundaries:

- no automatic Apply;
- no Burn button/API;
- no hidden tune changes;
- exact declared write targets only;
- stale-baseline checks and readback verification;
- explicit verified Restore;
- VE and ignition remain outside tuning authority.

Individual setting representations are qualified progressively. The Detector Delta Window scalar path is part of the RC public-test surface; the nearby temporary-value example is not a tuning recommendation.

## R3 — Finish real Guided implementations

Status: **ACTIVE AFTER RC FEEDBACK**

Continue replacing planned task scaffolds with real evidence and recommendation logic, starting upstream where it provides the strongest foundation for everything downstream.

Likely priorities include:

- AE Foundation threshold/sensitivity behaviour;
- Engagement Validation / repeated-stab and reversal behaviour;
- TPS AE compensation/completion/validation;
- broader MAP Predict validation;
- advanced Wall Wetting tau/beta mapping;
- Decel / Tip-out detection, fuel shape and MAP prediction;
- Instant Fuel setup/event-strength/condition multipliers only where residual error justifies it;
- stack interaction review and simplification.

Task names/grouping remain provisional and may change when real implementation reveals a better controller abstraction.

## R4 — MAP Estimate maturity

Status: **ACTIVE FOUNDATION / MORE WORK PLANNED**

Continue improving:

- per-cell evidence quality and coverage;
- changed/excluded-cell transparency;
- calibration-state comparison;
- preservation of unvisited cells;
- proposal safety and applicability.

## R5 — Passive Analysis expansion

Status: **PLANNED AFTER GUIDED BASE**

The current priority is to finish a coherent Guided base first. Passive Analysis will then be expanded using the mature shared evidence/algorithm foundations rather than developing two partially overlapping systems in parallel.

## R6 — Public RC feedback and compatibility

Status: **ACTIVE**

Use `v0.4.2-rc.1` to collect broader EpicEFI/TunerStudio feedback on:

- plugin loading and Java/TunerStudio compatibility;
- project-setting resolution;
- output-channel resolution;
- UI layout across different screen/window sizes;
- Guided workflow clarity;
- lifecycle/reconnect stability;
- reports/evidence quality;
- deliberately tested supported Apply/readback/Restore paths.

Public issue attachments are public. Users should review logs/tunes/exports before posting and avoid sharing private information unintentionally.

## R7 — Stable 0.4.2 disposition

Status: **PLANNED**

After RC feedback:

1. fix demonstrated RC defects;
2. rerun deterministic and synthetic validation;
3. repeat any relevant physical/write-contract checks affected by fixes;
4. decide whether an `rc.2` is warranted or whether the line is ready for stable `0.4.2`;
5. publish exact source/JAR/checksum provenance.

## Deferred / not authorized

Automatic tune application and ECU Burn remain outside the current product scope. Either would require a separate safety architecture and explicit authorization before implementation.