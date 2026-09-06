# Controller persistence capability matrix

Status: safety gate for any future AE Tuner Burn/persistence feature.

This matrix deliberately separates four questions that are easy to conflate:

1. can the controller accept a working-RAM tuning write;
2. does the affected algorithm apply that value live;
3. can the firmware accept a Burn/persistence request while the engine is running;
4. can the physical non-volatile write safely execute while the engine is running.

A **yes** in one column must never be inherited into another column.

Firmware source inspected at EpicEFI fork commit `6a300295650ed131e57d8881afb2f00fd5291d47`.

## Matrix

| Controller | Processor / board evidence | Persistent config storage | Running working-RAM tuning | TPS AE timing live-effect today | Running-engine Burn / physical flash | Engine-off persistence requirement | Expected interruption / reset behavior | AE Tuner Burn status |
|---|---|---|---|---|---|---|---|---|
| **EpicECU beta** (`epicECU`) | **STM32F7** (`P-Prefix: F7`). Board makefile explicitly defines `NO_RUNNING_FLASH=1` with comment `F7 is broken`. | Internal MCU flash through the common persistent-state storage path, with backup and primary copies. | **Yes** for supported runtime settings: the TunerStudio write path updates working RAM. Per-setting live effect still has to be proven separately. | **No for `tpsAccelLookback` / `tpsAeDeltaWindowMs` in the inspected firmware** until the TPS-AE-local live geometry refresh is implemented. RAM readback alone is insufficient. | **Do not perform physical flash while the engine is running.** The common flash logic treats F4/F7 internal flash as CPU-blocking and the board explicitly disables running flash. A Burn request may remain pending/deferred rather than writing immediately. | **Yes for the physical persistence write** under normal running conditions. Persistence should occur only once the engine is stopped / flash becomes safe. | Internal flash on F4/F7 can freeze/block the CPU during erase/write. The persistence layer therefore defers unsafe running writes. The dual-copy scheme writes backup first and primary last so an interrupted Burn can retain the previous valid primary. No AE Tuner assumption of a harmless running reset is permitted. | **BLOCKED.** Do not add running Burn support. Future persistence UI/API requires controller-specific physical qualification and must preserve engine-off deferral. |
| **EpicECU v1** (`epicECUv1`) | **STM32H7** (`P-Prefix: H7`). Board makefile does not define `NO_RUNNING_FLASH`. | Common persistent-state storage path; internal flash behavior is governed by the MCU storage implementation and common dual-copy configuration writer. | **Yes** for supported runtime settings. | **Same TPS AE timing gap as above** in the inspected common algorithm until the local refresh patch lands. Other settings remain setting-specific. | **Not yet physically qualified for AE Tuner.** Absence of `NO_RUNNING_FLASH` is not proof that a running-engine Burn is safe for this controller. Common code can permit running flash on MCUs reported capable, but the storage source itself retains H7-specific uncertainty/TODO context. | Can be forced engine-off by the common `deferAllWritesUntilEngineOff` policy. Until controller-specific proof exists, AE Tuner must treat engine-off persistence as the safe supported assumption. | Common persistence writes backup before primary and validates flash contents. Exact running interruption, communication pause, scheduling effects, and reset/no-reset behavior require hardware validation on EpicECU v1 before product support. | **BLOCKED pending hardware-specific persistence qualification.** Do not inherit beta behavior or infer safety from H7 alone. |
| **Mega144H7** (`MEGA144H7`) | **STM32H7** (`P-Prefix: H7`). Board makefile does not define `NO_RUNNING_FLASH`. | Common persistent-state storage path; internal flash behavior is governed by the MCU storage implementation and common dual-copy configuration writer. | **Yes** for supported runtime settings; this is the controller used by the current AE Tuner vehicle program. | **Archive35 proves the TPS AE timing gap physically:** Sample Length RAM Apply/readback did not alter running 50 ms / 10 sample / stride 5 geometry. Requires firmware live refresh. | **Not yet qualified as an AE Tuner running-engine Burn capability.** H7 board configuration differs from beta/F7, but that does not establish product-safe running persistence. | Common firmware can defer writes until engine-off. For AE Tuner, engine-off persistence remains the required assumption until a dedicated Mega144H7 Burn campaign proves otherwise. | Common dual-copy/validation behavior applies. Exact real-vehicle interruption, USB/TunerStudio continuity, engine execution impact and reset behavior must be measured, not inferred. | **BLOCKED pending a dedicated Mega144H7 persistence campaign.** Current road A/B testing remains RAM Apply/readback/live proof/Restore only. |

## Common firmware persistence semantics

The common configuration writer maintains a primary and backup persistent copy.

The relevant safety behavior is:

- if flash cannot safely be written under current engine conditions, the write remains pending/deferred;
- `deferAllWritesUntilEngineOff` can force all physical writes to wait for engine stop even on an MCU otherwise reported capable of running flash;
- on internal flash implementations that block execution, ignition/injection safety handling is applied around the flash operation;
- backup is written before primary;
- the primary copy is written last so power loss/interruption during an attempted Burn does not destroy the previously valid primary first;
- written contents are verified/compared by the storage layer.

These properties improve persistence robustness but do **not** make an unqualified running-engine Burn safe for every board.

## Product rules derived from the matrix

### RAM tuning

AE Tuner may continue temporary working-RAM tuning only through its existing guarded production path:

`ProposalWritePlan -> ProposalApplyCoordinator -> immediate readback -> live proof when required -> explicit verified Restore`

Per-setting live-effect semantics remain mandatory. A RAM-readable value that feeds derived firmware state must prove that the derived runtime state actually changed.

### Burn / persistence

No production Burn support is authorized by this matrix.

Before Burn can be added for any controller, that exact controller must have a physical persistence qualification covering at least:

- engine running versus engine stopped;
- whether the Burn request is accepted, rejected, queued or deferred;
- when non-volatile erase/write actually occurs;
- ECU execution interruption duration/behavior;
- ignition/injection behavior during the operation;
- USB/TunerStudio connection continuity;
- whether a reset/reboot occurs or is required;
- power interruption during backup write;
- power interruption during primary write;
- post-restart selection of primary versus backup;
- readback of the persisted value after a complete controller power cycle.

Until those rows are physically proven, AE Tuner must not expose a generic Burn control.

## Current road-test authority

For the upcoming Foundation 1 timing work, persistence is intentionally irrelevant. The authoritative temporary comparison remains:

`RAM Apply -> readback -> running timing geometry proof -> controlled maneuver -> verified Restore`

Repeated flash Burn is neither required nor desired for Sample Length / Delta Window road A/B testing.