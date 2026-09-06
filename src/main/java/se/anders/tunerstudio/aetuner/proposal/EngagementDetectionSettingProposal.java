package se.anders.tunerstudio.aetuner.proposal;

import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Explicit guarded working-tune proposals for TPS Movement / Timing. */
public final class EngagementDetectionSettingProposal {
    private static final double EPSILON = 0.000001;

    private EngagementDetectionSettingProposal() { }

    /**
     * Build one coherent passive Foundation timing proposal. Delta Window is
     * resolved first from measured TPS traces; Sample Length is then checked as
     * the history-capacity dependency of that selected Delta Window. Either,
     * both, or neither setting may need to change. No capture-time write and no
     * burn are implied by this plan.
     */
    public static ProposalWritePlan timingPair(AeProjectSnapshot snapshot,
                                               double requestedDeltaMs,
                                               double requestedSampleSeconds) {
        requireSnapshot(snapshot);
        List<ProposalWritePlan.Change> changes = new ArrayList<ProposalWritePlan.Change>();
        ProposalWritePlan.Change delta = deltaWindowChange(snapshot, requestedDeltaMs);
        if (delta != null) changes.add(delta);
        ProposalWritePlan.Change sample = sampleLengthChange(snapshot, requestedSampleSeconds);
        if (sample != null) changes.add(sample);
        if (changes.isEmpty()) return null;
        return new ProposalWritePlan(
                "engagement-detection-timing-pair",
                "AE Foundation — TPS Movement / Timing",
                snapshot.getConfigurationName(),
                "Passive evidence resolves 1/2 Delta Window first, then automatically advances to 2/2 Sample Length history-capacity checking. Only settings that actually need to change are declared here. Capture performs no controller writes; Apply remains explicit and guarded; no burn.",
                changes);
    }

    /** Build a guarded Delta Window plan, or null when unchanged. */
    public static ProposalWritePlan deltaWindow(AeProjectSnapshot snapshot,
                                                double requestedMs) {
        requireSnapshot(snapshot);
        ProposalWritePlan.Change change = deltaWindowChange(snapshot, requestedMs);
        if (change == null) return null;
        return new ProposalWritePlan(
                "engagement-detection-delta-window",
                "AE Foundation — Delta Window",
                snapshot.getConfigurationName(),
                "Evidence-backed passive Delta Window result. Capture performs no temporary timing write; Apply remains explicit and guarded; no burn.",
                Collections.singletonList(change));
    }

    /** Build a guarded Sample Length plan, or null when unchanged. */
    public static ProposalWritePlan sampleLength(AeProjectSnapshot snapshot,
                                                 double requestedSeconds) {
        requireSnapshot(snapshot);
        ProposalWritePlan.Change change = sampleLengthChange(snapshot, requestedSeconds);
        if (change == null) return null;
        return new ProposalWritePlan(
                "engagement-detection-sample-length",
                "AE Foundation — Sample Length",
                snapshot.getConfigurationName(),
                "Evidence-backed Sample Length history-capacity result derived after Delta Window resolution. Capture performs no temporary timing write; Apply remains explicit and guarded; no burn.",
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

    private static ProposalWritePlan.Change sampleLengthChange(
            AeProjectSnapshot snapshot, double requestedSeconds) {
        if (!snapshot.hasEngagementSampleLength()) {
            throw new IllegalArgumentException(
                    "working tune does not expose a finite AE Sample Length baseline");
        }
        if (!Double.isFinite(requestedSeconds) || requestedSeconds <= 0.0) {
            throw new IllegalArgumentException(
                    "requested AE Sample Length must be a finite positive value");
        }
        double current = snapshot.getEngagementSampleLengthSeconds();
        if (Math.abs(current - requestedSeconds) <= EPSILON) return null;
        return ProposalWritePlan.Change.scalar(
                AeParameterNames.TPS_ACCEL_LOOKBACK,
                current, requestedSeconds, "Sample Length", "s");
    }

    private static void requireSnapshot(AeProjectSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("working-tune snapshot is required");
        }
    }
}
