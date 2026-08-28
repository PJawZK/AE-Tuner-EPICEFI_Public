package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModule;
import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModules;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

/** UI/routing checks for the first explicit detector-setting proposal. */
public final class EngagementDetectionSettingRoutingRegressionTest {
    private EngagementDetectionSettingRoutingRegressionTest() { }

    public static void main(String[] args) {
        focusStartsAtWorkingTuneAndDoesNotInventChange();
        repeatedRefreshPreservesPendingChoiceButFreshReadResetsIt();
        explicitFocusSelectionCreatesDirectPlanWithoutCapture();
        System.out.println("EngagementDetectionSettingRoutingRegressionTest passed");
    }

    private static void focusStartsAtWorkingTuneAndDoesNotInventChange() {
        EngagementDetectionWriteSelection.resetForTest();
        AeProjectSnapshot snapshot = snapshot(25.0);
        EngagementDetectionWriteSelection.observeWorkingTune(snapshot);
        EngagementDetectionGuidedFocusPanel focus =
                new EngagementDetectionGuidedFocusPanel();

        require(focus.requestedDeltaWindowEnabledForTest(),
                "Delta Window proposal control did not enable after working-tune read");
        requireClose(25.0, focus.requestedDeltaWindowForTest(),
                "focus did not initialize at the exact working-tune Delta Window");
        require(focus.currentTextForTest().contains("Dual stride, newest")
                        && focus.currentTextForTest().contains("Delta Window 25 ms")
                        && focus.currentTextForTest().contains("no test change selected"),
                "focus did not expose detector baseline/no-test-change state");
        require(!EngagementDetectionWriteSelection.snapshot().hasRequestedChange(),
                "opening the setting surface invented an ECU change");
    }

    private static void repeatedRefreshPreservesPendingChoiceButFreshReadResetsIt() {
        EngagementDetectionWriteSelection.resetForTest();
        AeProjectSnapshot firstRead = snapshot(25.0);
        EngagementDetectionWriteSelection.observeWorkingTune(firstRead);
        EngagementDetectionWriteSelection.requestDeltaWindowMs(24.0);

        EngagementDetectionWriteSelection.observeWorkingTune(firstRead);
        require(EngagementDetectionWriteSelection.snapshot().hasRequestedChange(),
                "same-snapshot UI refresh erased the pending detector choice");
        requireClose(24.0,
                EngagementDetectionWriteSelection.snapshot().requestedDeltaWindowMs,
                "same-snapshot refresh changed the requested value");

        AeProjectSnapshot afterRestoreRead = snapshot(25.0);
        EngagementDetectionWriteSelection.observeWorkingTune(afterRestoreRead);
        require(!EngagementDetectionWriteSelection.snapshot().hasRequestedChange(),
                "fresh working-tune read retained a stale temporary request");
        requireClose(25.0,
                EngagementDetectionWriteSelection.snapshot().requestedDeltaWindowMs,
                "fresh working-tune read did not reset request to restored baseline");
    }

    private static void explicitFocusSelectionCreatesDirectPlanWithoutCapture() {
        EngagementDetectionWriteSelection.resetForTest();
        AeProjectSnapshot snapshot = snapshot(25.0);
        GuidedAeMethodModule module =
                GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.ENGAGEMENT_DETECTION);
        module.currentTuneContext(snapshot);

        EngagementDetectionGuidedFocusPanel focus =
                new EngagementDetectionGuidedFocusPanel();
        focus.setRequestedDeltaWindowForTest(24.0);
        require(EngagementDetectionWriteSelection.snapshot().hasRequestedChange(),
                "explicit focus selection was not retained as a pending setting change");

        ProposalWritePlan plan = module.explicitSettingWritePlan(snapshot);
        require(plan != null && plan.changeCount() == 1,
                "explicit detector setting did not produce a direct plan before capture");
        require(plan.reviewText().contains("Delta Window: 25 ms -> 24 ms"),
                "direct detector diff did not expose exact before/after values");

        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            panel.setProjectSnapshotForTest(snapshot);
            panel.selectTuningTaskForTest(GuidedTuningRecipe.ENGAGEMENT_DETECTION);
            focus.setRequestedDeltaWindowForTest(24.0);
            require(panel.workflowStageTextForTest().contains("REVIEW CHANGE")
                            && !panel.workflowStageTextForTest().contains("CAPTURE"),
                    "direct setting route still requires a capture stage before Apply");
            require(panel.proposalTextForTest().contains("DIRECT SETTING REVIEW")
                            && panel.proposalTextForTest().contains("No capture is required"),
                    "Guided review did not explain the direct setting route");
        } finally {
            panel.disposePanel();
        }
    }

    private static AeProjectSnapshot snapshot(double deltaWindowMs) {
        return new AeProjectSnapshot(
                "engagement-routing",
                new double[]{2.0}, new double[]{20.0}, new double[][]{{1.0}},
                new double[]{1000.0}, new double[]{1.5},
                1.0, 0.0,
                new double[0], new double[0],
                false, false, "none", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                "Dual stride, newest", deltaWindowMs, 0.050, true, 0.10);
    }

    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.000001) {
            throw new AssertionError(message + ": expected " + expected
                    + " but was " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
