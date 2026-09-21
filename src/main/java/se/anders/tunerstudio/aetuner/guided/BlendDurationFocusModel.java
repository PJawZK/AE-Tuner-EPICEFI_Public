package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.List;
import java.util.Locale;

/**
 * Driver-facing projection of the physical-response Blend Duration capture.
 *
 * This class never decides capture validity and never proposes an ECU value.
 * BlendDurationGuidedSession remains the measurement authority; this model only
 * translates its state into the next physical action and compact progress.
 * Prediction/fallback values are exposed only as diagnostic context.
 */
public final class BlendDurationFocusModel {
    private static final long RUNTIME_PRESENTATION_INTERVAL_NS = 50000000L;
    private static final Object RUNTIME_CACHE_LOCK = new Object();
    private static BlendDurationFocusModel runtimeCachedModel;
    private static long runtimeCachedNano;
    private static GuidedCaptureState runtimeCachedState;
    private static BlendDurationCaptureConfig runtimeCachedConfig;
    private static BlendDurationComparabilityGroups runtimeCachedGroups;
    private static boolean runtimeCachedPlateau;
    private static long runtimeBuilds;
    private static long runtimeCacheHits;

    public enum DriverPhase {
        SETUP,
        GET_STEADY,
        OPEN_AND_SETTLE,
        HOLD_FOR_MAP,
        RESULT,
        COMPLETE,
        PAUSED
    }

    public final GuidedCaptureState captureState;
    public final DriverPhase phase;
    public final String instruction;
    public final String status;
    public final String lastResult;
    public final String detail;

    public final double targetRpm;
    public final double liveRpm;
    public final double rpmError;
    public final double rpmTolerance;
    public final boolean rpmInRange;

    public final double baselineTps;
    public final double liveTps;
    public final double liveTpsStep;
    public final double desiredTpsStep;
    public final double tpsStepLow;
    public final double tpsStepHigh;
    public final boolean tpsStepInRange;

    public final double liveMap;
    public final double liveFallbackMap;
    public final double liveEffectiveMap;

    /** Primary physical-response projection. */
    public final double physicalBaselineMap;
    public final double physicalLateMap;
    public final double physicalMapStep;
    public final double responseLowMap;
    public final double responseHighMap;
    public final double physicalResponseSeconds;
    public final double physicalObservationSeconds;
    public final boolean physicalResponseComplete;
    public final boolean boundedLateWindow;

    /** Prediction diagnostics only. */
    public final double predictionTarget;
    public final double mapRemaining;
    public final double targetGap;
    public final double currentBlendDuration;
    public final boolean softPlateauAcquired;
    /** Compatibility alias for older UI/tests; now equals physicalResponseSeconds. */
    public final double physicalCatchupSeconds;
    public final int effectiveMapReplaySamples;
    public final double effectiveMapMeanAbsoluteError;
    public final double effectiveMapMaxAbsoluteError;
    public final boolean effectiveMapReplayConsistent;
    public final String predictionCounterEvidence;

    public final double lastEventDuration;
    public final double lastEventBaseRpm;
    public final double lastEventBaseMap;
    public final double lastEventBaseTps;
    public final double lastEventHeldTps;
    /** Compatibility field: now the last event's physical MAP step. */
    public final double lastEventGap;
    public final double lastEventPredictionGap;
    public final double lastEventPhysicalLateMap;
    public final String lastEventTrend;

    public final int matchingEvents;
    public final int targetEvents;
    public final int allValidEvents;
    public final int excludedEvents;
    public final int returnedEvents;
    public final String repeatability;
    public final String comparabilityHint;
    public final boolean numericalApplyWithheld;

    /** Presentation-only time-domain traces; never measurement authority. */
    public final BlendDurationFocusTrace currentTrace;
    public final BlendDurationFocusTrace previousAcceptedTrace;

    private BlendDurationFocusModel(GuidedCaptureState captureState,
                                    DriverPhase phase,
                                    String instruction,
                                    String status,
                                    String lastResult,
                                    String detail,
                                    double targetRpm,
                                    double liveRpm,
                                    double rpmTolerance,
                                    double baselineTps,
                                    double liveTps,
                                    double desiredTpsStep,
                                    double tpsStepLow,
                                    double tpsStepHigh,
                                    double liveMap,
                                    double liveFallbackMap,
                                    double liveEffectiveMap,
                                    double physicalBaselineMap,
                                    double physicalLateMap,
                                    double physicalMapStep,
                                    double responseLowMap,
                                    double responseHighMap,
                                    double physicalResponseSeconds,
                                    double physicalObservationSeconds,
                                    boolean physicalResponseComplete,
                                    boolean boundedLateWindow,
                                    double predictionTarget,
                                    double targetGap,
                                    double currentBlendDuration,
                                    boolean softPlateauAcquired,
                                    int effectiveMapReplaySamples,
                                    double effectiveMapMeanAbsoluteError,
                                    double effectiveMapMaxAbsoluteError,
                                    boolean effectiveMapReplayConsistent,
                                    String predictionCounterEvidence,
                                    double lastEventDuration,
                                    double lastEventBaseRpm,
                                    double lastEventBaseMap,
                                    double lastEventBaseTps,
                                    double lastEventHeldTps,
                                    double lastEventGap,
                                    double lastEventPredictionGap,
                                    double lastEventPhysicalLateMap,
                                    String lastEventTrend,
                                    int matchingEvents,
                                    int targetEvents,
                                    int allValidEvents,
                                    int excludedEvents,
                                    int returnedEvents,
                                    String repeatability,
                                    String comparabilityHint,
                                    BlendDurationFocusTrace currentTrace,
                                    BlendDurationFocusTrace previousAcceptedTrace) {
        this.captureState = captureState == null ? GuidedCaptureState.IDLE : captureState;
        this.phase = phase == null ? DriverPhase.SETUP : phase;
        this.instruction = safe(instruction);
        this.status = safe(status);
        this.lastResult = safe(lastResult);
        this.detail = safe(detail);
        this.targetRpm = targetRpm;
        this.liveRpm = liveRpm;
        this.rpmError = Double.isFinite(liveRpm) && Double.isFinite(targetRpm)
                ? liveRpm - targetRpm : Double.NaN;
        this.rpmTolerance = rpmTolerance;
        this.rpmInRange = Double.isFinite(rpmError) && Double.isFinite(rpmTolerance)
                && Math.abs(rpmError) <= rpmTolerance;
        this.baselineTps = baselineTps;
        this.liveTps = liveTps;
        this.liveTpsStep = Double.isFinite(liveTps) && Double.isFinite(baselineTps)
                ? liveTps - baselineTps : Double.NaN;
        this.desiredTpsStep = desiredTpsStep;
        this.tpsStepLow = tpsStepLow;
        this.tpsStepHigh = tpsStepHigh;
        this.tpsStepInRange = Double.isFinite(liveTpsStep)
                && liveTpsStep >= tpsStepLow && liveTpsStep <= tpsStepHigh;
        this.liveMap = liveMap;
        this.liveFallbackMap = liveFallbackMap;
        this.liveEffectiveMap = liveEffectiveMap;
        this.physicalBaselineMap = physicalBaselineMap;
        this.physicalLateMap = physicalLateMap;
        this.physicalMapStep = physicalMapStep;
        this.responseLowMap = responseLowMap;
        this.responseHighMap = responseHighMap;
        this.physicalResponseSeconds = physicalResponseSeconds;
        this.physicalObservationSeconds = physicalObservationSeconds;
        this.physicalResponseComplete = physicalResponseComplete;
        this.boundedLateWindow = boundedLateWindow;
        this.predictionTarget = predictionTarget;
        this.mapRemaining = Double.isFinite(predictionTarget) && Double.isFinite(liveMap)
                ? Math.max(0.0, predictionTarget - liveMap) : Double.NaN;
        this.targetGap = targetGap;
        this.currentBlendDuration = currentBlendDuration;
        this.softPlateauAcquired = softPlateauAcquired;
        this.physicalCatchupSeconds = physicalResponseSeconds;
        this.effectiveMapReplaySamples = Math.max(0, effectiveMapReplaySamples);
        this.effectiveMapMeanAbsoluteError = effectiveMapMeanAbsoluteError;
        this.effectiveMapMaxAbsoluteError = effectiveMapMaxAbsoluteError;
        this.effectiveMapReplayConsistent = effectiveMapReplayConsistent;
        this.predictionCounterEvidence = safe(predictionCounterEvidence);
        this.lastEventDuration = lastEventDuration;
        this.lastEventBaseRpm = lastEventBaseRpm;
        this.lastEventBaseMap = lastEventBaseMap;
        this.lastEventBaseTps = lastEventBaseTps;
        this.lastEventHeldTps = lastEventHeldTps;
        this.lastEventGap = lastEventGap;
        this.lastEventPredictionGap = lastEventPredictionGap;
        this.lastEventPhysicalLateMap = lastEventPhysicalLateMap;
        this.lastEventTrend = safe(lastEventTrend);
        this.matchingEvents = Math.max(0, matchingEvents);
        this.targetEvents = Math.max(1, targetEvents);
        this.allValidEvents = Math.max(0, allValidEvents);
        this.excludedEvents = Math.max(0, excludedEvents);
        this.returnedEvents = Math.max(0, returnedEvents);
        this.repeatability = safe(repeatability);
        this.comparabilityHint = safe(comparabilityHint);
        this.numericalApplyWithheld = true;
        this.currentTrace = currentTrace == null
                ? BlendDurationFocusTrace.empty() : currentTrace;
        this.previousAcceptedTrace = previousAcceptedTrace == null
                ? BlendDurationFocusTrace.empty() : previousAcceptedTrace;
    }

    static BlendDurationFocusModel build(GuidedCaptureState state,
                                         BlendDurationCaptureConfig config,
                                         LiveSample latest,
                                         RoadBaselineTracker.Baseline rollingBaseline,
                                         RoadBaselineTracker.Baseline frozenBaseline,
                                         LiveSample holdAnchor,
                                         boolean plateauAcquired,
                                         PhysicalMapResponseMeasurement physical,
                                         MapCatchupMeasurement predictionDiagnostic,
                                         BlendDurationComparabilityGroups groups,
                                         List<BlendDurationAttempt> validAttempts,
                                         int excluded,
                                         int returned,
                                         String instruction,
                                         String checkText,
                                         String latestResult,
                                         BlendDurationFocusTrace currentTrace,
                                         BlendDurationFocusTrace previousAcceptedTrace) {
        BlendDurationCaptureConfig safeConfig = config == null
                ? new BlendDurationCaptureConfig(2000.0, 20.0, 5, 0, false)
                : config;
        GuidedCaptureState safeState = state == null ? GuidedCaptureState.IDLE : state;

        boolean runtimeWorker = isRuntimeWorker();
        long runtimeNow = runtimeWorker ? System.nanoTime() : 0L;
        if (runtimeWorker && latest != null) {
            synchronized (RUNTIME_CACHE_LOCK) {
                long age = runtimeNow - runtimeCachedNano;
                if (runtimeCachedModel != null
                        && runtimeCachedState == safeState
                        && runtimeCachedConfig == safeConfig
                        && runtimeCachedGroups == groups
                        && runtimeCachedPlateau == plateauAcquired
                        && age >= 0L && age < RUNTIME_PRESENTATION_INTERVAL_NS) {
                    runtimeCacheHits++;
                    return runtimeCachedModel;
                }
            }
        }

        DriverPhase phase = phase(safeState, plateauAcquired);
        double liveRpm = value(latest, ChannelRole.RPM);
        double liveTps = value(latest, ChannelRole.TPS);
        double liveMap = value(latest, ChannelRole.MAP);
        double liveFallbackMap = value(latest, ChannelRole.FALLBACK_MAP);
        double liveEffectiveMap = value(latest, ChannelRole.EFFECTIVE_MAP);
        RoadBaselineTracker.Baseline base = frozenBaseline != null
                && frozenBaseline.valid() ? frozenBaseline : rollingBaseline;
        double baseTps = base == null ? Double.NaN : base.tps;

        double rpmTolerance = BlendDurationGuidedSession.ENTRY_RPM_TOLERANCE;
        double physicalBaseline = physical == null ? Double.NaN : physical.baselineMap();
        double physicalLate = physical == null ? Double.NaN : physical.lateMap();
        double physicalStep = physical == null ? Double.NaN : physical.mapStep();
        double lowMap = physical == null ? Double.NaN : physical.lowThreshold();
        double highMap = physical == null ? Double.NaN : physical.highThreshold();
        double responseSeconds = physical == null ? Double.NaN : physical.durationSeconds();
        double observationSeconds = physical == null
                ? Double.NaN : physical.observationElapsedSeconds(latest);
        boolean responseComplete = physical != null && physical.isComplete();
        boolean bounded = physical != null && physical.usedBoundedLateWindow();

        double target = predictionDiagnostic == null
                ? Double.NaN : predictionDiagnostic.finalPredictionTarget();
        double predictionGap = predictionDiagnostic == null
                ? Double.NaN : predictionDiagnostic.bestGap();
        int replaySamples = predictionDiagnostic == null
                ? 0 : predictionDiagnostic.modelSampleCount();
        double replayMean = predictionDiagnostic == null
                ? Double.NaN : predictionDiagnostic.modelMeanAbsoluteError();
        double replayMax = predictionDiagnostic == null
                ? Double.NaN : predictionDiagnostic.modelMaxAbsoluteError();
        boolean replayConsistent = predictionDiagnostic != null
                && predictionDiagnostic.effectiveMapModelConsistent();
        String counterEvidence = predictionDiagnostic == null
                ? "Prediction counters: unavailable" : predictionDiagnostic.counterEvidenceText();

        BlendDurationAttempt lastAttempt = validAttempts == null || validAttempts.isEmpty()
                ? null : validAttempts.get(validAttempts.size() - 1);
        double lastDuration = lastAttempt == null ? Double.NaN : lastAttempt.duration;
        double lastBaseRpm = lastAttempt == null ? Double.NaN : lastAttempt.baseRpm;
        double lastBaseMap = lastAttempt == null ? Double.NaN : lastAttempt.baseMap;
        double lastBaseTps = lastAttempt == null ? Double.NaN : lastAttempt.baseTps;
        double lastHeldTps = lastAttempt == null ? Double.NaN : lastAttempt.heldTps;
        double lastPhysicalStep = lastAttempt == null ? Double.NaN : lastAttempt.physicalMapStep;
        double lastPredictionGap = lastAttempt == null ? Double.NaN : lastAttempt.predictionGap;
        double lastPhysicalLate = lastAttempt == null ? Double.NaN : lastAttempt.physicalLateMap;
        String lastTrend = lastAttempt == null ? "" : lastAttempt.trend;

        boolean hasFocusBin = Double.isFinite(safeConfig.startRpm);
        int best = groups == null ? 0
                : hasFocusBin ? groups.bestGroupCountForBin(safeConfig.startRpm)
                : groups.minimumBestGroupCount(safeConfig.armedRpmBins());
        double leadingStep = groups == null ? Double.NaN
                : hasFocusBin ? groups.bestGroupMeanStepForBin(safeConfig.startRpm)
                : groups.bestGroupMeanStep();
        int valid = validAttempts == null ? 0 : validAttempts.size();
        String repeatability = repeatability(groups == null
                ? null : hasFocusBin ? groups.bestAttemptsForBin(safeConfig.startRpm)
                : groups.bestAttempts(), safeConfig.targetCount);
        String compareHint = comparabilityHint(groups, valid, best);
        String driverInstruction = driverInstruction(phase, safeConfig,
                leadingStep, latestResult);
        String status = statusText(phase, safeConfig, liveRpm, baseTps,
                liveTps, liveMap, physicalBaseline, physicalStep,
                observationSeconds, best, repeatability);
        String detail = "Engineering checks\n" + safe(checkText)
                + "\n\nCurrent method state\n" + safe(instruction)
                + "\n\nPhysical response authority\n"
                + (physical == null ? "Physical response not armed." : physical.evidenceText())
                + "\n\nPrediction diagnostics only\n"
                + "Latest timer-reset target: " + f1(target) + " kPa"
                + " | target-anchor gap: " + f1(predictionGap) + " kPa"
                + "\n" + (predictionDiagnostic == null
                    ? "Current-tune replay unavailable." : predictionDiagnostic.modelEvidenceText())
                + "\n" + counterEvidence
                + "\n\nArmed RPM-bin progress\n"
                + (groups == null ? "No valid event groups yet."
                    : groups.binProgress(safeConfig.armedRpmBins(), safeConfig.targetCount))
                + "\n\nComparability\n"
                + (groups == null ? "No valid event groups yet." : groups.summary())
                + "\n\nNumerical Blend Duration Apply remains intentionally withheld; this stage validates prediction-independent physical timing and diagnostic firmware replay separately.";

        BlendDurationFocusModel built = new BlendDurationFocusModel(safeState, phase,
                driverInstruction, status, latestResult, detail,
                safeConfig.startRpm, liveRpm, rpmTolerance,
                baseTps, liveTps, safeConfig.desiredTpsStep,
                safeConfig.usableStepLow(), safeConfig.usableStepHigh(),
                liveMap, liveFallbackMap, liveEffectiveMap,
                physicalBaseline, physicalLate, physicalStep, lowMap, highMap,
                responseSeconds, observationSeconds, responseComplete, bounded,
                target, predictionGap,
                safeConfig.blendDurationAt(workingBlendContextRpm(safeConfig)),
                plateauAcquired, replaySamples,
                replayMean, replayMax, replayConsistent, counterEvidence,
                lastDuration, lastBaseRpm, lastBaseMap, lastBaseTps,
                lastHeldTps, lastPhysicalStep, lastPredictionGap,
                lastPhysicalLate, lastTrend,
                best, safeConfig.targetCount, valid, excluded, returned,
                repeatability, compareHint, currentTrace, previousAcceptedTrace);

        if (runtimeWorker && latest != null) {
            synchronized (RUNTIME_CACHE_LOCK) {
                runtimeCachedModel = built;
                runtimeCachedNano = runtimeNow;
                runtimeCachedState = safeState;
                runtimeCachedConfig = safeConfig;
                runtimeCachedGroups = groups;
                runtimeCachedPlateau = plateauAcquired;
                runtimeBuilds++;
            }
        }
        return built;
    }

    /** Compatibility overload for older tests that project diagnostic catch-up only. */
    static BlendDurationFocusModel build(GuidedCaptureState state,
                                         BlendDurationCaptureConfig config,
                                         LiveSample latest,
                                         RoadBaselineTracker.Baseline rollingBaseline,
                                         RoadBaselineTracker.Baseline frozenBaseline,
                                         LiveSample holdAnchor,
                                         boolean plateauAcquired,
                                         MapCatchupMeasurement catchup,
                                         BlendDurationComparabilityGroups groups,
                                         List<BlendDurationAttempt> validAttempts,
                                         int excluded,
                                         int returned,
                                         String instruction,
                                         String checkText,
                                         String latestResult,
                                         BlendDurationFocusTrace currentTrace,
                                         BlendDurationFocusTrace previousAcceptedTrace) {
        return build(state, config, latest, rollingBaseline, frozenBaseline,
                holdAnchor, plateauAcquired, null, catchup, groups, validAttempts,
                excluded, returned, instruction, checkText, latestResult,
                currentTrace, previousAcceptedTrace);
    }

    static BlendDurationFocusModel setup() {
        return new BlendDurationFocusModel(GuidedCaptureState.IDLE, DriverPhase.SETUP,
                "READ WORKING TUNE — SELECT 1–4 BLEND DURATION RPM BINS",
                "No Blend Duration capture is active.", "", "",
                Double.NaN, Double.NaN, RoadBaselineTracker.RPM_ACQUIRE_TOLERANCE,
                Double.NaN, Double.NaN, 20.0,
                PedalPlateauDetector.MIN_USABLE_STEP,
                PedalPlateauDetector.MAX_USABLE_STEP,
                Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                false, false,
                Double.NaN, Double.NaN, Double.NaN,
                false, 0, Double.NaN, Double.NaN, false,
                "Prediction counters: unavailable",
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, "",
                0, 5, 0, 0, 0, "WAITING", "No comparable events yet.",
                BlendDurationFocusTrace.empty(), BlendDurationFocusTrace.empty());
    }

    private static boolean isRuntimeWorker() {
        return "AE-Tuner-Guided-worker".equals(Thread.currentThread().getName());
    }

    static void resetRuntimePresentationCacheForTest() {
        synchronized (RUNTIME_CACHE_LOCK) {
            runtimeCachedModel = null;
            runtimeCachedNano = 0L;
            runtimeCachedState = null;
            runtimeCachedConfig = null;
            runtimeCachedGroups = null;
            runtimeCachedPlateau = false;
            runtimeBuilds = 0L;
            runtimeCacheHits = 0L;
        }
    }

    static long runtimePresentationBuildsForTest() {
        synchronized (RUNTIME_CACHE_LOCK) { return runtimeBuilds; }
    }

    static long runtimePresentationCacheHitsForTest() {
        synchronized (RUNTIME_CACHE_LOCK) { return runtimeCacheHits; }
    }

    private static DriverPhase phase(GuidedCaptureState state, boolean plateau) {
        switch (state) {
            case PAUSED: return DriverPhase.PAUSED;
            case COMPLETE: return DriverPhase.COMPLETE;
            case ACCEPTED:
            case WARNING:
            case EXCLUDED:
            case RETURNING: return DriverPhase.RESULT;
            case OPENING_PENDING: return DriverPhase.OPEN_AND_SETTLE;
            case CAPTURING: return plateau ? DriverPhase.HOLD_FOR_MAP : DriverPhase.OPEN_AND_SETTLE;
            case SETTLING:
            case RECOVERING: return DriverPhase.GET_STEADY;
            case READY: return DriverPhase.OPEN_AND_SETTLE;
            default: return DriverPhase.SETUP;
        }
    }

    private static String driverInstruction(DriverPhase phase,
                                            BlendDurationCaptureConfig config,
                                            double leadingStep,
                                            String latestResult) {
        switch (phase) {
            case GET_STEADY:
                return Double.isFinite(config.startRpm)
                        ? "GET STEADY — AUTO CANDIDATE " + f0(config.startRpm)
                            + " RPM — HOLD TO LATCH"
                        : "GET STEADY — HOLD NEAR ONE ARMED BIN: "
                            + config.armedRpmText() + " RPM";
            case OPEN_AND_SETTLE:
                if (Double.isFinite(leadingStep)) {
                    return "OPEN ONCE NEAR +" + f1(leadingStep)
                            + " TPS POINTS — MATCH THE LEADING GROUP, THEN HOLD";
                }
                return "OPEN ONCE SMOOTHLY — THEN STOP MOVING YOUR FOOT";
            case HOLD_FOR_MAP:
                return "HOLD STILL — MEASURING PHYSICAL MAP RESPONSE";
            case RESULT:
                return latestResult != null && latestResult.startsWith("EVENT EXCLUDED")
                        ? "EVENT REJECTED — RETURN TO NORMAL THROTTLE AND REPEAT"
                        : "EVENT STORED — RETURN TO NORMAL THROTTLE";
            case COMPLETE:
                return "SERIES COMPLETE — REVIEW PHYSICAL REPEATABILITY AND PREDICTION DIAGNOSTICS";
            case PAUSED:
                return "CAPTURE PAUSED";
            default:
                return "READ WORKING TUNE — SELECT 1–4 BLEND DURATION RPM BINS";
        }
    }

    private static String statusText(DriverPhase phase,
                                     BlendDurationCaptureConfig config,
                                     double rpm,
                                     double baselineTps,
                                     double liveTps,
                                     double map,
                                     double physicalBaselineMap,
                                     double physicalMapStep,
                                     double observationSeconds,
                                     int bestCount,
                                     String repeatability) {
        StringBuilder out = new StringBuilder();
        if (config.armedRpmBinCount() == 1) {
            out.append("Matching events ").append(bestCount).append('/').append(config.targetCount);
        } else {
            out.append("Bin progress ").append(bestCount).append('/').append(config.targetCount);
        }
        out.append("  |  Repeatability ").append(repeatability)
                .append("  |  Armed bins ").append(config.armedRpmText());
        if (Double.isFinite(rpm)) {
            out.append("  |  RPM ").append(f0(rpm));
            if (Double.isFinite(config.startRpm)) {
                out.append(phase == DriverPhase.GET_STEADY
                        ? " | auto candidate " : " | LATCHED ")
                        .append(f0(config.startRpm));
            }
            if (phase == DriverPhase.OPEN_AND_SETTLE || phase == DriverPhase.HOLD_FOR_MAP) {
                out.append(" (no post-start ceiling)");
            }
        }
        if ((phase == DriverPhase.OPEN_AND_SETTLE || phase == DriverPhase.HOLD_FOR_MAP)
                && Double.isFinite(baselineTps) && Double.isFinite(liveTps)) {
            out.append("  |  TPS step +").append(f1(liveTps - baselineTps));
        }
        if (phase == DriverPhase.OPEN_AND_SETTLE) {
            out.append("  |  usable +")
                    .append(f0(PedalPlateauDetector.MIN_USABLE_STEP))
                    .append("…+").append(f0(PedalPlateauDetector.MAX_USABLE_STEP))
                    .append(" TPS; comparable group spread ≤")
                    .append(f0(BlendDurationComparabilityGroups.TPS_STEP_LIMIT));
        }
        if (phase == DriverPhase.HOLD_FOR_MAP && Double.isFinite(map)) {
            out.append("  |  real MAP ").append(f1(map)).append(" kPa");
            if (Double.isFinite(physicalBaselineMap)) {
                out.append(" | baseline ").append(f1(physicalBaselineMap));
            }
            if (Double.isFinite(physicalMapStep)) {
                out.append(" | observed step +").append(f1(physicalMapStep));
            }
            if (Double.isFinite(observationSeconds)) {
                out.append(" | observing ").append(f1(observationSeconds)).append(" s");
            }
        }
        return out.toString();
    }

    private static String repeatability(List<BlendDurationAttempt> attempts, int targetCount) {
        if (attempts == null || attempts.isEmpty()) return "WAITING";
        if (attempts.size() < 2) return "BUILDING";
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double sum = 0.0;
        int n = 0;
        for (BlendDurationAttempt attempt : attempts) {
            if (attempt == null || !Double.isFinite(attempt.duration)) continue;
            min = Math.min(min, attempt.duration);
            max = Math.max(max, attempt.duration);
            sum += attempt.duration;
            n++;
        }
        if (n < 2) return "BUILDING";
        double mean = sum / n;
        double width = max - min;
        double ratio = mean > 0.0 ? width / mean : Double.POSITIVE_INFINITY;
        if (n >= targetCount && ratio <= 0.20) return "STRONG";
        if (ratio <= 0.35) return "GOOD";
        return "SPREAD OPEN";
    }

    private static String comparabilityHint(BlendDurationComparabilityGroups groups,
                                             int valid, int best) {
        if (valid <= 0 || groups == null) {
            return "No comparable events yet. The first valid opening establishes the natural TPS/physical-MAP response reference.";
        }
        if (groups.groupCount() <= 1) {
            return "Recent valid events are physically comparable; repeat near +"
                    + f1(groups.bestGroupMeanStep())
                    + " TPS under similar road/load conditions to build the same five-event set.";
        }
        int outside = Math.max(0, valid - best);
        return outside == 0
                ? "Recent valid events are physically comparable."
                : outside + " valid event" + (outside == 1 ? " was" : "s were")
                    + " retained separately because RPM/load/TPS-step/physical-MAP-step/gear conditions differed. Match the same road conditions for the next repetition.";
    }

    private static double workingBlendContextRpm(BlendDurationCaptureConfig config) {
        if (config == null) return Double.NaN;
        if (Double.isFinite(config.startRpm)) return config.startRpm;
        double[] armed = config.armedRpmBins();
        return armed.length == 1 ? armed[0] : Double.NaN;
    }

    private static double value(LiveSample sample, ChannelRole role) {
        return sample == null ? Double.NaN : sample.get(role);
    }

    private static String safe(String text) { return text == null ? "" : text; }
    private static String f0(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.0f", value) : "n/a";
    }
    private static String f1(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.1f", value) : "n/a";
    }
}
