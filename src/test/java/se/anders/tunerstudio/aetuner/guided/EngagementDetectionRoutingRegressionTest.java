package se.anders.tunerstudio.aetuner.guided;

/**
 * UI-level guard for the upstream TPS Movement / Timing route.
 * The task remains independently selectable from TPS AE fuel while controller
 * detector choice/Sample Length/Fast Callback are read-only context and Delta
 * Window is the supported A/B timing setting.
 */
public final class EngagementDetectionRoutingRegressionTest {
    private EngagementDetectionRoutingRegressionTest() { }

    public static void main(String[] args) {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            panel.selectTuningTaskForTest(GuidedTuningRecipe.ENGAGEMENT_DETECTION);
            require(panel.selectedTuningAreaForTest().equals("AE Foundation")
                            && panel.selectedTuningTaskForTest().contains("TPS Movement / Timing"),
                    "TPS Movement / Timing is not independently selectable under AE Foundation");
            require(panel.startCaptureEnabledForTest(),
                    "TPS Movement / Timing evidence route cannot start");

            String setup = panel.checksTextForTest();
            require(setup.contains("Fuel: TPS AE change")
                            && setup.contains("AccelThreshold")
                            && setup.contains("Fuel: AE delta newest pair")
                            && setup.contains("Fuel: AE window")
                            && setup.contains("Fuel: AE delta stride"),
                    "TPS Movement / Timing UI omitted production movement/threshold/timing evidence");
            require(!setup.contains("Fuel: AE delta timed")
                            && !setup.contains("Fuel: AE delta span")
                            && !setup.contains("Fuel: AE delta from floor"),
                    "TPS Movement / Timing retained obsolete five-model comparison requirements");

            String proposal = panel.proposalTextForTest();
            require(proposal.contains("WORKING-TUNE CONTEXT")
                            && proposal.contains("catalogued settings"),
                    "TPS Movement / Timing did not expose working-tune parameter-family context");
            require(!panel.applyCurrentProposalEnabledForTest(),
                    "TPS Movement / Timing enabled Apply without a Delta Window change");
        } finally {
            panel.disposePanel();
        }
        System.out.println("EngagementDetectionRoutingRegressionTest passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
