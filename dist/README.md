# Local build output

`dist/` is the local staging directory for generated AE Tuner build artifacts. Generated files are ignored by Git; only this README is tracked.

Run:

```bash
bash scripts/build.sh
```

to create the current local plugin JAR.

Do not commit generated JARs from this directory. Public release candidates and releases are distributed through GitHub Releases with their checksums and release notes.
