package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
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
        naturalPedalPlateauIsAcceptedWithoutAbsoluteTarget();
        differentRoadLoadCreatesAnotherValidGroupInsteadOfExclusion();
        finalComparableEventSurvivesSeriesCompleteTransition();
        driverTargetBaselineAppearsOnlyAfterOpeningIsFrozen();
        inactiveFallbackGapCannotBecomeMeasurementAnchor();
        System.out.println("GuidedVehicleTest9RegressionTest passed");
    }

    private static void rollingBaselineAcceptsGradualRoadLoadChange() {
        BlendDurationGuidedSession session = session(3);
        double time = settle(session, 0.0, 50.0, 8.0);
        require(session.snapshot().state == GuidedCaptureState.READY,
                "trend-aware road baseline did not reach READY");
        double initial = session.baselineTpsForDisplay();

        // Continue smoothly from the end of settle() rather than stepping the
        // pedal backward before the synthetic hill/load correction.
        for (int i = 1; i <= 8; i++) {
            double t = time + i * 0.05;
            session.accept(sample(t, 2045.0 + i * 12.0,
                    51.68 + i * 0.25, 8.42 + i * 0.14,
                    51.68 + i * 0.25, false));
        }
        require(session.snapshot().state == GuidedCaptureState.READY,
                "gradual hill/load correction cancelled READY");
        require(Double.isNaN(initial),
                "READY rolling baseline must not be exposed as a moving driver target");
        require(Double.isNaN(session.baselineTpsForDisplay()),
                "gradual READY road corrections must not create a chasing TPS target");
    }

    private static void naturalPedalPlateauIsAcceptedWithoutAbsoluteTarget() {
        BlendDurationGuidedSession session = session(3);
        double time = settle(session, 0.0, 50.0, 8.0);
        time = opening(session, time, 50.0, 8.0, 31.5);
        GuidedOutcome outcome = session.drainOutcome();
        require(outcome != null && outcome.isValid(),
                "jumpy but bounded natural pedal plateau was not retained");
        require("A".equals(outcome.groupId),
                "first valid road event did not establish group A");
        require(session.snapshot().result.contains("Natural held TPS"),
                "adaptive result did not report measured natural hold");
        require(session.snapshot().result.contains("TPS step: +"),
                "adaptive result did not report relative TPS step");
    }

    private static void differentRoadLoadCreatesAnotherValidGroupInsteadOfExclusion() {
        BlendDurationGuidedSession session = session(3);
        double time = settle(session, 0.0, 50.0, 8.0);
        time = opening(session, time, 50.0, 8.0, 31.5);
        GuidedOutcome first = session.drainOutcome();
        require(first != null && first.isValid(), "reference road event missing");

        time = settleAfterOutcome(session, time, 62.0, 10.0);
        opening(session, time, 62.0, 10.0, 33.0);
        GuidedOutcome second = session.drainOutcome();
        require(second != null && second.isValid(),
                "clean event at a different road load was incorrectly excluded");
        require(!first.groupId.equals(second.groupId),
                "materially different baseline load was mixed into one group");
        require(session.snapshot().state == GuidedCaptureState.ACCEPTED
                        || session.snapshot().state == GuidedCaptureState.WARNING,
                "different-load valid event ended in exclusion state");
    }

    private static void finalComparableEventSurvivesSeriesCompleteTransition() {
        BlendDurationGuidedSession session = session(2);
        double time = settle(session, 0.0, 50.0, 8.0);
        time = opening(session, time, 50.0, 8.0, 31.5);
        GuidedOutcome first = session.drainOutcome();
        require(first != null && first.isValid(), "first valid event missing");

        time = settleAfterOutcome(session, time, 62.0, 10.0);
        time = opening(session, time, 62.0, 10.0, 33.0);
        GuidedOutcome otherGroup = session.drainOutcome();
        require(otherGroup != null && otherGroup.isValid(),
                "second load-group event missing");

        time = settleAfterOutcome(session, time, 51.5, 8.5);
        opening(session, time, 51.5, 8.5, 32.0);
        require(session.snapshot().state == GuidedCaptureState.COMPLETE,
                "best adaptive group reaching target did not complete series");
        GuidedOutcome finalOutcome = session.drainOutcome();
        require(finalOutcome != null && finalOutcome.isValid(),
                "final accepted event was swallowed by SERIES COMPLETE transition");
        require("A".equals(finalOutcome.groupId)
                        && finalOutcome.groupCount == 2,
                "final event did not enter the comparable group before completion");
        require(session.validCount() == 3,
                "series completion lost a valid event from the session count");
    }

    private static void driverTargetBaselineAppearsOnlyAfterOpeningIsFrozen() {
        GuidedVehicleTestLimits.restoreCandidateDefaults();
        BlendDurationGuidedSession session = session(3);
        double time = settleFlat(session, 0.0, 50.0, 8.0);
        require(session.snapshot().state == GuidedCaptureState.READY,
                "flat road baseline did not reach READY");
        require(Double.isNaN(session.baselineTpsForDisplay()),
                "READY must not expose its rolling baseline as a driver target");

        session.accept(sampleDetailed(time + 0.05, 2000.0,
                50.2, 9.2, 50.2, false, false));
        require(session.snapshot().state == GuidedCaptureState.OPENING_PENDING,
                "small opening did not enter OPENING_PENDING");
        double frozen = session.baselineTpsForDisplay();
        require(Double.isFinite(frozen),
                "OPENING_PENDING must expose the now-frozen pre-opening baseline");

        session.accept(sampleDetailed(time + 0.10, 2005.0,
                50.5, 9.5, 50.5, false, false));
        require(session.snapshot().state == GuidedCaptureState.OPENING_PENDING,
                "sub-threshold pending opening was confirmed too early");
        require(Math.abs(session.baselineTpsForDisplay() - frozen) < 0.0001,
                "frozen driver-target baseline moved with TPS during OPENING_PENDING");
    }

    private static void inactiveFallbackGapCannotBecomeMeasurementAnchor() {
        GuidedVehicleTestLimits.restoreCandidateDefaults();
        BlendDurationGuidedSession session = session(3);
        double time = settleFlat(session, 0.0, 50.0, 8.0);
        require(session.snapshot().state == GuidedCaptureState.READY,
                "Archive 13 anchor regression did not reach READY");

        // Archive-13-shaped opening: fallbackMap is stale and extremely high
        // before MAP prediction starts. That inactive gap must never define the
        // 90% catch-up threshold.
        session.accept(sampleDetailed(time + 0.05, 2000.0,
                51.17, 10.3, 112.80, false, false));
        session.accept(sampleDetailed(time + 0.10, 2010.0,
                52.34, 11.8, 112.80, false, false));
        session.accept(sampleDetailed(time + 0.15, 2020.0,
                54.01, 13.9, 112.80, false, false));

        // Current-event prediction begins here. The largest eligible gap is
        // 96.10 - 73.02 = 23.08 kPa, not the stale 61.63 kPa above.
        session.accept(sampleDetailed(time + 0.20, 2040.0,
                62.19, 19.1, 80.40, true, true));
        session.accept(sampleDetailed(time + 0.25, 2055.0,
                73.02, 26.0, 96.10, true, true));
        session.accept(sampleDetailed(time + 0.30, 2070.0,
                80.00, 29.0, 96.00, true, true));
        session.accept(sampleDetailed(time + 0.35, 2080.0,
                84.00, 29.2, 97.00, true, true));
        session.accept(sampleDetailed(time + 0.40, 2090.0,
                87.00, 29.1, 98.00, true, true));
        session.accept(sampleDetailed(time + 0.45, 2100.0,
                90.00, 29.0, 99.00, false, false));
        session.accept(sampleDetailed(time + 0.50, 2110.0,
                92.00, 29.1, 99.00, false, false));

        // Correct prediction-active threshold is about 93.79 kPa and is
        // reached while the pedal is still held. The stale-gap implementation
        // instead targets about 106.64 kPa and cannot complete this event.
        session.accept(sampleDetailed(time + 0.60, 2120.0,
                94.71, 29.1, 99.00, false, false));
        session.accept(sampleDetailed(time + 0.65, 2130.0,
                95.00, 29.0, 99.00, false, false));

        GuidedOutcome outcome = session.drainOutcome();
        require(outcome != null && outcome.isValid(),
                "prediction-active Archive 13 catch-up was not retained");
        require(outcome.trace.contains("gap_kpa=23.08"),
                "measurement trace did not select the prediction-active 23.08 kPa gap");
        require(!outcome.trace.contains("gap_kpa=61.63"),
                "inactive stale fallback gap still became the measurement anchor");
        require(outcome.durationSeconds > 0.30 && outcome.durationSeconds < 0.40,
                "Archive 13 shaped catch-up duration did not come from the active prediction anchor");
    }

    private static BlendDurationGuidedSession session(int targetCount) {
        BlendDurationGuidedSession session = new BlendDurationGuidedSession();
        session.start(new BlendDurationCaptureConfig(
                2000.0, 22.0, targetCount, 2, false));
        return session;
    }

    private static double settle(BlendDurationGuidedSession session,
                                 double start, double map, double tps) {
        double time = start;
        for (int i = 0; i < 22; i++) {
            session.accept(sample(time,
                    1940.0 + i * 5.0,
                    map + i * 0.08,
                    tps + i * 0.02,
                    map + i * 0.08,
                    false));
            time += 0.05;
        }
        return time;
    }

    private static double settleFlat(BlendDurationGuidedSession session,
                                     double start, double map, double tps) {
        double time = start;
        for (int i = 0; i < 22; i++) {
            session.accept(sampleDetailed(time, 2000.0, map, tps,
                    map, false, false));
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
        session.accept(sample(time + 0.05, 2000.0, baseMap,
                baseTps + 10.0, baseMap + 22.0, true));
        session.accept(sample(time + 0.10, 2010.0, baseMap + 7.0,
                heldTps - 2.5, baseMap + 31.0, false));
        session.accept(sample(time + 0.15, 2025.0, baseMap + 11.0,
                heldTps + 1.0, baseMap + 32.0, false));
        session.accept(sample(time + 0.20, 2040.0, baseMap + 15.0,
                heldTps - 0.8, baseMap + 32.0, false));
        session.accept(sample(time + 0.25, 2055.0, baseMap + 19.0,
                heldTps + 0.6, baseMap + 32.0, false));
        session.accept(sample(time + 0.30, 2070.0, baseMap + 22.0,
                heldTps, baseMap + 32.0, false));
        session.accept(sample(time + 0.55, 2110.0, baseMap + 30.5,
                heldTps + 0.5, baseMap + 32.0, false));
        session.accept(sample(time + 0.70, 2130.0, baseMap + 31.0,
                heldTps - 0.3, baseMap + 32.0, false));
        return time + 0.70;
    }

    private static LiveSample sample(double seconds, double rpm,
                                     double map, double tps,
                                     double fallback, boolean trigger) {
        return sampleDetailed(seconds, rpm, map, tps, fallback,
                trigger, trigger);
    }

    private static LiveSample sampleDetailed(double seconds, double rpm,
                                             double map, double tps,
                                             double fallback,
                                             boolean detector,
                                             boolean prediction) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.MAP, map);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.FALLBACK_MAP, fallback);
        values.put(ChannelRole.ENGINE_RUNNING, 1.0);
        values.put(ChannelRole.ENGINE_CRANKING, 0.0);
        values.put(ChannelRole.FUEL_CUT, 0.0);
        values.put(ChannelRole.TOTAL_SPARK_CUT, 0.0);
        values.put(ChannelRole.TRIGGER_ERROR, 0.0);
        values.put(ChannelRole.MAP_PRED_ACTIVE, prediction ? 1.0 : 0.0);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, detector ? 1.0 : 0.0);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, detector ? 3.0 : 0.0);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.0);
        values.put(ChannelRole.GEAR, 2.0);
        values.put(ChannelRole.VSS, 40.0);
        long nano = Math.round(seconds * 1000000000.0);
        return new LiveSample(nano, seconds, values,
                detector ? 60.0 : 0.0, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
