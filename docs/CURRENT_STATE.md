# AE Tuner current public state

- Latest public release: **v0.4.6**; previous: **v0.4.5**
- Source branch: `main`
- Exact qualified public-identity build source: `baddb47d3a13b2e772d5b6154f735f2f8c2ddb38`
- GitHub Actions: **#1932 / run 40725785125 — PASS**
- Canonical public JAR: `ae-tuner-epicefi-0.4.6.jar`
- JAR SHA-256: `31e23679f055789be0073f35e810f1ae793e0713dfc381c92e293c98ecdc19dd`
- Java target: Java 8; compilation/CI JDK: Java 17

## Runtime and capabilities

Guided v0.19 remains the production workspace. It offers Foundation, TPS AE, MAP Predict, Wall Wetting, Instant Fuel and Decel/Tip-out workflows. Recent changes improve Cancel/Start Over, current-state coaching, historical Decel analysis and independent cross-log physical-event pooling, responsive scroll/wrap/fixed-footer pop-outs and lifecycle/recovery/export performance.

Decel analysis uses threshold-independent physical event classification (`classification-boundary-v2`) and refuses incompatible cross-log pooling (`detector-compatible-pool-v1`). Unresolved cells are **NEED EVIDENCE**, not inferred zero or extrapolated values.

## Safety

Only explicitly reviewed plans can request Working Tune/RAM writes through `ProposalWritePlan → ProposalApplyCoordinator → stale check → write → exact readback → rollback/Restore`. No automatic Apply, no Burn, no VE/ignition tuning.

Software safety/regression checks passed; broader vehicle and task-specific numerical tuning qualification remains unfinished. See `docs/KNOWN_ISSUES.md` and `docs/RELEASE_0.4.6.md` for scope.

## Source integrity

Public `src/`, `scripts/`, `pom.xml` are synchronized with the qualified release build source. The third-party TunerStudio Plugin API binary and all private vehicle data are excluded.
