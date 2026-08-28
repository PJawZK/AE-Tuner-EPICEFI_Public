package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModules;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

/** Direct-setting route for Sample Length + Fast Callback; no capture dependency. */
public final class EngagementTimingSettingRoutingRegressionTest {
    private EngagementTimingSettingRoutingRegressionTest() { }

    public static void main(String[] args) {
        sampleLengthAndFastCallbackCreateExactDirectPlan();
        guidedFocusKeepsAllFourControlsSecondary();
        freshReadClearsTemporaryTimingSelections();
        System.out.println("EngagementTimingSettingRoutingRegressionTest passed");
    }

    private static void sampleLengthAndFastCallbackCreateExactDirectPlan() {
        EngagementDetectionWriteSelection.resetForTest();
        AeProjectSnapshot snapshot = snapshot(0.050, true);
        EngagementDetectionWriteSelection.observeWorkingTune(snapshot);
        EngagementDetectionWriteSelection.requestSampleLengthSeconds(0.055);
        EngagementDetectionWriteSelection.requestFastCallback(false);

        ProposalWritePlan plan = GuidedAeMethodModules.forRecipe(
                GuidedTuningRecipe.ENGAGEMENT_DETECTION)
                .explicitSettingWritePlan(snapshot);
        require(plan != null && plan.changeCount() == 2,
                "Sample Length + Fast Callback should create exactly two direct changes");
        require(AeParameterNamesForTest.contains(plan, "tpsAccelLookback", 0.050, 0.055),
                "direct plan omitted exact Sample Length scalar change");
        require(AeParameterNamesForTest.contains(plan, "tpsAeFastCallback", 1.0, 0.0),
                "direct plan omitted exact Fast Callback logical bit change");
        require(plan.getContext().contains("Sample Length 0.05 -> 0.055 s")
                        && plan.getContext().contains("Fast Callback ON -> OFF")
                        && plan.getContext().contains("no burn"),
                "timing direct plan lost review context");
    }

    private static void guidedFocusKeepsAllFourControlsSecondary() {
        EngagementDetectionWriteSelection.resetForTest();
        EngagementDetectionWriteSelection.observeWorkingTune(snapshot(0.050, true));
        EngagementDetectionGuidedFocusPanel panel = new EngagementDetectionGuidedFocusPanel();
        panel.updateModel(EngagementFocusModel.setupFromWorkingTune(GuidedCaptureState.IDLE));
        require(panel.requestedEngagementModelEnabledForTest(),
                "Engagement Model control is not available after working-tune read");
        require(panel.requestedDeltaWindowEnabledForTest(),
                "Delta Window control is not available after working-tune read");
        require(panel.requestedSampleLengthEnabledForTest(),
                "Sample Length control is not available after working-tune read");
        require(panel.requestedFastCallbackEnabledForTest(),
                "Fast Callback control is not available after working-tune read");
        requireClose(0.050, panel.requestedSampleLengthForTest(), "Sample Length baseline");
        require(panel.requestedFastCallbackForTest(),
                "Fast Callback baseline should reflect working-tune ON state");

        panel.setDriverView(true);
        require(!panel.settingsToggleVisibleForTest()
                        && !panel.settingsPanelVisibleForTest(),
                "Driver view still promotes Detector Model / Timing setting controls");
        require(panel.guidanceTextForTest().contains("START A BASELINE CAPTURE")
                        && panel.guidanceTextForTest().contains("BASELINE / A-B REPEAT SET")
                        && panel.guidanceTextForTest().contains("change ONE setting")
                        && panel.guidanceTextForTest().contains("recorded channels remain the evidence"),
                "Guided Focus lost the evidence-first A/B coaching contract");

        panel.setDriverView(false);
        require(panel.settingsToggleVisibleForTest()
                        && !panel.settingsPanelVisibleForTest(),
                "all four controls should remain available only through a collapsed secondary experiment surface");
    }

    private static void freshReadClearsTemporaryTimingSelections() {
        EngagementDetectionWriteSelection.resetForTest();
        AeProjectSnapshot first = snapshot(0.050, true);
        EngagementDetectionWriteSelection.observeWorkingTune(first);
        EngagementDetectionWriteSelection.requestSampleLengthSeconds(0.055);
        EngagementDetectionWriteSelection.requestFastCallback(false);
        EngagementDetectionWriteSelection.Snapshot pending =
                EngagementDetectionWriteSelection.snapshot();
        require(pending.hasRequestedSampleLengthChange()
                        && pending.hasRequestedFastCallbackChange(),
                "temporary timing choices were not retained on same snapshot");

        // Same object refresh must preserve operator choices.
        EngagementDetectionWriteSelection.observeWorkingTune(first);
        pending = EngagementDetectionWriteSelection.snapshot();
        require(pending.hasRequestedSampleLengthChange()
                        && pending.hasRequestedFastCallbackChange(),
                "same-snapshot UI refresh cleared pending timing choices");

        // A new snapshot represents a real Read Working Tune and resets choices.
        EngagementDetectionWriteSelection.observeWorkingTune(snapshot(0.050, true));
        EngagementDetectionWriteSelection.Snapshot clean =
                EngagementDetectionWriteSelection.snapshot();
        require(!clean.hasRequestedSampleLengthChange()
                        && !clean.hasRequestedFastCallbackChange(),
                "fresh working-tune read did not clear temporary timing choices");
    }

    private static AeProjectSnapshot snapshot(double sampleLength, boolean fastCallback) {
        return new AeProjectSnapshot(
                "cfg",
                new double[]{2.0}, new double[]{20.0}, new double[][]{{1.0}},
                new double[]{1000.0}, new double[]{1.5},
                1.0, 0.0,
                new double[0], new double[0],
                false, false, "none", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                "Dual stride, newest", 25.0, sampleLength, fastCallback, 0.10);
    }

    private static final class AeParameterNamesForTest {
        static boolean contains(ProposalWritePlan plan, String parameter,
                                double expected, double proposed) {
            for (ProposalWritePlan.Change change : plan.getChanges()) {
                if (parameter.equals(change.parameterName)
                        && Math.abs(change.expectedValue - expected) <= 0.000001
                        && Math.abs(change.proposedValue - proposed) <= 0.000001) {
                    return true;
                }
            }
            return false;
        }
    }

    private static void requireClose(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > 0.000001) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
