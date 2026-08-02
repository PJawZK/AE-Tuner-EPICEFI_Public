# Roadmap

Use `docs/CURRENT_STATE.md` for the exact accepted release, active candidate, evidence gate and next action.

## Status vocabulary

- `DONE` — implemented and accepted at the stated evidence level.
- `VALIDATE` — implemented but awaiting required physical or matched-log evidence.
- `ACTIVE` — current bounded development or repository-control stage.
- `PLANNED` — accepted direction, not implemented.
- `BLOCKED` — accepted direction with an unresolved dependency or policy decision.
- `DEFER` — intentionally postponed.

## M0 — Repository and continuity foundation

Status: `ACTIVE`

Completed:

- private authority repository;
- Java 8 validation and reproducible builds;
- GitHub Actions validation;
- governance and evidence policy;
- compact current-state/handoff documents;
- handoff pre-flight command and required continuation sequence;
- offline continuation-authority consistency checks;
- private/public repository relationship documentation;
- sanitized non-publishing public review-export tooling;
- Apache License 2.0 selected and added;
- TunerStudio Plugin API excluded from public publication;
- separate private developer README and public end-user README;
- publication-approved export mode and licence/readme validation.

Remaining:

- complete CI review of the publication branch;
- reconcile repository-authority/publication branches after PR #12 receives its physical disposition;
- record exact public stable and candidate commit provenance;
- implement trusted synchronization only after the manual mapping is proven.

## M1 — Operational-state safety and channel evidence

Status: `DONE`

v0.3.17 passed matched physical start/run/key-off evidence. Exact generated channels resolved, actual cuts remained inactive, code 14 stayed key-off diagnostic context, false shutdown recommendations were removed, and counter attribution matched the raw log.

## M2 — Synthetic plugin integration

Status: `DONE`

The real Swing panel, output-channel callback, report/CSV save dialogs, screenshot evidence, asynchronous-shutdown fixture, responsive reachability and nested-wheel boundary behavior run automatically.

## M3 — Session Guidance

Status: `DONE`

Recommendation History is session-only, memory-only, clickable from the recommendation card, bounded to 100 entries and protected against duplicate refresh/key-off entries.

## M4 — Consolidated v0.3.18 release

Status: `DONE`

- integration commit: `d1bb3ace7b0b7a7d645cb2718e7e36035bf8fb67`
- accepted JAR SHA-256: `2d22c6a11407eea744df3ca81524732f0c30de90cb4c2562eb4bd9456ec44828`
- physical acceptance record: `docs/V0.3.18_ACCEPTANCE.md`

## M5 — Predictive Map Blend Duration maturity

Status: `VALIDATE`

Active candidate: `0.3.19`  
Active branch: `agent/per-rpm-blend-duration-evidence`  
Candidate source head: `dc89032595a043e4d894ae039987529d55cf63ed`  
Active PR: #12  
Active issue: #11  
Canonical CI JAR SHA-256: `e009f0ebf2ed386b11bf42e849f998995752a4bb76cd3eb884c1374bd11a130b`

Implemented and automated:

1. evidence tied to actual table RPM points and midpoint-defined regions;
2. per-point prediction counts and usable/retained held-opening counts;
3. observed RPM coverage;
4. catch-up median, mean, range, IQR, standard deviation and outlier counts;
5. confidence, eligibility, current/proposed value and explicit rejection reasons;
6. repeated-stab separation from the base curve;
7. three-event bounded-spread minimum for eligibility and five tighter events for high confidence;
8. exact preservation of unsupported/ineligible points with no silent interpolation or smoothing;
9. deterministic insufficient, medium-confidence, high-confidence, repeated-stab and high-spread regressions;
10. full real-panel Swing/report assertions;
11. long-session characterization;
12. bounded structural UI extraction;
13. responsive reachability and nested-wheel handoff.

Automated disposition: `PASS` at exact head.

Remaining gate:

1. controlled physical single-held-opening evidence at safe useful RPM regions;
2. report/CSV/MSL review of assignment, coverage, spread, rejections and unchanged unsupported points;
3. explicit `ACCEPT` or `REVISE` disposition before merging PR #12.

## M6 — MAP Estimate maturity

Status: `PLANNED`

Improve coverage reporting, per-cell confidence, deterministic orientation/filter tests, session comparison and explicit changed/excluded-cell evidence. Unvisited cells remain unchanged.

## M7 — Wall Wetting guidance

Status: `PLANNED`

Compare MAP Predict-only and MAP Predict + Wall Wetting sessions, including tip-in and tip-out behavior, after MAP Predict maturity. Guidance remains advisory and read-only.

## M8 — Instant Fuel

Status: `DEFER`

Entry gate: a residual sharp early lean hole remains after MAP Predict and Wall Wetting are accepted.

## M9 — On-demand diagnostics export

Status: `PLANNED`

Add a separate user-requested diagnostics report while keeping the AE tuning report separate.

Constraints:

- no background firmware polling;
- no new recurring timer or listener for diagnostics;
- prefer cached TunerStudio metadata;
- at most one explicit read-only firmware/signature request per export when necessary;
- short timeout and honest offline/unavailable status;
- no persistent diagnostic history;
- preserve Java 8 and read-only behavior.

This work must be developed on a separate branch and must not alter the frozen v0.3.19 physical-validation candidate.

## M10 — Offline analysis

Status: `DEFER`

Sequence:

1. plugin CSV/report import;
2. multi-session comparison;
3. `.msl` import;
4. shared analysis engine between live and offline modes.

## M11 — Public source publication

Status: `ACTIVE`

Policy decisions completed:

- Apache License 2.0 approved for AE Tuner source and documentation;
- `TunerStudioPluginAPI.jar` excluded from public source and release packages;
- public end-user README approved;
- private developer README remains private-authority documentation;
- sanitized source publication authorized after final export validation.

Current operational steps:

1. validate the final publication branch and export artifact;
2. populate public `main` with a sanitized v0.3.18 stable source tree;
3. populate public `candidate/v0.3.19` with clearly marked candidate source;
4. record public commit/tree digests in private authority documents;
5. attach a prebuilt stable JAR and checksums through a controlled GitHub Release process;
6. add branch protection and trusted synchronization after the mapping is proven.

The public repository must never receive the private API JAR, evidence files, private handoff documents or unlabelled candidate content.

## M12 — ECU RAM write or burn

Status: `DEFER`

Not authorized. Requires a separate approved safety architecture, explicit task authorization and staged validation.
