package se.anders.tunerstudio.aetuner.guided;

/**
 * UI-level guard for the shared upstream detector route. The detector remains
 * independently selectable from TPS AE fuel while Foundation now exposes the
 * additional planned Threshold/Sensitivity and Validation tasks around it.
 */
public final class EngagementDetectionRoutingRegressionTest {
    private EngagementDetectionRoutingRegressionTest() { }

    public static void main(String[] args) {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            panel.selectTuningTaskForTest(GuidedTuningRecipe.ENGAGEMENT_DETECTION);
            require(panel.selectedTuningAreaForTest().equals("AE Foundation")
                            && panel.selectedTuningTaskForTest().contains("Detector Model / Timing"),
                    "Detector Model / Timing is not independently selectable under AE Foundation");
            require(panel.startCaptureEnabledForTest(),
                    "Detector Model / Timing evidence route cannot start");

            String setup = panel.checksTextForTest();
            require(setup.contains("Fuel: AE delta newest pair")
                            && setup.contains("Fuel: AE delta timed")
                            && setup.contains("Fuel: AE delta span")
                            && setup.contains("Fuel: AE delta from floor")
                            && setup.contains("Fuel: AE window")
                            && setup.contains("Fuel: AE delta stride"),
                    "Detector Model / Timing UI omitted simultaneous detector evidence");
            String proposal = panel.proposalTextForTest();
            require(proposal.contains("WORKING-TUNE CONTEXT")
                            && proposal.contains("catalogued settings")
                            && proposal.contains("guarded Apply"),
                    "Detector Model / Timing did not expose its AE parameter-family/common Apply context");
            require(!panel.applyCurrentProposalEnabledForTest(),
                    "Detector Model / Timing enabled Apply without a working-tune change");
            require(!proposal.contains("read-only")
                            && !proposal.contains("Write authority remains disabled"),
                    "Detector Model / Timing retained the obsolete read-only/write-authority gate");
        } finally {
            panel.disposePanel();
        }
        System.out.println("EngagementDetectionRoutingRegressionTest passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
