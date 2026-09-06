# Decision log

This file contains the current decisions that future work should treat as authoritative. Superseded historical decisions remain available in Git history and version-specific acceptance records.

## D-001 — `main` is the single private development authority

Status: accepted  
Date: 2026-08-28

The private repository keeps one long-lived branch: `main`.

Temporary bounded task branches are allowed when useful, but must be removed after integration or abandonment. Candidate/phase branches are not retained as historical bookmarks; exact commits, checksums, closed PRs and evidence records preserve provenance.

## D-002 — AE Tuner is a general transient-fuelling / AE tuner

Status: accepted

The product is not limited to MAP Predict. Guided scope may include AE Foundation/detection, TPS AE, MAP Predict, Wall Wetting, Decel/Tip-out, optional residual correction and final review/simplification.

VE and ignition are outside tuning authority. Ignition may be observed only as context/confounder information.

## D-003 — Guided Tuning is coaching-first

Status: accepted

Guided Focus should tell the operator what to do and what the system is observing, using compact visual/audio cues where practical. Driver View is not a settings dashboard.

Driver View must not use a root scrollbar, and live updates must not cause scrolling or viewport jumps.

The task map is provisional. Real implementation/evidence may justify renaming, merging, splitting or reordering tasks.

## D-004 — Planned tasks cannot pretend to be implemented

Status: accepted

A planned/scaffold task may expose known controls, intended procedure and proposed coaching UX, but must not fabricate evidence accumulation, recommendations, `ProposalWritePlan` objects or write authority.

## D-005 — Explicit guarded Apply/Restore is allowed; automatic Apply and Burn are not

Status: accepted

A reviewed proposal may update supported TunerStudio working-tune/RAM values only through the centralized `ProposalApplyCoordinator` and only when the active task owns that setting.

Required properties:

- explicit operator action;
- immutable declared target/baseline/proposed values;
- stale-baseline preflight;
- exact target scope;
- complete readback verification;
- explicit verified LIFO Restore;
- rollback attempt after partial failure.

Automatic Apply, hidden writes and ECU Burn remain prohibited.

## D-006 — New writable representations require one physical qualification

Status: accepted

Software regression coverage is necessary but does not by itself prove that a newly used INI/controller representation behaves correctly in real TunerStudio.

A representation should receive one direct Apply/readback/Restore isolation test when it first becomes a supported write target. That test is not repeated unless the representation or host contract changes materially.

Physical qualification already completed includes:

- Predictive MAP Blend Duration;
- MAP Estimate indexed table cells;
- Detector Delta Window scalar: `25 ms -> temporary value -> Apply/readback PASS -> Restore 25 ms PASS`.

Sample Length and Fast Callback write experiments also worked during development, but those controls are now read-only context/prerequisite in normal product authority. Engagement Model representation research was completed but the editing feature itself was scrapped from the product path.

## D-007 — Physical evidence is decisive for tuning conclusions

Status: accepted

Evidence authority order:

1. direct same-protocol A/B from the current relevant calibration;
2. accumulated evidence under the same relevant calibration state;
3. older tune revisions only as reference unless the relevant calibration fingerprint matches.

Automated tests validate software properties, not numerical tune correctness.

## D-008 — Preserve unknown/missing data semantics

Status: accepted

Missing, unavailable, unresolved, blank and measured zero are distinct states. Preserve raw values. User-facing surfaces use displayed TunerStudio/MegaLogViewer names.

Unvisited MAP Estimate cells may not be rewritten as though they were measured. Unsupported/ineligible proposal points remain unchanged unless an explicit validated policy says otherwise.

## D-009 — TPS Movement / Timing uses Dual Stride / Newest as controller context

Status: accepted for the current vehicle/controller evidence

Working context:

- detector mechanism: Dual Stride / Newest
- Delta Window: 25 ms
- Sample Length: 50 ms
- Fast TPS callback: approximately 200 Hz

Normal Guided authority is:

- TPS movement -> `Fuel: TPS AE change` -> `AccelThreshold` is the tuning question;
- Delta Window is the current guarded timing A/B setting;
- Engagement Model is read-only context and alternate detector models are not exposed;
- Sample Length is read-only context;
- Fast Callback is read-only prerequisite/information.

The temporary value used during Delta Window representation qualification must not be mistaken for a tuning recommendation.

## D-010 — Public distribution excludes the TunerStudio Plugin API binary

Status: accepted

AE Tuner source/documentation uses Apache License 2.0.

`lib/TunerStudioPluginAPI.jar` is a private development dependency, is not covered by AE Tuner's license, and must not be included in the public repository, release assets or inside the AE Tuner JAR.

Public publication requires an explicit sanitized tree audit rather than blindly mirroring the private repository.

## D-011 — RC2 is a full release candidate published as a public test release

Status: accepted

`v0.4.2-rc.2` is the corrected current release-candidate identity. It is published as a public test release so external users can provide compatibility, UI and Guided feedback while later Guided tasks continue to mature.

`v0.4.2-rc.1` remains historical provenance but is superseded because it retained temporary detector-research/editing surfaces and incorrectly described Delta Window physical qualification as pending.

Release wording must not imply that planned Guided tasks are mature tuning authority.
