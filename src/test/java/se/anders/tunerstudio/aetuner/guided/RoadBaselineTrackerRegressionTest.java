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

public final class RoadBaselineTrackerRegressionTest {
    private RoadBaselineTrackerRegressionTest() { }

    public static void main(String[] args) {
        actualBinWindowsRemainNarrowAndExplicit();
        gradualRoadTrendProducesProjectedBaseline();
        offBinBaselineDoesNotAcquireReady();
        recoveryIntervalRemainsPartOfAcquireGate();
        abruptReadyMovementCancelsOnceItEntersHistory();
        System.out.println("RoadBaselineTrackerRegressionTest passed");
    }

    private static void actualBinWindowsRemainNarrowAndExplicit() {
        require(RoadBaselineTracker.RPM_ACQUIRE_TOLERANCE == 200.0,
                "actual-bin READY acquisition tolerance changed");
        require(RoadBaselineTracker.RPM_READY_RELEASE_TOLERANCE == 200.0,
                "READY retention must stay on the same selected table bin");
        require(RoadBaselineTracker.RPM_CAPTURE_TOLERANCE == 300.0,
                "active capture RPM drift allowance changed");
    }

    private static void gradualRoadTrendProducesProjectedBaseline() {
        RoadBaselineTracker tracker = new RoadBaselineTracker();
        LiveSample last = null;
        for (int i = 0; i < 22; i++) {
            double t = i * 0.05;
            last = sample(t, 2540.0 + i * 5.0,
                    50.0 + i * 0.08, 8.0 + i * 0.02,
                    false, false);
            tracker.add(last);
        }
        RoadBaselineTracker.AcquireCheck check =
                tracker.acquireCheck(last, 2600.0, 0L, 1.50);
        require(check.ready, "gradual road trend near actual table bin did not reach READY");
        RoadBaselineTracker.Baseline baseline = tracker.baseline(false);
        require(baseline.valid(), "projected road baseline is invalid");
        require(Math.abs(baseline.rpm - last.get(ChannelRole.RPM)) < 2.0,
                "RPM trend-end projection changed");
        require(Math.abs(baseline.map - last.get(ChannelRole.MAP)) < 0.1,
                "MAP trend-end projection changed");
        require(Math.abs(baseline.tps - last.get(ChannelRole.TPS)) < 0.05,
                "TPS trend-end projection changed");
    }

    private static void offBinBaselineDoesNotAcquireReady() {
        RoadBaselineTracker tracker = new RoadBaselineTracker();
        LiveSample last = null;
        for (int i = 0; i < 22; i++) {
            double t = i * 0.05;
            last = sample(t, 2320.0 + i,
                    50.0 + i * 0.02, 8.0 + i * 0.01,
                    false, false);
            tracker.add(last);
        }
        RoadBaselineTracker.AcquireCheck check =
                tracker.acquireCheck(last, 2600.0, 0L, 1.50);
        require(!check.ready,
                "a smooth baseline more than 200 RPM from the selected bin incorrectly reached READY");
        require(check.text.contains("2600 ±200"),
                "READY diagnostics do not expose the actual-bin RPM window");
    }

    private static void recoveryIntervalRemainsPartOfAcquireGate() {
        RoadBaselineTracker tracker = settled();
        LiveSample sample = sample(2.0, 2600.0, 51.0, 8.2, false, false);
        tracker.add(sample);
        RoadBaselineTracker.AcquireCheck blocked =
                tracker.acquireCheck(sample, 2600.0,
                        sample.getNanoTime() - 500000000L, 1.50);
        require(!blocked.ready && !blocked.recovered,
                "recovery interval stopped gating baseline acquisition");
        RoadBaselineTracker.AcquireCheck recovered =
                tracker.acquireCheck(sample, 2600.0,
                        sample.getNanoTime() - 1600000000L, 1.50);
        require(recovered.recovered,
                "completed recovery interval was not recognized");
    }

    private static void abruptReadyMovementCancelsOnceItEntersHistory() {
        RoadBaselineTracker tracker = settled();
        LiveSample abrupt = sample(1.10, 2600.0, 62.0, 16.0, false, false);
        tracker.add(abrupt);
        RoadBaselineTracker.ReadyCheck immediate =
                tracker.readyCheck(abrupt, 2600.0);
        require(immediate.ready,
                "READY no longer excludes the newest sample from its trend window");

        LiveSample next = sample(1.15, 2600.0, 62.0, 16.0, false, false);
        tracker.add(next);
        RoadBaselineTracker.ReadyCheck check = tracker.readyCheck(next, 2600.0);
        require(!check.ready,
                "abrupt movement was not rejected after entering READY history");
        require(check.instruction.length() > 0,
                "READY rejection lost corrective instruction");
    }

    private static RoadBaselineTracker settled() {
        RoadBaselineTracker tracker = new RoadBaselineTracker();
        for (int i = 0; i < 22; i++) {
            double t = i * 0.05;
            tracker.add(sample(t, 2580.0 + i * 2.0,
                    50.0 + i * 0.03, 8.0 + i * 0.01,
                    false, false));
        }
        return tracker;
    }

    private static LiveSample sample(double seconds, double rpm,
                                     double map, double tps,
                                     boolean detector, boolean prediction) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.MAP, map);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.FALLBACK_MAP, map);
        values.put(ChannelRole.ENGINE_RUNNING, 1.0);
        values.put(ChannelRole.ENGINE_CRANKING, 0.0);
        values.put(ChannelRole.FUEL_CUT, 0.0);
        values.put(ChannelRole.TOTAL_SPARK_CUT, 0.0);
        values.put(ChannelRole.TRIGGER_ERROR, 0.0);
        values.put(ChannelRole.MAP_PRED_ACTIVE, prediction ? 1.0 : 0.0);
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
