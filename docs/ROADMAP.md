# Features and limitations

This document is a guide to the capabilities included in the latest public release, **AE Tuner v0.4.6**. For installation, see the [README](../README.md); for changes since v0.4.5, see [release notes](RELEASE_0.4.6.md).

## Guided tuning areas

| Area | Available workflows |
| --- | --- |
| AE Foundation | TPS movement/timing, Threshold/Sensitivity |
| TPS AE | Fuel-by-cycle, RPM/temperature compensation, closed-loop handoff and validation |
| MAP Predict | MAP Estimate, Blend Duration, transient validation |
| Wall Wetting | Base Tau/Beta, advanced mapping and film validation |
| Decel / Tip-out | Detection/threshold analysis, compatible historical multi-log evidence review |
| Optional Instant Fuel | Pulse/inhibit, event strength, operating-condition correction and residual validation |

Some areas contain informational or planned controls rather than fully qualified automatic recommendations. The interface will withhold proposals when supporting evidence is insufficient.

## Evidence and operating conditions

RPM/TPS alone do not describe all driving conditions. Temperature, MAP, load, gear, fuel and lambda-response delay may change results. Use comparable real-vehicle logs and review all recommendations before applying them.

## ECU write boundaries

AE Tuner supports explicit guarded Working Tune/RAM Apply and Restore only. It does not Burn, automatically tune, or edit VE/ignition. See [known limitations](KNOWN_ISSUES.md).
