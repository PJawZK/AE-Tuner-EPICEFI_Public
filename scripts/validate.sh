#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

bash scripts/build.sh

SOURCE_VERSION="$(sed -n 's/.*public static final String VERSION = "\([^"]*\)".*/\1/p' src/main/java/se/anders/tunerstudio/aetuner/AeTunerPlugin.java)"
POM_VERSION="$(sed -n '0,/<version>/{s/.*<version>\([^<]*\)<\/version>.*/\1/p}' pom.xml)"
JAR="dist/ae-tuner-epicefi-${SOURCE_VERSION}.jar"

[[ "$SOURCE_VERSION" == "$POM_VERSION" ]] || {
  echo "Version mismatch: source=$SOURCE_VERSION pom=$POM_VERSION" >&2
  exit 1
}

MANIFEST="$(unzip -p "$JAR" META-INF/MANIFEST.MF | tr -d '\r')"
grep -q '^ApplicationPlugin: se.anders.tunerstudio.aetuner.AeTunerPlugin$' <<<"$MANIFEST"
grep -q "^Implementation-Version: ${SOURCE_VERSION}$" <<<"$MANIFEST"

CLASS_FILE="target/classes/se/anders/tunerstudio/aetuner/AeTunerPlugin.class"
MAJOR_HEX="$(od -An -t u1 -j 6 -N 2 "$CLASS_FILE" | awk '{print $1*256+$2}')"
if (( MAJOR_HEX > 52 )); then
  echo "Class version $MAJOR_HEX is newer than Java 8 (52)" >&2
  exit 1
fi

if [[ -d src/test/java ]]; then
  rm -rf target/test-classes target/test-sources.list
  mkdir -p target/test-classes
  find src/test/java -name '*.java' -print | sort > target/test-sources.list
  if [[ -s target/test-sources.list ]]; then
    javac --release 8 \
      -cp "target/classes:lib/TunerStudioPluginAPI.jar" \
      -d target/test-classes \
      @target/test-sources.list
    for test_class in \
      se.anders.tunerstudio.aetuner.SessionMonitorRegressionTest \
      se.anders.tunerstudio.aetuner.OutputChannelResolutionRegressionTest \
      se.anders.tunerstudio.aetuner.RecommendationHistoryRegressionTest \
      se.anders.tunerstudio.aetuner.MapBlendSuggestionRegressionTest
    do
      java -cp "target/classes:target/test-classes:lib/TunerStudioPluginAPI.jar" "$test_class"
    done
    java -Djava.awt.headless=true \
      -cp "target/classes:target/test-classes:lib/TunerStudioPluginAPI.jar" \
      se.anders.tunerstudio.aetuner.LongSessionCharacterizationTest
  fi
fi

bash scripts/validation-tooling-regression.sh
bash scripts/check-conflict-markers.sh

# Private authority exports include the continuation checker. Sanitized public
# source exports intentionally omit private continuation state, so validation
# remains usable there after the user supplies an authorized Plugin API JAR.
if [[ -f scripts/check-continuation-authority.sh ]]; then
  bash scripts/check-continuation-authority.sh
fi

if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git diff --check
fi

echo "Validation passed for AE Tuner (EPICEFI) ${SOURCE_VERSION}"
