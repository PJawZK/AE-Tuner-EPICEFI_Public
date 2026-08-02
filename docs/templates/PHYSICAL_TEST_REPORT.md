# Physical test report template

## Candidate identity

- Private repository commit:
- Branch/PR:
- Plugin version:
- Canonical candidate JAR filename:
- Canonical candidate JAR SHA-256:
- Local development JAR used instead?: yes/no
- Local development JAR SHA-256, when applicable:
- Reason a noncanonical artifact was used, when applicable:
- TunerStudio version:
- EpicEFI/INI identity:
- Firmware signature/version, if independently verified:
- Tune baseline:

## Pre-test verification

- [ ] Only one intended AE Tuner JAR installed.
- [ ] Displayed plugin version matches candidate.
- [ ] Installed JAR SHA-256 recorded.
- [ ] Candidate source head matches controlled state.
- [ ] Required channels resolved or missing channels explicitly recorded.
- [ ] Vehicle/test conditions are safe for the planned procedure.

## Test setup

- Vehicle state:
- Enabled transient systems:
- Disabled transient systems:
- Boost-control state:
- DFCO/STFT/EGO state:
- Fuel:
- Gear/RPM/test pattern:
- Environmental/traffic/disturbance notes:

## External evidence

- TunerStudio log:
- Plugin CSV:
- MAP Predict report:
- Diagnostics report, when available:
- `.msq` if different from baseline:
- Screenshots/video:
- Additional files:
- File checksums:

## Results

### UI and performance

### Channel resolution

Distinguish unresolved, unavailable, inactive and genuinely received zero values.

### Event classification

### MAP Estimate draft

### Blend Duration per-RPM evidence

- Actual table RPM points/regions exercised:
- Prediction events per point:
- Usable/raw and retained events per point:
- Catch-up spread:
- Rejection reasons:
- Confidence/eligibility:
- Unsupported points preserved exactly?:
- Repeated stabs excluded from base evidence?:

### Wall Wetting/Instant Fuel

### Full-load safety context

Do not claim safety when required channels are missing or invalid.

## Disposition

- ACCEPT / REVISE / DEFER
- Confirmed findings:
- Rejected hypotheses:
- Remaining uncertainty:
- Proposal approved for manual paste?: yes/no
- PR merge approved?: yes/no
- Exact next action:
