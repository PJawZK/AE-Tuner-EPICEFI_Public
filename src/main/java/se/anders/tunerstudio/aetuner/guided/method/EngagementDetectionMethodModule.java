package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.EngagementDetectionWriteSelection;
import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;
import se.anders.tunerstudio.aetuner.host.AeTuningParameterCatalog;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.EngagementDetectionSettingProposal;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.List;

/** Shared TPS movement-detector evidence and direct-setting route. */
public final class EngagementDetectionMethodModule extends AbstractProbeMethodModule {
    private static final ChannelRole[] REQUIRED = new ChannelRole[]{
            ChannelRole.RPM,
            ChannelRole.TPS,
            ChannelRole.DELTA_TPS,
            ChannelRole.ACCEL_THRESHOLD,
            ChannelRole.AE_DELTA_MAX_STEP,
            ChannelRole.AE_DELTA_TIMED,
            ChannelRole.AE_DELTA_SPAN,
            ChannelRole.AE_DELTA_FLOOR,
            ChannelRole.AE_DELTA_NEWEST_PAIR,
            ChannelRole.AE_WINDOW_MS,
            ChannelRole.AE_DELTA_STRIDE
    };

    private static final ChannelRole[] CONTEXT = new ChannelRole[]{
            ChannelRole.AE_WINDOW_SAMPLES,
            ChannelRole.SMOOTHED_DELTA_TPS,
            ChannelRole.AE_ABOVE_THRESHOLD,
            ChannelRole.TPS_AE_CYCLE_CNT,
            ChannelRole.MAP_PRED_ACTIVE,
            ChannelRole.AE_ADD_MS,
            ChannelRole.WALL_WETTING_PW,
            ChannelRole.INSTANT_PULSE_PW
    };

    @Override public GuidedTuningRecipe recipe() {
        return GuidedTuningRecipe.ENGAGEMENT_DETECTION;
    }

    @Override public String setupTitle() {
        return "AE detector behavior / timing";
    }

    @Override public String setupGuidance() {
        return "Read Working Tune, then use Guided Focus as the driver-facing detector coach. Establish a baseline capture first: ordinary opening, quick stab->hold, partial lift->reapply and stacked short stabs. Watch the selected detector against AccelThreshold and compare all five detector outputs. Setting controls remain available as a secondary one-change-at-a-time A/B experiment after the baseline is reviewed.";
    }

    @Override public String captureGoal() {
        return "Build a repeatable detector-behavior baseline from varied real throttle events. Look for prompt genuine threshold crossings, fast drop-out when pedal motion stops, reversal clearing and clean fresh re-arm on reapply. After review, change one setting only and repeat the same maneuver set at similar RPM/load.";
    }

    @Override public ChannelRole[] requiredRoles() { return REQUIRED.clone(); }
    @Override public ChannelRole[] contextRoles() { return CONTEXT.clone(); }

    @Override public String operatorInputs(AeProjectSnapshot snapshot) {
        return "Read Working Tune first. Start a baseline capture and follow Guided Focus: normal moderate opening -> quick stab/hold -> partial lift/reapply -> stacked short stabs. Finish/Review before changing anything. For an A/B comparison, change ONE Detector Model / Timing setting in the secondary controls, review/apply it, Read Working Tune, then repeat the same maneuver set in similar conditions.";
    }

    @Override public String accumulationPlan() {
        return "For every coherent evidence sample, retain production TPS change, AccelThreshold and all five detector outputs: legacy max step, timed max step, window span, rise from floor and newest pair. Also retain actual AE window and stride. Live visual/audio guidance helps execute comparable events; recorded channels remain the evidence.";
    }

    @Override public String reviewOutputs() {
        return "Evidence review: selected-output/threshold separation, all-five same-event comparison, stale-positive tails, hold drop-out, reversal clearing, lift/reapply re-arm behavior, stacked-event separation, actual window/stride and channel completeness. A/B setting review is exact current -> requested diff for Engagement Model, Delta Window, Sample Length and/or Fast Callback. No automatic recommendation, no automatic Apply and no burn.";
    }

    @Override public String currentTuneContext(AeProjectSnapshot snapshot) {
        int parameterCount = AeTuningParameterCatalog.forSubsystem(
                AeTuningParameterCatalog.Subsystem.ENGAGEMENT_DETECTION).size();
        if (snapshot == null) {
            return "AE Engagement / Detection parameter family: " + parameterCount
                    + " catalogued settings. Read Working Tune to load Engagement Model, Delta Window, Sample Length, callback rate and threshold context. Guided Focus should establish evidence before A/B setting experiments; no burn.";
        }
        EngagementDetectionWriteSelection.observeWorkingTune(snapshot);
        return "AE Engagement / Detection parameter family: " + parameterCount
                + " catalogued settings. " + snapshot.engagementSettingsText()
                + ". Guided Focus uses these as the baseline for live detector coaching. Secondary setting controls create explicit operator proposals only; they are not automatic tuning recommendations.";
    }

    @Override public boolean activityObserved(LiveSample sample) {
        if (sample == null) return false;
        double threshold = sample.get(ChannelRole.ACCEL_THRESHOLD);
        if (!Double.isFinite(threshold)) {
            return positive(sample, ChannelRole.DELTA_TPS);
        }
        return above(sample, ChannelRole.AE_DELTA_MAX_STEP, threshold)
                || above(sample, ChannelRole.AE_DELTA_TIMED, threshold)
                || above(sample, ChannelRole.AE_DELTA_SPAN, threshold)
                || above(sample, ChannelRole.AE_DELTA_FLOOR, threshold)
                || above(sample, ChannelRole.AE_DELTA_NEWEST_PAIR, threshold);
    }

    @Override public ProposalWritePlan explicitSettingWritePlan(AeProjectSnapshot snapshot) {
        return selectedDetectorSettingsPlan(snapshot);
    }

    @Override public ProposalWritePlan reviewedWritePlan(AeProjectSnapshot snapshot,
                                                          List<LiveSample> evidence) {
        // No automatic evidence-derived detector recommendation yet. A pending
        // explicit operator setting remains available after an evidence capture.
        return selectedDetectorSettingsPlan(snapshot);
    }

    private ProposalWritePlan selectedDetectorSettingsPlan(AeProjectSnapshot snapshot) {
        if (snapshot == null) return null;
        EngagementDetectionWriteSelection.observeWorkingTune(snapshot);
        EngagementDetectionWriteSelection.Snapshot selection =
                EngagementDetectionWriteSelection.snapshot();
        if (!selection.hasRequestedChange()) return null;
        return EngagementDetectionSettingProposal.detectorSettings(
                snapshot,
                selection.modelBaselineAvailable ? selection.requestedEngagementModel : null,
                selection.baselineAvailable ? selection.requestedDeltaWindowMs : Double.NaN,
                selection.sampleLengthBaselineAvailable
                        ? selection.requestedSampleLengthSeconds : Double.NaN,
                selection.fastCallbackBaselineAvailable
                        ? Boolean.valueOf(selection.requestedFastCallback) : null);
    }

    private static boolean above(LiveSample sample, ChannelRole role, double threshold) {
        double value = sample.get(role);
        return Double.isFinite(value) && value > threshold;
    }
}
