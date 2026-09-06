#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Historical filename retained because fast/full validation already call this
# hook. The physical-validation campaign no longer needs a second copied JAR.
# Verify and report the one canonical build artifact instead.
mapfile -t jars < <(find dist -maxdepth 1 -type f -name 'ae-tuner-epicefi-*.jar' -print | sort)
if [[ ${#jars[@]} -ne 1 ]]; then
  echo "Expected exactly one canonical AE Tuner JAR in dist; found ${#jars[@]}" >&2
  exit 1
fi

CANONICAL="${jars[0]}"
rm -f target/ae-tuner-plugin-dev.jar

echo "Validated canonical plugin: $CANONICAL"
echo "SHA-256: $(sha256sum "$CANONICAL" | awk '{print $1}')"
