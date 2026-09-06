# Blend Duration measurement-model correction

Status: public release (`0.4.3`). Corrected measurement model retained; numerical Blend Duration proposal remains withheld pending physical validation of the measurement-to-setting conversion.

## Why the old numerical rule was retired

Archive19 road data plus the matching EPICEFI/rusEFI Predictive MAP implementation showed that the previous Guided measurement did not model the firmware state closely enough for numerical tuning.

The retired method found the largest prediction-active `fallbackMap - MAP` gap, timed MAP to 90% of that gap, then converted the retained median plus a fixed margin directly into a Blend Duration value. That can anchor to an intermediate prediction target and can retrospectively complete before the requested pedal plateau has been established.

No Archive19 proposal produced by that conversion is authoritative. In particular, do not use the old `0.10/0.16` 2600-RPM or `0.28` 3800-RPM proposals as validated tuning values.

## Firmware semantics retained by vehicle-test.17

During Predictive MAP operation:

1. Blend Duration is interpolated from the RPM curve at the current RPM.
2. A prediction episode starts from the current MAP-estimate/fallback target.
3. While prediction remains active, any higher `fallbackMap` replaces the latched prediction target and restarts the blend timer.
4. Effective MAP follows the firmware blend form:

   `effectiveMap = predictedTarget + (sensorMap - predictedTarget) * elapsed / blendDuration`

5. Prediction may end because the timer expires, throttle is released, or measured MAP removes the benefit of prediction.

## Corrected controlled measurement

The Guided Blend recipe:

- uses a real relative TPS-step target window around the driver-selected step; the current implementation accepts desired step ±10 TPS points, clamped to the existing +10..+40 road-capture limits;
- never searches backward through pre-plateau samples to declare catch-up;
- tracks the final/upward-latched prediction target and restarts its measurement anchor whenever a higher prediction-active `fallbackMap` appears;
- measures physical MAP catch-up to the exact final latched target rather than a 90% threshold;
- attaches the current working Blend Duration curve to the session and replays logged `Effective MAP` with current-RPM interpolation as a source/model tell-tale;
- retains prediction-counter evidence in the diagnostic trace;
- retains every physically valid event, then combines only comparable RPM/load/TPS-step/gear events for repeatability review;
- withholds numerical Blend Duration proposal/copy-paste/write-plan generation until the corrected physical measurement-to-duration conversion is separately validated.

## 0.4.3 Driver Focus

The Foundation 1/2 vehicle work established that a reliable method is not enough: the driver must be able to obtain valid evidence without interpreting the internal state machine. Blend Duration now follows that operator model.

The dedicated Guided Focus card reduces the road task to four physical phases:

1. **GET STEADY** — drive smoothly near the selected real Blend Duration RPM table point and keep the pedal calm while the rolling road baseline qualifies itself.
2. **OPEN & SETTLE** — make one smooth acceleration opening, settle inside the displayed relative TPS-step window, then hold. The driver does not chase an absolute TPS value.
3. **HOLD FOR MAP** — once the pedal plateau is proven, hold steady while measured MAP approaches the final upward-latched prediction target. The card shows measured MAP, target MAP and remaining catch-up.
4. **RESULT / REPEAT** — a valid event is stored or an invalid attempt is rejected with a physical retry instruction; normal throttle automatically reacquires the next rolling baseline.

Driver View exposes:

- selected RPM target and live distance to its allowed window;
- current relative TPS step and the accepted step window;
- measured MAP versus the final latched prediction target during catch-up;
- **MATCHING EVENTS x/N** separately from repeatability quality;
- a physical comparability hint when otherwise-valid events were retained separately because RPM/load/TPS-step/gear conditions differed;
- the last event result;
- an explicit `MEASUREMENT VALIDATION ONLY — NUMERICAL BLEND DURATION APPLY IS WITHHELD` boundary.

Details View retains the existing engineering material: baseline checks, adaptive comparability groups, final-target catch-up evidence, Effective MAP firmware replay, prediction counters and diagnostics.

The Driver view never decides validity, never relabels an event and never creates a numerical proposal. `BlendDurationGuidedSession`, `MapCatchupMeasurement` and `BlendDurationComparabilityGroups` remain authoritative.

## Gear semantics

- Manual gear: operator selection is authoritative metadata. ECU detected gear is informational only and can never reject or split a Manual event.
- Automatic detected: detected gear is sampled only until a short stable run is obtained, then latched and used as a comparability dimension. Continuous gear/VSS evaluation is avoided after the latch.
- Ignore gear: gear does not participate in comparability.

## Write-safety boundary

The physically proven guarded Apply/Restore infrastructure remains unchanged, but Blend Duration exposes no production write plan in `.17`. No automatic Apply and no ECU burn are permitted.

## Current vehicle curve during model validation

Keep the working curve unchanged while validating the corrected model:

- 1500 RPM: 0.08 s
- 2600 RPM: 0.26 s
- 3800 RPM: 0.24 s
- 5000 RPM: 0.18 s

## Next physical test

The next car session should validate **capture usability and corrected model evidence**, not tune the curve numerically.

For one selected real table point, follow only the Driver Focus prompts and collect the requested comparable repetitions. The acceptance questions are:

- can the driver reliably reach READY without manually reasoning about all baseline checks;
- do clean openings naturally reach the requested relative TPS-step hold;
- does the displayed final target match the firmware-faithful upward-latched `fallbackMap` event;
- does physical MAP catch-up complete consistently;
- do comparable repetitions form a useful repeatability set without manual group management;
- does Effective MAP replay remain consistent with the current curve;
- do rejection messages tell the driver exactly what to change on the next attempt.

Only after that physical dataset is reviewed should a numerical measurement-to-Blend-Duration conversion be designed or re-enabled.
