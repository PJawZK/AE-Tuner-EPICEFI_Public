package se.anders.tunerstudio.aetuner.proposal;

import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;

import java.util.Collections;

/** Explicit operator-selected working-tune proposal for TPS Movement / Timing. */
public final class EngagementDetectionSettingProposal {
    private static final double EPSILON = 0.000001;

    private EngagementDetectionSettingProposal() { }

    /**
     * Build a manual Delta Window plan, or null when unchanged.
     *
     * Delta Window is the only AE Foundation timing setting currently exposed
     * for guarded A/B changes. Engagement Model, Sample Length and Fast
     * Callback are intentionally read-only context in AE Tuner.
     */
    public static ProposalWritePlan deltaWindow(AeProjectSnapshot snapshot,
                                                double requestedMs) {
        requireSnapshot(snapshot);
        ProposalWritePlan.Change change = deltaWindowChange(snapshot, requestedMs);
        if (change == null) return null;
        return new ProposalWritePlan(
                "engagement-detection-delta-window",
                "AE Foundation — Delta Window",
                snapshot.getConfigurationName(),
                "Explicit operator-selected Delta Window A/B experiment. The scalar representation has been physically qualified through Apply/readback/Restore. The requested value is not an automatic tuning recommendation.",
                Collections.singletonList(change));
    }

    private static ProposalWritePlan.Change deltaWindowChange(
            AeProjectSnapshot snapshot, double requestedMs) {
        if (!snapshot.hasEngagementDeltaWindow()) {
            throw new IllegalArgumentException(
                    "working tune does not expose a finite AE Delta Window baseline");
        }
        if (!Double.isFinite(requestedMs) || requestedMs <= 0.0) {
            throw new IllegalArgumentException(
                    "requested AE Delta Window must be a finite positive value");
        }
        double current = snapshot.getEngagementDeltaWindowMs();
        if (Math.abs(current - requestedMs) <= EPSILON) return null;
        return ProposalWritePlan.Change.scalar(
                AeParameterNames.TPS_AE_DELTA_WINDOW_MS,
                current, requestedMs, "Delta Window", "ms");
    }

    private static void requireSnapshot(AeProjectSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("working-tune snapshot is required");
        }
    }
}
