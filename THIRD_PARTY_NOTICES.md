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

The private authority repository contains:

```text
lib/TunerStudioPluginAPI.jar
```

It is required to compile the plugin, but it is a separate third-party dependency.

The project has not established permission to redistribute that binary publicly. Therefore:

- the binary remains in the private authority repository only;
- every public source export and public repository synchronization excludes it;
- AE Tuner release JARs must not embed or contain it;
- the API JAR is not covered by AE Tuner's Apache-2.0 licence;
- public-source developers obtain an authorized copy independently, as described in `lib/README.md`;
- normal plugin users install only the AE Tuner plugin JAR and do not need the API JAR as a separate runtime installation.

Publishing AE Tuner without `TunerStudioPluginAPI.jar` is explicitly approved. The dependency exclusion is a publication rule, not a remaining publication blocker.

## Product names and trademarks

TunerStudio, EFI Analytics, EpicEFI and other third-party names are used only to describe compatibility and integration. They remain the property of their respective owners. No endorsement or ownership is implied.

## Repositories

Private authority:

```text
PJawZK/AE-Tuner-EPICEFI-
```

Public sanitized downstream:

```text
PJawZK/AE-Tuner-EPICEFI_Public
```
