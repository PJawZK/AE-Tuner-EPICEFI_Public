# Public export provenance

This file records the relationship between the private engineering authority and the sanitized public distribution.

## Current release candidate — 0.4.2-rc.2

- Project: AE Tuner (EPICEFI)
- Public status: **full release candidate / public test source**
- Version: `0.4.2-rc.2`
- Private authority repository: `PJawZK/AE-Tuner-EPICEFI-`
- Exact validated private product source: `49b8e4cfad84050e31bf829e9006282b7f6fb960`
- Private validation workflow run: `33170088930`
- Public downstream repository: `PJawZK/AE-Tuner-EPICEFI_Public`
- JAR: `ae-tuner-epicefi-0.4.2-rc.2.jar`
- JAR SHA-256: `243f79de8fbfc93f1ef90754a9fc24d5e1c768338f3e75dc9e62a0a2855c6ea6`
- Java bytecode target: `8`
- Intended GitHub release tag: `v0.4.2-rc.2`

The exact private source above passed the complete permanent validation workflow: full regression validation, static/write safety, synthetic real-plugin/Swing integration, 1366/1024/820 width checks, long-session characterization, deterministic checksum recording, and validated-JAR artifact upload.

The final deterministic JAR reproduced the same SHA-256 previously observed during intermediate RC2 builds, making that checksum authoritative for the validated source.

## Why RC2 supersedes RC1

`v0.4.2-rc.1` remains historical provenance, but RC2 supersedes it for public testing because RC1 retained temporary detector-research/editing surfaces that were no longer part of the intended product path and incorrectly described Delta Window qualification as pending.

RC2 corrects that product boundary:

- first Foundation task is **TPS Movement / Timing**;
- normal signal chain is `TPS movement -> Fuel: TPS AE change -> AccelThreshold`;
- Dual Stride / Newest is read-only controller context;
- Delta Window is the current guarded timing A/B setting;
- Sample Length is read-only context;
- Fast Callback is read-only prerequisite/information, with approximately 200 Hz intended;
- alternate detector models and the five-model comparison are not normal Guided functionality;
- Engagement Model editing is removed from the product path.

## Public source relationship

The public RC2 source is derived from the exact validated private product source while deliberately excluding private-only and third-party material.

The public distribution includes AE Tuner product source, tests, deterministic build/validation scripts, Maven metadata, licensing files, and selected public-facing design/safety documentation.

The public distribution intentionally excludes:

- `lib/TunerStudioPluginAPI.jar`;
- private vehicle logs and tune files;
- private generated evidence/recovery packages;
- private continuation and repository-control documents;
- credentials, registrations, secrets, private keys, and personal machine paths.

The TunerStudio Plugin API is a separately licensed third-party dependency and is not redistributed with AE Tuner source or release JARs.

## Write/safety boundary

`0.4.2-rc.2` supports explicit guarded working-tune Apply/Restore only for a real reviewed proposal with an exact `ProposalWritePlan` owned by the active task.

It does not provide:

- automatic Apply;
- ECU Burn;
- hidden tune mutation;
- VE tuning authority;
- ignition tuning authority.

Completed physical representation qualifications include:

- Predictive MAP Blend Duration;
- MAP Estimate indexed table cells;
- Delta Window scalar: `25 ms -> temporary 24 ms -> Apply/readback PASS -> Restore 25 ms PASS`.

Sample Length and Fast Callback write experiments also worked during development, but both are read-only in current product authority. Engagement Model representation research was completed, but Engagement Model editing itself is scrapped from the product path.

## Previous public releases

### v0.4.2-rc.1

Status: **SUPERSEDED PUBLIC TEST**.

- Private validated source: `778a6da72a5ec1d05bcc510ed8fca0c9439189c8`
- Public source commit: `5c02477b20b532fc6e5f76a009e0fe2e2fa5e54d`
- JAR SHA-256: `500fb9f7b5f7cf79701b61af48c49c5ec58c0427a82758446e5d18dc61f219e6`

RC1 is retained for provenance and rollback; RC2 is the corrected public-test candidate.

### v0.4.0

Previous public stable release:

- JAR: `ae-tuner-epicefi-0.4.0.jar`
- SHA-256: `1af45f58584b0dda8a8e2eb9b78ddfb09276f407b9264ee74bbf9408d54b13d8`
- release date: `2026-08-09`

## Licensing

AE Tuner source code and public project documentation are published under **Apache License 2.0**.

Third-party products and binaries, including the TunerStudio Plugin API, remain under their own applicable licences.
