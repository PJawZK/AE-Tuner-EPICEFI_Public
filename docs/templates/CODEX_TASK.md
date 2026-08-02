# Codex task template

## Identity

- Private authority repository:
- Issue/task:
- Starting branch:
- Required starting commit:
- Target branch:
- Current accepted plugin version:
- Active candidate version/branch/head:
- Is the active candidate head frozen for physical validation?:
- Proposed version:
- Public-export policy affected?: yes/no

## Pre-write verification

- [ ] Fetched private `main` and recorded exact head.
- [ ] Fetched required starting/candidate branch and recorded exact head.
- [ ] Compared live refs with `docs/CURRENT_STATE.md`.
- [ ] Reported any mismatch before writes.
- [ ] Confirmed the task will not move an unrelated frozen candidate.

## Problem

Describe the observed issue and include exact evidence filenames, screenshots, logs or code paths.

## Current accepted behavior

State what must remain unchanged.

## In scope

- 

## Non-goals

- No ECU writes or burns.
- No unrelated refactor.
- No silent change to a frozen physical-validation candidate.
- No public repository push unless separately authorized.
- 

## Safety and terminology constraints

- Use displayed TunerStudio/MegaLogViewer names in user-facing surfaces.
- Distinguish missing, unresolved, unavailable, inactive and received zero.
- Preserve Java 8 compatibility.
- Preserve read-only behavior.
- Preserve raw values and evidence identity.

## Acceptance criteria

1. 
2. 
3. `bash scripts/validate.sh` result is recorded.
4. Required controlled documents are updated.
5. Exact final commit and canonical artifact identity are reported when an artifact is produced.
6. Local development artifacts are clearly distinguished from canonical CI artifacts.

## Required tests

- Deterministic/local:
- TunerStudio runtime:
- Physical vehicle:
- Public review export, when affected:

## Required documentation updates

- `docs/CURRENT_STATE.md`
- `docs/KNOWN_ISSUES.md`
- `docs/TEST_RESULTS.md`
- `docs/HANDOFF.md`
- `docs/CHAT_CONTINUATION.md`
- `docs/EVIDENCE_INDEX.md`
- `CHANGELOG.md`

## Handoff requirement

At a major milestone, remind the user and follow this order:

1. Run the handoff pre-flight.
2. Create a continuation handoff.
3. Continue from the repository.

## Expected result report

- Root cause:
- Starting commit:
- Final commit:
- Changed files:
- Tests executed:
- Build result:
- Unavailable checks:
- Remaining uncertainty:
- Canonical CI JAR SHA-256:
- Local development JAR SHA-256, when applicable:
- Physical disposition required?:
- Candidate head preserved or intentionally replaced?:
