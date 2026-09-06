package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.EnumMap;

public final class EngagementDeltaWindowSweepRegressionTest {
    private EngagementDeltaWindowSweepRegressionTest() { }

    public static void main(String[] args) {
        onePhysicalMoveIsOneEventAndTemporaryCandidatesRestore();
        rpmDisagreementWithholdsGlobalRecommendation();
        consistentRpmRegionsExposeReviewedRecommendation();
        System.out.println("EngagementDeltaWindowSweepRegressionTest passed");
    }

    private static void onePhysicalMoveIsOneEventAndTemporaryCandidatesRestore() {
        Fixture f = new Fixture();
        f.start(1800.0);
        EngagementDeltaWindowSweepRuntime.Snapshot stage2 = EngagementDeltaWindowSweepRuntime.snapshot();
        require(stage2.candidateCount == 2,
                "50 ms Sample Length should bound Delta Window candidates to distinct 25/20 ms values");

        f.completeDeltaCandidate(Behavior.FRAGMENTED);
        requireClose(20.0, f.writer.current,
                "lower Delta Window neighbor was not automatically applied");
        require(EngagementDeltaWindowSweepRuntime.snapshot().eventsThisCandidate == 0,
                "candidate progress leaked into the next Delta Window value");

        EngagementDeltaWindowSweepRuntime.forceWatchdogForTest();
        requireClose(25.0, f.writer.current, "watchdog did not restore Delta Window baseline");
        f.settleToReadyOrComplete();
        requireClose(20.0, f.writer.current, "candidate was not reapplied after watchdog recovery");

        f.completeDeltaCandidate(Behavior.POST_PEAK_RETRIGGER);
        EngagementDeltaWindowSweepRuntime.Snapshot done = EngagementDeltaWindowSweepRuntime.snapshot();
        require(done.phase == EngagementDeltaWindowSweepRuntime.Phase.COMPLETE,
                "full two-stage RPM-region sweep did not complete");
        require(done.completedRuns == 1 && !done.recommendationReady,
                "one RPM region incorrectly produced a final recommendation");
        requireClose(25.0, f.writer.current, "Delta Window baseline was not restored");
        requireClose(0.050, f.writer.sampleLengthSeconds, "Sample Length baseline was not restored");
        require(!f.writer.temporaryActive(), "temporary timing write remained active after completion");
        require(EngagementDeltaWindowSweepRuntime.reviewText().contains("distinct RPM Starting Point"),
                "one-region review did not request another RPM region");
    }

    private static void rpmDisagreementWithholdsGlobalRecommendation() {
        Fixture f = new Fixture();
        f.start(1800.0);
        f.completeDeltaCandidate(Behavior.FRAGMENTED);
        f.completeDeltaCandidate(Behavior.CLEAN);

        f.armNextRegion(3000.0);
        f.completeDeltaCandidate(Behavior.CLEAN);
        f.completeDeltaCandidate(Behavior.POST_PEAK_RETRIGGER);

        EngagementDeltaWindowSweepRuntime.Snapshot done = EngagementDeltaWindowSweepRuntime.snapshot();
        require(done.completedRuns == 2 && done.distinctRpmRegions >= 2,
                "two distinct RPM regions were not retained");
        require(!done.recommendationReady,
                "conflicting RPM-region Delta winners produced a recommendation");
        require(EngagementDeltaWindowSweepRuntime.recommendationPlan(f.snapshot) == null,
                "conflicting RPM-region results exposed an Apply plan");
        require(EngagementDeltaWindowSweepRuntime.reviewText().contains("timing-pair results disagree"),
                "RPM disagreement review did not explain timing-pair disagreement");
    }

    private static void consistentRpmRegionsExposeReviewedRecommendation() {
        Fixture f = new Fixture();
        f.start(1800.0);
        f.completeDeltaCandidate(Behavior.FRAGMENTED);
        f.completeDeltaCandidate(Behavior.CLEAN);

        f.armNextRegion(3000.0);
        f.completeDeltaCandidate(Behavior.FRAGMENTED);
        f.completeDeltaCandidate(Behavior.CLEAN);

        EngagementDeltaWindowSweepRuntime.Snapshot done = EngagementDeltaWindowSweepRuntime.snapshot();
        require(done.recommendationReady,
                "consistent distinct RPM regions did not produce a final timing-pair decision");
        requireClose(20.0, done.recommendedMs, "wrong Delta Window winner");
        requireClose(0.050, done.recommendedSampleLengthSeconds,
                "neutral Sample Length stage did not retain 50 ms baseline");
        ProposalWritePlan plan = EngagementDeltaWindowSweepRuntime.recommendationPlan(f.snapshot);
        require(plan != null && plan.changeCount() == 1,
                "expected one reviewed Delta Window change");
        require(plan.reviewText().contains("Delta Window: 25 ms -> 20 ms"),
                "reviewed plan lost exact Delta Window before/after values");
    }

    private enum Behavior { CLEAN, FRAGMENTED, POST_PEAK_RETRIGGER }

    private static final class Fixture {
        final AeProjectSnapshot snapshot = snapshot(25.0);
        final FakeWriter writer = new FakeWriter(25.0, 0.050);
        double seconds;
        long sampleIndex;
        double rpm;

        void start(double rpmStart) {
            EngagementDeltaWindowSweepRuntime.resetForTest();
            FoundationTpsNoiseGate.reset();
            EngagementDeltaWindowSweepRuntime.setWriterForTest(writer);
            EngagementDeltaWindowSweepRuntime.updatePendingConfig(config(rpmStart));
            EngagementDeltaWindowSweepRuntime.observeWorkingTune(snapshot);
            rpm = rpmStart;
            primeQuietCalibration();
            settleToReadyOrComplete();
            require(EngagementDeltaWindowSweepRuntime.snapshot().stage
                            == EngagementDeltaWindowSweepRuntime.Stage.SAMPLE_LENGTH,
                    "sweep did not begin with Sample Length Stage 1");
            completeNeutralSampleLengthStage();
        }

        void armNextRegion(double rpmStart) {
            require(EngagementDeltaWindowSweepRuntime.snapshot().phase
                            == EngagementDeltaWindowSweepRuntime.Phase.COMPLETE,
                    "previous RPM region was not complete");
            require(EngagementDeltaWindowSweepRuntime.updatePendingConfig(config(rpmStart)),
                    "completed sweep did not accept new RPM Starting Point");
            rpm = rpmStart;
            // Rearm is deliberately live-data driven: the first sample after Continue Capture
            // starts a new run and resets the stage to Sample Length.
            feed(rpm, 10.0, 0.20, 0.0, 0.05);
            settleToReadyOrComplete();
            require(EngagementDeltaWindowSweepRuntime.snapshot().stage
                            == EngagementDeltaWindowSweepRuntime.Stage.SAMPLE_LENGTH,
                    "new RPM region did not re-arm Sample Length Stage 1");
            completeNeutralSampleLengthStage();
        }

        void completeNeutralSampleLengthStage() {
            for (int candidate = 0; candidate < 3; candidate++) {
                completeCurrentCandidate(Behavior.CLEAN);
                settleToReadyOrComplete();
            }
            EngagementDeltaWindowSweepRuntime.Snapshot s = EngagementDeltaWindowSweepRuntime.snapshot();
            require(s.stage == EngagementDeltaWindowSweepRuntime.Stage.DELTA_WINDOW
                            && s.phase == EngagementDeltaWindowSweepRuntime.Phase.READY,
                    "Sample Length Stage 1 did not hand off to Delta Window Stage 2");
            requireClose(0.050, s.selectedSampleLengthSeconds,
                    "equal Sample Length candidates should retain baseline 50 ms");
        }

        void completeDeltaCandidate(Behavior behavior) {
            require(EngagementDeltaWindowSweepRuntime.snapshot().stage
                            == EngagementDeltaWindowSweepRuntime.Stage.DELTA_WINDOW,
                    "Delta candidate helper called outside Stage 2");
            completeCurrentCandidate(behavior);
            settleToReadyOrComplete();
        }

        void completeCurrentCandidate(Behavior behavior) {
            int target = EngagementDeltaWindowSweepRuntime.snapshot().eventsPerCandidate;
            for (int event = 0; event < target; event++) {
                if (EngagementDeltaWindowSweepRuntime.snapshot().phase
                        != EngagementDeltaWindowSweepRuntime.Phase.READY) {
                    settleToReadyOrComplete();
                }
                performManeuver(behavior);
            }
        }

        void settleToReadyOrComplete() {
            for (int i = 0; i < 40; i++) {
                EngagementDeltaWindowSweepRuntime.Snapshot s = EngagementDeltaWindowSweepRuntime.snapshot();
                if (s.phase == EngagementDeltaWindowSweepRuntime.Phase.COMPLETE
                        || s.phase == EngagementDeltaWindowSweepRuntime.Phase.ERROR) return;
                if (s.phase == EngagementDeltaWindowSweepRuntime.Phase.READY
                        && s.liveTimingQualified) return;
                feed(rpm, 10.0, 0.20, 0.0, 0.05);
            }
            require(false, "fixture did not settle to qualified READY/COMPLETE");
        }

        void primeQuietCalibration() {
            FoundationTpsNoiseGate.beginCalibration();
            double dt = 0.025;
            int samples = Math.max(FoundationTpsNoiseGate.minimumQuietSamples() + 8,
                    (int) Math.ceil(FoundationTpsNoiseGate.minimumCalibrationSeconds() / dt) + 10);
            for (int i = 0; i < samples; i++) feed(rpm, 10.0, 0.10, 0.0, dt);
            require(FoundationTpsNoiseGate.calibrationFrozen(),
                    "sweep fixture did not freeze TPS quiet baseline");
        }

        void performManeuver(Behavior behavior) {
            double[] tps = new double[]{12.0, 14.0, 16.0, 18.5, 20.0};
            for (int i = 0; i < tps.length; i++) {
                double delta = behavior == Behavior.CLEAN ? 1.40
                        : behavior == Behavior.FRAGMENTED ? ((i % 2 == 0) ? 1.40 : 0.80)
                        : (i < 2 ? 0.80 : 1.40);
                feed(rpm, tps[i], delta, 50.0, 0.04);
            }
            feed(rpm, 18.0, 0.70, -50.0, 0.04);
            for (int i = 0; i < 6; i++) {
                double delta = behavior == Behavior.POST_PEAK_RETRIGGER && i == 1 ? 1.40 : 0.20;
                feed(rpm, 10.0, delta, i == 0 ? -80.0 : 0.0, 0.05);
            }
        }

        void feed(double rpmValue, double tpsValue, double delta, double tpsRate, double dt) {
            seconds += dt;
            sampleIndex++;
            EngagementDeltaWindowSweepRuntime.accept(snapshot,
                    sample(seconds, sampleIndex, rpmValue, tpsValue, delta, 1.0, tpsRate,
                            writer.current, writer.sampleLengthSeconds));
        }
    }

    private static final class FakeWriter implements EngagementDeltaWindowSweepRuntime.TemporaryWriter {
        final double baseline;
        final double baselineSampleLengthSeconds;
        double current;
        double sampleLengthSeconds;
        boolean deltaTemporary;
        boolean sampleTemporary;

        FakeWriter(double baseline, double sampleLengthSeconds) {
            this.baseline = baseline;
            this.current = baseline;
            this.baselineSampleLengthSeconds = sampleLengthSeconds;
            this.sampleLengthSeconds = sampleLengthSeconds;
        }

        @Override public EngagementDeltaWindowSweepRuntime.WriteResult apply(double candidateMs) {
            current = candidateMs;
            deltaTemporary = Math.abs(candidateMs - baseline) > 0.000001;
            return EngagementDeltaWindowSweepRuntime.WriteResult.ok("Delta candidate applied");
        }

        @Override public boolean supportsSampleLength() { return true; }

        @Override public EngagementDeltaWindowSweepRuntime.WriteResult applySampleLength(double candidateSeconds) {
            sampleLengthSeconds = candidateSeconds;
            sampleTemporary = Math.abs(candidateSeconds - baselineSampleLengthSeconds) > 0.000001;
            return EngagementDeltaWindowSweepRuntime.WriteResult.ok("Sample Length candidate applied");
        }

        @Override public EngagementDeltaWindowSweepRuntime.WriteResult restore() {
            if (deltaTemporary) {
                current = baseline;
                deltaTemporary = false;
            } else if (sampleTemporary) {
                sampleLengthSeconds = baselineSampleLengthSeconds;
                sampleTemporary = false;
            }
            return EngagementDeltaWindowSweepRuntime.WriteResult.ok("baseline restored");
        }

        @Override public boolean temporaryActive() { return deltaTemporary || sampleTemporary; }
    }

    private static EngagementDeltaWindowSweepRuntime.Config config(double rpm) {
        return new EngagementDeltaWindowSweepRuntime.Config(rpm, 200.0, 10.0, -2.0, 2.0, 5, 5.0, false);
    }

    private static LiveSample sample(double seconds, long index, double rpm, double tps,
                                     double productionDelta, double threshold, double tpsRate,
                                     double deltaWindowMs, double sampleLengthSeconds) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        double windowMs = sampleLengthSeconds * 1000.0;
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.DELTA_TPS, productionDelta);
        values.put(ChannelRole.ACCEL_THRESHOLD, threshold);
        values.put(ChannelRole.AE_DELTA_NEWEST_PAIR, productionDelta);
        values.put(ChannelRole.AE_WINDOW_MS, windowMs);
        values.put(ChannelRole.AE_WINDOW_SAMPLES, Math.rint(windowMs / 5.0));
        values.put(ChannelRole.AE_DELTA_STRIDE, Math.rint(deltaWindowMs / 5.0));
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, productionDelta > threshold ? 1.0 : 0.0);
        values.put(ChannelRole.TPS_DECEL_ACTIVE, 0.0);
        return new LiveSample(index, seconds, values, tpsRate, 0.0);
    }

    private static AeProjectSnapshot snapshot(double deltaWindowMs) {
        return new AeProjectSnapshot(
                "engagement-sweep",
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
