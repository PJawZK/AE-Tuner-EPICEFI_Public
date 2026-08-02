package se.anders.tunerstudio.aetuner;

import java.util.Collections;
import java.util.EnumMap;

/** Deterministic regression checks for operational-state-aware session monitoring. */
public final class SessionMonitorRegressionTest {
    private SessionMonitorRegressionTest() { }

    public static void main(String[] args) {
        runningLowLoadTriggerFaultMustRemainCritical();
        crankingTriggerActivityMustRemainDiagnostic();
        keyOffActivityMustNotBecomeRunningFault();
        laggingRunningMustYieldToEitherIgnitionOffSignal();
        actualRunningCutOutputMustRemainImmediate();
        persistentRunningCutReasonMustPassGuardBeforeCritical();
        counterIncrementsMustAccumulateAcrossReset();
        ignitionCounterIncrementsMustAccumulateAcrossReset();
        ignitionCounterIncrementsMustBeAttributedByState();
        receivedZeroMustRemainDifferentFromMissing();
        System.out.println("SessionMonitorRegressionTest passed");
    }

    private static void runningLowLoadTriggerFaultMustRemainCritical() {
        SessionMonitor monitor = new SessionMonitor();
        monitor.addSample(sample(1L, 0.01, State.RUNNING, 900.0, 5.0, 60.0,
                0.0, 10.0, 15.0, 0.0, 0.0, 0.0));
        monitor.addSample(sample(2L, 0.02, State.RUNNING, 880.0, 4.0, 58.0,
                1.0, 13.0, 14.0, 0.0, 0.0, 0.0));

        SessionMonitor.Snapshot snapshot = monitor.snapshot();
        require(!snapshot.hasData(), "Low-load running samples must not become full-load evidence");
        require(snapshot.hasTriggerSyncFault(), "Running trigger activity must remain critical");
        require(snapshot.hasSessionFaultOrCut(), "Running trigger activity must require review");
        require(close(snapshot.triggerErrorCountDelta, 3.0), "Running trigger-counter increase must be 3");
        require(close(snapshot.crankingTriggerErrorCountDelta, 0.0), "Running increase must not leak into cranking");
        require(close(snapshot.keyOffTriggerErrorCountDelta, 0.0), "Running increase must not leak into key-off");

        SessionReview review = SessionReview.build(Collections.<EventSummary>emptyList(), snapshot);
        require(review.triggerSyncNeedsReview(), "Running trigger/sync evidence must be prioritized");
        require(review.recommendedNextStep().contains("running trigger/sync loss"),
                "Recommendation must identify a running trigger/sync loss");
    }

    private static void crankingTriggerActivityMustRemainDiagnostic() {
        SessionMonitor monitor = new SessionMonitor();
        monitor.addSample(sample(3L, 0.03, State.CRANKING, 190.0, 0.0, 99.0,
                0.0, 20.0, 10.0, 0.0, 0.0, 0.0));
        monitor.addSample(sample(4L, 0.04, State.CRANKING, 210.0, 0.0, 98.0,
                1.0, 22.0, 10.0, 0.0, 0.0, 0.0));

        SessionMonitor.Snapshot snapshot = monitor.snapshot();
        require(!snapshot.hasTriggerSyncFault(), "Cranking trigger activity must not be a running fault");
        require(!snapshot.hasSessionFaultOrCut(), "Cranking activity must not create a critical recommendation");
        require(snapshot.hasCrankingTriggerActivity(), "Cranking trigger activity must remain visible diagnostically");
        require(close(snapshot.crankingTriggerErrorCountDelta, 2.0), "Cranking counter increase must be retained");

        SessionReview review = SessionReview.build(Collections.<EventSummary>emptyList(), snapshot);
        require(review.toDisplayText().contains("Cranking trigger activity: seen (diagnostic, not a running fault)"),
                "Report must classify cranking trigger activity as diagnostic");
        require(!review.recommendedNextStep().contains("trigger/sync loss"),
                "Cranking-only activity must not replace the tuning recommendation");
    }

    private static void keyOffActivityMustNotBecomeRunningFault() {
        SessionMonitor monitor = new SessionMonitor();
        monitor.addSample(sample(5L, 0.05, State.RUNNING, 900.0, 0.0, 50.0,
                0.0, 30.0, 15.0, 0.0, 0.0, 0.0));
        LiveSample keyOff = sample(6L, 0.06, State.KEY_OFF, 250.0, 0.0, 100.0,
                1.0, 31.0, 0.0, 0.0, 0.0, 0.0);
        EnumMap<ChannelRole, Double> keyOffValues = copyValues(keyOff);
        keyOffValues.put(ChannelRole.IGN_CUT_CODE, 14.0);
        keyOffValues.put(ChannelRole.FUEL_CUT_CODE, 14.0);
        monitor.addSample(new LiveSample(6L, 0.06, keyOffValues, 0.0, 0.0));

        SessionMonitor.Snapshot snapshot = monitor.snapshot();
        require(!snapshot.hasTriggerSyncFault(), "Key-off trigger activity must not be a running fault");
        require(!snapshot.hasSessionFaultOrCut(), "Key-off cuts must not create a critical recommendation");
        require(snapshot.hasKeyOffTriggerActivity(), "Key-off trigger activity must remain visible");
        require(snapshot.keyOffFaultOrCut, "Key-off cut activity must be retained diagnostically");
        require(close(snapshot.keyOffTriggerErrorCountDelta, 1.0), "Key-off counter increase must be retained");

        String report = SessionReview.build(Collections.<EventSummary>emptyList(), snapshot).toDisplayText();
        require(report.contains("Key-off/coast-down trigger activity: seen and excluded"),
                "Report must state that key-off trigger activity was excluded");
        require(report.contains("Key-off fault/cut activity: seen and excluded"),
                "Report must state that key-off cuts were excluded");
    }

    private static void laggingRunningMustYieldToEitherIgnitionOffSignal() {
        SessionMonitor monitor = new SessionMonitor();
        monitor.addSample(sample(100_000_000L, 1.00, State.RUNNING, 900.0, 0.0, 50.0,
                0.0, 0.0, 15.0, 0.0, 0.0, 0.0));
        LiveSample stale = sample(120_000_000L, 1.02, State.RUNNING, 650.0, 0.0, 80.0,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        EnumMap<ChannelRole, Double> values = copyValues(stale);
        values.put(ChannelRole.ENGINE_RUNNING, 1.0);
        values.put(ChannelRole.MAIN_RELAY_HAS_IGN, 0.0);
        values.put(ChannelRole.IGNITION_ON, 1.0);
        values.put(ChannelRole.IGN_CUT_CODE, 14.0);
        values.put(ChannelRole.FUEL_CUT_CODE, 14.0);
        monitor.addSample(new LiveSample(120_000_000L, 1.02, values, 0.0, 0.0));
        SessionMonitor.Snapshot snapshot = monitor.snapshot();
        require(snapshot.keyOffSamples > 0L, "Either explicit ignition-off signal must override a lagging running flag");
        require(!snapshot.hasSessionFaultOrCut(), "Shutdown code 14 with lagging running must not become a running fault");
        require(snapshot.keyOffFaultOrCut, "Shutdown reason codes must remain visible as key-off diagnostic context");
    }

    private static void actualRunningCutOutputMustRemainImmediate() {
        SessionMonitor monitor = new SessionMonitor();
        LiveSample running = sample(200_000_000L, 2.00, State.RUNNING, 1200.0, 5.0, 60.0,
                0.0, 0.0, 15.0, 0.0, 0.0, 0.0);
        EnumMap<ChannelRole, Double> values = copyValues(running);
        values.put(ChannelRole.TOTAL_SPARK_CUT, 1.0);
        monitor.addSample(new LiveSample(200_000_000L, 2.00, values, 0.0, 0.0));
        SessionMonitor.Snapshot snapshot = monitor.snapshot();
        require(snapshot.hasSessionFaultOrCut(), "Actual running spark-cut output must remain critical");
        require(snapshot.cutEvidence.runningActualSparkCut, "Actual running spark-cut output must be reported separately");
        require(!snapshot.cutEvidence.runningIgnitionCutReason, "Actual output must not be misreported as a confirmed reason code");
    }

    private static void persistentRunningCutReasonMustPassGuardBeforeCritical() {
        SessionMonitor monitor = new SessionMonitor();
        long[] nanos = new long[]{300_000_000L, 330_000_000L, 360_000_000L};
        for (int i = 0; i < nanos.length; i++) {
            LiveSample running = sample(nanos[i], 3.00 + i * 0.03, State.RUNNING,
                    1200.0, 5.0, 60.0, 0.0, 0.0, 15.0, 0.0, 0.0, 0.0);
            EnumMap<ChannelRole, Double> values = copyValues(running);
            values.put(ChannelRole.IGN_CUT_CODE, 7.0);
            monitor.addSample(new LiveSample(nanos[i], 3.00 + i * 0.03, values, 0.0, 0.0));
            if (i < 2) require(!monitor.snapshot().hasSessionFaultOrCut(), "A transient running reason code must remain guarded");
        }
        SessionMonitor.Snapshot snapshot = monitor.snapshot();
        require(snapshot.hasSessionFaultOrCut(), "A persistent coherent running cut reason must become critical after the guard");
        require(snapshot.cutEvidence.runningIgnitionCutReason, "Confirmed running ignition reason must be reported separately");
        require(snapshot.cutEvidence.guardedIgnitionReasonSamples == 2L, "The two pre-confirmation reason samples must be counted as guarded");
    }

    private static void counterIncrementsMustAccumulateAcrossReset() {
        SessionMonitor monitor = new SessionMonitor();
        monitor.addSample(sample(7L, 0.07, State.RUNNING, 1000.0, 0.0, 50.0,
                0.0, 10.0, 15.0, 0.0, 0.0, 0.0));
        monitor.addSample(sample(8L, 0.08, State.RUNNING, 1000.0, 0.0, 50.0,
                0.0, 13.0, 15.0, 0.0, 0.0, 0.0));
        monitor.addSample(sample(9L, 0.09, State.KEY_OFF, 0.0, 0.0, 100.0,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
        monitor.addSample(sample(10L, 0.10, State.RUNNING, 1000.0, 0.0, 50.0,
                0.0, 2.0, 15.0, 0.0, 0.0, 0.0));

        SessionMonitor.Snapshot snapshot = monitor.snapshot();
        require(close(snapshot.triggerErrorCountDelta, 5.0),
                "Positive trigger-counter increments must accumulate across a reset");
        require(snapshot.triggerErrorCountResets == 1L, "Trigger-counter reset must be counted separately");
    }

    private static void ignitionCounterIncrementsMustAccumulateAcrossReset() {
        SessionMonitor monitor = new SessionMonitor();
        monitor.addSample(sampleWithIgnitionCounters(11L, 0.11, 43.0));
        monitor.addSample(sampleWithIgnitionCounters(12L, 0.12, 48.0));
        monitor.addSample(sampleWithIgnitionCounters(13L, 0.13, 0.0));
        monitor.addSample(sampleWithIgnitionCounters(14L, 0.14, 2.0));

        SessionMonitor.Snapshot snapshot = monitor.snapshot();
        require(close(snapshot.underchargeWarningsDelta, 7.0),
                "Ignition counter positive increments must accumulate across reset");
        require(snapshot.underchargeWarningsResets == 1L,
                "Ignition counter reset must be counted separately");
    }

    private static void ignitionCounterIncrementsMustBeAttributedByState() {
        SessionMonitor monitor = new SessionMonitor();
        monitor.addSample(sampleWithIgnitionCountersForState(400_000_000L, 4.00, State.RUNNING, 10.0));
        monitor.addSample(sampleWithIgnitionCountersForState(410_000_000L, 4.01, State.RUNNING, 13.0));
        monitor.addSample(sampleWithIgnitionCountersForState(420_000_000L, 4.02, State.CRANKING, 15.0));
        monitor.addSample(sampleWithIgnitionCountersForState(430_000_000L, 4.03, State.KEY_OFF, 20.0));
        SessionMonitor.Snapshot snapshot = monitor.snapshot();
        PositiveCounter.Snapshot counter = snapshot.underchargeCounter;
        require(close(counter.increase, 10.0), "Total ignition-counter increase must remain 10");
        require(close(counter.runningIncrease, 3.0), "Running ignition-counter increase must be 3");
        require(close(counter.crankingIncrease, 2.0), "Cranking ignition-counter increase must be 2");
        require(close(counter.keyOffIncrease, 5.0), "Key-off ignition-counter increase must be 5");
        String report = SessionReview.build(Collections.<EventSummary>emptyList(), snapshot).toDisplayText();
        require(report.contains("running +3.0, cranking +2.0, key-off +5.0"), "Report must expose per-state ignition-counter increments");
    }

    private static void receivedZeroMustRemainDifferentFromMissing() {
        SessionMonitor monitor = new SessionMonitor();
        monitor.addSample(sample(15L, 0.15, State.RUNNING, 1000.0, 2.0, 55.0,
                0.0, 20.0, 16.0, 0.0, 0.0, 0.0));

        SessionMonitor.Snapshot snapshot = monitor.snapshot();
        require(snapshot.ignitionTimingSamples == 1L, "Timing: ignition must be recorded");
        require(snapshot.boostTargetSamples == 1L, "Boost: Target zero must be recorded as received");
        require(snapshot.fuelPressureHighSamples == 1L, "High fuel-pressure zero must be recorded as received");
        require(snapshot.fuelPressureLowSamples == 1L, "Low fuel-pressure zero must be recorded as received");

        String report = SessionReview.build(Collections.<EventSummary>emptyList(), snapshot).toDisplayText();
        require(report.contains("Boost: Target: received, zero/inactive"),
                "Resolved Boost: Target zero must not be reported as missing");
        require(report.contains("Fuel pressure _high: received, zero/inactive"),
                "Resolved high fuel-pressure zero must not be reported as missing");
        require(report.contains("Timing: ignition: received 16.0..16.0 deg"),
                "Timing range must be reported when values are received");
    }

    private static LiveSample sampleWithIgnitionCountersForState(long nanoTime,
                                                                  double seconds,
                                                                  State state,
                                                                  double undercharge) {
        LiveSample base = sample(nanoTime, seconds, state,
                state == State.CRANKING ? 200.0 : (state == State.KEY_OFF ? 250.0 : 1000.0),
                0.0, 50.0, 0.0, 0.0, 15.0, 0.0, 0.0, 0.0);
        EnumMap<ChannelRole, Double> values = copyValues(base);
        values.put(ChannelRole.IGN_UNDERCHARGE_WARNINGS, undercharge);
        return new LiveSample(nanoTime, seconds, values, 0.0, 0.0);
    }

    private static LiveSample sampleWithIgnitionCounters(long nanoTime, double seconds, double undercharge) {
        LiveSample base = sample(nanoTime, seconds, State.RUNNING, 1000.0, 0.0, 50.0,
                0.0, 0.0, 15.0, 0.0, 0.0, 0.0);
        EnumMap<ChannelRole, Double> values = copyValues(base);
        values.put(ChannelRole.IGN_UNDERCHARGE_WARNINGS, undercharge);
        return new LiveSample(nanoTime, seconds, values, 0.0, 0.0);
    }

    private static LiveSample sample(long nanoTime, double seconds, State state,
                                     double rpm, double tps, double map,
                                     double triggerError, double triggerCount,
                                     double timing, double boostTarget,
                                     double fuelPressureHigh, double fuelPressureLow) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.MAP, map);
        values.put(ChannelRole.BATTERY, state == State.KEY_OFF ? 0.0 : 13.8);
        values.put(ChannelRole.ENGINE_RUNNING, state == State.RUNNING ? 1.0 : 0.0);
        values.put(ChannelRole.ENGINE_CRANKING, state == State.CRANKING ? 1.0 : 0.0);
        values.put(ChannelRole.IGNITION_ON, state == State.KEY_OFF ? 0.0 : 1.0);
        values.put(ChannelRole.MAIN_RELAY_HAS_IGN, state == State.KEY_OFF ? 0.0 : 1.0);
        values.put(ChannelRole.TRIGGER_ERROR, triggerError);
        values.put(ChannelRole.TRIGGER_ERROR_COUNT, triggerCount);
        values.put(ChannelRole.IGNITION_TIMING, timing);
        values.put(ChannelRole.BOOST_TARGET, boostTarget);
        values.put(ChannelRole.FUEL_PRESSURE_HIGH, fuelPressureHigh);
        values.put(ChannelRole.FUEL_PRESSURE_LOW, fuelPressureLow);
        values.put(ChannelRole.IGNITION_FAULT, 0.0);
        values.put(ChannelRole.INJECTOR_FAULT, 0.0);
        values.put(ChannelRole.TOTAL_SPARK_CUT, 0.0);
        values.put(ChannelRole.IGN_CUT_CODE, 0.0);
        values.put(ChannelRole.FUEL_CUT, 0.0);
        values.put(ChannelRole.FUEL_CUT_CODE, 0.0);
        values.put(ChannelRole.STOP_ENGINE_CODE, 0.0);
        values.put(ChannelRole.IGN_OVERDWELL, 0.0);
        values.put(ChannelRole.IGN_OVERCHARGE_WARNINGS, 0.0);
        values.put(ChannelRole.IGN_UNDERCHARGE_WARNINGS, 0.0);
        values.put(ChannelRole.IGN_SPARK_OUT_OF_ORDER, 0.0);
        return new LiveSample(nanoTime, seconds, values, 0.0, 0.0);
    }

    private static EnumMap<ChannelRole, Double> copyValues(LiveSample sample) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        for (ChannelRole role : ChannelRole.values()) {
            double value = sample.get(role);
            if (Double.isFinite(value)) values.put(role, value);
        }
        return values;
    }

    private enum State { RUNNING, CRANKING, KEY_OFF }

    private static boolean close(double actual, double expected) {
        return Double.isFinite(actual) && Math.abs(actual - expected) < 0.000001;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
