package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateCellScope;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateCoverageStrategy;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateEvidenceSession;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateFocusModel;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateGuidedFocusPanel;

import java.io.IOException;

/**
 * Presentation/configuration bus for the modeless Guided Focus window.
 * Capture/session code owns tuning state; this class owns no ECU-write authority.
 */
public final class GuidedFocusHub {
    public static final class State {
        public final GuidedTuningRecipe recipe;
        public final GuidedCaptureState captureState;
        public final MapEstimateFocusModel mapEstimate;
        public final EngagementFocusModel engagement;
        public final String guidance;

        private State(GuidedTuningRecipe recipe,
                      GuidedCaptureState captureState,
                      MapEstimateFocusModel mapEstimate,
                      EngagementFocusModel engagement,
                      String guidance) {
            this.recipe = recipe == null ? GuidedTuningRecipe.MAP_ESTIMATE : recipe;
            this.captureState = captureState == null ? GuidedCaptureState.IDLE : captureState;
            this.mapEstimate = mapEstimate;
            this.engagement = engagement;
            this.guidance = guidance == null ? "" : guidance;
        }

        public boolean isIdle() { return captureState == GuidedCaptureState.IDLE; }
        public void refresh(GuidedFocusWindow window) {
            if (window != null) {
                window.update(recipe, captureState, mapEstimate, engagement, guidance);
            }
        }
    }

    private static volatile State latest = new State(
            GuidedTuningRecipe.MAP_ESTIMATE, GuidedCaptureState.IDLE,
            null, null,
            "Read Working Tune and select MAP Estimate Table to initialize learned coverage.");
    private static volatile MapEstimateGuidedFocusPanel.ConfigurationListener mapEstimateListener;

    private GuidedFocusHub() { }

    public static State snapshot() { return latest; }

    public static void setMapEstimateConfigurationListener(
            MapEstimateGuidedFocusPanel.ConfigurationListener listener) {
        mapEstimateListener = listener;
    }

    static MapEstimateGuidedFocusPanel.ConfigurationListener mapEstimateConfigurationListener() {
        return mapEstimateListener;
    }

    public static void publish(GuidedTuningRecipe recipe,
                               GuidedCaptureState captureState,
                               MapEstimateFocusModel mapEstimate,
                               String guidance) {
        if (recipe == GuidedTuningRecipe.ENGAGEMENT_DETECTION) {
            latest = new State(recipe, captureState, null,
                    EngagementFocusModel.setupFromWorkingTune(captureState), guidance);
            return;
        }
        latest = new State(recipe, captureState, mapEstimate, null, guidance);
    }

    public static void publishEngagement(GuidedCaptureState captureState,
                                         EngagementFocusModel engagement,
                                         String guidance) {
        latest = new State(GuidedTuningRecipe.ENGAGEMENT_DETECTION,
                captureState, null, engagement, guidance);
    }

    /** Binary/source compatibility for the frozen dev15 shell while dev16 owns the real model. */
    public static void publish(GuidedTuningRecipe recipe,
                               GuidedCaptureState captureState,
                               MapEstimateFocusSnapshot legacySnapshot,
                               String guidance) {
        if (recipe == GuidedTuningRecipe.ENGAGEMENT_DETECTION) {
            latest = new State(recipe, captureState, null,
                    EngagementFocusModel.setupFromWorkingTune(captureState), guidance);
            return;
        }
        MapEstimateFocusModel model = legacySetupModel(legacySnapshot);
        latest = new State(recipe, captureState,
                model == null ? latest.mapEstimate : model,
                null, guidance);
    }

    public static void publishMapEstimateSetup(MapEstimateFocusModel mapEstimate, String guidance) {
        publish(GuidedTuningRecipe.MAP_ESTIMATE, GuidedCaptureState.IDLE, mapEstimate, guidance);
    }

    /**
     * Compatibility with the dev15 setup publication. Unlike the first dev16
     * bridge, this converts the legacy working-tune axes into a real dev16
     * empty learned-state model so Guided Focus is correctly sized before the
     * first capture begins.
     */
    public static void publishMapEstimateSetup(MapEstimateFocusSnapshot legacySnapshot,
                                               String guidance) {
        MapEstimateFocusModel model = legacySetupModel(legacySnapshot);
        latest = new State(GuidedTuningRecipe.MAP_ESTIMATE,
                GuidedCaptureState.IDLE,
                model == null ? latest.mapEstimate : model,
                null, guidance);
    }

    private static MapEstimateFocusModel legacySetupModel(MapEstimateFocusSnapshot legacy) {
        if (legacy == null || legacy.tpsBins.length == 0 || legacy.rpmBins.length == 0) {
            return null;
        }
        try {
            MapEstimateEvidenceSession empty = new MapEstimateEvidenceSession(
                    null, "guided-focus-setup", legacy.tpsBins, legacy.rpmBins);
            return MapEstimateFocusModel.build(
                    empty, null, legacy.minimumSamples, 115.0,
                    legacy.liveTps, legacy.liveRpm,
                    legacy.eligibility.getDisplayText(),
                    MapEstimateCoverageStrategy.INTERPOLATED_COVERAGE,
                    MapEstimateCellScope.all(legacy.tpsBins.length, legacy.rpmBins.length));
        } catch (IOException impossibleWithoutStore) {
            throw new IllegalStateException(
                    "Could not initialize in-memory MAP Estimate Focus axes",
                    impossibleWithoutStore);
        }
    }

    public static void clear() {
        latest = new State(GuidedTuningRecipe.MAP_ESTIMATE,
                GuidedCaptureState.IDLE, null, null,
                "No active Guided Focus session. Read Working Tune and select a Guided method.");
    }
}
