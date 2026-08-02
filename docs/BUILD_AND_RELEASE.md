# Build and release

## Supported build target

- Source language: Java
- Bytecode target: Java 8
- Build systems:
  - repository script using `javac`/`jar`;
  - Maven when available.

## Repository build

```bash
bash scripts/build.sh
```

Output:

```text
dist/ae-tuner-epicefi-<version>.jar
```

The repository build uses a fixed archive timestamp when supported by the installed `jar` tool. Java bytecode remains targeted to release 8.

## Full validation

```bash
bash scripts/validate.sh
```

Validation checks:

- source compiles with `--release 8`;
- JAR is produced;
- manifest plugin entry is correct;
- version is consistent across source, POM and artifact name;
- class major version is Java 8-compatible;
- deterministic regressions pass;
- long-session characterization runs;
- validation-tooling regression passes;
- tracked files contain no merge conflict markers;
- current continuation authority is internally consistent in the private repository;
- Git whitespace check runs when inside a Git checkout.

## Handoff pre-flight

Before creating a continuation handoff:

```bash
bash scripts/handoff-preflight.sh
```

At a release, candidate, major evidence or substantial repository-state milestone:

```bash
bash scripts/handoff-preflight.sh --validate
```

The required order is:

1. Run the handoff pre-flight.
2. Create a continuation handoff.
3. Continue from the repository.

See `docs/HANDOFF_PREFLIGHT.md`.

## TunerStudio installation

1. Remove old `ae-tuner-epicefi-*.jar` files from the TunerStudio plugins directory.
2. Copy the intended accepted or candidate JAR into the plugins directory.
3. Restart TunerStudio.
4. Confirm only one AE Tuner entry appears.
5. Confirm displayed version.
6. Confirm project/settings and required channels resolve.
7. Record the exact JAR SHA-256 used for any physical evidence.

Normal users install only the AE Tuner plugin JAR. `TunerStudioPluginAPI.jar` is a source-build dependency and is not a separate end-user installation step.

## Artifact identity

Always distinguish:

- **canonical CI artifact** — produced by the clean repository workflow at the exact candidate head;
- **local development artifact** — built from local source/tooling and useful for development smoke testing only unless separately promoted;
- **historical/superseded artifact** — retained for traceability but not current.

Matching source content does not guarantee matching JAR bytes when different JDK tooling or archive timestamps are used. Do not replace a canonical CI hash with a local development hash.

## Release gate

A release candidate requires:

- completed code review;
- clean validation result at the exact final source head;
- canonical candidate JAR SHA-256;
- TunerStudio load test appropriate to the host-registration boundary;
- UI scaling/regression test;
- matched live evidence for algorithm or safety changes;
- updated `CURRENT_STATE`, `KNOWN_ISSUES`, `TEST_RESULTS`, `HANDOFF`, `CHAT_CONTINUATION`, `EVIDENCE_INDEX` and `CHANGELOG`;
- successful handoff pre-flight;
- no private logs, tunes, videos or generated evidence in Git;
- explicit physical disposition;
- merge authorization.

PR #12 remains unmerged until controlled v0.3.19 held-opening evidence receives an explicit `ACCEPT` disposition.

## Versioning

Use semantic-style versions:

- patch: bounded fix or diagnostic refinement;
- minor: meaningful workflow/analysis feature;
- major: incompatible architecture or safety-scope change.

The following must match:

- `pom.xml` version;
- `AeTunerPlugin.VERSION`;
- README candidate/release identity;
- CHANGELOG heading;
- JAR filename;
- manifest `Implementation-Version`.

## Release artifacts

Generated JARs and reports belong in ignored `dist/`, `target/` or external release storage, not ordinary source commits.

Public binary releases should attach at least:

```text
ae-tuner-epicefi-<version>.jar
SHA256SUMS.txt
```

The release notes must state stable versus candidate status, source commit, Java target, supported host boundary and read-only safety scope.

## Source licence and third-party dependency

AE Tuner source and project documentation are licensed under Apache License 2.0. Public distributions must contain:

```text
LICENSE
NOTICE
THIRD_PARTY_NOTICES.md
```

`lib/TunerStudioPluginAPI.jar` is a separate third-party dependency. It must not be committed to the public repository, included in public source exports or embedded in AE Tuner release JARs.

Public-source developers obtain an authorized copy independently and place it at the documented `lib/` path. This exclusion is an approved publication rule and no longer blocks source publication.

## Public export

Create a review export:

```bash
bash scripts/build-public-export.sh target/public-export
```

Create a publication-approved sanitized export:

```bash
PUBLIC_EXPORT_MODE=publish bash scripts/build-public-export.sh target/public-export
```

The exporter:

- copies tracked files only;
- follows `config/public-export-allowlist.txt`;
- promotes `docs/public/README.md` to the public root `README.md`;
- includes Apache-2.0 `LICENSE` and `NOTICE`;
- excludes the private root README and `lib/TunerStudioPluginAPI.jar`;
- rejects evidence, archive, media, credential and personal-path leakage;
- writes exact source/provenance metadata;
- does not push or modify the public repository by itself.

`.github/workflows/public-export-validate.yml` uploads the sanitized directory as a short-retention review artifact.

## Public publication gate

Sanitized source publication is authorized when all of the following pass:

- final export source commit is recorded;
- public branch is clearly labeled accepted versus candidate;
- Apache-2.0 `LICENSE`, `NOTICE` and third-party notices are present;
- the public end-user README is at the repository root;
- `TunerStudioPluginAPI.jar` is absent;
- private continuation/evidence files are absent;
- provenance matches the exported source;
- final leak check and public-export CI pass;
- the public commit/tree digest is recorded back in private authority documents.

Attaching a prebuilt JAR, enabling branch protection and adding automatic synchronization are separate release/operations tasks. They do not block publication of the approved sanitized source.

See `docs/REPOSITORY_RELATIONSHIP.md`.
