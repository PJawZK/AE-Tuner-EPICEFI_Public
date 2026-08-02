# Repository relationship and publication boundary

## Repositories

### Private authority

- Repository: `PJawZK/AE-Tuner-EPICEFI-`
- Visibility: private
- Role: authoritative development, evidence metadata, continuation state, pull requests, candidate validation and release decisions
- Contains the tracked `lib/TunerStudioPluginAPI.jar` required by the current private build

### Public downstream

- Repository: `PJawZK/AE-Tuner-EPICEFI_Public`
- Visibility: public
- Role: sanitized downstream publication target only
- It is not an independent authority and must not silently diverge from the private repository

The private repository remains the sole authority until a future decision explicitly changes that model.

## Approved publication model

The following decisions are approved:

- AE Tuner source code and project documentation use the Apache License, Version 2.0;
- public publication excludes `lib/TunerStudioPluginAPI.jar`;
- the private repository keeps its developer/authority README;
- the public repository receives a separate end-user README;
- the public repository may contain sanitized source and documentation when provenance and leak checks pass.

The TunerStudio Plugin API exclusion is a standing publication rule, not a remaining blocker.

## Publication rules

Public content must be generated from an exact private source commit through the controlled export policy. Do not manually copy a changing selection of files and then treat it as reproducible.

The public export must exclude at least:

- `lib/TunerStudioPluginAPI.jar`;
- vehicle logs, tune files, reports, CSV exports, archives, screenshots and videos;
- credentials, registrations, keys, tokens and local configuration;
- machine-specific or personal paths;
- private continuation documents and private evidence indexes unless a separately reviewed sanitized version is supplied;
- generated build output.

The public export must include:

- the Apache-2.0 `LICENSE` file;
- the project `NOTICE` file;
- the end-user public README;
- third-party dependency notices;
- provenance identifying the exact private source commit and export-policy version.

## Public README policy

The private root `README.md` is intentionally developer- and authority-oriented.

The public source is maintained at:

```text
docs/public/README.md
```

During export it is promoted to the public repository root as:

```text
README.md
```

The private README must not appear in the public export. The public README must clearly state that ordinary users install a prebuilt plugin JAR and do not need to compile source.

## Controlled export

The private repository provides:

- `config/public-export-allowlist.txt` — explicit export scope;
- `scripts/build-public-export.sh` — creates a clean sanitized directory from tracked files only;
- `scripts/check-public-export.sh` — rejects forbidden paths and high-risk content and verifies the approved licence/README;
- `.github/workflows/public-export-validate.yml` — builds and uploads a review artifact without pushing to the public repository.

Export modes:

```bash
# Review artifact
bash scripts/build-public-export.sh target/public-export

# Publication-approved source export
PUBLIC_EXPORT_MODE=publish bash scripts/build-public-export.sh target/public-export
```

The review workflow remains non-publishing. Public synchronization requires an explicit authorized action and branch/status mapping.

## Branch mapping

- private `main` represents the accepted integrated baseline;
- public `main` contains only an explicitly approved sanitized stable baseline;
- an active private candidate may map to a clearly named public candidate branch;
- a public candidate branch must identify the exact private source commit and must never be presented as an accepted release;
- private evidence and continuation-only changes that do not affect the export need not create a public commit.

Current intended mapping:

```text
private main                                  -> public main
private agent/per-rpm-blend-duration-evidence -> public candidate/v0.3.19
```

Publication-support commits may wrap the exact production source with licence, end-user documentation and export tooling, provided provenance identifies both the publication source commit and the frozen production candidate where applicable.

## Provenance requirements

For each public synchronization record:

- private repository source commit;
- private source branch;
- accepted or candidate status;
- plugin version;
- export-policy file checksum;
- exported-tree checksum or public commit;
- excluded dependency statement;
- publication date;
- reviewer/disposition.

## Remaining release work, not publication blockers

The source may now be published. Separate work may still be required to:

- attach prebuilt plugin JARs to GitHub Releases;
- create stable and candidate public branch protection;
- automate trusted private-to-public synchronization;
- establish a repeatable public CI method for obtaining an authorized Plugin API JAR;
- publish issue templates and extended end-user documentation.

These items affect release convenience and automation, not the approved right to publish the sanitized Apache-2.0 source.

## Safety

Public synchronization must not modify ECU behavior, tuning algorithms or candidate acceptance. It is a repository publication operation only. The plugin remains strictly read-only unless a future separately approved safety architecture changes scope.
