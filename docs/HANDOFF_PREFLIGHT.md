# Handoff pre-flight and chat continuation

This document defines the required sequence for moving AE Tuner work into a new chat or agent session.

## Required reminder

When the user asks for a handoff, before creating the handoff, remind the user to follow this sequence in this exact order:

1. **Run the handoff pre-flight.**
2. **Create a continuation handoff.**
3. **Continue from the repository.**

The requested handoff is step 2. Do not silently skip step 1 unless the handoff pre-flight has already completed successfully for the exact repository state being handed off. Step 3 happens in the new chat after the continuation handoff exists.

## Step 1 — Run the handoff pre-flight

From a clean checkout of the private authority repository:

```bash
bash scripts/handoff-preflight.sh
```

Use `--validate` when a full build/regression validation is appropriate for the milestone:

```bash
bash scripts/handoff-preflight.sh --validate
```

The pre-flight must:

- fetch current remote refs unless explicitly run offline;
- report the current branch and exact commit;
- report the remote `main` head;
- verify the documented active candidate branch and candidate source head;
- reject uncommitted or staged changes;
- verify current authority documents do not contain known stale continuation instructions;
- verify the private/public repository relationship and safety boundary are documented;
- optionally run the full repository validation;
- report any uncertainty instead of inventing a successful state.

A failed pre-flight means the handoff must first explain and resolve, or explicitly preserve, the failure. Do not present stale repository state as authoritative.

## Step 2 — Create a continuation handoff

Replace, rather than append to, the compact continuation state in:

- `docs/HANDOFF.md` — exact current handoff;
- `docs/CHAT_CONTINUATION.md` — compact prompt/state for a new chat;
- `docs/CURRENT_STATE.md` — active authority, accepted baseline, candidate identity and exact next action.

A continuation handoff should contain only:

- private authority repository and exact relevant branch/commit identities;
- accepted release and candidate identity;
- canonical artifact identities, clearly separated from local development artifacts;
- latest valid evidence and disposition;
- preserved safety and algorithm constraints;
- unresolved issues;
- required files not stored in Git;
- the exact next action.

Do not copy a long narrative chat history into the handoff. Historical records remain in their dedicated evidence and acceptance documents.

## Step 3 — Continue from the repository

The new chat or agent session must begin by fetching the current private repository state, not by trusting the pasted handoff alone.

Read in this order:

1. `AGENTS.md`
2. `docs/CURRENT_STATE.md`
3. `docs/HANDOFF.md`
4. `docs/CHAT_CONTINUATION.md`
5. `docs/EVIDENCE_INDEX.md`
6. the active issue and pull request named in `docs/CURRENT_STATE.md`
7. task-specific architecture, safety, strategy and test documents

Then report:

- exact fetched `main` head;
- exact fetched active candidate head;
- whether those heads match the controlled documents;
- accepted release versus unaccepted candidate;
- unresolved evidence gate;
- exact next action before writes.

## Terminology

- **Handoff pre-flight** means verification of the live repository and controlled continuation state.
- **Continuation handoff** means the compact state package created after a successful or explicitly qualified pre-flight.
- **Continue from the repository** means the new chat independently re-fetches and verifies repository authority before working.
