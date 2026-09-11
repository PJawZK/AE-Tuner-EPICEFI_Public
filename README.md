# AE Tuner (EPICEFI)

AE Tuner is a TunerStudio plugin for EpicEFI transient-fuelling analysis and guided acceleration-enrichment tuning.

## Public release

Current release: **v0.4.4**  
Plugin: `ae-tuner-epicefi-0.4.4.jar`  
Java target: Java 8 bytecode  
SHA-256: `1a60283a4414167142fbd8a96584780caf52a7eb8cbaba2ae88b466016df1bfd`

v0.4.4 promotes the CI-validated `0.4.3-vehicle-test.31` runtime into the public release line. The previous public release was `v0.4.3`.

## Highlights

- The normal **Guided v0.19 workspace** now carries the current task-based workflow for AE Foundation, TPS AE, MAP Predict, Wall Wetting, Instant Fuel and decel/transient work.
- Capture lifecycle and evidence readiness are separate. Finishing too early no longer turns insufficient evidence into a review-ready result; **Continue Capture** resumes the same evidence window.
- TPS AE, Wall Wetting and Instant Fuel use method-owned transient-event evidence instead of broad same-sample attribution, including delayed lambda-response attribution where appropriate.
- Wall Tau automatic movement remains intentionally withheld until lambda transport delay is measured/aligned.
- TPS closed-loop handoff automatic movement remains intentionally withheld without authoritative EGO/trim re-entry evidence.
- Guided worker ownership, release/decel sample preservation, recommendation caching, MAP Estimate Focus invalidation and recovery/runtime behavior were hardened for long-running TunerStudio use.
- The Foundation 2 / Threshold & Sensitivity workflow received a final real-vehicle process check before publication and behaved as intended. Broader method/recommendation validation remains ongoing.

## Safety boundary

Supported tune changes are explicit **working-tune/RAM** Apply operations from a reviewed `ProposalWritePlan`, followed by exact readback and a verified Restore path. AE Tuner does **not** Burn ECU changes. VE and ignition tuning remain outside AE Tuner tuning authority.

Software validation and a successful workflow test do not imply that every numerical recommendation is physically validated. Vehicle `.mlg` evidence remains authoritative for tuning conclusions.

## Install

1. Download `ae-tuner-epicefi-0.4.4.jar` from the GitHub Release.
2. Remove older `ae-tuner-epicefi-*.jar` versions from the TunerStudio plugin directory.
3. Install the v0.4.4 JAR and restart TunerStudio.
4. Confirm AE Tuner reports version `0.4.4`.
5. Use **Read Working Tune** after enabling/disabling an AE method or after changing/applying/restoring tune settings.

## Building from source

The TunerStudio Plugin API JAR is a third-party dependency and is intentionally not redistributed in this repository. See `lib/README.md` for local build setup.

Full release details are in `CHANGELOG.md` and `docs/RELEASE_0.4.4.md`.
