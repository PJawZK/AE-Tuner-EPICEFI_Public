package se.anders.tunerstudio.aetuner.guided;

/** Locks the v0.19 Focus -> existing production capture handoff used by Decel missing-region capture. */
public final class GuidedFocusHubIdleCaptureRegressionTest {
    private GuidedFocusHubIdleCaptureRegressionTest() { }

    public static void main(String[] args) {
        correctIdleRecipeStartsThroughExistingCaptureControl();
        wrongRecipeCannotStartCapture();
        nonIdleCaptureCannotBeRestarted();
        System.out.println("GuidedFocusHubIdleCaptureRegressionTest passed");
    }

    private static void correctIdleRecipeStartsThroughExistingCaptureControl() {
        FakeControl control = new FakeControl(GuidedTuningRecipe.DECEL_DETECTION, GuidedCaptureState.IDLE);
        GuidedFocusHub.setCaptureControl(control);
        GuidedCaptureState state = GuidedFocusHub.startIdleCapture(GuidedTuningRecipe.DECEL_DETECTION);
        require(state == GuidedCaptureState.CAPTURING && control.startCalls == 1,
                "matching idle Decel Focus did not delegate exactly once to existing production capture control");
        GuidedFocusHub.dispose();
    }

    private static void wrongRecipeCannotStartCapture() {
        FakeControl control = new FakeControl(GuidedTuningRecipe.MAP_ESTIMATE, GuidedCaptureState.IDLE);
        GuidedFocusHub.setCaptureControl(control);
        GuidedCaptureState state = GuidedFocusHub.startIdleCapture(GuidedTuningRecipe.DECEL_DETECTION);
        require(state == GuidedCaptureState.IDLE && control.startCalls == 0,
                "Decel Focus started a differently selected production recipe");
        GuidedFocusHub.dispose();
    }

    private static void nonIdleCaptureCannotBeRestarted() {
        FakeControl control = new FakeControl(GuidedTuningRecipe.DECEL_DETECTION, GuidedCaptureState.PAUSED);
        GuidedFocusHub.setCaptureControl(control);
        GuidedCaptureState state = GuidedFocusHub.startIdleCapture(GuidedTuningRecipe.DECEL_DETECTION);
        require(state == GuidedCaptureState.PAUSED && control.startCalls == 0,
                "Focus idle-start helper restarted a non-IDLE capture");
        GuidedFocusHub.dispose();
    }

    private static final class FakeControl implements GuidedFocusHub.CaptureControl {
        private final GuidedTuningRecipe recipe;
        private GuidedCaptureState state;
        int startCalls;

        FakeControl(GuidedTuningRecipe recipe, GuidedCaptureState state) {
            this.recipe = recipe;
            this.state = state;
        }

        @Override public GuidedCaptureState finishCapture() {
            state = GuidedCaptureState.COMPLETE;
            return state;
        }

        @Override public GuidedCaptureState togglePause() { return state; }

        @Override public GuidedCaptureState continueCapture() {
            startCalls++;
            state = GuidedCaptureState.CAPTURING;
            return state;
        }

        @Override public GuidedCaptureState captureState() { return state; }
        @Override public GuidedTuningRecipe activeRecipe() { return recipe; }
        @Override public boolean reviewReady() { return false; }
        @Override public boolean canContinueCapture() { return true; }
        @Override public boolean canExportEvidence() { return false; }
        @Override public void exportEvidence() { }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
