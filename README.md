# AE Tuner for EPICEFI

AE Tuner is a TunerStudio plugin for EpicEFI transient-fuelling analysis and guided tuning.

## Current public build

**`v0.4.2-rc.1` is the current release candidate and is being published as a public test release.**

It is a full release-candidate build: the public source, plugin version, JAR filename, manifest identity and release tag all use `0.4.2-rc.1`. It is not yet being presented as the next stable release; `v0.4.0` remains the previous public stable release while RC feedback is collected.

Release-candidate artifact:

- JAR: `ae-tuner-epicefi-0.4.2-rc.1.jar`
- SHA-256: `500fb9f7b5f7cf79701b61af48c49c5ec58c0427a82758446e5d18dc61f219e6`
- Java target: Java 8 bytecode
- Source: public `main`
- Safety boundary: explicit reviewed Apply/Restore only; **no automatic Apply and no Burn**

Normal users do not need to compile anything. Download the JAR from the `v0.4.2-rc.1` GitHub Release.

## What changed since the previous public release

The project has moved from a MAP-Predict-focused workflow toward a **general AE / transient-fuelling tuner**. Guided Tuning now exposes seven product-level areas:

1. **AE Foundation**
2. **TPS AE**
3. **MAP Predict**
4. **Wall Wetting**
5. **Decel / Tip-out**
6. **Optional / Residual Correction**
7. **Review / Simplification**

Tasks are numbered locally inside each area. The task map is intentionally provisional and can evolve as controller behaviour and vehicle evidence justify better grouping.

The RC also carries forward the existing Passive analysis, MAP Estimate work, Guided evidence capture, audio cues, recovery, reports/exports, long-session protections and synthetic TunerStudio/Swing validation developed through the earlier releases.

## Guided Tuning maturity

This release candidate establishes a coherent Guided base across the whole AE product rather than pretending every task already has a finished tuning algorithm.

Implemented areas retain real evidence/review behaviour where it exists. Other tasks expose concrete coaching, relevant EpicEFI controls, required observations and future evidence requirements, but deliberately do **not** fabricate:

- evidence accumulation;
- tuning recommendations;
- `ProposalWritePlan` objects;
- write authority.

Useful specialized Guided Focus surfaces include the current MAP Estimate work and **AE Foundation → Detector Model / Timing**.

## Apply / Restore boundary

AE Tuner no longer has a blanket read-only product boundary. It can make an **explicit operator-requested working-tune change** only when the selected task has produced a real reviewed `ProposalWritePlan`.

The shared guarded path requires:

- exact declared target(s);
- baseline/stale-value checks before mutation;
- explicit user Apply;
- readback verification;
- explicit verified Restore;
- rollback handling after partial failure.

There is still:

- **no automatic Apply**;
- **no Burn button**;
- **no production Burn API**;
- no hidden tune changes;
- no VE tuning authority;
- no ignition tuning authority.

Ignition may be observed only as transient context/confounder information.

A previous real TunerStudio Apply/Restore isolation test verified one intended working-tune change with zero undeclared MSQ changes and a successful restore. Individual setting representations still need their own qualification as they become writable.

### Detector Delta Window public-test note

The Detector Model / Timing Focus includes the Delta Window scalar proposal path. The intended representation qualification is:

`25 ms -> temporary 24 ms -> Apply/readback -> Restore 25 ms`

`24 ms` is only a nearby representation-test value. It is **not** a recommended vehicle setting. This check does not require the engine to be running or a capture to be active.

## Installation

1. Download `ae-tuner-epicefi-0.4.2-rc.1.jar` from the `v0.4.2-rc.1` release.
2. Keep your previous known-good JAR available for rollback.
3. In TunerStudio use **Tools → TunerStudio Plugins → Add or update plugin**, or use your normal manual plugin-install method.
4. Ensure only one `ae-tuner-epicefi-*.jar` is installed.
5. Restart TunerStudio.
6. Open **AE Tuner (EPICEFI)** and confirm version `0.4.2-rc.1`.
7. Test audio cues while stationary before relying on them during a driving test.

The AE Tuner release JAR does not contain `TunerStudioPluginAPI.jar`.

## What feedback is useful

This public RC is specifically intended to broaden testing beyond the original development vehicle. Useful reports include:

- whether the plugin loads normally in your TunerStudio setup;
- EpicEFI firmware/controller and TunerStudio versions;
- whether working-tune settings and live channels resolve correctly;
- Guided Area → Task navigation and whether Guided Focus explains the intended procedure clearly;
- clipping/layout problems, especially at smaller window sizes;
- runtime or reconnect/lifecycle problems;
- unexpected evidence classification or coaching;
- Apply/readback/Restore behaviour **only if you deliberately choose to test a supported reviewed proposal**;
- screenshots, exported AE Tuner evidence, and concise reproduction steps.

Use the public issue templates where appropriate. GitHub attachments are public: review logs, tune files and exports before posting and remove anything you do not want publicly accessible.

Do not perform unsafe driving or force an exact road condition merely to create test data.

## Building from source — developers only

The public source intentionally excludes the third-party TunerStudio Plugin API binary. To compile locally, obtain an authorized copy from EFI Analytics or your own TunerStudio installation and place it at:

```text
lib/TunerStudioPluginAPI.jar
```

Then use:

```bash
bash scripts/validate.sh
```

For the synthetic plugin-panel exercise:

```bash
bash scripts/synthetic-plugin-integration.sh
```

Build output is written under `dist/`.

Do not redistribute `TunerStudioPluginAPI.jar` unless its separate licence expressly permits that use.

## Documentation

Useful project documents in the public tree include:

- `ROADMAP.md`
- `PUBLIC_PROVENANCE.md`
- `CHANGELOG.md`
- `docs/GUIDED_TUNING_PRODUCT_MAP.md`
- `docs/GENERAL_AE_TUNER_FRAMEWORK.md`
- `docs/GUIDED_COACHING_FOUNDATION.md`
- `docs/SAFETY_AND_SCOPE.md`

## Licence

AE Tuner source code and project documentation are licensed under the **Apache License 2.0**. See `LICENSE` and `NOTICE`.

The TunerStudio Plugin API and other named third-party products are separate dependencies and are not included under the AE Tuner licence. See `THIRD_PARTY_NOTICES.md` and `lib/README.md`.