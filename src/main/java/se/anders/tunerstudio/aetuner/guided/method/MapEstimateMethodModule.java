package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

/** MAP Estimate table evidence route used by the predictive-MAP workflow. */
public final class MapEstimateMethodModule extends AbstractProbeMethodModule {
    private static final ChannelRole[] REQUIRED = new ChannelRole[]{
            ChannelRole.RPM,
            ChannelRole.TPS,
            ChannelRole.MAP,
            ChannelRole.MAP_PRED_ACTIVE,
            ChannelRole.AE_ABOVE_THRESHOLD,
            ChannelRole.AE_EXTRA_SHOT,
            ChannelRole.INSTANT_PULSE_PW,
            ChannelRole.DFCO,
            ChannelRole.FUEL_CUT
    };
    private static final ChannelRole[] CONTEXT = new ChannelRole[]{
            ChannelRole.FALLBACK_MAP,
            ChannelRole.LAMBDA,
            ChannelRole.TARGET_LAMBDA,
            ChannelRole.COOLANT,
            ChannelRole.IAT,
            ChannelRole.GEAR,
            ChannelRole.VSS
    };

    @Override public GuidedTuningRecipe recipe() { return GuidedTuningRecipe.MAP_ESTIMATE; }
    @Override public String setupTitle() { return "MAP Estimate Table — steady learned-surface calibration"; }
    @Override public String setupGuidance() {
        return "Drive steady operating points rather than pedal stabs. Stable measured MAP keeps its actual TPS/RPM coordinates in compact learned memory; table-node authority is derived later from geometrically supported direct evidence or bounded interpolation. Transient and fuel-cut samples are rejected.";
    }
    @Override public String captureGoal() {
        return "Build a trustworthy MAP Estimate TPS/RPM surface from stable measured-MAP evidence. Interpolated Coverage requests high-value anchors by default; Direct Fine Tune can restrict capture and proposal authority to selected cells.";
    }
    @Override public ChannelRole[] requiredRoles() { return REQUIRED.clone(); }
    @Override public ChannelRole[] contextRoles() { return CONTEXT.clone(); }
    @Override public String operatorInputs(AeProjectSnapshot snapshot) {
        return "Set minimum stable samples per direct anchor and the high-TPS MAP safety cap. Then hold steady throttle/RPM regions long enough to build evidence near the requested target; vary TPS and RPM deliberately instead of creating acceleration-enrichment events.";
    }
    @Override public String accumulationPlan() {
        return "Accept at most 25 Hz while RPM >= 500, |TPSdot| <= 2 %/s and |MAPdot| <= 12 kPa/s. Reject DFCO/fuel cut, active MAP prediction, active TPS AE/extra-shot and Instant Fuel. Accepted samples retain actual TPS/RPM coordinates in 1% TPS x 100 RPM micro-buckets. A table node becomes Direct only when enough geometrically supported evidence reaches that exact coordinate and remains within the 5 kPa standard deviation quality gate; completed-session agreement can confirm it, disagreement can mark it Recheck/Conflict. Interpolation is triangle-bounded and never extrapolates.";
    }
    @Override public String reviewOutputs() {
        return "Stable sample count, Direct/interpolated provenance, provisional/confirmed/recheck maturity, changed-cell diff and a TunerStudio paste-ready MAP Estimate table. Unvisited or unsupported cells stay unchanged; the high-TPS cap is applied only to eligible proposal cells.";
    }
    @Override public String currentTuneContext(AeProjectSnapshot snapshot) {
        if (snapshot == null) return "Working tune not read yet.";
        return "Use MAP estimate during transient: " + enabled(snapshot.isMapEstimateEnabled())
                + " | table: " + (snapshot.hasMapEstimateTable()
                ? snapshot.getMapEstimateTpsBins().length + " TPS rows x " + snapshot.getMapEstimateRpmBins().length + " RPM columns"
                : "not found")
                + " | TPS axis: " + axis(snapshot.getMapEstimateTpsBins())
                + " | RPM axis: " + axis(snapshot.getMapEstimateRpmBins());
    }
    @Override public boolean activityObserved(LiveSample sample) {
        return false;
    }
}
