package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;

/** Regression for failed temporary-timing restore ownership across a working-tune reread. */
public final class EngagementDeltaWindowRestoreOwnershipRegressionTest {
    private EngagementDeltaWindowRestoreOwnershipRegressionTest() { }

    public static void main(String[] args) {
        failedRestoreKeepsOriginalBaselineAndRecoveryOwnerUntilRetry();
        System.out.println("EngagementDeltaWindowRestoreOwnershipRegressionTest passed");
    }

    private static void failedRestoreKeepsOriginalBaselineAndRecoveryOwnerUntilRetry() {
        EngagementDeltaWindowSweepRuntime.resetForTest();
        FakeWriter writer = new FakeWriter(25.0);
        EngagementDeltaWindowSweepRuntime.setWriterForTest(writer);

        AeProjectSnapshot original = snapshot("restore-owner-original", 25.0);
        AeProjectSnapshot reread = snapshot("restore-owner-reread", 30.0);
        EngagementDeltaWindowSweepRuntime.observeWorkingTune(original);
        requireClose(25.0, EngagementDeltaWindowSweepRuntime.snapshot().baselineMs,
                "original working-tune baseline was not established");

        writer.current = 20.0;
        writer.temporary = true;
        writer.failNextRestore = true;

        EngagementDeltaWindowSweepRuntime.observeWorkingTune(reread);

        EngagementDeltaWindowSweepRuntime.Snapshot failed =
                EngagementDeltaWindowSweepRuntime.snapshot();
        require(failed.phase == EngagementDeltaWindowSweepRuntime.Phase.ERROR,
                "failed automatic restore did not block the runtime in ERROR");
        requireClose(25.0, failed.baselineMs,
                "failed restore replaced the original baseline before recovery");
        require(writer.temporary,
                "failed restore lost the original writer's temporary ownership");
        requireClose(20.0, writer.current,
                "failed restore silently changed the temporary candidate state");
        require(failed.status.contains("restore failed")
                        && failed.status.contains("fresh working-tune baseline"),
                "failed restore did not expose an explicit recovery error tied to the fresh working-tune read");

        EngagementDeltaWindowSweepRuntime.observeWorkingTune(reread);

        EngagementDeltaWindowSweepRuntime.Snapshot recovered =
                EngagementDeltaWindowSweepRuntime.snapshot();
        require(recovered.phase == EngagementDeltaWindowSweepRuntime.Phase.IDLE,
                "successful restore retry did not allow the fresh baseline to be accepted");
        requireClose(30.0, recovered.baselineMs,
                "fresh working-tune baseline was not accepted after verified restore");
        require(!writer.temporary,
                "verified restore retry left the old temporary candidate active");
        requireClose(25.0, writer.current,
                "verified retry did not restore through the original recovery owner");
        require(writer.restoreAttempts == 2,
                "working-tune reread did not retry restoration through the same writer");

        EngagementDeltaWindowSweepRuntime.setWriterForTest(null);
        EngagementDeltaWindowSweepRuntime.resetForTest();
    }

    private static final class FakeWriter
            implements EngagementDeltaWindowSweepRuntime.TemporaryWriter {
        final double baseline;
        double current;
        boolean temporary;
        boolean failNextRestore;
        int restoreAttempts;

        FakeWriter(double baseline) {
            this.baseline = baseline;
            this.current = baseline;
        }

        @Override public EngagementDeltaWindowSweepRuntime.WriteResult apply(double candidateMs) {
            current = candidateMs;
            temporary = true;
            return EngagementDeltaWindowSweepRuntime.WriteResult.ok("synthetic candidate applied");
        }

        @Override public EngagementDeltaWindowSweepRuntime.WriteResult restore() {
            restoreAttempts++;
            if (!temporary) {
                return EngagementDeltaWindowSweepRuntime.WriteResult.ok(
                        "synthetic writer already at baseline");
            }
            if (failNextRestore) {
                failNextRestore = false;
                return EngagementDeltaWindowSweepRuntime.WriteResult.fail(
                        "synthetic restore failure");
            }
            current = baseline;
            temporary = false;
            return EngagementDeltaWindowSweepRuntime.WriteResult.ok(
                    "synthetic baseline restored");
        }

        @Override public boolean temporaryActive() { return temporary; }
    }

    private static AeProjectSnapshot snapshot(String id, double deltaWindowMs) {
        return new AeProjectSnapshot(
                id,
                new double[]{2.0}, new double[]{20.0}, new double[][]{{1.0}},
                new double[]{1000.0}, new double[]{1.5},
                1.0, 0.0, new double[0], new double[0],
                false, false, "none", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                "Dual stride, newest", deltaWindowMs, 0.050, true, 0.10);
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
