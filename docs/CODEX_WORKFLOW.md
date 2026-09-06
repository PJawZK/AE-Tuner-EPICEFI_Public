# Codex / implementation workflow

## Repository model

- `main` is the authoritative integrated branch.
- The private repo intentionally has one long-lived branch.
- A bounded implementation branch may be created for a specific task, then deleted after integration/abandonment.
- Do not create permanent candidate/phase branches merely for provenance.

## Start of a task

Before writing:

1. fetch current `main` and record its exact commit;
2. read `AGENTS.md`;
3. read `README.md`, `docs/CURRENT_STATE.md`, `docs/SAFETY_AND_SCOPE.md`, `docs/GENERAL_AE_TUNER_FRAMEWORK.md`, `docs/GUIDED_TUNING_PRODUCT_MAP.md`, `docs/GUIDED_COACHING_FOUNDATION.md`, `docs/ARCHITECTURE.md`, `docs/EVIDENCE_INDEX.md` and `docs/ROADMAP.md` as relevant;
4. inspect the actual current source/tests instead of relying on historical branch names;
5. state scope, non-goals, safety constraints and validation plan.

## Implementation loop

1. Create a temporary bounded branch only when useful.
2. Implement the smallest coherent change.
3. Add/update deterministic tests.
4. Run the appropriate validation level.
5. Inspect the full diff.
6. Update current-authority docs only when the product state actually changed.
7. Open/review a PR if the workflow benefits from one.
8. Run physical TunerStudio/vehicle evidence when the task requires it.
9. Record exact evidence identity/disposition.
10. Integrate to `main`.
11. Delete the temporary branch.

## Safety contract for implementation tasks

Do not assume the plugin is globally read-only: explicit guarded working-tune Apply/Restore is now part of the product.

However, writes are narrowly constrained:

- only reviewed `ProposalWritePlan` targets;
- centralized `ProposalApplyCoordinator` only;
- stale-check before mutation;
- complete readback verification;
- explicit Restore;
- no automatic Apply;
- no Burn;
- no VE/ignition tuning authority.

A planned Guided scaffold must not gain recommendation/write authority merely because it has a selector entry or Focus proposal.

## Validation

Fast:

```bash
bash scripts/check-fast.sh
```

Full:

```bash
bash scripts/validate.sh
```

Synthetic real-plugin/Swing:

```bash
bash scripts/synthetic-plugin-integration.sh
```

The permanent GitHub `Build and validate` workflow runs full validation and synthetic integration together.

## Physical evidence return

When physical validation is required, report/provide:

- exact source commit;
- JAR SHA-256;
- install/check procedure;
- exact setting/maneuver under test;
- pass/fail criteria;
- rollback/Restore procedure;
- non-sensitive evidence identities.

Do not invent conclusions from missing vehicle evidence.

## Public-release tasks

Public publication is a separate bounded operation from private development.

Before syncing public source:

- audit the existing public repo/tags/releases;
- exclude `lib/TunerStudioPluginAPI.jar` and other private-only material;
- make prerelease/test maturity explicit;
- attach only the exact validated AE Tuner JAR;
- record SHA-256;
- keep no-Burn/no-auto-Apply limitations visible;
- do not describe planned Guided tasks as validated tuning algorithms.
