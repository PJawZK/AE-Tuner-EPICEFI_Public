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
        result.append("\n\nTuning task: Predictive MAP Blend Duration");
        if (state == GuidedCaptureState.IDLE) {
            result.append("\nSession setup: not armed — Start Capture uses the selected table bin and current setup controls shown above.");
        } else {
            result.append("\nCapture RPM target: ").append(f0(settings.startRpm)).append(" RPM")
                    .append(" | READY/acquire ±").append(f0(RoadBaselineTracker.RPM_ACQUIRE_TOLERANCE))
                    .append(" RPM | active capture ±").append(f0(RoadBaselineTracker.RPM_CAPTURE_TOLERANCE))
                    .append(" RPM")
                    .append(" | target TPS step: +")
                    .append(f1(settings.desiredTpsStep))
                    .append(" | accepted +").append(f1(settings.targetStepLow()))
                    .append(" to +").append(f1(settings.targetStepHigh()))
                    .append(" | gear: ").append(settings.gearText());
        }
        result.append("\nActual valid events: ").append(validCount)
                .append(" | excluded: ").append(excluded)
                .append(" | returned: ").append(returnedToBaseline)
                .append(" | attempts: ").append(attempts)
                .append("\nComparability groups: ").append(groups.summary());
        if (groups.bestGroupCount() > 0) {
            result.append("\nMeasurement group: ").append(groups.bestGroupId())
                    .append(" ").append(groups.bestGroupCount())
                    .append("/").append(settings.targetCount)
                    .append(" comparable valid events");
        }
        if (stats.count > 0) {
            result.append("\nBest-group final-target catch-up median: ")
                    .append(f3(stats.median)).append(" s | range: ")
                    .append(f3(stats.min)).append("-")
                    .append(f3(stats.max)).append(" s | width: ")
                    .append(f3(stats.range)).append(" s");
        }
        result.append("\n").append(quality(stats, groups.bestGroupCount()))
                .append("\nNumerical Blend Duration proposal/apply is intentionally withheld while the corrected firmware-faithful conversion rule is being validated.")
                .append("\n\nRead-only: Guided measurement/capture never writes ECU RAM, burns settings, or removes passive/raw events.");
        return new GuidedSessionSnapshot(state, stateName(state, plateauAcquired),
                instruction, checkText, result.toString(),
                validCount, lastAttemptTrace);
    }

    private static String quality(BlendDurationSeriesStats stats, int bestGroupCount) {
        if (bestGroupCount < 3) {
            return "Measurement-group repeatability: INCOMPLETE — valid final-target events remain retained in their groups.";
        }
        if (stats.range > 0.18 || stats.iqr > 0.10 || stats.sd > 0.08) {
            return "Measurement-group repeatability: LOW — capture remains valid, but the physical final-target duration spread is broad.";
        }
        if (bestGroupCount >= 5 && stats.range <= 0.10
                && stats.iqr <= 0.05 && stats.sd <= 0.04) {
            return "Measurement-group repeatability: HIGH — the current best group is tightly repeatable.";
        }
        return "Measurement-group repeatability: MEDIUM — the current best group is sufficiently consistent for model review.";
    }

    private static String stateName(GuidedCaptureState state,
                                    boolean plateauAcquired) {
        switch (state) {
            case SETTLING: return "ESTABLISHING ROAD BASELINE";
            case READY: return "READY — MAKE ONE CONTROLLED OPENING";
            case OPENING_PENDING: return "OPENING PENDING";
            case CAPTURING:
                return plateauAcquired
                        ? "TARGET TPS HOLD ACQUIRED — HOLD"
                        : "OPENING — SETTLE INSIDE TARGET STEP";
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
