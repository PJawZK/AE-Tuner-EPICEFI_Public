package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModule;
import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModules;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;

/** UI/routing regression for passive Foundation 1 setting authority. */
public final class EngagementDetectionSettingRoutingRegressionTest {
    private EngagementDetectionSettingRoutingRegressionTest() { }

    public static void main(String[] args) {
        focusStartsReadOnlyWithoutManualDeltaControl();
        productionModuleHasNoDirectRoadWritePlan();
        contextExplainsInternalRecommendationBoundary();
        System.out.println("EngagementDetectionSettingRoutingRegressionTest passed");
    }

    private static void focusStartsReadOnlyWithoutManualDeltaControl() {
        EngagementDetectionWriteSelection.resetForTest();
        AeProjectSnapshot snapshot = snapshot(25.0);
        EngagementDetectionWriteSelection.observeWorkingTune(snapshot);
        EngagementDetectionGuidedFocusPanel focus = new EngagementDetectionGuidedFocusPanel();
        focus.updateModel(EngagementFocusModel.build(snapshot, null,
                GuidedCaptureState.IDLE, 0, 6, 0, 0));

        require(!focus.requestedDeltaWindowEnabledForTest(),
                "passive Foundation 1 exposed the retired manual Delta Window road control");
        require(!focus.settingsToggleVisibleForTest()
                        && !focus.settingsPanelVisibleForTest(),
                "passive Foundation 1 exposed retired sweep/settings UI");
        require(focus.currentTextForTest().contains("capture is read-only"),
                "Focus did not expose the passive read-only road boundary");
        require(!EngagementDetectionWriteSelection.snapshot().hasRequestedChange(),
                "opening passive Foundation 1 invented an ECU change");
    }

    private static void productionModuleHasNoDirectRoadWritePlan() {
        AeProjectSnapshot snapshot = snapshot(25.0);
        GuidedAeMethodModule module = GuidedAeMethodModules.forRecipe(
                GuidedTuningRecipe.ENGAGEMENT_DETECTION);
        module.currentTuneContext(snapshot);
        require(module.explicitSettingWritePlan(snapshot) == null,
                "passive Foundation 1 still exposes a direct setting plan before evidence");
        require(module.setupGuidance().contains("No temporary timing write occurs during capture")
                        && module.accumulationPlan().contains("No controller writes occur during capture")
                        && module.setupGuidance().contains("presentation-only")
                        && module.setupGuidance().contains("guarded Apply is withheld"),
                "production route lost the no-write/reference/provisional-operating-range contract");
    }

    private static void contextExplainsInternalRecommendationBoundary() {
        GuidedAeMethodModule module = GuidedAeMethodModules.forRecipe(
                GuidedTuningRecipe.ENGAGEMENT_DETECTION);
        AeProjectSnapshot snapshot = snapshot(25.0);
        String context = module.currentTuneContext(snapshot);
        String review = module.reviewOutputs();

        require(context.contains("Capture is read-only")
                        && context.contains("Delta Window is estimated after a comparable natural-movement set")
                        && context.contains("Sample Length remains history capacity")
                        && context.contains("representative pre-event operating RPM coverage confirms it")
                        && context.contains("No burn"),
                "working-tune context does not describe the passive Foundation 1 authority boundary");
        require(review.contains("PASSIVE TPS MOVEMENT ANALYSIS")
                        && review.contains("No controller settings were changed during capture"),
                "review output does not expose plugin-internal passive analysis");
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

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
