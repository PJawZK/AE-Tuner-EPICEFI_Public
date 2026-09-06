# Session Guidance automated acceptance

Integrated by PR #7 at commit `0daeda7427538b2d959d1cf3eb4646d13ca045f6`.

## Final clean workflows

- Build and validate run: `30625214191`
- Synthetic plugin integration run: `30625214252`
- Source head validated: `75e870c8c99b5e7268e8f7553188cb3274bf1def`
- Synthetic artifact ID: `8791057353`
- Synthetic artifact digest: `sha256:dba815fbafd406a8d55cccc59fdfc4e71967e13ebe7b5e5047140a7985d4b930`
- Candidate JAR artifact ID: `8791051218`
- Candidate JAR build SHA-256: `862c2f77f1d4bfa49eb04b9f093fa8d7f28747deedabb33e1c52650109e3720d`

The candidate JAR above still reported version `0.3.17` and is not an approved distribution artifact. It was used only to validate the merged feature before the v0.3.18 release-identity step.

## Passed behavior

- [x] Dedicated **Session Guidance** tab exists.
- [x] Clicking **Recommended next step** opens that tab.
- [x] Initial recommendation is recorded once.
- [x] Identical refreshes do not duplicate entries.
- [x] Recommendation, severity or confidence transitions are recorded.
- [x] Critical channel-resolution transitions are recorded.
- [x] Running trigger/sync loss becomes one `CRITICAL / HIGH` entry.
- [x] Accepted event ID `[1]` is retained in the critical entry.
- [x] Ordinary key-off does not duplicate an unchanged running-fault entry.
- [x] The exact Archive 4 asynchronous shutdown fixture adds zero entries.
- [x] Reset clears the temporary timeline.
- [x] Configuration-identity changes clear the temporary timeline.
- [x] Maximum retained entries are bounded to 100.
- [x] Report, CSV, guidance text, shutdown evidence, screenshot and checksums are generated.
- [x] No report, CSV, project-file or ECU persistence is added.
- [x] Java 8 validation passes.

## Final synthetic timeline

Exactly two meaningful entries were produced:

1. `COLLECT_EVIDENCE` — `INFO / LOW`
2. `RUNNING_TRIGGER_SYNC` — `CRITICAL / HIGH`

The Archive 4 fixture retained code 14 and its trigger increment as key-off diagnostics while actual spark and fuel cut outputs remained inactive.

## Release consequence

The feature is accepted at deterministic integration level. A uniquely versioned v0.3.18 consolidated candidate still requires a short stationary physical sanity check before release acceptance.
