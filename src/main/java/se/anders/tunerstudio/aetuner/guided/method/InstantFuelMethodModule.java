package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.Locale;

/** Final Instant Fuel residual-validation route; tuning belongs to tasks 1-3. */
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
            ChannelRole.WALL_CORRECTION,
            ChannelRole.WALL_WETTING_PW,
            ChannelRole.MAP_PRED_ACTIVE,
            ChannelRole.FALLBACK_MAP,
            ChannelRole.DFCO,
            ChannelRole.FUEL_CUT,
            ChannelRole.COOLANT,
            ChannelRole.IAT
    };

    @Override public GuidedTuningRecipe recipe() { return GuidedTuningRecipe.INSTANT_FUEL; }
    @Override public String setupTitle() { return "Instant Fuel residual lean-hole validation"; }
    @Override public String setupGuidance() {
        return "Validate the completed Instant Fuel setup after Global Pulse/Inhibit, Event Strength and Operating-Condition Multipliers have been tuned. The goal is to prove the residual early lean hole is removed without converting a short first-moment correction into a sustained rich error. This final task is evidence-only; it owns no duplicate write target.";
    }
    @Override public String captureGoal() {
        return "Accumulate repeated Instant Fuel pulse events and compare the early response with the later transient while retaining overlap from every other fuel path. Sustained same-direction error belongs upstream and must not be hidden by Instant Fuel.";
    }
    @Override public ChannelRole[] requiredRoles() { return REQUIRED.clone(); }
    @Override public ChannelRole[] contextRoles() { return CONTEXT.clone(); }
    @Override public String operatorInputs(AeProjectSnapshot snapshot) {
        return "Use one unchanged completed Working Tune. Repeat the same residual early lean-hole validation event at comparable RPM/load, then include several condition and re-apply checks. Avoid fuel cut. Do not change Instant Fuel settings during validation.";
    }
    @Override public String accumulationPlan() {
        return "Count distinct pulse events, compare first-moment versus sustained lambda error, and report overlap counts for TPS AE / Wall Wetting / MAP Predict. Classify any remaining error back to Global Pulse/Inhibit, Event Strength, Operating Conditions, or an upstream method. No setting is proposed from this final validation task.";
    }
    @Override public String reviewOutputs() {
        return "Residual early lean-hole/rich-spike verdict, clean pulse count, sustained-error rejection, overlap counts, condition/re-apply coverage and routing back to the owning tuning task. No ProposalWritePlan; capture never writes; no Burn.";
    }
    @Override public String currentTuneContext(AeProjectSnapshot snapshot) {
        if (snapshot == null) return "Working tune not read yet.";
        String instant = snapshot.hasInstantFuelSettings()
                ? " | multiplier " + f(snapshot.getExtraShotMultiplier())
                        + " | timer " + f(snapshot.getExtraShotTimer()) + " cycle(s)"
                : " | multiplier/timer baseline unavailable";
        return "Instant Fuel Pulse: " + enabled(snapshot.isExtraShotEnabled())
                + instant
                + " | tuning ownership: Tasks 1-3"
                + " | this task: evidence-only final validation"
                + " | TPS AE: " + enabled(snapshot.isTpsAeEnabled())
                + " | Wall Wetting: " + enabled(snapshot.isWallWettingEnabled())
                + " | MAP Predict: " + enabled(snapshot.isMapEstimateEnabled());
    }
    @Override public boolean activityObserved(LiveSample sample) {
        return sample != null && (positive(sample, ChannelRole.INSTANT_PULSE_PW)
                || sample.bool(ChannelRole.AE_EVENT_JUST_OCCURRED));
    }

    private static String f(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.2f", value) : "n/a";
    }
}
