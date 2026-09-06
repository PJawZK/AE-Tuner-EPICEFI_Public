package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.EnumMap;

/** End-to-end regression: only the strict target band advances candidate progress. */
public final class EngagementManeuverQualityIntegrationRegressionTest {
    private EngagementManeuverQualityIntegrationRegressionTest() { }

    public static void main(String[] args) {
        offTargetAttemptsRemainEvidenceWithoutCandidateProgress();
        System.out.println("EngagementManeuverQualityIntegrationRegressionTest passed");
    }

    private static void offTargetAttemptsRemainEvidenceWithoutCandidateProgress() {
        Fixture f = new Fixture();
        f.start();

        EngagementDeltaWindowSweepRuntime.Snapshot start = EngagementDeltaWindowSweepRuntime.snapshot();
        require(start.stage == EngagementDeltaWindowSweepRuntime.Stage.SAMPLE_LENGTH,
                "quality regression did not begin in Sample Length Stage 1");

        f.perform(7.5);
        EngagementDeltaWindowSweepRuntime.Snapshot good = EngagementDeltaWindowSweepRuntime.snapshot();
        require(good.eventsThisCandidate == 0 && good.attemptsThisCandidate == 1,
                "+7.5 GOOD maneuver incorrectly advanced strict candidate progress");
        require(good.lastAttemptQuality == EngagementManeuverQualityPolicy.Quality.GOOD,
                "+7.5 maneuver did not surface GOOD diagnostic quality");
        EngagementFocusModel.resetPresentationCacheForTest();
        EngagementFocusModel goodFocus = EngagementFocusModel.build(
                f.snapshot, f.latest, GuidedCaptureState.CAPTURING,
                good.eventsThisCandidate, good.eventsPerCandidate, 100, 100);
        require(goodFocus.detectorStatusText().contains("LAST GOOD")
                        && goodFocus.nextActionText().contains("Last attempt: GOOD"),
                "Guided Focus did not retain the GOOD diagnostic grade while reacquiring start");

        f.reacquireReady();
        f.perform(25.0);
        EngagementDeltaWindowSweepRuntime.Snapshot diagnostic = EngagementDeltaWindowSweepRuntime.snapshot();
        require(diagnostic.eventsThisCandidate == 0
                        && diagnostic.attemptsThisCandidate == 2
                        && diagnostic.diagnosticAttemptsThisCandidate == 1,
                "+25 diagnostic attempt incorrectly advanced candidate progress");
        require(diagnostic.lastAttemptQuality == EngagementManeuverQualityPolicy.Quality.DIAGNOSTIC_ONLY,
                "+25 maneuver was not retained as diagnostic-only evidence");

        f.reacquireReady();
        f.perform(6.0);
        EngagementDeltaWindowSweepRuntime.Snapshot usable = EngagementDeltaWindowSweepRuntime.snapshot();
        require(usable.candidateIndex == 0
                        && usable.eventsThisCandidate == 0
                        && usable.attemptsThisCandidate == 3,
                "+6 USABLE maneuver incorrectly advanced strict candidate progress");
        require(usable.lastAttemptQuality == EngagementManeuverQualityPolicy.Quality.USABLE,
                "+6 maneuver did not surface USABLE diagnostic quality");

        f.reacquireReady();
        f.perform(2.0);
        EngagementDeltaWindowSweepRuntime.Snapshot rejected = EngagementDeltaWindowSweepRuntime.snapshot();
        require(rejected.candidateIndex == 0
                        && rejected.eventsThisCandidate == 0
                        && rejected.attemptsThisCandidate == 4
                        && rejected.rejectedAttemptsThisCandidate == 1,
                "tiny rejected attempt changed strict candidate progress");
        require(rejected.lastAttemptQuality == EngagementManeuverQualityPolicy.Quality.REJECT,
                "tiny physical attempt was not surfaced as REJECT");

        f.reacquireReady();
        f.perform(9.8);
        require(EngagementDeltaWindowSweepRuntime.snapshot().eventsThisCandidate == 1,
                "first strict target-band maneuver did not advance candidate once");
        f.reacquireReady();
        f.perform(9.8);
        f.feed(10.0, 0.20, 0.0, 0.05);
        EngagementDeltaWindowSweepRuntime.Snapshot secondCandidate = EngagementDeltaWindowSweepRuntime.snapshot();
        require(secondCandidate.stage == EngagementDeltaWindowSweepRuntime.Stage.SAMPLE_LENGTH
                        && secondCandidate.candidateIndex == 1,
                "two strict target-band maneuvers did not advance to second Sample Length candidate");
        require(close(f.writer.sampleLengthSeconds, 0.060),
                "second Sample Length candidate was not applied");

        f.reacquireReady();
        f.perform(14.0);
        require(EngagementDeltaWindowSweepRuntime.snapshot().eventsThisCandidate == 0,
                "+14 GOOD maneuver incorrectly advanced second Sample Length candidate");
        f.reacquireReady();
        f.perform(9.8);
        f.reacquireReady();
        f.perform(9.8);
        f.feed(10.0, 0.20, 0.0, 0.05);
        EngagementDeltaWindowSweepRuntime.Snapshot thirdCandidate = EngagementDeltaWindowSweepRuntime.snapshot();
        require(thirdCandidate.stage == EngagementDeltaWindowSweepRuntime.Stage.SAMPLE_LENGTH
                        && thirdCandidate.candidateIndex == 2,
                "third Sample Length candidate did not start after two strict comparable maneuvers");
        require(close(f.writer.sampleLengthSeconds, 0.070),
                "third Sample Length candidate was not applied");

        f.reacquireReady();
        f.perform(9.8);
        f.reacquireReady();
        f.perform(9.8);
        f.feed(10.0, 0.20, 0.0, 0.05);
        EngagementDeltaWindowSweepRuntime.Snapshot handoff = EngagementDeltaWindowSweepRuntime.snapshot();
        require(handoff.stage == EngagementDeltaWindowSweepRuntime.Stage.DELTA_WINDOW,
                "strict quality-aware Sample Length stage did not hand off automatically to Delta Window");
        require(handoff.phase != EngagementDeltaWindowSweepRuntime.Phase.ERROR,
                "strict quality-aware Sample Length completion entered an error state");
        require(Double.isFinite(handoff.selectedSampleLengthSeconds),
                "Sample Length winner was not retained for Delta Window Stage 2");
    }

    private static final class Fixture {
        final AeProjectSnapshot snapshot = snapshot();
        final FakeWriter writer = new FakeWriter(25.0, 0.050);
        double seconds;
        long index;
        LiveSample latest;

        void start() {
            EngagementDeltaWindowSweepRuntime.resetForTest();
            FoundationTpsNoiseGate.reset();
            EngagementDeltaWindowSweepRuntime.setWriterForTest(writer);
            EngagementDeltaWindowSweepRuntime.updatePendingConfig(
                    new EngagementDeltaWindowSweepRuntime.Config(
                            1800.0, 200.0, 10.0, -2.0, 2.0, 2, 5.0, false));
            EngagementDeltaWindowSweepRuntime.observeWorkingTune(snapshot);
            FoundationTpsNoiseGate.beginCalibration();
            double dt = 0.025;
            int samples = Math.max(FoundationTpsNoiseGate.minimumQuietSamples() + 8,
                    (int) Math.ceil(FoundationTpsNoiseGate.minimumCalibrationSeconds() / dt) + 10);
            for (int i = 0; i < samples; i++) feed(10.0, 0.20, 0.0, dt);
            require(FoundationTpsNoiseGate.calibrationFrozen(), "fixture did not freeze quiet baseline");
            reacquireReady();
        }

        void reacquireReady() {
            for (int i = 0; i < 12; i++) feed(10.0, 0.20, 0.0, 0.05);
            require(EngagementDeltaWindowSweepRuntime.snapshot().phase == EngagementDeltaWindowSweepRuntime.Phase.READY,
                    "fixture did not reacquire READY");
        }

        void perform(double peakDelta) {
            double peak = 10.0 + peakDelta;
            double first = Math.min(peak, 12.0);
            feed(first, 1.40, 50.0, 0.04);
            if (peak > first + 0.05) {
                double mid = first + (peak - first) * 0.50;
                feed(mid, 1.40, 55.0, 0.04);
                feed(peak, 1.40, 45.0, 0.04);
            }
            feed(Math.max(9.5, peak - 1.0), 0.70, -40.0, 0.04);
            for (int i = 0; i < 7; i++) feed(10.0, 0.20, i == 0 ? -60.0 : 0.0, 0.05);
        }

        void feed(double tps, double detectorDelta, double tpsRate, double dt) {
            seconds += dt;
            index++;
            latest = sample(index, seconds, 1800.0, tps, detectorDelta, 1.0,
                    tpsRate, writer.deltaWindowMs, writer.sampleLengthSeconds);
            EngagementDeltaWindowSweepRuntime.accept(snapshot, latest);
        }
    }

    private static final class FakeWriter implements EngagementDeltaWindowSweepRuntime.TemporaryWriter {
        final double baselineDeltaWindowMs;
        final double baselineSampleLengthSeconds;
        double deltaWindowMs;
        double sampleLengthSeconds;

        FakeWriter(double deltaWindowMs, double sampleLengthSeconds) {
            this.baselineDeltaWindowMs = deltaWindowMs;
            this.baselineSampleLengthSeconds = sampleLengthSeconds;
            this.deltaWindowMs = deltaWindowMs;
            this.sampleLengthSeconds = sampleLengthSeconds;
        }

        @Override public EngagementDeltaWindowSweepRuntime.WriteResult apply(double candidateMs) {
            deltaWindowMs = candidateMs;
            return EngagementDeltaWindowSweepRuntime.WriteResult.ok("Delta Window candidate applied");
        }

        @Override public boolean supportsSampleLength() { return true; }

        @Override public EngagementDeltaWindowSweepRuntime.WriteResult applySampleLength(double candidateSeconds) {
            sampleLengthSeconds = candidateSeconds;
            return EngagementDeltaWindowSweepRuntime.WriteResult.ok("Sample Length candidate applied");
        }

        @Override public EngagementDeltaWindowSweepRuntime.WriteResult restore() {
            if (Math.abs(deltaWindowMs - baselineDeltaWindowMs) > 0.000001) {
                deltaWindowMs = baselineDeltaWindowMs;
            } else if (Math.abs(sampleLengthSeconds - baselineSampleLengthSeconds) > 0.000001) {
                sampleLengthSeconds = baselineSampleLengthSeconds;
            }
            return EngagementDeltaWindowSweepRuntime.WriteResult.ok("baseline restored");
        }

        @Override public boolean temporaryActive() {
            return Math.abs(deltaWindowMs - baselineDeltaWindowMs) > 0.000001
                    || Math.abs(sampleLengthSeconds - baselineSampleLengthSeconds) > 0.000001;
        }
    }

    private static LiveSample sample(long index, double seconds, double rpm, double tps,
                                     double detectorDelta, double threshold, double tpsRate,
                                     double deltaWindowMs, double sampleLengthSeconds) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        double windowMs = sampleLengthSeconds * 1000.0;
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.DELTA_TPS, detectorDelta);
        values.put(ChannelRole.ACCEL_THRESHOLD, threshold);
        values.put(ChannelRole.AE_DELTA_NEWEST_PAIR, detectorDelta);
        values.put(ChannelRole.AE_WINDOW_MS, windowMs);
        values.put(ChannelRole.AE_WINDOW_SAMPLES, Math.rint(windowMs / 5.0));
        values.put(ChannelRole.AE_DELTA_STRIDE, Math.rint(deltaWindowMs / 5.0));
        values.put(ChannelRole.AE_ABOVE_THRESHOLD, detectorDelta > threshold ? 1.0 : 0.0);
        values.put(ChannelRole.TPS_DECEL_ACTIVE, 0.0);
        return new LiveSample(index, seconds, values, tpsRate, 0.0);
    }

    private static AeProjectSnapshot snapshot() {
        return new AeProjectSnapshot(
                "maneuver-quality-integration",
                new double[]{2.0}, new double[]{20.0}, new double[][]{{1.0}},
                new double[]{1000.0}, new double[]{1.5},
                1.0, 0.0, new double[0], new double[0],
                false, false, "none", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                "Dual stride, newest", 25.0, 0.050, true, 0.10);
    }

    private static boolean close(double a, double b) { return Math.abs(a - b) <= 0.000001; }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
