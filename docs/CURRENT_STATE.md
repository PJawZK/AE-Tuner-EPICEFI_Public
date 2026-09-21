# AE Tuner current public state

- Release target: **`v0.4.5`**
- Previous public release: `v0.4.4`
- Qualified private runtime identity: `0.4.5-vehicle-test.16`
- Exact qualified private runtime source: `b9ec558a206408dd4e19cff8ce24b2eceb36f331`
- Private runtime qualification: CI **#1712 / run `35534865479` — PASS**
- Private post-merge qualification: CI **#1713 / run `35535012493` — PASS**
- Canonical private vehicle-test JAR SHA-256: `5b6738f29caa5a153ffbd1a8aeaa637321af50dcfc0340eb5c25114b31ac1860`
- Public runtime promotion commit: `5afb85c416a24026cde53e3148b4787c3a641837`
- Public release candidate branch: `20260921-release-v045`
- Public candidate version: `0.4.5`
- Canonical public JAR target: `ae-tuner-epicefi-0.4.5.jar`
- Final public qualification/JAR SHA-256: to be recorded from the exact qualified public PR head before merge/release.
- Java target: Java 8 bytecode

## Current product state

The normal Guided v0.19 workspace remains the public presentation authority. Capture is read-only. Evidence-derived changes require a review-ready task and an explicit reviewed plan.

AE Foundation Focus uses rolling live graphs for TPS movement, `Fuel: TPS AE change` and `AccelThreshold` so the relevant controller behavior can be read together during a road session.

Blend Duration now treats real physical MAP response as the primary timing authority. Timing is measured over the central 20→80% portion of the observed MAP movement after a stable throttle opening. Predictive/fallback/current-tune replay remains diagnostic/context evidence rather than a physical catch-up target.

A Blend session may arm 1–4 actual Working Tune RPM bins. A stable armed bin is acquired with the existing ±300 RPM entry/READY authority and a 0.65 s dwell, then remains immutable through that event. Evidence/progress is retained per bin and never merged across RPM bins. After the opening begins, RPM is no longer a ceiling for the event.

Completed comparable Blend events use the comparable-group median as the completed series result. Result presentation distinguishes complete evidence from partial recommendation maturity.

The final multi-bin Blend selection/latching workflow was exercised on the vehicle and behaved as intended. This is a workflow validation statement, not a blanket claim that every numerical recommendation or AE method is physically validated.

## Safety / write authority

Supported working-tune writes remain centralized through:

`ProposalWritePlan → ProposalApplyCoordinator → write → exact readback → Restore snapshot`

Hard constraints:

- no Burn;
- no alternate writer;
- no hidden recipe-local mutation path;
- explicit reviewed plan required for evidence-derived changes;
- stale-baseline preflight;
- exact target scope;
- exact readback verification;
- rollback after partial failure;
- verified Restore.

Matching TunerStudio `.mlg` evidence remains the authority for physical engine response.
