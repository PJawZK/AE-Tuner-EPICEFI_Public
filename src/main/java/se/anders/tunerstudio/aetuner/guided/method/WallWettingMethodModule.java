package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

/** Wall Wetting evidence route; future tau/beta math stays in this method boundary. */
public final class WallWettingMethodModule extends AbstractProbeMethodModule {
    private static final ChannelRole[] REQUIRED = new ChannelRole[]{
            ChannelRole.RPM,
            ChannelRole.TPS,
            ChannelRole.MAP,
            ChannelRole.LAMBDA,
            ChannelRole.TARGET_LAMBDA,
            ChannelRole.PW,
            ChannelRole.WALL_CORRECTION,
            ChannelRole.WALL_WETTING_PW,
            ChannelRole.SMOOTHED_DELTA_TPS,
            ChannelRole.ACCEL_THRESHOLD
    };
    private static final ChannelRole[] CONTEXT = new ChannelRole[]{
            ChannelRole.AE_ADD_MS,
            ChannelRole.EXTRA_FUEL,
            ChannelRole.INSTANT_PULSE_PW,
            ChannelRole.MAP_PRED_ACTIVE,
            ChannelRole.FALLBACK_MAP,
            ChannelRole.DFCO,
            ChannelRole.FUEL_CUT,
            ChannelRole.COOLANT,
            ChannelRole.IAT
    };

    @Override public GuidedTuningRecipe recipe() { return GuidedTuningRecipe.WALL_WETTING; }
    @Override public String setupTitle() { return "Wall Wetting transient accumulation"; }
    @Override public String setupGuidance() {
        return "Collect both clean tip-in and tip-out examples at a warmed, repeatable operating condition. Wall correction and wall-wetting pulse must be visible together with lambda/target lambda so future tau/beta changes can be attributed instead of guessed from AFR alone.";
    }
    @Override public String captureGoal() {
        return "Accumulate repeated Wall Wetting-active transients with lambda error, pulse width and overlapping AE-path context.";
    }
    @Override public ChannelRole[] requiredRoles() { return REQUIRED.clone(); }
    @Override public ChannelRole[] contextRoles() { return CONTEXT.clone(); }
    @Override public String operatorInputs(AeProjectSnapshot snapshot) {
        return "Choose the desired number of transient events. Capture a balanced set of tip-ins and tip-outs at similar RPM/load, preferably fully warm, and keep each pedal action clean enough that one transient can be evaluated at a time.";
    }
    @Override public String accumulationPlan() {
        return "Count separate Wall Wetting-active bursts after a quiet interval. Retain the full lambda response before/during/after wall correction, measured MAP/TPS/RPM and injector pulse width. Record TPS AE, Instant Fuel and MAP Predict overlap so mixed events can be down-weighted or excluded when tau/beta math is added.";
    }
    @Override public String reviewOutputs() {
        return "Event-count progress, required-channel readiness, peak wall correction/pulse, lambda-minus-target excursion, transient duration and overlap counts with TPS AE, MAP Predict and Instant Fuel. The later tau/beta evaluator should use repeatable tip-in/tip-out shape rather than one AFR peak.";
    }
    @Override public String currentTuneContext(AeProjectSnapshot snapshot) {
        if (snapshot == null) return "Working tune not read yet.";
        return "Wall Wetting: " + enabled(snapshot.isWallWettingEnabled())
                + " | model: " + snapshot.getWallWettingModel()
                + " | TPS cycle AE: " + enabled(snapshot.isTpsAeEnabled())
                + " | MAP Predict: " + enabled(snapshot.isMapEstimateEnabled())
                + " | Instant Fuel: " + enabled(snapshot.isExtraShotEnabled());
    }
    @Override public boolean activityObserved(LiveSample sample) {
        return nonZero(sample, ChannelRole.WALL_CORRECTION)
                || nonZero(sample, ChannelRole.WALL_WETTING_PW);
    }
}
