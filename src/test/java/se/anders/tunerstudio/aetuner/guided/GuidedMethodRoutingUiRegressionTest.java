package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.EnumMap;

public final class GuidedMethodRoutingUiRegressionTest {
    private GuidedMethodRoutingUiRegressionTest() { }

    public static void main(String[] args) {
        mapPredictIsAnActiveIndependentTask();
        everyAeBaseRoutesThroughTheSharedWorkflow();
        methodPlansExposeRequiredAndContextData();
        tableDraftMethodsRequireWorkingTuneBaseline();
        disabledTransientMethodsAreBlockedWithoutLosingBaselines();
        mapEstimateGetsDedicatedAccumulationInputs();
        blendDriverTargetsDoNotLeakIntoOtherMethods();
        methodSelectionUpdatesIdleStatus();
        probeCaptureCollectsEvidenceWithoutInventingProposal();
        tpsAeDraftFlowsThroughSharedReview();
        methodSelectorLocksOnlyDuringActiveCapture();
        methodSwitchGuardIsVisibleAndExportable();
        System.out.println("GuidedMethodRoutingUiRegressionTest passed");
    }

    private static void mapPredictIsAnActiveIndependentTask() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            panel.selectTuningTaskForTest(GuidedTuningRecipe.MAP_PREDICT);
            require(panel.selectedTuningAreaForTest().equals("MAP Predict"),
                    "Transient Validation is not routed under the MAP Predict area");
            require(panel.selectedTuningTaskForTest().contains("3. Transient Validation"),
                    "MAP Predict combined-behavior validation is not independently selectable");
            require(panel.startCaptureEnabledForTest(),
                    "Transient Validation evidence capture is not active");
            require(panel.headlineTextForTest().contains("Transient Validation"),
                    "shared Guided headline did not route to Transient Validation");
            require(panel.proposalTextForTest().contains("WORKING-TUNE CONTEXT"),
                    "Transient Validation UI did not expose the method/tune context");
        } finally {
            panel.disposePanel();
        }
    }

    private static void everyAeBaseRoutesThroughTheSharedWorkflow() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            GuidedTuningRecipe[] bases = new GuidedTuningRecipe[]{
                    GuidedTuningRecipe.MAP_PREDICT,
                    GuidedTuningRecipe.MAP_ESTIMATE,
                    GuidedTuningRecipe.WALL_WETTING,
                    GuidedTuningRecipe.TPS_AE,
                    GuidedTuningRecipe.INSTANT_FUEL
            };
            for (GuidedTuningRecipe recipe : bases) {
                panel.selectTuningTaskForTest(recipe);
                if (recipe != GuidedTuningRecipe.MAP_ESTIMATE
                        && recipe != GuidedTuningRecipe.TPS_AE) {
                    require(panel.startCaptureEnabledForTest(),
                            "active method evidence route lost Start Capture: " + recipe);
                }
                require(panel.workflowStageTextForTest().contains("CAPTURE")
                                && panel.workflowStageTextForTest().contains("REVIEW")
                                && panel.workflowStageTextForTest().contains("APPLY"),
                        "evidence method route did not expose capture/review/apply workflow: " + recipe);
                require(!panel.applyCurrentProposalEnabledForTest(),
                        "method with no reviewed changed value unexpectedly enabled Apply: " + recipe);
            }
        } finally {
            panel.disposePanel();
        }
    }

    private static void methodPlansExposeRequiredAndContextData() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            panel.selectTuningTaskForTest(GuidedTuningRecipe.TPS_AE);
            String setup = panel.checksTextForTest();
            require(setup.contains("HOW DATA IS ACCEPTED / GROUPED")
                            && setup.contains("REQUIRED CHANNELS (")
                            && setup.contains("CONTEXT / ATTRIBUTION CHANNELS (")
                            && setup.contains("REVIEW OUTPUTS")
                            && setup.contains("Fuel: TPS AE add fuel ms")
                            && setup.contains("Lambda")
                            && setup.contains("Target lambda"),
                    "TPS AE plan did not expose the data contract needed for useful accumulation");
            require(!setup.contains("\n  - "),
                    "setup data contract regressed to a tall one-channel-per-line list");
            require(panel.probeSetupTextForTest().contains("ACTION:")
                            && panel.probeSetupTextForTest().contains("TUNE:"),
                    "compact method setup did not expose operator action and working-tune context");
            require(panel.proposalTextForTest().contains("bounded TPS AE table generator")
                            || setup.contains("bounded TPS AE table generator"),
                    "TPS AE method contract does not disclose its reviewed table-draft path");
        } finally {
            panel.disposePanel();
        }
    }

    private static void tableDraftMethodsRequireWorkingTuneBaseline() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            panel.selectTuningTaskForTest(GuidedTuningRecipe.MAP_ESTIMATE);
            require(!panel.startCaptureEnabledForTest(),
                    "MAP Estimate could start without a working-table baseline");

            panel.selectTuningTaskForTest(GuidedTuningRecipe.TPS_AE);
            require(!panel.startCaptureEnabledForTest()
                            && panel.connectionTextForTest().contains("Read Working Tune"),
                    "TPS AE could start without the working TPS-to/cycle table baseline");

            panel.setProjectSnapshotForTest(tpsSnapshot());
  require(panel.startCaptureEnabledForTest(),
          "valid TPS AE working-table baseline did not enable Guided capture; status="
                  + panel.connectionTextForTest());
  require(panel.connectionTextForTest().contains("1. Fuel by Engine Cycle selected"),
          "TPS AE ready status did not identify the selected task; status="
                  + panel.connectionTextForTest());
  require(panel.connectionTextForTest().contains("ready"),
          "TPS AE ready status did not report readiness; status="
                  + panel.connectionTextForTest());
        } finally {
            panel.disposePanel();
        }
    }

    private static void disabledTransientMethodsAreBlockedWithoutLosingBaselines() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            panel.setProjectSnapshotForTest(readinessSnapshot(false, true, false, true));

            panel.selectTuningTaskForTest(GuidedTuningRecipe.TPS_AE);
            require(!panel.startCaptureEnabledForTest(),
                    "disabled TPS AE unexpectedly enabled Start Capture");
            require(panel.connectionTextForTest().contains("TPS Acceleration Enrichment is DISABLED")
                            && panel.connectionTextForTest().contains("blocked")
                            && !panel.connectionTextForTest().contains("Read Working Tune"),
                    "TPS AE disabled-state status did not distinguish activity readiness from its valid table baseline");
            require(panel.probeSetupTextForTest().contains("TPS Acceleration Enrichment: DISABLED"),
                    "TPS AE working-table context disappeared when the method was disabled");
            panel.startSelectedTaskForTest();
            require(panel.probeSampleCountForTest() == 0
                            && panel.connectionTextForTest().contains("capture blocked"),
                    "defensive TPS AE start path ignored the disabled working-tune state");

            panel.selectTuningTaskForTest(GuidedTuningRecipe.INSTANT_FUEL);
            require(!panel.startCaptureEnabledForTest()
                            && panel.connectionTextForTest().contains("Instant Fuel Pulse is DISABLED"),
                    "disabled Instant Fuel did not receive the same activity-readiness gate");
            panel.startSelectedTaskForTest();
            require(panel.probeSampleCountForTest() == 0
                            && panel.connectionTextForTest().contains("capture blocked"),
                    "defensive Instant Fuel start path ignored the disabled working-tune state");

            panel.selectTuningTaskForTest(GuidedTuningRecipe.WALL_WETTING);
            require(panel.startCaptureEnabledForTest(),
                    "enabled Wall Wetting was incorrectly blocked by the readiness gate");

            panel.selectTuningTaskForTest(GuidedTuningRecipe.MAP_PREDICT);
            require(panel.startCaptureEnabledForTest(),
                    "enabled MAP Predict was incorrectly blocked by the readiness gate");

            panel.selectTuningTaskForTest(GuidedTuningRecipe.MAP_ESTIMATE);
            panel.setProjectSnapshotForTest(readinessSnapshot(false, true, false, false));
            require(panel.startCaptureEnabledForTest(),
                    "MAP Estimate stable-table collection was incorrectly tied to MAP Predict enable state");
            require(!panel.applyCurrentProposalEnabledForTest(),
                    "readiness changes unexpectedly invented a changed write plan");
        } finally {
            panel.disposePanel();
        }
    }

    private static void mapEstimateGetsDedicatedAccumulationInputs() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            panel.selectTuningTaskForTest(GuidedTuningRecipe.MAP_ESTIMATE);
            require(panel.mapEstimateInputsVisibleForTest(),
                    "MAP Estimate did not expose samples-per-cell / MAP-cap inputs");
            require(!panel.probeEventInputsVisibleForTest(),
                    "generic transient event count leaked into steady MAP Estimate setup");
            panel.selectTuningTaskForTest(GuidedTuningRecipe.WALL_WETTING);
            require(panel.probeEventInputsVisibleForTest()
                            && !panel.mapEstimateInputsVisibleForTest(),
                    "transient method did not restore event-target inputs cleanly");
        } finally {
            panel.disposePanel();
        }
    }

    private static void blendDriverTargetsDoNotLeakIntoOtherMethods() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            panel.selectTuningTaskForTest(GuidedTuningRecipe.BLEND_DURATION);
            require(panel.liveDriverTargetsVisibleForTest(),
                    "Blend Duration driver targets disappeared");
            panel.selectTuningTaskForTest(GuidedTuningRecipe.WALL_WETTING);
            require(!panel.liveDriverTargetsVisibleForTest(),
                    "Blend-specific target gauges leaked into Wall Wetting UI");
        } finally {
            panel.disposePanel();
        }
    }

    private static void methodSelectionUpdatesIdleStatus() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            panel.selectTuningTaskForTest(GuidedTuningRecipe.WALL_WETTING);
            require(panel.connectionTextForTest().contains("1. Model / Base Tau-Beta")
                            && panel.connectionTextForTest().contains("selected")
                            && panel.connectionTextForTest().contains("ready"),
                    "Wall Wetting selection did not expose a current idle/ready status");
            panel.startSelectedTaskForTest();
            require(panel.connectionTextForTest().contains("1. Model / Base Tau-Beta capture started"),
                    "Wall Wetting start status was not exposed");
            panel.finishSelectedTaskForTest();
            panel.markProbeEvidenceExportedForTest();

            panel.selectTuningTaskForTest(GuidedTuningRecipe.TPS_AE);
            require(panel.connectionTextForTest().contains("1. Fuel by Engine Cycle selected")
                            && !panel.connectionTextForTest().contains("1. Model / Base Tau-Beta capture started"),
                    "TPS AE selection retained stale Wall Wetting capture status");
            require(panel.selectedTuningTaskForTest().contains("Guided table evidence available")
                            && !panel.selectedTuningTaskForTest().contains("capture active"),
                    "selector maturity text still reads like a running capture state");
        } finally {
            panel.disposePanel();
        }
    }

    private static void probeCaptureCollectsEvidenceWithoutInventingProposal() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            panel.selectTuningTaskForTest(GuidedTuningRecipe.MAP_PREDICT);
            panel.startSelectedTaskForTest();
            panel.acceptProbeSampleForTest(sample(1.0, false));
            panel.acceptProbeSampleForTest(sample(1.02, true));
            require(panel.probeSampleCountForTest() == 2,
                    "MAP Predict UI route did not feed its evidence session");
            require(panel.probeActivityEventCountForTest() == 1,
                    "MAP Predict UI did not expose distinct event accumulation");
            require(panel.proposalTextForTest().contains("CURRENT CAPTURE METRICS")
                            && panel.proposalTextForTest().contains("Peak smoothedDeltaTps / AccelThreshold"),
                    "MAP Predict review did not expose useful accumulated metrics");
            require(!panel.applyCurrentProposalEnabledForTest(),
                    "capture evidence alone must not invent a changed ProposalWritePlan");
        } finally {
            panel.disposePanel();
        }
    }

    private static void tpsAeDraftFlowsThroughSharedReview() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            panel.selectTuningTaskForTest(GuidedTuningRecipe.TPS_AE);
            panel.setProjectSnapshotForTest(tpsSnapshot());
            panel.startSelectedTaskForTest();
            addTpsAeEvent(panel, 10.00);
            addTpsAeEvent(panel, 11.40);
            addTpsAeEvent(panel, 12.80);
            panel.finishSelectedTaskForTest();

            require(panel.proposalTextForTest().contains("TPS AE TABLE REVIEW")
                            && panel.proposalTextForTest().contains("Basis: 3 usable TPS AE fuel-proved event(s)"),
                    "shared Guided review did not surface the TPS AE table generator result");
            require(panel.copyReviewedDraftEnabledForTest(),
                    "reviewed TPS AE table did not enable Copy Reviewed Draft");
            require(!panel.applyCurrentProposalEnabledForTest(),
                    "TPS AE copy/paste draft should not enable Apply until its module returns an explicit ProposalWritePlan");
        } finally {
            panel.disposePanel();
        }
    }

    private static void methodSelectorLocksOnlyDuringActiveCapture() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            panel.selectTuningTaskForTest(GuidedTuningRecipe.MAP_PREDICT);
            panel.startSelectedTaskForTest();
            require(!panel.tuningTaskEnabledForTest(),
                    "method selector remained editable during an active capture");
            panel.acceptProbeSampleForTest(sample(2.0, true));
            panel.finishSelectedTaskForTest();
            require(panel.tuningTaskEnabledForTest(),
                    "method selector remained locked after review/finish");
            require(panel.startCaptureTextForTest().contains("Continue"),
                    "same-method review did not expose clean continuation of accumulation");
            panel.selectTuningTaskForTest(GuidedTuningRecipe.WALL_WETTING);
            require(panel.selectedTuningTaskForTest().contains("1. Model / Base Tau-Beta"),
                    "completed method could not be browsed/switched without global reset");
        } finally {
            panel.disposePanel();
        }
    }

    private static void methodSwitchGuardIsVisibleAndExportable() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            panel.selectTuningTaskForTest(GuidedTuningRecipe.MAP_PREDICT);
            panel.startSelectedTaskForTest();
            panel.acceptProbeSampleForTest(sample(3.0, true));
            panel.finishSelectedTaskForTest();

            panel.selectTuningTaskForTest(GuidedTuningRecipe.WALL_WETTING);
            require(!panel.startCaptureEnabledForTest(),
                    "different method could start while prior unexported evidence was retained");
            require(panel.selectedTuningTaskForTest().contains("1. Model / Base Tau-Beta")
                  && panel.connectionTextForTest().contains("1. Model / Base Tau-Beta")
                  && panel.connectionTextForTest().contains("3. Transient Validation")
                  && panel.connectionTextForTest().contains("export retained"),
          "method switch guard did not preserve immediate navigation plus retained-evidence blocking");
  require(panel.saveReportEnabledForTest(),
          "retained prior evidence was not directly exportable while browsing the next method");

            panel.markProbeEvidenceExportedForTest();
            require(panel.startCaptureEnabledForTest(),
                    "exported prior evidence still blocked the next method");
            panel.startSelectedTaskForTest();
            require(panel.probeSampleCountForTest() == 0,
                    "starting the next method retained already-exported samples from the prior method");
        } finally {
            panel.disposePanel();
        }
    }

    private static AeProjectSnapshot readinessSnapshot(boolean tpsAeEnabled,
                                                       boolean wallWettingEnabled,
                                                       boolean instantFuelEnabled,
                                                       boolean mapPredictEnabled) {
        return new AeProjectSnapshot(
                "readiness-ui-test",
                new double[]{0.0, 2.0, 4.0, 6.0, 10.0, 12.0, 14.0, 16.0},
                new double[]{0.0, 14.0, 29.0, 43.0, 57.0, 71.0, 86.0, 100.0},
                new double[][]{
                        {1.0, 1.0, 0.9, 0.8, 0.6, 0.5, 0.4, 0.3},
                        {1.0, 1.0, 0.9, 0.8, 0.6, 0.5, 0.4, 0.3},
                        {1.0, 1.0, 0.9, 0.8, 0.6, 0.5, 0.4, 0.3},
                        {1.0, 1.0, 0.9, 0.8, 0.6, 0.5, 0.4, 0.3},
                        {1.0, 1.0, 0.9, 0.8, 0.6, 0.5, 0.4, 0.3},
                        {1.0, 1.0, 0.9, 0.8, 0.6, 0.5, 0.4, 0.3},
                        {1.0, 1.0, 0.9, 0.8, 0.6, 0.5, 0.4, 0.3},
                        {1.0, 1.0, 0.9, 0.8, 0.6, 0.5, 0.4, 0.3}
                },
                new double[]{1000.0, 4000.0}, new double[]{1.0, 1.5},
                0.0, 0.0, new double[0], new double[0],
                tpsAeEnabled, wallWettingEnabled, "tau-beta", instantFuelEnabled,
                mapPredictEnabled, false, false,
                new double[0][0], new double[0][0],
                new double[]{1000.0, 3000.0}, new double[]{10.0, 30.0},
                new double[][]{{45.0, 55.0}, {50.0, 60.0}},
                new double[]{1500.0, 2600.0, 3800.0, 5000.0},
                new double[]{0.08, 0.26, 0.24, 0.18});
    }

    private static AeProjectSnapshot tpsSnapshot() {
        return new AeProjectSnapshot(
                "tps-ui-test",
                new double[]{2.0, 4.0, 6.0, 10.0, 12.0},
                new double[]{10.0, 20.0, 30.0},
                new double[][]{
                        {1.00, 1.00, 0.90, 0.70, 0.50},
                        {1.00, 1.00, 0.90, 0.70, 0.50},
                        {1.00, 1.00, 0.90, 0.70, 0.50}
                },
                new double[]{1000.0, 4000.0}, new double[]{1.0, 1.5},
                0.0, 0.0, new double[0], new double[0],
                true, false, "none", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0]);
    }

    private static void addTpsAeEvent(GuidedCapturePanel panel, double baseSeconds) {
        panel.acceptProbeSampleForTest(tpsAeSample(baseSeconds, 8.0, 0.0, 1.00, false));
        panel.acceptProbeSampleForTest(tpsAeSample(baseSeconds + 0.05, 14.0, 2.0, 1.18, true));
        panel.acceptProbeSampleForTest(tpsAeSample(baseSeconds + 0.10, 18.0, 4.0, 1.16, true));
        panel.acceptProbeSampleForTest(tpsAeSample(baseSeconds + 0.20, 20.0, 6.0, 1.02, true));
        panel.acceptProbeSampleForTest(tpsAeSample(baseSeconds + 0.30, 20.0, 10.0, 1.02, true));
        panel.acceptProbeSampleForTest(tpsAeSample(baseSeconds + 0.45, 20.0, 12.0, 1.01, false));
        panel.acceptProbeSampleForTest(tpsAeSample(baseSeconds + 0.70, 20.0, 12.0, 1.01, false));
        panel.acceptProbeSampleForTest(tpsAeSample(baseSeconds + 0.90, 20.0, 12.0, 1.01, false));
    }

    private static LiveSample sample(double seconds, boolean active) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, 2200.0);
        values.put(ChannelRole.TPS, active ? 25.0 : 8.0);
        values.put(ChannelRole.MAP, active ? 60.0 : 50.0);
        values.put(ChannelRole.FALLBACK_MAP, active ? 82.0 : 50.0);
        values.put(ChannelRole.EFFECTIVE_MAP, active ? 78.0 : 50.0);
        values.put(ChannelRole.MAP_PRED_ACTIVE, active ? 1.0 : 0.0);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, active ? 2.0 : 0.0);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.5);
        values.put(ChannelRole.MAP_PRED_RESET_CNT, 1.0);
        values.put(ChannelRole.MAP_PRED_EVENT_OVER, 0.0);
        return new LiveSample(Math.round(seconds * 1000000000.0), seconds, values, 0.0, 0.0);
    }

    private static LiveSample tpsAeSample(double seconds, double tps,
                                          double cycle, double lambda,
                                          boolean active) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, 2500.0);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.MAP, active ? 75.0 : 50.0);
        values.put(ChannelRole.LAMBDA, lambda);
        values.put(ChannelRole.TARGET_LAMBDA, 1.0);
        values.put(ChannelRole.PW, active ? 4.5 : 3.0);
        values.put(ChannelRole.TPS_FROM, 8.0);
        values.put(ChannelRole.TPS_TO, 20.0);
        values.put(ChannelRole.DELTA_TPS, active ? 12.0 : 0.0);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, active ? 2.0 : 0.0);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.5);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, active ? 1.0 : 0.0);
        values.put(ChannelRole.AE_ADD_MS, active ? 0.80 : 0.0);
        values.put(ChannelRole.EXTRA_FUEL, 0.0);
        values.put(ChannelRole.TPS_AE_CYCLE_MULT, active ? 1.0 : 0.0);
        values.put(ChannelRole.TPS_AE_CYCLE_CNT, cycle);
        values.put(ChannelRole.WALL_WETTING_PW, 0.0);
        values.put(ChannelRole.INSTANT_PULSE_PW, 0.0);
        values.put(ChannelRole.MAP_PRED_ACTIVE, 0.0);
        values.put(ChannelRole.FALLBACK_MAP, active ? 75.0 : 50.0);
        values.put(ChannelRole.DFCO, 0.0);
        values.put(ChannelRole.FUEL_CUT, 0.0);
        values.put(ChannelRole.COOLANT, 80.0);
        values.put(ChannelRole.IAT, 25.0);
        return new LiveSample(Math.round(seconds * 1000000000.0), seconds,
                values, active ? 80.0 : 0.0, active ? 100.0 : 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
