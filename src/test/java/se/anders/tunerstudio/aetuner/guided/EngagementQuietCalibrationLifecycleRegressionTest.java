package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.EnumMap;

/** Safety/comparability regression for user-requested TPS quiet recalibration. */
public final class EngagementQuietCalibrationLifecycleRegressionTest {
    private EngagementQuietCalibrationLifecycleRegressionTest() { }

    public static void main(String[] args) {
        recalibrationIsBlockedDuringPhysicalMovement();
        recalibrationRestoresTemporaryCandidateAndRestartsComparison();
        failedRestoreBlocksRecalibrationAndPreservesRecoveryOwner();
        System.out.println("EngagementQuietCalibrationLifecycleRegressionTest passed");
    }

    private static void recalibrationIsBlockedDuringPhysicalMovement() {
        Fixture fixture = new Fixture();
        fixture.start();
        fixture.feed(1800.0, 12.0, 1.40, 50.0, 0.04);
        require(EngagementDeltaWindowSweepRuntime.snapshot().phase
                        == EngagementDeltaWindowSweepRuntime.Phase.MOVING,
                "fixture did not enter MOVING before recalibration block test");

        EngagementQuietCalibrationControl.Result result =
                EngagementQuietCalibrationControl.recalibrate();
        require(!result.success && result.message.contains("MOVING"),
                "recalibration was not explicitly blocked during a physical maneuver");
        require(FoundationTpsNoiseGate.calibrationFrozen(),
                "blocked recalibration changed the frozen quiet baseline");
        require(fixture.writer.restoreCount == 0,
                "blocked recalibration touched the writer during a physical maneuver");
    }

    private static void recalibrationRestoresTemporaryCandidateAndRestartsComparison() {
        Fixture fixture = new Fixture();
        fixture.start();
        fixture.completeBaselineCandidate();
        EngagementDeltaWindowSweepRuntime.Snapshot neighbor =
                EngagementDeltaWindowSweepRuntime.snapshot();
        require(neighbor.candidateIndex == 1 && fixture.writer.temporary,
                "fixture did not reach a temporary neighbor candidate");
        requireClose(20.0, fixture.writer.current,
                "fixture temporary neighbor was not applied");

        EngagementQuietCalibrationControl.Result result =
                EngagementQuietCalibrationControl.recalibrate();
        require(result.success,
                "safe recalibration was blocked outside a physical maneuver: " + result.message);
        require(fixture.writer.restoreCount == 1 && !fixture.writer.temporary,
                "recalibration did not restore the temporary Delta Window exactly once");
        requireClose(25.0, fixture.writer.current,
                "recalibration did not leave the pre-test Delta Window restored");
        require(FoundationTpsNoiseGate.calibrationState()
                        == FoundationTpsNoiseGate.CalibrationState.CALIBRATING,
                "successful recalibration did not start a fresh explicit quiet-baseline window");

        EngagementDeltaWindowSweepRuntime.Snapshot reset =
                EngagementDeltaWindowSweepRuntime.snapshot();
        require(reset.phase == EngagementDeltaWindowSweepRuntime.Phase.IDLE
                        && reset.completedRuns == 0 && reset.eventsThisCandidate == 0,
                "old candidate/RPM comparison evidence survived recalibration");
        EngagementDeltaWindowSweepRuntime.Config preserved =
                EngagementDeltaWindowSweepRuntime.pendingConfig();
        requireClose(1800.0, preserved.rpmStartingPoint,
                "recalibration lost the operator's RPM Starting Point");
        require(preserved.eventsPerCandidate == 2,
                "recalibration lost the normalized maneuvers-per-candidate setting");
    }

    private static void failedRestoreBlocksRecalibrationAndPreservesRecoveryOwner() {
        Fixture fixture = new Fixture();
        fixture.start();
        fixture.completeBaselineCandidate();
        require(fixture.writer.temporary,
                "fixture did not reach a temporary candidate before restore-failure test");
        fixture.writer.failRestore = true;

        EngagementQuietCalibrationControl.Result result =
                EngagementQuietCalibrationControl.recalibrate();
        require(!result.success,
                "recalibration proceeded after the temporary candidate failed to restore");
        require(EngagementDeltaWindowSweepRuntime.snapshot().phase
                        == EngagementDeltaWindowSweepRuntime.Phase.ERROR,
                "failed recalibration restore did not retain the blocking ERROR state");
        require(fixture.writer.temporary,
                "failed restore incorrectly discarded temporary-write ownership");
        require(FoundationTpsNoiseGate.calibrationFrozen(),
                "failed restore started a new calibration and obscured the recovery state");
    }

    private static final class Fixture {
        final AeProjectSnapshot snapshot = snapshot();
        final FakeWriter writer = new FakeWriter(25.0);
        double seconds;
        long index;

        void start() {
            EngagementDeltaWindowSweepRuntime.resetForTest();
            FoundationTpsNoiseGate.reset();
            EngagementDeltaWindowSweepRuntime.setWriterForTest(writer);
            EngagementDeltaWindowSweepRuntime.updatePendingConfig(
                    new EngagementDeltaWindowSweepRuntime.Config(
                            1800.0, 200.0, 10.0, -2.0, 2.0,
                            2, 5.0, false));
            EngagementDeltaWindowSweepRuntime.observeWorkingTune(snapshot);
            FoundationTpsNoiseGate.beginCalibration();
            double dt = 0.025;
            int samples = Math.max(FoundationTpsNoiseGate.minimumQuietSamples() + 8,
                    (int) Math.ceil(FoundationTpsNoiseGate.minimumCalibrationSeconds() / dt) + 10);
            for (int i = 0; i < samples; i++) {
                feed(1800.0, 10.0, 0.20, 0.0, dt);
            }
            require(FoundationTpsNoiseGate.calibrationFrozen(),
                    "fixture did not freeze the explicit quiet baseline");
            reacquireReady();
        }

        void completeBaselineCandidate() {
            for (int event = 0; event < 2; event++) {
                if (EngagementDeltaWindowSweepRuntime.snapshot().phase
                        != EngagementDeltaWindowSweepRuntime.Phase.READY) {
                    reacquireReady();
                }
                performManeuver();
            }
            // Candidate changes are applied on the next live ECU sample. Feed one
            // settled row so lifecycle assertions observe the actual temporary owner.
            feed(1800.0, 10.0, 0.20, 0.0, 0.05);
        }

        void reacquireReady() {
            for (int i = 0; i < 10; i++) {
                feed(1800.0, 10.0, 0.20, 0.0, 0.05);
            }
            require(EngagementDeltaWindowSweepRuntime.snapshot().phase
                            == EngagementDeltaWindowSweepRuntime.Phase.READY,
                    "fixture did not reach READY");
        }

        void performManeuver() {
            feed(1800.0, 12.0, 1.40, 50.0, 0.04);
            feed(1800.0, 15.0, 1.40, 60.0, 0.04);
            feed(1800.0, 18.0, 1.40, 60.0, 0.04);
            feed(1800.0, 20.0, 1.40, 40.0, 0.04);
            feed(1800.0, 18.0, 0.70, -50.0, 0.04);
            for (int i = 0; i < 7; i++) {
                feed(1800.0, 10.0, 0.20, i == 0 ? -80.0 : 0.0, 0.05);
            }
        }

        void feed(double rpm, double tps, double delta,
                  double tpsRate, double dt) {
            seconds += dt;
            index++;
            EngagementDeltaWindowSweepRuntime.accept(snapshot,
                    sample(index, seconds, rpm, tps, delta, 1.0, tpsRate,
                            writer.current));
        }
    }

    private static final class FakeWriter
            implements EngagementDeltaWindowSweepRuntime.TemporaryWriter {
        final double baseline;
        double current;
        boolean temporary;
        boolean failRestore;
        int restoreCount;

        FakeWriter(double baseline) {
            this.baseline = baseline;
            this.current = baseline;
        }

        @Override public EngagementDeltaWindowSweepRuntime.WriteResult apply(double candidateMs) {
            current = candidateMs;
            temporary = Math.abs(candidateMs - baseline) > 0.000001;
            return EngagementDeltaWindowSweepRuntime.WriteResult.ok("candidate applied");
        }

        @Override public EngagementDeltaWindowSweepRuntime.WriteResult restore() {
            if (failRestore) {
                return EngagementDeltaWindowSweepRuntime.WriteResult.fail("synthetic restore failure");
            }
            if (temporary) restoreCount++;
            current = baseline;
            temporary = false;
            return EngagementDeltaWindowSweepRuntime.WriteResult.ok("baseline restored");
        }

        @Override public boolean temporaryActive() { return temporary; }
    }

    private static LiveSample sample(long index, double seconds,
                                     double rpm, double tps,
                                     double delta, double threshold,
                                     double tpsRate, double deltaWindowMs) {
        EnumMap<ChannelRole, Double> values =
                new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.DELTA_TPS, delta);
        values.put(ChannelRole.ACCEL_THRESHOLD, threshold);
        values.put(ChannelRole.AE_DELTA_NEWEST_PAIR, delta);
        values.put(ChannelRole.AE_WINDOW_MS, 50.0);
        values.put(ChannelRole.AE_WINDOW_SAMPLES, 10.0);
        values.put(ChannelRole.AE_DELTA_STRIDE, Math.rint(deltaWindowMs / 5.0));
        values.put(ChannelRole.TPS_DECEL_ACTIVE, 0.0);
        return new LiveSample(index, seconds, values, tpsRate, 0.0);
    }

    private static AeProjectSnapshot snapshot() {
        return new AeProjectSnapshot(
                "quiet-recalibration-lifecycle",
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
