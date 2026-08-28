#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

command -v xvfb-run >/dev/null 2>&1 || {
  echo "xvfb-run is required for the physical-candidate synthetic panel gate." >&2
  exit 1
}

bash scripts/validate.sh
bash scripts/synthetic-plugin-integration.sh

SOURCE_VERSION="$(sed -n 's/.*public static final String VERSION = "\([^"]*\)".*/\1/p' src/main/java/se/anders/tunerstudio/aetuner/AeTunerPlugin.java)"
JAR="dist/ae-tuner-epicefi-${SOURCE_VERSION}.jar"

[[ -f "$JAR" ]] || {
  echo "Candidate JAR is missing after validation: $JAR" >&2
  exit 1
}

mapfile -t jars < <(find dist -maxdepth 1 -type f -name 'ae-tuner-epicefi-*.jar' | sort)
if [[ ${#jars[@]} -ne 1 || "${jars[0]}" != "$JAR" ]]; then
  echo "Candidate check requires exactly one deterministic AE Tuner JAR in dist/: $JAR" >&2
  printf 'Found: %s\n' "${jars[@]}" >&2
  exit 1
fi

sha256sum "$JAR"
echo "Candidate check passed for AE Tuner (EPICEFI) ${SOURCE_VERSION}"
