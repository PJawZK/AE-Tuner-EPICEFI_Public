# Decision log

## D-001 — Repository is the continuity authority

Status: accepted  
Date: repository bootstrap

The private authority repository overrides chat recollection. Controlled continuation documents are compact and replaceable, but a new session must fetch live refs and report any mismatch before work.

## D-002 — Keep accepted plugin read-only

Status: accepted

Generated values are reviewed and manually pasted into TunerStudio. ECU writes and burns are outside current scope.

## D-003 — Use displayed TunerStudio/MegaLogViewer names

Status: accepted

User-facing UI, reports, CSV fields and discussions use displayed names. Internal names remain implementation details unless explicitly requested.

## D-004 — Primary transient strategy

Status: accepted

Tune MAP Predict first, Wall Wetting second and Instant Fuel last. Legacy TPS cycle AE remains a separate compatibility mode.

## D-005 — Repeated stabs are not base Blend Duration evidence

Status: accepted

Multiple distinct TPS-change bursts are drift diagnostics and are excluded from the base Blend Duration curve.

## D-006 — Preserve unvisited MAP Estimate cells

Status: accepted

A turbo cap may constrain an observed proposal but may not rewrite unvisited cells as though they were measured.

## D-007 — Private evidence stays outside normal Git history

Status: accepted

Logs, tune files, CSV exports, reports and videos are referenced by metadata and conclusions in controlled documents.

## D-008 — Three-workstream model

Status: accepted

Repository Control owns authority and promotion; Codex performs bounded implementation; Vehicle Evidence handles physical data and tuning interpretation.

## D-009 — Ordered handoff continuation process

Status: accepted  
Date: 2026-08-02

When a handoff is requested, the user must be reminded of this ordered sequence before the handoff is created:

1. Run the handoff pre-flight.
2. Create a continuation handoff.
3. Continue from the repository.

The pre-flight verifies live refs, clean state and controlled-document consistency. The new chat independently fetches the repository instead of trusting the pasted handoff alone.

## D-010 — Freeze exact physical-validation candidates

Status: accepted  
Date: 2026-08-02

Unrelated work must not move a candidate head after exact CI/artifact/host evidence has been recorded for physical validation. Documentation, diagnostics and repository-control changes use separate branches.

Current frozen candidate:

- version `0.3.19`;
- branch `agent/per-rpm-blend-duration-evidence`;
- head `dc89032595a043e4d894ae039987529d55cf63ed`;
- PR #12.

## D-011 — Private authority and public sanitized downstream

Status: accepted  
Date: 2026-08-02

`PJawZK/AE-Tuner-EPICEFI-` remains the private authority. `PJawZK/AE-Tuner-EPICEFI_Public` is a sanitized downstream target only.

Public content must be generated from an exact private commit through an explicit allowlist, licence check, provenance record and leak check. Public synchronization does not change candidate acceptance or ECU behavior.

## D-012 — Review export remains non-publishing automation

Status: accepted  
Date: 2026-08-02

The private repository may build and upload a sanitized review artifact without automatically pushing to the public repository. This keeps pull-request validation separate from a trusted publication action.

Manual or future automated publication is permitted only after an explicit authorized action, exact branch/status mapping and successful final export validation.

## D-013 — TunerStudio Plugin API binary remains private

Status: accepted  
Date: 2026-08-02

`lib/TunerStudioPluginAPI.jar` is excluded from public exports and public releases. Public-source developers obtain an authorized copy independently. Normal users installing a prebuilt AE Tuner JAR do not install the API JAR separately.

The binary is not covered by AE Tuner's source licence. Its exclusion is mandatory but does not block publication of AE Tuner source.

## D-014 — Apache-2.0 source publication

Status: accepted  
Date: 2026-08-02

AE Tuner source code and project documentation are licensed under the Apache License, Version 2.0.

Public distributions include:

- `LICENSE` containing the full Apache-2.0 text;
- `NOTICE`;
- `THIRD_PARTY_NOTICES.md`;
- provenance identifying the exact private source commit.

Sanitized source publication is authorized when the controlled export passes.

## D-015 — Separate private and public README files

Status: accepted  
Date: 2026-08-02

The private root `README.md` remains developer- and authority-oriented.

The public end-user README is maintained at:

```text
docs/public/README.md
```

The exporter promotes it to the public repository root as `README.md` and excludes the private root README. The public README must make clear that ordinary users install a prebuilt plugin JAR and do not need to compile source.

## Pending decisions

- Trusted authenticated private-to-public synchronization and branch-promotion implementation.
- Public binary release/tag workflow.
- TunerStudio Plugin API exact upstream version identification, if needed for reproducible public CI.
- Whether and when a guarded ECU-write safety plan should be authored.
- Whether a completed-event retention policy is needed after further measured long-session evidence.
