package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModule;
import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModules;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;

/** Lifecycle safety regression for immediate verified controlled-sweep restoration. */
public final class EngagementDeltaWindowLifecycleRestoreRegressionTest {
    private EngagementDeltaWindowLifecycleRestoreRegressionTest() { }

    public static void main(String[] args) {
        failedPauseBlocksStateTransitionUntilRestoreSucceeds();
        finishRestoresBeforeCompletingProbeSession();
        resetRestoresBeforeClearingProbeSession();
        closeRestoresBeforeCompletingProbeSession();
        System.out.println("EngagementDeltaWindowLifecycleRestoreRegressionTest passed");
    }

    private static void failedPauseBlocksStateTransitionUntilRestoreSucceeds() {
        Fixture fixture = new Fixture();
        fixture.writer.failNextRestore = true;
        require(!fixture.session.togglePause(),
                "Pause succeeded even though the temporary Delta Window restore failed");
        require(fixture.session.state() == GuidedCaptureState.CAPTURING,
                "failed Pause advanced the probe session out of CAPTURING");
        require(fixture.writer.temporary,
                "failed Pause lost temporary-write ownership");
        require(EngagementDeltaWindowSweepRuntime.snapshot().phase
                        == EngagementDeltaWindowSweepRuntime.Phase.ERROR,
                "failed Pause did not expose the controlled-sweep restore error");

        require(fixture.session.togglePause(),
                "Pause retry did not succeed after the restore path recovered");
        require(fixture.session.state() == GuidedCaptureState.PAUSED,
                "verified Pause retry did not enter PAUSED");
        fixture.requireRestored("Pause retry");
        fixture.cleanup();
    }

    private static void finishRestoresBeforeCompletingProbeSession() {
        Fixture fixture = new Fixture();
        require(fixture.session.finish(), "Finish unexpectedly failed");
        fixture.requireRestored("Finish");
        require(fixture.session.state() == GuidedCaptureState.COMPLETE,
                "Finish did not complete the probe session after verified restore");
        fixture.cleanup();
    }

    private static void resetRestoresBeforeClearingProbeSession() {
        Fixture fixture = new Fixture();
        require(fixture.session.reset(), "Reset unexpectedly failed");
        fixture.requireRestored("Reset");
        require(fixture.session.state() == GuidedCaptureState.IDLE,
                "Reset did not return the probe session to IDLE after verified restore");
        require(fixture.session.module() == null,
                "Reset retained the previous probe module after verified restore");
    }

    private static void closeRestoresBeforeCompletingProbeSession() {
        Fixture fixture = new Fixture();
        require(fixture.session.closeForLifecycle(), "Close lifecycle unexpectedly failed");
        fixture.requireRestored("Close");
        require(fixture.session.state() == GuidedCaptureState.COMPLETE,
                "Close lifecycle did not complete the active probe session after restore");
        fixture.cleanup();
    }

    private static final class Fixture {
        final GuidedMethodProbeSession session = new GuidedMethodProbeSession();
        final FakeWriter writer = new FakeWriter(25.0);

        Fixture() {
            EngagementDeltaWindowSweepRuntime.resetForTest();
            GuidedAeMethodModule module = GuidedAeMethodModules.forRecipe(
                    GuidedTuningRecipe.ENGAGEMENT_DETECTION);
            session.start(module, snapshot(), 5, 20, 115.0);
            require(session.state() == GuidedCaptureState.CAPTURING,
                    "fixture did not start an engagement probe capture");
            EngagementDeltaWindowSweepRuntime.setWriterForTest(writer);
            writer.current = 20.0;
            writer.temporary = true;
        }

        void requireRestored(String action) {
            require(!writer.temporary,
                    action + " left the temporary Delta Window candidate active");
            requireClose(25.0, writer.current,
                    action + " did not restore the pre-test Delta Window baseline");
            require(writer.restoreAttempts >= 1,
                    action + " did not call the restore owner synchronously");
        }

        void cleanup() {
            session.reset();
            EngagementDeltaWindowSweepRuntime.resetForTest();
        }
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
                        "synthetic writer already restored");
            }
            if (failNextRestore) {
                failNextRestore = false;
                return EngagementDeltaWindowSweepRuntime.WriteResult.fail(
                        "synthetic lifecycle restore failure");
            }
            current = baseline;
            temporary = false;
            return EngagementDeltaWindowSweepRuntime.WriteResult.ok(
                    "synthetic lifecycle baseline restored");
        }

        @Override public boolean temporaryActive() { return temporary; }
    }

    private static AeProjectSnapshot snapshot() {
        return new AeProjectSnapshot(
                "engagement-lifecycle-restore",
                new double[]{2.0}, new double[]{20.0}, new double[][]{{1.0}},
                new double[]{1000.0}, new double[]{1.5},
                1.0, 0.0, new double[0], new double[0],
                false, false, "none", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                "Dual stride, newest", 25.0, 0.050, true, 0.10);
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
