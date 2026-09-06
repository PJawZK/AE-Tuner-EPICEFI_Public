# 0.4.3-vehicle-test.3 — Archive35 correction build

## Scope

This internal vehicle-test build preserves the existing guarded working-RAM Apply/Restore path and does not add Burn support.

Archive35 exposed that a successful RAM write/readback of TPS AE timing settings does not prove live detector geometry changed. The vehicle workflow therefore continues to require live ECU timing qualification before any maneuver can count. Firmware-side live timing refresh is specified separately in `EPICEFI_TPS_AE_LIVE_TIMING_RUNTIME.md`; controller persistence assumptions are bounded by `CONTROLLER_PERSISTENCE_CAPABILITY_MATRIX.md`.

## Foundation 1 corrections

- Sample Length remains Stage 1; Delta Window remains Stage 2.
- Final timing acceptance still requires agreement across at least two distinct RPM regions.
- Only the requested +10 TPS target band of ±2 TPS is counting/comparable evidence. Good/Usable off-target physical maneuvers remain diagnostic and contribute zero candidate progress.
- Maneuver start must remain inside the configured RPM region after READY; the previous widened post-READY start allowance is removed.
- `Fuel: TPS Decel Active` is an explicit required start-authority channel. Active or recent decel forces reacquisition/re-arm before Foundation 1 may start a maneuver.
- ECU detector reaction uses the explicit `Fuel: TPS AE Active` channel and is presented as a neutral, not-counted cue. The later strict Guided maneuver result uses the distinct counted/not-counted cue family.

## UI / evidence cleanup

- The temporary Apply/Restore Validation Lab has no normal Guided UI entry point after the complete 816/816 physical validation campaign. Its canonical catalog, validated mappings, validation engine, coordinator/readback/restore safety and permanent regressions remain retained.
- Guided controlled-session evidence and Guided method-sample evidence remain distinct payloads, but both resolve to the single `AE Tuner Export/Guided Evidence` root.
- The product action remains `Export Evidence`; export progress is status, not a changing button identity.

## Safety boundary

- `ProposalWritePlan -> ProposalApplyCoordinator` remains authoritative.
- stale-baseline preflight remains required.
- immediate readback remains required.
- rollback after partial failure remains required.
- verified LIFO Restore remains required.
- no automatic Apply.
- no Burn button or production Burn API.

This build is not vehicle-accepted until permanent CI passes and a subsequent road test confirms the firmware live timing refresh with the unchanged live timing qualification gate.
