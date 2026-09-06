# TunerStudio Plugin API dependency

The plugin requires `TunerStudioPluginAPI.jar` to compile from source.

The private authority/development repository may contain that binary. It is a separate third-party dependency and is not covered by AE Tuner's Apache-2.0 licence.

## Public distribution

The API JAR is intentionally excluded from the public repository, public source exports and AE Tuner release JARs.

Do not publish, bundle or redistribute the API JAR unless its own applicable licence expressly permits that use.

## End users

End users installing a prebuilt AE Tuner plugin JAR do not need to copy or install `TunerStudioPluginAPI.jar` separately. Install only the AE Tuner plugin JAR according to the public README/release instructions.

## Developers building from public source

Obtain an authorized copy of the TunerStudio Plugin API from EFI Analytics or from your own TunerStudio installation and place it at:

```text
lib/TunerStudioPluginAPI.jar
```

Then run:

```bash
bash scripts/validate.sh
```
