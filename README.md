# AE Tuner (EPICEFI)

AE Tuner is a TunerStudio plugin for EpicEFI transient-fuelling analysis and guided acceleration-enrichment tuning.

## Public release

Current release: **v0.4.3**
Plugin: `ae-tuner-epicefi-0.4.3.jar`
Java target: Java 8 bytecode
SHA-256: `22492a065d41e3ca594485eb8c5ae70d88079cc4c6f879b444938ad4ede54143`

v0.4.3 promotes the validated 0.4.3 vehicle-test development line into the public repository. The previous public release was `v0.4.2-rc.2`.

## Highlights

- **AE Foundation 1 / TPS Movement & Timing** is mature for the current vehicle-test workflow, with retained 40 ms Delta Window and 60 ms Sample Length behavior, passive evidence capture, re-anchoring and driving-range qualification.
- **AE Foundation 2 / Threshold & Sensitivity** now uses the physically validated Normal Correction / Acceleration Opening workflow, 80-sample + 1.5-second quiet lock, maneuver-quality rejection, independent count/separation completion, guarded controller-context verification and same-event OLD-vs-APPLIED A/B validation.
- Archive46 physically accepted the guarded Foundation 2 Apply -> Read Working Tune -> fresh B -> KEEP lifecycle. The retained static threshold curve is 0.570 / 0.597 / 0.623 / 0.650 at 800 / 1600 / 2400 / 3200 RPM.
- **MAP Predict / Blend Duration** now follows the final/upward-latched prediction-active `fallbackMap` target, exact physical MAP catch-up, current-curve Effective MAP replay and comparable-event grouping. The retired largest-gap/T90 numerical rule is no longer authoritative.
- Blend Duration has a dedicated Driver Focus: **GET STEADY -> OPEN & SETTLE -> HOLD FOR MAP -> RESULT / REPEAT**. Numerical Blend Duration proposal/write generation remains intentionally withheld until the corrected measurement-to-setting conversion is physically validated.
- Guarded Apply/Restore infrastructure and regression coverage were expanded. No automatic Burn path exists.

## Safety boundary

AE Tuner does not automatically Burn tune changes. Persistent changes must use explicit reviewed proposals through the guarded Apply -> readback -> Restore path. VE and ignition tuning remain outside AE Tuner tuning authority.

## Install

1. Download `ae-tuner-epicefi-0.4.3.jar` from the GitHub Release.
2. Install it using your normal TunerStudio plugin installation method.
3. Restart TunerStudio and confirm AE Tuner reports version `0.4.3`.

## Building from source

The TunerStudio Plugin API JAR is a third-party dependency and is intentionally not redistributed in this repository. See `lib/README.md` for local build setup.

Full release details are in `CHANGELOG.md` and `docs/RELEASE_0.4.3.md`.
