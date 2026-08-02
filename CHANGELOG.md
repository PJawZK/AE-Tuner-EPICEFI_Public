# Changelog

## Unreleased — repository authority, continuation and publication

- Added the required handoff sequence: run the handoff pre-flight, create a continuation handoff, then continue from the repository.
- Added `scripts/handoff-preflight.sh` and offline continuation-authority consistency checks.
- Replaced stale v0.3.17/PR #5 continuation documents with the current v0.3.18 accepted baseline and exact v0.3.19 candidate identity.
- Corrected the canonical v0.3.19 exact-head CI artifact identity and separated it from the local JDK 21 development artifact and superseded candidates.
- Updated architecture, roadmap, known issues, build/release and workstream documentation to match the final v0.3.19 source structure and evidence state.
- Defined the private authority and public sanitized downstream repository relationship.
- Added an explicit public-export allowlist, leak checks, provenance generation and a non-publishing review-artifact workflow.
- Selected Apache License 2.0 for AE Tuner source code and project documentation.
- Added `LICENSE`, `NOTICE` and updated third-party dependency notices.
- Approved public publication without `TunerStudioPluginAPI.jar`; the API binary remains private and is not bundled in AE Tuner releases.
- Added a dedicated end-user public README while retaining the private developer/authority README.
- Updated the sanitized exporter to promote the public README, require Apache-2.0 licence files and record review/publish provenance.
- Replaced resolved publication blockers with operational binary-release and synchronization tasks.
- Kept the v0.3.19 physical-validation candidate head unchanged; this repository-control and publication work changes no production Java or tuning algorithm.

## 0.3.19

- Replaced the coarse Predictive Map Blend Duration summary with evidence tied to the actual table RPM points and their midpoint-defined regions.
- Added per-RPM prediction-event counts, usable held-opening counts, observed RPM coverage, catch-up median/mean/range/IQR/standard deviation, statistical outlier counts, confidence and eligibility.
- Added explicit per-RPM rejection reasons for reset discontinuities, missing detector bursts, repeated stabs, insufficient MAP gap, throttle release, unresolved catch-up, invalid duration and missing RPM.
- Kept multiple detector bursts visible diagnostically while excluding them from the base Blend Duration curve.
- Withheld paste-ready output until one RPM point has at least three retained coherent held-opening events with acceptable spread; high confidence requires at least five tighter events.
- Preserved every unsupported or ineligible RPM point exactly and applied no silent interpolation or smoothing.
- Added deterministic accepted, insufficient, repeated-stab and high-spread regressions plus full Swing-report assertions.
- Added long-session diagnostics and characterization, bounded presentation/helper extraction, responsive reachability and nested wheel handoff.
- Preserved MAP Estimate, Wall Wetting, Instant Fuel, operational-state safety, Session Guidance, Java 8 and strictly read-only ECU behavior.

## 0.3.18

- Consolidated the physically accepted v0.3.17 operational-state, cut-reason and ignition-counter safety behavior with the merged deterministic Swing integration harness.
- Added the session-only Recommendation History and dedicated Session Guidance tab with clickable recommendation-card navigation.
- Verified the exact Archive 4 asynchronous shutdown sequence through automated Swing integration, including no false or duplicate shutdown guidance entry.
- Kept Recommendation History memory-only and excluded it from reports, CSV exports, project files and ECU state.
- Synchronized source, Maven, manifest and JAR filename identity as version 0.3.18.
- Preserved MAP Estimate, Predictive Map Blend Duration, transient-event logic and strictly read-only ECU behavior.

## 0.3.17

- Made either resolved explicit ignition-off signal override a lagging `running` value during asynchronous output-channel updates.
- Added a short coherent-running guard before a cut reason code alone can create a critical running cut recommendation.
- Kept actual `Total spark cut` and `Total fuel cut` outputs immediately safety-critical while reporting them separately from `Ign: Cut Code` and `Fuel: Cut Code`.
- Split ignition diagnostic-counter positive increments into running, cranking, key-off, and unknown-state totals while preserving reset counts.
- Added deterministic regressions for the exact physical shutdown sequence that previously produced a false running cut warning.
- Preserved MAP Estimate, Predictive Map Blend Duration, transient-event logic, and strictly read-only ECU behavior.

## 0.3.16

- Added exact generated EPICEFI output-channel names for `ready`/running, `crank`/cranking, ignition and fuel cut reasons, and all four ignition diagnostic counters.
- Extracted deterministic output-channel resolution so the generated internal-name mappings are regression-tested.
- Added saved-report evidence showing each critical role's exact selected TunerStudio channel and latest raw value, or the complete attempted candidate list when unresolved.
- Preserved the v0.3.15 running/cranking/key-off safety classifier, counter accumulation, tuning algorithms, and strictly read-only behavior.

## 0.3.15

- Classify live safety evidence as running, cranking, key-off/coast-down, or unknown using the resolved `running`, `cranking`, `ignitionOn`, and `Main relay: Has IGN voltage` channels with RPM and `Batt V` fallbacks.
- Keep running trigger/sync loss and running fault/cut activity safety-critical while retaining cranking and key-off activity as explicit diagnostic context.
- Exclude normal key-off trigger pulses and cut codes from running-engine troubleshooting recommendations.
- Accumulate positive Trigger Error Counter and ignition diagnostic-counter increments across resets while reporting resets separately.
- Added exact EPICEFI aliases for `overDwellNotScheduled`, `overchargeWarnings`, `undechargeWarnings`, `underchargeWarnings`, and `sparkOutOfOrder`.
- Added deterministic regression coverage for running, cranking, key-off, counter-reset, and resolved-zero behavior.
- Made CSV/report filenames and report headers derive from the plugin version instead of hardcoded version text.

## 0.3.14

- Monitor trigger/sync faults, ignition/injector faults, cuts, stop codes, and diagnostic counters across the complete live session instead of only during full-load samples.
- Keep boost, lambda, injector-duty, ignition-timing, and fuel-pressure full-load summaries restricted to explicit full-load samples.
- Added exact EPICEFI channel candidates for `Timing: ignition`, `Trigger Error Counter`, `Boost: Target`, and low/high fuel pressure from the matching `mainController.ini`.
- Added critical-channel evidence states that distinguish received values, received zero/inactive values, and unresolved or unavailable channels.
- Prevented unsupported `none seen` safety conclusions when required fault/cut channels did not deliver live values.
- Prioritize trigger/sync loss in the recommended next step.
- Added a deterministic low-load trigger-fault regression test to repository validation.

## 0.3.13

- Changed Blend Duration analysis to distinguish normal timer-counter increments within one continuous TPS-change burst from genuine repeated pedal stabs.
- Starts catch-up timing after the final trigger/reset sample and rejects events where throttle is released before measured MAP catches up.
- Added `event_prediction_trigger_bursts` to CSV export and replaced misleading repeated-reset session wording with separate TPS-change burst counts.
- MAP Estimate drafts now preserve all unvisited cells, even when they exceed the configured turbo cap; those cells are reported as warnings instead.
- Added per-cell MAP spread checks, changed/excluded-cell details, and sample-count/standard-deviation reporting.
- Raised the minimum meaningful Wall Wetting event threshold to avoid idle/background numerical correction events.
- Added more ignition timing, boost target and fuel-pressure channel aliases.
- Updated CSV/report filenames and plugin version to 0.3.13.

## 0.3.11

- Stabilized Overview and Technical details scrolling and section sizing.
