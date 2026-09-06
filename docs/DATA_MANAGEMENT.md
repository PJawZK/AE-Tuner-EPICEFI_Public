# Evidence and data management

## Do not commit by default

- `.msl` / `.mlg` vehicle logs;
- `.msq` tune files;
- plugin CSV/session exports;
- generated reports;
- recovery/checkpoint folders from physical sessions;
- screenshots/videos unless explicitly selected as safe documentation;
- machine-specific paths;
- credentials, registrations or private keys;
- private controller definitions/dependencies unless explicitly reviewed.

Use ignored `local-evidence/` or external storage for private vehicle evidence.

## Controlled evidence reference

For an important test, record as relevant:

- date/time;
- exact source commit;
- plugin version and JAR SHA-256;
- TunerStudio version;
- ECU/INI/tune identity;
- evidence filenames/hashes;
- exact test task/maneuver;
- confirmed findings;
- uncertainty/limitations;
- disposition.

Do not put private tune contents into source-control documentation merely to make the evidence index self-contained.

## Repository topology

The private repository keeps one long-lived branch: `main`.

Temporary task branches may exist briefly, then are deleted after integration/abandonment.

Historical identity is preserved through immutable commits, JAR hashes, closed PRs/issues and evidence/acceptance records—not candidate branch refs.

## Build/JAR artifacts

- `dist/` is local/CI build output and JARs are ignored by Git.
- The permanent CI workflow may retain a short-lived validated JAR as a GitHub Actions artifact.
- Routine CI artifacts are not historical product authority.
- Public release JARs belong on the public GitHub Release (or equivalent release distribution), not committed into the source tree.
- Exact release JAR SHA-256 must be recorded.

Historical candidate JAR branches are no longer retained.

## Private TunerStudio Plugin API dependency

`lib/TunerStudioPluginAPI.jar` may exist in the private repository/build environment because compilation requires it.

It is not covered by the AE Tuner Apache-2.0 license and must not be distributed in the public source tree, public release assets or embedded inside the AE Tuner JAR.

Public source synchronization must therefore be selective/sanitized.

## Physical Apply/Restore evidence

For every newly supported writable representation, preserve enough before/after evidence to prove:

- declared targets;
- pre-write baseline;
- requested value;
- readback result;
- whole-tune/parameter isolation where practical;
- Restore result;
- whether Burn occurred (it must not).

A nearby temporary value used for representation qualification is not automatically a tuning recommendation.

## Evidence authority across tune revisions

Rank evidence as:

1. direct same-protocol A/B under the current relevant calibration;
2. accumulated evidence under the same relevant calibration state;
3. older revisions as reference unless the relevant calibration fingerprint matches.

This prevents historical sessions from silently voting on a tune whose relevant settings have changed.

## Anonymized fixtures

Small synthetic/anonymized fixtures may be committed when they:

- contain no personal path/identity;
- contain no sensitive tune content;
- are minimal and deterministic;
- document their origin/intended assertion;
- are suitable for repository licensing.

## Suggested external evidence naming

```text
YYYY-MM-DD_HHMM_<plugin-version>_<task>_<purpose>/
```

Keep exact hashes/identities in controlled notes when a result becomes authoritative.
