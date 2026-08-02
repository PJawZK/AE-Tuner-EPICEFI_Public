# AE Tuner for EPICEFI

AE Tuner is a **read-only TunerStudio plugin** for reviewing acceleration-enrichment and transient-fuelling behaviour on EpicEFI projects.

> **You do not need to compile or modify code to use the plugin.**
> Normal installation uses a prebuilt `ae-tuner-epicefi-<version>.jar` from the repository's **Releases** page. The build instructions near the end of this page are only for developers.

AE Tuner does not write ECU RAM values, burn settings, automatically apply tables, or make hidden tune changes. It captures evidence and presents tuning guidance for the user to review.

## Current release state

- **Stable:** `0.3.18` — physically accepted baseline.
- **Candidate:** `0.3.19` — automated and registered-host validation passed; controlled physical held-opening evidence is still pending.

Use the stable release for ordinary use. Candidate builds are for explicit testing and should not be treated as accepted releases.

## Project roadmap

See [`ROADMAP.md`](ROADMAP.md) for:

- completed milestones;
- the current physical-validation gate;
- planned MAP Estimate, Wall Wetting, Instant Fuel, report and diagnostics work;
- public release and rolling-candidate operations;
- the read-only safety boundary.

The public roadmap is updated when work is completed, a milestone changes status, or the approved development direction changes.

## What it does

AE Tuner can:

- read the current EpicEFI/TunerStudio project configuration;
- resolve relevant displayed output channels;
- capture and classify transient events;
- distinguish MAP Predict, Wall Wetting, Instant Fuel and legacy TPS cycle AE contribution where channels permit;
- collect stable MAP Estimate evidence while preserving unvisited cells;
- produce per-RPM Predictive MAP Blend Duration evidence;
- keep repeated throttle stabs visible diagnostically without using them as base-curve evidence;
- review running, cranking and key-off safety context;
- export a tuning report and a detailed captured-events CSV;
- keep a temporary in-session guidance history.

It is an evidence and review tool, not an automatic tuner.

## Requirements

- TunerStudio with application-plugin support;
- an EpicEFI project exposing the required settings and output channels;
- Java compatible with the installed TunerStudio version;
- a supported AE configuration.

The plugin targets **Java 8 bytecode** for broad TunerStudio compatibility.

## Installation

1. Download the intended stable plugin JAR from **Releases**.
2. Close TunerStudio.
3. Remove older `ae-tuner-epicefi-*.jar` files from the TunerStudio plugins directory.
4. Copy in only the downloaded JAR.
5. Restart TunerStudio.
6. Open **AE Tuner (EPICEFI)** and confirm the displayed version.

The plugin JAR does **not** contain `TunerStudioPluginAPI.jar`; normal users do not need to install that developer dependency separately.

## Quick start

1. Open the correct TunerStudio project and connect to the ECU.
2. Open AE Tuner.
3. Select **Read AE project data**.
4. Check that the project settings and important live channels resolve.
5. Run **TPS noise calibration** with the throttle untouched.
6. Drive or test deliberately enough to capture the intended transient events.
7. Review the event list, Overview, Technical details and Session Guidance.
8. Export the report and captured-events CSV before resetting the session.

For tuning evidence, use controlled repeatable tests. Avoid mixing repeated throttle stabs, gear changes, wheelspin and unrelated disturbances into the same evidence set.

## Reports and session controls

### Save MAP Predict Report

Current plugin name for the combined tuning report. It includes:

- session diagnostics;
- MAP Estimate draft evidence;
- per-RPM Blend Duration evidence;
- channel-resolution evidence;
- transient contribution counts;
- operational-state and safety review;
- the recommended next step.

A future version is planned to rename this broader report to **AE Tuning Report** as dedicated analysis for more AE stages is added.

### Export Captured Events

Exports the captured event and sample data as CSV for detailed offline review.

### Reset Session

Clears captured events, temporary diagnostics and Session Guidance history. It is not a report export.

## Intended tuning order

The primary staged workflow is:

1. MAP Predict;
2. Wall Wetting;
3. Instant Fuel only when a residual sharp early lean hole remains.

Legacy TPS cycle AE remains available for compatibility and diagnostics, but it is not the primary workflow for the current EpicEFI setup.

## Safety and interpretation

- AE Tuner is strictly read-only.
- Recommendations are not automatically applied.
- Missing or unavailable channels are not treated as zero.
- Unvisited MAP Estimate cells remain unchanged.
- Repeated-stab events do not define the base Blend Duration curve.
- Physical vehicle evidence remains decisive.
- Do not carry out unsafe tests merely to hit an exact table axis point.

Always review proposed values against the original log, tune and test conditions before making manual ECU changes.

## Known limitations

- Compatibility depends on the EpicEFI/TunerStudio project definition and available output channels.
- Some evidence requires several coherent events before a proposal is eligible.
- Wall Wetting and Instant Fuel currently have contribution and diagnostic coverage, but not the same dedicated proposal depth as MAP Estimate and Blend Duration.
- The current combined report still uses the older **MAP Predict Report** name.
- A separate one-shot diagnostics export is planned but is not yet implemented.

## Getting help or reporting a problem

When opening an issue, include where practical:

- AE Tuner version;
- TunerStudio version;
- Java version and operating system;
- EpicEFI firmware/project identity;
- exact reproduction steps;
- exported report and captured-events CSV;
- matching TunerStudio log when relevant.

Review files before posting them publicly. Project names, machine details, tune settings and logged vehicle data may be present.

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

The built plugin JAR is written to `dist/`.

Do not commit or redistribute the TunerStudio Plugin API binary unless its separate licence expressly permits that use.

## Licence

AE Tuner source code and project documentation are licensed under the **Apache License 2.0**. See `LICENSE` and `NOTICE`.

The TunerStudio Plugin API and other named third-party products are not included under the AE Tuner licence. See `THIRD_PARTY_NOTICES.md` and `lib/README.md`.
