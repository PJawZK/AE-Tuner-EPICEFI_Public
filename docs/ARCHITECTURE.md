# Architecture

## Purpose

AE Tuner (EPICEFI) is a Java Swing TunerStudio plugin for transient-fuelling analysis, guided testing and explicit reviewed tune proposals.

The current architecture on `main` is the authority. Historical candidate-branch architecture documents are not separate product states anymore.

## Host boundary

AE Tuner does not own ECU transport. TunerStudio provides project settings, live output channels and parameter update APIs.

The `host` package contains the host/config compatibility boundary, including:

- `AeControllerBridge`
- `OutputChannelResolver`
- `ChannelResolutionEvidence`
- `AeTuningParameterCatalog`
- `ProposalApplyCoordinator`

Firmware/internal identifiers should remain inside implementation/config layers; user-facing surfaces use displayed TunerStudio/MegaLogViewer names.

### Central write gateway

`ProposalApplyCoordinator` is the only production working-tune mutation gateway.

A write must originate from an explicit reviewed `ProposalWritePlan` and is required to:

1. pre-read/stale-check every declared target;
2. mutate only declared settings/cells;
3. preserve undeclared array cells and parameter shape;
4. read back complete affected parameters;
5. verify the expected result;
6. retain successful operations for explicit LIFO Restore;
7. attempt and verify rollback after partial failure.

Automatic Apply and ECU Burn remain outside the architecture.

## Shared model

The `model` package contains shared live/transient data structures and analysis components, including:

- `AeProjectSnapshot`
- `ChannelRole`
- `LiveSample`
- transient signal/event analysis and formatting
- MAP prediction metrics

Missing/unresolved values remain distinct from measured zero.

## Passive analysis

The `passive` package owns normal-driving observation, session diagnostics and Passive-specific calibration/UI.

Passive capture is observational. TPS-noise calibration affects AE Tuner's Passive event detection only; it does not change ECU settings or Guided opening thresholds.

Passive analysis is intentionally developed after the current Guided base rather than being allowed to redefine Guided tuning authority.

## Guided tuning

The `guided` package contains the Guided task framework, evidence sessions, task-specific modules, Focus/coaching UI and existing Blend Duration capture architecture.

Current product-level areas:

1. AE Foundation
2. TPS AE
3. MAP Predict
4. Wall Wetting
5. Decel / Tip-out
6. Optional / Residual Correction
7. Review / Simplification

Each area owns local task numbering. The map is provisional.

### Method/module boundary

Each Guided recipe routes through exactly one method module.

Implemented modules may own real capture/evidence and proposal behavior. Planned modules are `ARCHITECTURE_ONLY` scaffolds and must not create fake evidence, recommendations or write plans.

### Coaching/Focus boundary

Guided Focus is the driver-facing coach.

The general target lifecycle is:

`SETUP -> BASELINE / READY -> COACHED ACTION -> EVIDENCE -> REVIEW -> PROPOSAL -> EXPLICIT APPLY -> REPEAT -> A/B REVIEW -> KEEP OR RESTORE`

Driver View should be eyes-up and avoid root scrolling where practical. Visual/audio coaching is the product target; generic prose is a fallback.

Specialized Focus surfaces remain when they already contain useful real behavior, including MAP Estimate and the current Detector Model / Timing implementation.

## Guided runtime/data-flow boundary

High-rate TunerStudio delivery must stay bounded and must not wait on expensive Guided computation.

Conceptually:

```text
TunerStudio live callback
        ↓
coherent sample assembly / Passive processing
        ↓
nonblocking GuidedSampleDispatcher offer
        ↓
bounded queue/coalescing
        ↓
single Guided worker
        ↓
selected Guided method/evidence session
        ↓
Guided Focus / review / proposal
```

Important invariants:

- no unbounded Guided backlog;
- no static global Guided listener bus;
- Passive capture is not destroyed by Guided UI lifecycle;
- Swing presentation refresh is slower than sample acquisition;
- hide/reopen is reversible;
- destructive shutdown is reserved for actual host close;
- audio/worker resources stop cleanly when inactive.

## Proposal layer

The `proposal` package contains proposal policies, export/report support and immutable write-plan descriptions.

A proposal object does not directly write to TunerStudio. Mutation authority exists only when a reviewed proposal is converted to an explicit `ProposalWritePlan` and handed to the host coordinator.

## Recovery

The `recovery` package checkpoints local evidence/session state. Recovery must never alter ECU state merely because evidence was restored.

## UI support

The `ui` package provides reusable Swing layout/lifecycle helpers. Vehicle/laptop widths, especially 1366×768 and narrower synthetic gates, remain regression targets.

## Safety architecture

Permitted:

- read project/runtime data;
- capture/analyze/export evidence;
- create reviewed proposals;
- explicit guarded working-tune/RAM Apply/Restore for supported representations.

Prohibited:

- automatic Apply;
- Burn/flash operations;
- arbitrary generic ECU editing;
- undeclared writes;
- silently treating unvisited MAP Estimate cells as measured;
- silently interpolating unsupported proposal points;
- VE or ignition tuning authority.

Physical tune correctness remains a separate evidence question from software architecture correctness.
