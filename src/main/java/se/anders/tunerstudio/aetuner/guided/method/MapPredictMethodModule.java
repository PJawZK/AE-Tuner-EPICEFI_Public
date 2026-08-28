package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

/** MAP Predict activation/effective-MAP evidence route; Blend Duration conversion remains separate. */
public final class MapPredictMethodModule extends AbstractProbeMethodModule {
    private static final ChannelRole[] REQUIRED = new ChannelRole[]{
            ChannelRole.RPM,
            ChannelRole.TPS,
            ChannelRole.MAP,
            ChannelRole.FALLBACK_MAP,
            ChannelRole.EFFECTIVE_MAP,
            ChannelRole.MAP_PRED_ACTIVE,
            ChannelRole.SMOOTHED_DELTA_TPS,
            ChannelRole.ACCEL_THRESHOLD,
            ChannelRole.MAP_PRED_RESET_CNT,
            ChannelRole.MAP_PRED_EVENT_OVER
    };
    private static final ChannelRole[] CONTEXT = new ChannelRole[]{
            ChannelRole.LAMBDA,
            ChannelRole.TARGET_LAMBDA,
            ChannelRole.AE_ADD_MS,
            ChannelRole.WALL_WETTING_PW,
            ChannelRole.INSTANT_PULSE_PW,
            ChannelRole.GEAR,
            ChannelRole.VSS
    };

    @Override public GuidedTuningRecipe recipe() { return GuidedTuningRecipe.MAP_PREDICT; }
    @Override public String setupTitle() { return "MAP Predict Transient — activation/use verification"; }
    @Override public String setupGuidance() {
        return "Reproduce clean single throttle openings. The plugin records the shared detector threshold, prediction activation, fallbackMap, Effective MAP and predictor counters so trigger behavior and same-frame prediction behavior can be reviewed independently of Blend Duration conversion.";
    }
    @Override public String captureGoal() {
        return "Accumulate repeated clean MAP Predict activations with coherent trigger, fallbackMap, Effective MAP and measured MAP evidence.";
    }
    @Override public ChannelRole[] requiredRoles() { return REQUIRED.clone(); }
    @Override public ChannelRole[] contextRoles() { return CONTEXT.clone(); }
    @Override public String operatorInputs(AeProjectSnapshot snapshot) {
        return "Choose the desired number of clean transient events. Use representative RPM/load regions and one deliberate pedal opening per event; hold the pedal long enough for measured MAP to respond before returning to baseline.";
    }
    @Override public String accumulationPlan() {
        return "Count one event only after a fresh prediction activation/fallback lead following a quiet interval. Record smoothedDeltaTps versus AccelThreshold, predictor-active state, reset/event-over counters, fallbackMap, Effective MAP and measured MAP for the complete transient. Repeated stabs remain separate evidence rather than being merged.";
    }
    @Override public String reviewOutputs() {
        return "Event-count progress, required-channel readiness, peak detector ratio, prediction-active sample count, maximum fallbackMap lead, Effective MAP/fallback alignment diagnostics and optional lambda/fuel-path overlap context.";
    }
    @Override public String currentTuneContext(AeProjectSnapshot snapshot) {
        if (snapshot == null) return "Working tune not read yet.";
        return "Use MAP estimate during transient: " + enabled(snapshot.isMapEstimateEnabled())
                + " | MAP Estimate table: "
                + (snapshot.hasMapEstimateTable() ? snapshot.getMapEstimateTpsBins().length + "x" + snapshot.getMapEstimateRpmBins().length : "not found")
                + " | Blend Duration RPM axis: " + axis(snapshot.getBlendDurationRpmBins());
    }
    @Override public boolean activityObserved(LiveSample sample) {
        return sample != null && sample.bool(ChannelRole.MAP_PRED_ACTIVE);
    }
}
