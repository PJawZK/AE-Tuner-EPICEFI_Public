# Chat and workstream initialization

## Purpose

This project uses three related but distinct workstreams:

1. **Repository Control Chat** — authoritative continuity, planning, review, documentation, issue creation, PR review and promotion.
2. **Codex Implementation Workstream** — bounded code changes on issue-backed branches.
3. **Vehicle Evidence and Tuning Chat** — logs, reports, videos, physical behavior, tuning interpretation and test planning.

The private repository is the authoritative continuity source. Controlled repository documents override chat recollection, but live fetched refs override stale controlled text and must trigger a documentation repair.

## Required handoff sequence

When the user asks for a handoff, before creating it, remind the user to use this exact order:

1. **Run the handoff pre-flight.**
2. **Create a continuation handoff.**
3. **Continue from the repository.**

The handoff itself is step 2. Step 1 verifies the exact repository state. Step 3 requires the new chat to fetch and verify the private repository independently.

See `docs/HANDOFF_PREFLIGHT.md`.

## Separation rules

- Repository Control owns authoritative state and repository promotion.
- Codex implementation does not broaden tasks beyond issue acceptance criteria.
- Vehicle Evidence does not silently change source authority or claim source changes were merged.
- Private logs, tune files, CSV exports, reports and videos remain outside ordinary Git history.
- Physical conclusions are summarized in controlled documents without committing private source artifacts.
- A rejected or superseded tuning proposal must not become project authority.
- Unrelated work must not move a candidate head frozen for physical validation.
- Public review exports do not become authoritative and do not authorize public synchronization.

## Repository Control Chat prompt

```text
Continue AE Tuner (EPICEFI) from its private authority repository:

PJawZK/AE-Tuner-EPICEFI-

The repository is the authoritative continuity source. Do not depend on a long narrative recap from the previous chat.

First:
1. Fetch current private main and report the exact commit.
2. Fetch the active candidate and task branch named in docs/CURRENT_STATE.md and report exact commits.
3. Read AGENTS.md.
4. Read docs/CURRENT_STATE.md, docs/HANDOFF.md, docs/CHAT_CONTINUATION.md, docs/HANDOFF_PREFLIGHT.md, docs/EVIDENCE_INDEX.md, docs/ROADMAP.md, docs/ARCHITECTURE.md, docs/TRANSIENT_TUNING_STRATEGY.md, docs/SAFETY_AND_SCOPE.md, docs/KNOWN_ISSUES.md, docs/TEST_RESULTS.md, docs/CODEX_WORKFLOW.md, docs/WORKSTREAM_INITIALIZATION.md and docs/REPOSITORY_RELATIONSHIP.md.
5. Read the active issue and PR named in CURRENT_STATE.
6. Report any mismatch between live refs and controlled documents.
7. Summarize the latest valid state, constraints, external evidence required, unresolved issues and exact next action before writes.

This is the Repository Control Chat. Keep it focused on authoritative continuity, roadmap, issues, Codex task definition, PR review, evidence promotion, releases and handoffs.

Preserve strict read-only ECU behavior unless a future controlled safety plan explicitly changes scope.
```

## Vehicle Evidence and Tuning Chat prompt

```text
You are the dedicated Vehicle Evidence and Tuning workstream for AE Tuner (EPICEFI).

Private authority repository:
PJawZK/AE-Tuner-EPICEFI-

This chat handles TunerStudio logs, plugin CSV/report files, screenshots, videos, vehicle behavior, controlled test planning and tuning interpretation. It is not the repository-control chat and must not claim repository writes or merges.

First:
1. Fetch current private main and the active candidate named in docs/CURRENT_STATE.md; report exact commits.
2. Read AGENTS.md, docs/CURRENT_STATE.md, docs/HANDOFF.md, docs/TRANSIENT_TUNING_STRATEGY.md, docs/SAFETY_AND_SCOPE.md, docs/KNOWN_ISSUES.md and docs/TEST_RESULTS.md.
3. Report any live-ref/document mismatch.
4. Summarize current accepted plugin behavior, current tuning stage, required channels and exact evidence question before analysing new files.

Rules:
- Use displayed TunerStudio/MegaLogViewer names unless internal names are explicitly requested.
- Distinguish missing, unresolved, unavailable, inactive and received zero.
- Do not infer safety or causes beyond the supplied evidence.
- Keep MAP Predict, Wall Wetting, Instant Fuel, DFCO, boost control, base fueling and ignition conclusions separated.
- Propose one-variable-at-a-time tests where practical.
- Return a compact evidence package to Repository Control after major conclusions.

At a major evidence milestone, return:
- exact repository commit/plugin version tested;
- canonical JAR SHA-256 and any local-artifact distinction;
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

Do not create routine accumulating checkpoints. Run the handoff pre-flight first, then replace `docs/HANDOFF.md` and `docs/CHAT_CONTINUATION.md` with the latest compact state.

## Public downstream

`PJawZK/AE-Tuner-EPICEFI_Public` is a sanitized downstream target, not an independent workstream authority. Follow `docs/REPOSITORY_RELATIONSHIP.md`. The current review-export workflow does not push to it.
