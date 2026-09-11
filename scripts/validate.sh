#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

while IFS= read -r -d '' source; do
  first_line="$(head -n 1 "$source")"
  if [[ "$first_line" == *'\n'* ]]; then
    echo "Serialized Java source line boundaries detected in $source" >&2
    echo "The file contains literal \\n separators on its first physical line instead of real line breaks." >&2
    exit 1
  fi
done < <(find src/main/java src/test/java -type f -name '*.java' -print0)

bash scripts/build.sh
bash scripts/check-static-safety.sh
bash scripts/compile-tests.sh

if [[ -d src/test/java ]]; then
  for test_class in \
    se.anders.tunerstudio.aetuner.passive.SessionMonitorRegressionTest \
    se.anders.tunerstudio.aetuner.passive.CoherentLiveSampleAssemblerRegressionTest \
    se.anders.tunerstudio.aetuner.passive.OutputChannelResolutionRegressionTest \
    se.anders.tunerstudio.aetuner.passive.RecommendationHistoryRegressionTest \
    se.anders.tunerstudio.aetuner.proposal.BlendDurationPolicyRegressionTest \
    se.anders.tunerstudio.aetuner.proposal.MapBlendSuggestionRegressionTest \
    se.anders.tunerstudio.aetuner.proposal.ProposalWritePlanRegressionTest \
    se.anders.tunerstudio.aetuner.proposal.AeTableSuggestionMemoizationRegressionTest \
    se.anders.tunerstudio.aetuner.proposal.SessionExportSupportRegressionTest \
    se.anders.tunerstudio.aetuner.host.AeTuningParameterCatalogRegressionTest \
    se.anders.tunerstudio.aetuner.host.AeApplyRestoreValidationTargetsRegressionTest \
    se.anders.tunerstudio.aetuner.host.GuidedWriteCapabilityMaturityRegressionTest \
    se.anders.tunerstudio.aetuner.host.ProposalApplyCoordinatorRegressionTest \
    se.anders.tunerstudio.aetuner.host.ProposalApplyCoordinatorMultiCellRegressionTest \
    se.anders.tunerstudio.aetuner.host.ProposalApplyCoordinatorBitSelectionRegressionTest \
    se.anders.tunerstudio.aetuner.host.AeApplyRestoreValidationEngineRegressionTest \
    se.anders.tunerstudio.aetuner.host.AeApplyRestoreValidationLabModelRegressionTest \
    se.anders.tunerstudio.aetuner.host.AeApplyRestoreValidationAutomationRegressionTest \
    se.anders.tunerstudio.aetuner.host.AeValidationAutomaticAxisSafetyRegressionTest \
    se.anders.tunerstudio.aetuner.host.AeApplyRestoreValidationReportRegressionTest \
    se.anders.tunerstudio.aetuner.host.EngagementDetectionApplyWorkflowRegressionTest \
    se.anders.tunerstudio.aetuner.host.MapEstimateApplyResolutionRegressionTest \
    se.anders.tunerstudio.aetuner.host.GuidedBlendApplyWorkflowRegressionTest \
    se.anders.tunerstudio.aetuner.host.GuidedTaskSettingsPanelIntegrationRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedTaskSettingsPresentationRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedVehicleTestLimitsRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedVehicleTest9RegressionTest \
    se.anders.tunerstudio.aetuner.guided.PhaseA3LegacyInvariantMigrationTest \
    se.anders.tunerstudio.aetuner.guided.GuidedAttemptTraceRegressionTest \
    se.anders.tunerstudio.aetuner.guided.PedalPlateauDetectorRegressionTest \
    se.anders.tunerstudio.aetuner.guided.RoadBaselineTrackerRegressionTest \
    se.anders.tunerstudio.aetuner.guided.MapCatchupMeasurementRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedAttemptEvidenceRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedGearStatusRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedEventGearEvidenceRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedEventGearIntegrationRegressionTest \
    se.anders.tunerstudio.aetuner.guided.PedalOpeningDetectorRegressionTest \
    se.anders.tunerstudio.aetuner.guided.BlendDurationComparabilityGroupsRegressionTest \
    se.anders.tunerstudio.aetuner.guided.PhaseBGuidedArchitectureRegressionTest \
    se.anders.tunerstudio.aetuner.guided.BlendDurationGuidedSummaryRegressionTest \
    se.anders.tunerstudio.aetuner.guided.BlendDurationFocusCoachingRegressionTest \
    se.anders.tunerstudio.aetuner.guided.PhaseA2AdaptiveTypeMigrationTest \
    se.anders.tunerstudio.aetuner.guided.GuidedMethodProbeSessionRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedEvidenceReadinessRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedTpsAeMultiCellProposalRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedTaskSettingsDraftRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedProbeLongSessionRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedProbeExportThreadingRegressionTest \
    se.anders.tunerstudio.aetuner.guided.EngagementDetectionRoutingRegressionTest \
    se.anders.tunerstudio.aetuner.guided.EngagementDetectionSettingRoutingRegressionTest \
    se.anders.tunerstudio.aetuner.guided.EngagementPassiveCaptureRegressionTest \
    se.anders.tunerstudio.aetuner.guided.FoundationPassiveArchitectureRegressionTest \
    se.anders.tunerstudio.aetuner.guided.FoundationTpsQuietCalibrationRegressionTest \
    se.anders.tunerstudio.aetuner.guided.FoundationTpsNoiseFreezeRegressionTest \
    se.anders.tunerstudio.aetuner.guided.EngagementFocusCoachingRegressionTest \
    se.anders.tunerstudio.aetuner.guided.RuntimeResidueCleanupRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedTuningNavigationRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedV019CaptureLifecycleRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedV019InteractionHardeningRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedMapEstimateExperimentRoutingRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedFocusRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedFocusHubLifecycleRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedV019RetainedEvidenceRegressionTest \
    se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModulesRegressionTest \
    se.anders.tunerstudio.aetuner.guided.method.BaselineRecommendationCacheLifecycleRegressionTest \
    se.anders.tunerstudio.aetuner.guided.method.TransientEvidenceEventRegressionTest \
    se.anders.tunerstudio.aetuner.guided.method.WallWettingRecommendationRegressionTest \
    se.anders.tunerstudio.aetuner.guided.method.InstantFuelRecommendationRegressionTest \
    se.anders.tunerstudio.aetuner.guided.method.FoundationThresholdRecommendationRegressionTest \
    se.anders.tunerstudio.aetuner.guided.method.FoundationThresholdValidationRegressionTest \
    se.anders.tunerstudio.aetuner.guided.FoundationTimingValidationRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedProposalValidationSessionRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedMethodRoutingUiRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedLifecycleRegressionTest \
    se.anders.tunerstudio.aetuner.RuntimeOverhaulRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedBlendProposalRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedAudioCueRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedAudioCueLabRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedUiRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedProductionUiIntegrationRegressionTest \
    se.anders.tunerstudio.aetuner.VehicleTestIdentityRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedSampleDispatcherRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedSampleDispatcherHardeningRegressionTest \
    se.anders.tunerstudio.aetuner.guided.PhaseCSampleDispatchArchitectureTest \
    se.anders.tunerstudio.aetuner.passive.PhaseDPanelLayoutArchitectureTest \
    se.anders.tunerstudio.aetuner.passive.PhaseDAdvisoryActionsArchitectureTest \
    se.anders.tunerstudio.aetuner.passive.PhaseDOverviewControllerArchitectureTest \
    se.anders.tunerstudio.aetuner.ui.AeUtilityWorkspaceRegressionTest \
    se.anders.tunerstudio.aetuner.model.AeProjectSnapshotDecelSettingsRegressionTest \
    se.anders.tunerstudio.aetuner.model.MapPredictionMetricsRegressionTest \
    se.anders.tunerstudio.aetuner.model.TransientEventAnalyzerRegressionTest \
    se.anders.tunerstudio.aetuner.model.TransientEventAssessmentRegressionTest \
    se.anders.tunerstudio.aetuner.model.TransientEventFormatterRegressionTest \
    se.anders.tunerstudio.aetuner.model.PhaseETransientEventArchitectureTest \
    se.anders.tunerstudio.aetuner.PhaseFPackageArchitectureTest \
    se.anders.tunerstudio.aetuner.guided.GuidedEvidenceRecorderRegressionTest \
    se.anders.tunerstudio.aetuner.recovery.EvidenceRecoveryStoreRegressionTest \
    se.anders.tunerstudio.aetuner.recovery.EvidenceRecoveryThreadingRegressionTest \
    se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateDev16RegressionTest \
    se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateDev17RegressionTest \
    se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateDev18RegressionTest \
    se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateArchitectureMigrationRegressionTest \
    se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateGuidedControllerRegressionTest \
    se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateFocusLiveCellCacheRegressionTest \
    se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateProposalLimitSafetyRegressionTest \
    se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateFocusUiRegressionTest \
    se.anders.tunerstudio.aetuner.guided.GuidedChannelValidityRegressionTest
  do
    java -cp "target/classes:target/test-classes:lib/TunerStudioPluginAPI.jar" "$test_class"
  done

  java -Djava.awt.headless=true \
    -cp "target/classes:target/test-classes:lib/TunerStudioPluginAPI.jar" \
    se.anders.tunerstudio.aetuner.passive.LongSessionCharacterizationTest
fi

bash scripts/validation-tooling-regression.sh
bash scripts/package-dev-plugin.sh

echo "Full validation passed for AE Tuner (EPICEFI)"
