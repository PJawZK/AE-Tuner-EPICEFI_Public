#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

[[ -d target/classes ]] || {
  echo "Production classes are missing. Run scripts/build.sh first." >&2
  exit 1
}
[[ -f lib/TunerStudioPluginAPI.jar ]] || {
  echo "Missing lib/TunerStudioPluginAPI.jar" >&2
  exit 1
}

rm -rf target/test-classes target/test-sources.list
mkdir -p target/test-classes
find src/test/java -name '*.java' -print | sort > target/test-sources.list

if [[ -s target/test-sources.list ]]; then
  javac --release 8 \
    -cp "target/classes:lib/TunerStudioPluginAPI.jar" \
    -d target/test-classes \
    @target/test-sources.list
fi

echo "Test classes compiled."
