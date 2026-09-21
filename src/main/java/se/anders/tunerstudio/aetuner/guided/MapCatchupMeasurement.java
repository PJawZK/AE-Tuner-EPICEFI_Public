package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;
import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import java.util.List;
import java.util.Locale;

/**
 * Firmware-shaped final-target MAP catch-up and Effective MAP replay for Guided Blend Duration.
 *
 * EPICEFI evaluates Blend Duration from current RPM. While acceleration/TPS
 * movement remains above the firmware detector threshold it performs a fresh
 * MAP-estimate lookup and resets the prediction timer. Once that movement ends,
 * the timer is allowed to run and predicted MAP blends back toward live MAP.
 * This class follows the final timer-reset prediction target rather than the
 * highest fallbackMap value seen during the opening.
 * The physical catch-up measurement is independent of the working Blend Duration
 * curve. Logged Effective MAP may still be replayed against that existing curve
 * as a diagnostic-only firmware/context check; replay agreement has no authority
 * over event validity, warning state, measured duration, or repeatability.
 *
 * Measurement acquisition is deliberately decoupled from pedal-hold validation:
 * prediction-active target/catch-up evidence is buffered as soon as the opening
 * occurs, then becomes eligible only after Guided later proves a valid plateau.
 */
final class MapCatchupMeasurement {
    private static final double EFFECTIVE_MAP_REPLAY_TOLERANCE_KPA = 2.0;
    private static final double MIN_MODEL_SPAN_KPA = 1.0;

    private BlendDurationCaptureConfig config;
    private LiveSample measurementAnchor;
    private LiveSample physicalCatchSample;
    private LiveSample completionSample;
    private double finalTarget = Double.NaN;
    private double bestGap = Double.NaN;
    private double threshold = Double.NaN;
    private boolean catchupArmed;
    private long lastPredictionActiveNano;

    private int modelSamples;
    private int modelViolations;
    private double modelAbsErrorSum;
    private double maxModelError;
    private double predResetStart = Double.NaN;
    private double predResetLast = Double.NaN;
    private double predExpiredStart = Double.NaN;
    private double predExpiredLast = Double.NaN;

    void configure(BlendDurationCaptureConfig next) {
        config = next;
    }

    void reset() {
        measurementAnchor = null;
        physicalCatchSample = null;
        completionSample = null;
        finalTarget = Double.NaN;
        bestGap = Double.NaN;
        threshold = Double.NaN;
        catchupArmed = false;
        lastPredictionActiveNano = 0L;
        modelSamples = 0;
        modelViolations = 0;
        modelAbsErrorSum = 0.0;
        maxModelError = 0.0;
        predResetStart = Double.NaN;
        predResetLast = Double.NaN;
        predExpiredStart = Double.NaN;
        predExpiredLast = Double.NaN;
    }

    void observePredictionGap(LiveSample sample) {
        if (sample == null) return;

        double resetCounter = sample.get(ChannelRole.MAP_PRED_RESET_CNT);
        boolean resetAdvanced = Double.isFinite(resetCounter)
                && Double.isFinite(predResetLast)
                && resetCounter > predResetLast + 0.5;
        boolean movementStillActive = triggered(sample);
        observeCounters(sample);

        if (!sample.bool(ChannelRole.MAP_PRED_ACTIVE)) return;

        lastPredictionActiveNano = sample.getNanoTime();
        double map = sample.get(ChannelRole.MAP);
        double fallback = sample.get(ChannelRole.FALLBACK_MAP);
        if (!Double.isFinite(map) || !Double.isFinite(fallback)) return;

        // The firmware resets its timer while accel/TPS movement remains active,
        // even when the fresh fallback lookup is lower than an earlier lookup.
        // The latest timer reset therefore wins.
        if (measurementAnchor == null || resetAdvanced || movementStillActive) {
            relatch(sample, map, fallback);
        }

        observeEffectiveMapReplay(sample);
    }

    private void relatch(LiveSample sample, double map, double fallback) {
        finalTarget = fallback;
        measurementAnchor = sample;
        bestGap = fallback - map;
        threshold = fallback;
        physicalCatchSample = null;
        completionSample = null;

        modelSamples = 0;
        modelViolations = 0;
        modelAbsErrorSum = 0.0;
        maxModelError = 0.0;
    }

    void beginCatchup(List<LiveSample> attemptSamples, double mapCatchupSeconds) {
        catchupArmed = true;
        physicalCatchSample = null;
        completionSample = null;

        // The predictive transient is often shorter than the 0.20 s pedal
        // plateau proof. Preserve the physical measurement now, but do not let
        // Guided accept it until the caller has independently validated the
        // later pedal hold. Only samples at/after the latest timer-reset target anchor
        // are eligible, so an older intermediate target can never complete it.
        if (attemptSamples == null || measurementAnchor == null
                || !Double.isFinite(threshold)) return;
        for (LiveSample sample : attemptSamples) {
            if (sample == null
                    || sample.getNanoTime() < measurementAnchor.getNanoTime()) {
                continue;
            }
            double map = sample.get(ChannelRole.MAP);
            if (Double.isFinite(map) && map >= threshold) {
                physicalCatchSample = sample;
                break;
            }
        }
    }

    void observeCatchup(LiveSample sample) {
        if (sample == null || !catchupArmed
                || measurementAnchor == null || !Double.isFinite(threshold)
                || sample.getNanoTime() < measurementAnchor.getNanoTime()) {
            return;
        }
        if (physicalCatchSample == null) {
            double map = sample.get(ChannelRole.MAP);
            if (Double.isFinite(map) && map >= threshold) {
                physicalCatchSample = sample;
            }
        }
        // Once the physical catch has happened, keep the most recent sample as
        // the completion/cue sample. This lets Guided enforce its post-plateau
        // hold time without corrupting the earlier physical catch-up duration.
        if (physicalCatchSample != null) {
            completionSample = sample;
        }
    }

    boolean timedOut(LiveSample sample, double mapCatchupSeconds) {
        return physicalCatchSample == null && catchupArmed
                && measurementAnchor != null && sample != null
                && seconds(measurementAnchor.getNanoTime(), sample.getNanoTime())
                > mapCatchupSeconds;
    }

    boolean hasFinalTimerAnchor() {
        return measurementAnchor != null
                && Double.isFinite(finalTarget)
                && Double.isFinite(bestGap);
    }

    LiveSample measurementAnchor() {
        return measurementAnchor;
    }

    /** Most recent post-plateau completion/cue sample after physical catch. */
    LiveSample catchSample() {
        return completionSample;
    }

    /** Physical sample where measured MAP first reached the final target. */
    LiveSample physicalCatchSample() {
        return physicalCatchSample;
    }

    /** Gap at the current/final firmware timer-reset prediction target. */
    double bestGap() {
        return bestGap;
    }

    /** Exact final latched fallbackMap target; no 90-percent threshold. */
    double threshold() {
        return threshold;
    }

    double finalPredictionTarget() {
        return finalTarget;
    }

    double catchupDurationSeconds() {
        return measurementAnchor == null || physicalCatchSample == null
                ? Double.NaN
                : seconds(measurementAnchor.getNanoTime(),
                        physicalCatchSample.getNanoTime());
    }

    boolean effectiveMapModelConsistent() {
        if (modelSamples < 3) return false;
        return modelViolations <= Math.max(1, modelSamples / 10);
    }

    int modelSampleCount() {
        return modelSamples;
    }

    double modelMeanAbsoluteError() {
        return modelSamples == 0 ? Double.NaN : modelAbsErrorSum / modelSamples;
    }

    double modelMaxAbsoluteError() {
        return modelSamples == 0 ? Double.NaN : maxModelError;
    }

    String modelEvidenceText() {
        if (config == null || !config.hasBlendCurve()) {
            return "Current-tune Effective MAP replay diagnostic only: unavailable (working Blend Duration curve not attached).";
        }
        if (modelSamples == 0) {
            return "Current-tune Effective MAP replay diagnostic only: unavailable (no usable prediction-active Effective MAP samples).";
        }
        return "Current-tune Effective MAP replay diagnostic only: "
                + modelSamples + " sample(s), " + modelViolations
                + " >±" + f2(EFFECTIVE_MAP_REPLAY_TOLERANCE_KPA) + " kPa, mean |error| "
                + f2(modelMeanAbsoluteError()) + " kPa, max |error| "
                + f2(modelMaxAbsoluteError()) + " kPa"
                + (effectiveMapModelConsistent() ? " — CONSISTENT" : " — REVIEW");
    }

    String counterEvidenceText() {
        return "Prediction counters: new-cycle " + counterRange(predResetStart, predResetLast)
                + " | expired " + counterRange(predExpiredStart, predExpiredLast);
    }

    private void observeEffectiveMapReplay(LiveSample sample) {
        if (config == null || !config.hasBlendCurve()
                || measurementAnchor == null || !Double.isFinite(finalTarget)) {
            return;
        }
        double map = sample.get(ChannelRole.MAP);
        double effective = sample.get(ChannelRole.EFFECTIVE_MAP);
        double rpm = sample.get(ChannelRole.RPM);
        double duration = config.blendDurationAt(rpm);
        if (!Double.isFinite(map) || !Double.isFinite(effective)
                || !Double.isFinite(duration) || !(duration > 0.0)
                || Math.abs(finalTarget - map) < MIN_MODEL_SPAN_KPA) {
            return;
        }

        double elapsed = seconds(measurementAnchor.getNanoTime(), sample.getNanoTime());
        double blend = elapsed / duration;
        // An output sample that still reports prediction active should normally
        // be inside the timer window. Clamp only for numerical reconstruction;
        // a materially over-duration active sample will still create model error
        // if Effective MAP does not agree with sensor MAP.
        double boundedBlend = Math.max(0.0, Math.min(1.0, blend));
        double expected = finalTarget + (map - finalTarget) * boundedBlend;
        double error = Math.abs(effective - expected);
        modelSamples++;
        modelAbsErrorSum += error;
        maxModelError = Math.max(maxModelError, error);
        if (error > EFFECTIVE_MAP_REPLAY_TOLERANCE_KPA) {
            modelViolations++;
        }
    }

    private static boolean triggered(LiveSample sample) {
        if (sample.bool(ChannelRole.AE_ABOVE_THRESHOLD)) return true;
        double change = sample.get(ChannelRole.SMOOTHED_DELTA_TPS);
        double limit = sample.get(ChannelRole.ACCEL_THRESHOLD);
        return Double.isFinite(change) && Double.isFinite(limit)
                && limit > 0.0 && change > limit;
    }

    private void observeCounters(LiveSample sample) {
        double reset = sample.get(ChannelRole.MAP_PRED_RESET_CNT);
        if (Double.isFinite(reset)) {
            if (!Double.isFinite(predResetStart)) predResetStart = reset;
            predResetLast = reset;
        }
        double expired = sample.get(ChannelRole.MAP_PRED_EVENT_OVER);
        if (Double.isFinite(expired)) {
            if (!Double.isFinite(predExpiredStart)) predExpiredStart = expired;
            predExpiredLast = expired;
        }
    }

    private static String counterRange(double first, double last) {
        if (!Double.isFinite(first) || !Double.isFinite(last)) return "unavailable";
        return String.format(Locale.US, "%.0f->%.0f", first, last);
    }

    private static String f2(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.US, "%.2f", value) : "n/a";
    }

    private static double seconds(long earlier, long later) {
        return Math.max(0.0, (later - earlier) / 1000000000.0);
    }
}
