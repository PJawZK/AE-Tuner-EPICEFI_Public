# AE Tuner (EPICEFI) public repository instructions

## Authority

- Repository: `PJawZK/AE-Tuner-EPICEFI_Public`
- Authoritative branch: `main`
- Current public release: **`v0.4.4`**
- Previous public release: `v0.4.3`
- Exact validated private runtime source: `23dfbfba15a99f453c242dd0c85bff4a7b1f3cbd`
- Exact public-identity build source: `3976f8e584efdfd9d0e4bd88b438098421596aba`
- Canonical v0.4.4 JAR SHA-256: `1a60283a4414167142fbd8a96584780caf52a7eb8cbaba2ae88b466016df1bfd`

Use `README.md`, `docs/CURRENT_STATE.md`, `docs/SAFETY_AND_SCOPE.md`, `docs/GUIDED_TUNING_PRODUCT_MAP.md` and `docs/RELEASE_0.4.4.md` as the first public references.

## Product boundary

AE Tuner is a general transient-fuelling / acceleration-enrichment tuner for EpicEFI/TunerStudio. VE and ignition are outside tuning authority; ignition may be observed only as context/confounder information.

Guided Tuning is coaching-first. Planned or immature tasks must never fabricate evidence, recommendations, `ProposalWritePlan` objects or write authority.

## Write safety boundary

Explicit reviewed working-tune/RAM writes are allowed only through the centralized guarded proposal path and only when the active task owns the setting.

Required invariants:

- no automatic final Apply;
- no Burn button or production Burn API;
- no hidden tune changes;
- only an explicit reviewed `ProposalWritePlan` may request a write;
- exact parameter/cell targets only;
- stale-baseline preflight;
- complete readback verification;
- explicit verified Restore;
- best-effort rollback and verification after partial failure;
- production `updateParameter(...)` calls remain centralized in `host/ProposalApplyCoordinator.java`.

Do not broaden write authority for release convenience.

## Evidence authority

Prefer, in order:

1. same-protocol physical A/B evidence from the current relevant calibration;
2. accumulated evidence from the same calibration state;
3. older revisions only as reference unless the relevant calibration fingerprint matches.

Software/CI validation establishes build, safety and behavior contracts. It does not make every numerical tuning recommendation physically correct.

## Public distribution boundary

AE Tuner source/documentation is Apache-2.0. `lib/TunerStudioPluginAPI.jar` is a third-party development dependency and must not be committed, attached to releases or embedded in the AE Tuner JAR.

Public binaries belong in GitHub Releases. Do not publish private logs, tune files, recordings, recovery packages, credentials or private-development artifacts.
