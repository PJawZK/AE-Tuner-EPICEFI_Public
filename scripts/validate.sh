#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

bash scripts/build.sh

SOURCE_VERSION="$(sed -n 's/.*public static final String VERSION = "\([^"]*\)".*/\1/p' src/main/java/se/anders/tunerstudio/aetuner/AeTunerPlugin.java)"
POM_VERSION="$(sed -n '0,/<version>/{s/.*<version>\([^<]*\)<\/version>.*/\1/p}' pom.xml)"
JAR="dist/ae-tuner-epicefi-${SOURCE_VERSION}.jar"

[[ "$SOURCE_VERSION" == "$POM_VERSION" ]] || {
  echo "Version mismatch: source=$SOURCE_VERSION pom=$POM_VERSION" >&2
  exit 1
}

MANIFEST="$(unzip -p "$JAR" META-INF/MANIFEST.MF | tr -d '\r')"
grep -q '^ApplicationPlugin: se.anders.tunerstudio.aetuner.AeTunerPlugin$' <<<"$MANIFEST"
grep -q "^Implementation-Version: ${SOURCE_VERSION}$" <<<"$MANIFEST"

CLASS_FILE="target/classes/se/anders/tunerstudio/aetuner/AeTunerPlugin.class"
MAJOR_HEX="$(od -An -t u1 -j 6 -N 2 "$CLASS_FILE" | awk '{print $1*256+$2}')"
if (( MAJOR_HEX > 52 )); then
  echo "Class version $MAJOR_HEX is newer than Java 8 (52)" >&2
  exit 1
fi

if [[ -d src/test/java ]]; then
  rm -rf target/test-classes target/test-sources.list
  mkdir -p target/test-classes
  find src/test/java -name '*.java' -print | sort > target/test-sources.list
  if [[ -s target/test-sources.list ]]; then
    javac --release 8 \
      -cp "target/classes:lib/TunerStudioPluginAPI.jar" \
      -d target/test-classes \
      @target/test-sources.list
    for test_class in \
      se.anders.tunerstudio.aetuner.passive.SessionMonitorRegressionTest \
      se.anders.tunerstudio.aetuner.passive.OutputChannelResolutionRegressionTest \
      se.anders.tunerstudio.aetuner.passive.RecommendationHistoryRegressionTest \
      se.anders.tunerstudio.aetuner.proposal.BlendDurationPolicyRegressionTest \
      se.anders.tunerstudio.aetuner.proposal.MapBlendSuggestionRegressionTest \
      se.anders.tunerstudio.aetuner.guided.GuidedVehicleTestLimitsRegressionTest \
      se.anders.tunerstudio.aetuner.guided.GuidedVehicleTest9RegressionTest \
      se.anders.tunerstudio.aetuner.guided.PhaseA3LegacyInvariantMigrationTest \
      se.anders.tunerstudio.aetuner.guided.GuidedAttemptTraceRegressionTest \
      se.anders.tunerstudio.aetuner.guided.PedalPlateauDetectorRegressionTest \
      se.anders.tunerstudio.aetuner.guided.RoadBaselineTrackerRegressionTest \
      se.anders.tunerstudio.aetuner.guided.MapCatchupMeasurementRegressionTest \
      se.anders.tunerstudio.aetuner.guided.PedalOpeningDetectorRegressionTest \
      se.anders.tunerstudio.aetuner.guided.BlendDurationComparabilityGroupsRegressionTest \
      se.anders.tunerstudio.aetuner.guided.PhaseBGuidedArchitectureRegressionTest \
      se.anders.tunerstudio.aetuner.guided.GuidedAttemptEvidenceRegressionTest \
      se.anders.tunerstudio.aetuner.guided.BlendDurationGuidedSummaryRegressionTest \
      se.anders.tunerstudio.aetuner.guided.PhaseA2AdaptiveTypeMigrationTest \
      se.anders.tunerstudio.aetuner.guided.GuidedLifecycleRegressionTest \
      se.anders.tunerstudio.aetuner.RuntimeOverhaulRegressionTest \
      se.anders.tunerstudio.aetuner.guided.GuidedBlendProposalRegressionTest \
      se.anders.tunerstudio.aetuner.guided.GuidedAudioCueRegressionTest \
      se.anders.tunerstudio.aetuner.guided.GuidedAudioCueLabRegressionTest \
      se.anders.tunerstudio.aetuner.guided.GuidedUiRegressionTest \
      se.anders.tunerstudio.aetuner.VehicleTestIdentityRegressionTest \
      se.anders.tunerstudio.aetuner.guided.GuidedSampleDispatcherRegressionTest \
      se.anders.tunerstudio.aetuner.guided.PhaseCSampleDispatchArchitectureTest \
      se.anders.tunerstudio.aetuner.passive.PhaseDPanelLayoutArchitectureTest \
      se.anders.tunerstudio.aetuner.passive.PhaseDAdvisoryActionsArchitectureTest \
      se.anders.tunerstudio.aetuner.passive.PhaseDOverviewControllerArchitectureTest \
      se.anders.tunerstudio.aetuner.model.MapPredictionMetricsRegressionTest \
      se.anders.tunerstudio.aetuner.model.TransientEventAnalyzerRegressionTest \
      se.anders.tunerstudio.aetuner.model.TransientEventAssessmentRegressionTest \
      se.anders.tunerstudio.aetuner.model.TransientEventFormatterRegressionTest \
      se.anders.tunerstudio.aetuner.model.PhaseETransientEventArchitectureTest \
      se.anders.tunerstudio.aetuner.PhaseFPackageArchitectureTest \
      se.anders.tunerstudio.aetuner.guided.GuidedEvidenceRecorderRegressionTest \
      se.anders.tunerstudio.aetuner.recovery.EvidenceRecoveryStoreRegressionTest \
      se.anders.tunerstudio.aetuner.guided.GuidedChannelValidityRegressionTest
    do
      java -cp "target/classes:target/test-classes:lib/TunerStudioPluginAPI.jar" "$test_class"
    done
    java -Djava.awt.headless=true \
      -cp "target/classes:target/test-classes:lib/TunerStudioPluginAPI.jar" \
      se.anders.tunerstudio.aetuner.passive.LongSessionCharacterizationTest
  fi
fi

bash scripts/validation-tooling-regression.sh
bash scripts/check-conflict-markers.sh

if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git diff --check
fi

echo "Validation passed for AE Tuner (EPICEFI) ${SOURCE_VERSION}"