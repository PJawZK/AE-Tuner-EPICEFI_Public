# Current public release

**AE Tuner v0.4.6** is the latest published stable release (28 October 2026). It supersedes v0.4.5.

- [Download v0.4.6 and checksums](https://github.com/PJawZK/AE-Tuner-EPICEFI_Public/releases/tag/v0.4.6)
- Plugin: `ae-tuner-epicefi-0.4.6.jar`
- SHA-256: `31e23679f055789be0073f35e810f1ae793e0713dfc381c92e293c98ecdc19dd`
- Java 8-compatible bytecode; TunerStudio displays `0.4.6` / `PUBLIC RELEASE`
- Public source and distribution branch: `main`
- [What changed since v0.4.5](RELEASE_0.4.6.md)

## Capabilities

The Guided workspace covers AE Foundation, TPS AE, MAP Predict, Wall Wetting, Instant Fuel and Decel/Tip-out. v0.4.6 adds compatible historical Decel multi-log evidence pooling, UI usability improvements, and reliability/performance hardening.

Not all tasks offer the same recommendation maturity or controller write authority. Decel RPM regions without sufficient evidence remain `NEED EVIDENCE`.

## Safety and qualification

Supported changes are explicitly reviewed Working Tune/RAM operations with stale-baseline checks, exact readback, and guarded rollback/Restore. No automatic Apply, ECU Burn, or VE/ignition writes.

The published binary passed the release identity and software safety/regression pipeline; this does not imply universal physical calibration correctness. See [known limitations](KNOWN_ISSUES.md).

## Release integrity

The published JAR is the CI-qualified 0.4.6 binary (private qualification source `baddb47d3a13b2e772d5b6154f735f2f8c2ddb38`, CI #1932 / run `40725785125`). Public `src/`, `scripts/` and `pom.xml` match that release source. The proprietary TunerStudio API binary and private vehicle evidence are excluded from public distribution.
