# AE Tuner public roadmap

This roadmap summarizes the public development direction for AE Tuner (EPICEFI).

The private authority repository remains the source of truth for detailed engineering state, evidence, and release decisions. This public roadmap is the sanitized end-user and contributor view.

## Status vocabulary

- **DONE** — implemented and accepted at the stated evidence level.
- **VALIDATE** — implemented, but still awaiting required physical or matched-log evidence.
- **ACTIVE** — work is currently underway.
- **PLANNED** — accepted direction, not yet implemented.
- **DEFERRED** — intentionally postponed until an earlier dependency is satisfied.
- **OUT OF SCOPE** — not authorized within the current safety model.

## Current release state

- **Stable:** `0.4.0` — physically validated and accepted.
- **Accepted JAR SHA-256:** `1af45f58584b0dda8a8e2eb9b78ddfb09276f407b9264ee74bbf9408d54b13d8`.
- **Public stable branch:** `main` after completion of the current public `0.4.0` publication update.
- **Active community candidate:** none.
- Historical `candidate/v0.3.19` and `0.4.0-vehicle-test.*` states are superseded by accepted `0.4.0`.

A future candidate is not stable merely because automated validation passes. Physical or otherwise appropriate evidence plus an explicit acceptance decision remain required before promotion.

## R0 — Repository, validation, and public-release foundation

Status: **DONE**

Completed:

- Java 8 bytecode target and deterministic repository validation;
- GitHub Actions validation and synthetic plugin integration;
- strict read-only safety boundary;
- Apache License 2.0 public source publication;
- public source publication without `TunerStudioPluginAPI.jar`;
- stable release assets/checksums and provenance discipline;
- public leak/privacy checks and log-submission guidance.

Remaining operational improvements are tracked under R9.

## R1 — Operational-state safety and channel evidence

Status: **DONE**

Completed:

- running, cranking, key-off, and unknown-state classification;
- EpicEFI output-channel resolution where supported;
- separation of actual cut outputs from cut-reason codes;
- trigger, ignition, and fuel-path evidence review;
- missing, unavailable, and received-zero values kept distinct;
- matched physical shutdown validation without false running warnings.

## R2 — Session Guidance and Passive analysis baseline

Status: **DONE**

Completed:

- session-only Recommendation History / Session Guidance;
- bounded in-memory guidance timeline;
- event capture, reports, CSV export, and MAP Estimate evidence;
- deterministic Swing integration coverage;
- physically accepted Passive behavior carried forward into `0.4.0`.

## R3 — Predictive MAP Blend Duration and Guided Capture

Status: **DONE**

Accepted in `0.4.0`:

- per-RPM Blend Duration evidence tied to actual table regions;
- retained held-opening counts, coverage, spread/statistical evidence, confidence, and explicit rejection reasons;
- repeated throttle stabs retained diagnostically but excluded from the base Blend Duration curve;
- unsupported/ineligible RPM points preserved without silent interpolation or smoothing;
- adaptive **Guided Capture** for natural pedal openings;
- frozen pre-opening TPS guidance;
- prediction-active-only Blend Duration measurement anchoring;
- attempt traces, comparability grouping, typed evidence, and session summaries;
- audio-led workflow and stationary Audio Cue Lab;
- hide/reopen lifecycle recovery and repeated Guided-session operation;
- bounded Guided sample dispatcher so Guided computation does not block the Passive/TunerStudio sample callback;
- local evidence checkpoint/recovery support;
- physical road validation with simultaneous healthy Passive capture.

No generated Blend Duration proposal became an automatically accepted tune through this milestone.

## R4 — MAP Estimate maturity

Status: **PLANNED**

This is a likely next bounded development area, but it is not yet declared the active implementation milestone.

Planned direction:

- clearer coverage and per-cell confidence;
- deterministic orientation and filter tests;
- improved changed/excluded-cell evidence;
- session comparison support;
- continued exact preservation of unvisited cells.

## R5 — Wall Wetting analysis and guidance

Status: **PLANNED**

Planned direction:

- separate Wall Wetting contribution evidence;
- compare MAP Predict-only and MAP Predict + Wall Wetting behavior;
- review tip-in, tip-out, and rich-recovery behavior;
- add evidence-backed guidance only after detection, eligibility, and safety rules are validated.

## R6 — Instant Fuel analysis

Status: **DEFERRED**

Entry condition:

- a sharp early lean hole remains after MAP Predict and Wall Wetting are mature and physically accepted.

Planned direction:

- activation/contribution evidence;
- residual lean-hole assessment;
- evidence for whether Instant Fuel use is justified;
- no automatic table application.

## R7 — Reports and on-demand diagnostics

Status: **PLANNED**

Planned direction:

- evolve the combined tuning export as additional AE stages mature;
- improve separation between tuning evidence and one-shot diagnostics;
- keep diagnostics user-requested rather than persistent background polling;
- report unavailable/offline metadata honestly.

## R8 — Offline and multi-session analysis

Status: **DEFERRED**

Expected sequence:

1. import AE Tuner report and captured-event CSV files;
2. compare multiple sessions;
3. add TunerStudio `.msl` import;
4. share validated analysis logic between live and offline modes.

## R9 — Public repository and release automation

Status: **ACTIVE**

Completed:

- public stable/candidate publication history;
- stable release assets and checksums;
- sanitized export and dependency-exclusion checks;
- exact source/binary provenance;
- public issue template and privacy/safety guidance;
- maintained public roadmap.

Current maintenance:

- publish accepted `0.4.0` source and stable artifact;
- retire stale `0.3.19` / vehicle-test candidate presentation and obsolete candidate payloads;
- reconcile public provenance to the accepted `0.4.0` identity.

Planned operational work:

- protect stable branches/tags as appropriate;
- automate trusted private-to-public synchronization;
- automate guarded accepted promotion and candidate cleanup;
- keep the public roadmap synchronized with accepted project state;
- establish an authorized public-CI method for the separately supplied TunerStudio Plugin API dependency when required.

## R10 — ECU writes and burns

Status: **OUT OF SCOPE**

AE Tuner remains strictly read-only:

- no ECU RAM writes;
- no burns;
- no automatic table application;
- no hidden tune changes.

Any future write capability would require a separate approved safety architecture, explicit authorization, and staged physical validation.

## Roadmap maintenance rule

Update this roadmap when a milestone changes state, release status changes, a feature is added/removed/deferred/reordered, or public branch/release lifecycle changes.

The public roadmap must not expose private logs, tune files, personal paths, credentials, or raw private evidence. Detailed internal evidence remains in the private authority repository.
