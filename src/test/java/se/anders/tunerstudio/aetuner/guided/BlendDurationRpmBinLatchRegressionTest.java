package se.anders.tunerstudio.aetuner.guided;

public final class BlendDurationRpmBinLatchRegressionTest {
    private BlendDurationRpmBinLatchRegressionTest() { }

    public static void main(String[] args) {
        latchesOnlyAfterStableDwellAndNeverMigratesMidEvent();
        releaseAllowsNextArmedBin();
        System.out.println("BlendDurationRpmBinLatchRegressionTest passed");
    }

    private static void latchesOnlyAfterStableDwellAndNeverMigratesMidEvent() {
        BlendDurationRpmBinLatch latch = new BlendDurationRpmBinLatch();
        latch.configure(new double[]{1500.0, 2600.0});
        long t0 = 1_000_000_000L;
        requireClose(2600.0, latch.observe(2570.0, t0), "2600 candidate not acquired");
        require(!latch.candidateStable(t0 + 500_000_000L), "candidate latched before dwell");
        latch.observe(2620.0, t0 + 700_000_000L);
        require(latch.candidateStable(t0 + 700_000_000L), "stable candidate did not qualify");
        requireClose(2600.0, latch.latch(t0 + 700_000_000L), "wrong bin latched");

        // Once latched, a rising-RPM event cannot migrate to another bin.
        requireClose(2600.0, latch.observe(3900.0, t0 + 800_000_000L),
                "latched event followed live RPM mid-event");
        requireClose(2600.0, latch.latchedRpm(), "latched bin changed mid-event");
    }

    private static void releaseAllowsNextArmedBin() {
        BlendDurationRpmBinLatch latch = new BlendDurationRpmBinLatch();
        latch.configure(new double[]{1500.0, 2600.0});
        long t0 = 2_000_000_000L;
        latch.observe(1505.0, t0);
        latch.observe(1498.0, t0 + 700_000_000L);
        requireClose(1500.0, latch.latch(t0 + 700_000_000L), "1500 did not latch");
        latch.release();
        require(!latch.isLatched(), "release did not clear event latch");
        latch.observe(2590.0, t0 + 1_000_000_000L);
        latch.observe(2605.0, t0 + 1_700_000_000L);
        requireClose(2600.0, latch.latch(t0 + 1_700_000_000L),
                "2600 could not latch after previous 1500 event released");
    }

    private static void requireClose(double expected, double actual, String message) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 0.5) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
