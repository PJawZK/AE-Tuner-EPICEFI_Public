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
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;

import java.io.IOException;

/** Presentation/configuration bus for the modeless Guided Focus window. */
public final class GuidedFocusHub {
    public interface CaptureControl {
        GuidedCaptureState finishCapture();
        GuidedCaptureState togglePause();
        GuidedCaptureState continueCapture();
        GuidedCaptureState captureState();
        GuidedTuningRecipe activeRecipe();
        boolean reviewReady();
        boolean canContinueCapture();
        boolean canExportEvidence();
        void exportEvidence();
    }

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

    private static volatile State latest = idleState(
            "Read Working Tune and select MAP Estimate Table to initialize learned coverage.");
    private static volatile MapEstimateGuidedFocusPanel.ConfigurationListener mapEstimateListener;
    private static volatile CaptureControl captureControl;

    private GuidedFocusHub() { }

    private static State idleState(String guidance) {
        return new State(GuidedTuningRecipe.MAP_ESTIMATE, GuidedCaptureState.IDLE,
                null, null, null, null, guidance);
    }

    public static State snapshot() { return latest; }

    public static void setCaptureControl(CaptureControl control) {
        captureControl = control;
    }

    public static GuidedCaptureState activeCaptureState() {
        CaptureControl control = captureControl;
        if (control != null) {
            GuidedCaptureState state = control.captureState();
            if (state != null) return state;
        }
        return latest == null ? GuidedCaptureState.IDLE : latest.captureState;
    }

    public static GuidedTuningRecipe activeCaptureRecipe() {
        CaptureControl control = captureControl;
        if (control != null) {
            GuidedTuningRecipe recipe = control.activeRecipe();
            if (recipe != null) return recipe;
        }
        return latest == null ? null : latest.recipe;
    }

    public static boolean canFinishCapture() {
        CaptureControl control = captureControl;
        GuidedCaptureState state = activeCaptureState();
        return control != null && state.isCaptureInProgress();
    }

    /**
     * COMPLETE is only a lifecycle state. Review/export authority is granted by
     * the production evidence model separately so an early manual Finish cannot
     * be presented as accepted evidence.
     */
    public static boolean isActiveEvidenceReviewReady() {
        CaptureControl control = captureControl;
        return control != null
                && activeCaptureState() == GuidedCaptureState.COMPLETE
                && control.reviewReady();
    }

    /** Continue only an explicitly stopped, still-incomplete capture. */
    public static boolean canContinueActiveCapture() {
        CaptureControl control = captureControl;
        return control != null
                && activeCaptureState() == GuidedCaptureState.COMPLETE
                && !control.reviewReady()
                && control.canContinueCapture();
    }

    public static GuidedCaptureState finishActiveCapture() {
        CaptureControl control = captureControl;
        return control == null ? activeCaptureState() : control.finishCapture();
    }

    public static GuidedCaptureState toggleActiveCapturePause() {
        CaptureControl control = captureControl;
        return control == null ? activeCaptureState() : control.togglePause();
    }

    public static GuidedCaptureState continueActiveCapture() {
        CaptureControl control = captureControl;
        return control == null || !canContinueActiveCapture()
                ? activeCaptureState() : control.continueCapture();
    }

    public static boolean canExportActiveEvidence() {
        CaptureControl control = captureControl;
        return control != null && isActiveEvidenceReviewReady()
                && control.canExportEvidence();
    }

    public static void exportActiveEvidence() {
        CaptureControl control = captureControl;
        if (control != null && isActiveEvidenceReviewReady()
                && control.canExportEvidence()) control.exportEvidence();
    }

    /**
     * Session export is a read-only snapshot of whatever has been collected so
     * far. Unlike Export Evidence, it does not imply Review readiness and does
     * not unlock Review or Apply.
     */
    public static boolean canExportActiveSession() {
        CaptureControl control = captureControl;
        return control != null && control.canExportEvidence();
    }

    public static void exportActiveSession() {
        CaptureControl control = captureControl;
        if (control != null && control.canExportEvidence()) control.exportEvidence();
    }

    public static void setMapEstimateConfigurationListener(
            MapEstimateGuidedFocusPanel.ConfigurationListener listener) {
        mapEstimateListener = listener;
    }

    static MapEstimateGuidedFocusPanel.ConfigurationListener mapEstimateConfigurationListener() {
        return mapEstimateListener;
    }

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

    public static void publishMapEstimateSetup(MapEstimateFocusModel mapEstimate,
                                               String guidance) {
        publish(GuidedTuningRecipe.MAP_ESTIMATE, GuidedCaptureState.IDLE,
                mapEstimate, guidance);
    }

    /**
     * Initialize idle MAP Estimate Focus directly from the current Working Tune.
     * This setup view intentionally has no learned-memory authority; the real
     * GuidedMethodProbeSession loads persistent memory when MAP Estimate capture
     * is configured.
     */
    public static void publishMapEstimateSetup(AeProjectSnapshot snapshot,
                                               int minimumSamples,
                                               double capKpa,
                                               String guidance) {
        if (snapshot == null || !snapshot.hasMapEstimateTable()) {
            latest = new State(GuidedTuningRecipe.MAP_ESTIMATE,
                    GuidedCaptureState.IDLE, null, null, null, null, guidance);
            return;
        }
        try {
            double[] tps = snapshot.getMapEstimateTpsBins();
            double[] rpm = snapshot.getMapEstimateRpmBins();
            MapEstimateEvidenceSession empty = new MapEstimateEvidenceSession(
                    null, snapshot.getConfigurationName(), tps, rpm);
            MapEstimateFocusModel model = MapEstimateFocusModel.build(
                    empty, snapshot.getMapEstimateTable(), minimumSamples, capKpa,
                    Double.NaN, Double.NaN, "Waiting for capture",
                    MapEstimateCoverageStrategy.INTERPOLATED_COVERAGE,
                    MapEstimateCellScope.all(tps.length, rpm.length),
                    MapEstimateEvidenceBasis.LEARNED_MEMORY,
                    MapEstimateProposalLimitPolicy.HIGH_TPS_CAP);
            publishMapEstimateSetup(model, guidance);
        } catch (IOException impossibleWithoutStore) {
            throw new IllegalStateException(
                    "Could not initialize in-memory MAP Estimate Focus axes",
                    impossibleWithoutStore);
        }
    }

    /**
     * Reset presentation state during a normal Guided session reset. Listener and
     * capture-control ownership deliberately survives this operation because the
     * same live plugin instance still owns the next capture.
     */
    public static void clear() {
        FoundationThresholdFocusBridge.reset();
        latest = idleState(
                "No active Guided Focus session. Read Working Tune and select a Guided method.");
    }

    /**
     * Final plugin/classloader retirement. Unlike clear(), this releases static
     * callbacks that otherwise retain the previous production bridge/session.
     */
    public static void dispose() {
        FoundationThresholdFocusBridge.reset();
        captureControl = null;
        mapEstimateListener = null;
        latest = idleState("No active Guided Focus plugin instance.");
    }

    static boolean hasCaptureControlForTest() { return captureControl != null; }
    static boolean hasMapEstimateListenerForTest() { return mapEstimateListener != null; }
}
