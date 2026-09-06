package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.List;
import java.util.Locale;

/** Evidence route plus conservative static-threshold recommendation for AE Foundation. */
public final class FoundationThresholdMethodModule extends AbstractProbeMethodModule {
    private static final ChannelRole[] REQUIRED = new ChannelRole[]{
            ChannelRole.RPM, ChannelRole.TPS, ChannelRole.DELTA_TPS, ChannelRole.ACCEL_THRESHOLD
    };
    private static final ChannelRole[] CONTEXT = new ChannelRole[]{
            ChannelRole.SMOOTHED_DELTA_TPS, ChannelRole.AE_ABOVE_THRESHOLD,
            ChannelRole.AE_DELTA_NEWEST_PAIR, ChannelRole.AE_WINDOW_MS,
            ChannelRole.AE_WINDOW_SAMPLES, ChannelRole.AE_DELTA_STRIDE,
            ChannelRole.TPS_AE_CYCLE_CNT, ChannelRole.MAP_PRED_ACTIVE, ChannelRole.AE_ADD_MS
    };

    private volatile boolean reviewDirty = true;
    private volatile String lastEvidenceReview = defaultReviewText();

    @Override public GuidedTuningRecipe recipe() { return GuidedTuningRecipe.FOUNDATION_THRESHOLD; }
    @Override public String setupTitle() { return "Threshold / sensitivity"; }

    @Override public String setupGuidance() {
        return "Read Working Tune, then let the quiet TPS-rate calibration lock before maneuver evidence begins. Guided Focus owns the event meaning: in each broad RPM region it first asks for a Normal Correction — a small natural pedal adjustment that should not request acceleration — then an Acceleration Opening — a clear normal quick throttle opening intended to accelerate. Event shape may reject an obvious mismatch but peak TPS rate never relabels accepted intent. Clean effective-space evidence locks permanently for the capture; dynamic/smoothing controls remain operator-reviewed settings.";
    }

    @Override public String captureGoal() {
        return "Freeze one quiet calibration first, then follow the live NORMAL CORRECTION / ACCELERATION OPENING phase shown for the current broad RPM region. Collect at least three accepted events of each physical class. Once their measured Fuel: TPS AE change distributions separate conservatively, that effective-space PASS locks and cannot regress because of later driving.";
    }

    @Override public ChannelRole[] requiredRoles() { return REQUIRED.clone(); }
    @Override public ChannelRole[] contextRoles() { return CONTEXT.clone(); }

    @Override public String operatorInputs(AeProjectSnapshot snapshot) {
        FoundationThresholdFocusBridge.observeWorkingTune(snapshot);
        return "Use one unchanged working tune. Begin with a steady pedal until Guided Focus says the quiet calibration is LOCKED; lock requires at least 80 qualifying quiet samples and 1.5 continuous seconds of quiet data. Then obey the phase shown for the broad RPM region you are currently driving in. NORMAL CORRECTION = make a small natural pedal adjustment without trying to accelerate. ACCELERATION OPENING = make a clear normal quick throttle opening to accelerate; do not slowly roll into the pedal. You do not need to hold an exact curve-bin RPM. Do not change threshold, Dynamic Threshold or smoothing settings during capture.";
    }

    @Override public String accumulationPlan() {
        return "Retain RPM, TPS, production Fuel: TPS AE change and live AccelThreshold for the whole session. At least 80 qualifying quiet samples spanning 1.5 continuous seconds freeze p95/p99 and movement/shape-rate references; later quiet driving cannot move them. Whole physical opening events are then assigned the current Guided Focus Normal Correction / Acceleration Opening phase at close, with obvious shape mismatches rejected. Each broad RPM region locks after conservative 3+3 effective-space separation; static proposal eligibility is evaluated separately from that measured PASS.";
    }

    @Override public synchronized String reviewOutputs() { return reviewDirty ? defaultReviewText() : lastEvidenceReview; }

    @Override public String currentTuneContext(AeProjectSnapshot snapshot) {
        FoundationThresholdFocusBridge.observeWorkingTune(snapshot);
        if (snapshot == null) {
            return "Read Working Tune to load the TPS AE Rate of change vs RPM threshold curve, dynamic-threshold state, static/dynamic averaging state and Delta TPS smoothing alpha.";
        }
        return "Dynamic threshold " + enabled(snapshot.isDynamicThresholdEnabled())
                + " | static/dynamic averaging " + enabled(snapshot.isDynamicThresholdAverageStatic())
                + " | Delta TPS smoothing alpha " + finite(snapshot.getDeltaTpsAverageAlpha())
                + " | threshold reference: 1000=" + threshold(snapshot, 1000.0)
                + ", 2000=" + threshold(snapshot, 2000.0)
                + ", 3000=" + threshold(snapshot, 3000.0)
                + ", 4000=" + threshold(snapshot, 4000.0)
                + ", 5000=" + threshold(snapshot, 5000.0)
                + " delta TPS. Automatic static-curve authority follows firmware exactly: Dynamic OFF is direct; Dynamic ON with static/dynamic averaging ON is solved through Effective=(Static+Dynamic)/2; Dynamic ON with averaging OFF gives the static curve zero runtime authority. All validated task settings remain available through Edit/Review Task Settings.";
    }

    @Override public synchronized boolean activityObserved(LiveSample sample) {
        reviewDirty = true;
        FoundationThresholdFocusBridge.observe(sample);
        if (sample == null) return false;
        double delta = sample.get(ChannelRole.DELTA_TPS);
        double threshold = sample.get(ChannelRole.ACCEL_THRESHOLD);
        return Double.isFinite(delta) && Double.isFinite(threshold) && delta > threshold;
    }

    @Override public synchronized ProposalWritePlan reviewedWritePlan(AeProjectSnapshot snapshot, List<LiveSample> evidence) {
        FoundationThresholdFocusBridge.replaceEvidence(snapshot, evidence);
        FoundationThresholdRecommendation.Result result = FoundationThresholdRecommendation.evaluate(snapshot, evidence);
        lastEvidenceReview = result.reviewText;
        reviewDirty = false;
        return result.plan;
    }

    private static String defaultReviewText() {
        return "Evidence review will show frozen quiet calibration, guided Normal Correction / Acceleration Opening event counts, rejected physical-shape mismatches, locked effective-space separation and separate static Dynamic/inversion authority. Before sufficient locked evidence exists, no ProposalWritePlan is produced. When eligible, only evidence-backed static tpsAeThresholdValue bins may be proposed; no automatic Apply and no burn.";
    }

    private static String threshold(AeProjectSnapshot snapshot, double rpm) { return finite(snapshot.recommendThresholdForRpm(rpm)); }
    private static String finite(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.3f", value) : "n/a"; }
}
