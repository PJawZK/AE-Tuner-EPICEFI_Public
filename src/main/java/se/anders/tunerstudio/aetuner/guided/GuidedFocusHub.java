package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateCellScope;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateCoverageStrategy;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateEvidenceBasis;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateEvidenceSession;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateFocusModel;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateGuidedFocusPanel;
import se.anders.tunerstudio.aetuner.guided.mapestimate.MapEstimateProposalLimitPolicy;
import se.anders.tunerstudio.aetuner.guided.method.FoundationThresholdFocusBridge;
import se.anders.tunerstudio.aetuner.guided.method.FoundationThresholdFocusModel;

import java.io.IOException;

/** Presentation/configuration bus for the modeless Guided Focus window. */
public final class GuidedFocusHub {
    public static final class State {
        public final GuidedTuningRecipe recipe;
        public final GuidedCaptureState captureState;
        public final MapEstimateFocusModel mapEstimate;
        public final EngagementFocusModel engagement;
        public final FoundationThresholdFocusModel foundationThreshold;
        public final BlendDurationFocusModel blendDuration;
        public final String guidance;

        private State(GuidedTuningRecipe recipe, GuidedCaptureState captureState,
                      MapEstimateFocusModel mapEstimate, EngagementFocusModel engagement,
                      FoundationThresholdFocusModel foundationThreshold,
                      BlendDurationFocusModel blendDuration, String guidance) {
            this.recipe = recipe == null ? GuidedTuningRecipe.MAP_ESTIMATE : recipe;
            this.captureState = captureState == null ? GuidedCaptureState.IDLE : captureState;
            this.mapEstimate = mapEstimate;
            this.engagement = engagement;
            this.foundationThreshold = foundationThreshold;
            this.blendDuration = blendDuration;
            this.guidance = guidance == null ? "" : guidance;
        }

        public boolean isIdle() { return captureState == GuidedCaptureState.IDLE; }
        public void refresh(GuidedFocusWindow window) {
            if (window != null) {
                window.update(recipe, captureState, mapEstimate, engagement,
                        foundationThreshold, blendDuration, guidance);
            }
        }
    }

    private static volatile State latest = new State(
            GuidedTuningRecipe.MAP_ESTIMATE, GuidedCaptureState.IDLE,
            null, null, null, null,
            "Read Working Tune and select MAP Estimate Table to initialize learned coverage.");
    private static volatile MapEstimateGuidedFocusPanel.ConfigurationListener mapEstimateListener;

    private GuidedFocusHub() { }

    public static State snapshot() { return latest; }

    public static void setMapEstimateConfigurationListener(MapEstimateGuidedFocusPanel.ConfigurationListener listener) {
        mapEstimateListener = listener;
    }
    static MapEstimateGuidedFocusPanel.ConfigurationListener mapEstimateConfigurationListener() { return mapEstimateListener; }

    public static void publish(GuidedTuningRecipe recipe, GuidedCaptureState captureState,
                               MapEstimateFocusModel mapEstimate, String guidance) {
        if (recipe == GuidedTuningRecipe.ENGAGEMENT_DETECTION) {
            latest = new State(recipe, captureState, null,
                    EngagementFocusModel.setupFromWorkingTune(captureState), null, null, guidance);
            return;
        }
        if (recipe == GuidedTuningRecipe.FOUNDATION_THRESHOLD) {
            latest = new State(recipe, captureState, null, null,
                    FoundationThresholdFocusBridge.snapshot(captureState), null, guidance);
            return;
        }
        if (recipe == GuidedTuningRecipe.BLEND_DURATION) {
            BlendDurationFocusModel model = latest.blendDuration == null
                    ? BlendDurationFocusModel.setup() : latest.blendDuration;
            latest = new State(recipe, captureState, null, null, null, model, guidance);
            return;
        }
        latest = new State(recipe, captureState, mapEstimate, null, null, null, guidance);
    }

    public static void publishEngagement(GuidedCaptureState captureState,
                                         EngagementFocusModel engagement, String guidance) {
        latest = new State(GuidedTuningRecipe.ENGAGEMENT_DETECTION,
                captureState, null, engagement, null, null, guidance);
    }

    public static void publishBlendDuration(GuidedCaptureState captureState,
                                            BlendDurationFocusModel blendDuration,
                                            String guidance) {
        latest = new State(GuidedTuningRecipe.BLEND_DURATION,
                captureState, null, null, null,
                blendDuration == null ? BlendDurationFocusModel.setup() : blendDuration,
                guidance);
    }

    public static void publish(GuidedTuningRecipe recipe, GuidedCaptureState captureState,
                               MapEstimateFocusSnapshot legacySnapshot, String guidance) {
        if (recipe == GuidedTuningRecipe.ENGAGEMENT_DETECTION) {
            latest = new State(recipe, captureState, null,
                    EngagementFocusModel.setupFromWorkingTune(captureState), null, null, guidance);
            return;
        }
        if (recipe == GuidedTuningRecipe.FOUNDATION_THRESHOLD) {
            latest = new State(recipe, captureState, null, null,
                    FoundationThresholdFocusBridge.snapshot(captureState), null, guidance);
            return;
        }
        if (recipe == GuidedTuningRecipe.BLEND_DURATION) {
            BlendDurationFocusModel model = latest.blendDuration == null
                    ? BlendDurationFocusModel.setup() : latest.blendDuration;
            latest = new State(recipe, captureState, null, null, null, model, guidance);
            return;
        }
        MapEstimateFocusModel model = legacySetupModel(legacySnapshot);
        latest = new State(recipe, captureState,
                model == null ? latest.mapEstimate : model, null, null, null, guidance);
    }

    public static void publishMapEstimateSetup(MapEstimateFocusModel mapEstimate, String guidance) {
        publish(GuidedTuningRecipe.MAP_ESTIMATE, GuidedCaptureState.IDLE, mapEstimate, guidance);
    }

    public static void publishMapEstimateSetup(MapEstimateFocusSnapshot legacySnapshot, String guidance) {
        MapEstimateFocusModel model = legacySetupModel(legacySnapshot);
        latest = new State(GuidedTuningRecipe.MAP_ESTIMATE,
                GuidedCaptureState.IDLE, model == null ? latest.mapEstimate : model,
                null, null, null, guidance);
    }

    private static MapEstimateFocusModel legacySetupModel(MapEstimateFocusSnapshot legacy) {
        if (legacy == null || legacy.tpsBins.length == 0 || legacy.rpmBins.length == 0) return null;
        try {
            MapEstimateEvidenceSession empty = new MapEstimateEvidenceSession(
                    null, "guided-focus-setup", legacy.tpsBins, legacy.rpmBins);
            return MapEstimateFocusModel.build(empty, null, legacy.minimumSamples, 115.0,
                    legacy.liveTps, legacy.liveRpm, legacy.eligibility.getDisplayText(),
                    MapEstimateCoverageStrategy.INTERPOLATED_COVERAGE,
                    MapEstimateCellScope.all(legacy.tpsBins.length, legacy.rpmBins.length));
        } catch (IOException impossibleWithoutStore) {
            throw new IllegalStateException("Could not initialize in-memory MAP Estimate Focus axes",
                    impossibleWithoutStore);
        }
    }

    public static void clear() {
        FoundationThresholdFocusBridge.reset();
        latest = new State(GuidedTuningRecipe.MAP_ESTIMATE,
                GuidedCaptureState.IDLE, null, null, null, null,
                "No active Guided Focus session. Read Working Tune and select a Guided method.");
    }
}
