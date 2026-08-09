#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${SYNTHETIC_INTEGRATION_OUT:-$ROOT/target/synthetic-plugin-integration}"

cd "$ROOT"
rm -rf "$OUT"
mkdir -p "$OUT"

if [[ ! -d target/classes || ! -d target/test-classes ]]; then
  bash scripts/validate.sh
fi

export SYNTHETIC_INTEGRATION_OUT="$OUT"

xvfb-run -a -s '-screen 0 1440x1000x24' \
  java -Djava.awt.headless=false \
  -cp 'target/classes:target/test-classes:lib/TunerStudioPluginAPI.jar' \
  se.anders.tunerstudio.aetuner.passive.SyntheticPluginIntegrationTest

for required in \
  result.txt \
  synthetic-v0317-shutdown.txt \
  synthetic-session-guidance.txt \
  synthetic-events.csv \
  synthetic-map-predict-report.txt \
  synthetic-plugin-panel.png \
  synthetic-plugin-panel-narrow.png \
  synthetic-plugin-overview-narrow-bottom.png
do
  test -s "$OUT/$required" || {
    echo "Synthetic integration output missing or empty: $required" >&2
    exit 1
  }
done

REPORT="$OUT/synthetic-map-predict-report.txt"
grep -q 'Predictive Map Blend Duration per-RPM evidence.' "$REPORT"
grep -q 'No interpolation or smoothing is applied.' "$REPORT"
grep -q '600 RPM point (region' "$REPORT"
grep -q '2450 RPM point (region' "$REPORT"
grep -q 'No paste-ready Blend Duration proposal is available.' "$REPORT"
grep -q 'multiple detector bursts remain visible diagnostically but never define the base curve' "$REPORT"

sha256sum \
  "$OUT/synthetic-v0317-shutdown.txt" \
  "$OUT/synthetic-session-guidance.txt" \
  "$OUT/synthetic-events.csv" \
  "$OUT/synthetic-map-predict-report.txt" \
  "$OUT/synthetic-plugin-panel.png" \
  "$OUT/synthetic-plugin-panel-narrow.png" \
  "$OUT/synthetic-plugin-overview-narrow-bottom.png" \
  > "$OUT/evidence.sha256"

echo "Synthetic plugin integration evidence written to $OUT"
