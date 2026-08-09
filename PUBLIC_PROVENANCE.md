# Public export provenance

This file records the relationship between the private engineering authority and the sanitized public distribution.

## Accepted 0.4.0 upstream authority

- Project: AE Tuner (EPICEFI)
- Accepted plugin version: `0.4.0`
- Private authority repository: `PJawZK/AE-Tuner-EPICEFI-`
- Public downstream repository: `PJawZK/AE-Tuner-EPICEFI_Public`
- Accepted private product-integration commit: `86178dc311df656567f0226e5a067ab460b93ffe`
- Private accepted-state/docs head used for publication packaging: `96e60c1ce2ff0a4d25e742266cb0827eaaaa38d5`
- Accepted JAR: `ae-tuner-epicefi-0.4.0.jar`
- Accepted JAR SHA-256: `1af45f58584b0dda8a8e2eb9b78ddfb09276f407b9264ee74bbf9408d54b13d8`
- Sanitized source publication ZIP: `AE-Tuner-EPICEFI_Public-0.4.0-source.zip`
- Sanitized source ZIP SHA-256: `be8dc04abbb24ba96601fc7251a0ef7e9ad506001be4b47919ffcbcdeed1e47e`
- Java build toolchain used for accepted deterministic reproduction: Eclipse Temurin `17.0.19+10`
- Java bytecode target: `8`
- ECU boundary: strictly read-only
- Acceptance date: `2026-08-09`

The accepted JAR checksum was reproduced locally from the exact accepted source/toolchain and matched the deterministic CI-built artifact before physical release-identity smoke testing.

## Physical/validation basis

Accepted `0.4.0` is the controlled stable promotion of the physically validated `0.4.0-vehicle-test.12` runtime/architecture behavior.

The accepted evidence chain includes:

- deterministic repository validation — PASS;
- real synthetic Swing/plugin-panel integration — PASS;
- exact deterministic JAR identity reproduction — PASS;
- stationary accepted-identity plugin smoke test — PASS;
- prior full stationary/road Guided + Passive physical validation of the promoted runtime — PASS;
- hide/reopen lifecycle and repeated Guided-session operation — PASS;
- prediction-active-only Blend Duration measurement-anchor validation — PASS;
- no ECU writes/burns and no automatic tune application.

No generated Blend Duration proposal/value became an accepted tune merely because the architecture/release gate passed.

## Public 0.4.0 publication state

- Preparation branch: `update/public-0.4.0`
- Intended stable branch after review/integration: `main`
- Intended stable release tag: `v0.4.0`
- Public source/JAR publication: **pending final file upload and integration at the time of this preparation record**

The final public publication commit/tag should be recorded here only after those identities actually exist. Do not invent or predeclare their commit SHA.

## Sanitization boundary

The public `0.4.0` source payload intentionally includes product source, regression tests, Maven identity, and deterministic build/validation tooling required for the public source distribution.

The public distribution intentionally excludes:

- `lib/TunerStudioPluginAPI.jar`;
- private vehicle logs, `.msl`/`.mlg` files, tune files, and generated evidence CSV/report packages;
- retained private candidate/recovery binaries;
- private continuation/handoff/authority documents that are not needed by public users;
- credentials, registrations, secrets, private keys, and personal machine paths.

AE Tuner source and public project documentation are published under **Apache-2.0**. The TunerStudio Plugin API binary is intentionally excluded and remains a separate third-party dependency.

## Historical stable publication — 0.3.18

The previous public stable baseline was `0.3.18`.

- Public release tag: `v0.3.18`
- Released JAR: `ae-tuner-epicefi-0.3.18.jar`
- Released JAR SHA-256: `2d22c6a11407eea744df3ca81524732f0c30de90cb4c2562eb4bd9456ec44828`
- Published source-tree commit: `6a34bf4cd15a76297cba20d415fb82b5c99ac9fc`
- Release tag source commit: `bda28a0d7e6928b7af67ff0a7d8adbd42bae565b`
- Original sanitized export ZIP SHA-256: `286006e6f20c5ae3e0bbb002310884a888cee927194761ce61563527c03415e8`
- Publication date: `2026-08-02`

`0.3.18` remains historical provenance but is superseded as the current accepted release by `0.4.0` once the prepared public `0.4.0` publication is integrated.
