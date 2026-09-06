# vehicle-test.12 architecture-refactor candidate

- Version: `0.4.0-vehicle-test.12`
- Branch: `agent/vehicle-test-12-architecture-refactor`
- Architecture base: `b59c8b9d23c59db5b9de037912d8913f5aad6c10`
- Exact candidate source commit: `70580ceb5ceda0847d7e847bcb2e7cc3e4ff1f05`
- JAR SHA-256: `e3cf2b8d346dd8c02f59726783e9d8a3a85357afc67fe5097b15b5e255eaa306`
- Java target: Java 8 bytecode
- ECU behavior: strictly read-only; no writes or burns
- Status: unaccepted physical test candidate
- Purpose: stationary and vehicle validation of the completed A-F architecture refactor.

Validation completed before the candidate identity note was committed:
- full deterministic regression suite: PASS
- Phase F package architecture regression: PASS
- long-session characterization: PASS
- real Swing synthetic EPICEFI integration under Xvfb: PASS

Exact JAR recovery:
- deterministic rebuild from exact source `70580ceb5ceda0847d7e847bcb2e7cc3e4ff1f05`: PASS
- recovered JAR commit: `67875f53bb2de5d122bf1e70d20440262e1f9fb0`
- recovered branch path: `recovery/ae-tuner-epicefi-0.4.0-vehicle-test.12.jar`
- recovered JAR SHA-256 re-verification: `e3cf2b8d346dd8c02f59726783e9d8a3a85357afc67fe5097b15b5e255eaa306`

Repository/CI housekeeping performed after the candidate source was frozen does not change the candidate bytes. Routine CI is now storage-independent: validation and synthetic integration no longer require GitHub Actions artifact uploads. The revised workflows were both verified green on the `.12` branch after the change (`Build and validate` run `31296894773`; `Synthetic plugin integration` run `31296894764`).

This candidate does not approve or change any Blend Duration proposal/value.
