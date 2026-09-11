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

# Exact legacy Guided fallback remains independently covered. Passive and
# Diagnostics are globally ported utility workspaces even in this fallback lane.
xvfb-run -a -s '-screen 0 1440x1000x24' \
  java -Djava.awt.headless=false -Dae.tuner.guided.ui.v019=false \
  -cp 'target/classes:target/test-classes:lib/TunerStudioPluginAPI.jar' \
  se.anders.tunerstudio.aetuner.passive.SyntheticPluginIntegrationEntryPoint

# Default path must be the structurally ported v0.19 production UI.
xvfb-run -a -s '-screen 0 1440x1000x24' \
  java -Djava.awt.headless=false \
  -cp 'target/classes:target/test-classes:lib/TunerStudioPluginAPI.jar' \
  se.anders.tunerstudio.aetuner.guided.GuidedProductionWorkspaceSyntheticIntegrationTest

# Secondary production utilities have their own real-Swing evidence lane so
# every new section and both Light/Dark theme states remain permanently gated.
xvfb-run -a -s '-screen 0 1440x1000x24' \
  java -Djava.awt.headless=false \
  -cp 'target/classes:target/test-classes:lib/TunerStudioPluginAPI.jar' \
  se.anders.tunerstudio.aetuner.ui.UtilityWorkspaceSyntheticIntegrationTest

# Task Settings is part of the normal v0.19 path. Render the real production
# editor independently so legacy tabs/stock confirmation chrome cannot return.
xvfb-run -a -s '-screen 0 1440x1000x24' \
  java -Djava.awt.headless=false \
  -cp 'target/classes:target/test-classes:lib/TunerStudioPluginAPI.jar' \
  se.anders.tunerstudio.aetuner.guided.GuidedTaskSettingsUiSyntheticTest

# All implemented baseline recipes that intentionally share the generic bound
# evidence Focus must remain production-bound rather than falling back to the
# unsupported placeholder. Dedicated numerical Focus classes are covered by the
# main production workspace synthetic lane above.
xvfb-run -a -s '-screen 0 1440x1000x24' \
  java -Djava.awt.headless=false \
  -cp 'target/classes:target/test-classes:lib/TunerStudioPluginAPI.jar' \
  se.anders.tunerstudio.aetuner.guided.GuidedV019BoundEvidenceFocusSyntheticTest

# Existing production Focus windows remain independently regression-covered.
xvfb-run -a -s '-screen 0 1440x1000x24' \
  java -Djava.awt.headless=false \
  -cp 'target/classes:target/test-classes:lib/TunerStudioPluginAPI.jar' \
  se.anders.tunerstudio.aetuner.guided.EngagementDriverViewSyntheticTest

xvfb-run -a -s '-screen 0 1440x1000x24' \
  java -Djava.awt.headless=false \
  -cp 'target/classes:target/test-classes:lib/TunerStudioPluginAPI.jar' \
  se.anders.tunerstudio.aetuner.guided.EngagementDetailsScrollSyntheticTest

xvfb-run -a -s '-screen 0 1440x1000x24' \
  java -Djava.awt.headless=false \
  -cp 'target/classes:target/test-classes:lib/TunerStudioPluginAPI.jar' \
  se.anders.tunerstudio.aetuner.guided.FoundationThresholdFocusSyntheticTest

xvfb-run -a -s '-screen 0 1440x1000x24' \
  java -Djava.awt.headless=false \
  -cp 'target/classes:target/test-classes:lib/TunerStudioPluginAPI.jar' \
  se.anders.tunerstudio.aetuner.guided.BlendDurationFocusSyntheticTest

for required in \
  result.txt \
  guided-v019-ui-result.txt \
  utility-v019-ui-result.txt \
  workspace-overview-1366.png \
  workspace-passive-event-preview-1366.png \
  workspace-passive-setup-1366.png \
  workspace-evidence-audio-1366.png \
  workspace-guided-1366.png \
  workspace-guided-v019-light-1366.png \
  workspace-guided-v019-capture-light-1366.png \
  workspace-guided-v019-dark-1024.png \
  workspace-guided-v019-dark-820.png \
  workspace-guided-v019-threshold-prepare-info-light-900.png \
  workspace-guided-v019-threshold-capture-info-light-900.png \
  workspace-guided-v019-passive-popout-light-1180.png \
  workspace-guided-v019-diagnostics-popout-light-1120.png \
  workspace-guided-v019-task-settings-light-1060.png \
  workspace-guided-v019-task-settings-dark-1060.png \
  utility-passive-overview-light-1180.png \
  utility-passive-event-preview-light-1180.png \
  utility-passive-notes-light-1180.png \
  utility-passive-guidance-light-1180.png \
  utility-passive-setup-light-1180.png \
  utility-passive-setup-dark-1180.png \
  utility-diagnostics-overview-light-1120.png \
  utility-diagnostics-runtime-light-1120.png \
  utility-diagnostics-audio-light-1120.png \
  utility-diagnostics-recovery-light-1120.png \
  utility-diagnostics-runtime-dark-1120.png \
  workspace-guided-v019-tps-focus-light-1260.png \
  workspace-guided-v019-threshold-focus-light-1260.png \
  workspace-guided-v019-decel-detection-focus-light-1260.png \
  workspace-guided-v019-map-focus-light-1260.png \
  workspace-guided-v019-blend-focus-light-1260.png \
  workspace-guided-v019-tps-fuel-focus-light-1260.png \
  workspace-guided-v019-tps-scaling-focus-light-1260.png \
  workspace-guided-v019-wall-focus-light-1260.png \
  workspace-guided-v019-wall-advanced-focus-light-1260.png \
  workspace-guided-v019-instant-setup-focus-light-1260.png \
  workspace-guided-v019-instant-strength-focus-light-1260.png \
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
  workspace-guided-focus-map-estimate.png \
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
    echo "Synthetic workspace integration output missing or empty: $required" >&2
    exit 1
  }
done

grep -q 'Workspace synthetic integration: passed' "$OUT/result.txt"
grep -q 'Passive sections: Overview | Event Preview | Session Notes | Session Guidance | Setup / Calibration' "$OUT/result.txt"
grep -q 'Evidence sections: Overview | Channels / Runtime | Audio Cue Lab | Recovery / Audit' "$OUT/result.txt"
grep -q 'Passive/Diagnostics nested legacy tabs: ABSENT' "$OUT/result.txt"
grep -q 'Guided Restore/Reconnect horizontal reachability: 1366 / 1024 / 820 PASS' "$OUT/result.txt"
grep -q 'Guided Focus: modeless MAP Estimate heat map open/hide/reopen PASS' "$OUT/result.txt"

grep -q 'Guided v0.19 structural production Swing parity: passed' "$OUT/guided-v019-ui-result.txt"
grep -q 'Direct host: old Overview/Guided/Passive/Evidence tabs absent PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'Prototype main structure: task selector + graphical stepper + 2x3 six-card workflow PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'Legacy Guided surface absent from normal v0.19 component tree PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'Task ownership: 19 visible / 16 production bindings clickable' "$OUT/guided-v019-ui-result.txt"
grep -q 'Full TPS AE / Wall Wetting / Instant Fuel baseline routes: AVAILABLE + non-unsupported Focus PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'Prepare: Read Working Tune -> Continue -> Capture PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'Task-specific Options/Info: Threshold Prepare + Capture PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'Passive Analysis + Evidence/Diagnostics: modeless pop-outs PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'TPS Movement / Timing: prototype Guided Focus + production evidence PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'Threshold / Sensitivity: prototype Guided Focus + production separation model PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'Decel Detection: dedicated falling-TPS Focus + threshold-only production authority PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'TPS AE: base + compensation Focus routes PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'Wall Wetting: base + advanced Focus routes PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'Instant Fuel: global setup + event-strength Focus routes PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'MAP Estimate: prototype table-first Focus + production Working/Learned/Proposed PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'Blend Duration: prototype five-phase Focus + measured production response PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'Review / Apply / Result: incomplete evidence remains fail-closed and routes back to Capture PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'Still-planned Decel tasks: visible / fail-closed routing PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'Widths: 1366 / 1024 / 820 PASS' "$OUT/guided-v019-ui-result.txt"
grep -q 'Explicit legacy fallback remains covered by separate synthetic lane PASS' "$OUT/guided-v019-ui-result.txt"

grep -q 'v0.19 utility workspace Swing integration: passed' "$OUT/utility-v019-ui-result.txt"
grep -q 'Passive section routing: Overview / Event / Notes / Guidance / Setup PASS' "$OUT/utility-v019-ui-result.txt"
grep -q 'Diagnostics section routing: Overview / Runtime / Audio / Recovery PASS' "$OUT/utility-v019-ui-result.txt"
grep -q 'Nested legacy tabs: ABSENT' "$OUT/utility-v019-ui-result.txt"
grep -q 'Light Side / Dark Side utility theme continuity PASS' "$OUT/utility-v019-ui-result.txt"

sha256sum \
  "$OUT/result.txt" \
  "$OUT/guided-v019-ui-result.txt" \
  "$OUT/utility-v019-ui-result.txt" \
  "$OUT/workspace-overview-1366.png" \
  "$OUT/workspace-passive-event-preview-1366.png" \
  "$OUT/workspace-passive-setup-1366.png" \
  "$OUT/workspace-evidence-audio-1366.png" \
  "$OUT/workspace-guided-1366.png" \
  "$OUT/workspace-guided-v019-light-1366.png" \
  "$OUT/workspace-guided-v019-capture-light-1366.png" \
  "$OUT/workspace-guided-v019-dark-1024.png" \
  "$OUT/workspace-guided-v019-dark-820.png" \
  "$OUT/workspace-guided-v019-threshold-prepare-info-light-900.png" \
  "$OUT/workspace-guided-v019-threshold-capture-info-light-900.png" \
  "$OUT/workspace-guided-v019-passive-popout-light-1180.png" \
  "$OUT/workspace-guided-v019-diagnostics-popout-light-1120.png" \
  "$OUT/workspace-guided-v019-task-settings-light-1060.png" \
  "$OUT/workspace-guided-v019-task-settings-dark-1060.png" \
  "$OUT/utility-passive-overview-light-1180.png" \
  "$OUT/utility-passive-event-preview-light-1180.png" \
  "$OUT/utility-passive-notes-light-1180.png" \
  "$OUT/utility-passive-guidance-light-1180.png" \
  "$OUT/utility-passive-setup-light-1180.png" \
  "$OUT/utility-passive-setup-dark-1180.png" \
  "$OUT/utility-diagnostics-overview-light-1120.png" \
  "$OUT/utility-diagnostics-runtime-light-1120.png" \
  "$OUT/utility-diagnostics-audio-light-1120.png" \
  "$OUT/utility-diagnostics-recovery-light-1120.png" \
  "$OUT/utility-diagnostics-runtime-dark-1120.png" \
  "$OUT/workspace-guided-v019-tps-focus-light-1260.png" \
  "$OUT/workspace-guided-v019-threshold-focus-light-1260.png" \
  "$OUT/workspace-guided-v019-decel-detection-focus-light-1260.png" \
  "$OUT/workspace-guided-v019-map-focus-light-1260.png" \
  "$OUT/workspace-guided-v019-blend-focus-light-1260.png" \
  "$OUT/workspace-guided-v019-tps-fuel-focus-light-1260.png" \
  "$OUT/workspace-guided-v019-tps-scaling-focus-light-1260.png" \
  "$OUT/workspace-guided-v019-wall-focus-light-1260.png" \
  "$OUT/workspace-guided-v019-wall-advanced-focus-light-1260.png" \
  "$OUT/workspace-guided-v019-instant-setup-focus-light-1260.png" \
  "$OUT/workspace-guided-v019-instant-strength-focus-light-1260.png" \
  "$OUT/workspace-guided-v019-bound-tps-scaling-light-1260.png" \
  "$OUT/workspace-guided-v019-bound-tps-completion-light-1260.png" \
  "$OUT/workspace-guided-v019-bound-tps-validation-light-1260.png" \
  "$OUT/workspace-guided-v019-bound-wall-advanced-light-1260.png" \
  "$OUT/workspace-guided-v019-bound-wall-validation-light-1260.png" \
  "$OUT/workspace-guided-v019-bound-instant-event-strength-light-1260.png" \
  "$OUT/workspace-guided-v019-bound-instant-conditions-light-1260.png" \
  "$OUT/workspace-guided-v019-bound-instant-validation-light-1260.png" \
  "$OUT/workspace-guided-v019-review-light-1260.png" \
  "$OUT/workspace-guided-v019-apply-no-write-light-1260.png" \
  "$OUT/workspace-guided-v019-result-incomplete-light-1180.png" \
  "$OUT/workspace-guided-focus-map-estimate.png" \
  "$OUT/workspace-guided-focus-engagement-driver-1180.png" \
  "$OUT/workspace-guided-focus-engagement-driver-1024.png" \
  "$OUT/workspace-guided-focus-engagement-driver-820.png" \
  "$OUT/workspace-guided-focus-engagement-details-scroll-1366.png" \
  "$OUT/workspace-guided-focus-foundation-threshold-driver-1180.png" \
  "$OUT/workspace-guided-focus-foundation-threshold-details-1180.png" \
  "$OUT/workspace-guided-focus-blend-duration-driver-1180.png" \
  "$OUT/workspace-guided-focus-blend-duration-driver-820.png" \
  "$OUT/workspace-guided-focus-blend-duration-details-1180.png" \
  > "$OUT/evidence.sha256"

echo "Synthetic workspace integration evidence written to $OUT"
