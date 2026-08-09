package se.anders.tunerstudio.aetuner.passive;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

/**
 * Continuous session-safety and full-load accumulator.
 *
 * Running faults are safety-critical. Cranking and key-off activity are retained
 * as diagnostic context but cannot create a running-engine warning.
 */
final class SessionMonitorCore {
    private static final double FULL_LOAD_TPS = 85.0;
    private static final double FULL_LOAD_MAP = 120.0;
    private static final double FULL_LOAD_RPM = 1800.0;
    private static final double RUNNING_RPM_FALLBACK = 550.0;
    private static final double CRANKING_RPM_MIN = 60.0;
    private static final double KEY_OFF_BATTERY_MAX = 3.0;

    private boolean inFullLoad;
    private int fullLoadSegments;
    private long fullLoadSamples;
    private long sessionSamples;
    private long runningSamples;
    private long crankingSamples;
    private long keyOffSamples;
    private long unknownStateSamples;

    private long runningSignalSamples;
    private long crankingSignalSamples;
    private long ignitionOnSamples;
    private long mainRelayIgnSamples;

    private double peakMap = Double.NaN;
    private double peakRpm = Double.NaN;
    private double peakDuty = Double.NaN;
    private double peakGaugeBoostBar = Double.NaN;
    private double maxLeanError = 0.0;
    private double maxRichError = 0.0;
    private double minIgnitionTiming = Double.NaN;
    private double maxIgnitionTiming = Double.NaN;
    private double minFuelPressureHigh = Double.NaN;
    private double maxFuelPressureHigh = Double.NaN;
    private double minFuelPressureDifferential = Double.NaN;

    private long ignitionTimingSamples;
    private double observedMinIgnitionTiming = Double.NaN;
    private double observedMaxIgnitionTiming = Double.NaN;
    private long boostTargetSamples;
    private double observedMinBoostTarget = Double.NaN;
    private double observedMaxBoostTarget = Double.NaN;
    private long fuelPressureHighSamples;
    private double observedMinFuelPressureHigh = Double.NaN;
    private double observedMaxFuelPressureHigh = Double.NaN;
    private long fuelPressureLowSamples;
    private double observedMinFuelPressureLow = Double.NaN;
    private double observedMaxFuelPressureLow = Double.NaN;

    private long ignitionFaultSamples;
    private long injectorFaultSamples;
    private long triggerErrorSamples;
    private long triggerErrorCountSamples;
    private long sparkCutSamples;
    private long fuelCutSamples;
    private long stopCodeSamples;
    private long actualSparkCutSamples;
    private long ignitionCutReasonSamples;
    private long actualFuelCutSamples;
    private long fuelCutReasonSamples;
    private long guardedIgnitionReasonSamples;
    private long guardedFuelReasonSamples;

    /** Existing compatibility fields represent RUNNING-state activity only. */
    private boolean ignitionFault;
    private boolean injectorFault;
    private boolean triggerError;
    private boolean sparkCut;
    private boolean fuelCut;
    private boolean stopCode;
    private boolean fullLoadFaultOrCut;
    private boolean runningActualSparkCut;
    private boolean runningIgnitionCutReason;
    private boolean runningActualFuelCut;
    private boolean runningFuelCutReason;

    private boolean crankingTriggerError;
    private boolean keyOffTriggerError;
    private boolean unknownTriggerError;
    private boolean crankingFaultOrCut;
    private boolean keyOffFaultOrCut;

    private double lastTriggerErrorCount = Double.NaN;
    private double runningTriggerErrorCountIncrease;
    private double crankingTriggerErrorCountIncrease;
    private double keyOffTriggerErrorCountIncrease;
    private double unknownTriggerErrorCountIncrease;
    private long triggerErrorCountResets;

    private final PositiveCounter overDwell = new PositiveCounter();
    private final PositiveCounter overchargeWarnings = new PositiveCounter();
    private final PositiveCounter underchargeWarnings = new PositiveCounter();
    private final PositiveCounter sparkOutOfOrder = new PositiveCounter();
    private final CutReasonGuard ignitionCutReasonGuard = new CutReasonGuard();
    private final CutReasonGuard fuelCutReasonGuard = new CutReasonGuard();

    synchronized void reset() {
        inFullLoad = false;
        fullLoadSegments = 0;
        fullLoadSamples = 0L;
        sessionSamples = 0L;
        runningSamples = 0L;
        crankingSamples = 0L;
        keyOffSamples = 0L;
        unknownStateSamples = 0L;
        runningSignalSamples = 0L;
        crankingSignalSamples = 0L;
        ignitionOnSamples = 0L;
        mainRelayIgnSamples = 0L;

        peakMap = Double.NaN;
        peakRpm = Double.NaN;
        peakDuty = Double.NaN;
        peakGaugeBoostBar = Double.NaN;
        maxLeanError = 0.0;
        maxRichError = 0.0;
        minIgnitionTiming = Double.NaN;
        maxIgnitionTiming = Double.NaN;
        minFuelPressureHigh = Double.NaN;
        maxFuelPressureHigh = Double.NaN;
        minFuelPressureDifferential = Double.NaN;

        ignitionTimingSamples = 0L;
        observedMinIgnitionTiming = Double.NaN;
        observedMaxIgnitionTiming = Double.NaN;
        boostTargetSamples = 0L;
        observedMinBoostTarget = Double.NaN;
        observedMaxBoostTarget = Double.NaN;
        fuelPressureHighSamples = 0L;
        observedMinFuelPressureHigh = Double.NaN;
        observedMaxFuelPressureHigh = Double.NaN;
        fuelPressureLowSamples = 0L;
        observedMinFuelPressureLow = Double.NaN;
        observedMaxFuelPressureLow = Double.NaN;

        ignitionFaultSamples = 0L;
        injectorFaultSamples = 0L;
        triggerErrorSamples = 0L;
        triggerErrorCountSamples = 0L;
        sparkCutSamples = 0L;
        fuelCutSamples = 0L;
        stopCodeSamples = 0L;
        actualSparkCutSamples = 0L;
        ignitionCutReasonSamples = 0L;
        actualFuelCutSamples = 0L;
        fuelCutReasonSamples = 0L;
        guardedIgnitionReasonSamples = 0L;
        guardedFuelReasonSamples = 0L;

        ignitionFault = false;
        injectorFault = false;
        triggerError = false;
        sparkCut = false;
        fuelCut = false;
        stopCode = false;
        fullLoadFaultOrCut = false;
        runningActualSparkCut = false;
        runningIgnitionCutReason = false;
        runningActualFuelCut = false;
        runningFuelCutReason = false;
        crankingTriggerError = false;
        keyOffTriggerError = false;
        unknownTriggerError = false;
        crankingFaultOrCut = false;
        keyOffFaultOrCut = false;

        lastTriggerErrorCount = Double.NaN;
        runningTriggerErrorCountIncrease = 0.0;
        crankingTriggerErrorCountIncrease = 0.0;
        keyOffTriggerErrorCountIncrease = 0.0;
        unknownTriggerErrorCountIncrease = 0.0;
        triggerErrorCountResets = 0L;
        overDwell.reset();
        overchargeWarnings.reset();
        underchargeWarnings.reset();
        sparkOutOfOrder.reset();
        ignitionCutReasonGuard.reset();
        fuelCutReasonGuard.reset();
    }

    synchronized void addSample(LiveSample sample) {
        if (sample == null) {
            return;
        }
        sessionSamples++;

        OperationalState state = classifyState(sample);
        recordState(state, sample);
        observeCriticalChannels(sample, state);
        boolean runningFaultOrCut = observeSessionSafety(sample, state);

        double tps = sample.get(ChannelRole.TPS);
        double map = sample.get(ChannelRole.MAP);
        double rpm = sample.get(ChannelRole.RPM);
        boolean fullLoad = state == OperationalState.RUNNING
                && Double.isFinite(tps) && tps >= FULL_LOAD_TPS
                && Double.isFinite(map) && map >= FULL_LOAD_MAP
                && Double.isFinite(rpm) && rpm >= FULL_LOAD_RPM;
        if (fullLoad && !inFullLoad) {
            fullLoadSegments++;
        }
        inFullLoad = fullLoad;
        if (!fullLoad) {
            return;
        }

        if (runningFaultOrCut) {
            fullLoadFaultOrCut = true;
        }
        fullLoadSamples++;
        peakMap = maxFinite(peakMap, map);
        peakRpm = maxFinite(peakRpm, rpm);
        peakDuty = maxFinite(peakDuty, sample.get(ChannelRole.INJ_DUTY));

        double baro = sample.get(ChannelRole.BARO);
        if (Double.isFinite(baro) && baro > 50.0) {
            peakGaugeBoostBar = maxFinite(peakGaugeBoostBar, (map - baro) / 100.0);
        }

        double lambda = sample.get(ChannelRole.LAMBDA);
        double target = sample.get(ChannelRole.TARGET_LAMBDA);
        if ((!Double.isFinite(lambda) || !Double.isFinite(target) || target <= 0.0)
                && Double.isFinite(sample.get(ChannelRole.AFR))
                && Double.isFinite(sample.get(ChannelRole.TARGET_AFR))
                && sample.get(ChannelRole.TARGET_AFR) > 0.1) {
            lambda = sample.get(ChannelRole.AFR) / sample.get(ChannelRole.TARGET_AFR);
            target = 1.0;
        }
        if (Double.isFinite(lambda) && Double.isFinite(target) && target > 0.0) {
            double error = lambda - target;
            maxLeanError = Math.max(maxLeanError, error);
            maxRichError = Math.min(maxRichError, error);
        }

        double timing = sample.get(ChannelRole.IGNITION_TIMING);
        if (Double.isFinite(timing)) {
            minIgnitionTiming = minFinite(minIgnitionTiming, timing);
            maxIgnitionTiming = maxFinite(maxIgnitionTiming, timing);
        }

        double fuelPressure = sample.get(ChannelRole.FUEL_PRESSURE_HIGH);
        if (Double.isFinite(fuelPressure) && fuelPressure > 10.0) {
            minFuelPressureHigh = minFinite(minFuelPressureHigh, fuelPressure);
            maxFuelPressureHigh = maxFinite(maxFuelPressureHigh, fuelPressure);
            minFuelPressureDifferential = minFinite(minFuelPressureDifferential, fuelPressure - map);
        }
    }

    private OperationalState classifyState(LiveSample sample) {
        double relayIgn = sample.get(ChannelRole.MAIN_RELAY_HAS_IGN);
        double ignitionOn = sample.get(ChannelRole.IGNITION_ON);
        boolean relayAvailable = Double.isFinite(relayIgn);
        boolean ignitionOnAvailable = Double.isFinite(ignitionOn);
        boolean explicitIgnAvailable = relayAvailable || ignitionOnAvailable;
        boolean explicitIgnOn = valueOn(relayIgn) || valueOn(ignitionOn);
        // Either resolved explicit ignition-off signal overrides a lagging running flag.
        if ((relayAvailable && !valueOn(relayIgn))
                || (ignitionOnAvailable && !valueOn(ignitionOn))) {
            return OperationalState.KEY_OFF;
        }

        if (valueOn(sample.get(ChannelRole.ENGINE_CRANKING))) {
            return OperationalState.CRANKING;
        }
        if (valueOn(sample.get(ChannelRole.ENGINE_RUNNING))) {
            return OperationalState.RUNNING;
        }

        double rpm = sample.get(ChannelRole.RPM);
        double battery = sample.get(ChannelRole.BATTERY);
        if (!explicitIgnAvailable && Double.isFinite(battery)
                && battery <= KEY_OFF_BATTERY_MAX
                && (!Double.isFinite(rpm) || rpm < RUNNING_RPM_FALLBACK)) {
            return OperationalState.KEY_OFF;
        }
        if (Double.isFinite(rpm) && rpm >= RUNNING_RPM_FALLBACK) {
            return OperationalState.RUNNING;
        }
        if (explicitIgnOn && Double.isFinite(rpm)
                && rpm >= CRANKING_RPM_MIN && rpm < RUNNING_RPM_FALLBACK) {
            return OperationalState.CRANKING;
        }
        return OperationalState.UNKNOWN;
    }

    private void recordState(OperationalState state, LiveSample sample) {
        if (Double.isFinite(sample.get(ChannelRole.ENGINE_RUNNING))) runningSignalSamples++;
        if (Double.isFinite(sample.get(ChannelRole.ENGINE_CRANKING))) crankingSignalSamples++;
        if (Double.isFinite(sample.get(ChannelRole.IGNITION_ON))) ignitionOnSamples++;
        if (Double.isFinite(sample.get(ChannelRole.MAIN_RELAY_HAS_IGN))) mainRelayIgnSamples++;
        switch (state) {
            case RUNNING: runningSamples++; break;
            case CRANKING: crankingSamples++; break;
            case KEY_OFF: keyOffSamples++; break;
            default: unknownStateSamples++; break;
        }
    }

    private void observeCriticalChannels(LiveSample sample, OperationalState state) {
        if (state == OperationalState.KEY_OFF) {
            return;
        }
        double timing = sample.get(ChannelRole.IGNITION_TIMING);
        if (Double.isFinite(timing)) {
            ignitionTimingSamples++;
            observedMinIgnitionTiming = minFinite(observedMinIgnitionTiming, timing);
            observedMaxIgnitionTiming = maxFinite(observedMaxIgnitionTiming, timing);
        }

        double boostTarget = sample.get(ChannelRole.BOOST_TARGET);
        if (Double.isFinite(boostTarget)) {
            boostTargetSamples++;
            observedMinBoostTarget = minFinite(observedMinBoostTarget, boostTarget);
            observedMaxBoostTarget = maxFinite(observedMaxBoostTarget, boostTarget);
        }

        double high = sample.get(ChannelRole.FUEL_PRESSURE_HIGH);
        if (Double.isFinite(high)) {
            fuelPressureHighSamples++;
            observedMinFuelPressureHigh = minFinite(observedMinFuelPressureHigh, high);
            observedMaxFuelPressureHigh = maxFinite(observedMaxFuelPressureHigh, high);
        }

        double low = sample.get(ChannelRole.FUEL_PRESSURE_LOW);
        if (Double.isFinite(low)) {
            fuelPressureLowSamples++;
            observedMinFuelPressureLow = minFinite(observedMinFuelPressureLow, low);
            observedMaxFuelPressureLow = maxFinite(observedMaxFuelPressureLow, low);
        }
    }

    private boolean observeSessionSafety(LiveSample sample, OperationalState state) {
        boolean runningCritical = false;
        boolean diagnosticActivity = false;

        double ignitionFaultValue = sample.get(ChannelRole.IGNITION_FAULT);
        if (Double.isFinite(ignitionFaultValue)) {
            ignitionFaultSamples++;
            if (valueOn(ignitionFaultValue)) {
                if (state == OperationalState.RUNNING) {
                    ignitionFault = true;
                    runningCritical = true;
                } else diagnosticActivity = true;
            }
        }

        double injectorFaultValue = sample.get(ChannelRole.INJECTOR_FAULT);
        if (Double.isFinite(injectorFaultValue)) {
            injectorFaultSamples++;
            if (valueOn(injectorFaultValue)) {
                if (state == OperationalState.RUNNING) {
                    injectorFault = true;
                    runningCritical = true;
                } else diagnosticActivity = true;
            }
        }

        double triggerErrorValue = sample.get(ChannelRole.TRIGGER_ERROR);
        if (Double.isFinite(triggerErrorValue)) {
            triggerErrorSamples++;
            if (valueOn(triggerErrorValue)) {
                switch (state) {
                    case RUNNING: triggerError = true; runningCritical = true; break;
                    case CRANKING: crankingTriggerError = true; diagnosticActivity = true; break;
                    case KEY_OFF: keyOffTriggerError = true; diagnosticActivity = true; break;
                    default: unknownTriggerError = true; diagnosticActivity = true; break;
                }
            }
        }

        double totalSparkCut = sample.get(ChannelRole.TOTAL_SPARK_CUT);
        double ignitionCutCode = sample.get(ChannelRole.IGN_CUT_CODE);
        boolean actualSparkActive = absGreater(totalSparkCut, 0.001);
        boolean ignitionReasonActive = absGreater(ignitionCutCode, 0.001);
        if (Double.isFinite(totalSparkCut)) actualSparkCutSamples++;
        if (Double.isFinite(ignitionCutCode)) ignitionCutReasonSamples++;
        if (Double.isFinite(totalSparkCut) || Double.isFinite(ignitionCutCode)) sparkCutSamples++;
        boolean ignitionReasonConfirmed = ignitionCutReasonGuard.observe(
                ignitionReasonActive, state == OperationalState.RUNNING, sample.getNanoTime());
        if (actualSparkActive) {
            if (state == OperationalState.RUNNING) {
                runningActualSparkCut = true;
                sparkCut = true;
                runningCritical = true;
            } else diagnosticActivity = true;
        }
        if (ignitionReasonActive) {
            if (state == OperationalState.RUNNING) {
                if (ignitionReasonConfirmed) {
                    runningIgnitionCutReason = true;
                    sparkCut = true;
                    runningCritical = true;
                } else guardedIgnitionReasonSamples++;
            } else diagnosticActivity = true;
        }

        double totalFuelCut = sample.get(ChannelRole.FUEL_CUT);
        double fuelCutCode = sample.get(ChannelRole.FUEL_CUT_CODE);
        boolean actualFuelActive = absGreater(totalFuelCut, 0.001);
        boolean fuelReasonActive = absGreater(fuelCutCode, 0.001);
        if (Double.isFinite(totalFuelCut)) actualFuelCutSamples++;
        if (Double.isFinite(fuelCutCode)) fuelCutReasonSamples++;
        if (Double.isFinite(totalFuelCut) || Double.isFinite(fuelCutCode)) fuelCutSamples++;
        boolean fuelReasonConfirmed = fuelCutReasonGuard.observe(
                fuelReasonActive, state == OperationalState.RUNNING, sample.getNanoTime());
        if (actualFuelActive) {
            if (state == OperationalState.RUNNING) {
                runningActualFuelCut = true;
                fuelCut = true;
                runningCritical = true;
            } else diagnosticActivity = true;
        }
        if (fuelReasonActive) {
            if (state == OperationalState.RUNNING) {
                if (fuelReasonConfirmed) {
                    runningFuelCutReason = true;
                    fuelCut = true;
                    runningCritical = true;
                } else guardedFuelReasonSamples++;
            } else diagnosticActivity = true;
        }

        double stopCodeValue = sample.get(ChannelRole.STOP_ENGINE_CODE);
        if (Double.isFinite(stopCodeValue)) {
            stopCodeSamples++;
            if (absGreater(stopCodeValue, 0.001)) {
                if (state == OperationalState.RUNNING) {
                    stopCode = true;
                    runningCritical = true;
                } else diagnosticActivity = true;
            }
        }

        if (diagnosticActivity) {
            if (state == OperationalState.CRANKING) crankingFaultOrCut = true;
            if (state == OperationalState.KEY_OFF) keyOffFaultOrCut = true;
        }

        observeTriggerCounter(sample.get(ChannelRole.TRIGGER_ERROR_COUNT), state);
        int counterState = counterState(state);
        overDwell.add(sample.get(ChannelRole.IGN_OVERDWELL), counterState);
        overchargeWarnings.add(sample.get(ChannelRole.IGN_OVERCHARGE_WARNINGS), counterState);
        underchargeWarnings.add(sample.get(ChannelRole.IGN_UNDERCHARGE_WARNINGS), counterState);
        sparkOutOfOrder.add(sample.get(ChannelRole.IGN_SPARK_OUT_OF_ORDER), counterState);
        return runningCritical;
    }

    private void observeTriggerCounter(double value, OperationalState state) {
        if (!Double.isFinite(value)) {
            return;
        }
        triggerErrorCountSamples++;
        if (Double.isFinite(lastTriggerErrorCount)) {
            if (value >= lastTriggerErrorCount) {
                double increase = value - lastTriggerErrorCount;
                switch (state) {
                    case RUNNING: runningTriggerErrorCountIncrease += increase; break;
                    case CRANKING: crankingTriggerErrorCountIncrease += increase; break;
                    case KEY_OFF: keyOffTriggerErrorCountIncrease += increase; break;
                    default: unknownTriggerErrorCountIncrease += increase; break;
                }
            } else if (lastTriggerErrorCount - value > 0.000001) {
                triggerErrorCountResets++;
            }
        }
        lastTriggerErrorCount = value;
    }

    synchronized SessionMonitorSnapshot snapshot() {
        return new SessionMonitorSnapshot(fullLoadSegments, fullLoadSamples, sessionSamples,
                runningSamples, crankingSamples, keyOffSamples, unknownStateSamples,
                runningSignalSamples, crankingSignalSamples, ignitionOnSamples, mainRelayIgnSamples,
                peakMap, peakRpm, peakDuty, peakGaugeBoostBar, maxLeanError, maxRichError,
                minIgnitionTiming, maxIgnitionTiming,
                minFuelPressureHigh, maxFuelPressureHigh, minFuelPressureDifferential,
                ignitionTimingSamples, observedMinIgnitionTiming, observedMaxIgnitionTiming,
                boostTargetSamples, observedMinBoostTarget, observedMaxBoostTarget,
                fuelPressureHighSamples, observedMinFuelPressureHigh, observedMaxFuelPressureHigh,
                fuelPressureLowSamples, observedMinFuelPressureLow, observedMaxFuelPressureLow,
                ignitionFaultSamples, injectorFaultSamples, triggerErrorSamples,
                triggerErrorCountSamples, sparkCutSamples, fuelCutSamples, stopCodeSamples,
                ignitionFault, injectorFault, triggerError, sparkCut, fuelCut, stopCode,
                fullLoadFaultOrCut, crankingTriggerError, keyOffTriggerError, unknownTriggerError,
                crankingFaultOrCut, keyOffFaultOrCut,
                runningTriggerErrorCountIncrease, crankingTriggerErrorCountIncrease,
                keyOffTriggerErrorCountIncrease, unknownTriggerErrorCountIncrease,
                triggerErrorCountResets,
                new CutEvidenceSnapshot(actualSparkCutSamples, ignitionCutReasonSamples,
                        actualFuelCutSamples, fuelCutReasonSamples,
                        guardedIgnitionReasonSamples, guardedFuelReasonSamples,
                        runningActualSparkCut, runningIgnitionCutReason,
                        runningActualFuelCut, runningFuelCutReason),
                overDwell.snapshot(), overchargeWarnings.snapshot(),
                underchargeWarnings.snapshot(), sparkOutOfOrder.snapshot());
    }

    private static int counterState(OperationalState state) {
        switch (state) {
            case RUNNING: return PositiveCounter.RUNNING;
            case CRANKING: return PositiveCounter.CRANKING;
            case KEY_OFF: return PositiveCounter.KEY_OFF;
            default: return PositiveCounter.UNKNOWN;
        }
    }

    private static boolean valueOn(double value) {
        return Double.isFinite(value) && Math.abs(value) > 0.5;
    }

    private static boolean absGreater(double value, double threshold) {
        return Double.isFinite(value) && Math.abs(value) > threshold;
    }

    private static double maxFinite(double current, double value) {
        if (!Double.isFinite(value)) return current;
        return !Double.isFinite(current) ? value : Math.max(current, value);
    }

    private static double minFinite(double current, double value) {
        if (!Double.isFinite(value)) return current;
        return !Double.isFinite(current) ? value : Math.min(current, value);
    }

    private enum OperationalState { RUNNING, CRANKING, KEY_OFF, UNKNOWN }
}
