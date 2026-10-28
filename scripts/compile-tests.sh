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

# Decel historical replay now spans native-log identity, running qualification,
# physical-event/state semantics, classification-boundary threshold decisions,
# compatible cross-log physical-event pooling, read-only result/coverage
# presentation, explicit proposal promotion and the missing-region Focus ->
# existing production capture handoff. Keep that chain as a cheap permanent gate.
for test_class in \
  se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalChannelIdentityRegressionTest \
  se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalRunningContextRegressionTest \
  se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalStateReplayRegressionTest \
  se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalThresholdDecisionRegressionTest \
  se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalThresholdProposalRegressionTest \
  se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalResultModelRegressionTest \
  se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalTest15RegressionTest \
  se.anders.tunerstudio.aetuner.guided.method.DecelHistoricalPoolRegressionTest \
  se.anders.tunerstudio.aetuner.guided.GuidedFocusHubIdleCaptureRegressionTest
do
  java -cp "target/classes:target/test-classes:lib/TunerStudioPluginAPI.jar" "$test_class"
done

# Report identity and evidence strength must originate at the replay source so
# every copy/export path receives them, not only the result dialog wrapper.
grep -q 'AE TUNER REPORT IDENTITY' \
  src/main/java/se/anders/tunerstudio/aetuner/guided/method/DecelHistoricalReplayService.java
grep -q 'Decel decision algorithm:' \
  src/main/java/se/anders/tunerstudio/aetuner/guided/method/DecelHistoricalReplayService.java
grep -q 'EVIDENCE STRENGTH — INDEPENDENT PHYSICAL EVENTS' \
  src/main/java/se/anders/tunerstudio/aetuner/guided/method/DecelHistoricalReplayService.java

# Multi-log pooling must be an explicit UI selection and remain tied to the
# detector-compatible policy rather than silently accumulating sessions.
grep -q 'setMultiSelectionEnabled(true)' \
  src/main/java/se/anders/tunerstudio/aetuner/guided/GuidedV019DecelHistoricalReplayDialog.java
grep -q 'DECEL_POOL_POLICY' \
  src/main/java/se/anders/tunerstudio/aetuner/host/BuildIdentity.java
grep -q 'Tune CRC, historical Decel threshold curve/values and Hold are intentionally excluded' \
  src/main/java/se/anders/tunerstudio/aetuner/guided/method/DecelHistoricalPoolCompatibility.java

echo "Test classes compiled; permanent Decel historical/result/coverage/Test-15/cross-log-pool regression gate passed."
