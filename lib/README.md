# TunerStudio Plugin API dependency

The plugin requires `TunerStudioPluginAPI.jar` to compile from source.

That binary is a separate third-party dependency and is intentionally excluded from the public repository and public source exports. It is not covered by AE Tuner's Apache-2.0 licence.

## End users

End users installing a prebuilt AE Tuner plugin JAR do not need to copy or install `TunerStudioPluginAPI.jar` separately. Install only the AE Tuner JAR according to the public README.

## Developers building from source

Obtain an authorized copy of the TunerStudio Plugin API from EFI Analytics or from your own TunerStudio installation and place it at:

```text
lib/TunerStudioPluginAPI.jar
```

Then run:

```bash
bash scripts/validate.sh
```

Do not commit, publish, bundle or redistribute the API JAR unless its own applicable licence expressly permits that use.
