package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

/** Instant Fuel Pulse evidence route; pulse-specific rules remain isolated here. */
public final class InstantFuelMethodModule extends AbstractProbeMethodModule {
    private static final ChannelRole[] REQUIRED = new ChannelRole[]{
            ChannelRole.RPM,
            ChannelRole.TPS,
            ChannelRole.MAP,
            ChannelRole.LAMBDA,
            ChannelRole.TARGET_LAMBDA,
            ChannelRole.PW,
            ChannelRole.SMOOTHED_DELTA_TPS,
            ChannelRole.ACCEL_THRESHOLD,
            ChannelRole.AE_EVENT_JUST_OCCURRED,
            ChannelRole.INSTANT_PULSE_PW,
            ChannelRole.INSTANT_PULSE_CNT
    };
    private static final ChannelRole[] CONTEXT = new ChannelRole[]{
            ChannelRole.AE_ABOVE_THRESHOLD,
            ChannelRole.AE_ADD_MS,
            ChannelRole.EXTRA_FUEL,
            ChannelRole.WALL_WETTING_PW,
            ChannelRole.MAP_PRED_ACTIVE,
            ChannelRole.FALLBACK_MAP,
            ChannelRole.DFCO,
            ChannelRole.FUEL_CUT,
            ChannelRole.COOLANT,
            ChannelRole.IAT
    };

    @Override public GuidedTuningRecipe recipe() { return GuidedTuningRecipe.INSTANT_FUEL; }
    @Override public String setupTitle() { return "Instant Fuel early-transient evidence"; }
    @Override public String setupGuidance() {
        return "Use clean fast pedal openings only after the base transient methods are close enough that a residual early lean hole can be distinguished. The pulse itself, event counter and early lambda response must all be visible; Instant Fuel should not become a blanket fix for MAP Estimate, Blend Duration, Wall Wetting or TPS AE errors.";
    }
    @Override public String captureGoal() {
        return "Accumulate repeatable Instant Fuel pulse events and the immediate lambda response while recording overlap from every other transient-fuel path.";
    }
    @Override public ChannelRole[] requiredRoles() { return REQUIRED.clone(); }
    @Override public ChannelRole[] contextRoles() { return CONTEXT.clone(); }
    @Override public String operatorInputs(AeProjectSnapshot snapshot) {
        return "Choose the desired number of clean fast-opening events. Reproduce the same residual early lean-hole condition several times at comparable RPM/load; avoid using this capture as the first tuning step when the other AE methods are still obviously wrong.";
    }
    @Override public String accumulationPlan() {
        return "Count distinct instant-pulse activations/counter events after quiet intervals. Capture pulse PW/count, shared detector ratio, injector PW and the immediate lambda-minus-target response. Record TPS AE, Wall Wetting and MAP Predict overlap so a pulse recommendation can later require a repeatable residual lean error rather than mixed-method activity.";
    }
    @Override public String reviewOutputs() {
        return "Event-count progress, required-channel readiness, maximum instant pulse PW, pulse-counter movement, trigger-ratio peak, early lambda-minus-target excursion and overlap counts with TPS AE, Wall Wetting and MAP Predict. No pulse-size proposal should be produced until repeated residual early-lean evidence survives those overlap checks.";
    }
    @Override public String currentTuneContext(AeProjectSnapshot snapshot) {
        if (snapshot == null) return "Working tune not read yet.";
        return "Instant Fuel Pulse: " + enabled(snapshot.isExtraShotEnabled())
                + " | TPS AE: " + enabled(snapshot.isTpsAeEnabled())
                + " | Wall Wetting: " + enabled(snapshot.isWallWettingEnabled())
                + " | MAP Predict: " + enabled(snapshot.isMapEstimateEnabled());
    }
    @Override public boolean activityObserved(LiveSample sample) {
        return sample != null && (positive(sample, ChannelRole.INSTANT_PULSE_PW)
                || sample.bool(ChannelRole.AE_EVENT_JUST_OCCURRED));
    }
}
