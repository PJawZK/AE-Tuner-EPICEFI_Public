# Third-party notices

## AE Tuner source licence

AE Tuner source code and project documentation are licensed under the Apache License, Version 2.0.

See:

```text
LICENSE
NOTICE
```

The Apache-2.0 licence applies to AE Tuner material published by this project. It does not grant rights to third-party products, names, binaries or interfaces that are not owned by the AE Tuner contributors.

## TunerStudio Plugin API

The private development repository contains `lib/TunerStudioPluginAPI.jar` because it is required to compile the plugin.

The binary is a separate third-party dependency and is intentionally excluded from public source/release distribution.

- It is not covered by AE Tuner's Apache-2.0 licence.
- It must not be embedded in AE Tuner release JARs.
- Public source/release packaging must exclude it.
- Developers building from public source obtain an authorized copy independently.
- Normal users installing a prebuilt AE Tuner plugin JAR do not install the API JAR separately.

See `lib/README.md`.

## Product names and trademarks

TunerStudio, EFI Analytics, EpicEFI and other third-party names are used only to describe compatibility and integration. They remain the property of their respective owners. No endorsement or ownership is implied.
