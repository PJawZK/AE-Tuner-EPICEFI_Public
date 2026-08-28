package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.EnumMap;

public final class GuidedEventGearIntegrationRegressionTest {
    private GuidedEventGearIntegrationRegressionTest() { }

    public static void main(String[] args) {
        sustainedEventMismatchChangesOnlyComparisonGear();
        System.out.println("GuidedEventGearIntegrationRegressionTest passed");
    }

    private static void sustainedEventMismatchChangesOnlyComparisonGear() {
        RoadBaselineTracker tracker = new RoadBaselineTracker();
        LiveSample ready = null;
        for (int i = 0; i < 20; i++) {
            ready = sample(1.00 + i * 0.05, 2.0, 26.0 + i * 0.05);
            tracker.add(ready);
        }
        RoadBaselineTracker.AcquireCheck check = tracker.acquireCheck(
                ready, 2000.0, 0L, 1.50);
        require(check.ready, "gear-2 baseline did not become READY");
        tracker.add(triggerSample(2.05, 2.0, 27.0));
        RoadBaselineTracker.Baseline baseline = tracker.baseline(false);
        require(baseline.sessionDetectedGear() == 2,
                "session gear did not latch to the clean READY gear");

        GuidedAttemptEvidence normalEvidence = new GuidedAttemptEvidence();
        for (int i = 0; i < 10; i++) {
            normalEvidence.add(sample(3.00 + i * 0.015, 2.0, 27.0 + i * 0.1));
        }
        BlendDurationAttempt normal = normalEvidence.buildAttempt(
                1, baseline, measurement(), hold(), end(), 0.16,
                autoSettings());
        require(!normal.eventGearMismatch && normal.comparisonGear() == 2,
                "session-matching event did not retain comparison gear 2");

        GuidedAttemptEvidence shiftedEvidence = new GuidedAttemptEvidence();
        for (int i = 0; i < 10; i++) {
            shiftedEvidence.add(sample(4.00 + i * 0.015, 3.0, 38.0 + i * 0.1));
        }
        BlendDurationAttempt shifted = shiftedEvidence.buildAttempt(
                2, baseline, measurement(), hold(), end(), 0.15,
                autoSettings());

        require(shifted.detectedGear == 2 && shifted.detectedGearLatched,
                "event mismatch changed the immutable session gear latch");
        require(shifted.eventGearMismatch && shifted.eventDetectedGear == 3,
                "sustained clean event gear 3 was not marked as a mismatch");
        require(shifted.comparisonGear() == 3,
                "mismatched event still reports session gear 2 for comparability");
        require(shifted.gearText().contains("SUSTAINED SESSION-GEAR MISMATCH")
                        && shifted.gearText().contains("session latch unchanged"),
                "event mismatch is not explicit in exported gear evidence");

        BlendDurationComparabilityGroups groups = new BlendDurationComparabilityGroups();
        BlendDurationComparabilityGroups.Assignment first = groups.assign(normal);
        BlendDurationComparabilityGroups.Assignment second = groups.assign(shifted);
        require("A".equals(first.groupId) && "B".equals(second.groupId),
                "real event-local gear change contaminated the latched-gear comparison group");
    }

    private static BlendDurationCaptureConfig autoSettings() {
        return new BlendDurationCaptureConfig(2000.0, 20.0, 5, 0, true);
    }

    private static LiveSample measurement() {
        return detailed(5.00, 2000.0, 55.0, 20.0, 78.0, 2.0, 27.0, false);
    }

    private static LiveSample hold() {
        return detailed(5.20, 2020.0, 65.0, 30.0, 80.0, 2.0, 27.5, false);
    }

    private static LiveSample end() {
        return detailed(5.40, 2050.0, 80.0, 30.0, 80.0, 2.0, 28.0, false);
    }

    private static LiveSample sample(double seconds, double gear, double vss) {
        return detailed(seconds, 2000.0, 50.0, 8.0, 50.0, gear, vss, false);
    }

    private static LiveSample triggerSample(double seconds, double gear, double vss) {
        return detailed(seconds, 2000.0, 50.0, 8.0, 50.0, gear, vss, true);
    }

    private static LiveSample detailed(double seconds, double rpm, double map,
                                       double tps, double fallback,
                                       double gear, double vss,
                                       boolean detector) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.MAP, map);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.FALLBACK_MAP, fallback);
        values.put(ChannelRole.GEAR, gear);
        values.put(ChannelRole.VSS, vss);
        values.put(ChannelRole.ENGINE_RUNNING, 1.0);
        values.put(ChannelRole.ENGINE_CRANKING, 0.0);
        values.put(ChannelRole.FUEL_CUT, 0.0);
        values.put(ChannelRole.TOTAL_SPARK_CUT, 0.0);
        values.put(ChannelRole.TRIGGER_ERROR, 0.0);
        values.put(ChannelRole.MAP_PRED_ACTIVE, 0.0);
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, detector ? 1.0 : 0.0);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, detector ? 3.0 : 0.0);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.0);
        long nano = Math.round(seconds * 1000000000.0);
        return new LiveSample(nano, seconds, values,
                detector ? 60.0 : 0.0, 0.0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
