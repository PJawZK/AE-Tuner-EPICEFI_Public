# Safety and scope

## Product scope

AE Tuner is a TunerStudio transient-fuelling analysis and guided-tuning plugin.

Allowed capabilities include:

- reading project/tune parameters;
- subscribing to displayed runtime channels;
- capturing and exporting evidence;
- computing draft/proposed settings;
- providing confidence, suitability and safety warnings;
- explicit reviewed working-tune/RAM Apply for representations/settings currently owned by the active task;
- explicit verified Restore of prior AE Tuner applies.

VE and ignition remain outside AE Tuner tuning authority. Ignition may be observed only as context/confounder information.

## Working-tune write boundary

A supported write is permitted only when all of the following are true:

- the active Guided task owns the setting/representation;
- the operator explicitly requests Apply;
- a reviewed immutable `ProposalWritePlan` exists;
- target parameter/cell identity is allowlisted/catalogued;
- the current baseline is pre-read and stale-checked;
- only declared targets are changed;
- complete affected parameters are read back;
- the expected result is verified;
- the operation is retained for explicit LIFO Restore.

Partial failure requires best-effort rollback plus verification. Uncertain outcomes must not be blindly retried.

All production tune mutation remains centralized in `host/ProposalApplyCoordinator.java`.

## Prohibited

- automatic Apply;
- automatic paste;
- hidden tune changes;
- arbitrary generic ECU editing;
- recipe-local direct parameter mutation;
- ECU Burn/flash commands;
- pretending a planned Guided task has validated recommendation/write authority;
- modifying VE or ignition as part of AE Tuner tuning;
- treating missing/unresolved data as zero.

## Representation qualification

Software tests verify routing, bounds and mutation isolation logic, but a newly supported controller/INI representation should also receive one real TunerStudio Apply/readback/Restore qualification before it is described as physically proven.

Completed physical qualifications include:

- Predictive MAP Blend Duration;
- MAP Estimate indexed table cells;
- Detector Delta Window scalar: `25 ms -> temporary value -> Apply/readback PASS -> Restore 25 ms PASS`.

Sample Length and Fast Callback write experiments also physically worked during development, but both are read-only context/prerequisite in current product authority. Engagement Model representation research was completed, but Engagement Model editing itself is scrapped from the product path.

Completed qualifications are not repeated unless the controller/host representation changes materially.

## Vehicle-test safety

- Use road/traffic conditions appropriate to the maneuver.
- Do not request full-throttle operation merely to collect transient evidence.
- Prefer moderate controlled loaded tip-ins when full load is unnecessary.
- Driver View should minimize distraction and use clear visual/audio cues.
- Driver View must not use a root scrollbar or jump the viewport during live updates.
- A/B tests should change one relevant setting at a time where practical.
- Stop or withhold evidence when required channels, controller state or maneuver comparability are inadequate.

## Evidence limits

Important contextual/required channels may include:

- measured and target lambda;
- fuel pressure;
- MAP / Effective MAP / prediction activity;
- TPS and detector outputs;
- transient fuel contributions;
- cuts/faults/DFCO;
- ignition/timing as read-only context.

Missing or unresolved data is not measured zero.

## Public-test safety

A public prerelease must clearly distinguish:

- software/runtime functionality that is regression/synthetic tested;
- write representations that have real Apply/Restore isolation evidence;
- settings that are currently read-only product context even if development write experiments once existed;
- planned/coaching-only Guided tasks;
- numerical tuning conclusions that still require vehicle-specific evidence.

Do not market a prerelease as an automatic tuner or as authority to Burn ECU settings.
