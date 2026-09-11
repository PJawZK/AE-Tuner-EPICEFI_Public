# AE Tuner current public state

- Public release: **`v0.4.4`**
- Previous public release: `v0.4.3`
- Exact validated private runtime source: `23dfbfba15a99f453c242dd0c85bff4a7b1f3cbd`
- Public-identity build source: `3976f8e584efdfd9d0e4bd88b438098421596aba`
- Public-identity validation: CI **#1511 / run `34564608676` — PASS**
- Canonical JAR: `ae-tuner-epicefi-0.4.4.jar`
- JAR SHA-256: `1a60283a4414167142fbd8a96584780caf52a7eb8cbaba2ae88b466016df1bfd`
- Validated artifact ID: `10185577172`
- Java target: Java 8 bytecode
- Public source/product files are content-identical to the validated public-identity source for `pom.xml`, `src/**` and `scripts/**`.

## Current product state

The normal Guided v0.19 workspace is the public presentation authority. Capture is read-only. Evidence-derived changes require a review-ready task and an explicit reviewed plan.

An operator may Finish a capture before enough evidence exists; that now remains **incomplete evidence** and may be continued in the same session with **Continue Capture**.

Foundation 2 / Threshold & Sensitivity received a final real-vehicle workflow check immediately before this release and the process behaved as intended. This is not a blanket physical-validation claim for every TPS AE, Wall Wetting, Instant Fuel, MAP Predict or decel recommendation.

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
