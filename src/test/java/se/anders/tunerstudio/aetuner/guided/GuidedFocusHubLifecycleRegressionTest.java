package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateCellScope;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateCoverageStrategy;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateEvidenceBasis;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateGuidedFocusPanel;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateProposalLimitPolicy;

/** Ensures session reset and final classloader retirement have different ownership. */
public final class GuidedFocusHubLifecycleRegressionTest {
    private GuidedFocusHubLifecycleRegressionTest() { }

    public static void main(String[] args) {
        clearPreservesLiveInstanceCallbacks();
        everyInProgressLifecycleCanFinishAndOwnNavigation();
        completeLifecycleStillRequiresEvidenceReadiness();
        incompleteCompletionCanContinueWithoutBecomingReviewReady();
        disposeReleasesStaticCallbacks();
        System.out.println("GuidedFocusHubLifecycleRegressionTest passed");
    }

    private static void clearPreservesLiveInstanceCallbacks() {
        GuidedFocusHub.dispose();
        GuidedFocusHub.setCaptureControl(new FakeCaptureControl());
        GuidedFocusHub.setMapEstimateConfigurationListener(new FakeMapListener());
        require(GuidedFocusHub.hasCaptureControlForTest(),
                "fixture capture control was not installed");
        require(GuidedFocusHub.hasMapEstimateListenerForTest(),
                "fixture MAP listener was not installed");
        GuidedFocusHub.clear();
        require(GuidedFocusHub.hasCaptureControlForTest(),
                "normal Guided session reset destroyed live plugin capture control");
        require(GuidedFocusHub.hasMapEstimateListenerForTest(),
                "normal Guided session reset destroyed live MAP configuration listener");
    }

    private static void everyInProgressLifecycleCanFinishAndOwnNavigation() {
        FakeCaptureControl control = new FakeCaptureControl();
        control.recipe = GuidedTuningRecipe.BLEND_DURATION;
        GuidedFocusHub.setCaptureControl(control);

        for (GuidedCaptureState state : GuidedCaptureState.values()) {
            control.state = state;
            boolean inProgress = state.isCaptureInProgress();
            require(GuidedFocusHub.canFinishCapture() == inProgress,
                    "Finish authority disagrees with active-session lifecycle for " + state);

            GuidedTaskAvailability other = GuidedTaskAvailabilityAdapter.evaluate(
                    GuidedProductionTask.MAP_ESTIMATE, null);
            if (inProgress) {
                require(!other.clickable && "CAPTURE ACTIVE".equals(other.status),
                        "another task became selectable during live Blend state " + state);
            } else {
                require(!"CAPTURE ACTIVE".equals(other.status),
                        "navigation remained capture-locked after " + state);
            }
        }
    }

    private static void completeLifecycleStillRequiresEvidenceReadiness() {
        FakeCaptureControl control = new FakeCaptureControl();
        control.state = GuidedCaptureState.COMPLETE;
        control.exportCapable = true;
        control.continueCapable = true;
        GuidedFocusHub.setCaptureControl(control);

        require(!GuidedFocusHub.isActiveEvidenceReviewReady(),
                "COMPLETE lifecycle state alone incorrectly granted Review authority");
        require(!GuidedFocusHub.canExportActiveEvidence(),
                "COMPLETE lifecycle state alone incorrectly granted Export authority");
        GuidedFocusHub.exportActiveEvidence();
        require(control.exportCalls == 0,
                "incomplete evidence was exported through the FocusHub gate");

        control.ready = true;
        require(GuidedFocusHub.isActiveEvidenceReviewReady(),
                "review-ready completed evidence did not unlock Review authority");
        require(GuidedFocusHub.canExportActiveEvidence(),
                "review-ready completed evidence did not unlock Export authority");
        require(!GuidedFocusHub.canContinueActiveCapture(),
                "review-ready completed evidence still offered incomplete-capture continuation");
        GuidedFocusHub.exportActiveEvidence();
        require(control.exportCalls == 1,
                "review-ready evidence did not pass the FocusHub export gate exactly once");
    }

    private static void incompleteCompletionCanContinueWithoutBecomingReviewReady() {
        FakeCaptureControl control = new FakeCaptureControl();
        control.state = GuidedCaptureState.COMPLETE;
        control.ready = false;
        control.continueCapable = true;
        GuidedFocusHub.setCaptureControl(control);

        require(GuidedFocusHub.canContinueActiveCapture(),
                "incomplete completed evidence did not expose the existing production continuation path");
        require(GuidedFocusHub.continueActiveCapture() == GuidedCaptureState.CAPTURING,
                "continuing incomplete evidence did not return to CAPTURING");
        require(control.continueCalls == 1,
                "FocusHub did not invoke the production continuation exactly once");
        require(!GuidedFocusHub.isActiveEvidenceReviewReady(),
                "continued incomplete capture incorrectly became review-ready");
    }

    private static void disposeReleasesStaticCallbacks() {
        require(GuidedFocusHub.hasCaptureControlForTest(),
                "pre-dispose capture control missing");
        GuidedFocusHub.dispose();
        require(!GuidedFocusHub.hasCaptureControlForTest(),
                "final plugin retirement retained static capture control");
        require(!GuidedFocusHub.hasMapEstimateListenerForTest(),
                "final plugin retirement retained static MAP listener");
        require(GuidedFocusHub.snapshot().isIdle(),
                "final plugin retirement did not reset presentation state");
    }

    private static final class FakeCaptureControl
            implements GuidedFocusHub.CaptureControl {
        GuidedCaptureState state = GuidedCaptureState.IDLE;
        GuidedTuningRecipe recipe = GuidedTuningRecipe.TPS_AE;
        boolean ready;
        boolean continueCapable;
        boolean exportCapable;
        int continueCalls;
        int exportCalls;

        @Override public GuidedCaptureState finishCapture() {
            state = GuidedCaptureState.COMPLETE;
            return state;
        }
        @Override public GuidedCaptureState togglePause() {
            state = GuidedCaptureState.PAUSED;
            return state;
        }
        @Override public GuidedCaptureState continueCapture() {
            continueCalls++;
            state = GuidedCaptureState.CAPTURING;
            return state;
        }
        @Override public GuidedCaptureState captureState() {
            return state;
        }
        @Override public GuidedTuningRecipe activeRecipe() {
            return recipe;
        }
        @Override public boolean reviewReady() { return ready; }
        @Override public boolean canContinueCapture() { return continueCapable; }
        @Override public boolean canExportEvidence() { return exportCapable; }
        @Override public void exportEvidence() { exportCalls++; }
    }

    private static final class FakeMapListener
            implements MapEstimateGuidedFocusPanel.ConfigurationListener {
        @Override public void onStrategyRequested(MapEstimateCoverageStrategy strategy) { }
        @Override public void onScopeRequested(MapEstimateCellScope scope) { }
        @Override public void onEvidenceBasisRequested(MapEstimateEvidenceBasis basis) { }
        @Override public void onProposalLimitPolicyRequested(MapEstimateProposalLimitPolicy policy) { }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
