# EpicEFI TPS AE live timing runtime contract

Status: implementation prerequisite for the next AE Tuner vehicle-test build.

Authority baseline:

- AE Tuner green source: `4ce8b4dd2288c71b8d53de8b451c6a0f4ade10c6`
- AE Tuner build: `0.4.3-vehicle-test.2`
- physical finding: Archive35
- EpicEFI fork inspected at `6a300295650ed131e57d8881afb2f00fd5291d47`

## Why this exists

Archive35 proved that a successful working-RAM write/readback is not sufficient proof that a TPS AE timing setting changed the running detector.

During the road run, attempts to test Sample Length values above the baseline read back through the working-tune path, but every engine-running timing row remained:

- `Fuel: AE window = 50 ms`
- `Fuel: AE window samples = 10`
- `Fuel: AE delta stride = 5`

AE Tuner must therefore keep three controller capabilities separate:

1. **Live RAM write** — the controller accepts a temporary working-configuration value and it reads back correctly.
2. **Live runtime effect** — the running algorithm has rebuilt any derived state and is actually using that value.
3. **Persistent Burn** — the value is committed to persistent controller storage.

A successful item 1 must never be treated as proof of item 2 or item 3.

## Confirmed firmware semantics

In the inspected EpicEFI fork, `TpsAccelEnrichment::onConfigurationChange()` owns the TPS AE timing geometry. It currently:

- resolves the TPS callback period from `tpsAeFastCallback`;
- converts `tpsAccelLookback` into TPS history-buffer length;
- computes `m_deltaStride` from `tpsAeDeltaWindowMs` and callback period;
- publishes `aeWindowSamples`;
- publishes `aeWindowMs`;
- publishes `aeDeltaStride`;
- resizes and primes the TPS history buffer.

The common `EngineModule::onConfigurationChange()` lifecycle is explicitly associated with configuration-change/Burn processing.

The TunerStudio working-RAM write path copies validated configuration bytes into working RAM but does not invoke the global configuration-change lifecycle. This explains Archive35: the configured scalar can change and read back while TPS AE continues running with the previously derived geometry.

## Required firmware behavior

For the TPS AE timing controls, working-RAM changes must become live without a persistent Burn:

`RAM timing-setting change -> safe TPS-AE-local geometry refresh -> detector history re-prime -> next detector calculation uses the new geometry`

This requirement applies at least to:

- `tpsAccelLookback`;
- `tpsAeDeltaWindowMs`;
- `tpsAeFastCallback`, because callback period changes both history sample count and delta stride.

The refresh must recompute/re-prime:

- history-buffer length;
- `m_deltaStride`;
- `aeWindowSamples`;
- `aeWindowMs`;
- `aeDeltaStride`;
- detector/history state that could otherwise contain samples collected under the previous geometry.

## Preferred implementation shape

Keep the refresh inside `TpsAccelEnrichment`; do **not** make generic working-RAM writes invoke every module's Burn-time `onConfigurationChange()` callback.

A minimal design is:

1. Factor the existing timing-geometry calculation/re-prime from `TpsAccelEnrichment::onConfigurationChange()` into an owner-local helper such as `refreshTimingGeometry()`.
2. Add a comparison such as `timingGeometryMatchesConfiguration()` which resolves the current expected callback period, lookback sample count and delta stride from working RAM.
3. At the beginning of `TpsAccelEnrichment::onNewValue()`, before the new TPS sample is evaluated, detect a geometry/configuration mismatch.
4. On mismatch, rebuild the derived geometry and clear/re-prime history from the current TPS value before the detector resumes.
5. Continue calling the same helper from `onConfigurationChange()` so startup/Burn behavior remains unchanged.

This owner-local mismatch detection has two useful properties:

- it does not depend on TunerStudio being the writer;
- it does not broaden a normal RAM write into a controller-wide Burn/configuration-change event.

## Firmware regression required

Add a unit regression that reproduces the Archive35 failure without calling `onConfigurationChange()` between working-RAM edits:

1. initialize at the effective 50 ms fast-callback geometry and verify 10 window samples and the baseline stride;
2. change working RAM `tpsAccelLookback` to 70 ms;
3. deliver the next TPS callback;
4. verify the published live geometry becomes approximately 70 ms / 14 samples immediately;
5. change `tpsAeDeltaWindowMs` in working RAM;
6. deliver the next TPS callback and verify `m_deltaStride` / `aeDeltaStride` changes immediately;
7. verify samples/motion retained from the pre-change geometry cannot cause a false AE event after the refresh.

Existing bounds/clamping for lookback length and requested/actual delta stride must remain authoritative.

## AE Tuner qualification contract

AE Tuner must not weaken live timing qualification to work around this firmware behavior.

For temporary road A/B testing the required sequence remains:

`RAM Apply -> immediate readback -> live timing proof -> maneuver -> Restore`

For `tpsAccelLookback` / `tpsAeDeltaWindowMs`, live timing proof means the running output channels must agree with the candidate's expected geometry before a maneuver can advance candidate progress.

If the working value reads back but `Fuel: AE window`, `Fuel: AE window samples`, or `Fuel: AE delta stride` remain at the old geometry, the candidate is **not live**, no maneuver is countable, and Restore remains available.

## Persistence boundary

This runtime correction is deliberately independent of persistent Burn support.

The next vehicle-test build must continue to have:

- no automatic Burn;
- no production Burn button/API;
- explicit guarded RAM Apply only through `ProposalWritePlan -> ProposalApplyCoordinator`;
- stale-baseline checks;
- immediate readback;
- best-effort rollback;
- explicit verified LIFO Restore.

Persistent Burn is a separate future capability and remains blocked on the controller capability matrix plus controller-specific physical validation.