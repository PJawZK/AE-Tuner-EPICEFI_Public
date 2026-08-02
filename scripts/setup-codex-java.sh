#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

for cmd in java javac jar unzip sha256sum; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Required command is unavailable: $cmd" >&2
    exit 1
  fi
done

if [[ ! -f lib/TunerStudioPluginAPI.jar ]]; then
  echo "Missing lib/TunerStudioPluginAPI.jar" >&2
  exit 1
fi

java -version
javac -version
bash scripts/validate.sh
