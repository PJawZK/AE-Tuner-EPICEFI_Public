# AE Tuner — authoritative controller-setting inventory

Authority: `mainController.ini` signature `rusEFI master.2026.08.26.MEGA144H7.2273317132`.

This document is the human-readable gap matrix for the permanent inventory implemented by `AeTuningParameterCatalog`, `AeControllerDefinitionCatalog` and `GuidedControllerSettingInventory`.

Rules:

- Exact controller names and representations come from the frozen EpicEFI INI; UI labels are not mapping authority.
- `Current catalog coverage = Yes` means the setting is present in the canonical AE catalog and has exact controller metadata.
- `CURRENT` means the shared production Apply/Restore infrastructure already has an established path for that task/surface. `PARTIAL` means some task targets are already used by production code while the complete task surface is not. `PLANNED` means the Guided product owns the setting but recommendation/write construction is intentionally future work.
- Production write support and tuning-recommendation maturity are separate: a setting may have a proven write/readback/restore path before vehicle evidence is mature enough to recommend changing it.
- Arrays/tables are not one validation item: each writable axis/value element or table cell is an individual physical validation target.
- Tasks classified `READ_ONLY` or `NO DIRECT WRITE TARGET` remain explicit so they cannot disappear from coverage.

## Gap matrix

| Area | Task | Intended setting | Exact firmware/INI constant | Shape/type | Current catalog coverage | Current production write support | Required action |
|---|---|---|---|---|---|---|---|
| AE Foundation | TPS Movement / Timing | Delta Window — controlled Stage 2 | `tpsAeDeltaWindowMs` | scalar U08, ms, 5..250 | Yes | CURRENT | Write transport physically validated; controlled road sweep additionally requires live ECU window/sample/stride qualification before evidence can count |
| AE Foundation | TPS Movement / Timing | Engagement model (read-only Guided context) | `tpsAeDetectMode` | bits U08 `[0:2]`, 8 enum states | Yes | No direct Guided write | Retain Dual Stride / Newest as accepted read-only context; no normal Guided write requirement |
| AE Foundation | TPS Movement / Timing | Sample Length — controlled Stage 1 | `tpsAccelLookback` | scalar U16, sec, scale 0.0001 | Yes | CURRENT | Use guarded temporary Apply/readback/Restore plus live ECU window/sample/stride qualification; vehicle recommendation remains evidence-gated |
| AE Foundation | TPS Movement / Timing | Fast Callback (read-only Guided prerequisite) | `tpsAeFastCallback` | bits U32 `[19:19]` | Yes | No direct Guided write | Retain as read-only prerequisite/information; intended comparison basis is approximately 200 Hz |
| AE Foundation | Threshold / Sensitivity | EMA alpha | `deltaTpsAverageAlpha` | scalar F32, 0..1 | Yes | PLANNED | Add future reviewed write-plan construction; physically validate generic scalar path |
| AE Foundation | Threshold / Sensitivity | Dynamic threshold RPM axis | `deltaTpsAverageCurveRpmBins` | array U08 `[8]`, RPM, scale 100 | Yes | PLANNED | Validate all 8 indexed elements |
| AE Foundation | Threshold / Sensitivity | Dynamic threshold multiplier | `deltaTpsAverageCurveMultiplier` | array U08 `[8]`, mult, scale 0.1 | Yes | PLANNED | Validate all 8 indexed elements |
| AE Foundation | Threshold / Sensitivity | Enable dynamic threshold | `tpsAeUseDynamicThreshold` | bits U32 `[8:8]` | Yes | PLANNED | Validate alternate bit state + restore |
| AE Foundation | Threshold / Sensitivity | Average static + dynamic threshold | `tpsAeDynamicTresholdAverageStaticCurve` | bits U32 `[9:9]` | Yes | PLANNED | Validate alternate bit state + restore |
| AE Foundation | Threshold / Sensitivity | Static threshold RPM axis | `tpsAeThresholdRpmBins` | array U08 `[8]`, RPM, scale 100 | Yes | PLANNED | Validate all 8 indexed elements |
| AE Foundation | Threshold / Sensitivity | Static threshold values | `tpsAeThresholdValue` | array U16 `[8]`, scale 0.001, 0..60 | Yes | CURRENT | Generic physical path validated; conservative recommendation exists only under its evidence/dynamic-threshold guards |
| AE Foundation | Engagement Validation | — | — | NO DIRECT WRITE TARGET | Explicit | NONE | Keep task covered; no artificial setting |
| TPS AE | Fuel by Engine Cycle | Enable TPS AE | `tpsAccelAeEnabled` | bits U32 `[1:1]` | Yes | PARTIAL | Validate bit write/restore |
| TPS AE | Fuel by Engine Cycle | Engine-cycle axis | `tpsAeCycleCycleBins` | array U08 `[8]`, cycle | Yes | PARTIAL | Validate all 8 elements |
| TPS AE | Fuel by Engine Cycle | Ending-TPS axis | `tpsAeCycleTpsToBins` | array U08 `[8]`, %, scale 0.5 | Yes | PARTIAL | Validate all 8 elements |
| TPS AE | Fuel by Engine Cycle | Fuel multiplier table | `tpsAeCycleValues` | array U08 `[8x8]`, %, scale 0.02 | Yes | PARTIAL | Validate all 64 cells |
| TPS AE | RPM / Temperature Compensation | RPM axis | `tpsTspCorrValuesBins` | array U08 `[4]`, RPM, scale 50 | Yes | PLANNED | Validate all 4 elements |
| TPS AE | RPM / Temperature Compensation | RPM multiplier | `tpsTspCorrValues` | array U08 `[4]`, multiplier, scale 0.02 | Yes | PLANNED | Validate all 4 elements |
| TPS AE | RPM / Temperature Compensation | TPS scale axis | `tps_ae_scale_tps_bins` | array U08 `[5]`, TPS, scale 0.5 | Yes | PLANNED | Validate all 5 elements |
| TPS AE | RPM / Temperature Compensation | CLT scale axis | `tps_ae_scale_clt_bins` | array U08 `[5]`, INI unit `TPS`, scale 1 | Yes | PLANNED | Preserve frozen authority spelling/unit; validate all 5 elements |
| TPS AE | RPM / Temperature Compensation | TPS-vs-CLT scale table | `tps_ae_scale_multiplier` | array U08 `[5x5]`, mult, scale 0.01 | Yes | PLANNED | Validate all 25 cells |
| TPS AE | RPM / Temperature Compensation | CLT correction axis | `aeCltCorrBins` | array S08 `[6]`, C, scale 2 | Yes | PLANNED | Validate all 6 elements |
| TPS AE | RPM / Temperature Compensation | CLT correction values | `aeCltCorr` | array U08 `[6]`, mult, scale 0.05 | Yes | PLANNED | Validate all 6 elements |
| TPS AE | Completion / Closed-Loop Handoff | Cycle-table tail axis | `tpsAeCycleCycleBins` | array U08 `[8]`, cycle | Yes | PLANNED | Same physical target as Fuel-by-Cycle; validate once, retain multi-task mapping |
| TPS AE | Completion / Closed-Loop Handoff | Burn Skip count | `tpsaeburnskipinitial` | scalar U08, 0..100 | Yes | PLANNED | Validate scalar write/restore |
| TPS AE | Completion / Closed-Loop Handoff | Reset EGO | `tpsAeResetsEgo` | bits U32 `[7:7]` | Yes | PLANNED | Validate bit write/restore |
| TPS AE | Completion / Closed-Loop Handoff | Closed-loop inhibit | `noFuelTrimAfterAccelTime` | scalar U08, sec, scale 0.1 | Yes | PLANNED | Validate scalar write/restore |
| TPS AE | TPS AE Validation | — | — | NO DIRECT WRITE TARGET | Explicit | NONE | Keep task covered |
| MAP Predict | MAP Estimate Table | RPM axis | `mapEstimateRpmBins` | array U08 `[16]`, RPM, scale 100 | Yes | CURRENT | Validate all 16 elements |
| MAP Predict | MAP Estimate Table | TPS axis | `mapEstimateTpsBins` | array U08 `[16]`, % TPS, scale 0.5 | Yes | CURRENT | Validate all 16 elements |
| MAP Predict | MAP Estimate Table | MAP estimate table | `mapEstimateTable` | array U16 `[16x16]`, kPa, scale 0.01 | Yes | CURRENT | Validate all 256 cells |
| MAP Predict | Blend Duration | Enable MAP estimate during transient | `useMapEstimateDuringTransient` | bits U32 `[3:3]` | Yes | CURRENT | Validate bit write/restore |
| MAP Predict | Blend Duration | RPM axis | `predictiveMapBlendDurationBins` | array U08 `[4]`, RPM, scale 50 | Yes | CURRENT | Validate all 4 elements |
| MAP Predict | Blend Duration | Duration values | `predictiveMapBlendDurationValues` | array U08 `[4]`, second, scale 0.02 | Yes | CURRENT | Validate all 4 elements |
| MAP Predict | Transient Validation | — | — | NO DIRECT WRITE TARGET | Explicit | NONE | Keep task covered |
| Wall Wetting | Model / Base Tau-Beta | Enable Wall Wetting | `wallWettingAeEnabled` | bits U32 `[2:2]` | Yes | PARTIAL | Validate bit write/restore |
| Wall Wetting | Model / Base Tau-Beta | Model type | `complexWallModel` | bits U32 `[3:3]`, Basic/Advanced enum | Yes | PARTIAL | Validate alternate valid enum + restore |
| Wall Wetting | Model / Base Tau-Beta | Base tau | `wwaeTau` | scalar F32, Seconds, 0..3 | Yes | PARTIAL | Validate scalar write/restore |
| Wall Wetting | Model / Base Tau-Beta | Base beta | `wwaeBeta` | scalar F32, Fraction, 0..1 | Yes | PARTIAL | Validate scalar write/restore |
| Wall Wetting | Advanced Tau/Beta Mapping | CLT axis | `wwCltBins` | array S08 `[8]`, deg C | Yes | PLANNED | Validate all 8 elements |
| Wall Wetting | Advanced Tau/Beta Mapping | Tau-vs-CLT | `wwTauCltValues` | array U08 `[8]`, scale 0.01 | Yes | PLANNED | Validate all 8 elements |
| Wall Wetting | Advanced Tau/Beta Mapping | Beta-vs-CLT | `wwBetaCltValues` | array U08 `[8]`, scale 0.005 | Yes | PLANNED | Validate all 8 elements |
| Wall Wetting | Advanced Tau/Beta Mapping | RPM axis | `wwRpmBins` | array U16 `[8]`, RPM | Yes | PLANNED | Validate all 8 elements |
| Wall Wetting | Advanced Tau/Beta Mapping | MAP axis | `wwMapBins` | array U16 `[8]`, kPa | Yes | PLANNED | Validate all 8 elements |
| Wall Wetting | Advanced Tau/Beta Mapping | Tau RPM/MAP table | `wwTauMapTable` | array U08 `[8x8]`, scale 0.01 | Yes | PLANNED | Validate all 64 cells |
| Wall Wetting | Advanced Tau/Beta Mapping | Beta RPM/MAP table | `wwBetaMapTable` | array U08 `[8x8]`, scale 0.01 | Yes | PLANNED | Validate all 64 cells |
| Wall Wetting | Film Validation | — | — | NO DIRECT WRITE TARGET | Explicit | NONE | Keep task covered |
| Decel / Tip-out | Decel Detection / Threshold | RPM axis | `tpsDecelThresholdRpmBins` | array U08 `[8]`, RPM, scale 100 | Yes | PLANNED | Validate all 8 elements |
| Decel / Tip-out | Decel Detection / Threshold | Falling-TPS threshold | `tpsDecelThresholdValue` | array U16 `[8]`, scale 0.001 | Yes | PLANNED | Validate all 8 elements |
| Decel / Tip-out | Decel Detection / Threshold | Hold cycles | `tpsDecelHoldCycles` | scalar U08, cycles | Yes | PLANNED | Validate scalar write/restore |
| Decel / Tip-out | Enleanment / Cycle Shape | Enable enleanment | `tpsDecelEnleanmentEnabled` | bits U32 `[31:31]` | Yes | PLANNED | Validate bit write/restore |
| Decel / Tip-out | Enleanment / Cycle Shape | Cycle axis | `tpsDecelCycleCycleBins` | array U08 `[8]`, cycle | Yes | PLANNED | Validate all 8 elements |
| Decel / Tip-out | Enleanment / Cycle Shape | Ending-TPS axis | `tpsDecelCycleTpsToBins` | array U08 `[8]`, %, scale 0.5 | Yes | PLANNED | Validate all 8 elements |
| Decel / Tip-out | Enleanment / Cycle Shape | Fuel multiplier table | `tpsDecelCycleValues` | array U08 `[8x8]`, mult, scale 0.01 | Yes | PLANNED | Validate all 64 cells |
| Decel / Tip-out | Enleanment / Cycle Shape | CLT axis | `tpsDecelCltBins` | array S08 `[6]`, C, scale 2 | Yes | PLANNED | Validate all 6 elements |
| Decel / Tip-out | Enleanment / Cycle Shape | CLT authority | `tpsDecelCltMult` | array U08 `[6]`, mult, scale 0.01 | Yes | PLANNED | Validate all 6 elements |
| Decel / Tip-out | Decel MAP Prediction | Enable decel MAP estimate | `useMapEstimateDuringDecel` | bits U32 `[28:28]` | Yes | PLANNED | Validate bit write/restore |
| Decel / Tip-out | Decel MAP Prediction | RPM axis | `decelMapBlendDurationBins` | array U08 `[4]`, RPM, scale 50 | Yes | PLANNED | Validate all 4 elements |
| Decel / Tip-out | Decel MAP Prediction | Duration values | `decelMapBlendDurationValues` | array U08 `[4]`, second, scale 0.02 | Yes | PLANNED | Validate all 4 elements |
| Decel / Tip-out | Tip-out / Overrun Validation | — | — | NO DIRECT WRITE TARGET | Explicit | NONE | Keep task covered |
| Optional / Residual | Global Pulse / Inhibit | Enable Instant Fuel | `tpsAccelExtraShot` | bits U32 `[26:26]` | Yes | PLANNED | Validate bit write/restore |
| Optional / Residual | Global Pulse / Inhibit | Global pulse multiplier | `tpsExtraShotMult` | scalar U08, `*`, scale 0.01 | Yes | PLANNED | Validate scalar write/restore |
| Optional / Residual | Global Pulse / Inhibit | Inhibit cycles | `tpsExtraShotTimer` | scalar U08, `#`, 0..15 | Yes | PLANNED | Validate scalar write/restore |
| Optional / Residual | Event Strength (Delta TPS) | Delta-TPS axis | `tpsAeInstantDeltaTpsBins` | array U16 `[5]`, %, scale 0.01 | Yes | PLANNED | Validate all 5 elements |
| Optional / Residual | Event Strength (Delta TPS) | Delta-TPS multiplier | `tpsAeInstantDeltaTpsMultiplier` | array U08 `[5]`, mult, scale 0.01 | Yes | PLANNED | Validate all 5 elements |
| Optional / Residual | Operating-Condition Multipliers | RPM axis | `tpsAeInstantRpmBins` | array U08 `[5]`, RPM, scale 100 | Yes | PLANNED | Validate all 5 elements |
| Optional / Residual | Operating-Condition Multipliers | RPM multiplier | `tpsAeInstantRpmMultiplier` | array U08 `[5]`, mult, scale 0.01 | Yes | PLANNED | Validate all 5 elements |
| Optional / Residual | Operating-Condition Multipliers | TPS axis | `tpsAeInstantTpsBins` | array U08 `[5]`, %TPS, scale 0.5 | Yes | PLANNED | Validate all 5 elements |
| Optional / Residual | Operating-Condition Multipliers | TPS multiplier | `tpsAeInstantTpsMultiplier` | array U08 `[5]`, mult, scale 0.01 | Yes | PLANNED | Validate all 5 elements |
| Optional / Residual | Operating-Condition Multipliers | MAP axis | `tpsAeInstantMapBins` | array U08 `[5]`, kPa | Yes | PLANNED | Validate all 5 elements |
| Optional / Residual | Operating-Condition Multipliers | MAP multiplier | `tpsAeInstantMapMultiplier` | array U08 `[5]`, mult, scale 0.01 | Yes | PLANNED | Validate all 5 elements |
| Optional / Residual | Operating-Condition Multipliers | CLT axis | `tpsAeInstantCltBins` | array U08 `[5]`, C | Yes | PLANNED | Validate all 5 elements |
| Optional / Residual | Operating-Condition Multipliers | CLT multiplier | `tpsAeInstantCltMultiplier` | array U08 `[5]`, mult, scale 0.01 | Yes | PLANNED | Validate all 5 elements |
| Optional / Residual | Residual Lean-Hole Validation | — | — | READ ONLY EVIDENCE / DIAGNOSTIC | Explicit | NONE | Keep task covered; do not write from this review task |
| Review / Simplification | Stack Interaction Review | — | — | NO DIRECT WRITE TARGET | Explicit | NONE | Keep task covered |
| Review / Simplification | Residual Error Review | — | — | READ ONLY EVIDENCE / DIAGNOSTIC | Explicit | NONE | Keep task covered |
| Review / Simplification | Simplification / Final Validation | — | — | NO DIRECT WRITE TARGET | Explicit | NONE | Keep task covered |

## Audited firmware surfaces intentionally outside the current/planned Guided ownership

These are not silently missing inventory entries. They were encountered during the frozen-INI audit but are not claimed by any current or planned Guided task.

| Firmware surface | Example constant/UI | Inventory decision | Reason |
|---|---|---|---|
| TPS AE transient ignition retard | `aeIgnRetardPanel` / firmware AE ignition-retard controls | Out of scope | No current/planned `GuidedTuningRecipe` claims transient ignition-retard tuning. Adding it would expand product scope rather than complete an existing mapping. |
| Legacy accelerator-pump fraction controls | commented `tpsAccelFractionPeriod`, `tpsAccelFractionDivisor` UI | Out of scope | Firmware UI is commented/legacy and the Guided product owns the current cycle-table strategy instead. |
| TPS-vs-WOT scaling | commented `tpsAeScaledFrom` UI | Out of scope | Not part of current/planned Guided product tasks. |
| DFCO | existing DFCO controls | Observed context only | Decel Guided specification explicitly says Decel shaping does not replace DFCO and validation treats DFCO as observed context. |

If a future Guided task deliberately adopts one of these surfaces, permanent CI requires an explicit inventory mapping and canonical controller definition before the task can be treated as writable.

## Permanent contract

`AeTuningParameterCatalogRegressionTest` permanently checks that:

1. every Guided task appears exactly once in the task inventory;
2. every writable/planned task declares controller targets;
3. every declared target exists in the canonical AE catalog;
4. every canonical target resolves exact frozen-INI controller metadata;
5. no read-only/no-target task can silently acquire write targets;
6. controller names are unique;
7. critical exact type/range/dimension/bit-field contracts remain pinned.

The generic validation engine and temporary Validation Lab are implemented over the canonical inventories rather than maintaining another controller map. The complete real TunerStudio/EpicEFI campaign exercised all 816 intended physical targets through `ProposalWritePlan -> ProposalApplyCoordinator -> controller readback -> Restore -> controller readback` with exact restore and no Burn for the frozen firmware/INI combination. Recommendation maturity remains separately gated by tuning evidence.