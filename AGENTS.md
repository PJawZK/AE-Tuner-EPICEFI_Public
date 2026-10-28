# AE Tuner (EPICEFI) — public repository

- Canonical source branch: **main**
- Public source version: **0.4.6**; latest published release: **v0.4.5** until the v0.4.6 release assets are verified and published.
- Qualified source identity: `baddb47d3a13b2e772d5b6154f735f2f8c2ddb38`
- Private CI validation: #1932, run `40725785125`, PASS
- Canonical JAR: `ae-tuner-epicefi-0.4.6.jar`
- SHA-256: `31e23679f055789be0073f35e810f1ae793e0713dfc381c92e293c98ecdc19dd`
- Source/public-state details: `docs/CURRENT_STATE.md` and `docs/RELEASE_0.4.6.md`

The public Java source and scripts must track the exact built source; no proprietary API JAR or private vehicle logs/tunes/credentials may be published.

The plugin supports explicit, reviewed Working Tune/RAM proposals, exact-target readback, rollback and Restore through one centralized `ProposalApplyCoordinator` writer. **No automatic Apply, no Burn, no VE or ignition tuning.** Physical numerical acceptance is not implied by passing software CI.

TunerStudio API JAR is a separately licensed, locally supplied build dependency. Keep public GitHub releases to a verified canonical JAR and SHA256SUMS, with source on `main`.
