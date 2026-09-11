package se.anders.tunerstudio.aetuner.guided;

import javax.swing.JComponent;
import javax.swing.JDialog;

/** Exact v0.19 task-view router; production models replace prototype preview data. */
final class GuidedV019FocusViews {
    private GuidedV019FocusViews() { }

    static JComponent create(GuidedProductionTask task, JDialog owner, Runnable onReviewReady) {
        if (task == GuidedProductionTask.TPS_MOVEMENT_TIMING)
            return new GuidedV019TpsTimingFocus(owner, onReviewReady);
        if (task == GuidedProductionTask.THRESHOLD_SENSITIVITY)
            return new GuidedV019ThresholdFocus(owner, onReviewReady);
        if (task == GuidedProductionTask.DECEL_DETECTION)
            return new GuidedV019DecelDetectionFocus(owner, onReviewReady);
        if (task == GuidedProductionTask.TPS_FUEL_RESPONSE)
            return new GuidedV019TpsAeFocus(owner, onReviewReady);
        if (task == GuidedProductionTask.MAP_ESTIMATE)
            return new GuidedV019MapEstimateFocus(owner, onReviewReady);
        if (task == GuidedProductionTask.BLEND_DURATION)
            return new GuidedV019BlendDurationFocus(owner, onReviewReady);
        if (task == GuidedProductionTask.WALL_FILM)
            return new GuidedV019WallWettingFocus(owner, onReviewReady);
        if (task == GuidedProductionTask.INSTANT_PULSE)
            return new GuidedV019InstantFuelFocus(owner, onReviewReady);

        if (task == GuidedProductionTask.TPS_SCALING
                || task == GuidedProductionTask.TPS_COMPLETION
                || task == GuidedProductionTask.TPS_VALIDATION
                || task == GuidedProductionTask.WALL_ADVANCED
                || task == GuidedProductionTask.WALL_VALIDATION
                || task == GuidedProductionTask.INSTANT_EVENT_STRENGTH
                || task == GuidedProductionTask.INSTANT_CONDITIONS
                || task == GuidedProductionTask.INSTANT_VALIDATION) {
            return new GuidedV019BoundEvidenceFocus(task, owner, onReviewReady);
        }
        return new GuidedV019UnsupportedFocus(task, owner, onReviewReady);
    }
}
