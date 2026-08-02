# Known issues

Use `docs/CURRENT_STATE.md` for the exact active candidate and immediate evidence gate.

## Open

### v0.3.19 physical Blend Duration validation pending

The exact-head v0.3.19 candidate has passed deterministic, CI, responsive-layout and registered-host smoke checks, but controlled vehicle evidence has not yet established per-RPM held-opening assignment, spread and proposal suitability.

Required evidence is a matched TunerStudio MSL, plugin CSV and MAP Predict report from deliberate single-held-opening events at safe useful RPM regions.

### Blend Duration evidence remains physically sparse

The new candidate correctly withholds unsupported or high-spread points, but useful physical evidence may remain sparse at some table RPM points. Do not force unsafe operation merely to cover an axis point. Unsupported points must remain unchanged.

### MAP Estimate ambiguity on a turbo engine

RPM and TPS do not uniquely determine MAP under all gear, load and spool conditions. Sample count alone is insufficient; spread filtering, exercised-cell review and session context remain essential.

### Low-RPM bog unresolved

The vehicle previously showed bog-like response below approximately 2000 RPM. Evidence suggested possible excessive predicted-MAP gap and repeated prediction rearming, but no final cause or accepted correction exists.

### Fuel-pressure evidence may be unavailable

Displayed fuel-pressure channels have been zero or unavailable in some sessions. Full-load safety conclusions cannot exclude pressure behavior without a valid trace.

### Captured event history is unbounded until reset

Detector buffers and Session Guidance are bounded, but completed captured events remain in memory until **Reset session** or plugin destruction. Long-session characterization found acceptable tested performance up to 1,500 events, with CSV generation the largest linear user-triggered cost. A retention cap must not be added without an explicit evidence-preservation policy.

### Combined report and diagnostics are not yet separated

Session diagnostics currently appear inside the combined MAP Predict report. A separate on-demand diagnostics export, including honest firmware identity where supported, is planned but not part of the frozen v0.3.19 physical-validation candidate.

### Firmware identity retrieval is not implemented

The plugin does not currently export a verified firmware signature/version. Any future implementation must prefer cached TunerStudio metadata and perform at most one explicit read-only request during a user-requested diagnostics export, with timeout and honest unavailable/offline reporting. No background polling is authorized.

### Public binary release is not yet attached

Sanitized Apache-2.0 source publication is approved, but ordinary users still need a controlled prebuilt plugin JAR attached through a GitHub Release before the public repository offers a complete no-build installation path.

The public README must state the actual binary availability honestly. Source publication and binary release are separate operations.

### Trusted public synchronization is not yet automated

The private repository can generate and validate a publication-approved export, but the trusted push/branch-promotion workflow is not implemented yet. Until it is, public synchronization is an explicit reviewed action with recorded provenance.

### Public CI still needs an authorized API dependency source

The public repository excludes `lib/TunerStudioPluginAPI.jar` by design. Developers can supply their own authorized copy locally, but fully automated public CI still needs an approved method to provide that dependency without redistributing it.

### TunerStudio Plugin API exact upstream identity is unrecorded

The binary remains private and excluded from publication. Its exact upstream version may need identification for reproducible public builds, but this is no longer a blocker for publishing AE Tuner source under Apache-2.0.

## Controlled historical issues

The following are solved at their stated evidence level and remain regression targets rather than open defects:

- v0.3.17 asynchronous key-off classification;
- exact generated critical-channel resolution;
- actual cut output versus reason-code separation;
- per-state ignition-counter attribution;
- deterministic real-panel integration;
- Session Guidance duplicate suppression and navigation;
- tracked-file conflict-marker validation;
- large `AeTunerPanel` responsibility concentration addressed by bounded helper extraction;
- responsive reachability down to the tested 620 px host width;
- nested Overview/Technical-details wheel handoff;
- PR #12 restored to draft state on 2026-08-02;
- Apache License 2.0 selected for AE Tuner source and documentation;
- public source approved without `TunerStudioPluginAPI.jar`;
- separate private developer README and public end-user README approved;
- earlier scrollbar jumping, clipping, flicker and oversized-card defects.

Physical vehicle evidence remains decisive when a future change touches any of these behaviors.
