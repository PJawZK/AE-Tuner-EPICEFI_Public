# AE Tuner for EPICEFI

AE Tuner is a **read-only TunerStudio plugin** for reviewing acceleration-enrichment and transient-fuelling behaviour on EpicEFI projects.

> **You do not need to compile or modify code to use the plugin.**
> Normal installation uses a prebuilt `ae-tuner-epicefi-<version>.jar` from the repository's **Releases** page. The build instructions near the end of this page are only for developers.

AE Tuner does not write ECU RAM values, burn settings, automatically apply tables, or make hidden tune changes. It captures evidence and presents tuning guidance for the user to review.

## Stable release

The current physically accepted stable release is **v0.4.0**.

Accepted artifact identity:

- JAR: `ae-tuner-epicefi-0.4.0.jar`
- SHA-256: `1af45f58584b0dda8a8e2eb9b78ddfb09276f407b9264ee74bbf9408d54b13d8`
- Java target: Java 8 bytecode
- ECU boundary: strictly read-only

There is currently **no active community test candidate**. Historical `0.3.19` and `0.4.0-vehicle-test.*` branches/builds are superseded by accepted `0.4.0` and must not be treated as the current release.

## What v0.4.0 adds

v0.4.0 keeps the existing Passive analysis and safety evidence while adding the physically validated Guided architecture used for Predictive MAP Blend Duration collection.

Highlights:

- **Guided Capture** for controlled, natural throttle-opening evidence;
- audio-led workflow cues plus a stationary **Audio Cue Lab**;
- frozen pre-opening TPS guidance instead of a target that follows the pedal;
- Blend Duration measurement anchoring only while MAP prediction is active;
- comparability grouping and typed retained evidence for Guided attempts;
- bounded background Guided processing so Passive/TunerStudio sample delivery is not blocked by Guided computation;
- local evidence recovery/checkpoint support;
- decomposed `guided`, `host`, `model`, `passive`, `proposal`, `recovery`, and `ui` implementation packages;
- preserved running/cranking/key-off safety classification, cut/fault evidence, MAP Estimate evidence, Session Guidance, reports, and CSV export;
- deterministic validation and synthetic Swing/plugin integration coverage.

The accepted architecture was physically exercised through stationary and road testing, repeated Guided sessions, hide/reopen lifecycle testing, audio-led operation, and simultaneous Passive capture. No generated Blend Duration value was automatically accepted as a tune.

## Project roadmap

See [`ROADMAP.md`](ROADMAP.md) for completed milestones and the current development direction.

The next bounded work has **not yet been committed to one active feature**. Current planned areas are:

- MAP Estimate maturity;
- Wall Wetting analysis and guidance;
- reports/diagnostics improvements;
- Instant Fuel only if a residual sharp early lean hole remains after the earlier stages are mature.

## Requirements

- TunerStudio with application-plugin support;
- an EpicEFI project exposing the required settings and output channels;
- Java compatible with the installed TunerStudio version;
- a supported AE configuration.

The plugin targets **Java 8 bytecode** for broad TunerStudio compatibility.

## Installation

1. Download `ae-tuner-epicefi-0.4.0.jar` from the `v0.4.0` release.
2. Keep your previous known-good JAR available as a rollback.
3. In TunerStudio, open **Tools → TunerStudio Plugins → Add or update plugin** and select the downloaded JAR.
4. Complete the TunerStudio installation prompts.
5. Open **AE Tuner (EPICEFI)** and confirm the displayed version is `0.4.0`.
6. If you intend to use Guided audio while driving, test the READY sound while stationary first.

If your TunerStudio installation requires manual plugin placement instead, close TunerStudio, remove older `ae-tuner-epicefi-*.jar` copies from the plugin directory, install only the `0.4.0` JAR, and restart TunerStudio.

The plugin JAR does **not** contain `TunerStudioPluginAPI.jar`; normal users do not install that developer dependency separately.

## Quick start

### Passive capture / reports

1. Open the correct TunerStudio project and connect to the ECU.
2. Open AE Tuner.
3. Select **Read AE project data**.
4. Check that project settings and important live channels resolve.
5. Run **TPS noise calibration** with the throttle untouched and use the recommended threshold when appropriate.
6. Drive or test deliberately enough to capture the intended transient events.
7. Review the event list, Overview, Technical details and Session Guidance.
8. Export the report and captured-events CSV before resetting the session.

### Guided Capture

1. Start normal TunerStudio logging before the test when matched log evidence is desired.
2. Open **Guided Capture** only after Passive/project data is working normally.
3. Test the audio cue while stationary.
4. Start with the normal Guided settings and use safe, natural pedal openings rather than forcing an exact road condition.
5. Review accepted, warning, and excluded attempts together with their evidence.
6. Save Guided evidence before ending/resetting a useful session.

Vehicle-test override controls change only the plugin's capture/acceptance assumptions; they do not change ECU settings. They are intended for advanced testing and should normally remain at their defaults unless the operator understands why a limit needs changing.

For useful tuning evidence, avoid mixing repeated throttle stabs, gear changes, wheelspin, or unrelated disturbances into the same base evidence set.

## Reports and session controls

### Save MAP Predict Report

Exports the combined Passive tuning report, including session/channel evidence, MAP Estimate draft evidence, Predictive MAP review, transient contribution counts, operational-state/safety review, and the recommended next step.

### Export Captured Events

Exports captured event and sample data as CSV for detailed offline review.

### Guided evidence exports

Guided Capture can save its attempt/session evidence separately. Use the Guided evidence together with the matching Passive export and TunerStudio log when investigating a specific result.

### Reset Session

Clears the current in-plugin session state. Save any evidence you want to retain first.

## Intended tuning order

The staged workflow remains:

1. MAP Predict;
2. Wall Wetting;
3. Instant Fuel only when a residual sharp early lean hole remains.

Legacy TPS cycle AE remains available for compatibility and diagnostics, but it is not the primary workflow for the current EpicEFI setup.

## Safety and interpretation

- AE Tuner is strictly read-only.
- Recommendations are not automatically applied.
- Missing or unavailable channels are not treated as zero.
- Unvisited MAP Estimate cells remain unchanged.
- Repeated-stab events must not define the base Blend Duration curve.
- Guided Blend Duration measurements require prediction-active evidence.
- Physical vehicle evidence remains decisive.
- Do not carry out unsafe tests merely to hit an exact table point.

Always review proposed values against the original log, tune, and test conditions before making manual ECU changes.

## Getting help or submitting logs for diagnosis

Use the public **[Log diagnosis / test feedback](https://github.com/PJawZK/AE-Tuner-EPICEFI_Public/issues/new?template=log-diagnosis.yml)** issue form for matched log review and test feedback.

For useful diagnosis, attach one ZIP containing files from the same test session where practical:

- the matching TunerStudio `.msl` log;
- the AE Tuner Passive captured-events `.csv`;
- the AE Tuner Passive report;
- Guided Capture TXT/CSV evidence when the issue concerns Guided;
- optional screenshots or intentionally shared project/tune details needed to interpret the event.

Also provide the AE Tuner version/JAR SHA-256, TunerStudio/Java/OS versions, EpicEFI firmware/project identity, observed versus expected behaviour, test conditions, and the exact MSL timestamp/marker or AE Tuner event ID requiring review.

GitHub issue attachments are public. Review every file before posting and remove credentials, licence or registration files, private keys, personal information, and anything else that should not be publicly accessible. Project names, machine details, tune settings, and logged vehicle data may be present.

If GitHub rejects an archive because it is too large, trim the log to the relevant period or split the evidence into clearly named archives. Do not commit diagnostic logs to repository history.

Do not perform unsafe driving or force an exact RPM/load point solely to collect diagnosis evidence.

## Building from source — developers only

End users can skip this section.

The public source intentionally excludes `lib/TunerStudioPluginAPI.jar`. To compile locally, obtain an authorized copy from EFI Analytics or from your own TunerStudio installation and place it at:

```text
lib/TunerStudioPluginAPI.jar
```

Then run:

```bash
bash scripts/validate.sh
```

For the real synthetic plugin-panel exercise, run:

```bash
bash scripts/synthetic-plugin-integration.sh
```

The built plugin JAR is written to `dist/`.

Do not commit or redistribute the TunerStudio Plugin API binary unless its separate licence expressly permits that use.

## Licence

AE Tuner source code and project documentation are licensed under the **Apache License 2.0**. See `LICENSE` and `NOTICE`.

The TunerStudio Plugin API and other named third-party products are not included under the AE Tuner licence. See `THIRD_PARTY_NOTICES.md` and `lib/README.md`.
