# Known issues and limitations

Use `docs/CURRENT_STATE.md` for current authority.

## Open product limitations

### Many Guided tasks are proposal/scaffold maturity

`0.4.2-rc.2` exposes a coherent task map and coaching proposal foundation, but not every task has validated evidence accumulation, recommendation logic or write authority.

Planned tasks are intentionally prevented from pretending to be implemented. External testers should distinguish UX/product feedback from validated tuning-algorithm behavior.

### TPS Movement / Timing is an early coaching implementation

RC2 removes the temporary Engagement Model editor and five-model normal-driver comparison. The Focus now centers on TPS movement, production `Fuel: TPS AE change` and `AccelThreshold`, with Delta Window as the only current A/B edit target. This interaction is intentionally expected to evolve from real vehicle use and tester feedback.

### Sample Length is read-only pending an independent tuning rationale

The controller value remains visible as working-tune context, but AE Tuner does not currently propose or apply Sample Length changes. Before returning it to tuning authority, firmware semantics and real evidence must show a useful independent calibration question beyond the current Dual Stride/Newest + Delta Window workflow.

### Fast Callback is read-only prerequisite/context

AE Tuner reads Fast Callback state but does not tune it. The intended detector workflow uses the fast callback at approximately 200 Hz. If it is unavailable/off, Guided may inform the user, but it must not turn this controller scheduling choice into an AE tuning experiment.

### MAP Estimate remains load/context sensitive

RPM and TPS do not uniquely determine MAP on a turbo engine under all gear/load/spool conditions. Coverage count alone is insufficient; spread, stability and operating context matter. Unvisited cells must remain unchanged.

### Some condition-mapping tasks require future cold/warm runs

TPS AE temperature compensation and advanced Wall Wetting mapping cannot be fully characterized in one already-warm session. Guided should derive useful future condition targets from the actual working-tune correction axes rather than inventing generic temperature bands.

### Vehicle low-RPM bog is not a resolved plugin issue

Earlier vehicle evidence showed bog-like behavior below roughly 2000 RPM. No final accepted transient-fuelling correction exists. A software release must not imply that this vehicle tuning issue is solved.

### Missing channels limit conclusions

Fuel pressure, target lambda, measured lambda, ignition/timing context, cuts/faults, prediction activity and individual transient-fuel contributions may be unavailable depending on controller/INI/runtime state.

Missing/unresolved data must remain unknown rather than being interpreted as zero.

### Public dependency boundary remains a release invariant

The RC1 publication proved the sanitized public-source path and confirmed that `TunerStudioPluginAPI.jar` is absent from public source/release assets. Keep this as a mandatory check for every future public sync.

### GitHub Release publication path is proven

The public repository can publish the exact validated JAR plus checksum through a bounded one-shot release path. Keep ordinary development CI separate from release publication and do not leave release-only workflows on `main`.

## Resolved / controlled

### Detector Delta Window representation

Resolved before RC publication work. The real TunerStudio scalar representation physically passed:

`25 ms -> temporary 24 ms -> Apply/readback PASS -> Restore 25 ms PASS`

No burn occurred. The temporary 24 ms value was a representation test, not a tuning recommendation.

### Temporary Engagement Model editing/research surface

Resolved in RC2. The multi-model comparison and Engagement Model write experiment were development tools used to evaluate detector behavior/host representation. Normal AE Tuner no longer exposes Engagement Model as a tuning control; Dual Stride / Newest is read-only context for the current Guided workflow.

### Detector Focus root scrolling / settings-first layout

Resolved structurally in RC2. TPS Movement / Timing Driver View has no root scroll container and puts visual/audio coaching ahead of the secondary Delta Window experiment control.

### Private branch forest

Resolved 2026-08-28. The private repository has one long-lived branch: `main`.

### CI workflow sprawl

Resolved 2026-08-28. One permanent `Build and validate` workflow owns full regression validation, synthetic integration, checksum reporting and JAR artifact upload.

### Guarded write isolation

Physically validated across the working-tune write foundation, including Blend Duration, MAP Estimate table writes and Detector Delta Window scalar Apply/Restore.

### Adaptive Guided runtime / lifecycle architecture

Earlier runtime, queue/backlog, hide/show and Guided correctness defects are retained as regression history and are not current known blockers unless reproduced.
