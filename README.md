# AE Tuner (EPICEFI)

A TunerStudio plugin for EpicEFI transient-fuelling analysis and guided acceleration-enrichment tuning.

## v0.4.6 — source synchronized, release asset pending

- **JAR:** `ae-tuner-epicefi-0.4.6.jar`
- **Version shown in TunerStudio:** `0.4.6` / `PUBLIC RELEASE`
- **Java target:** Java 8-compatible bytecode
- **SHA-256:** `31e23679f055789be0073f35e810f1ae793e0713dfc381c92e293c98ecdc19dd`
- **Download status:** The v0.4.6 release asset has not yet been published. [v0.4.5 remains available](https://github.com/PJawZK/AE-Tuner-EPICEFI_Public/releases/tag/v0.4.5).
- **Release notes:** [v0.4.6](docs/RELEASE_0.4.6.md)
- Previous release: [v0.4.5](https://github.com/PJawZK/AE-Tuner-EPICEFI_Public/releases/tag/v0.4.5)

v0.4.6 adds historical Decel cross-log analysis, improved Guided lifecycle/evidence recovery and faster review/export, updated Foundation/Blend/Threshold presentation, scrollable and wrapping pop-outs, fixed footer actions and clearer progress/cancel workflows.

### Installation

1. Once the stable v0.4.6 release is published, download its JAR and verify its SHA-256.
2. Exit TunerStudio; remove all older AE Tuner JARs from the active plugin directory.
3. Install only `ae-tuner-epicefi-0.4.6.jar` and restart TunerStudio.
4. Confirm version `0.4.6` and **PUBLIC RELEASE**, then **Read Working Tune**.

### Safety and validation

All ECU writes are explicit **Working Tune/RAM** changes from reviewed plans through `ProposalWritePlan → ProposalApplyCoordinator → exact readback → rollback/Restore`. No automatic Apply, no **Burn**, no VE/ignition tuning.

Full software CI passed on the exact 0.4.6 identity. **Not all numerical recommendations have completed physical engine validation.** Use suitable matching engine data, carefully review proposals and report findings; incomplete methods do not gain write authority.

### Building from source

Java source and tests match the qualified release implementation. The third-party `TunerStudioPluginAPI.jar` is not bundled; supply your own licensed copy as described in [lib/README.md](lib/README.md). Build with `bash scripts/build.sh` and validate with `bash scripts/validate.sh`.

See [release details](docs/RELEASE_0.4.6.md), [known limitations](docs/KNOWN_ISSUES.md), and [current state](docs/CURRENT_STATE.md).
