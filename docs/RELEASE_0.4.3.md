# AE Tuner (EPICEFI) 0.4.3

v0.4.3 is the first full public release after `v0.4.2-rc.2` and promotes the current validated 0.4.3 development line.

## What changed

- AE Foundation 1 timing/movement capture matured around the retained 40 ms Delta Window / 60 ms Sample Length workflow.
- AE Foundation 2 gained the dedicated Normal Correction / Acceleration Opening Driver workflow, stronger quiet calibration, maneuver rejection, separation validation, full context guarding and same-event OLD-vs-APPLIED post-Apply A/B validation.
- Archive46 physically accepted the complete Foundation 2 Apply/readback/B/KEEP lifecycle on the vehicle.
- MAP Predict / Blend Duration moved to the firmware-faithful final/upward-latched `fallbackMap` target and exact MAP catch-up model, with Effective MAP replay and comparable-event grouping.
- Blend Duration now has a Foundation-style Driver Focus. Numerical Blend Duration tuning remains intentionally withheld pending physical validation of the corrected conversion rule.
- Apply/Restore validation, diagnostics, audio cues, recovery/export paths and real Swing synthetic UI coverage were expanded while preserving the no-Burn boundary.

## Artifact

`ae-tuner-epicefi-0.4.3.jar`
SHA-256: `22492a065d41e3ca594485eb8c5ae70d88079cc4c6f879b444938ad4ede54143`

The executable tuning/control logic is promoted from the CI-validated private `0.4.3-vehicle-test.17` artifact; the public JAR changes only release/version/report identity strings and manifest metadata.
