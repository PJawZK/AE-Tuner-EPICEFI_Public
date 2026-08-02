# Architecture

## Purpose

AE Tuner (EPICEFI) is a Java Swing TunerStudio plugin that reads project settings and live output channels, captures transient events, and produces read-only tuning guidance and reports.

It does not own ECU communication. It consumes TunerStudio's plugin APIs.

## Main components

### `AeTunerPlugin`

TunerStudio `ApplicationPlugin` entry point. Owns plugin identity, version, display name, panel creation, initialization and shutdown.

### `AeTunerPanel`

Main Swing orchestration layer. It remains the lifecycle and live-sampling owner, but presentation and export responsibilities have been split into bounded helpers.

It:

- subscribes to live channels;
- reads project state;
- owns capture/session state;
- receives samples;
- coordinates calibration, capture, draft, report and export actions;
- preserves read-only operation;
- owns one periodic UI refresh timer that runs only while the panel is showing.

Future refactoring must preserve listener counts, timer lifecycle, component ownership, reset behavior, export behavior and clean plugin re-instantiation.

### Presentation and layout helpers

- `ControlPanelBuilder` builds the wrapping controls/settings area.
- `MainContentBuilder` configures the primary scroll page, live-channel table, event preview and lower tabs.
- `UiRefreshPresenter` updates technical and Overview text without owning evidence logic.
- `OverviewTextRenderer` and `TechnicalDetailsRenderer` provide pure presentation text.
- `StatusCard` and `CardState` provide compact state cards.
- `WrapLayout`, `WrappingColumnPanel` and `ViewportWidthPanel` provide width-aware vertical wrapping.
- `StableTabbedPane` prevents tab selection from moving the user's outer scrollbar.
- `NestedScrollWheelHandoff` keeps inner Overview/Technical scrolling in range and moves the outer page only at an inner boundary.
- `EventPlotPanel` renders the latest event preview.
- `LiveChannelTableRenderer` updates the resolved channel table while preserving missing-versus-zero distinctions.

### Export helpers

- `AdvisoryExportCoordinator` owns file selection, text writing, clipboard use and export-duration timing.
- `EventCsvWriter` serializes captured event evidence.
- `MapPredictReportBuilder` builds the combined read-only MAP Predict report without owning panel state.
- `ChannelResolutionEvidence` formats selected critical channel names and latest raw values.
- `SessionDiagnostics` summarizes session retention and export timing for the combined report.

A future separate diagnostics export must be on-demand only. It must not add background firmware polling, recurring timers, persistent history or ECU writes.

### `AeControllerBridge`

Reads TunerStudio controller parameters and produces an `AeProjectSnapshot`. Firmware/internal names belong here or in a future dedicated diagnostics bridge, not in user-facing text.

### `AeProjectSnapshot`

Immutable-ish representation of relevant project configuration:

- TPS cycle AE tables and trigger curve;
- MAP Estimate axes/table;
- Predictive Map Blend Duration axes/values;
- Wall Wetting and Instant Fuel states;
- dynamic threshold settings.

### `ChannelRole` and `OutputChannelResolver`

`ChannelRole` maps displayed live-channel roles to candidate channel names. `OutputChannelResolver` is the compatibility boundary for differing TunerStudio/EpicEFI names and role-specific fallbacks.

### `LiveSample` and `TransientSignals`

Compact live-sample representation and interpreted transient-path visibility rules.

### `AeEventDetector`

Captures bounded transient windows and classifies candidate events. It must distinguish:

- plugin-side pedal movement;
- TPS-change detector state;
- MAP Predict activity;
- Wall Wetting activity;
- Instant Fuel activity;
- legacy TPS cycle AE activity;
- unsuitable or diagnostic-only events.

The detector ring and active event are bounded. Completed event history remains retained by `AeTunerPanel` until Reset session or plugin destruction.

### `EventSummary`

Calculates event-level metrics, classifications, report text and CSV-facing fields. Event summaries cache prediction metrics so repeated presentation does not rescan every sample unnecessarily.

### `MapEstimateCollector`

Collects stable RPM/TPS/MAP observations outside transient, DFCO, cut and unstable conditions. Storage dimensions remain fixed to the project table axes.

### `MapEstimateSuggestion`

Builds a read-only MAP Estimate proposal. Current safeguards include:

- minimum samples per observed cell;
- MAP standard-deviation and range limits;
- no changes to unvisited cells;
- turbo cap applied only to observed proposals;
- changed/excluded-cell reporting.

### `PredictionBurstMath` and `CounterMath`

- `PredictionBurstMath` separates one continuous TPS-change burst from multiple distinct bursts.
- `CounterMath` handles small unsigned counter movement, wrap and discontinuities.

### `MapBlendSuggestion`

Builds conservative per-RPM Predictive Map Blend Duration evidence from suitable single-held-opening events.

Preserved semantics:

- assignment to the nearest actual table RPM point;
- midpoint-defined reporting regions;
- repeated-stab events remain diagnostic-only;
- at least three bounded-spread events for eligibility;
- five tighter events for high confidence;
- unsupported/ineligible points remain exactly unchanged;
- no silent interpolation or smoothing.

### `SessionMonitor`, `SessionMonitorCore` and snapshots

Aggregate continuous operational-state behavior, trigger/sync evidence, actual-cut versus reason-code evidence, diagnostic-counter attribution and full-load safety context.

### `SessionReview`

Combines event evidence with the latest session-monitor snapshot. Event-derived review content can be cached while safety state remains fresh.

### `RecommendationHistory`

Stores a maximum of 100 meaningful recommendation/channel-resolution transitions in memory. It is cleared by reset or plugin restart and is not persisted to reports, CSV files, project files or the ECU.

### `AeTableSuggestion`

Legacy TPS cycle AE multiplier-table suggestion logic. It remains for compatibility mode and is not the primary current tuning path.

## Data flow

```text
TunerStudio controller parameters
        ↓
AeControllerBridge → AeProjectSnapshot

TunerStudio live output channels
        ↓
AeTunerPanel → LiveSample / TransientSignals
        ↓
AeEventDetector → EventSummary
        ↓
MapEstimateCollector / MapBlendSuggestion / SessionMonitor
        ↓
SessionReview / RecommendationHistory
        ↓
Overview, Technical details, CSV, combined report
```

## Threading and performance

- TunerStudio may deliver samples at a high rate.
- The callback path is serialized with reset/reconnect/disconnect through `samplingLock`.
- Host unsubscribe calls must not run while holding the sampling-state lock.
- Event recording must remain lightweight.
- UI refresh runs every 500 ms only while the panel is visible.
- Event-derived `SessionReview` is cached by event revision; live safety is refreshed from the current monitor snapshot.
- Avoid boxed per-sample maps and repeated full-history rescans.
- Avoid changing Swing preferred sizes during live-value refresh.
- UI state and viewport behavior must remain stable while values update.
- CSV generation is the largest tested linear user-triggered cost at the characterized scale.

## Repository and continuation architecture

The private authority repository is `PJawZK/AE-Tuner-EPICEFI-`. `PJawZK/AE-Tuner-EPICEFI_Public` is a sanitized downstream target only.

Continuation uses three ordered steps:

1. handoff pre-flight;
2. compact continuation handoff;
3. new chat re-fetches and continues from the repository.

See `docs/HANDOFF_PREFLIGHT.md` and `docs/REPOSITORY_RELATIONSHIP.md`.

## Safety boundary

All tuning outputs are suggestions or reports. No class may write or burn ECU settings in the accepted baseline.

A future write path would require a separate safety architecture, explicit authorization, typed allowlists, identity checks, baseline/rollback, read-back verification and physical acceptance. It must not be added opportunistically to the current panel.
