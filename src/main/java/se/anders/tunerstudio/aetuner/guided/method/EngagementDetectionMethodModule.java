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

/** TPS movement / threshold timing evidence and Delta Window A/B route. */
public final class EngagementDetectionMethodModule extends AbstractProbeMethodModule {
    private static final ChannelRole[] REQUIRED = new ChannelRole[]{
            ChannelRole.RPM,
            ChannelRole.TPS,
            ChannelRole.DELTA_TPS,
            ChannelRole.ACCEL_THRESHOLD,
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

    @Override public GuidedTuningRecipe recipe() { return GuidedTuningRecipe.ENGAGEMENT_DETECTION; }

    @Override public String setupTitle() { return "TPS movement / timing"; }

    @Override public String setupGuidance() {
        return "Read Working Tune, then use Guided Focus as a driver coach for TPS movement -> Fuel: TPS AE change -> AccelThreshold. Dual Stride / Newest is expected read-only controller context. Sample Length and Fast Callback are informational here; AE Tuner does not tune them. Establish a baseline before considering a Delta Window A/B experiment.";
    }

    @Override public String captureGoal() {
        return "Build repeatable TPS-movement timing evidence from ordinary openings, quick stab/hold, partial lift/reapply and stacked short stabs. Look for prompt intentional threshold crossings, quick clear when pedal movement stops and clean fresh re-arm on reapply.";
    }

    @Override public ChannelRole[] requiredRoles() { return REQUIRED.clone(); }
    @Override public ChannelRole[] contextRoles() { return CONTEXT.clone(); }

    @Override public String operatorInputs(AeProjectSnapshot snapshot) {
        return "Start a baseline capture and follow Guided Focus. Finish/Review before changing anything. If timing evidence justifies an A/B experiment, change Delta Window only, Apply/readback, Read Working Tune, then repeat the same maneuver set in similar conditions.";
    }

    @Override public String accumulationPlan() {
        return "Retain TPS, production Fuel: TPS AE change, AccelThreshold, Dual Stride/Newest diagnostic output, actual AE window and stride. The production detected TPS change is the coached signal; the newest-pair diagnostic is a sanity/verification channel, not a competing user-selectable algorithm.";
    }

    @Override public String reviewOutputs() {
        return "Evidence review: TPS-movement onset, detected-change/threshold separation, trigger timing, hold drop-out, reversal clearing, lift/reapply re-arm, stacked-event separation, actual window/stride and channel completeness. Delta Window may be tested through exact baseline -> one change -> repeated maneuver A/B. No automatic recommendation, no automatic Apply and no burn.";
    }

    @Override public String currentTuneContext(AeProjectSnapshot snapshot) {
        int parameterCount = AeTuningParameterCatalog.forSubsystem(
                AeTuningParameterCatalog.Subsystem.ENGAGEMENT_DETECTION).size();
        if (snapshot == null) {
            return "AE Foundation detector/timing family: " + parameterCount
                    + " catalogued settings. Read Working Tune to load detector mode, Delta Window, Sample Length, Fast Callback and threshold context. Only Delta Window is currently an AE Tuner A/B edit target.";
        }
        EngagementDetectionWriteSelection.observeWorkingTune(snapshot);
        return "AE Foundation detector/timing family: " + parameterCount
                + " catalogued settings. " + snapshot.engagementSettingsText()
                + ". Engagement Model, Sample Length and Fast Callback are read-only context; Delta Window is the current guarded A/B setting.";
    }

    @Override public boolean activityObserved(LiveSample sample) {
        if (sample == null) return false;
        double delta = sample.get(ChannelRole.DELTA_TPS);
        double threshold = sample.get(ChannelRole.ACCEL_THRESHOLD);
        if (!Double.isFinite(delta)) return false;
        if (!Double.isFinite(threshold)) return delta > 0.0;
        return delta > threshold;
    }

    @Override public ProposalWritePlan explicitSettingWritePlan(AeProjectSnapshot snapshot) {
        return selectedDeltaWindowPlan(snapshot);
    }

    @Override public ProposalWritePlan reviewedWritePlan(AeProjectSnapshot snapshot,
                                                          List<LiveSample> evidence) {
        // No automatic evidence-derived recommendation yet. Only an explicit
        // operator-selected Delta Window A/B proposal may be returned.
        return selectedDeltaWindowPlan(snapshot);
    }

    private ProposalWritePlan selectedDeltaWindowPlan(AeProjectSnapshot snapshot) {
        if (snapshot == null) return null;
        EngagementDetectionWriteSelection.observeWorkingTune(snapshot);
        EngagementDetectionWriteSelection.Snapshot selection =
                EngagementDetectionWriteSelection.snapshot();
        if (!selection.hasRequestedDeltaWindowChange()) return null;
        return EngagementDetectionSettingProposal.deltaWindow(
                snapshot, selection.requestedDeltaWindowMs);
    }
}
