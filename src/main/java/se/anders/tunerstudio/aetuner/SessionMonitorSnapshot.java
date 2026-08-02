package se.anders.tunerstudio.aetuner;

/** Immutable operational-state-aware session snapshot. */
class SessionMonitorSnapshot {
    final int segments;
    final long samples;
    final long sessionSamples;
    final long runningSamples;
    final long crankingSamples;
    final long keyOffSamples;
    final long unknownStateSamples;
    final long runningSignalSamples;
    final long crankingSignalSamples;
    final long ignitionOnSamples;
    final long mainRelayIgnSamples;

    final double peakMap;
    final double peakRpm;
    final double peakDuty;
    final double peakGaugeBoostBar;
    final double maxLeanError;
    final double maxRichError;
    final double minIgnitionTiming;
    final double maxIgnitionTiming;
    final double minFuelPressureHigh;
    final double maxFuelPressureHigh;
    final double minFuelPressureDifferential;

    final long ignitionTimingSamples;
    final double observedMinIgnitionTiming;
    final double observedMaxIgnitionTiming;
    final long boostTargetSamples;
    final double observedMinBoostTarget;
    final double observedMaxBoostTarget;
    final long fuelPressureHighSamples;
    final double observedMinFuelPressureHigh;
    final double observedMaxFuelPressureHigh;
    final long fuelPressureLowSamples;
    final double observedMinFuelPressureLow;
    final double observedMaxFuelPressureLow;

    final long ignitionFaultSamples;
    final long injectorFaultSamples;
    final long triggerErrorSamples;
    final long triggerErrorCountSamples;
    final long sparkCutSamples;
    final long fuelCutSamples;
    final long stopCodeSamples;
    final CutEvidenceSnapshot cutEvidence;

    final boolean ignitionFault;
    final boolean injectorFault;
    final boolean triggerError;
    final boolean sparkCut;
    final boolean fuelCut;
    final boolean stopCode;
    final boolean fullLoadFaultOrCut;
    final boolean crankingTriggerError;
    final boolean keyOffTriggerError;
    final boolean unknownTriggerError;
    final boolean crankingFaultOrCut;
    final boolean keyOffFaultOrCut;

    /** Compatibility field: running-state counter increase only. */
    final double triggerErrorCountDelta;
    final double crankingTriggerErrorCountDelta;
    final double keyOffTriggerErrorCountDelta;
    final double unknownTriggerErrorCountDelta;
    final long triggerErrorCountResets;

    /** Compatibility fields now contain accumulated positive increments. */
    final double overDwellDelta;
    final double overchargeWarningsDelta;
    final double underchargeWarningsDelta;
    final double sparkOutOfOrderDelta;
    final long overDwellSamples;
    final long overchargeWarningsSamples;
    final long underchargeWarningsSamples;
    final long sparkOutOfOrderSamples;
    final long overDwellResets;
    final long overchargeWarningsResets;
    final long underchargeWarningsResets;
    final long sparkOutOfOrderResets;
    final PositiveCounter.Snapshot overDwellCounter;
    final PositiveCounter.Snapshot overchargeCounter;
    final PositiveCounter.Snapshot underchargeCounter;
    final PositiveCounter.Snapshot sparkOutOfOrderCounter;

    SessionMonitorSnapshot(int segments, long samples, long sessionSamples,
             long runningSamples, long crankingSamples, long keyOffSamples, long unknownStateSamples,
             long runningSignalSamples, long crankingSignalSamples,
             long ignitionOnSamples, long mainRelayIgnSamples,
             double peakMap, double peakRpm, double peakDuty,
             double peakGaugeBoostBar, double maxLeanError, double maxRichError,
             double minIgnitionTiming, double maxIgnitionTiming,
             double minFuelPressureHigh, double maxFuelPressureHigh, double minFuelPressureDifferential,
             long ignitionTimingSamples, double observedMinIgnitionTiming, double observedMaxIgnitionTiming,
             long boostTargetSamples, double observedMinBoostTarget, double observedMaxBoostTarget,
             long fuelPressureHighSamples, double observedMinFuelPressureHigh, double observedMaxFuelPressureHigh,
             long fuelPressureLowSamples, double observedMinFuelPressureLow, double observedMaxFuelPressureLow,
             long ignitionFaultSamples, long injectorFaultSamples, long triggerErrorSamples,
             long triggerErrorCountSamples, long sparkCutSamples, long fuelCutSamples, long stopCodeSamples,
             boolean ignitionFault, boolean injectorFault, boolean triggerError,
             boolean sparkCut, boolean fuelCut, boolean stopCode, boolean fullLoadFaultOrCut,
             boolean crankingTriggerError, boolean keyOffTriggerError, boolean unknownTriggerError,
             boolean crankingFaultOrCut, boolean keyOffFaultOrCut,
             double triggerErrorCountDelta, double crankingTriggerErrorCountDelta,
             double keyOffTriggerErrorCountDelta, double unknownTriggerErrorCountDelta,
             long triggerErrorCountResets,
             CutEvidenceSnapshot cutEvidence,
             PositiveCounter.Snapshot overDwell, PositiveCounter.Snapshot overcharge,
             PositiveCounter.Snapshot undercharge, PositiveCounter.Snapshot outOfOrder) {
        this.segments = segments;
        this.samples = samples;
        this.sessionSamples = sessionSamples;
        this.runningSamples = runningSamples;
        this.crankingSamples = crankingSamples;
        this.keyOffSamples = keyOffSamples;
        this.unknownStateSamples = unknownStateSamples;
        this.runningSignalSamples = runningSignalSamples;
        this.crankingSignalSamples = crankingSignalSamples;
        this.ignitionOnSamples = ignitionOnSamples;
        this.mainRelayIgnSamples = mainRelayIgnSamples;
        this.peakMap = peakMap;
        this.peakRpm = peakRpm;
        this.peakDuty = peakDuty;
        this.peakGaugeBoostBar = peakGaugeBoostBar;
        this.maxLeanError = maxLeanError;
        this.maxRichError = maxRichError;
        this.minIgnitionTiming = minIgnitionTiming;
        this.maxIgnitionTiming = maxIgnitionTiming;
        this.minFuelPressureHigh = minFuelPressureHigh;
        this.maxFuelPressureHigh = maxFuelPressureHigh;
        this.minFuelPressureDifferential = minFuelPressureDifferential;
        this.ignitionTimingSamples = ignitionTimingSamples;
        this.observedMinIgnitionTiming = observedMinIgnitionTiming;
        this.observedMaxIgnitionTiming = observedMaxIgnitionTiming;
        this.boostTargetSamples = boostTargetSamples;
        this.observedMinBoostTarget = observedMinBoostTarget;
        this.observedMaxBoostTarget = observedMaxBoostTarget;
        this.fuelPressureHighSamples = fuelPressureHighSamples;
        this.observedMinFuelPressureHigh = observedMinFuelPressureHigh;
        this.observedMaxFuelPressureHigh = observedMaxFuelPressureHigh;
        this.fuelPressureLowSamples = fuelPressureLowSamples;
        this.observedMinFuelPressureLow = observedMinFuelPressureLow;
        this.observedMaxFuelPressureLow = observedMaxFuelPressureLow;
        this.ignitionFaultSamples = ignitionFaultSamples;
        this.injectorFaultSamples = injectorFaultSamples;
        this.triggerErrorSamples = triggerErrorSamples;
        this.triggerErrorCountSamples = triggerErrorCountSamples;
        this.sparkCutSamples = sparkCutSamples;
        this.fuelCutSamples = fuelCutSamples;
        this.stopCodeSamples = stopCodeSamples;
        this.cutEvidence = cutEvidence;
        this.ignitionFault = ignitionFault;
        this.injectorFault = injectorFault;
        this.triggerError = triggerError;
        this.sparkCut = sparkCut;
        this.fuelCut = fuelCut;
        this.stopCode = stopCode;
        this.fullLoadFaultOrCut = fullLoadFaultOrCut;
        this.crankingTriggerError = crankingTriggerError;
        this.keyOffTriggerError = keyOffTriggerError;
        this.unknownTriggerError = unknownTriggerError;
        this.crankingFaultOrCut = crankingFaultOrCut;
        this.keyOffFaultOrCut = keyOffFaultOrCut;
        this.triggerErrorCountDelta = triggerErrorCountDelta;
        this.crankingTriggerErrorCountDelta = crankingTriggerErrorCountDelta;
        this.keyOffTriggerErrorCountDelta = keyOffTriggerErrorCountDelta;
        this.unknownTriggerErrorCountDelta = unknownTriggerErrorCountDelta;
        this.triggerErrorCountResets = triggerErrorCountResets;
        this.overDwellDelta = overDwell.increase;
        this.overchargeWarningsDelta = overcharge.increase;
        this.underchargeWarningsDelta = undercharge.increase;
        this.sparkOutOfOrderDelta = outOfOrder.increase;
        this.overDwellSamples = overDwell.samples;
        this.overchargeWarningsSamples = overcharge.samples;
        this.underchargeWarningsSamples = undercharge.samples;
        this.sparkOutOfOrderSamples = outOfOrder.samples;
        this.overDwellResets = overDwell.resets;
        this.overchargeWarningsResets = overcharge.resets;
        this.underchargeWarningsResets = undercharge.resets;
        this.sparkOutOfOrderResets = outOfOrder.resets;
        this.overDwellCounter = overDwell;
        this.overchargeCounter = overcharge;
        this.underchargeCounter = undercharge;
        this.sparkOutOfOrderCounter = outOfOrder;
    }

    SessionMonitorSnapshot(SessionMonitorSnapshot source) {
        this(source.segments, source.samples, source.sessionSamples,
                source.runningSamples, source.crankingSamples, source.keyOffSamples, source.unknownStateSamples,
                source.runningSignalSamples, source.crankingSignalSamples,
                source.ignitionOnSamples, source.mainRelayIgnSamples,
                source.peakMap, source.peakRpm, source.peakDuty, source.peakGaugeBoostBar,
                source.maxLeanError, source.maxRichError, source.minIgnitionTiming, source.maxIgnitionTiming,
                source.minFuelPressureHigh, source.maxFuelPressureHigh, source.minFuelPressureDifferential,
                source.ignitionTimingSamples, source.observedMinIgnitionTiming, source.observedMaxIgnitionTiming,
                source.boostTargetSamples, source.observedMinBoostTarget, source.observedMaxBoostTarget,
                source.fuelPressureHighSamples, source.observedMinFuelPressureHigh, source.observedMaxFuelPressureHigh,
                source.fuelPressureLowSamples, source.observedMinFuelPressureLow, source.observedMaxFuelPressureLow,
                source.ignitionFaultSamples, source.injectorFaultSamples, source.triggerErrorSamples,
                source.triggerErrorCountSamples, source.sparkCutSamples, source.fuelCutSamples, source.stopCodeSamples,
                source.ignitionFault, source.injectorFault, source.triggerError, source.sparkCut, source.fuelCut,
                source.stopCode, source.fullLoadFaultOrCut, source.crankingTriggerError, source.keyOffTriggerError,
                source.unknownTriggerError, source.crankingFaultOrCut, source.keyOffFaultOrCut,
                source.triggerErrorCountDelta, source.crankingTriggerErrorCountDelta,
                source.keyOffTriggerErrorCountDelta, source.unknownTriggerErrorCountDelta,
                source.triggerErrorCountResets, source.cutEvidence,
                source.overDwellCounter, source.overchargeCounter,
                source.underchargeCounter, source.sparkOutOfOrderCounter);
    }

    boolean hasData() { return samples > 0L; }

    boolean hasSessionFaultOrCut() {
        return ignitionFault || injectorFault || triggerError || sparkCut || fuelCut || stopCode
                || triggerErrorCountDelta > 0.0;
    }

    boolean hasTriggerSyncFault() {
        return triggerError || triggerErrorCountDelta > 0.0;
    }

    boolean hasCrankingTriggerActivity() {
        return crankingTriggerError || crankingTriggerErrorCountDelta > 0.0;
    }

    boolean hasKeyOffTriggerActivity() {
        return keyOffTriggerError || keyOffTriggerErrorCountDelta > 0.0;
    }

    boolean hasCompleteFaultCoverage() {
        return ignitionFaultSamples > 0L && injectorFaultSamples > 0L
                && triggerErrorSamples > 0L && triggerErrorCountSamples > 0L
                && sparkCutSamples > 0L && fuelCutSamples > 0L && stopCodeSamples > 0L;
    }

    boolean hasAnyFaultCoverage() {
        return ignitionFaultSamples > 0L || injectorFaultSamples > 0L
                || triggerErrorSamples > 0L || triggerErrorCountSamples > 0L
                || sparkCutSamples > 0L || fuelCutSamples > 0L || stopCodeSamples > 0L;
    }

    boolean hasFullLoadFaultOrCut() { return fullLoadFaultOrCut; }
    boolean hasLeanWarning() { return maxLeanError >= 0.10; }
    boolean hasDutyWarning() { return Double.isFinite(peakDuty) && peakDuty >= 85.0; }
}
