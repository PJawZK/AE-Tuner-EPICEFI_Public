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
            ChannelRole.RPM, ChannelRole.TPS, ChannelRole.DELTA_TPS,
            ChannelRole.TPS_DECEL_ACTIVE
    };
    private static final ChannelRole[] CONTEXT = new ChannelRole[]{
            ChannelRole.SMOOTHED_DELTA_TPS, ChannelRole.ACCEL_THRESHOLD,
            ChannelRole.AE_ABOVE_THRESHOLD, ChannelRole.DFCO, ChannelRole.FUEL_CUT,
            ChannelRole.MAP_PRED_ACTIVE, ChannelRole.AE_ADD_MS
    };

    private volatile boolean reviewDirty = true;
    private volatile String lastEvidenceReview = defaultReviewText();

    @Override public GuidedTuningRecipe recipe() { return GuidedTuningRecipe.DECEL_DETECTION; }
    @Override public String setupTitle() { return "Decel Detection / Threshold"; }

    @Override public String setupGuidance() {
        return "Read Working Tune, begin with a steady pedal until the quiet falling-TPS calibration locks, then follow Guided Focus through two physical closing-event classes in each broad RPM region. NORMAL CORRECTION is a small natural pedal reduction that should not intentionally enter overrun. DECEL RELEASE is a clear normal throttle lift. The recommendation may change only evidence-backed tpsDecelThresholdValue cells. A threshold of 0 is a firmware disable command and is never auto-enabled. Decel hold remains read-only in this pass.";
    }

    @Override public String captureGoal() {
        return "Freeze one quiet TPS-rate baseline, then collect at least three accepted NORMAL CORRECTION and three accepted DECEL RELEASE events in each representative RPM region. Lock a region only when falling-TPS magnitudes separate conservatively. Fuel: TPS Decel Active is retained as firmware-state evidence, but hold-cycle persistence is diagnostic and does not redefine the triggering edge.";
    }

    @Override public ChannelRole[] requiredRoles() { return REQUIRED.clone(); }
    @Override public ChannelRole[] contextRoles() { return CONTEXT.clone(); }

    @Override public String operatorInputs(AeProjectSnapshot snapshot) {
        return "Use one unchanged Working Tune. Hold the pedal steady until the quiet calibration is LOCKED. Then obey the current broad-RPM phase: NORMAL CORRECTION = a small natural pedal reduction without intentionally entering overrun; DECEL RELEASE = a clear normal lift, not a slow easing-out. Repeat comparable events. Do not change the decel threshold curve or tpsDecelHoldCycles during capture. Hold cycles are deliberately read-only until threshold behavior is validated independently.";
    }

    @Override public String accumulationPlan() {
        return "Retain RPM, TPS, signed Fuel: TPS AE change, Fuel: TPS Decel Active and closing TPS rate for the whole session. At least 80 qualifying quiet samples spanning 1.5 continuous seconds freeze the noise/rate reference. Whole negative-TPS movement events are then assigned the requested NORMAL CORRECTION / DECEL RELEASE semantic class at close; obvious physical mismatches are rejected but never relabelled. A region locks only after conservative 3+3 magnitude separation. Current threshold 0 remains disabled and cannot receive an automatic enable proposal.";
    }

    @Override public synchronized String reviewOutputs() {
        return reviewDirty ? defaultReviewText() : lastEvidenceReview;
    }

    @Override public String currentTuneContext(AeProjectSnapshot snapshot) {
        if (snapshot == null || !snapshot.hasDecelDetectionSettings()) {
            return "Read Working Tune to load tpsDecelThresholdRpmBins, tpsDecelThresholdValue and tpsDecelHoldCycles. No recommendation is available without the exact captured baseline.";
        }
        return "Decel threshold reference: 1000=" + threshold(snapshot, 1000.0)
                + ", 2000=" + threshold(snapshot, 2000.0)
                + ", 3000=" + threshold(snapshot, 3000.0)
                + ", 4000=" + threshold(snapshot, 4000.0)
                + ", 5000=" + threshold(snapshot, 5000.0)
                + " delta TPS magnitude | Decel hold " + finite(snapshot.getDecelHoldCycles())
                + " engine cycles (READ ONLY in this pass). Firmware threshold 0 means detection disabled at that RPM.";
    }

    @Override public synchronized boolean activityObserved(LiveSample sample) {
        reviewDirty = true;
        if (sample == null) return false;
        double delta = sample.get(ChannelRole.DELTA_TPS);
        return (Double.isFinite(delta) && delta < -0.000001)
                || sample.bool(ChannelRole.TPS_DECEL_ACTIVE);
    }

    @Override public synchronized ProposalWritePlan reviewedWritePlan(
            AeProjectSnapshot snapshot, List<LiveSample> evidence) {
        DecelDetectionRecommendation.Result result =
                DecelDetectionRecommendation.evaluate(snapshot, evidence);
        lastEvidenceReview = result.reviewText;
        reviewDirty = false;
        return result.plan;
    }

    private static String defaultReviewText() {
        return "Evidence review will show the frozen quiet calibration, NORMAL CORRECTION / DECEL RELEASE counts, rejected physical mismatches, locked falling-TPS separation, current zero/disabled cells, and threshold-only proposal eligibility. Before sufficient evidence exists, no ProposalWritePlan is produced. tpsDecelHoldCycles remains unchanged. No automatic Apply and no burn.";
    }

    private static String threshold(AeProjectSnapshot snapshot, double rpm) {
        return finite(snapshot.decelThresholdForRpm(rpm));
    }

    private static String finite(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%.3f", value) : "n/a";
    }
}
