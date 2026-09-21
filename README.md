# AE Tuner (EPICEFI)

AE Tuner is a TunerStudio plugin for EpicEFI transient-fuelling analysis and guided acceleration-enrichment tuning.

## Public release candidate

Release target: **v0.4.5**  
Plugin: `ae-tuner-epicefi-0.4.5.jar`  
Java target: Java 8 bytecode  
Final SHA-256: recorded from the exact public qualification artifact before merge/release.

v0.4.5 promotes the vehicle-tested `0.4.5-vehicle-test.16` runtime into the public release line. The previous public release is `v0.4.4`.

## Highlights

- The normal **Guided v0.19 workspace** remains the production presentation authority for AE Foundation, TPS AE, MAP Predict, Wall Wetting, Instant Fuel and decel/transient work.
- AE Foundation Focus now uses rolling live graphs so TPS movement, `Fuel: TPS AE change` and `AccelThreshold` can be read together during a road session without changing controller semantics.
- Blend Duration measurement now uses the **real physical MAP 20→80% response** as primary timing evidence. Predictive/fallback/current-tune replay remains diagnostic/context evidence rather than the physical target.
- Completed comparable Blend events use the group median as the series result, with clearer Result presentation and truthful recommendation maturity when evidence is only partial.
- A Blend session can arm **1–4 actual Working Tune RPM bins**. The plugin automatically acquires a stable armed bin, requires a 0.65 s dwell, latches that bin for the event, keeps evidence separated per bin, and releases after outcome/recovery.
- RPM is an entry/READY condition only for this workflow. Once the throttle opening begins, the event keeps its latched RPM bin and no post-opening RPM ceiling is introduced.
- Hidden/presentation lifecycle work was reduced while the plugin is not visible, without changing tuning or write semantics.
- The final multi-bin Blend capture/latching workflow was exercised on the vehicle and behaved as intended.

## Safety boundary

Supported tune changes are explicit **working-tune/RAM** Apply operations from a reviewed `ProposalWritePlan`, followed by exact readback and a verified Restore path. AE Tuner does **not** Burn ECU changes. VE and ignition tuning remain outside AE Tuner tuning authority.

The authoritative mutation path remains:

`ProposalWritePlan → ProposalApplyCoordinator → write → exact readback → Restore snapshot`

Software validation and a successful workflow test do not imply that every numerical recommendation is physically validated. Vehicle `.mlg` evidence remains authoritative for tuning conclusions.

## Install

1. Download `ae-tuner-epicefi-0.4.5.jar` from the v0.4.5 GitHub Release once published.
2. Remove older `ae-tuner-epicefi-*.jar` versions from the TunerStudio plugin directory.
3. Install the v0.4.5 JAR and restart TunerStudio.
4. Confirm AE Tuner reports version `0.4.5` and `PUBLIC RELEASE` identity.
5. Use **Read Working Tune** after enabling/disabling an AE method or after changing/applying/restoring tune settings.

## Building from source

The TunerStudio Plugin API JAR is a third-party dependency and is intentionally not redistributed in this repository. See `lib/README.md` for local build setup.

Full release details are in `CHANGELOG.md` and `docs/RELEASE_0.4.5.md`.
