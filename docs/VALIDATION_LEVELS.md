# Validation levels

AE Tuner separates ordinary development feedback from milestone and release evidence so validation does not become the development bottleneck.

## Level 1 — Fast development check

Command:

```bash
bash scripts/check-fast.sh
```

Purpose: cheap, high-signal feedback while implementing on a non-`main` branch.

Includes:

- deterministic compile/JAR assembly;
- source/POM/manifest identity checks;
- Java 8 bytecode target check;
- prohibition of burn APIs;
- enforcement that production `updateParameter(...)` remains centralized in `ProposalApplyCoordinator`;
- semantic MSQ verifier self-test;
- conflict/diff checks;
- compiled focused regressions covering runtime, Passive layout ownership, Guided lifecycle/UI/audio, comparability feedback, proposal policy, write plans and apply/restore safety.

GitHub Actions runs this automatically on non-`main` development pushes that change source, scripts or `pom.xml`.

No CI artifact is uploaded. The locally generated deterministic JAR is only an intermediate build product.

## Level 2 — Full validation

Command:

```bash
bash scripts/validate.sh
```

Purpose: milestone, PR and accepted-main regression proof.

Includes all source/write safety checks plus the complete behavioral/unit/architecture regression list, long-session characterization and validation-tooling regression.

The full suite remains the authority for capture behavior, proposal math, apply/restore safety, export serialization, runtime/lifecycle behavior and long-session bounds. The synthetic workspace gate below does not replace those tests.

GitHub Actions runs full validation for pull requests, accepted `main` pushes and manual dispatches.

The workflow records the deterministic JAR checksum in the run log but does not upload or retain a routine candidate artifact.

## Synthetic real-Swing workspace integration

Command, after Level 1 or Level 2 has prepared classes:

```bash
bash scripts/synthetic-plugin-integration.sh
```

Purpose: exercise the **current user-visible Swing workspace and responsive geometry** under Xvfb after the behavioral regressions have already been compiled/run by the appropriate validation level.

For post-M6B `0.4.2-dev.3`, this gate verifies the real plugin panel rather than preserving obsolete presentation assumptions. It checks:

- top-level `Overview / Guided Tuning / Passive Analysis / Evidence / Diagnostics` structure;
- no duplicate Overview navigation buttons;
- Passive `Overview / Setup / Calibration` structure;
- TPS noise calibration reachable in Passive setup;
- retired visible Passive `Technical details` tab absent;
- Evidence / Diagnostics `Overview / Channels / Runtime / Audio Cue Lab / Recovery / Audit` structure;
- Audio Cue Lab reachable on its dedicated tab;
- real Guided action-row geometry, including `Restore Previous Apply` and `Reconnect Guided worker`, at 1366, 1024 and 820 pixel frame widths;
- screenshot evidence for Overview, Passive setup, Audio Cue Lab and Guided Tuning.

The synthetic script does not invoke `validate.sh` implicitly and fails clearly if build/test prerequisites are missing.

The synthetic workspace gate intentionally does **not** duplicate the full behavioral regression suite or preserve retired UI solely to satisfy historical screenshot/layout assertions.

Synthetic evidence is checksum-characterized in the run workspace. Routine synthetic runs do not retain installable JAR artifacts.

## Level 3 — Physical candidate check

Command:

```bash
bash scripts/candidate-check.sh
```

Purpose: produce a build that is ready to be installed for a physical TunerStudio/vehicle test.

Runs:

1. full validation;
2. current synthetic real-Swing workspace integration;
3. deterministic single-JAR identity/checksum verification.

The explicit `Build physical candidate` workflow remains the place where a designated installable candidate JAR may be retained. Routine development and PR checks do not retain JAR artifacts.

For every newly writable proposal type, candidate/acceptance testing additionally requires real TunerStudio before/after `.msq` semantic verification with zero unexpected changes.

The first Predictive MAP Blend Duration Apply/Restore contract completed this requirement in M6B; that result does not automatically approve future writable recipes.

## Release/acceptance

Stable/private/public promotion remains stricter than ordinary CI. It requires the relevant full/candidate validation plus decisive real TunerStudio/vehicle evidence, exact source identity and exact accepted JAR SHA-256.

Do not use lack of a full/candidate run as a reason to stop ordinary implementation when the fast development check is the appropriate gate. Conversely, do not call a build a physical candidate or accepted release based only on the fast check.
