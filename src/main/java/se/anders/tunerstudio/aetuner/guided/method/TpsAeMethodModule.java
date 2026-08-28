package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

/** TPS AE evidence route; TPS-specific table/cycle evaluation remains isolated here. */
public final class TpsAeMethodModule extends AbstractProbeMethodModule {
    private static final ChannelRole[] REQUIRED = new ChannelRole[]{
            ChannelRole.RPM,
            ChannelRole.TPS,
            ChannelRole.MAP,
            ChannelRole.LAMBDA,
            ChannelRole.TARGET_LAMBDA,
            ChannelRole.PW,
            ChannelRole.TPS_FROM,
            ChannelRole.TPS_TO,
            ChannelRole.DELTA_TPS,
            ChannelRole.SMOOTHED_DELTA_TPS,
            ChannelRole.ACCEL_THRESHOLD,
            ChannelRole.AE_ABOVE_THRESHOLD,
            ChannelRole.AE_ADD_MS,
            ChannelRole.EXTRA_FUEL,
            ChannelRole.TPS_AE_CYCLE_MULT,
            ChannelRole.TPS_AE_CYCLE_CNT
    };
    private static final ChannelRole[] CONTEXT = new ChannelRole[]{
            ChannelRole.WALL_WETTING_PW,
            ChannelRole.INSTANT_PULSE_PW,
            ChannelRole.MAP_PRED_ACTIVE,
            ChannelRole.FALLBACK_MAP,
            ChannelRole.DFCO,
            ChannelRole.FUEL_CUT,
            ChannelRole.COOLANT,
            ChannelRole.IAT
    };

    @Override public GuidedTuningRecipe recipe() { return GuidedTuningRecipe.TPS_AE; }
    @Override public String setupTitle() { return "TPS AE transient/table evidence"; }
    @Override public String setupGuidance() {
        return "Collect repeatable pedal openings that exercise the actual TPS-to rows and cycle-duration shape in the working tune. Lambda/target lambda and the ECU's TPS AE contribution must be captured together so early amount and late duration can be separated.";
    }
    @Override public String captureGoal() {
        return "Accumulate repeated TPS AE fuel-proved events across useful TPS-to rows with complete detector, cycle and lambda evidence. Completed event windows feed the conservative TPS AE table draft; no ECU write is authorized.";
    }
    @Override public ChannelRole[] requiredRoles() { return REQUIRED.clone(); }
    @Override public ChannelRole[] contextRoles() { return CONTEXT.clone(); }
    @Override public String operatorInputs(AeProjectSnapshot snapshot) {
        return "Read the working tune first, then choose the desired number of clean transient events. Deliberately vary the ending TPS so more than one TPS-to row is covered; repeat similar openings enough times to distinguish early fuel amount from late cycle-duration/rate effects.";
    }
    @Override public String accumulationPlan() {
        return "Count clean TPS AE-active/fuel-visible bursts after quiet intervals. Guided retains a short pre/post event window, then reuses the existing TransientEvent attribution and conservative AeTableSuggestion logic. Capture TPS from/to/change, smoothedDeltaTps versus AccelThreshold, add-fuel/extraFuel, cycle multiplier/count, injector PW and lambda response. Preserve Wall Wetting, Instant Fuel and MAP Predict context so mixed events can be excluded or down-weighted rather than attributed entirely to TPS AE.";
    }
    @Override public String reviewOutputs() {
        return "Event-count progress, required-channel readiness, trigger-ratio peak, TPS-from/to coverage, maximum TPS AE fuel, cycle multiplier/count behavior, lambda-minus-target shape and overlap with other AE paths. When at least 3.0 effective fuel-proved events support a TPS-to row, the existing bounded TPS AE table generator can expose a reviewed paste-ready draft for Copy/Export only.";
    }
    @Override public String currentTuneContext(AeProjectSnapshot snapshot) {
        if (snapshot == null) return "Working tune not read yet; TPS AE draft generation requires the current TPS-to/cycle table baseline.";
        return "TPS Acceleration Enrichment: " + enabled(snapshot.isTpsAeEnabled())
                + " | TPS-to rows: " + axis(snapshot.getTpsToBins())
                + " | Engine Cycle columns: " + axis(snapshot.getCycleBins())
                + " | Wall Wetting: " + enabled(snapshot.isWallWettingEnabled())
                + " | MAP Predict: " + enabled(snapshot.isMapEstimateEnabled())
                + " | Instant Fuel: " + enabled(snapshot.isExtraShotEnabled());
    }
    @Override public boolean activityObserved(LiveSample sample) {
        return sample != null && (sample.bool(ChannelRole.AE_ABOVE_THRESHOLD)
                || positive(sample, ChannelRole.AE_ADD_MS)
                || nonZero(sample, ChannelRole.EXTRA_FUEL));
    }
}
