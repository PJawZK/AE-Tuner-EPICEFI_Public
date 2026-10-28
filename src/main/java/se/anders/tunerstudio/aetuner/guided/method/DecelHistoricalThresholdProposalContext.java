package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

/**
 * Small in-memory handoff for operator-reviewed historical Decel proposal evidence.
 *
 * This context owns no controller access. It can only expose an immutable
 * ProposalWritePlan to the existing Guided production path after the operator
 * explicitly staged eligible historical evidence.
 */
public final class DecelHistoricalThresholdProposalContext {
    private static DecelHistoricalThresholdProposal.Evidence stagedEvidence;
    private static String stagedConfigurationName;
    private static long revision;

    /** Cache invalidation token; no proposal or controller authority. */
    public static synchronized long revision() { return revision; }
    private static String lastStatus = "No historical Decel threshold proposal is staged.";

    private DecelHistoricalThresholdProposalContext() { }

    public static synchronized DecelHistoricalThresholdProposal.Result stage(
            DecelHistoricalThresholdProposal.Eligibility eligibility,
            AeProjectSnapshot snapshot) {
        revision++;
        if (eligibility == null || !eligibility.promotable || eligibility.evidence == null
                || snapshot == null || snapshot.getConfigurationName() == null) {
            stagedEvidence = null;
            stagedConfigurationName = null;
            lastStatus = eligibility == null
                    ? "Historical Decel proposal staging failed: no reviewed eligibility result."
                    : eligibility.reviewText;
            return DecelHistoricalThresholdProposal.evaluate(snapshot, null);
        }
        DecelHistoricalThresholdProposal.Result result =
                DecelHistoricalThresholdProposal.evaluate(snapshot, eligibility.evidence);
        if (result.state != DecelHistoricalThresholdProposal.State.PLAN_READY
                || result.plan == null) {
            stagedEvidence = null;
            stagedConfigurationName = null;
            lastStatus = result.reviewText;
            return result;
        }
        stagedEvidence = eligibility.evidence;
        stagedConfigurationName = snapshot.getConfigurationName();
        lastStatus = "STAGED / REVIEWED — historical Decel threshold evidence is now eligible to feed only the existing guarded production proposal path for controller configuration '"
                + stagedConfigurationName
                + "'. No controller write has occurred.\n\n" + result.reviewText;
        return result;
    }

    public static synchronized boolean active() {
        return stagedEvidence != null;
    }

    public static synchronized DecelHistoricalThresholdProposal.Result evaluate(
            AeProjectSnapshot snapshot) {
        if (stagedEvidence == null) {
            return DecelHistoricalThresholdProposal.evaluate(snapshot, null);
        }
        if (snapshot == null || snapshot.getConfigurationName() == null
                || stagedConfigurationName == null
                || !stagedConfigurationName.equals(snapshot.getConfigurationName())) {
            stagedEvidence = null;
            stagedConfigurationName = null;
            revision++;
            lastStatus = "Historical Decel proposal authority was discarded because the Working Tune controller configuration changed after review.";
            return DecelHistoricalThresholdProposal.evaluate(snapshot, null);
        }
        DecelHistoricalThresholdProposal.Result result =
                DecelHistoricalThresholdProposal.evaluate(snapshot, stagedEvidence);
        if (!result.reviewText.equals(lastStatus)) revision++;
        lastStatus = result.reviewText;
        return result;
    }

    public static synchronized ProposalWritePlan planFor(AeProjectSnapshot snapshot) {
        DecelHistoricalThresholdProposal.Result result = evaluate(snapshot);
        return result.plan;
    }

    public static synchronized String statusFor(AeProjectSnapshot snapshot) {
        if (stagedEvidence != null) evaluate(snapshot);
        return lastStatus;
    }

    public static synchronized void clear() {
        revision++;
        stagedEvidence = null;
        stagedConfigurationName = null;
        lastStatus = "No historical Decel threshold proposal is staged.";
    }
}
