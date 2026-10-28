package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.List;
import java.util.Locale;

/** Shared falling-TPS detector evidence and threshold-only recommendation route. */
public final class DecelDetectionMethodModule extends AbstractProbeMethodModule {
    private static final ChannelRole[] REQUIRED = new ChannelRole[]{
            ChannelRole.RPM, ChannelRole.TPS, ChannelRole.DECEL_DELTA_TPS,
            ChannelRole.TPS_DECEL_ACTIVE
    };
    private static final ChannelRole[] CONTEXT = new ChannelRole[]{
            ChannelRole.DELTA_TPS,
            ChannelRole.AE_DELTA_MIN_STEP, ChannelRole.AE_DELTA_TIMED_FALL,
            ChannelRole.AE_DELTA_SPAN, ChannelRole.AE_DELTA_FALL,
            ChannelRole.AE_DELTA_NEWEST_FALL,
            ChannelRole.DECEL_THRESHOLD, ChannelRole.TPS_DECEL_CYCLE_CNT,
            ChannelRole.DECEL_FUEL_MULT,
            ChannelRole.SMOOTHED_DELTA_TPS, ChannelRole.ACCEL_THRESHOLD,
            ChannelRole.AE_ABOVE_THRESHOLD, ChannelRole.DFCO, ChannelRole.FUEL_CUT,
            ChannelRole.MAP_PRED_ACTIVE, ChannelRole.AE_ADD_MS
    };

    private volatile boolean reviewDirty = true;
    private volatile String lastEvidenceReview = defaultReviewText();
    private volatile String lastHistoricalReview = "";

    @Override public GuidedTuningRecipe recipe() { return GuidedTuningRecipe.DECEL_DETECTION; }
    @Override public String setupTitle() { return "Decel Detection / Threshold"; }

    @Override public String setupGuidance() {
        return "Read Working Tune, begin with a steady pedal until the quiet falling-TPS calibration locks, then follow Guided Focus through two physical closing-event classes in each broad RPM region. NORMAL CORRECTION is a small natural pedal reduction that should not intentionally enter overrun. DECEL RELEASE is a clear normal throttle lift. The selected TPS detector mode's published falling twin is the live analysis authority; Fuel: TPS decel change/decelTps is retained as a cross-check/fallback and Fuel: TPS AE change is positive-side context only. The recommendation may change only evidence-backed tpsDecelThresholdValue cells. A threshold of 0 is a firmware disable command and is never auto-enabled. Decel hold remains read-only in this pass.";
    }

    @Override public String captureGoal() {
        return "Freeze one quiet TPS-rate baseline, then collect at least three accepted NORMAL CORRECTION and three accepted DECEL RELEASE events in each representative RPM region. Lock a region only when the firmware mode-matched falling-twin magnitudes separate conservatively. Fuel: TPS Decel Active is retained as firmware-state evidence, but hold-cycle persistence is diagnostic and does not redefine the triggering edge.";
    }

    @Override public ChannelRole[] requiredRoles() { return REQUIRED.clone(); }
    @Override public ChannelRole[] contextRoles() { return CONTEXT.clone(); }

    @Override public String operatorInputs(AeProjectSnapshot snapshot) {
        return "Use one unchanged Working Tune. Hold the pedal steady until the quiet calibration is LOCKED. Then obey the current broad-RPM phase: NORMAL CORRECTION = a small natural pedal reduction without intentionally entering overrun; DECEL RELEASE = a clear normal lift, not a slow easing-out. Repeat comparable events. Do not change the decel threshold curve, shared TPS detector mode or tpsDecelHoldCycles during capture. Hold cycles are deliberately read-only until threshold behavior is validated independently.";
    }

    @Override public String accumulationPlan() {
        return "Retain RPM, TPS, signed Fuel: TPS decel change, all published falling detector candidates, Fuel: TPS AE Active, Fuel: TPS Decel Active, logged Decel threshold/cycle state when available, and closing TPS rate for the whole session. The configured detector mode selects its published falling twin for recommendation evidence; direct decelTps is cross-check/fallback only. Fuel: TPS AE change is retained separately and never substituted for Decel. At least 80 qualifying quiet samples spanning 1.5 continuous seconds freeze the noise/rate reference. Whole negative-TPS movement events are then assigned the requested NORMAL CORRECTION / DECEL RELEASE semantic class at close; obvious physical mismatches are rejected but never relabelled. A region locks only after conservative 3+3 magnitude separation. Current threshold 0 remains disabled and cannot receive an automatic enable proposal.";
    }

    @Override public synchronized String reviewOutputs() {
        if (DecelHistoricalThresholdProposalContext.active()
                && lastHistoricalReview.length() > 0) {
            return lastHistoricalReview;
        }
        return reviewDirty ? defaultReviewText() : lastEvidenceReview;
    }

    @Override public String currentTuneContext(AeProjectSnapshot snapshot) {
        if (snapshot == null || !snapshot.hasDecelDetectionSettings()) {
            return "Read Working Tune to load tpsDecelThresholdRpmBins, tpsDecelThresholdValue, shared TPS detector mode and tpsDecelHoldCycles. No recommendation is available without the exact captured baseline.";
        }
        String staged = DecelHistoricalThresholdProposalContext.active()
                ? " | explicitly reviewed historical threshold evidence is staged; the current Working Tune is re-evaluated before exposing each bounded plan"
                : "";
        return "Decel detector mirror: " + snapshot.getEngagementModel()
                + " | threshold reference: 1000=" + threshold(snapshot, 1000.0)
                + ", 2000=" + threshold(snapshot, 2000.0)
                + ", 3000=" + threshold(snapshot, 3000.0)
                + ", 4000=" + threshold(snapshot, 4000.0)
                + ", 5000=" + threshold(snapshot, 5000.0)
                + " delta TPS magnitude | Decel hold " + finite(snapshot.getDecelHoldCycles())
                + " engine cycles (READ ONLY in this pass). Firmware threshold 0 means detection disabled at that RPM."
                + staged;
    }

    @Override public synchronized boolean activityObserved(LiveSample sample) {
        reviewDirty = true;
        if (sample == null) return false;
        return negative(sample, ChannelRole.AE_DELTA_MIN_STEP)
                || negative(sample, ChannelRole.AE_DELTA_TIMED_FALL)
                || negative(sample, ChannelRole.AE_DELTA_SPAN)
                || negative(sample, ChannelRole.AE_DELTA_FALL)
                || negative(sample, ChannelRole.AE_DELTA_NEWEST_FALL)
                || negative(sample, ChannelRole.DECEL_DELTA_TPS)
                || sample.bool(ChannelRole.TPS_DECEL_ACTIVE);
    }

    /**
     * Historical proposal promotion uses the existing explicit-plan slot only
     * after the operator reviewed and staged eligible evidence. No writer lives
     * here: GuidedCapturePanel still sends this immutable plan through the one
     * ProposalApplyCoordinator gateway.
     */
    @Override public synchronized ProposalWritePlan explicitSettingWritePlan(
            AeProjectSnapshot snapshot) {
        if (!DecelHistoricalThresholdProposalContext.active()) {
            lastHistoricalReview = "";
            return null;
        }
        DecelHistoricalThresholdProposal.Result result =
                DecelHistoricalThresholdProposalContext.evaluate(snapshot);
        lastHistoricalReview = result.reviewText;
        return result.plan;
    }

    @Override public synchronized ProposalWritePlan reviewedWritePlan(
            AeProjectSnapshot snapshot, List<LiveSample> evidence) {
        ChannelRole twin = DecelSignalSamples.modeTwin(snapshot);
        if (twin != null && !hasFinite(evidence, twin)) {
            lastEvidenceReview = "Recommendation WITHHELD: detector mode '"
                    + snapshot.getEngagementModel() + "' requires " + twin.getLabel()
                    + " as the falling-signal authority, but that channel is absent from this evidence. "
                    + "Fuel: TPS decel change alone is retained as fallback/context but does not receive proposal authority when the mode-specific twin is known. No ProposalWritePlan was created.";
            reviewDirty = false;
            return null;
        }

        List<LiveSample> decelEvidence = DecelSignalSamples.asPrimaryDelta(evidence, snapshot);
        DecelDetectionRecommendation.Result result =
                DecelDetectionRecommendation.evaluate(snapshot, decelEvidence);
        DecelSignalSamples.Agreement agreement =
                DecelSignalSamples.agreement(evidence, snapshot, 0.0005);
        String prefix = agreement.directUntrusted()
                ? "DIRECT decelTps CROSS-CHECK MISMATCH: proposal evidence uses the configured-mode falling twin; direct Fuel: TPS decel change is ignored for detector magnitude.\n\n"
                : "";
        lastEvidenceReview = prefix + result.reviewText;
        reviewDirty = false;
        return result.plan;
    }

    /** Final retirement clears live and historical presentation; contexts retire separately. */
    @Override public synchronized void clearLifecycleCache() {
        reviewDirty = true;
        lastEvidenceReview = defaultReviewText();
        lastHistoricalReview = "";
    }

    /** Retire historical presentation state only; live cache policy is unchanged. */
    public synchronized void clearHistoricalReview() {
        lastHistoricalReview = "";
    }

    private static String defaultReviewText() {
        return "Evidence review will show the frozen quiet calibration, NORMAL CORRECTION / DECEL RELEASE counts, rejected physical mismatches, locked falling-TPS separation, current zero/disabled cells, and threshold-only proposal eligibility. The configured-mode falling twin is detector authority; direct Fuel: TPS decel change is cross-check/fallback and Fuel: TPS AE change is context only. Before sufficient evidence exists, no ProposalWritePlan is produced. tpsDecelHoldCycles remains unchanged. No automatic Apply and no burn.";
    }

    private static boolean negative(LiveSample sample, ChannelRole role) {
        double value = sample.get(role);
        return Double.isFinite(value) && value < -0.000001;
    }

    private static boolean hasFinite(List<LiveSample> samples, ChannelRole role) {
        if (samples == null || role == null) return false;
        for (LiveSample sample : samples) {
            if (sample != null && Double.isFinite(sample.get(role))) return true;
        }
        return false;
    }

    private static String threshold(AeProjectSnapshot snapshot, double rpm) {
        return finite(snapshot.decelThresholdForRpm(rpm));
    }

    private static String finite(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%.3f", value) : "n/a";
    }
}
