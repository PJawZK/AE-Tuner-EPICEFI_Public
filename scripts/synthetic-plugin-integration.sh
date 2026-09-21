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
CP='target/classes:target/test-classes:lib/TunerStudioPluginAPI.jar'
XVFB=(xvfb-run -a -s '-screen 0 1440x1000x24')

# Permanent production host: v0.19 Guided only. This lane also proves that the
# old Passive control/pop-out and legacy top-level tab shell are absent.
"${XVFB[@]}" java -Djava.awt.headless=false -cp "$CP" \
  se.anders.tunerstudio.aetuner.guided.GuidedProductionWorkspaceSyntheticIntegrationTest

# Normal production Task Settings remains independently rendered so the guarded
# Apply/Restore presentation cannot drift back toward stock confirmation chrome.
"${XVFB[@]}" java -Djava.awt.headless=false -cp "$CP" \
  se.anders.tunerstudio.aetuner.guided.GuidedTaskSettingsUiSyntheticTest

# Implemented recipes that intentionally share the generic bound-evidence Focus
# remain production-bound rather than falling back to unsupported placeholders.
"${XVFB[@]}" java -Djava.awt.headless=false -cp "$CP" \
  se.anders.tunerstudio.aetuner.guided.GuidedV019BoundEvidenceFocusSyntheticTest

# Dedicated Focus views keep independent real-Swing evidence lanes.
"${XVFB[@]}" java -Djava.awt.headless=false -cp "$CP" \
  se.anders.tunerstudio.aetuner.guided.EngagementDriverViewSyntheticTest
"${XVFB[@]}" java -Djava.awt.headless=false -cp "$CP" \
  se.anders.tunerstudio.aetuner.guided.EngagementDetailsScrollSyntheticTest
"${XVFB[@]}" java -Djava.awt.headless=false -cp "$CP" \
  se.anders.tunerstudio.aetuner.guided.FoundationThresholdFocusSyntheticTest
"${XVFB[@]}" java -Djava.awt.headless=false -cp "$CP" \
  se.anders.tunerstudio.aetuner.guided.BlendDurationFocusSyntheticTest

for required in \
  guided-v019-ui-result.txt \
  workspace-guided-v019-light-1366.png \
  workspace-guided-v019-capture-light-1366.png \
  workspace-guided-v019-dark-1024.png \
  workspace-guided-v019-dark-820.png \
  workspace-guided-v019-threshold-prepare-info-light-900.png \
  workspace-guided-v019-threshold-capture-info-light-900.png \
  workspace-guided-v019-diagnostics-popout-light-1120.png \
  workspace-guided-v019-task-settings-light-1060.png \
  workspace-guided-v019-task-settings-dark-1060.png \
  workspace-guided-v019-tps-focus-light-1260.png \
  workspace-guided-v019-threshold-focus-light-1260.png \
  workspace-guided-v019-decel-detection-focus-light-1260.png \
  workspace-guided-v019-map-focus-light-1260.png \
  workspace-guided-v019-blend-focus-light-1260.png \
  workspace-guided-v019-bound-tps-scaling-light-1260.png \
  workspace-guided-v019-bound-tps-completion-light-1260.png \
  workspace-guided-v019-bound-tps-validation-light-1260.png \
  workspace-guided-v019-bound-wall-advanced-light-1260.png \
  workspace-guided-v019-bound-wall-validation-light-1260.png \
  workspace-guided-v019-bound-instant-event-strength-light-1260.png \
  workspace-guided-v019-bound-instant-conditions-light-1260.png \
  workspace-guided-v019-bound-instant-validation-light-1260.png \
  workspace-guided-v019-review-light-1260.png \
  workspace-guided-v019-apply-no-write-light-1260.png \
  workspace-guided-v019-result-incomplete-light-1180.png \
  workspace-guided-focus-engagement-driver-1180.png \
  workspace-guided-focus-engagement-driver-1024.png \
  workspace-guided-focus-engagement-driver-820.png \
  workspace-guided-focus-engagement-details-scroll-1366.png \
  workspace-guided-focus-foundation-threshold-driver-1180.png \
  workspace-guided-focus-foundation-threshold-details-1180.png \
  workspace-guided-focus-blend-duration-driver-1180.png \
  workspace-guided-focus-blend-duration-driver-820.png \
  workspace-guided-focus-blend-duration-details-1180.png
do
  test -s "$OUT/$required" || {
    echo "Synthetic Guided integration output missing or empty: $required" >&2
    exit 1
  }
done

grep -q 'Guided v0.19 structural production Swing parity: passed' "$OUT/guided-v019-ui-result.txt"
grep -q 'Direct host: legacy tabs and Passive pop-out absent PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'Prototype main structure: task selector + stepper + 2x3 six-card workflow PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'Task ownership: 19 visible / 16 production bindings clickable' "$OUT/guided-v019-ui-result.txt"
grep -q 'Passive Analysis control: ABSENT from production component tree PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'Evidence/Diagnostics: lifecycle-owned pop-out PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'TPS / Threshold / Decel / MAP Estimate / Blend Focus routes PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'Review / Apply / Result: incomplete evidence remains fail-closed PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'Widths: 1366 / 1024 / 820 PASS' "$OUT/guided-v019-ui-result.txt"

find "$OUT" -type f ! -name evidence.sha256 -print0 \
  | sort -z \
  | xargs -0 -r sha256sum \
  > "$OUT/evidence.sha256"

echo "Guided-only synthetic workspace evidence written to $OUT"
