# Guided Capture physical vehicle test

Candidate version: `0.4.0-vehicle-test.8`

Status: read-only, unaccepted, draft and not a stable release. Do not approve or paste a Blend Duration value during workflow testing.

Exact validated commit, workflows, artifacts, digests and JAR SHA-256 are recorded in draft PR #16 after the documentation-complete head passes.

## `.7` physical-session disposition

The `.7` interaction and audio workflow felt good overall, but the controlled series was incomplete:

- 23 attempts;
- 1 accepted event;
- 22 excluded events;
- 2 explicit return-to-baseline outcomes;
- accepted duration `0.122 s`;
- no eligible proposal.

Many stable pedal holds landed around `35–36.7%`, immediately below the compiled `37–43%` band. This is treated as pedal muscle memory, not another reason to keep changing the state machine. The next trial widens only the test TPS tolerance.

The plugin was accidentally closed before passive manual exports were saved. `.8` adds automatic local recovery for that failure mode.

## Automatic recovery preflight

Recovery is a safety net, not a replacement for manual exports.

Default recovery root:

```text
~/.ae-tuner-epicefi/recovery
```

Before the road session:

1. Install `.8` and open the plugin.
2. Confirm the recovery status bar shows the active local directory.
3. Create at least one short passive event or Guided outcome while stationary/safe.
4. Wait at least several seconds for the evidence-change checkpoint.
5. Close the plugin without using the normal passive Save buttons.
6. Reopen it.
7. Confirm a previous-session recovery notice appears.
8. Open the recovery folder and verify readable passive/Guided recovery files.
9. Dismiss the notice only after inspection.
10. Continue the real road session only after this recovery behavior is confirmed.

A 60-second periodic checkpoint also runs. Normal close performs a final snapshot and combines passive CSV chunks. A crash/power loss can leave chunks, which `.8` combines on the next startup.

## Stationary Audio Cue Lab

Before driving, use **Test all cues**, the successful-event demo and excluded/return demo. Confirm READY, target acquired, accepted/backoff, excluded/backoff and return-to-baseline cues are audible and distinguishable. Audio settings are session-local and freeze when the Guided Session starts.

## Vehicle-test overrides for the next session

Start a new Guided Session and enable overrides. Change only:

| Limit | Next trial |
|---|---:|
| TPS tolerance | `±5.00%` |

Keep:

| Limit | Value |
|---|---:|
| Detector confirmation | `0.55 s` |
| Target acquisition | `1.00 s` |
| MAP catch-up | `1.20 s` |
| TPS boundary epsilon | `0.05%` |
| Local TPS onset rise | `2.00 points` |

With a 40% target, the accepted band is `35–45%`.

Rules:

- this is a test-only override; compiled default remains `±3.00%`;
- take a fresh session snapshot after changing the value;
- never mix ±3% and ±5% events in one controlled series or proposal;
- keep the fixed baseline-MAP hard limit at `5 kPa` for now;
- do not widen other limits merely to force acceptances.

## Installation

1. Close TunerStudio.
2. Preserve accepted `0.3.18` as rollback.
3. Remove every older `ae-tuner-epicefi-*.jar`, including `.1` through `.7`.
4. Install only `ae-tuner-epicefi-0.4.0-vehicle-test.8.jar`.
5. Restart and verify version/banner `0.4.0-vehicle-test.8` plus read-only/unaccepted wording.
6. Complete the recovery and audio preflights.
7. Confirm whole-page vertical scrolling, target gauges and passive first-open behavior.

## Controlled session

| Setting | Value |
|---|---:|
| Blend Duration table point | `2450 RPM` |
| Start RPM | `2000 RPM` |
| Held TPS target | `40%` |
| Accepted-event target | `5` |
| Gear | manual second |
| TPS tolerance override | `±5.00%` |

Expected bands:

- baseline acquire: `1900–2100 RPM`;
- READY retention: `1800–2200 RPM`;
- accepted TPS: `35–45%`.

## Per-attempt procedure

1. Select a safe opportunity before READY.
2. Establish approximately 2000 RPM with ordinary light throttle.
3. At READY, make one prompt smooth opening.
4. Continue through `OPENING PENDING`; do not restart the movement.
5. Stop naturally inside the wider 35–45% band.
6. Hold through `TARGET ACQUIRED - HOLD`.
7. Back off immediately on accepted, excluded or return-to-baseline cue/state.
8. Recover fully before the next attempt.
9. Do not chase RPM after opening begins.

The wider band is intended to collect enough consistent evidence to evaluate the measurement approach while pedal placement develops naturally.

## Required behavior checks

Record whether:

- recovery checkpoints appear after completed evidence;
- normal close creates/finalizes readable recovery report and CSV files;
- reopening exposes previous-session recovery and the folder opens;
- dismissing the notice does not delete the files;
- the ±5% band displays as 35–45%;
- effective limits in Guided exports show the override;
- stable 35–36.7% holds can now become eligible target acquisitions;
- 50%+ overshoots remain excluded;
- `OPENING PENDING` and direct audio remain reliable;
- target-acquired, accepted, excluded and return cues are distinguishable;
- baseline-MAP mismatch remains explicit when the 5 kPa limit is exceeded;
- no ECU value is written, burned or applied.

## Evidence package

Always save manually before reset:

- Guided Capture TXT;
- Guided Capture CSV;
- passive MAP Predict report;
- passive captured-events CSV;
- optional matching `.msl` when reliable;
- candidate filename/checksum;
- tune filename/checksum;
- notes identifying the ±5% override, audio profile, conditions and deviations.

Also preserve the `.8` recovery run directory for this validation so manual and recovery exports can be compared.

## Disposition

### ACCEPT WORKFLOW

The collection, cue and recovery workflow is physically usable. This does not approve a tune value or merge PR #16.

### REVISE

A state, timing, measurement, audio, recovery, export, UI or host problem remains. Preserve manual exports, recovery files, compact traces and audio audit.

### ABORT

A safety, stability, validity or read-only concern occurred. Restore accepted `0.3.18` and preserve evidence.

## Rollback
1. Close TunerStudio.
2. Remove `.8`.
3. Restore only accepted `0.3.18`.
4. Restart and verify `0.3.18`.
