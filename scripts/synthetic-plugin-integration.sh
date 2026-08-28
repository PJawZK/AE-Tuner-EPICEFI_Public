#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${SYNTHETIC_INTEGRATION_OUT:-$ROOT/target/synthetic-plugin-integration}"

cd "$ROOT"
rm -rf "$OUT"
mkdir -p "$OUT"

if [[ ! -d target/classes || ! -d target/test-classes ]]; then
  echo "Synthetic integration prerequisites are missing." >&2
  echo "Run 'bash scripts/check-fast.sh' for development or 'bash scripts/validate.sh' for a milestone first." >&2
  exit 1
fi

export SYNTHETIC_INTEGRATION_OUT="$OUT"

xvfb-run -a -s '-screen 0 1440x1000x24' \
  java -Djava.awt.headless=false \
  -cp 'target/classes:target/test-classes:lib/TunerStudioPluginAPI.jar' \
  se.anders.tunerstudio.aetuner.passive.SyntheticPluginIntegrationEntryPoint

for required in \
  result.txt \
  workspace-overview-1366.png \
  workspace-passive-setup-1366.png \
  workspace-evidence-audio-1366.png \
  workspace-guided-1366.png \
  workspace-guided-focus-map-estimate.png
do
  test -s "$OUT/$required" || {
    echo "Synthetic workspace integration output missing or empty: $required" >&2
    exit 1
  }
done

grep -q 'Workspace synthetic integration: passed' "$OUT/result.txt"
grep -q 'Passive tabs: Overview | Setup / Calibration' "$OUT/result.txt"
grep -q 'Evidence tabs: Overview | Channels / Runtime | Audio Cue Lab | Recovery / Audit' "$OUT/result.txt"
grep -q 'Guided Restore/Reconnect horizontal reachability: 1366 / 1024 / 820 PASS' "$OUT/result.txt"
grep -q 'Guided Focus: modeless MAP Estimate heat map open/hide/reopen PASS' "$OUT/result.txt"

sha256sum \
  "$OUT/result.txt" \
  "$OUT/workspace-overview-1366.png" \
  "$OUT/workspace-passive-setup-1366.png" \
  "$OUT/workspace-evidence-audio-1366.png" \
  "$OUT/workspace-guided-1366.png" \
  "$OUT/workspace-guided-focus-map-estimate.png" \
  > "$OUT/evidence.sha256"

echo "Synthetic workspace integration evidence written to $OUT"
