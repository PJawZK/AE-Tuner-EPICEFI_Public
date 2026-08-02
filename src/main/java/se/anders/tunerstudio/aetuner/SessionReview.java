package se.anders.tunerstudio.aetuner;

import java.text.DecimalFormat;
import java.util.List;

/** Builds a read-only session review from captured transient events and continuous safety monitoring. */
final class SessionReview {
    private static final DecimalFormat F1 = new DecimalFormat("0.0");
    private static final DecimalFormat F2 = new DecimalFormat("0.00");

    private final int predictionOnlyEvents;
    private final int predictionWithWallEvents;
    private final int wallOnlyEvents;
    private final int instantFuelEvents;
    private final int lowRpmPredictionEvents;
    private final int lowRpmLargeGapEvents;
    private final int lowRpmMultipleBurstEvents;
    private final int lowRpmRichLargeGapEvents;
    private final int lowRpmLeanSmallGapEvents;
    private final int resetDiscontinuityEvents;
    private final SessionMonitor.Snapshot fullLoad;

    private SessionReview(int predictionOnlyEvents, int predictionWithWallEvents,
                          int wallOnlyEvents, int instantFuelEvents,
                          int lowRpmPredictionEvents, int lowRpmLargeGapEvents,
                          int lowRpmMultipleBurstEvents, int lowRpmRichLargeGapEvents,
                          int lowRpmLeanSmallGapEvents, int resetDiscontinuityEvents,
                          SessionMonitor.Snapshot fullLoad) {
        this.predictionOnlyEvents = predictionOnlyEvents;
        this.predictionWithWallEvents = predictionWithWallEvents;
        this.wallOnlyEvents = wallOnlyEvents;
        this.instantFuelEvents = instantFuelEvents;
        this.lowRpmPredictionEvents = lowRpmPredictionEvents;
        this.lowRpmLargeGapEvents = lowRpmLargeGapEvents;
        this.lowRpmMultipleBurstEvents = lowRpmMultipleBurstEvents;
        this.lowRpmRichLargeGapEvents = lowRpmRichLargeGapEvents;
        this.lowRpmLeanSmallGapEvents = lowRpmLeanSmallGapEvents;
        this.resetDiscontinuityEvents = resetDiscontinuityEvents;
        this.fullLoad = fullLoad;
    }

    static SessionReview build(List<EventSummary> events, SessionMonitor.Snapshot fullLoad) {
        int predictionOnly = 0;
        int predictionWithWall = 0;
        int wallOnly = 0;
        int instant = 0;
        int lowRpm = 0;
        int lowRpmLargeGap = 0;
        int lowRpmMultipleBurst = 0;
        int lowRpmRichLargeGap = 0;
        int lowRpmLeanSmallGap = 0;
        int resetDiscontinuity = 0;

        if (events != null) {
            for (EventSummary event : events) {
                boolean prediction = event.hasMapPrediction();
                boolean wall = event.hasWallWettingContribution();
                boolean instantVisible = event.hasInstantFuelContribution();
                if (prediction && wall) predictionWithWall++;
                else if (prediction) predictionOnly++;
                else if (wall) wallOnly++;
                if (instantVisible) instant++;

                CounterMath.Result resets = event.getPredictionResetMetrics();
                if (resets.hasDiscontinuity()) resetDiscontinuity++;

                double rpm = event.getMedianPredictionRpm();
                if (prediction && Double.isFinite(rpm) && rpm < 2200.0) {
                    lowRpm++;
                    double gap = event.getMaxEffectiveMapGap();
                    double lean = event.getMaxLeanLambdaError();
                    double rich = event.getMaxRichLambdaError();
                    if (gap >= 18.0) lowRpmLargeGap++;
                    if (event.getPredictionTriggerBurstCount() > 1) lowRpmMultipleBurst++;
                    if (gap >= 18.0 && rich <= -0.10) lowRpmRichLargeGap++;
                    if (gap <= 10.0 && lean >= 0.10) lowRpmLeanSmallGap++;
                }
            }
        }
        return new SessionReview(predictionOnly, predictionWithWall, wallOnly, instant,
                lowRpm, lowRpmLargeGap, lowRpmMultipleBurst,
                lowRpmRichLargeGap, lowRpmLeanSmallGap, resetDiscontinuity,
                fullLoad == null ? new SessionMonitor().snapshot() : fullLoad);
    }

    String contributionCardText() {
        return predictionOnlyEvents + " MAP-only • " + predictionWithWallEvents + " MAP+WW"
                + " • " + wallOnlyEvents + " WW-only";
    }

    String lowRpmCardText() {
        if (lowRpmPredictionEvents == 0) {
            return "No prediction events below 2200 RPM";
        }
        return lowRpmPredictionEvents + " event(s) • " + lowRpmLargeGapEvents + " large-gap"
                + " • " + lowRpmMultipleBurstEvents + " multi-burst";
    }

    String fullLoadCardText() {
        if (fullLoad.hasTriggerSyncFault()) {
            return "RUNNING TRIGGER/SYNC FAULT • counter +" + fmt(fullLoad.triggerErrorCountDelta);
        }
        if (sessionFaultNeedsReview()) {
            return "Running fault/cut activity recorded";
        }
        if (!fullLoad.hasData()) {
            if (fullLoad.hasCrankingTriggerActivity()) {
                return "Cranking trigger activity recorded • diagnostic";
            }
            return "No full-load segment captured";
        }
        String text = fullLoad.segments + " segment(s) • MAP " + fmt(fullLoad.peakMap) + " kPa";
        if (Double.isFinite(fullLoad.peakGaugeBoostBar)) {
            text += " (" + F2.format(fullLoad.peakGaugeBoostBar) + " bar)";
        }
        text += " • duty " + fmt(fullLoad.peakDuty) + "% • lean +" + F2.format(fullLoad.maxLeanError) + " λ";
        return text;
    }

    boolean lowRpmNeedsReview() {
        return lowRpmLargeGapEvents > 0 || lowRpmMultipleBurstEvents > 0
                || lowRpmRichLargeGapEvents > 0 || lowRpmLeanSmallGapEvents > 0;
    }

    boolean sessionFaultNeedsReview() {
        return fullLoad.hasSessionFaultOrCut();
    }

    boolean triggerSyncNeedsReview() {
        return fullLoad.hasTriggerSyncFault();
    }

    /** Retains the historical panel-facing name. Only running/full-load faults create a warning. */
    boolean fullLoadNeedsReview() {
        return sessionFaultNeedsReview()
                || (fullLoad.hasData()
                && (fullLoad.hasFullLoadFaultOrCut() || fullLoad.hasLeanWarning() || fullLoad.hasDutyWarning()));
    }

    boolean resetCounterNeedsReview() { return resetDiscontinuityEvents > 0; }

    String recommendedNextStep() {
        if (triggerSyncNeedsReview()) {
            return "Review running trigger/sync loss before further transient testing";
        }
        if (sessionFaultNeedsReview()) {
            return "Review running fault/cut activity before further transient testing";
        }
        if (fullLoad.hasData()
                && (fullLoad.hasFullLoadFaultOrCut() || fullLoad.hasLeanWarning() || fullLoad.hasDutyWarning())) {
            return "Review full-load safety before further WOT testing";
        }
        if (lowRpmNeedsReview()) {
            return "Review MAP Estimate and Blend Duration below 2200 RPM";
        }
        if (predictionOnlyEvents + predictionWithWallEvents < 4) {
            return "Collect deliberate loaded MAP Predict tip-ins";
        }
        return "Review MAP Estimate draft, then Blend Duration draft";
    }

    String toDisplayText() {
        StringBuilder text = new StringBuilder();
        text.append("AE Tuner session review\n\n")
                .append("Transient contribution\n")
                .append("- MAP Predict only: ").append(predictionOnlyEvents).append(" event(s)\n")
                .append("- MAP Predict + Wall Wetting: ").append(predictionWithWallEvents).append(" event(s)\n")
                .append("- Wall Wetting only: ").append(wallOnlyEvents).append(" event(s)\n")
                .append("- Instant Fuel visible: ").append(instantFuelEvents).append(" event(s)\n\n")
                .append("Low-RPM MAP Predict review (<2200 RPM)\n")
                .append("- Prediction events: ").append(lowRpmPredictionEvents).append("\n")
                .append("- Effective MAP gap >=18 kPa: ").append(lowRpmLargeGapEvents).append("\n")
                .append("- Multiple separate TPS-change bursts: ").append(lowRpmMultipleBurstEvents).append("\n")
                .append("- Large gap with rich response: ").append(lowRpmRichLargeGapEvents).append("\n")
                .append("- Small gap with lean response: ").append(lowRpmLeanSmallGapEvents).append("\n")
                .append("- Reset-counter discontinuity events: ").append(resetDiscontinuityEvents).append("\n");
        if (lowRpmNeedsReview()) {
            text.append("Guidance: below 2200 RPM, review the exercised MAP Estimate cells and Predictive MAP Blend Duration. Large gaps plus rich response point toward an estimate that is too high or held too long; small gaps plus lean response point toward too little/too-short prediction. predTimerResetCnt increments inside one continuous detector burst are expected and are not treated as repeated stabs. Lambda timing is not yet delay-aligned, so treat this as a review direction rather than an automatic change.\n");
        } else {
            text.append("Guidance: no consistent low-RPM over/under-prediction pattern is established yet.\n");
        }

        appendSessionSafety(text);
        appendCriticalChannelEvidence(text);
        appendFullLoadSafety(text);

        text.append("\nRecommended next step: ").append(recommendedNextStep()).append(".\n")
                .append("This report is diagnostic and does not write to the ECU.");
        return text.toString();
    }

    private void appendSessionSafety(StringBuilder text) {
        text.append("\nOperational-state classification\n")
                .append("- Running samples: ").append(fullLoad.runningSamples).append("\n")
                .append("- Cranking samples: ").append(fullLoad.crankingSamples).append("\n")
                .append("- Key-off/coast-down samples: ").append(fullLoad.keyOffSamples).append("\n")
                .append("- Unknown-state samples: ").append(fullLoad.unknownStateSamples).append("\n")
                .append("- State channels received: running ").append(fullLoad.runningSignalSamples)
                .append(", cranking ").append(fullLoad.crankingSignalSamples)
                .append(", ignitionOn ").append(fullLoad.ignitionOnSamples)
                .append(", Main relay: Has IGN voltage ").append(fullLoad.mainRelayIgnSamples).append("\n");

        text.append("\nSession-wide safety and sync review\n");
        if (fullLoad.triggerErrorSamples == 0L) {
            text.append("- Error: Trigger: no live value received; unresolved or unavailable.\n");
        } else if (fullLoad.triggerError) {
            text.append("- Error: Trigger: ACTIVE while engine classified RUNNING.\n");
        } else {
            text.append("- Error: Trigger: no running-state activity seen.\n");
        }
        text.append("- Cranking trigger activity: ")
                .append(fullLoad.hasCrankingTriggerActivity() ? "seen (diagnostic, not a running fault)" : "none seen")
                .append(".\n")
                .append("- Key-off/coast-down trigger activity: ")
                .append(fullLoad.hasKeyOffTriggerActivity() ? "seen and excluded from running-fault recommendations" : "none seen")
                .append(".\n");
        if (fullLoad.unknownTriggerError || fullLoad.unknownTriggerErrorCountDelta > 0.0) {
            text.append("- Unknown-state trigger activity: seen; review state-channel coverage.\n");
        }

        if (fullLoad.triggerErrorCountSamples == 0L) {
            text.append("- Trigger Error Counter: no live value received; unresolved or unavailable.\n");
        } else {
            text.append("- Trigger Error Counter positive increments: running +")
                    .append(fmt(fullLoad.triggerErrorCountDelta))
                    .append(", cranking +").append(fmt(fullLoad.crankingTriggerErrorCountDelta))
                    .append(", key-off +").append(fmt(fullLoad.keyOffTriggerErrorCountDelta))
                    .append(", unknown +").append(fmt(fullLoad.unknownTriggerErrorCountDelta))
                    .append("; resets ").append(fullLoad.triggerErrorCountResets).append(".\n");
        }

        if (fullLoad.hasSessionFaultOrCut()) {
            text.append("- Running fault/cut result: REVIEW — running-state activity was recorded.\n");
        } else if (fullLoad.hasCompleteFaultCoverage()) {
            text.append("- Running fault/cut result: none seen in all monitored channels.\n");
        } else if (fullLoad.hasAnyFaultCoverage()) {
            text.append("- Running fault/cut result: incomplete — no activity seen in received channels, but one or more required channels were unresolved or unavailable.\n");
        } else {
            text.append("- Running fault/cut result: unavailable — no monitored fault/cut channel delivered a live value.\n");
        }

        text.append("- Running flags: ignition fault ").append(state(fullLoad.ignitionFaultSamples, fullLoad.ignitionFault))
                .append(", injector fault ").append(state(fullLoad.injectorFaultSamples, fullLoad.injectorFault))
                .append(", stop code ").append(state(fullLoad.stopCodeSamples, fullLoad.stopCode)).append(".\n")
                .append("- Actual cut outputs: Total spark cut ")
                .append(state(fullLoad.cutEvidence.actualSparkCutSamples, fullLoad.cutEvidence.runningActualSparkCut))
                .append(", Total fuel cut ")
                .append(state(fullLoad.cutEvidence.actualFuelCutSamples, fullLoad.cutEvidence.runningActualFuelCut))
                .append(".\n")
                .append("- Guarded cut reason codes: Ign: Cut Code ")
                .append(state(fullLoad.cutEvidence.ignitionCutReasonSamples, fullLoad.cutEvidence.runningIgnitionCutReason))
                .append(", Fuel: Cut Code ")
                .append(state(fullLoad.cutEvidence.fuelCutReasonSamples, fullLoad.cutEvidence.runningFuelCutReason))
                .append("; transient running samples held by guard ignition ")
                .append(fullLoad.cutEvidence.guardedIgnitionReasonSamples)
                .append(", fuel ").append(fullLoad.cutEvidence.guardedFuelReasonSamples).append(".\n");
        if (fullLoad.crankingFaultOrCut) {
            text.append("- Cranking fault/cut activity: seen and retained as diagnostic context.\n");
        }
        if (fullLoad.keyOffFaultOrCut) {
            text.append("- Key-off fault/cut activity: seen and excluded from running-fault recommendations.\n");
        }

        text.append("- Ignition counters (positive increments by operational state):\n")
                .append("  - over-dwell: ").append(counterState(fullLoad.overDwellCounter)).append("\n")
                .append("  - overcharge: ").append(counterState(fullLoad.overchargeCounter)).append("\n")
                .append("  - undercharge: ").append(counterState(fullLoad.underchargeCounter)).append("\n")
                .append("  - spark out-of-order: ").append(counterState(fullLoad.sparkOutOfOrderCounter)).append("\n");
    }

    private void appendCriticalChannelEvidence(StringBuilder text) {
        text.append("\nCritical live-channel evidence\n")
                .append("- Timing: ignition: ")
                .append(rangeState(fullLoad.ignitionTimingSamples,
                        fullLoad.observedMinIgnitionTiming, fullLoad.observedMaxIgnitionTiming, " deg"))
                .append("\n")
                .append("- Boost: Target: ")
                .append(rangeState(fullLoad.boostTargetSamples,
                        fullLoad.observedMinBoostTarget, fullLoad.observedMaxBoostTarget, " kPa"))
                .append("\n")
                .append("- Fuel pressure _high: ")
                .append(rangeState(fullLoad.fuelPressureHighSamples,
                        fullLoad.observedMinFuelPressureHigh, fullLoad.observedMaxFuelPressureHigh, ""))
                .append("\n")
                .append("- Fuel pressure _low: ")
                .append(rangeState(fullLoad.fuelPressureLowSamples,
                        fullLoad.observedMinFuelPressureLow, fullLoad.observedMaxFuelPressureLow, ""))
                .append("\n");
    }

    private void appendFullLoadSafety(StringBuilder text) {
        text.append("\nFull-load safety summary\n");
        if (!fullLoad.hasData()) {
            text.append("- No running sample met TPS >=85%, MAP >=120 kPa and RPM >=1800.\n");
            return;
        }

        text.append("- Full-load segments: ").append(fullLoad.segments).append("\n")
                .append("- Peak RPM: ").append(fmt(fullLoad.peakRpm)).append("\n")
                .append("- Peak MAP: ").append(fmt(fullLoad.peakMap)).append(" kPa");
        if (Double.isFinite(fullLoad.peakGaugeBoostBar)) {
            text.append(" / ").append(F2.format(fullLoad.peakGaugeBoostBar)).append(" bar gauge");
        }
        text.append("\n- Peak injector duty: ").append(fmt(fullLoad.peakDuty)).append("%\n")
                .append("- Lambda error range: ").append(F2.format(fullLoad.maxRichError))
                .append(" to +").append(F2.format(fullLoad.maxLeanError)).append(" λ\n")
                .append("- Ignition timing range: ").append(fmt(fullLoad.minIgnitionTiming))
                .append(" to ").append(fmt(fullLoad.maxIgnitionTiming)).append(" deg\n")
                .append("- Full-load fault/cut activity: ")
                .append(fullLoad.hasFullLoadFaultOrCut() ? "REVIEW" : "none seen in captured full-load samples")
                .append("\n");
        if (Double.isFinite(fullLoad.minFuelPressureHigh)) {
            text.append("- Fuel pressure high: ").append(fmt(fullLoad.minFuelPressureHigh)).append("..")
                    .append(fmt(fullLoad.maxFuelPressureHigh)).append("; minimum pressure-MAP difference ")
                    .append(fmt(fullLoad.minFuelPressureDifferential)).append(" kPa\n");
        } else if (fullLoad.fuelPressureHighSamples > 0L) {
            text.append("- Fuel-pressure trace: received but zero/inactive or below the usable threshold; full-load pressure cannot be verified.\n");
        } else {
            text.append("- Fuel-pressure trace: no live value received; unresolved or unavailable, so full-load pressure cannot be verified.\n");
        }
    }

    private static String state(long samples, boolean active) {
        if (samples <= 0L) return "unresolved/unavailable";
        return active ? "ACTIVE seen" : "received inactive";
    }

    private static String counterState(PositiveCounter.Snapshot counter) {
        if (counter == null || counter.samples <= 0L) return "unresolved/unavailable";
        return "+" + fmt(counter.increase)
                + " (running +" + fmt(counter.runningIncrease)
                + ", cranking +" + fmt(counter.crankingIncrease)
                + ", key-off +" + fmt(counter.keyOffIncrease)
                + ", unknown +" + fmt(counter.unknownIncrease)
                + ") over " + counter.samples + " samples; resets " + counter.resets;
    }

    private static String rangeState(long samples, double minimum, double maximum, String unit) {
        if (samples <= 0L || !Double.isFinite(minimum) || !Double.isFinite(maximum)) {
            return "no live value received; unresolved or unavailable";
        }
        if (Math.abs(minimum) < 0.000001 && Math.abs(maximum) < 0.000001) {
            return "received, zero/inactive (" + samples + " samples)";
        }
        return "received " + fmt(minimum) + ".." + fmt(maximum) + unit
                + " (" + samples + " samples)";
    }

    private static String fmt(double value) {
        return Double.isFinite(value) ? F1.format(value) : "n/a";
    }
}
