# Public export provenance

This file records the relationship between the private engineering authority and the sanitized public distribution.

## Current release candidate — 0.4.2-rc.1

- Project: AE Tuner (EPICEFI)
- Public status: **release candidate / public test release**
- Version: `0.4.2-rc.1`
- Private authority repository: `PJawZK/AE-Tuner-EPICEFI-`
- Private authority RC commit: `778a6da72a5ec1d05bcc510ed8fca0c9439189c8`
- Private validation workflow run: `33160366938`
- Public downstream repository: `PJawZK/AE-Tuner-EPICEFI_Public`
- JAR: `ae-tuner-epicefi-0.4.2-rc.1.jar`
- JAR SHA-256: `500fb9f7b5f7cf79701b61af48c49c5ec58c0427a82758446e5d18dc61f219e6`
- Java bytecode target: `8`
- GitHub release tag: `v0.4.2-rc.1`

The RC was produced by promoting the validated Dev20 feature state to a release-candidate identity and rerunning the complete private validation gate. Full repository validation, static/write safety checks, synthetic real-plugin/Swing integration, layout checks and long-session characterization all passed before publication.

## Public source relationship

The public RC source is derived from the exact private RC product source while deliberately excluding private-only and third-party material.

The public distribution includes the AE Tuner product source, tests, deterministic build/validation scripts, Maven metadata, licensing files and selected public-facing design/safety documentation needed to understand the RC.

The public distribution intentionally excludes:

- `lib/TunerStudioPluginAPI.jar`;
- private vehicle logs and tune files;
- private generated evidence/recovery packages;
- private continuation and repository-control documents;
- credentials, registrations, secrets, private keys and personal machine paths.

The TunerStudio Plugin API is a separately licensed third-party dependency and is not redistributed with AE Tuner source or release JARs.

## RC write/safety boundary

`0.4.2-rc.1` supports explicit guarded working-tune Apply/Restore only for a real reviewed proposal with an exact `ProposalWritePlan`.

It does not provide:

- automatic Apply;
- ECU Burn;
- hidden tune mutation;
- VE tuning authority;
- ignition tuning authority.

The centralized Apply path uses declared targets, stale-baseline checks, readback verification and explicit Restore. A prior real TunerStudio whole-tune comparison verified the guarded mechanism with exactly one intended changed value and zero undeclared changes in that qualification test.

The Detector Delta Window scalar proposal path is included in this RC for public qualification. The nearby `25 -> temporary 24 -> restore 25 ms` example is a representation check only and is not a tuning recommendation.

## Previous public releases

The previous public stable release is `v0.4.0`:

- JAR: `ae-tuner-epicefi-0.4.0.jar`
- SHA-256: `1af45f58584b0dda8a8e2eb9b78ddfb09276f407b9264ee74bbf9408d54b13d8`
- release date: `2026-08-09`

The project also has an internal/accepted `0.4.1` development-history milestone that was not separately published as a public GitHub release before the broader `0.4.2-rc.1` public test line.

Historical public tags and release assets remain available for provenance and rollback.

## Licensing

AE Tuner source code and public project documentation are published under **Apache License 2.0**.

Third-party products and binaries, including the TunerStudio Plugin API, remain under their own applicable licences.