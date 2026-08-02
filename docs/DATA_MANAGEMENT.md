# Evidence and data management

## Do not commit by default

- `.msl` and `.mlg` logs;
- `.msq` tune files;
- plugin CSV exports;
- generated MAP Predict or diagnostics reports;
- screenshots and videos;
- evidence archives;
- machine-specific paths;
- credentials, registrations or secrets;
- private controller definitions unless explicitly reviewed for repository inclusion.

Use ignored `local-evidence/` or external storage.

## Controlled evidence reference

For each important session record:

- date/time;
- repository commit;
- plugin version;
- canonical JAR SHA-256;
- local development JAR SHA-256 when a noncanonical artifact was used;
- TunerStudio version;
- ECU/INI/tune identity;
- filenames;
- file SHA-256 where practical;
- exact test mode;
- confirmed findings;
- remaining uncertainty;
- disposition.

Do not silently substitute a local build hash for the canonical CI artifact identity.

## Anonymized test fixtures

Small synthetic or anonymized fixtures may be committed when they:

- contain no personal path or identity data;
- contain no sensitive tune content;
- are minimal and deterministic;
- document their origin and intended assertion;
- are suitable for repository licensing.

## Archive naming

Recommended local convention:

```text
YYYY-MM-DD_HHMM_<plugin-version>_<mode>_<purpose>/
```

Example:

```text
2026-07-30_1845_v0.3.13_map-predict_single-openings/
```

## Public export boundary

The public downstream repository must not receive private evidence metadata merely because it exists in the private repository.

Public review exports follow `config/public-export-allowlist.txt` and must exclude:

- private evidence files and archives;
- `docs/EVIDENCE_INDEX.md` and private continuation state unless a separately reviewed sanitized version is supplied;
- `lib/TunerStudioPluginAPI.jar`;
- personal paths and private attachment URLs;
- credentials and registrations;
- generated build output.

Generate a review export with:

```bash
bash scripts/build-public-export.sh target/public-export
```

The generated provenance identifies the exact private source commit. A successful leak check does not authorize publication while the source licence and API redistribution terms remain unresolved.
