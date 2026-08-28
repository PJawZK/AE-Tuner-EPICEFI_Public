package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Small routing contract for one Guided AE method.
 *
 * Shared Swing layout belongs to the Guided workspace. Method-specific channel
 * selection, activation evidence, operator guidance, tuning math and proposal
 * generation live behind this boundary so a failing method can be isolated
 * without contaminating the others.
 *
 * Guarded working-tune Apply is a normal part of the contract from the start.
 * There are two proposal sources:
 * - evidence-derived proposals, which require the method's capture/review rules;
 * - explicit setting proposals, which are operator-selected working-tune changes
 *   and therefore do not require fake/live capture just to unlock Apply.
 * Both terminate in the same ProposalApplyCoordinator safety gateway. Burn is
 * outside this contract entirely.
 */
public interface GuidedAeMethodModule {
    enum CaptureMode {
        BLEND_DURATION,
        READ_ONLY_PROBE,
        ARCHITECTURE_ONLY
    }

    GuidedTuningRecipe recipe();
    CaptureMode captureMode();
    String setupTitle();
    String setupGuidance();
    String captureGoal();

    /** Channels that must be present for this method's evidence to be trustworthy. */
    ChannelRole[] requiredRoles();

    /** Useful attribution/context channels. Missing context is reported but is not a hard capture prerequisite. */
    ChannelRole[] contextRoles();

    /**
     * Complete capture subscription/export role list, preserving required roles
     * first and then unique context roles.
     */
    default ChannelRole[] probeRoles() {
        Set<ChannelRole> roles = new LinkedHashSet<ChannelRole>();
        ChannelRole[] required = requiredRoles();
        ChannelRole[] context = contextRoles();
        if (required != null) {
            for (ChannelRole role : required) if (role != null) roles.add(role);
        }
        if (context != null) {
            for (ChannelRole role : context) if (role != null) roles.add(role);
        }
        return roles.toArray(new ChannelRole[roles.size()]);
    }

    /** What the operator chooses/controls before capture. */
    String operatorInputs(AeProjectSnapshot snapshot);

    /** Exact evidence accumulation rules the user should satisfy. */
    String accumulationPlan();

    /** User-visible outputs expected from a useful completed capture. */
    String reviewOutputs();

    /** Current tune context that matters to this method. */
    String currentTuneContext(AeProjectSnapshot snapshot);

    boolean activityObserved(LiveSample sample);

    /**
     * Return an explicit operator-selected working-tune change, or null when no
     * direct setting change is selected. This route is intentionally independent
     * of capture state: Read Working Tune -> Review Change -> Apply/Restore.
     */
    default ProposalWritePlan explicitSettingWritePlan(AeProjectSnapshot snapshot) {
        return null;
    }

    /**
     * Return the exact evidence-derived working-tune change for a completed
     * capture, or null when the current evidence/tuning math does not call for a
     * change. The returned plan does not write anything by itself.
     */
    default ProposalWritePlan reviewedWritePlan(AeProjectSnapshot snapshot,
                                                 List<LiveSample> evidence) {
        return null;
    }
}
