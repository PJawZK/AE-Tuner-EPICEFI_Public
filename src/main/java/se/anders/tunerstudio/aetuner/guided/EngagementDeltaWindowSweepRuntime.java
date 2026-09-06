package se.anders.tunerstudio.aetuner.guided;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import se.anders.tunerstudio.aetuner.host.AeControllerBridge;
import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.host.ProposalApplyCoordinator;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.EngagementDetectionSettingProposal;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Controlled TPS Movement / Timing road test.
 *
 * Production order is intentionally Sample Length first, followed automatically
 * by Delta Window at the Sample Length selected for that RPM region.  No road
 * maneuver may count until the live ECU timing channels prove that the requested
 * candidate is actually effective.
 */
public final class EngagementDeltaWindowSweepRuntime {
    private static final double EPSILON = 0.000001;
    private static final double STABLE_SECONDS = 0.30;
    private static final double DECEL_REARM_SECONDS = 0.50;
    private static final double POST_PEAK_SECONDS = 0.30;
    private static final double MANEUVER_TIMEOUT_SECONDS = 3.0;
    private static final double PEAK_REVERSAL_DROP_TPS = 0.35;
    private static final double PEAK_RATE_EPSILON = 1.5;
    private static final long WATCHDOG_NS = 1500000000L;
    private static final int MIN_RPM_REGIONS_FOR_RECOMMENDATION = 2;
    private static final int TIMING_QUALIFY_SAMPLES = 3;
    private static final double SAMPLE_LENGTH_STEP_SECONDS = 0.010;

    public enum Phase { IDLE, SEEK_START, READY, MOVING, POST_STOP, COMPLETE, ERROR }
    public enum Stage { SAMPLE_LENGTH, DELTA_WINDOW, COMPLETE }

    public static final class Config {
        public final double rpmStartingPoint;
        public final double rpmStartRange;
        public final double tpsStep;
        public final double targetMinOffset;
        public final double targetMaxOffset;
        public final int eventsPerCandidate;
        public final double candidateStepMs;
        public final boolean transitionBeep;

        public Config(double rpmStartingPoint, double rpmStartRange,
                      double tpsStep, double targetMinOffset,
                      double targetMaxOffset, int eventsPerCandidate,
                      double candidateStepMs, boolean transitionBeep) {
            this.rpmStartingPoint = clamp(rpmStartingPoint, 600.0, 6500.0);
            this.rpmStartRange = clamp(rpmStartRange, 50.0, 1200.0);
            this.tpsStep = clamp(tpsStep, 1.0, 40.0);
            this.targetMinOffset = clamp(targetMinOffset, -12.0, 0.0);
            this.targetMaxOffset = clamp(targetMaxOffset, 0.0, 12.0);
            this.eventsPerCandidate = clamp(eventsPerCandidate, 2, 10);
            this.candidateStepMs = clamp(candidateStepMs, 1.0, 20.0);
            this.transitionBeep = transitionBeep;
        }

        public static Config defaults() {
            return new Config(1800.0, 200.0, 10.0,
                    -2.0, 2.0, 5, 5.0, true);
        }

        String summary() {
            return "RPM start " + f0(rpmStartingPoint) + " +/- " + f0(rpmStartRange)
                    + " | TPS step +" + f1(tpsStep)
                    + " | target " + signed(targetMinOffset) + " / " + signed(targetMaxOffset)
                    + " | " + eventsPerCandidate + " maneuvers/value"
                    + " | Delta step " + f1(candidateStepMs) + " ms";
        }
    }

    public static final class Snapshot {
        public final boolean available;
        public final boolean active;
        public final Phase phase;
        public final Stage stage;
        public final Config config;
        public final double baselineMs;
        public final double candidateMs;
        public final double baselineSampleLengthSeconds;
        public final double candidateSampleLengthSeconds;
        public final double selectedSampleLengthSeconds;
        public final int candidateIndex;
        public final int candidateCount;
        public final int eventsThisCandidate;
        public final int eventsPerCandidate;
        public final int attemptsThisCandidate;
        public final int diagnosticAttemptsThisCandidate;
        public final int rejectedAttemptsThisCandidate;
        public final double liveRpm;
        public final double liveTps;
        public final double startTps;
        public final double targetTps;
        public final double targetMin;
        public final double targetMax;
        public final double peakTps;
        public final boolean targetReached;
        public final boolean liveTimingQualified;
        public final double liveWindowMs;
        public final double liveWindowSamples;
        public final double liveStride;
        public final EngagementManeuverQualityPolicy.Quality lastAttemptQuality;
        public final double lastAttemptPeakDeltaTps;
        public final String lastAttemptReason;
        public final int completedRuns;
        public final int distinctRpmRegions;
        public final double recommendedMs;
        public final double recommendedSampleLengthSeconds;
        public final boolean recommendationReady;
        public final long candidateRevision;
        public final String status;

        private Snapshot(boolean available, boolean active, Phase phase, Stage stage,
                         Config config, double baselineMs, double candidateMs,
                         double baselineSampleLengthSeconds,
                         double candidateSampleLengthSeconds,
                         double selectedSampleLengthSeconds,
                         int candidateIndex, int candidateCount,
                         int eventsThisCandidate, int eventsPerCandidate,
                         int attemptsThisCandidate,
                         int diagnosticAttemptsThisCandidate,
                         int rejectedAttemptsThisCandidate,
                         double liveRpm, double liveTps, double startTps,
                         double targetTps, double targetMin, double targetMax,
                         double peakTps, boolean targetReached,
                         boolean liveTimingQualified,
                         double liveWindowMs, double liveWindowSamples, double liveStride,
                         EngagementManeuverQualityPolicy.Quality lastAttemptQuality,
                         double lastAttemptPeakDeltaTps, String lastAttemptReason,
                         int completedRuns, int distinctRpmRegions,
                         double recommendedMs, double recommendedSampleLengthSeconds,
                         boolean recommendationReady,
                         long candidateRevision, String status) {
            this.available = available;
            this.active = active;
            this.phase = phase == null ? Phase.IDLE : phase;
            this.stage = stage == null ? Stage.SAMPLE_LENGTH : stage;
            this.config = config == null ? Config.defaults() : config;
            this.baselineMs = baselineMs;
            this.candidateMs = candidateMs;
            this.baselineSampleLengthSeconds = baselineSampleLengthSeconds;
            this.candidateSampleLengthSeconds = candidateSampleLengthSeconds;
            this.selectedSampleLengthSeconds = selectedSampleLengthSeconds;
            this.candidateIndex = candidateIndex;
            this.candidateCount = candidateCount;
            this.eventsThisCandidate = eventsThisCandidate;
            this.eventsPerCandidate = eventsPerCandidate;
            this.attemptsThisCandidate = attemptsThisCandidate;
            this.diagnosticAttemptsThisCandidate = diagnosticAttemptsThisCandidate;
            this.rejectedAttemptsThisCandidate = rejectedAttemptsThisCandidate;
            this.liveRpm = liveRpm;
            this.liveTps = liveTps;
            this.startTps = startTps;
            this.targetTps = targetTps;
            this.targetMin = targetMin;
            this.targetMax = targetMax;
            this.peakTps = peakTps;
            this.targetReached = targetReached;
            this.liveTimingQualified = liveTimingQualified;
            this.liveWindowMs = liveWindowMs;
            this.liveWindowSamples = liveWindowSamples;
            this.liveStride = liveStride;
            this.lastAttemptQuality = lastAttemptQuality;
            this.lastAttemptPeakDeltaTps = lastAttemptPeakDeltaTps;
            this.lastAttemptReason = lastAttemptReason == null ? "" : lastAttemptReason;
            this.completedRuns = completedRuns;
            this.distinctRpmRegions = distinctRpmRegions;
            this.recommendedMs = recommendedMs;
            this.recommendedSampleLengthSeconds = recommendedSampleLengthSeconds;
            this.recommendationReady = recommendationReady;
            this.candidateRevision = candidateRevision;
            this.status = status == null ? "" : status;
        }
    }

    interface TemporaryWriter {
        WriteResult apply(double candidateMs);
        WriteResult restore();
        boolean temporaryActive();
        default boolean supportsSampleLength() { return false; }
        default WriteResult applySampleLength(double candidateSeconds) {
            return WriteResult.fail("Sample Length temporary write is unavailable");
        }
    }

    static final class WriteResult {
        final boolean success;
        final String message;
        WriteResult(boolean success, String message) {
            this.success = success;
            this.message = message == null ? "" : message;
        }
        static WriteResult ok(String message) { return new WriteResult(true, message); }
        static WriteResult fail(String message) { return new WriteResult(false, message); }
    }

    private static final class ProductionWriter implements TemporaryWriter {
        private final AeProjectSnapshot baseline;
        private final ProposalApplyCoordinator coordinator;

        ProductionWriter(AeProjectSnapshot baseline, ControllerAccess access) {
            this.baseline = baseline;
            this.coordinator = new ProposalApplyCoordinator(access);
        }

        @Override public WriteResult apply(double candidateMs) {
            try {
                ProposalWritePlan plan = EngagementDetectionSettingProposal.deltaWindow(
                        baseline, candidateMs);
                if (plan == null) return WriteResult.ok("Baseline Delta Window already active");
                ProposalApplyCoordinator.ApplyResult result = coordinator.apply(plan);
                return new WriteResult(result.success, result.message);
            } catch (RuntimeException ex) {
                return WriteResult.fail(ex.getMessage());
            }
        }

        @Override public boolean supportsSampleLength() { return true; }

        @Override public WriteResult applySampleLength(double candidateSeconds) {
            try {
                ProposalWritePlan plan = EngagementDetectionSettingProposal.sampleLength(
                        baseline, candidateSeconds);
                if (plan == null) return WriteResult.ok("Baseline Sample Length already active");
                ProposalApplyCoordinator.ApplyResult result = coordinator.apply(plan);
                return new WriteResult(result.success, result.message);
            } catch (RuntimeException ex) {
                return WriteResult.fail(ex.getMessage());
            }
        }

        @Override public WriteResult restore() {
            if (!coordinator.canRestorePreviousApply()) {
                return WriteResult.ok("No temporary timing candidate required restoration");
            }
            ProposalApplyCoordinator.ApplyResult result = coordinator.restorePreviousApply();
            return new WriteResult(result.success, result.message);
        }

        @Override public boolean temporaryActive() {
            return coordinator.canRestorePreviousApply();
        }
    }

    private static final class CandidateMetrics {
        final double value;
        int attempts;
        int events;
        int excellent;
        int good;
        int usable;
        int diagnosticOnly;
        int rejected;
        int missedDetector;
        int extraBursts;
        int postPeakRetriggers;
        int detectedEvents;
        double onsetDelaySum;
        double peakErrorSum;
        double startRpmSum;
        double detectorClearDelaySum;
        int detectorClearCount;
        double cycleResumeDelaySum;
        int cycleResumeCount;

        CandidateMetrics(double value) { this.value = value; }

        boolean addAttempt(EventMetrics event,
                           EngagementManeuverQualityPolicy.Assessment assessment,
                           Stage stage) {
            attempts++;
            EngagementManeuverQualityPolicy.Quality quality = assessment == null
                    ? EngagementManeuverQualityPolicy.Quality.REJECT : assessment.quality;
            switch (quality) {
                case EXCELLENT: excellent++; break;
                case GOOD: good++; break;
                case USABLE: usable++; break;
                case DIAGNOSTIC_ONLY: diagnosticOnly++; break;
                case REJECT:
                default: rejected++; break;
            }
            if (assessment == null || !assessment.comparable()) return false;
            events++;
            if (event.detectorBursts <= 0) missedDetector++;
            else {
                detectedEvents++;
                onsetDelaySum += Math.max(0.0,
                        event.firstDetectorSeconds - event.movementStartSeconds);
            }
            extraBursts += Math.max(0, event.detectorBursts - 1);
            postPeakRetriggers += Math.max(0, event.postPeakRetriggers);
            peakErrorSum += Math.abs(event.peakTps - event.targetTps);
            startRpmSum += event.startRpm;
            if (Double.isFinite(event.detectorClearDelay)) {
                detectorClearDelaySum += event.detectorClearDelay;
                detectorClearCount++;
            }
            if (Double.isFinite(event.cycleResumeDelay)) {
                cycleResumeDelaySum += event.cycleResumeDelay;
                cycleResumeCount++;
            }
            return true;
        }

        double averageOnset() { return detectedEvents <= 0 ? 1.0 : onsetDelaySum / detectedEvents; }
        double averagePeakError() { return events <= 0 ? 99.0 : peakErrorSum / events; }
        double averageClearDelay() { return detectorClearCount <= 0 ? 0.50 : detectorClearDelaySum / detectorClearCount; }
        double averageCycleResumeDelay() { return cycleResumeCount <= 0 ? 0.50 : cycleResumeDelaySum / cycleResumeCount; }

        double score(Stage stage) {
            if (events <= 0) return Double.POSITIVE_INFINITY;
            double score = missedDetector * 8.0
                    + extraBursts * 2.0
                    + postPeakRetriggers * 4.0
                    + averageOnset() * 1.5
                    + averagePeakError() * 0.20;
            if (stage == Stage.SAMPLE_LENGTH) {
                score += averageClearDelay() * 3.0 + averageCycleResumeDelay() * 4.0;
            }
            return score;
        }

        String summary(Stage stage) {
            String unit = stage == Stage.SAMPLE_LENGTH ? " ms Sample Length" : " ms Delta Window";
            StringBuilder out = new StringBuilder();
            out.append(f1(value)).append(unit)
                    .append(": score ").append(f2(score(stage)))
                    .append(" | comparable ").append(events).append("/attempts ").append(attempts)
                    .append(" | quality E/G/U/D/R ").append(excellent).append('/').append(good)
                    .append('/').append(usable).append('/').append(diagnosticOnly).append('/').append(rejected)
                    .append(" | missed ").append(missedDetector)
                    .append(" | extra bursts ").append(extraBursts)
                    .append(" | retriggers ").append(postPeakRetriggers)
                    .append(" | onset ").append(f3(averageOnset())).append(" s");
            if (stage == Stage.SAMPLE_LENGTH) {
                out.append(" | detector clear ").append(f3(averageClearDelay())).append(" s")
                        .append(" | cycle resume ").append(f3(averageCycleResumeDelay())).append(" s");
            }
            return out.toString();
        }
    }

    private static final class EventMetrics {
        double startRpm;
        double movementStartSeconds;
        double firstDetectorSeconds = Double.NaN;
        int detectorBursts;
        int postPeakRetriggers;
        double peakTps;
        double targetTps;
        double detectorClearDelay = Double.NaN;
        double cycleResumeDelay = Double.NaN;
    }

    private static final class StageResult {
        final Stage stage;
        final List<CandidateMetrics> candidates;
        final double winner;
        final String reason;
        StageResult(Stage stage, List<CandidateMetrics> candidates, double winner, String reason) {
            this.stage = stage;
            this.candidates = candidates;
            this.winner = winner;
            this.reason = reason;
        }
    }

    private static final class RunResult {
        final Config config;
        final StageResult sampleLength;
        final StageResult deltaWindow;
        RunResult(Config config, StageResult sampleLength, StageResult deltaWindow) {
            this.config = config;
            this.sampleLength = sampleLength;
            this.deltaWindow = deltaWindow;
        }
    }

    private static final class Winner {
        final double value;
        final String reason;
        Winner(double value, String reason) { this.value = value; this.reason = reason; }
    }

    private static final class Recommendation {
        final boolean ready;
        final double sampleLengthSeconds;
        final double deltaWindowMs;
        final String reason;
        Recommendation(boolean ready, double sampleLengthSeconds,
                       double deltaWindowMs, String reason) {
            this.ready = ready;
            this.sampleLengthSeconds = sampleLengthSeconds;
            this.deltaWindowMs = deltaWindowMs;
            this.reason = reason;
        }
    }

    private static final Object LOCK = new Object();
    private static Config pendingConfig = Config.defaults();
    private static Config activeConfig = Config.defaults();
    private static AeProjectSnapshot workingSnapshot;
    private static double baselineMs = Double.NaN;
    private static double baselineSampleLengthSeconds = Double.NaN;
    private static double selectedSampleLengthSeconds = Double.NaN;
    private static TemporaryWriter writer;
    private static TemporaryWriter testWriter;
    private static boolean legacyTestMode;
    private static boolean runActive;
    private static boolean rearmRequested;
    private static Phase phase = Phase.IDLE;
    private static Stage stage = Stage.SAMPLE_LENGTH;
    private static double[] candidateValues = new double[0];
    private static CandidateMetrics[] currentCandidates = new CandidateMetrics[0];
    private static int candidateIndex;
    private static long candidateRevision;
    private static boolean currentCandidateApplied;
    private static boolean sampleWinnerHeld;
    private static int timingQualifiedSamples;
    private static boolean liveTimingQualified;
    private static StageResult currentSampleResult;
    private static final List<RunResult> completedRuns = new ArrayList<RunResult>();
    private static String status = "Controlled TPS Movement / Timing sweep idle";
    private static long lastSampleWallNano;
    private static double lastLiveRpm = Double.NaN;
    private static double lastLiveTps = Double.NaN;
    private static double lastLiveWindowMs = Double.NaN;
    private static double lastLiveWindowSamples = Double.NaN;
    private static double lastLiveStride = Double.NaN;
    private static double lastDecelActiveSeconds = Double.NaN;
    private static double stableSince = Double.NaN;
    private static double startTps = Double.NaN;
    private static double startRpm = Double.NaN;
    private static double targetTps = Double.NaN;
    private static double targetMin = Double.NaN;
    private static double targetMax = Double.NaN;
    private static double movementStart = Double.NaN;
    private static double postPeakStart = Double.NaN;
    private static double peakTps = Double.NaN;
    private static boolean targetReached;
    private static int detectorBursts;
    private static int postPeakRetriggers;
    private static boolean detectorRawActive;
    private static boolean detectorSeen;
    private static boolean detectorCueLatched;
    private static boolean detectorInactiveAfterPeak;
    private static double firstDetectorSeconds = Double.NaN;
    private static double detectorClearSeconds = Double.NaN;
    private static double cycleResumeSeconds = Double.NaN;
    private static double peakSeconds = Double.NaN;
    private static EngagementManeuverQualityPolicy.Quality lastAttemptQuality;
    private static double lastAttemptPeakDeltaTps = Double.NaN;
    private static String lastAttemptReason = "";

    private static final ScheduledExecutorService WATCHDOG =
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "ae-tuner-engagement-sweep-watchdog");
                    thread.setDaemon(true);
                    return thread;
                }
            });

    static {
        WATCHDOG.scheduleAtFixedRate(new Runnable() {
            @Override public void run() { watchdog(); }
        }, 500L, 500L, TimeUnit.MILLISECONDS);
    }

    private EngagementDeltaWindowSweepRuntime() { }

    public static Config pendingConfig() { synchronized (LOCK) { return pendingConfig; } }

    public static boolean updatePendingConfig(Config config) {
        if (config == null) return false;
        synchronized (LOCK) {
            if (runActive) return false;
            boolean changed = !sameConfig(pendingConfig, config);
            pendingConfig = config;
            if (phase == Phase.COMPLETE && changed) {
                rearmRequested = true;
                status = "NEW RPM REGION ARMED — full Sample Length -> Delta Window sequence will repeat near "
                        + f0(config.rpmStartingPoint) + " RPM";
            }
            return true;
        }
    }

    public static void observeWorkingTune(AeProjectSnapshot snapshot) {
        if (snapshot == null) return;
        synchronized (LOCK) {
            if (workingSnapshot == snapshot) return;
            if (!restoreAllTemporaryLocked("fresh working-tune baseline")) return;
            workingSnapshot = snapshot;
            baselineMs = snapshot.getEngagementDeltaWindowMs();
            baselineSampleLengthSeconds = snapshot.getEngagementSampleLengthSeconds();
            selectedSampleLengthSeconds = baselineSampleLengthSeconds;
            writer = testWriter;
            legacyTestMode = testWriter != null && !testWriter.supportsSampleLength();
            runActive = false;
            rearmRequested = false;
            phase = Phase.IDLE;
            stage = legacyTestMode ? Stage.DELTA_WINDOW : Stage.SAMPLE_LENGTH;
            candidateValues = new double[0];
            currentCandidates = new CandidateMetrics[0];
            candidateIndex = 0;
            completedRuns.clear();
            currentSampleResult = null;
            lastDecelActiveSeconds = Double.NaN;
            clearLastAttemptLocked();
            resetManeuverLocked();
            status = snapshot.hasEngagementDeltaWindow() && snapshot.hasEngagementSampleLength()
                    ? "Ready for controlled Sample Length -> Delta Window sweep from "
                        + f0(baselineSampleLengthSeconds * 1000.0) + " ms / " + f1(baselineMs) + " ms baseline"
                    : "TPS Movement / Timing baseline unavailable; controlled sweep disabled";
        }
    }

    public static boolean accept(AeProjectSnapshot snapshot, LiveSample sample) {
        if (snapshot == null || sample == null) return false;
        synchronized (LOCK) {
            if (workingSnapshot != snapshot) observeWorkingTune(snapshot);
            lastSampleWallNano = System.nanoTime();
            lastLiveRpm = sample.get(ChannelRole.RPM);
            lastLiveTps = sample.get(ChannelRole.TPS);
            lastLiveWindowMs = sample.get(ChannelRole.AE_WINDOW_MS);
            lastLiveWindowSamples = sample.get(ChannelRole.AE_WINDOW_SAMPLES);
            lastLiveStride = sample.get(ChannelRole.AE_DELTA_STRIDE);
            trackDecelLocked(sample);
            if (!snapshot.hasEngagementDeltaWindow() || !snapshot.hasEngagementSampleLength()) return false;
            if (phase == Phase.ERROR) return false;
            if (!runActive) {
                if (phase == Phase.COMPLETE && !rearmRequested) return false;
                if (!startRunLocked()) return false;
            }
            if (!ensureCandidateAppliedLocked()) return false;
            updateTimingQualificationLocked(sample);
            if (!liveTimingQualified) {
                phase = Phase.SEEK_START;
                resetManeuverLocked();
                status = "VERIFYING LIVE " + stageName(stage) + " — requested "
                        + candidateLabelLocked() + "; ECU reports window "
                        + f1(lastLiveWindowMs) + " ms / " + f0(lastLiveWindowSamples)
                        + " samples / stride " + f0(lastLiveStride);
                return false;
            }

            if ((phase == Phase.SEEK_START || phase == Phase.READY)
                    && !decelStartGateReadyLocked(sample)) {
                resetManeuverLocked();
                return false;
            }

            FoundationTpsNoiseGate.Evaluation gate = FoundationTpsNoiseGate.evaluate(sample);
            if (!validCore(sample, gate)) {
                resetManeuverLocked();
                return false;
            }
            switch (phase) {
                case SEEK_START: processSeekLocked(sample, gate); return false;
                case READY: processReadyLocked(sample, gate); return false;
                case MOVING: processMovingLocked(sample); return false;
                case POST_STOP: return processPostPeakLocked(sample);
                default: return false;
            }
        }
    }

    public static double coachedDetectorOutput(LiveSample sample) {
        if (sample == null) return Double.NaN;
        synchronized (LOCK) {
            double delta = sample.get(ChannelRole.DELTA_TPS);
            double threshold = sample.get(ChannelRole.ACCEL_THRESHOLD);
            if (!runActive || !Double.isFinite(delta)
                    || !Double.isFinite(threshold) || threshold <= EPSILON) {
                return FoundationTpsNoiseGate.evaluate(sample).coachedDetectorOutput();
            }
            if (detectorCueLatched && (phase == Phase.MOVING || phase == Phase.POST_STOP)) {
                return Math.max(delta, threshold + 0.0001);
            }
            return Math.min(delta, threshold);
        }
    }

    public static Snapshot snapshot() {
        synchronized (LOCK) {
            double candidate = candidateIndex >= 0 && candidateIndex < candidateValues.length
                    ? candidateValues[candidateIndex] : (stage == Stage.SAMPLE_LENGTH
                        ? baselineSampleLengthSeconds * 1000.0 : baselineMs);
            Recommendation recommendation = recommendationLocked();
            CandidateMetrics metrics = currentCandidates.length > 0
                    && candidateIndex >= 0 && candidateIndex < currentCandidates.length
                    ? currentCandidates[candidateIndex] : null;
            Config config = runActive ? activeConfig : pendingConfig;
            return new Snapshot(writerAvailableLocked(), runActive, phase, stage, config,
                    baselineMs,
                    stage == Stage.DELTA_WINDOW ? candidate : baselineMs,
                    baselineSampleLengthSeconds,
                    stage == Stage.SAMPLE_LENGTH ? candidate / 1000.0 : selectedSampleLengthSeconds,
                    selectedSampleLengthSeconds,
                    candidateIndex, candidateValues.length,
                    metrics == null ? 0 : metrics.events, config.eventsPerCandidate,
                    metrics == null ? 0 : metrics.attempts,
                    metrics == null ? 0 : metrics.diagnosticOnly,
                    metrics == null ? 0 : metrics.rejected,
                    lastLiveRpm, lastLiveTps, startTps, targetTps, targetMin, targetMax,
                    peakTps, targetReached, liveTimingQualified,
                    lastLiveWindowMs, lastLiveWindowSamples, lastLiveStride,
                    lastAttemptQuality, lastAttemptPeakDeltaTps, lastAttemptReason,
                    completedRuns.size(), distinctRpmRegionsLocked(),
                    recommendation.deltaWindowMs, recommendation.sampleLengthSeconds,
                    recommendation.ready, candidateRevision, status);
        }
    }

    public static boolean hasCompletedRun() { synchronized (LOCK) { return !completedRuns.isEmpty(); } }

    static boolean pauseForLifecycle() {
        synchronized (LOCK) {
            if (!restoreAllTemporaryLocked("Pause")) return false;
            lastSampleWallNano = 0L;
            resetManeuverLocked();
            if (runActive) {
                currentCandidateApplied = false;
                sampleWinnerHeld = false;
                timingQualifiedSamples = 0;
                liveTimingQualified = false;
                phase = Phase.SEEK_START;
                status = "PAUSED — original Sample Length and Delta Window restored; current stage will reapply automatically on Resume";
            }
            return true;
        }
    }

    static boolean finishForLifecycle() {
        synchronized (LOCK) {
            if (!restoreAllTemporaryLocked("Finish/Review")) return false;
            runActive = false;
            rearmRequested = false;
            currentCandidateApplied = false;
            sampleWinnerHeld = false;
            lastSampleWallNano = 0L;
            resetManeuverLocked();
            phase = completedRuns.isEmpty() ? Phase.IDLE : Phase.COMPLETE;
            stage = phase == Phase.COMPLETE ? Stage.COMPLETE : Stage.SAMPLE_LENGTH;
            status = completedRuns.isEmpty()
                    ? "Capture finished at original timing baseline; no full RPM-region sequence retained"
                    : "Capture finished at original timing baseline; completed RPM-region evidence retained";
            return true;
        }
    }

    static boolean resetForLifecycle() {
        synchronized (LOCK) {
            if (!restoreAllTemporaryLocked("Reset Session")) return false;
            clearRuntimeLocked();
            status = "Controlled TPS Movement / Timing sweep reset at verified original baseline";
            return true;
        }
    }

    static boolean closeForLifecycle() {
        synchronized (LOCK) {
            if (!restoreAllTemporaryLocked("plugin Close")) return false;
            clearRuntimeLocked();
            status = "Controlled TPS Movement / Timing sweep closed at verified original baseline";
            return true;
        }
    }

    public static ProposalWritePlan recommendationPlan(AeProjectSnapshot snapshot) {
        synchronized (LOCK) {
            if (!restoreAllTemporaryLocked("review")) return null;
            Recommendation r = recommendationLocked();
            if (!r.ready) return null;
            List<ProposalWritePlan.Change> changes = new ArrayList<ProposalWritePlan.Change>();
            if (Double.isFinite(r.sampleLengthSeconds)
                    && Math.abs(r.sampleLengthSeconds - baselineSampleLengthSeconds) > EPSILON) {
                changes.add(ProposalWritePlan.Change.scalar(AeParameterNames.TPS_ACCEL_LOOKBACK,
                        baselineSampleLengthSeconds, r.sampleLengthSeconds,
                        "Sample Length", "s"));
            }
            if (Double.isFinite(r.deltaWindowMs)
                    && Math.abs(r.deltaWindowMs - baselineMs) > EPSILON) {
                changes.add(ProposalWritePlan.Change.scalar(AeParameterNames.TPS_AE_DELTA_WINDOW_MS,
                        baselineMs, r.deltaWindowMs, "Delta Window", "ms"));
            }
            if (changes.isEmpty()) return null;
            return new ProposalWritePlan("engagement-timing-pair",
                    "AE Foundation — TPS Movement / Timing",
                    snapshot.getConfigurationName(),
                    "Evidence-backed timing pair agreed across distinct RPM regions. Explicit Apply only; Restore remains available; no burn.",
                    changes);
        }
    }

    public static String reviewText() {
        synchronized (LOCK) {
            Recommendation recommendation = recommendationLocked();
            StringBuilder out = new StringBuilder();
            out.append("CONTROLLED TPS MOVEMENT / TIMING SWEEP\n")
                    .append("Order: Sample Length -> Delta Window\n")
                    .append("Original baseline: Sample Length ")
                    .append(f0(baselineSampleLengthSeconds * 1000.0)).append(" ms | Delta Window ")
                    .append(f1(baselineMs)).append(" ms\n")
                    .append("Completed RPM-region sequences: ").append(completedRuns.size())
                    .append(" | distinct regions: ").append(distinctRpmRegionsLocked()).append('\n');
            for (int i = 0; i < completedRuns.size(); i++) {
                RunResult run = completedRuns.get(i);
                out.append("\nRun ").append(i + 1).append(" — ").append(run.config.summary()).append('\n');
                appendStage(out, run.sampleLength);
                appendStage(out, run.deltaWindow);
            }
            out.append("\nCombined decision: ").append(recommendation.reason).append('\n');
            if (recommendation.ready) {
                out.append("Accepted pair: Sample Length ")
                        .append(f0(recommendation.sampleLengthSeconds * 1000.0)).append(" ms | Delta Window ")
                        .append(f1(recommendation.deltaWindowMs)).append(" ms\n")
                        .append("Original working-tune values are restored. Use Apply Current Proposal explicitly only after review. No burn.");
            } else {
                out.append("No final timing Apply plan is exposed yet. Original working-tune values remain restored.");
            }
            return out.toString();
        }
    }

    private static void appendStage(StringBuilder out, StageResult result) {
        if (result == null) return;
        out.append("  ").append(stageName(result.stage)).append('\n');
        for (CandidateMetrics candidate : result.candidates) {
            out.append("    ").append(candidate.summary(result.stage)).append('\n');
        }
        out.append("    Winner: ").append(f1(result.winner)).append(" ms — ")
                .append(result.reason).append('\n');
    }

    static void setWriterForTest(TemporaryWriter override) {
        synchronized (LOCK) {
            if (!restoreAllTemporaryLocked("test writer change")) return;
            testWriter = override;
            writer = override;
            legacyTestMode = override != null && !override.supportsSampleLength();
        }
    }

    static void forceWatchdogForTest() {
        synchronized (LOCK) { lastSampleWallNano = System.nanoTime() - WATCHDOG_NS - 1L; }
        watchdog();
    }

    static void resetForTest() {
        synchronized (LOCK) {
            if (!restoreAllTemporaryLocked("test reset")) return;
            clearRuntimeLocked();
            status = "Controlled TPS Movement / Timing sweep idle";
        }
    }

    private static void clearRuntimeLocked() {
        pendingConfig = Config.defaults();
        activeConfig = pendingConfig;
        workingSnapshot = null;
        baselineMs = Double.NaN;
        baselineSampleLengthSeconds = Double.NaN;
        selectedSampleLengthSeconds = Double.NaN;
        writer = null;
        testWriter = null;
        legacyTestMode = false;
        runActive = false;
        rearmRequested = false;
        phase = Phase.IDLE;
        stage = Stage.SAMPLE_LENGTH;
        candidateValues = new double[0];
        currentCandidates = new CandidateMetrics[0];
        candidateIndex = 0;
        candidateRevision = 0L;
        currentCandidateApplied = false;
        sampleWinnerHeld = false;
        timingQualifiedSamples = 0;
        liveTimingQualified = false;
        currentSampleResult = null;
        completedRuns.clear();
        lastSampleWallNano = 0L;
        lastLiveRpm = Double.NaN;
        lastLiveTps = Double.NaN;
        lastLiveWindowMs = Double.NaN;
        lastLiveWindowSamples = Double.NaN;
        lastLiveStride = Double.NaN;
        lastDecelActiveSeconds = Double.NaN;
        clearLastAttemptLocked();
        resetManeuverLocked();
    }

    private static void clearLastAttemptLocked() {
        lastAttemptQuality = null;
        lastAttemptPeakDeltaTps = Double.NaN;
        lastAttemptReason = "";
    }

    private static boolean startRunLocked() {
        if (!Double.isFinite(baselineMs) || baselineMs <= 0.0
                || !Double.isFinite(baselineSampleLengthSeconds)
                || baselineSampleLengthSeconds <= 0.0) {
            status = "Controlled sweep blocked: finite positive timing baselines required";
            return false;
        }
        activeConfig = pendingConfig;
        if (writer == null) writer = productionWriterLocked();
        if (writer == null) {
            status = "Controlled sweep unavailable without live guarded working-tune access";
            return false;
        }
        legacyTestMode = !writer.supportsSampleLength();
        stage = legacyTestMode ? Stage.DELTA_WINDOW : Stage.SAMPLE_LENGTH;
        currentSampleResult = null;
        selectedSampleLengthSeconds = baselineSampleLengthSeconds;
        prepareStageCandidatesLocked();
        runActive = true;
        rearmRequested = false;
        phase = Phase.SEEK_START;
        clearLastAttemptLocked();
        resetManeuverLocked();
        status = stage == Stage.SAMPLE_LENGTH
                ? "STAGE 1/2 — Sample Length. Candidate 1/" + candidateValues.length
                    + " at " + f0(candidateValues[0]) + " ms; stabilize near "
                    + f0(activeConfig.rpmStartingPoint) + " RPM"
                : "Legacy test mode — Delta Window stage";
        return true;
    }

    private static TemporaryWriter productionWriterLocked() {
        ControllerAccess access = AeControllerBridge.latestControllerAccess();
        if (access == null || workingSnapshot == null) return null;
        try { return new ProductionWriter(workingSnapshot, access); }
        catch (RuntimeException ex) {
            status = "Could not initialize guarded timing writer: " + safeMessage(ex);
            return null;
        }
    }

    private static void prepareStageCandidatesLocked() {
        if (stage == Stage.SAMPLE_LENGTH) {
            double base = baselineSampleLengthSeconds * 1000.0;
            candidateValues = new double[]{base,
                    base + SAMPLE_LENGTH_STEP_SECONDS * 1000.0,
                    base + SAMPLE_LENGTH_STEP_SECONDS * 2000.0};
        } else {
            double maxDistinct = Math.max(baselineMs,
                    selectedSampleLengthSeconds * 1000.0 / 2.0);
            List<Double> values = new ArrayList<Double>();
            values.add(Double.valueOf(baselineMs));
            double lower = Math.max(1.0, baselineMs - activeConfig.candidateStepMs);
            if (Math.abs(lower - baselineMs) > EPSILON) values.add(Double.valueOf(lower));
            double upper = baselineMs + activeConfig.candidateStepMs;
            if (upper <= maxDistinct + EPSILON) values.add(Double.valueOf(upper));
            candidateValues = new double[values.size()];
            for (int i = 0; i < values.size(); i++) candidateValues[i] = values.get(i).doubleValue();
        }
        currentCandidates = new CandidateMetrics[candidateValues.length];
        for (int i = 0; i < candidateValues.length; i++) currentCandidates[i] = new CandidateMetrics(candidateValues[i]);
        candidateIndex = 0;
        currentCandidateApplied = false;
        timingQualifiedSamples = 0;
        liveTimingQualified = false;
        candidateRevision++;
    }

    private static boolean ensureCandidateAppliedLocked() {
        if (currentCandidateApplied) return true;
        if (candidateIndex < 0 || candidateIndex >= candidateValues.length) return false;
        double candidate = candidateValues[candidateIndex];
        WriteResult result;
        if (stage == Stage.SAMPLE_LENGTH) {
            double seconds = candidate / 1000.0;
            if (Math.abs(seconds - baselineSampleLengthSeconds) <= EPSILON) {
                currentCandidateApplied = true;
                return true;
            }
            result = writer.applySampleLength(seconds);
        } else {
            if (!sampleWinnerHeld && Math.abs(selectedSampleLengthSeconds - baselineSampleLengthSeconds) > EPSILON) {
                result = writer.applySampleLength(selectedSampleLengthSeconds);
                if (!result.success) {
                    failLocked("Could not reapply selected Sample Length before Delta stage: " + result.message);
                    return false;
                }
                sampleWinnerHeld = true;
            }
            if (Math.abs(candidate - baselineMs) <= EPSILON) {
                currentCandidateApplied = true;
                return true;
            }
            result = writer.apply(candidate);
        }
        if (!result.success) {
            failLocked("Temporary " + stageName(stage) + " candidate Apply failed: " + result.message);
            return false;
        }
        currentCandidateApplied = true;
        timingQualifiedSamples = 0;
        liveTimingQualified = false;
        status = "Temporary " + stageName(stage) + " " + candidateLabelLocked()
                + " applied/read back; waiting for live ECU timing proof";
        return true;
    }

    private static void updateTimingQualificationLocked(LiveSample sample) {
        if (!currentCandidateApplied || candidateIndex < 0 || candidateIndex >= candidateValues.length) {
            liveTimingQualified = false;
            timingQualifiedSamples = 0;
            return;
        }
        double windowMs = sample.get(ChannelRole.AE_WINDOW_MS);
        double windowSamples = sample.get(ChannelRole.AE_WINDOW_SAMPLES);
        double stride = sample.get(ChannelRole.AE_DELTA_STRIDE);
        boolean qualified = false;
        if (stage == Stage.SAMPLE_LENGTH) {
            double requestedMs = candidateValues[candidateIndex];
            qualified = Double.isFinite(windowMs) && Double.isFinite(windowSamples)
                    && Math.abs(windowMs - requestedMs) <= Math.max(2.0, requestedMs * 0.06)
                    && windowSamples >= 2.0;
        } else {
            double requestedMs = candidateValues[candidateIndex];
            if (Double.isFinite(windowMs) && Double.isFinite(windowSamples)
                    && Double.isFinite(stride) && windowSamples >= 2.0 && windowMs > 0.0) {
                double samplePeriod = windowMs / windowSamples;
                double effectiveMs = stride * samplePeriod;
                qualified = Math.abs(effectiveMs - requestedMs)
                        <= Math.max(samplePeriod * 0.60, 2.0);
            }
        }
        timingQualifiedSamples = qualified ? timingQualifiedSamples + 1 : 0;
        liveTimingQualified = timingQualifiedSamples >= TIMING_QUALIFY_SAMPLES;
    }

    private static void processSeekLocked(LiveSample sample,
                                          FoundationTpsNoiseGate.Evaluation gate) {
        if (!gate.calibrated) {
            stableSince = Double.NaN;
            status = "Calibrating quiet TPS noise before controlled maneuvers";
            return;
        }
        double rpm = sample.get(ChannelRole.RPM);
        double tps = sample.get(ChannelRole.TPS);
        double delta = sample.get(ChannelRole.DELTA_TPS);
        double threshold = sample.get(ChannelRole.ACCEL_THRESHOLD);
        double stableRate = Math.max(4.0, gate.intentRateFloor * 0.50);
        boolean stable = Math.abs(rpm - activeConfig.rpmStartingPoint) <= activeConfig.rpmStartRange
                && Math.abs(sample.getTpsDot()) <= stableRate && delta <= threshold;
        if (!stable) {
            stableSince = Double.NaN;
            status = stagePrefix() + " — hold "
                    + f0(activeConfig.rpmStartingPoint - activeConfig.rpmStartRange) + "–"
                    + f0(activeConfig.rpmStartingPoint + activeConfig.rpmStartRange)
                    + " RPM approximately steady";
            return;
        }
        if (!Double.isFinite(stableSince)) stableSince = sample.getSeconds();
        if (sample.getSeconds() - stableSince < STABLE_SECONDS) {
            status = stagePrefix() + " — stabilizing near " + f0(rpm) + " RPM";
            return;
        }
        startTps = tps;
        startRpm = rpm;
        targetTps = clamp(startTps + activeConfig.tpsStep, 0.0, 100.0);
        targetMin = clamp(targetTps + activeConfig.targetMinOffset, 0.0, 100.0);
        targetMax = clamp(targetTps + activeConfig.targetMaxOffset, 0.0, 100.0);
        phase = Phase.READY;
        status = stagePrefix() + " — READY: TPS " + f1(startTps)
                + " -> target " + f1(targetTps) + " (" + f1(targetMin) + " to " + f1(targetMax) + ")";
    }

    private static void processReadyLocked(LiveSample sample,
                                           FoundationTpsNoiseGate.Evaluation gate) {
        double rpm = sample.get(ChannelRole.RPM);
        double tps = sample.get(ChannelRole.TPS);
        if (Math.abs(rpm - activeConfig.rpmStartingPoint) > activeConfig.rpmStartRange
                || Math.abs(tps - startTps) > Math.max(2.5, activeConfig.tpsStep * 0.30)) {
            resetManeuverLocked();
            status = stagePrefix() + " — start condition moved; reacquiring";
            return;
        }
        if (sample.getTpsDot() <= gate.intentRateFloor) return;
        phase = Phase.MOVING;
        movementStart = sample.getSeconds();
        peakTps = tps;
        targetReached = peakTps >= targetMin;
        detectorBursts = 0;
        postPeakRetriggers = 0;
        detectorRawActive = false;
        detectorSeen = false;
        detectorCueLatched = false;
        detectorInactiveAfterPeak = false;
        firstDetectorSeconds = Double.NaN;
        detectorClearSeconds = Double.NaN;
        cycleResumeSeconds = Double.NaN;
        peakSeconds = Double.NaN;
        postPeakStart = Double.NaN;
        observeDetectorLocked(sample, false);
        status = stagePrefix() + " — MOVING: aim for target band and let the movement peak naturally";
    }

    private static void processMovingLocked(LiveSample sample) {
        double tps = sample.get(ChannelRole.TPS);
        double tpsRate = sample.getTpsDot();
        peakTps = !Double.isFinite(peakTps) ? tps : Math.max(peakTps, tps);
        if (peakTps >= targetMin) targetReached = true;
        observeDetectorLocked(sample, false);
        double elapsed = sample.getSeconds() - movementStart;
        boolean reversal = tpsRate <= 0.0 || tps <= peakTps - PEAK_REVERSAL_DROP_TPS;
        boolean naturalPeak = Math.abs(tpsRate) <= PEAK_RATE_EPSILON;
        boolean timedPeak = elapsed > MANEUVER_TIMEOUT_SECONDS;
        if (!(reversal || naturalPeak || timedPeak)) return;
        phase = Phase.POST_STOP;
        postPeakStart = sample.getSeconds();
        peakSeconds = postPeakStart;
        detectorInactiveAfterPeak = !detectorRawActive;
        status = stagePrefix() + " — PEAK CAPTURED: observing detector clear / AE cycle resume";
    }

    private static boolean processPostPeakLocked(LiveSample sample) {
        observeDetectorLocked(sample, true);
        double cycle = sample.get(ChannelRole.TPS_AE_CYCLE_CNT);
        if (!Double.isFinite(cycleResumeSeconds) && Double.isFinite(cycle) && cycle > 0.0) {
            cycleResumeSeconds = sample.getSeconds();
        }
        if (sample.getSeconds() - postPeakStart < POST_PEAK_SECONDS) return false;

        EventMetrics event = new EventMetrics();
        event.startRpm = startRpm;
        event.movementStartSeconds = movementStart;
        event.firstDetectorSeconds = firstDetectorSeconds;
        event.detectorBursts = detectorBursts;
        event.postPeakRetriggers = postPeakRetriggers;
        event.peakTps = peakTps;
        event.targetTps = targetTps;
        if (Double.isFinite(detectorClearSeconds) && Double.isFinite(peakSeconds))
            event.detectorClearDelay = Math.max(0.0, detectorClearSeconds - peakSeconds);
        if (Double.isFinite(cycleResumeSeconds) && Double.isFinite(peakSeconds))
            event.cycleResumeDelay = Math.max(0.0, cycleResumeSeconds - peakSeconds);

        EngagementManeuverQualityPolicy.Assessment assessment =
                EngagementManeuverQualityPolicy.assess(startTps, peakTps, targetTps,
                        targetMin, targetMax, activeConfig.tpsStep);
        lastAttemptQuality = assessment.quality;
        lastAttemptPeakDeltaTps = assessment.peakDeltaTps;
        lastAttemptReason = assessment.reason;
        CandidateMetrics candidate = currentCandidates[candidateIndex];
        boolean comparable = candidate.addAttempt(event, assessment, stage);
        int accepted = candidate.events;
        int attempts = candidate.attempts;
        if (!comparable) {
            resetManeuverLocked();
            status = stagePrefix() + " — "
                    + (assessment.quality == EngagementManeuverQualityPolicy.Quality.REJECT
                    ? "REJECTED" : "DIAGNOSTIC ONLY") + ": " + assessment.reason
                    + "; progress " + accepted + "/" + activeConfig.eventsPerCandidate
                    + " | attempts " + attempts;
            return false;
        }
        if (candidate.events >= activeConfig.eventsPerCandidate) return advanceCandidateLocked();
        resetManeuverLocked();
        status = stagePrefix() + " — CAPTURED " + assessment.quality.name().replace('_', ' ')
                + " " + accepted + "/" + activeConfig.eventsPerCandidate
                + " | attempts " + attempts + " — reacquire start condition";
        return true;
    }

    private static void observeDetectorLocked(LiveSample sample, boolean afterPeak) {
        double delta = sample.get(ChannelRole.DELTA_TPS);
        double threshold = sample.get(ChannelRole.ACCEL_THRESHOLD);
        boolean raw = Double.isFinite(delta) && Double.isFinite(threshold)
                && threshold > EPSILON && delta > threshold;
        if (raw && !detectorRawActive) {
            detectorBursts++;
            if (!detectorSeen) {
                detectorSeen = true;
                detectorCueLatched = true;
                firstDetectorSeconds = sample.getSeconds();
            } else if (afterPeak && detectorInactiveAfterPeak) postPeakRetriggers++;
        }
        if (afterPeak && !raw) {
            detectorInactiveAfterPeak = true;
            if (!Double.isFinite(detectorClearSeconds)) detectorClearSeconds = sample.getSeconds();
        }
        detectorRawActive = raw;
    }

    private static void trackDecelLocked(LiveSample sample) {
        double decel = sample.get(ChannelRole.TPS_DECEL_ACTIVE);
        if (Double.isFinite(decel) && decel > 0.5 && Double.isFinite(sample.getSeconds())) {
            lastDecelActiveSeconds = sample.getSeconds();
        }
    }

    private static boolean decelStartGateReadyLocked(LiveSample sample) {
        double decel = sample.get(ChannelRole.TPS_DECEL_ACTIVE);
        double now = sample.getSeconds();
        if (!Double.isFinite(decel)) {
            status = stagePrefix()
                    + " — start blocked: required Fuel: TPS Decel Active channel is unavailable";
            return false;
        }
        if (decel > 0.5) {
            if (Double.isFinite(now)) lastDecelActiveSeconds = now;
            status = stagePrefix()
                    + " — TPS Decel active; wait for decel to clear before reacquiring the start condition";
            return false;
        }
        if (Double.isFinite(lastDecelActiveSeconds)) {
            double quiet = now - lastDecelActiveSeconds;
            if (!Double.isFinite(quiet) || quiet < DECEL_REARM_SECONDS) {
                status = stagePrefix() + " — recent TPS Decel activity; re-arming for "
                        + f2(Math.max(0.0, DECEL_REARM_SECONDS - Math.max(0.0, quiet))) + " s";
                return false;
            }
        }
        return true;
    }

    private static boolean advanceCandidateLocked() {
        if (!restoreCurrentCandidateLocked("candidate block complete")) return false;
        candidateIndex++;
        resetManeuverLocked();
        if (candidateIndex >= candidateValues.length) return completeStageLocked();
        currentCandidateApplied = false;
        timingQualifiedSamples = 0;
        liveTimingQualified = false;
        candidateRevision++;
        if (activeConfig.transitionBeep) safeBeep();
        phase = Phase.SEEK_START;
        status = stagePrefix() + " — NEXT VALUE " + candidateLabelLocked()
                + " | candidate " + (candidateIndex + 1) + "/" + candidateValues.length
                + " | live timing proof required before READY";
        return true;
    }

    private static boolean completeStageLocked() {
        List<CandidateMetrics> copy = new ArrayList<CandidateMetrics>();
        for (CandidateMetrics candidate : currentCandidates) copy.add(candidate);
        double baselineValue = stage == Stage.SAMPLE_LENGTH
                ? baselineSampleLengthSeconds * 1000.0 : baselineMs;
        Winner winner = selectWinnerLocked(copy, baselineValue, stage);
        StageResult result = new StageResult(stage, Collections.unmodifiableList(copy),
                winner.value, winner.reason);
        if (stage == Stage.SAMPLE_LENGTH) {
            currentSampleResult = result;
            selectedSampleLengthSeconds = winner.value / 1000.0;
            stage = Stage.DELTA_WINDOW;
            prepareStageCandidatesLocked();
            phase = Phase.SEEK_START;
            runActive = true;
            if (Math.abs(selectedSampleLengthSeconds - baselineSampleLengthSeconds) > EPSILON) {
                WriteResult held = writer.applySampleLength(selectedSampleLengthSeconds);
                if (!held.success) {
                    failLocked("Could not hold selected Sample Length for Delta Window stage: " + held.message);
                    return false;
                }
                sampleWinnerHeld = true;
            }
            safeStageTransitionCue();
            status = "STAGE 1 COMPLETE — selected Sample Length "
                    + f0(selectedSampleLengthSeconds * 1000.0)
                    + " ms. STAGE 2/2 — Delta Window begins automatically; stabilize near "
                    + f0(activeConfig.rpmStartingPoint) + " RPM";
            return true;
        }

        completedRuns.add(new RunResult(activeConfig, currentSampleResult, result));
        if (!restoreAllTemporaryLocked("RPM-region sequence complete")) return false;
        runActive = false;
        rearmRequested = false;
        phase = Phase.COMPLETE;
        stage = Stage.COMPLETE;
        candidateRevision++;
        safeRegionCompleteCue();
        status = "RPM REGION COMPLETE — Sample Length and Delta Window finished near "
                + f0(activeConfig.rpmStartingPoint)
                + " RPM; original timing restored. Enter a new RPM Starting Point and Continue Capture.";
        return true;
    }

    private static Winner selectWinnerLocked(List<CandidateMetrics> candidates,
                                              double baselineValue, Stage scoringStage) {
        if (candidates.isEmpty()) return new Winner(baselineValue, "No complete candidate evidence");
        CandidateMetrics baseline = candidates.get(0);
        CandidateMetrics best = baseline;
        for (CandidateMetrics candidate : candidates) {
            if (candidate.score(scoringStage) + EPSILON < best.score(scoringStage)) best = candidate;
        }
        if (Math.abs(best.value - baselineValue) <= EPSILON) {
            return new Winner(baselineValue, "current value scored best or tied best");
        }
        double improvement = baseline.score(scoringStage) - best.score(scoringStage);
        double required = Math.max(0.50, Math.abs(baseline.score(scoringStage)) * 0.08);
        if (improvement < required) {
            return new Winner(baselineValue,
                    "neighbor improvement was too small to justify changing the current value");
        }
        return new Winner(best.value, "candidate reduced controlled-event penalty by " + f2(improvement));
    }

    private static Recommendation recommendationLocked() {
        if (completedRuns.isEmpty()) {
            return new Recommendation(false, Double.NaN, Double.NaN,
                    "No complete Sample Length -> Delta Window RPM-region sequence yet");
        }
        if (distinctRpmRegionsLocked() < MIN_RPM_REGIONS_FOR_RECOMMENDATION) {
            RunResult latest = completedRuns.get(completedRuns.size() - 1);
            return new Recommendation(false, Double.NaN, Double.NaN,
                    "Promising pair at the tested RPM region: Sample Length "
                    + f0(latest.sampleLength.winner) + " ms / Delta Window "
                    + f1(latest.deltaWindow.winner)
                    + " ms. Enter a distinct RPM Starting Point and repeat the full two-stage sequence.");
        }
        double sampleWinner = completedRuns.get(0).sampleLength.winner;
        double deltaWinner = completedRuns.get(0).deltaWindow.winner;
        for (RunResult run : completedRuns) {
            if (Math.abs(run.sampleLength.winner - sampleWinner) > EPSILON
                    || Math.abs(run.deltaWindow.winner - deltaWinner) > EPSILON) {
                return new Recommendation(false, Double.NaN, Double.NaN,
                        "RPM-region timing-pair results disagree; retain the original timing pair and do not use one region to compensate for another.");
            }
        }
        return new Recommendation(true, sampleWinner / 1000.0, deltaWinner,
                "The same Sample Length / Delta Window pair was preferred or indistinguishable across the tested RPM regions.");
    }

    private static int distinctRpmRegionsLocked() {
        List<Double> centers = new ArrayList<Double>();
        for (RunResult run : completedRuns) {
            boolean distinct = true;
            for (Double center : centers) {
                double separation = Math.abs(center.doubleValue() - run.config.rpmStartingPoint);
                double needed = Math.max(300.0, Math.min(run.config.rpmStartRange, 500.0));
                if (separation < needed) { distinct = false; break; }
            }
            if (distinct) centers.add(Double.valueOf(run.config.rpmStartingPoint));
        }
        return centers.size();
    }

    private static boolean restoreCurrentCandidateLocked(String reason) {
        if (!currentCandidateApplied) return true;
        double candidate = candidateValues[candidateIndex];
        boolean baselineCandidate = stage == Stage.SAMPLE_LENGTH
                ? Math.abs(candidate / 1000.0 - baselineSampleLengthSeconds) <= EPSILON
                : Math.abs(candidate - baselineMs) <= EPSILON;
        if (!baselineCandidate) {
            WriteResult restored = writer.restore();
            if (!restored.success) {
                failLocked("Automatic candidate restore failed during " + reason + ": " + restored.message);
                return false;
            }
        }
        currentCandidateApplied = false;
        timingQualifiedSamples = 0;
        liveTimingQualified = false;
        return true;
    }

    private static boolean restoreAllTemporaryLocked(String reason) {
        if (writer == null) {
            currentCandidateApplied = false;
            sampleWinnerHeld = false;
            return true;
        }
        int guard = 0;
        while (writer.temporaryActive() && guard++ < 8) {
            WriteResult restored = writer.restore();
            if (!restored.success) {
                failLocked("Automatic original-baseline restore failed during " + reason + ": " + restored.message);
                return false;
            }
        }
        if (writer.temporaryActive()) {
            failLocked("Automatic original-baseline restore exceeded safe restore depth during " + reason);
            return false;
        }
        currentCandidateApplied = false;
        sampleWinnerHeld = false;
        timingQualifiedSamples = 0;
        liveTimingQualified = false;
        return true;
    }

    private static void watchdog() {
        synchronized (LOCK) {
            if (!runActive || lastSampleWallNano == 0L) return;
            if (System.nanoTime() - lastSampleWallNano <= WATCHDOG_NS) return;
            if (!restoreAllTemporaryLocked("sample timeout/pause")) return;
            resetManeuverLocked();
            phase = Phase.SEEK_START;
            status = "Capture paused/disconnected — original timing restored; current "
                    + stageName(stage) + " candidate will be reapplied when samples resume";
            lastSampleWallNano = 0L;
        }
    }

    private static void failLocked(String message) {
        runActive = false;
        phase = Phase.ERROR;
        status = message == null ? "Controlled timing sweep failed" : message;
        resetManeuverLocked();
    }

    private static void resetManeuverLocked() {
        stableSince = Double.NaN;
        startTps = Double.NaN;
        startRpm = Double.NaN;
        targetTps = Double.NaN;
        targetMin = Double.NaN;
        targetMax = Double.NaN;
        movementStart = Double.NaN;
        postPeakStart = Double.NaN;
        peakTps = Double.NaN;
        targetReached = false;
        detectorBursts = 0;
        postPeakRetriggers = 0;
        detectorRawActive = false;
        detectorSeen = false;
        detectorCueLatched = false;
        detectorInactiveAfterPeak = false;
        firstDetectorSeconds = Double.NaN;
        detectorClearSeconds = Double.NaN;
        cycleResumeSeconds = Double.NaN;
        peakSeconds = Double.NaN;
        if (runActive && phase != Phase.ERROR && phase != Phase.COMPLETE) phase = Phase.SEEK_START;
    }

    private static boolean validCore(LiveSample sample, FoundationTpsNoiseGate.Evaluation gate) {
        return Double.isFinite(sample.get(ChannelRole.RPM))
                && Double.isFinite(sample.get(ChannelRole.TPS))
                && Double.isFinite(sample.get(ChannelRole.DELTA_TPS))
                && Double.isFinite(sample.get(ChannelRole.ACCEL_THRESHOLD))
                && sample.get(ChannelRole.ACCEL_THRESHOLD) > EPSILON
                && Double.isFinite(sample.getTpsDot()) && gate != null;
    }

    private static boolean writerAvailableLocked() {
        if (testWriter != null || writer != null) return true;
        return workingSnapshot != null && AeControllerBridge.latestControllerAccess() != null;
    }

    private static String candidateLabelLocked() {
        if (candidateIndex < 0 || candidateIndex >= candidateValues.length) return "n/a";
        return f1(candidateValues[candidateIndex]) + " ms";
    }

    private static String stagePrefix() {
        return stage == Stage.SAMPLE_LENGTH ? "STAGE 1/2 SAMPLE LENGTH" : "STAGE 2/2 DELTA WINDOW";
    }

    private static String stageName(Stage value) {
        if (value == Stage.SAMPLE_LENGTH) return "Sample Length";
        if (value == Stage.DELTA_WINDOW) return "Delta Window";
        return "Complete";
    }

    private static boolean sameConfig(Config a, Config b) {
        return a != null && b != null
                && Math.abs(a.rpmStartingPoint - b.rpmStartingPoint) <= EPSILON
                && Math.abs(a.rpmStartRange - b.rpmStartRange) <= EPSILON
                && Math.abs(a.tpsStep - b.tpsStep) <= EPSILON
                && Math.abs(a.targetMinOffset - b.targetMinOffset) <= EPSILON
                && Math.abs(a.targetMaxOffset - b.targetMaxOffset) <= EPSILON
                && a.eventsPerCandidate == b.eventsPerCandidate
                && Math.abs(a.candidateStepMs - b.candidateStepMs) <= EPSILON
                && a.transitionBeep == b.transitionBeep;
    }

    private static void safeBeep() {
        try { Toolkit.getDefaultToolkit().beep(); } catch (Throwable ignored) { }
    }

    private static void safeStageTransitionCue() {
        safeBeep();
        WATCHDOG.schedule(new Runnable() {
            @Override public void run() { safeBeep(); }
        }, 180L, TimeUnit.MILLISECONDS);
    }

    private static void safeRegionCompleteCue() {
        safeBeep();
        WATCHDOG.schedule(new Runnable() {
            @Override public void run() { safeBeep(); }
        }, 180L, TimeUnit.MILLISECONDS);
        WATCHDOG.schedule(new Runnable() {
            @Override public void run() { safeBeep(); }
        }, 360L, TimeUnit.MILLISECONDS);
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null) return "unknown error";
        String message = throwable.getMessage();
        return message == null || message.trim().length() == 0
                ? throwable.getClass().getSimpleName() : message.trim();
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String signed(double value) { return (value >= 0.0 ? "+" : "") + f1(value); }
    private static String f0(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.0f", value) : "n/a"; }
    private static String f1(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.1f", value) : "n/a"; }
    private static String f2(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.2f", value) : "n/a"; }
    private static String f3(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.3f", value) : "n/a"; }
}
