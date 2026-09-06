package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;

/** Permanent behavioral gate for Sample Length -> Delta Window road-test ordering. */
public final class EngagementTwoStageTimingSweepRegressionTest {
    private EngagementTwoStageTimingSweepRegressionTest() { }

    public static void main(String[] args) {
        unchangedLiveTimingCannotFakeCandidateProgress();
        fullRegionSequencesSampleLengthBeforeDeltaWindow();
        System.out.println("EngagementTwoStageTimingSweepRegressionTest passed");
    }

    private static void unchangedLiveTimingCannotFakeCandidateProgress() {
        Fixture fixture = new Fixture();
        fixture.start(1800.0);
        fixture.completeCurrentCandidate(Behavior.FRAGMENTED);

        EngagementDeltaWindowSweepRuntime.Snapshot candidate =
                EngagementDeltaWindowSweepRuntime.snapshot();
        require(candidate.stage == EngagementDeltaWindowSweepRuntime.Stage.SAMPLE_LENGTH
                        && candidate.candidateIndex == 1,
                "Sample Length baseline did not advance to the first temporary candidate");
        requireClose(0.060, fixture.writer.sampleLengthSeconds,
                "60 ms Sample Length candidate was not applied");

        // Recreate the recovered-MLG failure mode: setting write/readback succeeds,
        // but the ECU continues reporting the old 50 ms / 10 sample signature.
        for (int i = 0; i < 20; i++) {
            fixture.feedWithTiming(1800.0, 10.0, 0.20, 0.0, 0.05,
                    50.0, 10.0, 5.0, 1.0);
        }
        EngagementDeltaWindowSweepRuntime.Snapshot blocked =
                EngagementDeltaWindowSweepRuntime.snapshot();
        require(!blocked.liveTimingQualified && blocked.eventsThisCandidate == 0,
                "candidate advanced despite unchanged live ECU timing signature");
        require(blocked.status.contains("VERIFYING LIVE Sample Length"),
                "live timing mismatch was not exposed to the operator");

        fixture.stabilize();
        require(EngagementDeltaWindowSweepRuntime.snapshot().liveTimingQualified,
                "correct 60 ms live timing signature did not qualify the candidate");
    }

    private static void fullRegionSequencesSampleLengthBeforeDeltaWindow() {
        Fixture fixture = new Fixture();
        fixture.start(1800.0);

        fixture.completeCurrentCandidate(Behavior.FRAGMENTED); // 50 ms
        fixture.completeCurrentCandidate(Behavior.CLEAN);      // 60 ms wins
        fixture.completeCurrentCandidate(Behavior.POST_PEAK_RETRIGGER); // 70 ms

        EngagementDeltaWindowSweepRuntime.Snapshot deltaStage =
                EngagementDeltaWindowSweepRuntime.snapshot();
        require(deltaStage.stage == EngagementDeltaWindowSweepRuntime.Stage.DELTA_WINDOW,
                "runtime did not transition automatically from Sample Length to Delta Window");
        requireClose(0.060, deltaStage.selectedSampleLengthSeconds,
                "Sample Length winner was not carried into Delta Window stage");
        requireClose(0.060, fixture.writer.sampleLengthSeconds,
                "selected Sample Length was not held temporarily for Delta Window testing");
        require(deltaStage.candidateCount > 0 && deltaStage.candidateIndex == 0,
                "automatic handoff did not initialize Delta Window Stage 2 at its first candidate");

        fixture.completeCurrentCandidate(Behavior.FRAGMENTED); // 25 ms
        fixture.completeCurrentCandidate(Behavior.POST_PEAK_RETRIGGER); // 20 ms
        fixture.completeCurrentCandidate(Behavior.CLEAN); // 30 ms wins

        EngagementDeltaWindowSweepRuntime.Snapshot firstRegion =
                EngagementDeltaWindowSweepRuntime.snapshot();
        require(firstRegion.phase == EngagementDeltaWindowSweepRuntime.Phase.COMPLETE
                        && firstRegion.stage == EngagementDeltaWindowSweepRuntime.Stage.COMPLETE,
                "full two-stage RPM-region sequence did not complete");
        require(firstRegion.completedRuns == 1 && !firstRegion.recommendationReady,
                "one RPM region incorrectly produced a final timing recommendation");
        require(firstRegion.status.contains("Enter a new RPM Starting Point"),
                "region completion did not request the next RPM starting point");
        requireClose(0.050, fixture.writer.sampleLengthSeconds,
                "region completion did not restore original Sample Length");
        requireClose(25.0, fixture.writer.deltaWindowMs,
                "region completion did not restore original Delta Window");
        require(!fixture.writer.temporaryActive(),
                "region completion left temporary timing writes active");

        fixture.armNextRegion(3000.0);
        require(EngagementDeltaWindowSweepRuntime.snapshot().stage
                        == EngagementDeltaWindowSweepRuntime.Stage.SAMPLE_LENGTH,
                "new RPM region did not restart from Sample Length stage");

        fixture.completeCurrentCandidate(Behavior.FRAGMENTED);
        fixture.completeCurrentCandidate(Behavior.CLEAN);
        fixture.completeCurrentCandidate(Behavior.POST_PEAK_RETRIGGER);
        fixture.completeCurrentCandidate(Behavior.FRAGMENTED);
        fixture.completeCurrentCandidate(Behavior.POST_PEAK_RETRIGGER);
        fixture.completeCurrentCandidate(Behavior.CLEAN);

        EngagementDeltaWindowSweepRuntime.Snapshot secondRegion =
                EngagementDeltaWindowSweepRuntime.snapshot();
        require(secondRegion.recommendationReady && secondRegion.distinctRpmRegions >= 2,
                "matching two-stage results at two RPM regions did not become recommendation-ready");
        requireClose(0.060, secondRegion.recommendedSampleLengthSeconds,
                "combined recommendation selected the wrong Sample Length");
        requireClose(30.0, secondRegion.recommendedMs,
                "combined recommendation selected the wrong Delta Window");
        ProposalWritePlan plan = EngagementDeltaWindowSweepRuntime.recommendationPlan(fixture.snapshot);
        require(plan != null && plan.changeCount() == 2,
                "agreed timing pair did not create one coherent two-setting proposal");
        require(plan.reviewText().contains("Sample Length")
                        && plan.reviewText().contains("Delta Window"),
                "combined timing proposal did not expose both reviewed changes");
    }

    private enum Behavior { CLEAN, FRAGMENTED, POST_PEAK_RETRIGGER }

    private static final class Fixture {
        final AeProjectSnapshot snapshot = snapshot();
        final FakeWriter writer = new FakeWriter(0.050, 25.0);
        double seconds;
        long index;
        double rpm;

        void start(double rpmStartingPoint) {
            EngagementDeltaWindowSweepRuntime.resetForTest();
            FoundationTpsNoiseGate.reset();
            EngagementDeltaWindowSweepRuntime.setWriterForTest(writer);
            EngagementDeltaWindowSweepRuntime.updatePendingConfig(config(rpmStartingPoint));
            EngagementDeltaWindowSweepRuntime.observeWorkingTune(snapshot);
            rpm = rpmStartingPoint;
            primeQuietCalibration();
            stabilize();
            EngagementDeltaWindowSweepRuntime.Snapshot ready =
                    EngagementDeltaWindowSweepRuntime.snapshot();
            require(ready.stage == EngagementDeltaWindowSweepRuntime.Stage.SAMPLE_LENGTH
                            && ready.phase == EngagementDeltaWindowSweepRuntime.Phase.READY,
                    "two-stage sweep did not start at Sample Length / READY");
        }

        void armNextRegion(double rpmStartingPoint) {
            require(EngagementDeltaWindowSweepRuntime.updatePendingConfig(config(rpmStartingPoint)),
                    "completed RPM region did not accept a new starting point");
            rpm = rpmStartingPoint;
            stabilize();
            require(EngagementDeltaWindowSweepRuntime.snapshot().phase
                            == EngagementDeltaWindowSweepRuntime.Phase.READY,
                    "new RPM region did not automatically re-arm to READY");
        }

        void primeQuietCalibration() {
            FoundationTpsNoiseGate.beginCalibration();
            double dt = 0.025;
            int samples = Math.max(FoundationTpsNoiseGate.minimumQuietSamples() + 10,
                    (int) Math.ceil(FoundationTpsNoiseGate.minimumCalibrationSeconds() / dt) + 12);
            for (int i = 0; i < samples; i++) feed(rpm, 10.0, 0.10, 0.0, dt);
            require(FoundationTpsNoiseGate.calibrationFrozen(),
                    "two-stage fixture did not freeze TPS quiet calibration");
        }

        void stabilize() {
            for (int i = 0; i < 12; i++) feed(rpm, 10.0, 0.20, 0.0, 0.05);
        }

        void completeCurrentCandidate(Behavior behavior) {
            int target = EngagementDeltaWindowSweepRuntime.snapshot().eventsPerCandidate;
            for (int event = 0; event < target; event++) {
                if (EngagementDeltaWindowSweepRuntime.snapshot().phase
                        != EngagementDeltaWindowSweepRuntime.Phase.READY) stabilize();
                performManeuver(behavior);
            }
        }

        void performManeuver(Behavior behavior) {
            double[] tps = new double[]{12.0, 14.0, 16.0, 18.5, 20.0};
            for (int i = 0; i < tps.length; i++) {
                double delta;
                if (behavior == Behavior.CLEAN) delta = 1.40;
                else if (behavior == Behavior.FRAGMENTED) delta = (i % 2 == 0) ? 1.40 : 0.80;
                else delta = i < 2 ? 0.80 : 1.40;
                feed(rpm, tps[i], delta, 50.0, 0.04);
            }
            feed(rpm, 18.0, 0.70, -50.0, 0.04);
            for (int i = 0; i < 7; i++) {
                double delta = behavior == Behavior.POST_PEAK_RETRIGGER && i == 1
                        ? 1.40 : 0.20;
                feed(rpm, 10.0, delta, i == 0 ? -80.0 : 0.0, 0.05);
            }
        }

        void feed(double rpmValue, double tpsValue, double delta,
                  double tpsRate, double dt) {
            double windowMs = writer.sampleLengthSeconds * 1000.0;
            double samples = Math.max(2.0, Math.rint(windowMs / 5.0));
            double period = windowMs / samples;
            double stride = Math.max(1.0, Math.rint(writer.deltaWindowMs / period));
            feedWithTiming(rpmValue, tpsValue, delta, tpsRate, dt,
                    windowMs, samples, stride, delta > 1.0 ? 0.0 : 1.0);
        }

        void feedWithTiming(double rpmValue, double tpsValue, double delta,
                            double tpsRate, double dt,
                            double windowMs, double windowSamples,
                            double stride, double cycleCount) {
            seconds += dt;
            index++;
            EnumMap<ChannelRole, Double> values =
                    new EnumMap<ChannelRole, Double>(ChannelRole.class);
            values.put(ChannelRole.RPM, rpmValue);
            values.put(ChannelRole.TPS, tpsValue);
            values.put(ChannelRole.DELTA_TPS, delta);
            values.put(ChannelRole.ACCEL_THRESHOLD, 1.0);
            values.put(ChannelRole.AE_DELTA_NEWEST_PAIR, delta);
            values.put(ChannelRole.AE_WINDOW_MS, windowMs);
            values.put(ChannelRole.AE_WINDOW_SAMPLES, windowSamples);
            values.put(ChannelRole.AE_DELTA_STRIDE, stride);
            values.put(ChannelRole.TPS_AE_CYCLE_CNT, cycleCount);
            values.put(ChannelRole.AE_ABOVE_THRESHOLD, delta > 1.0 ? 1.0 : 0.0);
            values.put(ChannelRole.TPS_DECEL_ACTIVE, 0.0);
            EngagementDeltaWindowSweepRuntime.accept(snapshot,
                    new LiveSample(index, seconds, values, tpsRate, 0.0));
        }
    }

    private static final class FakeWriter
            implements EngagementDeltaWindowSweepRuntime.TemporaryWriter {
        private static final class RestorePoint {
            final boolean sample;
            final double value;
            RestorePoint(boolean sample, double value) {
                this.sample = sample;
                this.value = value;
            }
        }

        final double baselineSampleLengthSeconds;
        final double baselineDeltaWindowMs;
        double sampleLengthSeconds;
        double deltaWindowMs;
        final Deque<RestorePoint> stack = new ArrayDeque<RestorePoint>();

        FakeWriter(double sampleLengthSeconds, double deltaWindowMs) {
            baselineSampleLengthSeconds = sampleLengthSeconds;
            baselineDeltaWindowMs = deltaWindowMs;
            this.sampleLengthSeconds = sampleLengthSeconds;
            this.deltaWindowMs = deltaWindowMs;
        }

        @Override public boolean supportsSampleLength() { return true; }

        @Override public EngagementDeltaWindowSweepRuntime.WriteResult applySampleLength(double seconds) {
            stack.push(new RestorePoint(true, sampleLengthSeconds));
            sampleLengthSeconds = seconds;
            return EngagementDeltaWindowSweepRuntime.WriteResult.ok("sample length applied");
        }

        @Override public EngagementDeltaWindowSweepRuntime.WriteResult apply(double ms) {
            stack.push(new RestorePoint(false, deltaWindowMs));
            deltaWindowMs = ms;
            return EngagementDeltaWindowSweepRuntime.WriteResult.ok("delta window applied");
        }

        @Override public EngagementDeltaWindowSweepRuntime.WriteResult restore() {
            if (stack.isEmpty()) return EngagementDeltaWindowSweepRuntime.WriteResult.ok("already baseline");
            RestorePoint point = stack.pop();
            if (point.sample) sampleLengthSeconds = point.value;
            else deltaWindowMs = point.value;
            return EngagementDeltaWindowSweepRuntime.WriteResult.ok("restored");
        }

        @Override public boolean temporaryActive() { return !stack.isEmpty(); }
    }

    private static EngagementDeltaWindowSweepRuntime.Config config(double rpm) {
        return new EngagementDeltaWindowSweepRuntime.Config(
                rpm, 200.0, 10.0, -2.0, 2.0, 5, 5.0, false);
    }

    private static AeProjectSnapshot snapshot() {
        return new AeProjectSnapshot(
                "two-stage-timing",
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
