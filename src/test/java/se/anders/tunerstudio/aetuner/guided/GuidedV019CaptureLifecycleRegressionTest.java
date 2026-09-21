package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import javax.swing.JComponent;
import java.util.EnumMap;

/** Regression for the 2026-09-09 vehicle recording capture/navigation failures. */
public final class GuidedV019CaptureLifecycleRegressionTest {
    private GuidedV019CaptureLifecycleRegressionTest() { }

    public static void main(String[] args) {
        GuidedCapturePanel production = new GuidedCapturePanel();
        final int[] recoveryDirtyCalls = new int[]{0};
        production.setRecoveryDirtyAction(new Runnable() {
            @Override public void run() { recoveryDirtyCalls[0]++; }
        });
        try {
            GuidedV019ProductionBridge bridge = new GuidedV019ProductionBridge(production);

            bridge.select(GuidedProductionTask.DECEL_DETECTION);
            bridge.startCapture();
            require(bridge.captureState(GuidedProductionTask.DECEL_DETECTION)
                            == GuidedCaptureState.CAPTURING,
                    "Start Capture did not synchronously expose CAPTURING to the v0.19 bridge");

            GuidedTaskAvailability blocked = GuidedTaskAvailabilityAdapter.evaluate(
                    GuidedProductionTask.THRESHOLD_SENSITIVITY, null);
            require(!blocked.clickable && "CAPTURE ACTIVE".equals(blocked.status),
                    "another task remained selectable while a Guided capture was active");

            // Reproduce the recording's stale presentation-bus race: the real
            // production session is still CAPTURING even if FocusHub momentarily
            // reports another idle task.
            GuidedFocusHub.publish(GuidedTuningRecipe.MAP_ESTIMATE,
                    GuidedCaptureState.IDLE, null, "synthetic stale presentation state");
            require(bridge.captureState(GuidedProductionTask.DECEL_DETECTION)
                            == GuidedCaptureState.CAPTURING,
                    "v0.19 capture state still depends on the stale FocusHub presentation state");
            require(GuidedFocusHub.activeCaptureState() == GuidedCaptureState.CAPTURING
                            && GuidedFocusHub.activeCaptureRecipe() == GuidedTuningRecipe.DECEL_DETECTION,
                    "FocusHub did not defer active capture authority to the real production session");

            // A second workspace/bridge may temporarily replace the static Focus
            // control. Opening Focus must explicitly restore this workspace's
            // production control so Pause/Finish never stay greyed out.
            GuidedCapturePanel unrelatedProduction = new GuidedCapturePanel();
            GuidedV019ProductionBridge unrelatedBridge =
                    new GuidedV019ProductionBridge(unrelatedProduction);
            require(GuidedFocusHub.activeCaptureState() == GuidedCaptureState.IDLE,
                    "synthetic unrelated bridge did not replace the global Focus control");
            bridge.activateFocusControl();
            require(GuidedFocusHub.activeCaptureState() == GuidedCaptureState.CAPTURING
                            && GuidedFocusHub.activeCaptureRecipe() == GuidedTuningRecipe.DECEL_DETECTION,
                    "workspace Focus control could not rebind to the actual active production capture");
            unrelatedProduction.disposePanel();

            production.acceptProbeSampleForTest(sample());
            require(production.probeSampleCountForTest() == 1,
                    "synthetic probe evidence was not retained during active capture");

            JComponent focusComponent = GuidedV019FocusViews.create(
                    GuidedProductionTask.DECEL_DETECTION, null, null);
            require(focusComponent instanceof GuidedV019FocusBase,
                    "Decel Focus did not use the production Guided Focus lifecycle base");
            GuidedV019FocusBase focus = (GuidedV019FocusBase) focusComponent;
            require(focus.pauseCapture.isEnabled() && focus.finishCapture.isEnabled(),
                    "Guided Focus did not expose working Pause/Finish controls during capture");
            require(focus.exportEvidence.isEnabled() && !focus.review.isEnabled(),
                    "current-session export should be available with collected data while Review remains locked");

            focus.finishCapture.doClick();
            require(bridge.captureState(GuidedProductionTask.DECEL_DETECTION)
                            == GuidedCaptureState.COMPLETE,
                    "Guided Focus Finish Capture did not stop the production session");
            require(recoveryDirtyCalls[0] > 0,
                    "Guided Focus Finish Capture bypassed the real production Finish lifecycle/recovery hook");
            require(!bridge.evidenceReady(GuidedProductionTask.DECEL_DETECTION),
                    "one incomplete Decel sample was incorrectly accepted as review-ready evidence");
            require(!GuidedFocusHub.isActiveEvidenceReviewReady(),
                    "FocusHub promoted lifecycle COMPLETE to evidence-ready without qualification");
            require(focus.finishCapture.isEnabled()
                            && "Continue Capture".equals(focus.finishCapture.getText()),
                    "incomplete finished capture did not expose an in-place Continue Capture action");
            require(focus.exportEvidence.isEnabled() && !focus.review.isEnabled(),
                    "incomplete finished capture lost current-session export or exposed Review Results");

            focus.finishCapture.doClick();
            require(bridge.captureState(GuidedProductionTask.DECEL_DETECTION)
                            == GuidedCaptureState.CAPTURING,
                    "Continue Capture did not resume the existing production probe session");
            require(production.probeSampleCountForTest() == 1,
                    "Continue Capture discarded the retained incomplete evidence window");
            require("Finish Capture".equals(focus.finishCapture.getText())
                            && focus.finishCapture.isEnabled(),
                    "resumed Focus did not return the continuation control to Finish Capture");

            focus.finishCapture.doClick();
            require(bridge.captureState(GuidedProductionTask.DECEL_DETECTION)
                            == GuidedCaptureState.COMPLETE
                            && !bridge.evidenceReady(GuidedProductionTask.DECEL_DETECTION),
                    "second incomplete Finish changed the evidence-quality decision");

            bridge.select(GuidedProductionTask.THRESHOLD_SENSITIVITY);
            require(bridge.retainedProbeSessionCount() == 0,
                    "incomplete Decel evidence was incorrectly archived as a completed retained session");
            require(production.probeSampleCountForTest() == 0,
                    "incomplete previous probe session still blocked the next task after task switch");
            require(production.selectedTuningTaskForTest().contains("Threshold / Sensitivity"),
                    "task switch did not reach the next production task after discarding incomplete completion state");

            System.out.println("GuidedV019CaptureLifecycleRegressionTest passed");
        } finally {
            production.disposePanel();
            GuidedFocusHub.clear();
        }
    }

    private static LiveSample sample() {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, 2200.0);
        values.put(ChannelRole.TPS, 12.0);
        values.put(ChannelRole.DELTA_TPS, -3.0);
        values.put(ChannelRole.TPS_DECEL_ACTIVE, 1.0);
        values.put(ChannelRole.LAMBDA, 1.0);
        values.put(ChannelRole.TARGET_LAMBDA, 1.0);
        return new LiveSample(System.nanoTime(), 1.0, values, -30.0, -10.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
