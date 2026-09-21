package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import java.util.EnumMap;

public final class GuidedVehicleTest9RegressionTest {
    private GuidedVehicleTest9RegressionTest() { }

    public static void main(String[] args) {
        rollingBaselineAcceptsGradualRoadLoadChange();
        stableUsablePedalPlateauIsAccepted();
        stableUsablePlateauIgnoresSuggestedStep();
        differentRoadLoadCreatesAnotherValidGroupInsteadOfExclusion();
        finalComparableEventSurvivesSeriesCompleteTransition();
        driverTargetBaselineAppearsOnlyAfterOpeningIsFrozen();
        latestPredictionTimerResetRemainsDiagnosticOnly();
        System.out.println("GuidedVehicleTest9RegressionTest passed");
    }

    private static void rollingBaselineAcceptsGradualRoadLoadChange() {
        BlendDurationGuidedSession session = session(3, 22.0);
        double time = settle(session, 0.0, 50.0, 8.0);
        require(session.snapshot().state == GuidedCaptureState.READY,
                "trend-aware road baseline did not reach READY");
        double initial = session.baselineTpsForDisplay();
        for (int i = 1; i <= 8; i++) {
            double t = time + i * 0.05;
            session.accept(sampleDetailed(t, 2045.0 + i * 12.0,
                    51.68 + i * 0.25, 8.42 + i * 0.14,
                    51.68 + i * 0.25, false, false, 2.0, 40.0));
        }
        require(session.snapshot().state == GuidedCaptureState.READY,
                "gradual hill/load correction cancelled READY");
        require(Double.isNaN(initial),
                "READY rolling baseline must not be exposed as a moving driver target");
        require(Double.isNaN(session.baselineTpsForDisplay()),
                "gradual READY road corrections must not create a chasing TPS target");
    }

    private static void stableUsablePedalPlateauIsAccepted() {
        BlendDurationGuidedSession session = session(3, 22.0);
        double time = settle(session, 0.0, 50.0, 8.0);
        opening(session, time, 50.0, 8.0, 30.0);
        GuidedOutcome outcome = session.drainOutcome();
        require(outcome != null && outcome.isValid(),
                "stable physically usable TPS-step plateau was not retained");
        require("A".equals(outcome.groupId),
                "first valid controlled road event did not establish group A");
        require(session.snapshot().result.contains("Controlled held TPS"),
                "controlled result did not report measured hold");
        require(session.snapshot().result.contains("Physical MAP 20->80 response duration"),
                "physical response duration was not reported");
        require(session.snapshot().result.contains("Prediction diagnostic only"),
                "prediction context was not visibly demoted from physical authority");
        require(outcome.trace.contains("physical_response_20_80_s="),
                "trace omitted normalized physical response duration");
        require(outcome.durationSeconds > 0.25 && outcome.durationSeconds < 0.35,
                "physical 20->80 response duration was not measured from the observed MAP step");
    }

    private static void stableUsablePlateauIgnoresSuggestedStep() {
        BlendDurationGuidedSession session = session(3, 40.0);
        double time = settle(session, 0.0, 50.0, 8.0);
        opening(session, time, 50.0, 8.0, 30.0);
        GuidedOutcome outcome = session.drainOutcome();
        require(outcome != null && outcome.isValid(),
                "stable +22 opening was rejected because the coaching suggestion was +40");
        require("A".equals(outcome.groupId) && outcome.groupCount == 1,
                "first natural usable opening did not establish its own comparable group");
        require(!outcome.details.contains("outside the requested"),
                "retired fixed TPS acceptance-window rejection leaked into the outcome");
        require(session.snapshot().result.contains(
                        "suggested TPS step: +40.0 (coaching only)"),
                "session summary does not expose the configured TPS value as coaching only");
    }

    private static void differentRoadLoadCreatesAnotherValidGroupInsteadOfExclusion() {
        BlendDurationGuidedSession session = session(3, 22.0);
        double time = settle(session, 0.0, 50.0, 8.0);
        time = opening(session, time, 50.0, 8.0, 30.0);
        GuidedOutcome first = session.drainOutcome();
        require(first != null && first.isValid(), "reference road event missing");
        time = settleAfterOutcome(session, time, 62.0, 10.0);
        opening(session, time, 62.0, 10.0, 32.0);
        GuidedOutcome second = session.drainOutcome();
        require(second != null && second.isValid(),
                "clean controlled event at a different road load was incorrectly excluded");
        require(!first.groupId.equals(second.groupId),
                "materially different baseline load was mixed into one group");
    }

    private static void finalComparableEventSurvivesSeriesCompleteTransition() {
        BlendDurationGuidedSession session = session(2, 22.0);
        double time = settle(session, 0.0, 50.0, 8.0);
        time = opening(session, time, 50.0, 8.0, 30.0);
        GuidedOutcome first = session.drainOutcome();
        require(first != null && first.isValid(), "first valid event missing");
        time = settleAfterOutcome(session, time, 62.0, 10.0);
        time = opening(session, time, 62.0, 10.0, 32.0);
        GuidedOutcome otherGroup = session.drainOutcome();
        require(otherGroup != null && otherGroup.isValid(),
                "second load-group event missing");
        time = settleAfterOutcome(session, time, 51.5, 8.5);
        opening(session, time, 51.5, 8.5, 30.5);
        require(session.snapshot().state == GuidedCaptureState.COMPLETE,
                "best controlled group reaching target did not complete series");
        GuidedOutcome finalOutcome = session.drainOutcome();
        require(finalOutcome != null && finalOutcome.isValid(),
                "final accepted event was swallowed by SERIES COMPLETE transition");
        require("A".equals(finalOutcome.groupId) && finalOutcome.groupCount == 2,
                "final event did not enter the comparable group before completion");
        require(session.validCount() == 3,
                "series completion lost a valid event from the session count");
    }

    private static void driverTargetBaselineAppearsOnlyAfterOpeningIsFrozen() {
        GuidedVehicleTestLimits.restoreCandidateDefaults();
        BlendDurationGuidedSession session = session(3, 22.0);
        double time = settleFlat(session, 0.0, 50.0, 8.0);
        require(session.snapshot().state == GuidedCaptureState.READY,
                "flat road baseline did not reach READY");
        require(Double.isNaN(session.baselineTpsForDisplay()),
                "READY must not expose its rolling baseline as a driver target");
        session.accept(sampleDetailed(time + 0.05, 2000.0,
                50.2, 9.2, 50.2, false, false, 2.0, 40.0));
        require(session.snapshot().state == GuidedCaptureState.OPENING_PENDING,
                "small opening did not enter OPENING_PENDING");
        double frozen = session.baselineTpsForDisplay();
        require(Double.isFinite(frozen),
                "OPENING_PENDING must expose the now-frozen pre-opening baseline");
        session.accept(sampleDetailed(time + 0.10, 2005.0,
                50.5, 9.5, 50.5, false, false, 2.0, 40.0));
        require(session.snapshot().state == GuidedCaptureState.OPENING_PENDING,
                "sub-threshold pending opening was confirmed too early");
        require(Math.abs(session.baselineTpsForDisplay() - frozen) < 0.0001,
                "frozen driver-target baseline moved with TPS during OPENING_PENDING");
    }

    private static void latestPredictionTimerResetRemainsDiagnosticOnly() {
        GuidedVehicleTestLimits.restoreCandidateDefaults();
        BlendDurationGuidedSession session = session(3, 22.0);
        double time = settleFlat(session, 0.0, 50.0, 8.0);
        require(session.snapshot().state == GuidedCaptureState.READY,
                "prediction diagnostic regression did not reach READY");
        session.accept(sampleDetailed(time + 0.05, 2000.0,
                54.0, 18.0, 68.0, true, true, 2.0, 40.0));
        session.accept(sampleDetailed(time + 0.10, 2010.0,
                58.0, 27.0, 74.0, true, true, 2.0, 40.0));
        session.accept(sampleDetailed(time + 0.15, 2020.0,
                62.0, 30.0, 78.0, true, true, 2.0, 40.0));
        session.accept(sampleDetailed(time + 0.20, 2030.0,
                66.0, 30.2, 86.0, true, true, 2.0, 40.0));
        session.accept(sampleDetailed(time + 0.25, 2040.0,
                70.0, 30.0, 84.0, true, true, 2.0, 40.0));
        session.accept(sampleDetailed(time + 0.30, 2050.0,
                74.0, 29.9, 82.0, true, true, 2.0, 40.0));
        session.accept(sampleDetailed(time + 0.40, 2070.0,
                82.0, 30.0, 82.0, false, false, 2.0, 40.0));
        session.accept(sampleDetailed(time + 0.50, 2090.0,
                86.2, 30.0, 82.0, false, false, 2.0, 40.0));
        session.accept(sampleDetailed(time + 0.60, 2100.0,
                86.2, 30.0, 82.0, false, false, 2.0, 40.0));
        session.accept(sampleDetailed(time + 0.70, 2110.0,
                86.2, 30.0, 82.0, false, false, 2.0, 40.0));
        session.accept(sampleDetailed(time + 0.80, 2120.0,
                86.2, 30.0, 82.0, false, false, 2.0, 40.0));
        GuidedOutcome outcome = session.drainOutcome();
        require(outcome != null && outcome.isValid(),
                "physical event was not retained while prediction diagnostics were present");
        require(outcome.trace.contains("prediction_target_diagnostic_kpa=82.00"),
                "latest lower fallbackMap was not retained as diagnostic context");
        require(outcome.trace.contains("physical_20pct_anchor_dt_s="),
                "trace lost the physical 20-percent timing anchor");
        require(outcome.durationSeconds > 0.25 && outcome.durationSeconds < 0.35,
                "physical duration still followed prediction-target timing instead of normalized MAP response");
    }

    private static BlendDurationGuidedSession session(int targetCount, double desiredStep) {
        BlendDurationGuidedSession session = new BlendDurationGuidedSession();
        session.start(new BlendDurationCaptureConfig(2000.0, desiredStep, targetCount, 2, false));
        return session;
    }

    private static double settle(BlendDurationGuidedSession session,
                                 double start, double map, double tps) {
        double time = start;
        for (int i = 0; i < 22; i++) {
            session.accept(sampleDetailed(time, 1940.0 + i * 5.0,
                    map + i * 0.08, tps + i * 0.02,
                    map + i * 0.08, false, false, 2.0, 40.0));
            time += 0.05;
        }
        return time;
    }

    private static double settleFlat(BlendDurationGuidedSession session,
                                     double start, double map, double tps) {
        double time = start;
        for (int i = 0; i < 22; i++) {
            session.accept(sampleDetailed(time, 2000.0, map, tps,
                    map, false, false, 2.0, 40.0));
            time += 0.05;
        }
        return time;
    }

    private static double settleAfterOutcome(BlendDurationGuidedSession session,
                                             double prior, double map, double tps) {
        return settle(session, prior + 2.0, map, tps);
    }

    private static double opening(BlendDurationGuidedSession session,
                                  double time, double baseMap,
                                  double baseTps, double heldTps) {
        double finalMap = baseMap + 32.0;
        session.accept(sampleDetailed(time + 0.05, 2000.0,
                baseMap + 4.0, baseTps + 10.0, baseMap + 18.0, true, true, 2.0, 40.0));
        session.accept(sampleDetailed(time + 0.10, 2010.0,
                baseMap + 8.0, heldTps - 2.5, baseMap + 25.0, true, true, 2.0, 40.0));
        session.accept(sampleDetailed(time + 0.15, 2025.0,
                baseMap + 12.0, heldTps, baseMap + 30.0, true, true, 2.0, 40.0));
        session.accept(sampleDetailed(time + 0.20, 2040.0,
                baseMap + 16.0, heldTps + 0.5, finalMap, true, true, 2.0, 40.0));
        session.accept(sampleDetailed(time + 0.25, 2055.0,
                baseMap + 20.0, heldTps - 0.2, finalMap, true, true, 2.0, 40.0));
        session.accept(sampleDetailed(time + 0.30, 2070.0,
                baseMap + 24.0, heldTps, finalMap, true, true, 2.0, 40.0));
        session.accept(sampleDetailed(time + 0.40, 2090.0,
                baseMap + 28.0, heldTps, finalMap, false, false, 2.0, 40.0));
        session.accept(sampleDetailed(time + 0.50, 2110.0,
                finalMap + 0.2, heldTps, finalMap, false, false, 2.0, 40.0));
        session.accept(sampleDetailed(time + 0.60, 2120.0,
                finalMap + 0.2, heldTps, finalMap, false, false, 2.0, 40.0));
        session.accept(sampleDetailed(time + 0.70, 2130.0,
                finalMap + 0.2, heldTps, finalMap, false, false, 2.0, 40.0));
        session.accept(sampleDetailed(time + 0.80, 2140.0,
                finalMap + 0.2, heldTps, finalMap, false, false, 2.0, 40.0));
        return time + 0.80;
    }

    private static LiveSample sampleDetailed(double seconds, double rpm,
                                             double map, double tps,
                                             double fallback,
                                             boolean detector,
                                             boolean prediction,
                                             double gear, double vss) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.MAP, map);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.FALLBACK_MAP, fallback);
        values.put(ChannelRole.EFFECTIVE_MAP, prediction ? map + (fallback - map) * 0.5 : map);
        values.put(ChannelRole.ENGINE_RUNNING, 1.0);
        values.put(ChannelRole.ENGINE_CRANKING, 0.0);
        values.put(ChannelRole.FUEL_CUT, 0.0);
        values.put(ChannelRole.TOTAL_SPARK_CUT, 0.0);
        values.put(ChannelRole.TRIGGER_ERROR, 0.0);
        values.put(ChannelRole.MAP_PRED_ACTIVE, prediction ? 1.0 : 0.0);
        values.put(ChannelRole.MAP_PRED_RESET_CNT, 10.0);
        values.put(ChannelRole.MAP_PRED_EVENT_OVER, 4.0);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, detector ? 1.0 : 0.0);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, detector ? 3.0 : 0.0);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.0);
        values.put(ChannelRole.GEAR, gear);
        values.put(ChannelRole.VSS, vss);
        long nano = Math.round(seconds * 1000000000.0);
        return new LiveSample(nano, seconds, values, detector ? 60.0 : 0.0, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
