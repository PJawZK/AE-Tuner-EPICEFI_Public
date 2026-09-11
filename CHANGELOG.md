# Changelog

## 0.4.4 — 2026-09-11

Status: **PUBLIC RELEASE**. Replaces `v0.4.3`.

### Guided workflow and UI
- Promoted the v0.19 task-based Guided workspace as the normal production presentation.
- Added strict task/method availability routing from the current Working Tune.
- Separated capture lifecycle completion from task-specific evidence readiness.
- An early Finish now reports incomplete evidence rather than a misleading no-write completion.
- Added **Continue Capture** so insufficient evidence can continue in the same evidence window.
- Hardened first-click/refresh interaction behavior and Focus lifecycle handling.
- Retained completed evidence only after the task-specific readiness gate is satisfied.

### Transient evidence and recommendations
- TPS AE, Wall Wetting and Instant Fuel now use method-owned transient-event evidence rather than broad generic same-sample attribution.
- Added delayed lambda-response attribution for transient response analysis.
- Preserved release/decel transients under dispatcher pressure rather than protecting only acceleration openings.
- Consolidated Instant Fuel around pulse-event ownership and conservative residual-error handling.
- Wall Tau automatic movement remains withheld until transport/sensor delay is measured/aligned.
- TPS closed-loop inhibit/handoff automatic movement remains withheld without authoritative EGO/trim re-entry evidence.

### Runtime and performance
- Hardened plugin/worker lifecycle ownership and final static-reference cleanup.
- Added recommendation/model caching and TPS AE table-suggestion memoization.
- Reduced high-rate MAP Estimate model work and strengthened Focus live-cell invalidation.
- Coalesced/deduplicated recovery work and removed duplicated completed-evidence serialization.
- Expanded permanent real-Swing/Xvfb, long-session, lifecycle and Apply/Restore regression coverage.

### Validation boundary
- The public-identity source passed the complete private permanent pipeline as CI **#1511 / run `34564608676`**.
- Canonical JAR: `ae-tuner-epicefi-0.4.4.jar`.
- SHA-256: `1a60283a4414167142fbd8a96584780caf52a7eb8cbaba2ae88b466016df1bfd`.
- Foundation 2 / Threshold & Sensitivity received a final real-vehicle workflow check before release and the process behaved as intended.
- Other method/recommendation tuning conclusions still require matching vehicle `.mlg` evidence.

### Safety
- Guarded mutation remains centralized through `ProposalWritePlan → ProposalApplyCoordinator → write → exact readback → Restore snapshot`.
- No Burn path was added.
- No alternate writer was added.
- VE and ignition remain outside AE Tuner tuning authority.

---

## 0.4.3 — 2026-09-06

Status: **PUBLIC RELEASE**. Replaces `v0.4.2-rc.2`.

### AE Foundation
- Foundation 1 TPS Movement / Timing matured with the retained 40 ms Delta Window / 60 ms Sample Length workflow, passive capture/re-anchor behavior and driving-range evidence.
- Foundation 2 now uses Normal Correction / Acceleration Opening terminology and physical maneuver-quality rejection.
- Quiet calibration requires 80 qualifying samples and at least 1.5 continuous seconds.
- Event-count completion is separate from effective-threshold separation validity.
- Post-Apply validation guards the full relevant controller context and compares the same fresh B events under OLD vs APPLIED effective threshold behavior.
- Operator abort/restore provenance is separated from algorithmic RESTORE.
- Archive46 physically accepted the complete guarded lifecycle and retained 0.570 / 0.597 / 0.623 / 0.650 at 800 / 1600 / 2400 / 3200 RPM.

### MAP Predict / Blend Duration
- Retired the old largest-gap/T90 numerical Blend Duration conversion.
- Measurement now follows the final/upward-latched prediction-active `fallbackMap` target; later higher targets restart the measurement anchor.
- Physical MAP catch-up is measured to the exact final target and checked against current-curve Effective MAP replay.
- Physically valid events are retained and grouped by comparable RPM/load/TPS-step/gear conditions.
- Added dedicated Driver Focus: GET STEADY -> OPEN & SETTLE -> HOLD FOR MAP -> RESULT / REPEAT.
- Numerical Blend Duration proposal/write generation remains withheld pending physical validation of the corrected conversion.

### Safety, validation and UI
- Expanded firmware-authoritative AE setting inventory and guarded Apply/Restore validation coverage.
- Production mutation remains explicit, readback-verified and reversible; no automatic Burn path exists.
- Expanded recovery/export diagnostics, guided audio-cue verification and real Swing synthetic coverage at practical viewport widths.

---

## 0.4.2-rc.2 — release candidate — 2026-08-28

Status: **RELEASE CANDIDATE / PUBLIC TEST**.

- Full release-candidate identity: `0.4.2-rc.2`.
- Exact validated private product source: `49b8e4cfad84050e31bf829e9006282b7f6fb960`.
- Private validation workflow: `33170088930`.
- RC JAR: `ae-tuner-epicefi-0.4.2-rc.2.jar`.
- RC JAR SHA-256: `243f79de8fbfc93f1ef90754a9fc24d5e1c768338f3e75dc9e62a0a2855c6ea6`.
- Java target: Java 8 bytecode.
- Supersedes `v0.4.2-rc.1` for public testing because RC1 retained temporary detector-research/editing surfaces that were no longer part of the intended product path.
- Renames **Detector Model / Timing** to **TPS Movement / Timing**.
- Centers the normal Foundation Focus on `TPS movement -> Fuel: TPS AE change -> AccelThreshold`.
- Keeps Dual Stride / Newest as read-only controller context rather than a user-selectable tuning choice.
- Removes the normal five-model comparison and Engagement Model editing path.
- Keeps Sample Length as read-only context.
- Keeps Fast Callback as read-only prerequisite/information; approximately 200 Hz is the intended setup.
- Retains Delta Window as the current guarded timing A/B setting.
- Records the already-completed physical Delta Window qualification correctly: `25 ms -> temporary 24 ms -> Apply/readback PASS -> Restore 25 ms PASS`.
- Simplifies TPS Movement / Timing Driver View around coaching, removes the root scroll container, and hides the secondary Delta Window experiment controls in Driver View.
- Preserves the seven-area general AE / transient-fuelling product direction and coaching-first Guided foundation.
- Removes obsolete Engagement Model/Fast Callback write-routing regressions from the product source/tests.
- Keeps automatic Apply prohibited, explicit guarded Apply/Restore only, and no Burn authority.
- VE and ignition remain outside AE Tuner tuning authority.
- Full private regression validation, static/write safety, synthetic real-plugin/Swing integration, width checks, long-session characterization, deterministic checksum recording and artifact upload passed for the exact RC2 source before public export.

## 0.4.2-rc.1 — release candidate — 2026-08-28

Status: **SUPERSEDED PUBLIC TEST**.

- Private validated source: `778a6da72a5ec1d05bcc510ed8fca0c9439189c8`.
- Public source commit: `5c02477b20b532fc6e5f76a009e0fe2e2fa5e54d`.
- RC JAR: `ae-tuner-epicefi-0.4.2-rc.1.jar`.
- RC JAR SHA-256: `500fb9f7b5f7cf79701b61af48c49c5ec58c0427a82758446e5d18dc61f219e6`.
- Published as GitHub prerelease `v0.4.2-rc.1` for broader public testing and feedback.
- Reframed AE Tuner as a general transient-fuelling / AE tuner rather than a MAP Predict-only tool.
- Added seven Guided Tuning areas: AE Foundation, TPS AE, MAP Predict, Wall Wetting, Decel / Tip-out, Optional / Residual Correction, and Review / Simplification.
- Added a provisional Guided task map and coaching-first foundation.
- RC1 still retained temporary Engagement Model, Sample Length and Fast Callback editing/research surfaces that were removed from the intended RC2 product boundary.
- RC1 publication text also incorrectly described Delta Window physical qualification as pending even though the scalar Apply/readback/Restore test had already passed.
- RC1 remains available as provenance and rollback history; RC2 is the corrected public-test candidate.

## 0.4.1 — accepted internal project milestone — 2026-08-10

Status: **ACCEPTED PROJECT MILESTONE / NOT SEPARATELY PUBLISHED AS A PUBLIC GITHUB RELEASE**.

- Accepted private integration commit: `d396cb0a0c50770a31630ea95e89cd865c80470e`.
- Accepted JAR SHA-256: `8946d8b841285454550bfd0dc0929ef0be98306d01b08a171ce8781ff4a4851e`.
- Consolidated Passive and Guided session export workflows.
- Added safer staged session-folder publication and background serialization.
- Improved report ordering and normalized Guided event/diagnostic CSV output.
- Passed real TunerStudio use and automated promotion gates.

## 0.4.0 — stable public release — 2026-08-09

Status: **ACCEPTED / PUBLIC STABLE**.

- Public tag: `v0.4.0`.
- JAR: `ae-tuner-epicefi-0.4.0.jar`.
- JAR SHA-256: `1af45f58584b0dda8a8e2eb9b78ddfb09276f407b9264ee74bbf9408d54b13d8`.
- Promoted the physically validated `0.4.0-vehicle-test.12` Guided/runtime architecture.
- Added adaptive Guided Capture, audio cues, natural opening/plateau handling, comparability grouping and prediction-active Blend Duration measurement anchoring.
- Completed the major package/runtime decomposition and bounded Guided dispatcher architecture.

## 0.3.19-rc.1 — historical public release candidate — 2026-08-02

- Historical prerelease preceding the 0.4.x Guided architecture.
- Superseded by `v0.4.0` and later `v0.4.2` release candidates.

## 0.3.18 — historical stable release

- JAR SHA-256: `2d22c6a11407eea744df3ca81524732f0c30de90cb4c2562eb4bd9456ec44828`.
- Superseded by later public releases.

Detailed development history remains available in Git history and the corresponding GitHub release notes.
