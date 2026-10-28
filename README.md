# AE Tuner (EpicEFI)

AE Tuner is a TunerStudio plugin for guided transient-fuelling and acceleration-enrichment tuning on compatible EpicEFI controllers.

## Download AE Tuner v0.4.6

**Latest stable release:** [AE Tuner v0.4.6](https://github.com/PJawZK/AE-Tuner-EPICEFI_Public/releases/tag/v0.4.6) — published 28 October 2026.

- **Plugin:** [ae-tuner-epicefi-0.4.6.jar](https://github.com/PJawZK/AE-Tuner-EPICEFI_Public/releases/download/v0.4.6/ae-tuner-epicefi-0.4.6.jar)
- **Checksum:** [SHA256SUMS.txt](https://github.com/PJawZK/AE-Tuner-EPICEFI_Public/releases/download/v0.4.6/SHA256SUMS.txt)
- **SHA-256:** `31e23679f055789be0073f35e810f1ae793e0713dfc381c92e293c98ecdc19dd`
- **Identity:** `0.4.6` / `PUBLIC RELEASE`
- **Java:** Java 8-compatible bytecode

### Installation

1. Download the v0.4.6 JAR and optionally verify it against `SHA256SUMS.txt`.
2. Exit TunerStudio and remove older AE Tuner JARs from its active plugin directory.
3. Copy `ae-tuner-epicefi-0.4.6.jar` into the plugin directory and restart TunerStudio.
4. Verify version `0.4.6` / `PUBLIC RELEASE` and select **Read Working Tune** before tuning.

## What AE Tuner does

AE Tuner guides you through gathering transient-fuelling evidence, evaluating engine response and reviewing supported tuning changes.

- **AE Foundation:** TPS movement/timing and threshold/sensitivity coaching with live traces.
- **TPS AE:** Fuel-by-cycle, RPM/temperature compensation, handoff and outcome validation workflows.
- **MAP Predict:** MAP Estimate coverage, physical MAP-response Blend Duration and transient validation.
- **Wall Wetting:** Tau/Beta investigation, mapping and film-response validation.
- **Decel / tip-out:** Falling-throttle evidence analysis, including compatible historical TunerStudio MLG logs.
- **Optional Instant Fuel:** Residual transient correction when upstream evidence supports it.

Typical flow: **Read Working Tune → Prepare → Capture or Analyze → Review → optional explicit Apply → Keep or Restore**. Recommendation and write availability depend on the chosen task and evidence; not every screen provides a numerical proposal.

## What's new in v0.4.6

- Historical Decel analysis and compatible cross-log evidence pooling.
- More legible Guided Focus and responsive pop-out windows, scrolling, report wrapping and fixed action footers.
- Improved progress/cancel workflows, Foundation/Threshold/Blend graphs and live-data feedback.
- More efficient review, export and evidence recovery.
- Hardened rollback/readback behavior for failed or uncertain Working Tune writes.

See the [full v0.4.6 release notes](docs/RELEASE_0.4.6.md) and [known limitations](docs/KNOWN_ISSUES.md). [Previous v0.4.5 release](https://github.com/PJawZK/AE-Tuner-EPICEFI_Public/releases/tag/v0.4.5).

## Safety

AE Tuner applies only explicitly reviewed, supported **Working Tune/RAM** changes with exact readback and guarded Restore. It does **not** Burn ECU flash, automatically apply changes, or tune VE/ignition. Software qualification does not guarantee the physical correctness of every suggested value; evaluate recommendations against matching engine evidence.

## Source and license

The source code is provided under Apache License 2.0. Developers who build locally must supply an authorized copy of the separately licensed TunerStudio Plugin API; it is not distributed with this project. See [lib/README.md](lib/README.md), [LICENSE](LICENSE), [NOTICE](NOTICE), and [third-party notices](THIRD_PARTY_NOTICES.md).
