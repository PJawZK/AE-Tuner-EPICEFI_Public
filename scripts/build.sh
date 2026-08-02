#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

JAVA_RELEASE="${JAVA_RELEASE:-8}"
PLUGIN_CLASS="se.anders.tunerstudio.aetuner.AeTunerPlugin"
VERSION="$(sed -n 's/.*public static final String VERSION = "\([^"]*\)".*/\1/p' src/main/java/se/anders/tunerstudio/aetuner/AeTunerPlugin.java)"
REPRODUCIBLE_JAR_DATE="${REPRODUCIBLE_JAR_DATE:-2000-01-01T00:00:00Z}"

if [[ -z "$VERSION" ]]; then
  echo "Could not determine plugin version from AeTunerPlugin.java" >&2
  exit 1
fi

if ! command -v javac >/dev/null 2>&1; then
  echo "javac is required" >&2
  exit 1
fi
if ! command -v jar >/dev/null 2>&1; then
  echo "jar is required" >&2
  exit 1
fi
if [[ ! -f lib/TunerStudioPluginAPI.jar ]]; then
  echo "Missing lib/TunerStudioPluginAPI.jar" >&2
  exit 1
fi

rm -rf target/classes target/sources.list target/MANIFEST.MF
mkdir -p target/classes dist
find src/main/java -name '*.java' -print | sort > target/sources.list

javac --release "$JAVA_RELEASE" \
  -cp lib/TunerStudioPluginAPI.jar \
  -d target/classes \
  @target/sources.list

cat > target/MANIFEST.MF <<MANIFEST
Manifest-Version: 1.0
ApplicationPlugin: ${PLUGIN_CLASS}
Implementation-Title: AE Tuner (EPICEFI)
Implementation-Version: ${VERSION}
MANIFEST

OUTPUT="dist/ae-tuner-epicefi-${VERSION}.jar"
rm -f "$OUTPUT"

# Modern JDKs can assign one fixed ZIP timestamp to every entry, making the
# canonical CI artifact reproducible across independent workflow jobs.
if jar --help 2>&1 | grep -q -- '--date'; then
  jar --create \
    --file "$OUTPUT" \
    --manifest target/MANIFEST.MF \
    --date "$REPRODUCIBLE_JAR_DATE" \
    -C target/classes .
else
  # Compatibility fallback for older JDK tooling. The plugin bytecode target
  # remains Java 8, but deterministic archive timestamps require a modern jar.
  jar cfm "$OUTPUT" target/MANIFEST.MF -C target/classes .
fi

echo "Built $OUTPUT"
sha256sum "$OUTPUT"
