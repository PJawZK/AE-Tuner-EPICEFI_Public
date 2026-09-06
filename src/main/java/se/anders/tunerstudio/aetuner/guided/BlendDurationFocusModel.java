package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.List;
import java.util.Locale;

/**
 * Driver-facing projection of the firmware-faithful Blend Duration capture.
 *
 * This class never decides capture validity and never proposes an ECU value.
 * BlendDurationGuidedSession remains the measurement authority; this model only
 * translates its state into the next physical action and compact progress.
 */
public final class BlendDurationFocusModel {
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
    public final double predictionTarget;
    public final double mapRemaining;
    public final double targetGap;
    public final double currentBlendDuration;

    public final int matchingEvents;
    public final int targetEvents;
    public final int allValidEvents;
    public final int excludedEvents;
    public final int returnedEvents;
    public final String repeatability;
    public final String comparabilityHint;
    public final boolean numericalApplyWithheld;

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
                                    double predictionTarget,
                                    double targetGap,
                                    double currentBlendDuration,
                                    int matchingEvents,
                                    int targetEvents,
                                    int allValidEvents,
                                    int excludedEvents,
                                    int returnedEvents,
                                    String repeatability,
                                    String comparabilityHint) {
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
        this.predictionTarget = predictionTarget;
        this.mapRemaining = Double.isFinite(predictionTarget) && Double.isFinite(liveMap)
                ? Math.max(0.0, predictionTarget - liveMap) : Double.NaN;
        this.targetGap = targetGap;
        this.currentBlendDuration = currentBlendDuration;
        this.matchingEvents = Math.max(0, matchingEvents);
        this.targetEvents = Math.max(1, targetEvents);
        this.allValidEvents = Math.max(0, allValidEvents);
        this.excludedEvents = Math.max(0, excludedEvents);
        this.returnedEvents = Math.max(0, returnedEvents);
        this.repeatability = safe(repeatability);
        this.comparabilityHint = safe(comparabilityHint);
        this.numericalApplyWithheld = true;
    }

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
                                         String latestResult) {
        BlendDurationCaptureConfig safeConfig = config == null
                ? new BlendDurationCaptureConfig(2000.0, 20.0, 5, 0, false)
                : config;
        GuidedCaptureState safeState = state == null ? GuidedCaptureState.IDLE : state;
        DriverPhase phase = phase(safeState, plateauAcquired);

        double liveRpm = value(latest, ChannelRole.RPM);
        double liveTps = value(latest, ChannelRole.TPS);
        double liveMap = value(latest, ChannelRole.MAP);
        RoadBaselineTracker.Baseline base = frozenBaseline != null
                && frozenBaseline.valid() ? frozenBaseline : rollingBaseline;
        double baseTps = base == null ? Double.NaN : base.tps;
        if (!Double.isFinite(baseTps) && holdAnchor != null) {
            double held = holdAnchor.get(ChannelRole.TPS);
            if (Double.isFinite(held)) baseTps = held - safeConfig.desiredTpsStep;
        }

        double rpmTolerance = safeState == GuidedCaptureState.CAPTURING
                || safeState == GuidedCaptureState.OPENING_PENDING
                ? RoadBaselineTracker.RPM_CAPTURE_TOLERANCE
                : RoadBaselineTracker.RPM_ACQUIRE_TOLERANCE;
        double target = catchup == null ? Double.NaN : catchup.finalPredictionTarget();
        double gap = catchup == null ? Double.NaN : catchup.bestGap();
        int best = groups == null ? 0 : groups.bestGroupCount();
        int valid = validAttempts == null ? 0 : validAttempts.size();
        String repeatability = repeatability(groups == null
                ? null : groups.bestAttempts(), safeConfig.targetCount);
        String compareHint = comparabilityHint(groups, valid, best);
        String driverInstruction = driverInstruction(phase, safeConfig, latest,
                baseTps, target, latestResult);
        String status = statusText(phase, safeConfig, liveRpm, baseTps,
                liveTps, liveMap, target, best, repeatability);
        String detail = "Engineering checks\n" + safe(checkText)
                + "\n\nCurrent method state\n" + safe(instruction)
                + "\n\nComparability\n"
                + (groups == null ? "No valid event groups yet." : groups.summary())
                + "\n\nNumerical Blend Duration Apply remains intentionally withheld; this stage validates physical measurement and firmware replay only.";

        return new BlendDurationFocusModel(safeState, phase,
                driverInstruction, status, latestResult, detail,
                safeConfig.startRpm, liveRpm, rpmTolerance,
                baseTps, liveTps, safeConfig.desiredTpsStep,
                safeConfig.targetStepLow(), safeConfig.targetStepHigh(),
                liveMap, target, gap,
                safeConfig.blendDurationAt(safeConfig.startRpm),
                best, safeConfig.targetCount, valid, excluded, returned,
                repeatability, compareHint);
    }

    static BlendDurationFocusModel setup() {
        return new BlendDurationFocusModel(GuidedCaptureState.IDLE, DriverPhase.SETUP,
                "READ WORKING TUNE — SELECT A BLEND DURATION RPM POINT",
                "No Blend Duration capture is active.", "", "",
                Double.NaN, Double.NaN, RoadBaselineTracker.RPM_ACQUIRE_TOLERANCE,
                Double.NaN, Double.NaN, 20.0,
                BlendDurationCaptureConfig.targetStepLow(20.0),
                BlendDurationCaptureConfig.targetStepHigh(20.0),
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                0, 5, 0, 0, 0, "WAITING", "No comparable events yet.");
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
                                            LiveSample latest,
                                            double baselineTps,
                                            double target,
                                            String latestResult) {
        switch (phase) {
            case GET_STEADY:
                return "GET STEADY NEAR " + f0(config.startRpm)
                        + " RPM — KEEP THE PEDAL CALM";
            case OPEN_AND_SETTLE:
                if (Double.isFinite(baselineTps)) {
                    return "OPEN SMOOTHLY BY ABOUT +" + f0(config.desiredTpsStep)
                            + "% TPS — SETTLE BETWEEN +" + f0(config.targetStepLow())
                            + " AND +" + f0(config.targetStepHigh()) + " AND HOLD";
                }
                return "ONE SMOOTH ACCELERATION OPENING — THEN HOLD THE PEDAL STEADY";
            case HOLD_FOR_MAP:
                return Double.isFinite(target)
                        ? "HOLD PEDAL STEADY — WAIT FOR MAP TO REACH " + f1(target) + " kPa"
                        : "HOLD PEDAL STEADY — WAIT FOR MAP CATCH-UP";
            case RESULT:
                return latestResult != null && latestResult.startsWith("EVENT EXCLUDED")
                        ? "EVENT REJECTED — RETURN TO NORMAL THROTTLE AND REPEAT"
                        : "EVENT STORED — RETURN TO NORMAL THROTTLE";
            case COMPLETE:
                return "SERIES COMPLETE — REVIEW REPEATABILITY AND MODEL CHECK";
            case PAUSED:
                return "CAPTURE PAUSED";
            default:
                return "READ WORKING TUNE — SELECT A BLEND DURATION RPM POINT";
        }
    }

    private static String statusText(DriverPhase phase,
                                     BlendDurationCaptureConfig config,
                                     double rpm,
                                     double baselineTps,
                                     double liveTps,
                                     double map,
                                     double target,
                                     int bestCount,
                                     String repeatability) {
        StringBuilder out = new StringBuilder();
        out.append("Matching events ").append(bestCount).append('/').append(config.targetCount)
                .append("  |  Repeatability ").append(repeatability);
        if (Double.isFinite(rpm)) {
            out.append("  |  RPM ").append(f0(rpm)).append(" / ").append(f0(config.startRpm));
        }
        if ((phase == DriverPhase.OPEN_AND_SETTLE || phase == DriverPhase.HOLD_FOR_MAP)
                && Double.isFinite(baselineTps) && Double.isFinite(liveTps)) {
            out.append("  |  TPS step +").append(f1(liveTps - baselineTps));
        }
        if (phase == DriverPhase.HOLD_FOR_MAP && Double.isFinite(map) && Double.isFinite(target)) {
            out.append("  |  MAP ").append(f1(map)).append(" → ").append(f1(target)).append(" kPa");
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
        if (valid <= 0 || groups == null) return "No comparable events yet.";
        if (groups.groupCount() <= 1) return "Recent valid events are physically comparable.";
        int outside = Math.max(0, valid - best);
        return outside == 0
                ? "Recent valid events are physically comparable."
                : outside + " valid event" + (outside == 1 ? " was" : "s were")
                    + " retained separately because RPM/load/TPS-step/gear conditions differed. Match the same road conditions for the next repetition.";
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
