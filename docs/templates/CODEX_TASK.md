# Codex task template

## Identity

- Issue:
- Starting branch:
- Required starting commit:
- Target branch:
- Current plugin version:
- Proposed version:

## Problem

Describe the observed issue and include exact evidence filenames, screenshots, logs, or code paths.

## Current accepted behavior

State what must remain unchanged.

## In scope

- 

## Non-goals

- No ECU writes or burns.
- No unrelated refactor.
- 

## Safety and terminology constraints

- Use displayed TunerStudio/MegaLogViewer names in user-facing surfaces.
- Distinguish missing, unresolved, zero, and inactive channels.
- Preserve Java 8 compatibility.
- Preserve read-only behavior.

## Acceptance criteria

1. 
2. 
3. `bash scripts/validate.sh` result is recorded.
4. Required controlled documents are updated.

## Required tests

- Deterministic/local:
- TunerStudio runtime:
- Physical vehicle:

## Required documentation updates

- `docs/PROJECT_STATE.md`
- `docs/KNOWN_ISSUES.md`
- `docs/TEST_RESULTS.md`
- `docs/HANDOFF.md`
- `CHANGELOG.md`

## Expected result report

- Root cause:
- Changed files:
- Tests executed:
- Build result:
- Unavailable checks:
- Remaining uncertainty:
- Candidate JAR SHA-256:
