#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat >&2 <<'EOF'
Usage:
  bash scripts/verify-guided-write-test.sh BEFORE_MSQ AFTER_MSQ GUIDED_SESSION_FOLDER

The Guided session folder must be the export made immediately after the
successful Apply or Restore being checked. It must contain:
  guided-report.txt
  guided-events.csv
  guided-diagnostics.csv
  guided-apply-manifest.json
EOF
  exit 2
}

[[ $# -eq 3 ]] || usage

BEFORE="$1"
AFTER="$2"
SESSION="$3"
MANIFEST="$SESSION/guided-apply-manifest.json"

[[ -f "$BEFORE" ]] || { echo "Missing before MSQ: $BEFORE" >&2; exit 1; }
[[ -f "$AFTER" ]] || { echo "Missing after MSQ: $AFTER" >&2; exit 1; }
[[ -d "$SESSION" ]] || { echo "Missing Guided session folder: $SESSION" >&2; exit 1; }

for required in \
  guided-report.txt \
  guided-events.csv \
  guided-diagnostics.csv \
  guided-apply-manifest.json
do
  [[ -s "$SESSION/$required" ]] || {
    echo "Guided write-test evidence missing or empty: $SESSION/$required" >&2
    exit 1
  }
done

command -v python3 >/dev/null 2>&1 || {
  echo "python3 is required for semantic MSQ verification" >&2
  exit 1
}
command -v sha256sum >/dev/null 2>&1 || {
  echo "sha256sum is required for write-test evidence identity" >&2
  exit 1
}

BEFORE_REAL="$(readlink -f "$BEFORE")"
AFTER_REAL="$(readlink -f "$AFTER")"
if [[ "$BEFORE_REAL" == "$AFTER_REAL" ]]; then
  echo "before.msq and after.msq must be separate saved files" >&2
  exit 1
fi

echo "AE Tuner Guided write-test verification"
echo "========================================"
echo "Before:   $BEFORE_REAL"
echo "After:    $AFTER_REAL"
echo "Manifest: $(readlink -f "$MANIFEST")"
echo

python3 "$ROOT/scripts/verify-msq-apply.py" "$BEFORE" "$AFTER" "$MANIFEST"

echo
echo "Evidence SHA-256"
sha256sum \
  "$BEFORE" \
  "$AFTER" \
  "$MANIFEST" \
  "$SESSION/guided-report.txt" \
  "$SESSION/guided-events.csv" \
  "$SESSION/guided-diagnostics.csv"

echo
echo "Guided write-test semantic verification PASS"
