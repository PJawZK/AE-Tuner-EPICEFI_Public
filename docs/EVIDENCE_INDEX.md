# Evidence index

Private logs, tune files, plugin exports, reports and videos are not committed. This file records controlled identities and conclusions that matter to the current product state.

## Current RC2 software state

Version: `0.4.2-rc.2`

Status: **final private validation / publication closure in progress**.

The exact final RC2 source SHA and deterministic JAR SHA-256 are authoritative only after the final corrected private `main` reaches a complete green permanent workflow. Earlier RC2 build hashes remain non-authoritative until reproduced by that exact final validated source.

Required final validation includes:

- full regression suite;
- static/write safety checks;
- real synthetic Swing/plugin integration;
- deterministic JAR checksum verification;
- existing width and long-session checks;
- validated artifact upload.

## Guided/product authority

AE Tuner is a general transient-fuelling / AE tuner.

Guided Capture / Guided Focus is coaching-first. Driver View must not use a root scrollbar or jump the viewport during live updates.

Current Guided areas:

1. AE Foundation
2. TPS AE
3. MAP Predict
4. Wall Wetting
5. Decel / Tip-out
6. Optional / Residual Correction
7. Review / Simplification

Passive Analysis is parked while Guided foundations mature.

## Guarded working-tune write foundation

The centralized Apply/Restore mechanism has real TunerStudio physical evidence across multiple representations.

### Predictive MAP Blend Duration — PASS

First physical write-isolation proof:

- source: `8060e1115be1ee20da78d094e643bc298302905a`
- JAR SHA-256: `7b0e634bce1168b3734bbf41c91314fccbbc55db93fc84a88df88c321d5ce593`
- workflow: `31402650370`
- selected cell baseline: 0.30 s at 1500 RPM
- explicit test Apply: `0.30 -> 0.08 s`
- 5,960 MSQ constants compared
- exactly one intended value changed
- zero undeclared changes
- Apply readback: PASS
- explicit Restore: `0.08 -> 0.30 s`
- Restore readback: PASS
- final tune matched original baseline across compared constants
- Burn: none

Detailed record: `docs/M6B_REAL_TUNERSTUDIO_WRITE_TEST.md`.

The 0.08 s value was a write-isolation value, not a tuning recommendation.

### MAP Estimate indexed table cells — PASS

Physical Apply/readback/Restore was completed successfully through the guarded working-tune path. No Burn was required.

### Detector Delta Window scalar — PASS

Physical scalar qualification was completed in the real TunerStudio working tune:

`25 ms -> temporary value -> Apply/readback PASS -> Restore 25 ms PASS`

No running-engine capture and no Burn were required. The temporary value was a representation check, not a tuning recommendation.

Sample Length and Fast Callback write experiments also physically worked during development, but those settings are now read-only context/prerequisite in normal product authority. Engagement Model representation research was completed, but Engagement Model editing itself was scrapped from the product path.

## TPS Movement / Timing detector evidence

Current controller/vehicle choice: **Dual Stride / Newest**.

Working context:

- Delta Window: 25 ms
- Sample Length: 50 ms
- Fast TPS callback: approximately 200 Hz

Official-firmware validation showed:

- `Fuel: AE window` = 50 ms
- `Fuel: AE window samples` = 10
- `Fuel: AE delta stride` = 5
- 5 samples × 5 ms = 25 ms
- `Fuel: TPS AE change` matched `Fuel: AE delta newest pair` at 99.9894% on running rows
- `ACCEL_TPS` tracked active TPS AE detector state

The current normal tuning question is:

`TPS movement -> Fuel: TPS AE change -> AccelThreshold`

Dual Stride/Newest is read-only controller context, not a user-selectable normal detector model. Delta Window is the current guarded timing A/B setting. Sample Length is read-only context. Fast Callback is read-only prerequisite/information. The completed five-model comparison is research provenance rather than normal Guided functionality.

## Superseded public RC1

`v0.4.2-rc.1` was the first public-test candidate and remains preserved as provenance.

- validated RC1 source: `778a6da72a5ec1d05bcc510ed8fca0c9439189c8`
- validated RC1 JAR SHA-256: `500fb9f7b5f7cf79701b61af48c49c5ec58c0427a82758446e5d18dc61f219e6`
- public RC1 source: `5c02477b20b532fc6e5f76a009e0fe2e2fa5e54d`

RC1 is superseded by RC2 because it retained temporary Engagement Model research/editing surfaces and its publication text incorrectly described Delta Window qualification as pending.

## Historical accepted release — 0.4.1

- accepted integration commit: `d396cb0a0c50770a31630ea95e89cd865c80470e`
- accepted JAR SHA-256: `8946d8b841285454550bfd0dc0929ef0be98306d01b08a171ce8781ff4a4851e`
- acceptance record: `docs/V0.4.1_ACCEPTANCE.md`
- disposition: ACCEPT, now historical beneath RC2 development

## Historical accepted release — 0.4.0

- accepted integration commit: `86178dc311df656567f0226e5a067ab460b93ffe`
- accepted JAR SHA-256: `1af45f58584b0dda8a8e2eb9b78ddfb09276f407b9264ee74bbf9408d54b13d8`
- acceptance record: `docs/V0.4.0_ACCEPTANCE.md`
- disposition: ACCEPT, superseded by 0.4.1

## Historical accepted baseline — 0.3.18

- accepted integration commit: `d1bb3ace7b0b7a7d645cb2718e7e36035bf8fb67`
- JAR SHA-256: `2d22c6a11407eea744df3ca81524732f0c30de90cb4c2562eb4bd9456ec44828`
- acceptance record: `docs/V0.3.18_ACCEPTANCE.md`

## Historical branch/artifact policy

The private repository no longer keeps historical candidate/development branch refs or retained candidate JARs merely as bookmarks.

Historical provenance is preserved by:

- immutable Git commits;
- exact JAR SHA-256 values;
- acceptance/test records;
- closed PRs/issues;
- external/private evidence identities.

Do not recreate deleted candidate branches solely for provenance.

## Evidence policy

- Physical vehicle evidence is decisive for tuning/physical acceptance.
- Automated validation can authorize software release/testing but does not approve numerical tune values.
- A generated proposal is not correct merely because software produced it.
- Preserve exact source/JAR/evidence identity for important dispositions.
- Prefer direct same-protocol A/B evidence before accumulated memory, and same-calibration memory before older tune history.
- Missing/unresolved channels remain unknown, not zero.
