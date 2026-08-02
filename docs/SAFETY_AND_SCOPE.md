# Safety and scope

## Accepted scope

The accepted plugin is a read-only analysis and guidance tool.

Allowed:

- reading TunerStudio project parameters;
- subscribing to displayed output channels;
- capturing and exporting events;
- calculating draft values;
- copying or saving reports for manual review;
- providing suitability, confidence, and safety warnings.

Not allowed in the accepted baseline:

- changing ECU RAM values;
- burning ECU values;
- applying generated tables automatically;
- hiding changes behind a button described as analysis;
- silently changing project files;
- bypassing TunerStudio review.

## Vehicle-test safety

- Keep testing appropriate to road, traffic, passenger, engine, drivetrain, and weather conditions.
- Do not request full-throttle testing merely to collect transient data.
- MAP Predict tuning should use moderate controlled loaded tip-ins.
- Full-load review is supplementary and must not encourage unsafe driving.
- Lower-confidence analysis must be labeled as such.

## Evidence limits

A conclusion is incomplete when required channels are unavailable. Important examples include:

- fuel pressure;
- Boost: Target;
- Timing: ignition;
- cuts and faults;
- target lambda;
- measured lambda;
- MAP prediction activity;
- Wall Wetting injection-time contribution.

A missing or unresolved channel is not zero.

## Future ECU-write gate

Any future write-enabled stage requires a separate approved document covering at least:

- exact controller/INI/tune identity;
- typed allowlist of writable settings;
- baseline capture and rollback;
- explicit preview and user acceptance;
- RAM write separated from burn;
- read-back verification;
- uncertain-outcome handling without blind retry;
- simulator/bench/vehicle validation;
- recovery procedure;
- physical acceptance.

Until that document is approved, repository agents must reject ECU-write implementation tasks.
