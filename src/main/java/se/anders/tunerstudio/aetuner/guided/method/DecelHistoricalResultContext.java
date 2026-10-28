package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;

/**
 * Read-only handoff for the latest historical Decel result. This context owns no
 * controller access and is intentionally separate from proposal/write authority.
 */
public final class DecelHistoricalResultContext {
    private static DecelHistoricalResultModel latest;
    private static double[] targetedRpmBins = new double[0];

    private DecelHistoricalResultContext() { }

    public static synchronized void publish(DecelHistoricalResultModel result) {
        latest = result;
        targetedRpmBins = result == null ? new double[0] : result.missingRpmBins.clone();
    }

    public static synchronized DecelHistoricalResultModel latest() {
        return latest;
    }

    public static synchronized boolean available() {
        return latest != null;
    }

    public static synchronized boolean reviewReady() {
        return latest != null && latest.totalBins > 0;
    }

    public static synchronized boolean proposalAvailable() {
        return latest != null && latest.proposalAvailable;
    }

    public static synchronized boolean targetedCaptureUseful() {
        return latest != null && latest.targetedCaptureUseful
                && targetedRpmBins.length > 0;
    }

    public static synchronized double[] targetedRpmBins() {
        return targetedRpmBins.clone();
    }

    public static synchronized String targetedRpmText() {
        if (targetedRpmBins.length == 0) return "none";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < targetedRpmBins.length; i++) {
            if (i > 0) out.append(" / ");
            out.append(Math.round(targetedRpmBins[i]));
        }
        return out.toString();
    }

    /** Re-evaluate retained historical proposal evidence after the controller was freshly read. */
    public static synchronized void refreshAfterWorkingTune(AeProjectSnapshot snapshot) {
        if (latest == null || !DecelHistoricalThresholdProposalContext.active()) return;
        DecelHistoricalThresholdProposal.Result proposal =
                DecelHistoricalThresholdProposalContext.evaluate(snapshot);
        DecelHistoricalResultModel refreshed =
                DecelHistoricalResultModel.refreshAfterWorkingTune(latest, proposal);
        if (refreshed != null) {
            latest = refreshed;
            targetedRpmBins = refreshed.missingRpmBins.clone();
        }
    }

    public static synchronized void clearTargetedCapture() {
        targetedRpmBins = new double[0];
    }

    public static synchronized void clear() {
        latest = null;
        targetedRpmBins = new double[0];
    }
}
