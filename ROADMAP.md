# AE Tuner public roadmap

This roadmap summarizes the public development direction for AE Tuner (EPICEFI).

The private authority repository remains the source of truth for detailed engineering state, evidence and release decisions. This public roadmap is the sanitized end-user and contributor view. It must be updated whenever a milestone is completed, its status changes, its scope changes, or the planned sequence changes.

## Status vocabulary

- **DONE** — implemented and accepted at the stated evidence level.
- **VALIDATE** — implemented, but still awaiting required physical or matched-log evidence.
- **ACTIVE** — work is currently underway.
- **PLANNED** — accepted direction, not yet implemented.
- **DEFERRED** — intentionally postponed until an earlier dependency is satisfied.
- **OUT OF SCOPE** — not authorized within the current safety model.

## Current release state

- **Stable:** `0.3.18` — physically accepted and available from GitHub Releases.
- **Candidate:** `0.3.19` — automated and registered-host validation passed; controlled physical held-opening evidence remains pending.
- **Public stable branch:** `main`.
- **Current transitional candidate branch:** `candidate/v0.3.19`.
- **Future pre-release branch:** rolling `candidate` branch after the v0.3.19 transition.

Candidate source is not stable merely because automated validation passed. Physical evidence and an explicit acceptance decision remain required before promotion.

## R0 — Repository, validation and public-release foundation

Status: **DONE**

Completed:

- Java 8 bytecode target and deterministic repository validation;
- GitHub Actions build and synthetic plugin integration;
- read-only safety boundary;
- Apache License 2.0 publication;
- separate end-user and developer README files;
- public source publication without `TunerStudioPluginAPI.jar`;
- sanitized stable and candidate source branches;
- stable v0.3.18 release JAR and checksum;
- private-to-public provenance and leak checks;
- rolling public candidate lifecycle policy.

Remaining operational improvements are tracked under R9.

## R1 — Operational-state safety and channel evidence

Status: **DONE**

Completed:

- running, cranking, key-off and unknown-state classification;
- exact EpicEFI output-channel resolution where supported;
- separation of actual cut outputs from cut-reason codes;
- trigger, ignition and fuel-path evidence review;
- missing, unavailable and received-zero values kept distinct;
- matched physical shutdown validation without false running warnings.

## R2 — Session Guidance and v0.3.18 stable release

Status: **DONE**

Completed:

- session-only Recommendation History;
- bounded in-memory guidance timeline;
- clickable recommendation-card navigation;
- deterministic Swing integration coverage;
- physically accepted v0.3.18 stable release.

## R3 — Per-RPM Predictive MAP Blend Duration evidence

Status: **VALIDATE**

Implemented in candidate v0.3.19:

- evidence assigned to actual Blend Duration RPM regions;
- retained held-opening counts and observed RPM coverage;
- median, mean, range, IQR, standard deviation and outlier evidence;
- confidence, eligibility and explicit rejection reasons;
- repeated throttle stabs kept diagnostic-only;
- unsupported and ineligible RPM points preserved exactly;
- no silent interpolation or smoothing;
- responsive layout and nested scroll-wheel handoff;
- long-session characterization.

Remaining gate:

1. controlled physical single-held-opening tests at safe useful RPM regions;
2. matched report, captured-events CSV and TunerStudio log review;
3. explicit **ACCEPT** or **REVISE** decision;
4. on acceptance, stable v0.3.19 publication and transition to the rolling public `candidate` branch.

## R4 — MAP Estimate maturity

Status: **PLANNED**

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
- review tip-in, tip-out and rich-recovery behavior;
- add evidence-backed guidance only after detection, eligibility and safety rules are validated.

## R6 — Instant Fuel analysis

Status: **DEFERRED**

Entry condition:

- a sharp early lean hole remains after MAP Predict and Wall Wetting are mature and physically accepted.

Planned direction:

- activation and contribution evidence;
- residual lean-hole assessment;
- evidence for whether Instant Fuel use is justified;
- no automatic table application.

## R7 — Reports and on-demand diagnostics

Status: **PLANNED**

Planned direction:

- rename the combined tuning export to **AE Tuning Report**;
- use the filename family `ae-tuner-tuning-report-...`;
- add dedicated sections as Wall Wetting, Instant Fuel and other AE-stage analysis matures;
- add a separate **Export Diagnostics** action;
- keep diagnostics one-shot and user-requested only;
- avoid background firmware polling, recurring listeners and persistent diagnostic history;
- report unavailable or offline metadata honestly.

The tuning report and diagnostics report remain separate products with different purposes.

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

- public stable and candidate source publication;
- stable release assets and checksums;
- sanitized export and dependency-exclusion checks;
- exact source provenance;
- rolling candidate lifecycle documentation;
- public roadmap publication and maintenance rule.

Planned operational work:

- protect public `main`, rolling `candidate` and stable tags;
- automate trusted private-to-public synchronization;
- automate guarded accepted promotion and candidate cleanup;
- keep the public roadmap synchronized with completed work and approved plan changes;
- add public issue templates and expanded user documentation;
- establish an authorized public-CI method for the separately supplied TunerStudio Plugin API dependency when required.

## R10 — ECU writes and burns

Status: **OUT OF SCOPE**

AE Tuner remains strictly read-only:

- no ECU RAM writes;
- no burns;
- no automatic table application;
- no hidden tune changes.

Any future write capability would require a separate approved safety architecture, explicit authorization and staged physical validation.

## Roadmap maintenance rule

The public roadmap is part of completing a public-facing change.

Update it when:

- a milestone changes from planned to active, validate or done;
- physical acceptance or revision changes release status;
- a feature is added, removed, deferred or reordered;
- a stable or candidate release changes;
- the public branch or release lifecycle changes;
- a completed change makes existing roadmap wording stale.

The public roadmap must not expose private logs, tune files, personal paths, credentials or private evidence. Detailed internal evidence remains in the private authority repository.
