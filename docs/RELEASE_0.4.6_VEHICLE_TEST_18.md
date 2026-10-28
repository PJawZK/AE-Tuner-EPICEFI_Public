# AE Tuner v0.4.6-vehicle-test.18 — public testing preview

**Status: proposed public pre-release, not a stable release.** The stable public release remains **v0.4.5**. Publish the matching CI-verified JAR as a pre-release asset before promoting this document or presenting the download as available.

## What changed from v0.4.5

- Harder safeguards for attempted-write rollback, final evidence recovery, consistent snapshot/export packaging and lifecycle cleanup.
- Improved Guided interface: task Cancel / Start Over, Decel analysis progress/cancellation, responsive pop-outs, vertical scrolling, text and table wrapping, persistent action footers, better Blend/Threshold chart space, and a simpler Evidence Source dialog.
- Historical Decel analysis supports explicitly selected, detector-compatible cross-log physical-event pooling. Individual logs are classified independently before events are pooled. Unresolved upper-RPM cells remain **NEED EVIDENCE**; no extrapolation.
- Wall Wetting counts separately qualified pedal movements; Instant Fuel rejects late-response evidence without sufficient measurement coverage. These newer evidence policies still need matched physical-log acceptance.

## Software validation and limits

- **Exact version:** `0.4.6-vehicle-test.18`
- **Private integration source:** `9657f9afc6e45c75b3bcb85ef51fdf272e5eed30`
- **Hosted GitHub Actions:** Build and validate **#1931**, run `40699189924`, **PASS**
- **Canonical asset:** `ae-tuner-epicefi-0.4.6-vehicle-test.18.jar`
- **SHA-256:** `1177c7a00f4ae4509e00d7645431dc7064da9bcffdd0be35edb2ee17fdb8cbe7`
- **Java bytecode:** Java 8 compatible
- **Private TunerStudio API JAR:** not embedded
- **Real TunerStudio:** initial operator check confirms the new layouts work; broader vehicle, platform, numerical tuning and hardware qualification remain incomplete

Software validation is not proof that all numerical tuning recommendations are correct for all engines, firmwares or calibrations. The original Test-16 pooled Decel result and unresolved upper cells remain the reference evidence, not a universal preset.

## ECU safety boundary

Only explicit reviewed Working Tune/RAM Apply operations may use the existing centralized `ProposalWritePlan → ProposalApplyCoordinator → stale check → write → exact readback → rollback/Restore` path. There is **no Burn**, automatic Apply, or VE/ignition tuning authority. Planned/unfinished methods do not gain write authority by being visible.

## Installation and rollback

1. Download only the `.18` JAR from the matching **pre-release assets**, and verify against the SHA-256 above.
2. Fully close TunerStudio, move existing `ae-tuner-epicefi-*.jar` files out of the active plugin directory, install exactly one `.18` JAR and restart.
3. Confirm displayed version `0.4.6-vehicle-test.18` with **VEHICLE TEST BUILD** status.
4. Read Working Tune, test non-writing Guided workflows and report any errors with the plugin version, firmware, TunerStudio version and relevant diagnostics. Use controlled explicit Apply/Restore only where appropriate, and **do not Burn**.
5. Roll back by removing `.18`, restoring the existing stable public `0.4.5` JAR and restarting TunerStudio.

## Publication/provenance caution

This binary was produced from the **private development integration commit**, not from the public stable `main` source tree. Until a matching, independently sanitized public-source snapshot is published, the proposed GitHub pre-release must be clearly marked **binary-only preview**. Do not claim the GitHub-generated source archive attached to a tag on the stable tree reproduces this JAR.

The public stable `v0.4.5` GitHub Release and `main` remain unchanged. The test preview must be flagged `prerelease=true`, not presented as latest stable. Do not upload personal vehicle logs, tune files, proprietary `TunerStudioPluginAPI.jar`, source credentials or other private evidence.

## License

AE Tuner source and its JAR are released under Apache-2.0. The proprietary TunerStudio Plugin API is not redistributed.
