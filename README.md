# AE Tuner for EPICEFI

AE Tuner is a TunerStudio plugin for EpicEFI transient-fuelling analysis and guided tuning.

## Current public candidate

**`0.4.2-rc.2` is the current full release candidate / public-test source.**

It supersedes `v0.4.2-rc.1` because RC1 still exposed temporary detector-research/editing surfaces that were not part of the intended product path. RC1 remains available as historical provenance.

Validated RC2 artifact identity:

- JAR: `ae-tuner-epicefi-0.4.2-rc.2.jar`
- SHA-256: `243f79de8fbfc93f1ef90754a9fc24d5e1c768338f3e75dc9e62a0a2855c6ea6`
- Validated private product source: `49b8e4cfad84050e31bf829e9006282b7f6fb960`
- Private validation workflow: `33170088930`
- Java target: Java 8 bytecode
- Safety boundary: explicit reviewed Apply/Restore only; **no automatic Apply and no Burn**

`v0.4.0` remains the previous public stable release while RC feedback is collected.

## Product direction

AE Tuner is a **general AE / transient-fuelling tuner**, not a MAP Predict-only tool.

Guided Tuning currently exposes seven product-level areas:

1. **AE Foundation**
2. **TPS AE**
3. **MAP Predict**
4. **Wall Wetting**
5. **Decel / Tip-out**
6. **Optional / Residual Correction**
7. **Review / Simplification**

The task map is intentionally evolvable. Implemented tasks retain real evidence/review behavior; planned tasks provide coaching scaffolds without fabricating recommendations or write authority.

## Guided is a coach first

Guided Capture / Guided Focus should primarily tell the operator what to do, how to perform the maneuver, whether the requested condition is being achieved, and when useful evidence has been obtained.

Driver-facing guidance uses task-appropriate combinations of action text, target corridors, timing/state cues, event freeze/replay, evidence maps, A/B comparison and audio cues. Settings are secondary to obtaining and understanding evidence.

Driver View has no root scrollbar, and live refreshes must not move the operator's viewport.

## AE Foundation — TPS Movement / Timing

The first Foundation task is now **TPS Movement / Timing**.

The normal tuning question is:

`TPS movement -> Fuel: TPS AE change -> AccelThreshold`

Current detector/timing authority:

- **Dual Stride / Newest** — accepted detector mechanism; read-only controller context
- **Delta Window** — current guarded timing A/B setting
- **Sample Length** — read-only context
- **Fast Callback** — read-only prerequisite/information; approximately 200 Hz is intended
- alternate detector models / five-model comparison — research tooling only, not normal Guided UX
- Engagement Model editing — removed from the product path

### Delta Window physical qualification — PASS

The scalar route has already passed a real TunerStudio working-tune qualification:

`25 ms -> temporary 24 ms -> Apply/readback PASS -> Restore 25 ms PASS`

`24 ms` was only a nearby representation-check value, not a tuning recommendation. No Burn was required.

## Apply / Restore boundary

AE Tuner may make an explicit operator-requested working-tune change only when the active task owns the setting and a reviewed `ProposalWritePlan` exists.

The shared guarded path requires:

- exact declared targets;
- stale-baseline checks before mutation;
- explicit user Apply;
- readback verification;
- explicit verified Restore;
- rollback handling after partial failure.

There is still:

- **no automatic Apply**;
- **no Burn button or Burn API**;
- no hidden tune changes;
- no VE tuning authority;
- no ignition tuning authority.

Ignition may be observed only as context/confounder information.

## Installation

When the `v0.4.2-rc.2` GitHub Release is available:

1. Download `ae-tuner-epicefi-0.4.2-rc.2.jar` from that release.
2. Keep your previous known-good JAR available for rollback.
3. In TunerStudio use **Tools → TunerStudio Plugins → Add or update plugin**, or your normal manual plugin-install method.
4. Ensure only one `ae-tuner-epicefi-*.jar` is installed.
5. Restart TunerStudio.
6. Open **AE Tuner (EPICEFI)** and confirm version `0.4.2-rc.2`.
7. Test audio cues while stationary before relying on them during a driving test.

The release JAR does not contain `TunerStudioPluginAPI.jar`.

## Public-test feedback

Useful reports include plugin loading/compatibility, controller-setting and channel resolution, Guided procedure clarity, layout/clipping, reconnect/lifecycle behavior, evidence classification, and deliberately tested supported Apply/readback/Restore paths.

GitHub attachments are public. Review logs, tune files and exported evidence before posting them.

## Building from source — developers only

The public source intentionally excludes the separately licensed TunerStudio Plugin API binary. Obtain an authorized copy and place it at:

```text
lib/TunerStudioPluginAPI.jar
```

Then run:

```bash
bash scripts/validate.sh
```

For the synthetic plugin-panel exercise:

```bash
bash scripts/synthetic-plugin-integration.sh
```

`dist/` is only a local generated-build staging directory. Generated JARs are ignored by Git; public binaries belong in GitHub Releases.

## Documentation

Public project documents include:

- `ROADMAP.md`
- `PUBLIC_PROVENANCE.md`
- `CHANGELOG.md`
- `docs/GUIDED_TUNING_PRODUCT_MAP.md`
- `docs/GENERAL_AE_TUNER_FRAMEWORK.md`
- `docs/GUIDED_COACHING_FOUNDATION.md`
- `docs/SAFETY_AND_SCOPE.md`

## Licence

AE Tuner source code and project documentation are licensed under the **Apache License 2.0**. See `LICENSE` and `NOTICE`.

The TunerStudio Plugin API and other named third-party products remain separate dependencies and are not included under the AE Tuner licence. See `THIRD_PARTY_NOTICES.md` and `lib/README.md`.
