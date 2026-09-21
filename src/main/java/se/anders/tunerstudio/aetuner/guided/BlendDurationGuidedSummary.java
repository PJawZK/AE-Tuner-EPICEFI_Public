package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
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
        boolean completedTargetGroup = state == GuidedCaptureState.COMPLETE
                && groups.targetsReached(settings.armedRpmBins(), settings.targetCount);
        StringBuilder result = new StringBuilder();
        if (completedTargetGroup && settings.armedRpmBinCount() == 1 && stats.count > 0) {
            result.append("SERIES RESULT — GROUP ").append(groups.bestGroupId())
                    .append(" MEDIAN")
                    .append("\nPhysical MAP 20→80 response: ")
                    .append(f3(stats.median)).append(" s")
                    .append("\nComparable valid events: ").append(stats.count)
                    .append(" | range: ").append(f3(stats.min)).append("-")
                    .append(f3(stats.max)).append(" s")
                    .append("\nThe completed-series result is the median of the comparable group; the most recent individual event is not promoted to the series result.");
        } else if (completedTargetGroup) {
            result.append("SERIES RESULT — ALL ARMED RPM BINS COMPLETE")
                    .append("\nPer-bin comparable-event progress: ")
                    .append(groups.binProgress(settings.armedRpmBins(), settings.targetCount))
                    .append("\nEach RPM bin remains an independent comparison cohort; durations from different table bins are never merged into one tuning result.");
        } else {
            result.append(latestResult);
        }
        result.append("\n\nTuning task: Predictive MAP Blend Duration");
        if (state == GuidedCaptureState.IDLE) {
            result.append("\nSession setup: not armed — select 1-4 actual Blend Duration table bins in the Capture card.");
        } else {
            result.append("\nArmed Blend Duration RPM bins: ").append(settings.armedRpmText()).append(" RPM")
                    .append(" | auto-latch dwell ~")
                    .append(f2(BlendDurationRpmBinLatch.LATCH_DWELL_SECONDS)).append(" s")
                    .append(" | entry/READY ±").append(f0(BlendDurationGuidedSession.ENTRY_RPM_TOLERANCE))
                    .append(" RPM | no post-start RPM ceiling")
                    .append(Double.isFinite(settings.startRpm)
                        ? " | current candidate/latch " + f0(settings.startRpm) + " RPM" : " | waiting for candidate")
                    .append(settings.armedRpmBinCount() == 1 && Double.isFinite(settings.startRpm)
                        ? "\nCapture RPM entry target: " + f0(settings.startRpm) + " RPM"
                        : "")
                    .append(" | suggested TPS step: +")
                    .append(f1(settings.desiredTpsStep)).append(" (coaching only)")
                    .append(" | physically usable +").append(f1(settings.usableStepLow()))
                    .append(" to +").append(f1(settings.usableStepHigh()))
                    .append(" | comparable-group TPS spread ≤")
                    .append(f1(BlendDurationComparabilityGroups.TPS_STEP_LIMIT))
                    .append(" | gear: ").append(settings.gearText());
        }
        result.append("\nActual valid events: ").append(validCount)
                .append(" | excluded: ").append(excluded)
                .append(" | returned: ").append(returnedToBaseline)
                .append(" | attempts: ").append(attempts)
                .append("\nComparability groups: ").append(groups.summary());
        result.append("\nArmed-bin progress: ")
                .append(groups.binProgress(settings.armedRpmBins(), settings.targetCount));
        if (groups.bestGroupCount() > 0) {
            if (settings.armedRpmBinCount() == 1) {
                result.append("\nMeasurement group: ").append(groups.bestGroupId())
                        .append(" ").append(groups.bestGroupCount())
                        .append("/").append(settings.targetCount)
                        .append(" comparable valid events");
            } else {
                result.append("\nLargest measurement group: ").append(groups.bestGroupId())
                        .append(" ").append(groups.bestGroupCount())
                        .append(" comparable valid events");
            }
        }
        if (stats.count > 0) {
            result.append("\nBest-group physical MAP 20→80 response median: ")
                    .append(f3(stats.median)).append(" s | range: ")
                    .append(f3(stats.min)).append("-")
                    .append(f3(stats.max)).append(" s | width: ")
                    .append(f3(stats.range)).append(" s");
            if (stats.count >= 3) {
                result.append("\nCoarse physical timing reference: ~")
                        .append(f2(roundHundredth(stats.median)))
                        .append(" s — measurement evidence only, not an ECU proposal.");
            }
        }
        result.append("\n").append(quality(stats, groups.bestGroupCount()))
                .append("\nPrimary timing is the event-relative physical MAP 20→80 response. Prediction/fallback target evidence is diagnostic only.")
                .append("\nNumerical Blend Duration proposal/apply is intentionally withheld while the physical-response-to-curve conversion is being validated.")
                .append("\n\nRead-only: Guided measurement/capture never writes ECU RAM, burns settings, or removes passive/raw events.");
        return new GuidedSessionSnapshot(state, stateName(state, plateauAcquired),
                instruction, checkText, result.toString(),
                validCount, lastAttemptTrace);
    }

    private static String quality(BlendDurationSeriesStats stats, int bestGroupCount) {
        if (bestGroupCount < 3) {
            return "Measurement-group repeatability: INCOMPLETE — valid physical MAP response events remain retained in their groups.";
        }
        if (stats.range > 0.18 || stats.iqr > 0.10 || stats.sd > 0.08) {
            return "Measurement-group repeatability: LOW — capture remains valid, but the physical 20→80 response spread is broad.";
        }
        if (bestGroupCount >= 5 && stats.range <= 0.10
                && stats.iqr <= 0.05 && stats.sd <= 0.04) {
            return "Measurement-group repeatability: HIGH — the current best physical-response group is tightly repeatable.";
        }
        return "Measurement-group repeatability: MEDIUM — the current best physical-response group is sufficiently consistent for model review.";
    }

    private static String stateName(GuidedCaptureState state,
                                    boolean plateauAcquired) {
        switch (state) {
            case SETTLING: return "ESTABLISHING ROAD BASELINE";
            case READY: return "READY — MAKE ONE CONTROLLED OPENING";
            case OPENING_PENDING: return "OPENING PENDING";
            case CAPTURING:
                return plateauAcquired
                        ? "STABLE TPS HOLD — MEASURING PHYSICAL MAP RESPONSE"
                        : "OPENING — FORM STABLE PEDAL PLATEAU";
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

    private static double roundHundredth(double value) {
        return Double.isFinite(value) ? Math.round(value * 100.0) / 100.0 : Double.NaN;
    }

    private static String f0(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.US, "%.0f", value) : "n/a";
    }

    private static String f1(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.US, "%.1f", value) : "n/a";
    }

    private static String f2(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.US, "%.2f", value) : "n/a";
    }

    private static String f3(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.US, "%.3f", value) : "n/a";
    }
}
