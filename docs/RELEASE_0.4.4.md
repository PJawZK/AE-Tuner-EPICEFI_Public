# AE Tuner (EPICEFI) 0.4.4

v0.4.4 is the stable public promotion of the validated `0.4.3-vehicle-test.31` runtime.

## Provenance

- Validated private runtime source: `23dfbfba15a99f453c242dd0c85bff4a7b1f3cbd`
- Public-identity build source: `3976f8e584efdfd9d0e4bd88b438098421596aba`
- Full validation: CI **#1511 / run `34564608676` — PASS**
- Validated Actions artifact: `ae-tuner-epicefi-main`, ID `10185577172`
- Artifact ZIP digest: `sha256:1aff39958e0efbdf2f8fdd5b35e648fca3147d3898f8adba756c1ba296ce40f4`
- Canonical public JAR: `ae-tuner-epicefi-0.4.4.jar`
- JAR SHA-256: `1a60283a4414167142fbd8a96584780caf52a7eb8cbaba2ae88b466016df1bfd`
- Java target: Java 8 bytecode

The public product source (`pom.xml`, `src/**`, and `scripts/**`) was verified content-identical to the validated public-identity source before publication. The public tree deliberately excludes the private TunerStudio Plugin API dependency and private development evidence.

## Main changes since v0.4.3

- New task-based Guided v0.19 production workspace and Focus surfaces.
- Strict method availability from the current Working Tune.
- Capture completion separated from task evidence readiness.
- Continue Capture preserves the same evidence window after an incomplete Finish.
- Method-owned transient-event attribution for TPS AE, Wall Wetting and Instant Fuel.
- Delayed lambda-response attribution instead of broad same-sample lambda attribution.
- Bidirectional acceleration/release critical sample preservation.
- Recommendation/model caching, MAP Focus invalidation and recovery/lifecycle performance hardening.
- Improved completed-evidence retention and runtime cleanup.

## Conservative tuning boundaries retained

- Wall Tau automatic movement is withheld until lambda transport delay is aligned.
- TPS closed-loop handoff automatic movement is withheld without authoritative EGO/trim re-entry evidence.
- Instant Fuel remains residual correction rather than a default first-line strategy.
- Numerical tuning conclusions still require physical vehicle evidence.

## Vehicle check before publication

Foundation 2 / Threshold & Sensitivity was exercised on the vehicle with this release-line workflow immediately before publication. The process behaved as intended. This supports release confidence in that workflow, but it is not a claim that every other method or recommendation has been physically validated.

## Safety

All supported writes remain explicit and use:

`ProposalWritePlan → ProposalApplyCoordinator → write → exact readback → Restore snapshot`

There is no production Burn path and no alternate writer.
