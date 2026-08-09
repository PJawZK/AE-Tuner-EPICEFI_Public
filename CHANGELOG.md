# Changelog

## 0.4.0

Status: **physically validated and accepted stable baseline**.

Accepted JAR SHA-256:

`1af45f58584b0dda8a8e2eb9b78ddfb09276f407b9264ee74bbf9408d54b13d8`

- Promoted the physically validated Guided/Passive architecture to the stable `0.4.0` identity without adding ECU write capability.
- Added Adaptive **Guided Capture** for Predictive MAP Blend Duration evidence using natural pedal openings rather than requiring exact pre-scripted throttle steps.
- Added frozen pre-opening TPS guidance so the target does not chase live TPS after the opening begins.
- Requires MAP prediction to be active at the Blend Duration measurement anchor; stale prediction-inactive fallback-MAP gaps are not valid anchors.
- Added attempt traces, typed outcomes/evidence, comparability grouping, retained/raw evidence, and Guided session summaries.
- Added one-shot audio workflow cues and a stationary **Audio Cue Lab**.
- Added local evidence checkpoint/recovery support.
- Replaced synchronous/static Guided sample publication with a bounded instance-local dispatcher so Passive/TunerStudio sample delivery does not wait for Guided computation.
- Split Guided, host compatibility, shared model, Passive analysis, proposal/report, recovery, and reusable UI responsibilities into focused packages.
- Split the previous monolithic transient-event analysis/presentation path into event model, analyzer, assessment, formatter, and MAP-prediction metrics components.
- Preserved MAP Estimate evidence, Session Guidance, event capture, reports/CSV export, running/cranking/key-off safety classification, trigger/fault/cut evidence, and Java 8 bytecode.
- Passed deterministic validation and real synthetic Swing/plugin-panel integration.
- Physical testing covered stationary startup/live data, audio, repeated hide/reopen, repeated Guided sessions, road workflow, Passive continuity under Guided load, and measurement-anchor correctness.
- No generated Blend Duration proposal/value became an automatically accepted tune through this release.

## 0.3.19 — superseded development candidate

The `0.3.19` development line introduced the per-RPM Predictive MAP Blend Duration evidence model that was later carried forward and physically validated as part of `0.4.0`.

- Assigned evidence to actual Blend Duration RPM regions.
- Added retained held-opening counts, observed coverage, median/mean/range/IQR/standard-deviation/outlier evidence, confidence, and eligibility.
- Added explicit rejection reasons for discontinuities, missing detector bursts, repeated stabs, insufficient MAP gap, throttle release, unresolved catch-up, invalid duration, and missing RPM.
- Preserved unsupported/ineligible RPM points without silent interpolation or smoothing.
- Kept repeated-stab events diagnostic-only for the base Blend Duration curve.

`0.3.19` is not a current release; its relevant work is incorporated into accepted `0.4.0`.

## 0.3.18

- Consolidated the physically accepted v0.3.17 operational-state, cut-reason and ignition-counter safety behavior with the merged deterministic Swing integration harness.
- Added the session-only Recommendation History and dedicated Session Guidance tab with clickable recommendation-card navigation.
- Verified the asynchronous shutdown sequence through automated Swing integration without false or duplicate shutdown guidance.
- Kept Recommendation History memory-only and excluded it from reports, CSV exports, project files and ECU state.
- Synchronized source, Maven, manifest and JAR filename identity as version 0.3.18.
- Preserved MAP Estimate, Predictive Map Blend Duration, transient-event logic and strictly read-only ECU behavior.

## 0.3.17

- Made either resolved explicit ignition-off signal override a lagging `running` value during asynchronous output-channel updates.
- Added a short coherent-running guard before a cut reason code alone can create a critical running cut recommendation.
- Kept actual `Total spark cut` and `Total fuel cut` outputs immediately safety-critical while reporting them separately from `Ign: Cut Code` and `Fuel: Cut Code`.
- Split ignition diagnostic-counter positive increments into running, cranking, key-off, and unknown-state totals while preserving reset counts.
- Added deterministic regressions for the physical shutdown sequence that previously produced a false running cut warning.
- Preserved MAP Estimate, Predictive Map Blend Duration, transient-event logic, and strictly read-only ECU behavior.

## 0.3.16

- Added exact generated EPICEFI output-channel names for running/cranking, ignition and fuel cut reasons, and ignition diagnostic counters.
- Extracted deterministic output-channel resolution so internal-name mappings are regression-tested.
- Added saved-report evidence showing each critical role's selected TunerStudio channel/latest raw value, or attempted candidates when unresolved.
- Preserved running/cranking/key-off safety classification, counter accumulation, tuning algorithms, and strictly read-only behavior.

## 0.3.15

- Classified live safety evidence as running, cranking, key-off/coast-down, or unknown using resolved operating-state channels with RPM and battery-voltage fallbacks.
- Kept running trigger/sync loss and running fault/cut activity safety-critical while retaining cranking and key-off activity as diagnostic context.
- Excluded normal key-off trigger pulses and cut codes from running-engine troubleshooting recommendations.
- Accumulated positive Trigger Error Counter and ignition diagnostic-counter increments across resets while reporting resets separately.
- Added deterministic coverage for running, cranking, key-off, counter-reset, and resolved-zero behavior.
- Made CSV/report filenames and report headers derive from the plugin version.

## 0.3.14

- Monitored trigger/sync faults, ignition/injector faults, cuts, stop codes, and diagnostic counters across the complete live session rather than only full-load samples.
- Kept boost, lambda, injector-duty, ignition-timing, and fuel-pressure full-load summaries restricted to explicit full-load samples.
- Added critical-channel evidence states that distinguish received values, received zero/inactive values, and unresolved/unavailable channels.
- Prevented unsupported `none seen` safety conclusions when required fault/cut channels did not deliver live values.
- Added deterministic low-load trigger-fault regression coverage.

## 0.3.13

- Distinguished normal timer-counter increments within one continuous TPS-change burst from genuine repeated pedal stabs.
- Started catch-up timing after the final trigger/reset sample and rejected events where throttle was released before measured MAP caught up.
- Added prediction-trigger-burst evidence to CSV export.
- Preserved all unvisited MAP Estimate cells and reported out-of-range unvisited values as warnings rather than silently changing them.
- Added per-cell MAP spread, changed/excluded-cell, sample-count, and standard-deviation evidence.
- Raised the minimum meaningful Wall Wetting event threshold to avoid idle/background numerical correction events.

## 0.3.11

- Stabilized Overview and Technical details scrolling and section sizing.
