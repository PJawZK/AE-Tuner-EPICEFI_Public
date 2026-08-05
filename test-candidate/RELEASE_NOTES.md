# AE Tuner community test candidate 0.4.0-vehicle-test.5

**Status:** read-only, unaccepted community test build. This is not the stable release and does not approve any proposed tune value.

## Purpose

This candidate opens the Guided Capture approach to broader testing. Feedback is wanted on whether the workflow is practical across different vehicles, EpicEFI projects, TunerStudio installations, computers and driving environments.

Predictive MAP Blend Duration is the first guided recipe. The plugin remains an evidence and review tool: it does not write ECU RAM, burn settings, paste proposals or make hidden tune changes.

## Main changes in this candidate

- Explicit driving states from **ESTABLISH BASELINE RPM** through **EVENT COMPLETE - BACKOFF TPS**.
- Marked live TPS and RPM gauges with dynamic targets and accepted bands.
- Stable Guided Capture check-panel scrolling and screen-relative startup sizing.
- Preserved early `fallbackMap - MAP` measurement anchor, separate from later TPS-hold validation.
- Compact per-attempt traces embedded in Guided TXT and CSV exports, reducing dependence on very large `.msl` logs.
- Session-only **Vehicle-test overrides**, disabled by default, for:
  - detector confirmation allowance;
  - target acquisition limit;
  - MAP catch-up limit;
  - TPS target tolerance;
  - TPS boundary epsilon;
  - local TPS onset rise.
- Active override values are frozen at session start and recorded in the evidence.
- Target-band-aware held-TPS and TPS-step comparability.
- Default local TPS onset increased to 2.00 points to avoid false attempts from small pedal or sensor drift.
- **Test sound** button and visible PCM audio success/failure status instead of silent audio-backend failure.

## Installation

1. Close TunerStudio.
2. Keep your known-good plugin as a rollback.
3. Open Tools menu, Tunerstudio Plugins then Add or update plugin
4. If succesful AE Tuner should startup right away after the 3 prompts have been gone thorugh.
5. If it doesn't launch, report it as an issue in this repository.

## Suggested first test

Start with Vehicle-test overrides **off**. Use a safe, repeatable low-RPM test condition appropriate for your vehicle. Do not force an exact RPM, load or throttle position when road conditions make the test unsafe.

1. Start a normal datalog before running the plugin.
2. Open the plugin, and start the engine.
3. Under the passive tab, locate the "Start the TPS noise calibratiion", wait for it to finish, then press "Use recommended threshold".
4. Now move on to the new "Guided Capture" window. Confirm sound is work through the "Test Sound" button.
5. Then start with a guided capture with settings as they are, try a few attempts.
6. if you find it hard to get any accepted runs, start altering the settings on the row that contains the "Table point" and "Start RPM".
7. If you want to go more advanced, there is the "Vehicle-test overrides". They do not alter anything outside of the plugin, they alter the plugins assumptions on what a accepted event is like, in the simplest of terms. Target values and time factors.
8. I only recommend the advanced method if you have some understanding of AE blend duration and how it' triggered. Or if you are jut curious what they do, have at it.
9. I would appreciate any feedback, and feedback can be reported under **[Log diagnosis / test feedback](https://github.com/PJawZK/AE-Tuner-EPICEFI_Public/issues/new?template=log-diagnosis.yml)**.

Save before resetting:

- Guided Capture TXT report;
- Guided Capture CSV;
- passive MAP Predict report;
- passive captured-events CSV;
- optional matching TunerStudio `.msl` when it can be saved reliably;
- notes identifying vehicle, ECU/project, TunerStudio, Java, operating system, configured targets and observed behaviour.

Submit feedback through the repository's **Log diagnosis / test feedback** issue form. Clearly state whether overrides were enabled and include every effective override value.

## Integrity

`ae-tuner-epicefi-0.4.0-vehicle-test.5.jar`

SHA-256:

`f25271d43f60ee3aa501c2962ffc4da6aeb7391fe56d5013196557822a286dea`

## Safety and interpretation

- This is experimental workflow validation, not an accepted tune or stable plugin release.
- Do not paste a proposal solely because the candidate generated it.
- Review evidence against the original tune, logs and test conditions.
- Stop testing for abnormal AFR, knock, trigger loss, unexpected cuts, mechanical noise, loss of control or traffic conflict.
