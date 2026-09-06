# AE Tuner (EPICEFI) repository instructions

## Authority

- Repository: `PJawZK/AE-Tuner-EPICEFI-`
- Authoritative branch: `main`
- Repository topology: one long-lived branch (`main`) only
- Current release-candidate version: `0.4.2-rc.2`
- `v0.4.2-rc.1` is superseded public-test provenance and must not be treated as current authority
- Exact final RC2 source/JAR identities become authoritative only after complete green validation of the final corrected private `main`

Temporary task branches may be used only when a bounded change requires them. Remove them after integration or abandonment. Do not keep candidate/phase branches as historical bookmarks; Git history, exact commit IDs, hashes, release records and closed PRs preserve provenance.

## Read first

1. `README.md`
2. `docs/CURRENT_STATE.md`
3. `docs/SAFETY_AND_SCOPE.md`
4. `docs/GENERAL_AE_TUNER_FRAMEWORK.md`
5. `docs/GUIDED_TUNING_PRODUCT_MAP.md`
6. `docs/GUIDED_COACHING_FOUNDATION.md`
7. `docs/EVIDENCE_INDEX.md`
8. `docs/ROADMAP.md`

Use version-specific acceptance/test documents only for historical provenance unless `CURRENT_STATE.md` explicitly promotes a conclusion from them.

## Product boundary

AE Tuner is a general transient-fuelling / acceleration-enrichment tuner for EpicEFI/TunerStudio. VE and ignition are outside tuning authority. Ignition may be observed only as context/confounder information.

Guided Tuning is coaching-first. Driver View should prefer compact visual/audio cues over walls of settings text, must not use a root scrollbar, and live UI updates must not jump the viewport. The task map is provisional and may be merged, split, renamed or reordered when implementation/evidence justifies it.

Planned tasks must never fabricate:

- evidence accumulation;
- recommendations;
- `ProposalWritePlan` objects;
- write authority.

## Write safety boundary

Explicit reviewed working-tune/RAM writes are allowed only through the centralized guarded proposal path and only when the active task owns the setting.

Required invariants:

- no automatic Apply;
- no Burn button or production Burn API;
- no hidden tune changes;
- only an explicit reviewed `ProposalWritePlan` may request a write;
- exact parameter/cell targets only;
- stale-baseline preflight before mutation;
- complete readback verification;
- explicit verified LIFO Restore;
- best-effort rollback and verification after partial failure;
- all production `updateParameter(...)` calls remain centralized in `host/ProposalApplyCoordinator.java`.

A newly used writable representation receives one real Apply/readback/Restore qualification when it first becomes a supported product write target. Do not repeat completed representation tests unless the representation/host contract changes materially.

Completed physical qualifications include Predictive MAP Blend Duration, MAP Estimate indexed table cells and Detector Delta Window scalar Apply/readback/Restore.

## AE Foundation — TPS Movement / Timing

The normal tuning question is:

`TPS movement -> Fuel: TPS AE change -> AccelThreshold`

Current controller context:

- detector: **Dual Stride / Newest**;
- Delta Window: **25 ms**;
- Sample Length: **50 ms**;
- Fast TPS callback: **approximately 200 Hz**.

Current product authority:

- Delta Window is the current guarded timing A/B setting;
- Engagement Model is read-only controller context and alternate detector models are not exposed;
- Sample Length is read-only context;
- Fast Callback is read-only prerequisite/information;
- the completed five-model comparison/editing experiment is research provenance, not normal Guided UX.

The Delta Window scalar representation has already physically passed `25 ms -> temporary value -> Apply/readback -> Restore 25 ms`. The temporary value was not a tuning recommendation.

## Evidence authority

Prefer, in order:

1. explicit same-protocol A/B evidence from the current relevant calibration;
2. accumulated evidence from the same relevant calibration state;
3. older tune revisions as reference unless the relevant fingerprint matches.

Physical vehicle evidence remains decisive for physical/tuning conclusions. Automated validation establishes software/build/safety properties, not numerical tune correctness.

Missing/unresolved data is not measured zero. Preserve raw values and use displayed TunerStudio/MegaLogViewer channel names in user-facing surfaces.

## Validation

Fast local development check:

```bash
bash scripts/check-fast.sh
```

Full repository regression/safety validation:

```bash
bash scripts/validate.sh
```

Real synthetic Swing/plugin exercise:

```bash
bash scripts/synthetic-plugin-integration.sh
```

The single permanent GitHub Actions workflow `.github/workflows/build.yml` runs full validation, synthetic integration, checksum reporting and validated-JAR artifact upload.

Do not claim a test, checksum, physical result, release or publication without matching evidence.

## Public distribution boundary

AE Tuner source/documentation is Apache-2.0. `lib/TunerStudioPluginAPI.jar` is a private development dependency and is not covered by the AE Tuner license; do not include it in public source/release distribution or inside the AE Tuner JAR.

The public `lib/` directory must contain dependency instructions only. Release JARs belong in GitHub Releases rather than being committed to the repository.

Before public publication, audit the exact public repository tree and release assets rather than copying the private repository blindly.
