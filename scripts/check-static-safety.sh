#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SOURCE_VERSION="$(sed -n 's/.*public static final String VERSION = "\([^"]*\)".*/\1/p' src/main/java/se/anders/tunerstudio/aetuner/AeTunerPlugin.java)"
POM_VERSION="$(sed -n '0,/<version>/{s/.*<version>\([^<]*\)<\/version>.*/\1/p}' pom.xml)"
JAR="dist/ae-tuner-epicefi-${SOURCE_VERSION}.jar"

[[ "$SOURCE_VERSION" == "$POM_VERSION" ]] || {
  echo "Version mismatch: source=$SOURCE_VERSION pom=$POM_VERSION" >&2
  exit 1
}
[[ -f "$JAR" ]] || {
  echo "Expected deterministic JAR is missing: $JAR" >&2
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

# Working-tune writes are authorized only through one explicit coordinator.
# Burn calls remain prohibited. Keep this source-level gate cheap enough to run
# on every development branch push.
if grep -R --include='*.java' -nE '\.(burnData|sendBurnCommand)[[:space:]]*\(' src/main/java; then
  echo "Burn API usage is prohibited in AE Tuner production source" >&2
  exit 1
fi
WRITE_CALLS="$(grep -R --include='*.java' -nE '\.updateParameter[[:space:]]*\(' src/main/java || true)"
if [[ -n "$WRITE_CALLS" ]]; then
  while IFS= read -r line; do
    [[ "$line" == src/main/java/se/anders/tunerstudio/aetuner/host/ProposalApplyCoordinator.java:* ]] || {
      echo "Controller updateParameter call exists outside ProposalApplyCoordinator: $line" >&2
      exit 1
    }
  done <<<"$WRITE_CALLS"
fi

command -v python3 >/dev/null 2>&1 || {
  echo "python3 is required for semantic MSQ apply verification" >&2
  exit 1
}
python3 scripts/verify-msq-apply.py --self-test

bash scripts/check-conflict-markers.sh

if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git diff --check
fi

echo "Static/write safety checks passed for AE Tuner (EPICEFI) ${SOURCE_VERSION}"
