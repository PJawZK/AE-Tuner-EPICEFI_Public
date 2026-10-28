# AE Tuner (EPICEFI) v0.4.6

**Public release — 28 October 2026.** Supersedes v0.4.5. This release makes the current Guided UI, reliability improvements and historical Decel analysis available for broader user testing. Software validation is complete; tuning-algorithm numerical/physical maturity is task-specific.

## What is new

- Guided navigation, explicit Cancel / Start Over and clearer transient-analysis progress and cancellation feedback.
- Responsive pop-out windows: width-fitting vertical scrolling, wrapping report/table text, bottom action footers that stay visible, improved window decorations and a compact Evidence Source prompt.
- Improved Foundation Threshold and Blend graphs, contrast, theme switching and no-live-data coaching.
- Lower-overhead report/export, bounded session caching and safer evidence recovery/lifecycle handling; explicit Apply/Restore remains centralized.
- Decel historical-log analysis with separately classified physical events and fail-closed cross-log pooling when detector configuration matches. Unmeasured RPM cells remain NEED EVIDENCE.
- Additional validation for Wall Wetting / Instant Fuel evidence handling. Neither result is a universally validated tuning preset.

## Installation

1. Download `ae-tuner-epicefi-0.4.6.jar` from the v0.4.6 GitHub Release and verify the SHA-256.
2. Close TunerStudio completely. Move any earlier AE Tuner plugin JARs out of its plugin folder; install only this JAR.
3. Restart TunerStudio. Verify **version 0.4.6 / PUBLIC RELEASE**.
4. Read Working Tune again after setting changes or enabling/disabling an AE method.
5. Report issues with version, EpicEFI firmware/INI, TunerStudio version, OS, logs/screenshots (remove personal details), and reproduction steps.

## Safety and evidence

AE Tuner never burns ECU flash. Every supported write requires a reviewed explicit proposal, stale-baseline checks, exact readback, and Restore/rollback protection. VE and ignition tuning are out of scope. The newer Decel results, Wall Wetting / Instant Fuel inferences and any not-yet-physically-qualified task still require suitable real-vehicle evidence before applying tuning conclusions confidently.

## Reproducible identity and provenance

- Public version: `0.4.6`
- Exact qualifying source (private release identity build): `baddb47d3a13b2e772d5b6154f735f2f8c2ddb38`
- Build validation: GitHub Actions **#1932** / run `40725785125` — **PASS**
- Artifact: `ae-tuner-epicefi-0.4.6.jar`
- SHA-256: `31e23679f055789be0073f35e810f1ae793e0713dfc381c92e293c98ecdc19dd`
- Compilation target: Java 8 bytecode (CI uses Java 17)
- Public source: updated `main` snapshot of the matching Java source, tests, scripts, and Maven identity. Private TunerStudio API dependency is excluded from distribution.

The GitHub-generated source archive uses the public main commit at this release tag. The source cannot be compiled without an independently obtained, appropriately licensed TunerStudio Plugin API JAR.

## License

Apache License 2.0, with third-party TunerStudio Plugin API dependency excluded from the repository and binary.
