package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModules;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.EnumMap;

/** Long-session regression for the high-rate Guided probe path. */
public final class GuidedProbeLongSessionRegressionTest {
    private static final int TOTAL_SAMPLES = 22000;
    private static final long MAX_TOTAL_MILLIS = 5000L;

    private GuidedProbeLongSessionRegressionTest() { }

    public static void main(String[] args) {
        boundedListWrapsWithoutShiftingLogicalOrder();
        probeSnapshotPathRemainsBoundedPastRetentionCap();
        foundationThresholdRetainsMultiMinuteAnalysisWindow();
        System.out.println("GuidedProbeLongSessionRegressionTest passed");
    }

    private static void boundedListWrapsWithoutShiftingLogicalOrder() {
        BoundedLiveSampleList list = new BoundedLiveSampleList(3);
        list.add(sample(1.0, false));
        list.add(sample(2.0, false));
        list.add(sample(3.0, false));
        requireClose(1.0, list.remove(0).getSeconds(),
                "bounded list removed the wrong oldest sample");
        list.add(sample(4.0, false));
        require(list.size() == 3, "bounded list size changed after wrap-around");
        requireClose(2.0, list.get(0).getSeconds(),
                "wrapped list lost oldest-retained logical order");
        requireClose(3.0, list.get(1).getSeconds(),
                "wrapped list middle element changed order");
        requireClose(4.0, list.get(2).getSeconds(),
                "wrapped list did not append newest sample at the tail");
        list.clear();
        require(list.isEmpty(), "bounded list clear did not reset storage");
    }

    private static void probeSnapshotPathRemainsBoundedPastRetentionCap() {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        session.start(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.MAP_PREDICT));

        long started = System.nanoTime();
        long earlyStarted = 0L;
        long earlyElapsed = 0L;
        long lateStarted = 0L;
        long lateElapsed = 0L;
        GuidedSessionSnapshot latest = null;

        for (int i = 0; i < TOTAL_SAMPLES; i++) {
            if (i == 2000) earlyStarted = System.nanoTime();
            if (i == 6000) earlyElapsed = System.nanoTime() - earlyStarted;
            if (i == 18000) lateStarted = System.nanoTime();

            double seconds = 10.0 + i * 0.01;
            session.accept(sample(seconds, (i % 250) < 12));
            // This intentionally mirrors GuidedCapturePanel's worker return path:
            // every delivered probe sample requests the current session snapshot.
            latest = session.snapshot();
        }
        lateElapsed = System.nanoTime() - lateStarted;
        long totalMillis = (System.nanoTime() - started) / 1000000L;

        require(latest != null && latest.state == GuidedCaptureState.CAPTURING,
                "long probe run did not remain in active capture state");
        require(latest.result.contains("Detailed retained-window metrics are calculated for Review/export")
                        && !latest.result.contains("Prediction-active samples retained:"),
                "active probe snapshot rebuilt retained-history metrics on the live sample path");
        require(session.sampleCount() == 6000,
                "long probe run did not retain exactly the bounded 6000-sample window");
        require(session.observedSampleCount() == TOTAL_SAMPLES,
                "long probe run lost lifetime observed-sample accounting");
        require(session.coverageText().contains("Dropped by probe retention cap: 16000"),
                "long probe run did not account for samples evicted from the retention window");
        require(totalMillis < MAX_TOTAL_MILLIS,
                "22k accept+snapshot Guided probe loop took " + totalMillis
                        + " ms; the live path has regressed toward retained-history work");

        if (earlyElapsed > 0L && lateElapsed > 0L) {
            long allowance = earlyElapsed * 3L + 250000000L;
            require(lateElapsed <= allowance,
                    "post-cap 4000-sample window degraded disproportionately: early="
                            + (earlyElapsed / 1000000L) + " ms, late="
                            + (lateElapsed / 1000000L) + " ms");
        }

        require(session.finish(), "long probe run could not enter Review");
        String reviewed = session.resultText();
        require(reviewed.contains("Retained-window metrics (up to 6000 coherent samples):")
                        && reviewed.contains("Prediction-active samples retained:"),
                "Finish/Review no longer exposes the full retained-window diagnostics");
    }

    private static void foundationThresholdRetainsMultiMinuteAnalysisWindow() {
        GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        session.start(GuidedAeMethodModules.forRecipe(GuidedTuningRecipe.FOUNDATION_THRESHOLD));
        final int total = 25000;
        for (int i = 0; i < total; i++) {
            session.accept(sample(300.0 + i * 0.01, false));
        }
        require(session.sampleCount() == 24000,
                "Foundation 2 did not retain the intended 24000-sample analysis window");
        require(session.observedSampleCount() == total,
                "Foundation 2 long run lost lifetime observed-sample accounting");
        require(session.coverageText().contains("Dropped by probe retention cap: 1000"),
                "Foundation 2 did not account for rows evicted beyond its larger retention window");
        require(session.finish(), "Foundation 2 long run could not enter Review");
        require(session.resultText().contains(
                        "Retained-window metrics (up to 24000 coherent samples):"),
                "Foundation 2 review did not disclose its larger retained analysis window");
    }

    private static LiveSample sample(double seconds, boolean active) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, 2200.0);
        values.put(ChannelRole.TPS, active ? 20.0 : 8.0);
        values.put(ChannelRole.MAP, active ? 65.0 : 50.0);
        values.put(ChannelRole.FALLBACK_MAP, active ? 82.0 : 50.0);
        values.put(ChannelRole.EFFECTIVE_MAP, active ? 78.0 : 50.0);
        values.put(ChannelRole.MAP_PRED_ACTIVE, active ? 1.0 : 0.0);
        values.put(ChannelRole.SMOOTHED_DELTA_TPS, active ? 2.0 : 0.0);
        values.put(ChannelRole.ACCEL_THRESHOLD, 1.5);
        values.put(ChannelRole.MAP_PRED_RESET_CNT, active ? 2.0 : 1.0);
        values.put(ChannelRole.MAP_PRED_EVENT_OVER, active ? 1.0 : 0.0);
        return new LiveSample(Math.round(seconds * 1000000000.0),
                seconds, values, active ? 60.0 : 0.0, active ? 100.0 : 0.0);
    }

    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.000001) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
