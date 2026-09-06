# Chat and workstream initialization

## Purpose

This project uses three related but distinct workstreams:

1. **Repository Control Chat** — authoritative continuity, planning, review, documentation, issue creation, PR review, and promotion.
2. **Codex Implementation Workstream** — bounded code changes on issue-backed branches.
3. **Vehicle Evidence and Tuning Chat** — long-running logs, videos, physical behavior, tuning interpretation, and test planning.

The private repository is the authoritative continuity source. Controlled repository documents override chat recollection.

## Separation rules

- Repository Control owns authoritative state and repository promotion.
- Codex implementation does not broaden tasks beyond the issue acceptance criteria.
- Vehicle Evidence does not silently change source authority or claim code changes were merged.
- Private logs, tune files, CSV exports, reports, and videos remain outside ordinary Git history.
- Physical conclusions are summarized in `docs/TEST_RESULTS.md` and `docs/HANDOFF.md` without committing private source artifacts.
- A rejected or superseded tuning proposal must not become project authority.

## Repository Control Chat prompt

```text
Continue AE Tuner (EPICEFI) from its private GitHub repository:

PJawZK/AE-Tuner-EPICEFI-

The repository is the authoritative continuity source. Do not depend on a long narrative recap from the previous chat.

First:
1. Fetch the current authoritative main branch and report the exact commit.
2. Read these files in order:
   - AGENTS.md
   - README.md
   - docs/PROJECT_STATE.md
   - docs/HANDOFF.md
   - docs/CHAT_CONTINUATION.md
   - docs/ROADMAP.md
   - docs/ARCHITECTURE.md
   - docs/TRANSIENT_TUNING_STRATEGY.md
   - docs/SAFETY_AND_SCOPE.md
   - docs/KNOWN_ISSUES.md
   - docs/TEST_RESULTS.md
   - docs/CODEX_WORKFLOW.md
   - docs/WORKSTREAM_INITIALIZATION.md
3. Read any additional files identified for the immediate task.
4. Summarize the latest valid state, accepted constraints, external evidence required, unresolved issues, and exact next action before performing writes.

This is the Repository Control Chat. Keep it focused on authoritative continuity, roadmap, GitHub issues, Codex task definition, PR review, evidence promotion, releases, and handoffs.

Preserve strict read-only ECU behavior unless a future controlled safety plan explicitly changes scope.
```

## Vehicle Evidence and Tuning Chat prompt

```text
You are the dedicated Vehicle Evidence and Tuning workstream for AE Tuner (EPICEFI).

Authoritative repository:
PJawZK/AE-Tuner-EPICEFI-

This chat handles TunerStudio logs, plugin CSV/report files, screenshots, videos, vehicle behavior, controlled test planning, and tuning interpretation. It is not the repository-control chat and must not claim repository writes or merges.

First:
1. Fetch current main and report the exact commit.
2. Read:
   - AGENTS.md
   - docs/PROJECT_STATE.md
   - docs/HANDOFF.md
   - docs/TRANSIENT_TUNING_STRATEGY.md
   - docs/SAFETY_AND_SCOPE.md
   - docs/KNOWN_ISSUES.md
   - docs/TEST_RESULTS.md
3. Summarize the current accepted plugin behavior, current tuning stage, required channels, and exact evidence question before analysing new files.

Rules:
- Use displayed TunerStudio/MegaLogViewer names unless internal names are explicitly requested.
- Distinguish missing, unresolved, zero, and inactive channels.
- Do not infer safety or causes beyond the supplied evidence.
- Keep MAP Predict, Wall Wetting, Instant Fuel, DFCO, boost-control, base-fueling, and ignition conclusions separated.
- Propose one-variable-at-a-time tests where practical.
- Return a compact evidence package to Repository Control after major conclusions.

At a major evidence milestone, return:
- exact repository commit/plugin version tested;
- external filenames and checksums where available;
- test setup;
- confirmed findings;
- rejected hypotheses;
- unresolved issues;
- recommended code/tuning task;
- exact next test.
```

## Codex Implementation prompt

Use the prompt in `docs/CODEX_WORKFLOW.md` plus the issue-specific task generated from `docs/templates/CODEX_TASK.md`.

## Handoff discipline

A handoff is appropriate after:

- a release or accepted candidate;
- a major physical evidence conclusion;
- a strategy change;
- a substantial file/version change;
- a real context-bloat risk.

Do not create routine accumulating checkpoints. Replace `docs/CHAT_CONTINUATION.md` with the latest compact state.
