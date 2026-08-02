# Codex and GitHub workflow

## Repository model

- Private authority: `PJawZK/AE-Tuner-EPICEFI-`.
- Public sanitized downstream: `PJawZK/AE-Tuner-EPICEFI_Public`.
- `main` in the private repository is the authoritative accepted integrated branch.
- Every implementation task starts from a GitHub issue or a clearly bounded task document.
- Codex works on one task branch at a time.
- Pull requests carry code, tests, documentation and exact validation results.
- Physical vehicle evidence is returned after the code candidate exists and is recorded in controlled documents before merge or in a follow-up evidence PR, depending on the task gate.
- Public review exports are generated from exact private commits and do not become authoritative merely by existing.

## Handoff and new-session gate

When a handoff is requested, first remind the user of this exact sequence:

1. **Run the handoff pre-flight.**
2. **Create a continuation handoff.**
3. **Continue from the repository.**

Before Codex continues a task from another chat/session, the new session must fetch and verify the private repository rather than trust pasted state alone.

Run before creating the handoff:

```bash
bash scripts/handoff-preflight.sh
```

Use `--validate` at a major implementation/evidence/release milestone.

## Codex environment

Suggested repository setup command:

```bash
bash scripts/setup-codex-java.sh
```

Primary validation command:

```bash
bash scripts/validate.sh
```

## Task branch naming

Examples:

```text
agent/map-estimate-coverage-report
agent/blend-duration-single-burst-tests
agent/channel-alias-resolution
agent/long-session-performance
agent/on-demand-diagnostics-export
agent/repository-authority-preflight
```

Avoid permanent catch-all development branches.

## Task input contract

Every Codex task should state:

- private authority repository;
- authoritative branch and exact starting commit;
- issue/task ID;
- current accepted plugin version;
- active candidate and whether its head is frozen for physical validation;
- problem and evidence;
- in-scope files/behavior;
- explicit non-goals;
- safety constraints;
- acceptance criteria;
- tests/builds to run;
- documentation to update;
- whether public-export policy is affected.

Use `docs/templates/CODEX_TASK.md`.

## Recommended implementation loop

1. Repository Control creates or refines the issue/task.
2. Codex fetches current private `main` and reports the exact commit.
3. Codex fetches any named candidate/task branch and reports its exact commit.
4. Codex reads `AGENTS.md` and controlled state/handoff documents.
5. Codex reports any mismatch between live refs and controlled documents before writes.
6. Codex summarizes current state, constraints and exact plan.
7. Codex creates a bounded branch from the authorized exact starting commit.
8. Implement the smallest reliable change.
9. Add or update deterministic tests/fixtures where possible.
10. Run `bash scripts/validate.sh`.
11. Update controlled documentation.
12. Inspect the complete diff.
13. Open a PR only when a real remote branch exists.
14. Repository Control reviews the PR and coordinates physical evidence.
15. User performs TunerStudio/vehicle testing when required.
16. Evidence and disposition are recorded.
17. Merge only after the task's acceptance gate is met.
18. At a major milestone, run handoff pre-flight before producing a continuation handoff.

## Frozen-candidate rule

The current v0.3.19 physical-validation candidate is:

- branch `agent/per-rpm-blend-duration-evidence`;
- head `dc89032595a043e4d894ae039987529d55cf63ed`;
- PR #12.

Unrelated documentation, repository-control, diagnostics-export or publication work must use a separate branch and must not move that candidate head. Changing the candidate head invalidates exact artifact and host evidence until rerun.

## Codex prompt for a new implementation task

```text
Continue AE Tuner (EPICEFI) from its private authority repository:

PJawZK/AE-Tuner-EPICEFI-

The private repository is the authoritative continuity source.

First:
1. Fetch current main and report the exact commit.
2. Fetch the active candidate/task branch named in docs/CURRENT_STATE.md and report the exact commit.
3. Read AGENTS.md.
4. Read README.md, docs/CURRENT_STATE.md, docs/HANDOFF.md, docs/CHAT_CONTINUATION.md, docs/HANDOFF_PREFLIGHT.md, docs/EVIDENCE_INDEX.md, docs/ARCHITECTURE.md, docs/TRANSIENT_TUNING_STRATEGY.md, docs/SAFETY_AND_SCOPE.md, docs/KNOWN_ISSUES.md, docs/TEST_RESULTS.md, docs/ROADMAP.md and docs/REPOSITORY_RELATIONSHIP.md.
5. Read the GitHub issue/task and any files it identifies.
6. Report any mismatch between fetched refs and controlled documents.
7. Summarize the latest valid state, constraints, evidence, unresolved issue and exact implementation plan before writing.

Use displayed TunerStudio/MegaLogViewer names in user-facing surfaces. Preserve strict read-only ECU behavior. Do not add writes or burns. Run bash scripts/validate.sh and report executed failures separately from unavailable checks.
```

## Physical evidence return

Codex must not invent conclusions from missing vehicle evidence. When physical validation is required, the PR or task result should provide:

- exact candidate commit and canonical JAR checksum;
- explicit distinction from any local development JAR;
- install instructions;
- bounded test procedure;
- channels/reports to capture;
- expected pass/fail observations;
- rollback instruction.

The user then returns logs, CSV/report, screenshots/video and subjective behavior. Repository Control records the conclusion.

## Public export boundary

Codex may update public-export policy only when the task explicitly includes it. It must not:

- copy `lib/TunerStudioPluginAPI.jar` into public output;
- push to the public repository without separately approved synchronization authorization;
- claim source publication rights before a licence is selected;
- include private evidence or continuation files outside the reviewed allowlist.
