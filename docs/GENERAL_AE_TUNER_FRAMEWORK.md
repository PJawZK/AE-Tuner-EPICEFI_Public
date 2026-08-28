# General AE Tuner framework — Dev20 foundation

Status: **ACTIVE / UNACCEPTED DEVELOPMENT**

Branch: `agent/dev20-general-ae-recipe-framework`

Base: exact dev19 head `1a67118610264e085d77670a43822d05d3b582d5`

## Product boundary

AE Tuner is a general transient-fuelling tuner. Its tuning scope is the controller's AE/transient-fuelling settings and the relationships between them.

In scope as AE tuning targets:

- shared AE engagement/detection settings;
- TPS AE fuel and duration settings;
- MAP Predict / MAP Estimate / Blend Duration settings;
- Wall Wetting settings;
- Instant Fuel settings when evidence justifies its use.

Out of scope as tuning targets:

- VE table;
- ignition table;
- unrelated steady-state engine calibration.

Out-of-scope systems may still be observed as evidence or used as quality gates. For example, unstable base fueling may block an AE recommendation without authorizing AE Tuner to edit VE.

## Existing architecture retained

Dev20 does not replace the current Guided/evidence/write foundation.

The shared operator workflow remains:

`SETUP -> CAPTURE -> REVIEW -> APPLY / VERIFY`

Existing safety behavior remains authoritative:

- capture and analysis do not write automatically;
- proposals declare exact immutable controller targets;
- all working-tune mutations remain centralized through `ProposalApplyCoordinator`;
- stale baselines are rejected before Apply;
- a second stale-value preflight runs immediately before the first mutation;
- complete readback verification is required;
- failed verification rolls back through the same guarded path;
- explicit Restore remains available through the existing guarded path;
- no burn button or burn API is part of the current product.

Guarded working-tune Apply is a normal capability of every Guided tuning recipe from the start. A method does **not** pass through a separate read-only maturity stage. If its reviewed tuning logic produces an explicit changed `ProposalWritePlan`, that plan may immediately use the common Apply/readback/Restore path. If no plan exists, Apply stays disabled because there is no supported changed value to apply—not because the method lacks write authority.

After any successful Apply or Restore, AE Tuner requires a fresh **Read Working Tune** before another capture. This prevents a new evidence set from silently inheriting the pre-write baseline.

## First-use setting confirmation

The generic working-tune mutation mechanism has already been proven. New AE settings therefore do not require a prolonged write-authority validation phase.

For a controller setting or representation that AE Tuner has not exercised before, use one short real TunerStudio check:

1. read the current working-tune value;
2. select one explicit nearby temporary value;
3. Finish/Review so the exact diff is visible;
4. Apply the reviewed proposal;
5. verify TunerStudio readback matches the requested representation/value;
6. Restore the previous value;
7. verify the restored readback;
8. Read Working Tune again and confirm no stale temporary request remains;
9. mark that setting/representation path confirmed and move on.

The engine does **not** need to be running for this mapping/write-contract check. The purpose is to prove TunerStudio working-tune access, parameter representation, stale checking, readback and Restore. Combustion/tuning validation remains a separate later test when the numerical recommendation itself needs vehicle evidence.

This is especially useful for the detector family because it spans several controller representations:

- ordinary scalar settings, such as Delta Window and Sample Length;
- enum/bitfield settings, such as Engagement Model;
- shared-word boolean bits, such as the fast TPS callback setting;
- curves/tables for threshold and method-response settings.

No Burn test is required because AE Tuner currently does not expose Burn.

## Canonical AE parameter catalog

`host/AeTuningParameterCatalog` defines the settings AE Tuner recognizes as part of the AE product domain.

Each catalog entry records:

- exact controller parameter name;
- user-facing display name;
- AE subsystem;
- parameter shape;
- unit;
- dependency tier;
- whether changing it can invalidate previously collected evidence.

Current subsystems:

1. `ENGAGEMENT_DETECTION`
2. `TPS_AE`
3. `MAP_PREDICT`
4. `WALL_WETTING`
5. `INSTANT_FUEL`

The dependency order is deliberately upstream-to-downstream:

1. detector model;
2. detector timing;
3. detector threshold;
4. method activation;
5. transient response;
6. fine correction.

A change to an evidence-breaking upstream setting invalidates dependent downstream evidence. Example: changing Engagement Model or Delta Window makes previously captured threshold/TPS-AE/MAP-Predict evidence configuration-incompatible until it is re-established under the new detector configuration.

Catalog membership makes a setting eligible for guarded Apply. Evidence/recommendation logic determines **what value, if any, should be proposed**.

## Engagement / Detection is a separate Guided recipe

`AE Engagement / Detection` is independent of `TPS AE` fuel.

Reason: the shared TPS movement detector may govern more than TPS AE fuel. MAP Predict can depend on the same driver-intent detection even when TPS AE fuel is disabled.

The Dev20 recipe uses the common guarded-Apply contract. Its detector analysis can accumulate evidence without writing; once the detector tuning logic or an explicit operator setting selection produces a supported changed setting, Finish/Review exposes that exact `ProposalWritePlan` immediately. There is no additional read-only gate.

Required detector evidence includes:

- TPS;
- selected `Fuel: TPS AE change`;
- `AccelThreshold`;
- legacy max-step delta;
- timed max-step delta;
- window-span delta;
- rise-from-floor delta;
- newest-pair delta;
- actual AE window;
- actual AE delta stride.

Useful context includes window sample count, smoothed delta TPS, current AE-active state, MAP Predict activity and downstream fuel-method outputs.

The review contract explicitly prioritizes:

- current driver intent;
- opening -> hold drop-out;
- reversal sign behavior;
- partial lift -> reapply re-arm behavior;
- stacked short-stab separation;
- stale-positive retention/tails;
- same-threshold comparison of all engagement models.

## Detector working-tune snapshot

`AeControllerBridge` now reads the upstream detector settings needed for reviewed changes into `AeProjectSnapshot`:

- `tpsAeDetectMode` — Engagement Model;
- `tpsAeDeltaWindowMs` — Delta Window;
- `tpsAccelLookback` — Sample Length;
- `tpsAeFastCallback` — fast callback bit/state;
- `deltaTpsAverageAlpha` — delta-TPS smoothing context.

The existing boolean reader remains deliberately conservative for shared bit fields: it prefers the controller parameter's displayed/string value and accepts the scalar fallback only when it is unambiguously 0 or 1. This avoids accidentally treating a containing bit-field word as the boolean value.

## First implemented setting qualification — Delta Window scalar

Dev20 now contains a complete first-use qualification route for the ordinary scalar `tpsAeDeltaWindowMs`.

`EngagementDetectionGuidedFocusPanel` shows the current detector settings and exposes a Delta Window spinner. The spinner initializes from the exact working-tune value. Merely opening or refreshing Guided Focus does not create a proposal.

Selecting another Delta Window stores an operator-requested value only. It does **not** write the ECU and it is explicitly not presented as an automatic tuning recommendation.

After Engagement / Detection is Finished/Reviewed, `EngagementDetectionMethodModule.reviewedWritePlan()` converts that pending selection into `EngagementDetectionSettingProposal.deltaWindow(...)`, which creates exactly one scalar `ProposalWritePlan.Change` bound to the snapshot baseline. The ordinary shared **Apply Current Proposal** button then uses `ProposalApplyCoordinator`; no detector-specific writer exists.

A fresh working-tune snapshot is also an explicit setting-selection boundary. Repeated UI refreshes of the same snapshot preserve a pending selection, while a real new Read Working Tune resets the requested Delta Window to the newly read baseline. This specifically prevents a temporary test value from silently reappearing after `Apply -> Restore -> Read Working Tune` when the restored numeric baseline equals the original value.

Regression coverage exercises:

- unchanged Delta Window -> no proposal;
- exact 25 ms -> 24 ms scalar plan generation;
- correct parameter allowlist (`tpsAeDeltaWindowMs` only);
- stale baseline rejection before any write;
- Apply + readback PASS;
- Restore back to the exact prior value;
- no Burn authority;
- same-snapshot refresh preserves an intentional pending request;
- fresh working-tune read clears a stale temporary request.

This is source-level qualification of the generic scalar route. The remaining acceptance step is one real TunerStudio working-tune confirmation using a nearby temporary value and immediate Restore.

## Current detector conclusion carried into the framework

The current vehicle evidence selects **Dual stride, newest** as the preferred engagement model at the present test configuration:

- Delta Window: 25 ms;
- Sample Length: 50 ms;
- fast TPS callback: 200 Hz.

This is vehicle evidence, not a hard-coded universal default. AE Tuner should preserve the ability to evaluate detector settings on another tune/vehicle rather than assuming every installation must use those exact values.

The 24 ms value used in Dev20 regression examples is only a nearby temporary scalar-write qualification value. It is **not** a recommendation to change the current 25 ms vehicle setting.

## Evidence compatibility — next architectural step

The catalog now defines dependency/evidence-breaking semantics, but Dev20 does not yet persist the complete AE configuration with every captured evidence set.

The next bounded implementation should add an AE configuration fingerprint/snapshot that records materially relevant AE settings with Guided/Passive evidence. At minimum it should distinguish:

- Engagement Model;
- Delta Window;
- Sample Length;
- callback mode/rate where relevant;
- TPS threshold/rate-of-change configuration;
- enabled AE methods;
- method-specific settings required by the active recipe.

Evidence recorded under incompatible upstream settings must not be silently pooled.

## Validation boundary

Regression coverage now asserts or is being updated to assert:

- every AE subsystem has catalogued settings;
- exact current EpicEFI detector parameter names remain stable;
- detector model/timing changes are upstream evidence dependencies;
- VE and ignition remain outside the AE tuning catalog;
- Engagement / Detection is a separate Guided route;
- all five simultaneous engagement-model diagnostics are part of its evidence contract;
- every completed Guided probe method may return a reviewed `ProposalWritePlan` through the common Apply path;
- a method with no changed plan leaves Apply disabled without being classified read-only;
- detector settings are present in the working-tune snapshot;
- Delta Window has an exact scalar proposal route through the shared Apply/Restore coordinator;
- capture itself never writes;
- successful Apply/Restore requires a fresh working-tune read before another capture;
- fresh working-tune reads clear stale detector test selections;
- no burn path is introduced.

After the real Delta Window scalar confirmation, the next detector representations to qualify should be handled independently rather than assuming scalar success proves them automatically: Engagement Model enum/bitfield, then the fast-callback shared-word boolean, followed by any additional scalar/curve/table settings as they first become tunable outputs.
