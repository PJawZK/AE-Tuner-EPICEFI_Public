package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.List;
import java.util.Locale;

/** Pure snapshot/status presentation for the Blend Duration Guided workflow. */
final class BlendDurationGuidedSummary {
    private BlendDurationGuidedSummary() { }

    static GuidedSessionSnapshot snapshot(GuidedCaptureState state,
                                          boolean plateauAcquired,
                                          String instruction,
                                          String checkText,
                                          String latestResult,
                                          BlendDurationCaptureConfig settings,
                                          int validCount,
                                          int excluded,
                                          int returnedToBaseline,
                                          int attempts,
                                          BlendDurationComparabilityGroups groups,
                                          String lastAttemptTrace) {
        List<BlendDurationAttempt> best = groups.bestAttempts();
        BlendDurationSeriesStats stats = BlendDurationSeriesStats.from(best);
        StringBuilder result = new StringBuilder(latestResult);
        result.append("\n\nRecipe: Adaptive Predictive MAP Blend Duration")
                .append("\nRoad RPM region: ").append(f0(settings.startRpm))
                .append(" ±").append(f0(RoadBaselineTracker.RPM_ACQUIRE_TOLERANCE))
                .append(" acquire / ±").append(f0(RoadBaselineTracker.RPM_READY_RELEASE_TOLERANCE))
                .append(" READY retain")
                .append(" | desired TPS step: +")
                .append(f1(settings.desiredTpsStep)).append(" points (advisory)")
                .append(" | gear: ").append(settings.gearText())
                .append("\nActual valid events: ").append(validCount)
                .append(" | excluded: ").append(excluded)
                .append(" | returned: ").append(returnedToBaseline)
                .append(" | attempts: ").append(attempts)
                .append("\nComparability groups: ").append(groups.summary());
        if (groups.bestGroupCount() > 0) {
            result.append("\nProposal group: ").append(groups.bestGroupId())
                    .append(" ").append(groups.bestGroupCount())
                    .append("/").append(settings.targetCount)
                    .append(" comparable valid events");
        }
        if (stats.count > 0) {
            result.append("\nBest-group duration median: ")
                    .append(f3(stats.median)).append(" s | range: ")
                    .append(f3(stats.min)).append("-")
                    .append(f3(stats.max)).append(" s | width: ")
                    .append(f3(stats.range)).append(" s");
        }
        result.append("\n").append(quality(stats, groups.bestGroupCount()))
                .append("\n\nRead-only: adaptive Guided Capture never writes ECU RAM, burns settings, or removes passive/raw events.");
        return new GuidedSessionSnapshot(state, stateName(state, plateauAcquired),
                instruction, checkText, result.toString(),
                validCount, lastAttemptTrace);
    }

    private static String quality(BlendDurationSeriesStats stats, int bestGroupCount) {
        if (bestGroupCount < 3) {
            return "Proposal-group quality: INCOMPLETE — valid events remain retained in their groups.";
        }
        if (stats.range > 0.18 || stats.iqr > 0.10 || stats.sd > 0.08) {
            return "Proposal-group quality: LOW — capture remains valid, but duration spread withholds a proposal.";
        }
        if (bestGroupCount >= 5 && stats.range <= 0.10
                && stats.iqr <= 0.05 && stats.sd <= 0.04) {
            return "Proposal-group quality: HIGH — current best group meets the existing high-confidence spread limits.";
        }
        return "Proposal-group quality: MEDIUM — current best group meets the existing proposal spread limits.";
    }

    private static String stateName(GuidedCaptureState state,
                                    boolean plateauAcquired) {
        switch (state) {
            case SETTLING: return "ESTABLISHING ROAD BASELINE";
            case READY: return "READY — MAKE ONE MODERATE OPENING";
            case OPENING_PENDING: return "OPENING PENDING";
            case CAPTURING:
                return plateauAcquired
                        ? "PEDAL HOLD ACQUIRED — HOLD"
                        : "OPENING — LET PEDAL SETTLE";
            case ACCEPTED:
            case WARNING:
                return "VALID EVENT — RETURN TO NORMAL THROTTLE";
            case EXCLUDED: return "EVENT EXCLUDED";
            case RETURNING: return "RETURN TO NORMAL THROTTLE";
            case RECOVERING: return "RECOVERING ROAD BASELINE";
            case PAUSED: return "PAUSED";
            case COMPLETE: return "SERIES COMPLETE";
            default: return "IDLE";
        }
    }

    private static String f0(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.US, "%.0f", value) : "n/a";
    }

    private static String f1(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.US, "%.1f", value) : "n/a";
    }

    private static String f3(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.US, "%.3f", value) : "n/a";
    }
}
