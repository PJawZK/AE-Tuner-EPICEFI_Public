# Changelog

## 0.4.2-rc.1 — release candidate — 2026-08-28

Status: **RELEASE CANDIDATE / PUBLIC TEST**.

- Full release-candidate identity: `0.4.2-rc.1`.
- Private authority RC commit: `778a6da72a5ec1d05bcc510ed8fca0c9439189c8`.
- RC JAR: `ae-tuner-epicefi-0.4.2-rc.1.jar`.
- RC JAR SHA-256: `500fb9f7b5f7cf79701b61af48c49c5ec58c0427a82758446e5d18dc61f219e6`.
- Java target: Java 8 bytecode.
- Published as GitHub prerelease `v0.4.2-rc.1` for broader public testing and feedback.
- Reframes AE Tuner as a general transient-fuelling / AE tuner rather than a MAP Predict-only tool.
- Adds seven Guided Tuning areas: AE Foundation, TPS AE, MAP Predict, Wall Wetting, Decel / Tip-out, Optional / Residual Correction, and Review / Simplification.
- Adds a provisional Guided task map with local numbering inside each area.
- Adds a coaching-first foundation across the complete Guided task map while keeping planned tasks honest about incomplete evidence/recommendation logic.
- Preserves specialized real Guided behaviour where already implemented, including MAP Estimate and Detector Model / Timing Focus surfaces.
- Adds Engagement/Detection working-tune setting support for Engagement Model, Delta Window, Sample Length and Fast Callback through the shared guarded proposal/write architecture.
- Preserves the centralized explicit `ProposalWritePlan` Apply/readback/Restore contract.
- Automatic Apply remains prohibited.
- ECU Burn remains unavailable; there is no Burn button or production Burn API.
- VE and ignition remain outside AE Tuner tuning authority; ignition is observation/confounder context only.
- Carries forward Passive analysis, session exports, recovery/audit support, audio-led Guided operation, bounded Guided sample dispatch and long-session safeguards.
- Full private regression validation and synthetic real-plugin/Swing integration passed for the exact RC identity before publication.
- Detector Delta Window `25 -> temporary 24 -> verified Apply -> Restore 25` remains a bounded setting-representation qualification path; `24 ms` is not a tuning recommendation.
- Blend Duration numerical conversion remains withheld until its model is sufficiently validated.

## 0.4.1 — accepted internal project milestone — 2026-08-10

Status: **ACCEPTED PROJECT MILESTONE / NOT SEPARATELY PUBLISHED AS A PUBLIC GITHUB RELEASE**.

- Accepted private integration commit: `d396cb0a0c50770a31630ea95e89cd865c80470e`.
- Accepted JAR SHA-256: `8946d8b841285454550bfd0dc0929ef0be98306d01b08a171ce8781ff4a4851e`.
- Consolidated Passive and Guided session export workflows.
- Added safer staged session-folder publication and background serialization.
- Improved report ordering and normalized Guided event/diagnostic CSV output.
- Passed real TunerStudio use and automated promotion gates.
- This milestone was subsequently incorporated into the broader `0.4.2-rc.1` public line rather than receiving its own public release tag.

## 0.4.0 — stable public release — 2026-08-09

Status: **ACCEPTED / PUBLIC STABLE**.

- Public tag: `v0.4.0`.
- JAR: `ae-tuner-epicefi-0.4.0.jar`.
- JAR SHA-256: `1af45f58584b0dda8a8e2eb9b78ddfb09276f407b9264ee74bbf9408d54b13d8`.
- Promoted the physically validated `0.4.0-vehicle-test.12` Guided/runtime architecture.
- Added adaptive Guided Capture, audio cues, natural opening/plateau handling, comparability grouping and prediction-active Blend Duration measurement anchoring.
- Completed the major package/runtime decomposition and bounded Guided dispatcher architecture.
- Preserved Passive analysis and read-only tuning guidance in that stable line.

## 0.3.19-rc.1 — historical public release candidate — 2026-08-02

- Historical prerelease preceding the 0.4.x Guided architecture.
- Superseded by `v0.4.0` and later `v0.4.2-rc.1`.

## 0.3.18 — historical stable release

- JAR SHA-256: `2d22c6a11407eea744df3ca81524732f0c30de90cb4c2562eb4bd9456ec44828`.
- Superseded by later public releases.

Detailed development history remains available in Git history and the corresponding GitHub release notes.