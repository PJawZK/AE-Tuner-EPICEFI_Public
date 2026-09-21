# AE Tuner (EPICEFI) 0.4.5

v0.4.5 is the public promotion target for the vehicle-tested `0.4.5-vehicle-test.16` runtime.

## Provenance

- Exact qualified private runtime source: `b9ec558a206408dd4e19cff8ce24b2eceb36f331`
- Private runtime merge: `b6b6eedb3669f4df2312d459a8284d498ddae41c`
- Private runtime qualification: CI **#1712 / run `35534865479` — PASS**
- Private post-merge qualification: CI **#1713 / run `35535012493` — PASS**
- Canonical private vehicle-test JAR: `ae-tuner-epicefi-0.4.5-vehicle-test.16.jar`
- Canonical private vehicle-test JAR SHA-256: `5b6738f29caa5a153ffbd1a8aeaa637321af50dcfc0340eb5c25114b31ac1860`
- Public runtime promotion commit: `5afb85c416a24026cde53e3148b4787c3a641837`
- Public version identity: `0.4.5` / `PUBLIC RELEASE`
- Canonical public JAR target: `ae-tuner-epicefi-0.4.5.jar`
- Final public qualification, artifact ID and JAR SHA-256 are recorded from the exact final PR head before publication.
- Java target: Java 8 bytecode

The public runtime source is a direct public-identity promotion of the qualified private runtime. The promotion changes release identity only; it does not introduce alternate tuning, firmware or write semantics.

## Main changes since v0.4.4

### AE Foundation live presentation

- TPS Foundation Focus now uses a rolling graph for absolute TPS, `Fuel: TPS AE change` and `AccelThreshold`.
- Threshold / Sensitivity uses the same rolling graph language for `TPS AE change` versus `AccelThreshold`.
- The rolling history is presentation-only and does not change controller sampling, evidence capture, recommendation or write semantics.

### Blend Duration physical timing authority

- Real MAP movement is now the primary physical timing authority for Blend Duration.
- A valid event stabilizes around the selected RPM region, opens the throttle, holds it steady and measures how long real MAP takes to move across the central 20→80% portion of its observed change.
- The measurement no longer requires predicted MAP to become the physical catch-up target.
- Predictive MAP, fallback MAP and current-tune Effective MAP replay remain useful diagnostic/context evidence, but they do not replace the real physical response measurement.
- No existing Blend curve value is used as the physical target.

### Completed series result and Result presentation

- Comparable completed events are grouped with RPM-bin identity as a hard comparability boundary.
- The median of a comparable group is used as the completed Blend series result.
- Result presentation was tightened for in-car readability and now distinguishes complete evidence from partial recommendation maturity rather than overstating readiness.

### Multi-bin road workflow

- A Blend session can arm **1–4 actual Blend Duration RPM bins from the current Working Tune**.
- The session automatically acquires the nearest armed bin inside the existing ±300 RPM entry/READY authority.
- A candidate must remain stable for **0.65 s** before it becomes the event's latched bin.
- Once latched, that RPM bin is immutable through the opening/measurement/outcome path.
- If READY validity disappears before the opening begins, the latch is released rather than silently migrating the event.
- After the opening begins, RPM no longer acts as a ceiling and the event cannot migrate to another bin.
- Evidence and completion progress are retained per RPM bin and are never merged across bins.
- One-bin sessions retain the established single-bin report/presentation behavior.

### Runtime/lifecycle refinement

- Presentation/passive background work is suspended while the plugin is hidden where safe to do so.
- Redundant runtime/UI compatibility residue was removed while preserving live Guided production capture, passive Foundation evidence capture, vehicle-test limits and Guided Audio Cue Lab functionality.

## Vehicle check before publication

The final 1–4-bin Blend selection and automatic latching workflow was exercised on the vehicle after software qualification. The workflow behaved as intended, including selecting and using the requested RPM-bin context instead of silently remaining on the previous/default bin.

This validates the workflow that was physically exercised. It does not make a blanket claim that every numerical recommendation or AE method is physically validated; matching vehicle `.mlg` evidence remains authoritative for tuning conclusions.

## Safety

All supported writes remain explicit and use:

`ProposalWritePlan → ProposalApplyCoordinator → write → exact readback → Restore snapshot`

There is no production Burn path and no alternate writer. VE and ignition tuning remain outside AE Tuner tuning authority.
