package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModule;
import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModules;
import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.EngagementModelOption;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

/** UI/lifecycle contract for direct Engagement Model selection. */
public final class EngagementModelSettingRoutingRegressionTest {
    private EngagementModelSettingRoutingRegressionTest() { }

    public static void main(String[] args) {
        focusStartsAtDualStrideWithoutInventingChange();
        sameSnapshotPreservesTemporaryModelAndFreshReadClearsIt();
        modelSelectionCreatesDirectPlanWithoutCapture();
        System.out.println("EngagementModelSettingRoutingRegressionTest passed");
    }

    private static void focusStartsAtDualStrideWithoutInventingChange() {
        EngagementDetectionWriteSelection.resetForTest();
        AeProjectSnapshot snapshot = snapshot();
        EngagementDetectionWriteSelection.observeWorkingTune(snapshot);
        EngagementDetectionGuidedFocusPanel focus = new EngagementDetectionGuidedFocusPanel();

        require(focus.requestedEngagementModelEnabledForTest(),
                "Engagement Model control did not enable after working-tune read");
        require(focus.requestedEngagementModelForTest()
                        == EngagementModelOption.DUAL_STRIDE_NEWEST,
                "model control did not initialize at Dual stride working tune");
        require(!EngagementDetectionWriteSelection.snapshot().hasRequestedChange(),
                "opening model control invented a setting change");
    }

    private static void sameSnapshotPreservesTemporaryModelAndFreshReadClearsIt() {
        EngagementDetectionWriteSelection.resetForTest();
        AeProjectSnapshot firstRead = snapshot();
        EngagementDetectionWriteSelection.observeWorkingTune(firstRead);
        EngagementDetectionWriteSelection.requestEngagementModel(
                EngagementModelOption.MAX_STEP_TIMED);

        EngagementDetectionWriteSelection.observeWorkingTune(firstRead);
        require(EngagementDetectionWriteSelection.snapshot().hasRequestedModelChange(),
                "same-snapshot refresh erased temporary Timed selection");
        require(EngagementDetectionWriteSelection.snapshot().requestedEngagementModel
                        == EngagementModelOption.MAX_STEP_TIMED,
                "same-snapshot refresh changed temporary model");

        AeProjectSnapshot afterRestoreRead = snapshot();
        EngagementDetectionWriteSelection.observeWorkingTune(afterRestoreRead);
        require(!EngagementDetectionWriteSelection.snapshot().hasRequestedChange(),
                "fresh Read Working Tune retained stale temporary model");
        require(EngagementDetectionWriteSelection.snapshot().requestedEngagementModel
                        == EngagementModelOption.DUAL_STRIDE_NEWEST,
                "fresh working-tune read did not restore UI request to Dual stride");
    }

    private static void modelSelectionCreatesDirectPlanWithoutCapture() {
        EngagementDetectionWriteSelection.resetForTest();
        AeProjectSnapshot snapshot = snapshot();
        GuidedAeMethodModule module = GuidedAeMethodModules.forRecipe(
                GuidedTuningRecipe.ENGAGEMENT_DETECTION);
        module.currentTuneContext(snapshot);

        EngagementDetectionGuidedFocusPanel focus = new EngagementDetectionGuidedFocusPanel();
        focus.setRequestedEngagementModelForTest(EngagementModelOption.MAX_STEP_TIMED);
        EngagementDetectionWriteSelection.Snapshot selected =
                EngagementDetectionWriteSelection.snapshot();
        require(selected.hasRequestedModelChange()
                        && !selected.hasRequestedDeltaWindowChange(),
                "model selection did not remain an isolated direct setting change");

        ProposalWritePlan plan = module.explicitSettingWritePlan(snapshot);
        require(plan != null && plan.changeCount() == 1,
                "model selection did not create one direct plan before capture");
        ProposalWritePlan.Change change = plan.getChanges().get(0);
        require(AeParameterNames.TPS_AE_DETECT_MODE.equals(change.parameterName),
                "model direct plan targeted wrong setting");
        require(plan.reviewText().contains("Engagement Model: 4 -> 1"),
                "model direct review omitted exact code transition");
        require(plan.getContext().contains("Dual stride, newest")
                        && plan.getContext().contains("Max step, timed"),
                "model direct review omitted enum meanings");

        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            panel.setProjectSnapshotForTest(snapshot);
            panel.selectTuningTaskForTest(GuidedTuningRecipe.ENGAGEMENT_DETECTION);
            focus.setRequestedEngagementModelForTest(EngagementModelOption.MAX_STEP_TIMED);
            require(panel.workflowStageTextForTest().contains("REVIEW CHANGE")
                            && !panel.workflowStageTextForTest().contains("CAPTURE"),
                    "Engagement Model direct route incorrectly requires capture");
            require(panel.proposalTextForTest().contains("Engagement Model: 4 -> 1")
                            && panel.proposalTextForTest().contains("No capture is required"),
                    "Guided direct review did not expose model/no-capture contract");
        } finally {
            panel.disposePanel();
        }
    }

    private static AeProjectSnapshot snapshot() {
        return new AeProjectSnapshot(
                "engagement-model-routing",
                new double[]{2.0}, new double[]{20.0}, new double[][]{{1.0}},
                new double[]{1000.0}, new double[]{1.5},
                1.0, 0.0,
                new double[0], new double[0],
                false, false, "none", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                "Dual stride, newest", 25.0, 0.050, true, 0.10);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
