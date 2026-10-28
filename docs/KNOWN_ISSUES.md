# Known issues and limitations

Use `docs/CURRENT_STATE.md` for current authority.

## Open limitations

### Physical validation is narrower than software coverage

v0.4.6 has extensive CI/regression coverage and focused real-vehicle checks for Foundation/Blend workflows. That does not mean every TPS AE, MAP Predict, Wall Wetting, Instant Fuel or decel recommendation is physically validated. Retain matching `.mlg` evidence when testing.

### Desktop layout needs wider platform coverage

Responsive pop-outs, scrolling, wrapping and fixed action footers pass synthetic
UI tests; different TunerStudio desktops and OS window managers still need
feedback from real users.

### Historical Decel evidence limits

Threshold-independent physical-event classification and compatible multi-log
pooling are software-validated against retained real logs. Numerical threshold
acceptance on other engines/configurations requires matching physical evidence.
Upper-RPM cells without credible evidence remain NEED EVIDENCE.

### Wall Tau automatic inference is withheld

Wall correction state is immediate while measured lambda is delayed by combustion/exhaust/sensor transport. Automatic Tau movement remains withheld until that delay is measured/aligned sufficiently for trustworthy persistence inference.

### TPS closed-loop handoff automatic movement is withheld

Lambda recovery alone does not prove EGO/trim re-entry. Automatic movement of closed-loop handoff/inhibit timing remains withheld without an authoritative EGO/trim-active signal.

### Instant Fuel remains residual correction

Instant Fuel should not become a default substitute for incorrect upstream transient fueling. Event ownership and pulse evidence are implemented, but vehicle evidence should establish a residual lean hole before adding authority.

### MAP Estimate remains load/context sensitive

RPM and TPS do not uniquely determine MAP on a turbo engine under all gear/load/spool conditions. Coverage count alone is insufficient; spread, stability and operating context matter. Unvisited cells must remain unchanged.

### Cold/warm mapping needs condition coverage

Temperature compensation and advanced Wall Wetting mapping require evidence over the corresponding real temperature ranges; one warm drive cannot validate all cells.

### Missing channels limit conclusions

Fuel pressure, target lambda, measured lambda, ignition context, cuts/faults, prediction activity and individual transient-fuel contributions may be unavailable depending on controller/INI/runtime state. Missing data must remain unknown rather than being treated as zero.

### No ECU Burn

Supported changes are working-tune/RAM Apply operations with readback and Restore. AE Tuner intentionally provides no production Burn path.

### Public dependency boundary

`TunerStudioPluginAPI.jar` is not redistributed in public source or release assets. Developers building from source must supply an authorized local copy as documented in `lib/README.md`.

## Controlled / regression-covered

- stale-baseline preflight and exact readback;
- rollback/Restore behavior;
- bounded Guided dispatch and acceleration/release critical sample preservation;
- plugin worker/lifecycle cleanup;
- v0.19 first-click interaction behavior;
- incomplete evidence vs review-ready completion;
- MAP Focus live-cell invalidation;
- recommendation/model caching;
- recovery coalescing;
- real Swing synthetic checks at practical widths.
