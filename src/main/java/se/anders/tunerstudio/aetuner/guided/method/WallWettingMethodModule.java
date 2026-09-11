package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.List;
import java.util.Locale;

/** Wall Wetting evidence route with bounded Basic-model Beta authority. */
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

    private volatile boolean reviewDirty = true;
    private volatile String lastEvidenceReview = defaultReviewText();
    private AeProjectSnapshot cachedSnapshot;
    private int cachedEvidenceSize = -1;
    private long cachedLastNano = Long.MIN_VALUE;
    private WallWettingRecommendation.Result cachedResult;

    @Override public GuidedTuningRecipe recipe() { return GuidedTuningRecipe.WALL_WETTING; }
    @Override public String setupTitle() { return "Wall Wetting transient accumulation"; }
    @Override public String setupGuidance() {
        return "Collect clean comparable Wall-active tip-ins at a warmed, repeatable operating condition. Basic Beta uses method-owned delayed-response events. Tau remains visible and directly review/apply-capable, but automatic Tau movement is withheld until measured lambda transport delay is aligned with immediate wall-correction decay.";
    }
    @Override public String captureGoal() {
        return "Accumulate repeated Wall Wetting-active transients with lambda error, pulse width and overlapping AE-path context. Obtain at least three clean comparable method-owned tip-ins for Beta; retain tip-outs and decay behavior for Tau diagnostics and later transport-delay calibration.";
    }
    @Override public ChannelRole[] requiredRoles() { return REQUIRED.clone(); }
    @Override public ChannelRole[] contextRoles() { return CONTEXT.clone(); }
    @Override public String operatorInputs(AeProjectSnapshot snapshot) {
        return "Use one unchanged Working Tune. Capture a balanced set of clean tip-ins and tip-outs at similar RPM/load, preferably fully warm. Repeated comparable tip-ins are Beta authority; tip-outs and decay are retained for Tau diagnostics/transport-delay calibration. Do not alter Tau, Beta, TPS AE, MAP Predict or Instant Fuel during the capture. Instant-pulse overlap is rejected from automatic Basic Beta evidence.";
    }
    @Override public String accumulationPlan() {
        return "Retain the full response around wall correction, measured MAP/TPS/RPM and injector PW. Beta is binned from method-owned delayed-response events. Raw wall-decay versus lambda-recovery timing remains diagnostic only until exhaust/sensor transport is aligned; Tau is therefore never auto-moved by this pass.";
    }
    @Override public synchronized String reviewOutputs() {
        return reviewDirty ? defaultReviewText() : lastEvidenceReview;
    }
    @Override public String currentTuneContext(AeProjectSnapshot snapshot) {
        if (snapshot == null) return "Working tune not read yet.";
        String scalars = snapshot.hasWallTauBeta()
                ? " | Tau " + f(snapshot.getWallTau()) + " s | Beta " + f(snapshot.getWallBeta())
                : " | Tau/Beta baseline unavailable";
        return "Wall Wetting: " + enabled(snapshot.isWallWettingEnabled())
                + " | model: " + snapshot.getWallWettingModel()
                + scalars
                + " | automatic authority: Basic Beta only"
                + " | Tau: transport-unaligned diagnostic / direct Task Settings"
                + " | TPS cycle AE: " + enabled(snapshot.isTpsAeEnabled())
                + " | MAP Predict: " + enabled(snapshot.isMapEstimateEnabled())
                + " | Instant Fuel: " + enabled(snapshot.isExtraShotEnabled());
    }
    @Override public synchronized boolean activityObserved(LiveSample sample) {
        reviewDirty = true;
        return nonZero(sample, ChannelRole.WALL_CORRECTION)
                || nonZero(sample, ChannelRole.WALL_WETTING_PW);
    }
    @Override public synchronized ProposalWritePlan reviewedWritePlan(
            AeProjectSnapshot snapshot, List<LiveSample> evidence) {
        WallWettingRecommendation.Result result = evaluationFor(snapshot, evidence);
        lastEvidenceReview = result.reviewText;
        reviewDirty = false;
        return result.plan;
    }

    private WallWettingRecommendation.Result evaluationFor(
            AeProjectSnapshot snapshot, List<LiveSample> evidence) {
        int size = evidence == null ? 0 : evidence.size();
        long lastNano = size == 0 ? Long.MIN_VALUE
                : evidence.get(size - 1).getNanoTime();
        if (cachedResult != null && snapshot == cachedSnapshot
                && size == cachedEvidenceSize && lastNano == cachedLastNano) {
            return cachedResult;
        }
        cachedSnapshot = snapshot;
        cachedEvidenceSize = size;
        cachedLastNano = lastNano;
        cachedResult = WallWettingRecommendation.evaluate(snapshot, evidence);
        return cachedResult;
    }

    private static String defaultReviewText() {
        return "Wall Wetting review shows Basic Working Tune Tau/Beta, method-owned tip-in count, delayed lambda-target direction, overlap/rejection gates and a bounded Beta proposal when repeated evidence supports it. Tau timing remains diagnostic only until measured lambda transport alignment exists; Tau itself remains directly review/apply-capable. Capture never writes; no Burn.";
    }

    private static String f(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%.4f", value) : "n/a";
    }
}
