package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.EngagementModelOption;
import se.anders.tunerstudio.aetuner.model.LiveSample;

/**
 * Read-only live presentation model for Detector Model / Timing Guided Focus.
 *
 * This model owns no tuning authority. It only turns the working-tune detector
 * selection plus live diagnostic channels into driver-facing visual guidance.
 */
public final class EngagementFocusModel {
    public final GuidedCaptureState captureState;
    public final EngagementModelOption workingModel;
    public final double rpm;
    public final double tps;
    public final double productionDeltaTps;
    public final double threshold;
    public final double legacy;
    public final double timed;
    public final double span;
    public final double floor;
    public final double newest;
    public final double selectedOutput;
    public final double windowMs;
    public final double windowSamples;
    public final double stride;
    public final boolean liveReady;
    public final boolean selectedAboveThreshold;
    public final int activityEvents;
    public final int targetEvents;
    public final int observedSamples;
    public final int completeRequiredSamples;

    private EngagementFocusModel(GuidedCaptureState captureState,
                                 EngagementModelOption workingModel,
                                 double rpm,
                                 double tps,
                                 double productionDeltaTps,
                                 double threshold,
                                 double legacy,
                                 double timed,
                                 double span,
                                 double floor,
                                 double newest,
                                 double selectedOutput,
                                 double windowMs,
                                 double windowSamples,
                                 double stride,
                                 boolean liveReady,
                                 boolean selectedAboveThreshold,
                                 int activityEvents,
                                 int targetEvents,
                                 int observedSamples,
                                 int completeRequiredSamples) {
        this.captureState = captureState == null ? GuidedCaptureState.IDLE : captureState;
        this.workingModel = workingModel;
        this.rpm = rpm;
        this.tps = tps;
        this.productionDeltaTps = productionDeltaTps;
        this.threshold = threshold;
        this.legacy = legacy;
        this.timed = timed;
        this.span = span;
        this.floor = floor;
        this.newest = newest;
        this.selectedOutput = selectedOutput;
        this.windowMs = windowMs;
        this.windowSamples = windowSamples;
        this.stride = stride;
        this.liveReady = liveReady;
        this.selectedAboveThreshold = selectedAboveThreshold;
        this.activityEvents = Math.max(0, activityEvents);
        this.targetEvents = Math.max(1, targetEvents);
        this.observedSamples = Math.max(0, observedSamples);
        this.completeRequiredSamples = Math.max(0, completeRequiredSamples);
    }

    /** Setup-only Focus state after Read Working Tune and before capture begins. */
    public static EngagementFocusModel setupFromWorkingTune(GuidedCaptureState state) {
        EngagementDetectionWriteSelection.Snapshot settings =
                EngagementDetectionWriteSelection.snapshot();
        return new EngagementFocusModel(state,
                settings.modelBaselineAvailable ? settings.baselineEngagementModel : null,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                false, false, 0, 5, 0, 0);
    }

    public static EngagementFocusModel build(AeProjectSnapshot snapshot,
                                             LiveSample sample,
                                             GuidedCaptureState state,
                                             int activityEvents,
                                             int targetEvents,
                                             int observedSamples,
                                             int completeRequiredSamples) {
        EngagementModelOption model = snapshot == null
                ? null : EngagementModelOption.fromControllerText(snapshot.getEngagementModel());
        double rpm = value(sample, ChannelRole.RPM);
        double tps = value(sample, ChannelRole.TPS);
        double delta = value(sample, ChannelRole.DELTA_TPS);
        double threshold = value(sample, ChannelRole.ACCEL_THRESHOLD);
        double legacy = value(sample, ChannelRole.AE_DELTA_MAX_STEP);
        double timed = value(sample, ChannelRole.AE_DELTA_TIMED);
        double span = value(sample, ChannelRole.AE_DELTA_SPAN);
        double floor = value(sample, ChannelRole.AE_DELTA_FLOOR);
        double newest = value(sample, ChannelRole.AE_DELTA_NEWEST_PAIR);
        double selected = selectedDetectorOutput(model, legacy, timed, span, floor, newest);
        double windowMs = value(sample, ChannelRole.AE_WINDOW_MS);
        double windowSamples = value(sample, ChannelRole.AE_WINDOW_SAMPLES);
        double stride = value(sample, ChannelRole.AE_DELTA_STRIDE);
        // AE_WINDOW_SAMPLES is useful context, but it is not a REQUIRED channel
        // for this task. Do not freeze the driver coach merely because that
        // supplementary diagnostic is absent on a compatible firmware build.
        boolean ready = model != null
                && finite(rpm, tps, delta, threshold, legacy, timed, span, floor,
                        newest, selected, windowMs, stride);
        boolean active = ready && selected > threshold;
        return new EngagementFocusModel(state, model,
                rpm, tps, delta, threshold,
                legacy, timed, span, floor, newest, selected,
                windowMs, windowSamples, stride,
                ready, active, activityEvents, targetEvents,
                observedSamples, completeRequiredSamples);
    }

    public static double selectedDetectorOutput(AeProjectSnapshot snapshot,
                                                LiveSample sample) {
        EngagementModelOption model = snapshot == null
                ? null : EngagementModelOption.fromControllerText(snapshot.getEngagementModel());
        return selectedDetectorOutput(model,
                value(sample, ChannelRole.AE_DELTA_MAX_STEP),
                value(sample, ChannelRole.AE_DELTA_TIMED),
                value(sample, ChannelRole.AE_DELTA_SPAN),
                value(sample, ChannelRole.AE_DELTA_FLOOR),
                value(sample, ChannelRole.AE_DELTA_NEWEST_PAIR));
    }

    public double selectedThresholdRatio() {
        return Double.isFinite(selectedOutput) && Double.isFinite(threshold)
                && threshold > 0.000001 ? selectedOutput / threshold : Double.NaN;
    }

    public String detectorStatusText() {
        if (!liveReady) return "WAIT — detector diagnostic data incomplete";
        if (selectedAboveThreshold) {
            return "TRIGGERED — selected detector is above AccelThreshold";
        }
        if (Double.isFinite(productionDeltaTps) && productionDeltaTps > 0.0) {
            return "OPENING / BELOW THRESHOLD — movement is present but the selected detector is not triggered";
        }
        return "READY / BELOW THRESHOLD — make the next deliberate opening when safe";
    }

    public String nextActionText() {
        if (workingModel == null) {
            return "READ WORKING TUNE\nLoad the current Detector Model / Timing baseline before testing.";
        }
        if (captureState == GuidedCaptureState.COMPLETE) {
            return "REVIEW THIS SET\nCompare the captured detector traces before changing anything. If testing a setting, change ONE setting, Apply/verify, Read Working Tune, then repeat the same maneuver set.";
        }
        if (captureState == GuidedCaptureState.PAUSED) {
            return "PAUSED\nResume only when you are ready to continue the same comparison set.";
        }
        if (captureState == GuidedCaptureState.IDLE) {
            return "START A BASELINE CAPTURE\nDrive normally first. When safe, use the maneuver sequence below. The live detector/threshold display begins when Start Capture is pressed.";
        }
        if (!liveReady) {
            return "WAIT FOR LIVE DATA\nKeep the ECU connected until TPS, RPM, AccelThreshold and all five detector diagnostics are present.";
        }
        if (selectedAboveThreshold) {
            return "HOLD / OBSERVE\nThe selected detector crossed AccelThreshold. Stop adding pedal briefly and watch that its output falls back below threshold instead of staying stale.";
        }
        return maneuverInstruction(activityEvents);
    }

    public String maneuverPlanText() {
        return "BASELINE / A-B REPEAT SET\n"
                + "1. Normal moderate opening — clean trigger without a false early hit.\n"
                + "2. Quick stab -> brief hold — detector should trigger promptly, then decay below threshold on the hold.\n"
                + "3. Partial lift -> reapply — old history should clear and the reapply should create a fresh trigger.\n"
                + "4. Two or three stacked short stabs — each genuine reapply should be separable without a stale positive tail.\n\n"
                + "After the baseline set: Finish/Review -> change ONE setting -> Apply/verify -> Read Working Tune -> repeat the same maneuvers in similar RPM/load. Do not compare several changed settings at once.";
    }

    public String audioPlanText() {
        return "AUDIO DURING DETECTOR CAPTURE\n"
                + "READY tone: required detector data is present and the selected detector is below threshold.\n"
                + "TARGET tone: the selected detector crosses AccelThreshold.\n"
                + "RETURN tone: the selected detector has cleared below threshold long enough to separate the event.\n"
                + "Series-complete tone: Finish/Review completed the current set.\n"
                + "Audio is guidance only; the recorded channels remain the evidence.";
    }

    private static String maneuverInstruction(int events) {
        if (events <= 0) {
            return "DO THIS NOW — NORMAL OPENING\nMake one ordinary moderate throttle opening when safe. Watch for the selected signal to cross the threshold and then clear.";
        }
        if (events == 1) {
            return "DO THIS NOW — QUICK STAB -> HOLD\nGive one quick opening, then hold the pedal briefly. The important observation is how quickly the detector falls below threshold once pedal movement stops.";
        }
        if (events == 2) {
            return "DO THIS NOW — PARTIAL LIFT -> REAPPLY\nOpen, lift part-way, then reapply. Watch that the old positive history clears and the reapply creates a fresh crossing.";
        }
        if (events == 3) {
            return "DO THIS NOW — STACKED SHORT STABS\nUse two or three short genuine reapplications. Watch for separate crossings rather than one long stale-positive detector state.";
        }
        return "CONTINUE COMPARABLE EVENTS\nRepeat the same maneuver types at similar RPM/load until the set is representative, then Finish/Review. More varied clean events are more useful than chasing the minimum counter.";
    }

    private static double selectedDetectorOutput(EngagementModelOption model,
                                                 double legacy,
                                                 double timed,
                                                 double span,
                                                 double floor,
                                                 double newest) {
        if (model == null) return Double.NaN;
        switch (model) {
            case MAX_STEP_LEGACY: return legacy;
            case MAX_STEP_TIMED: return timed;
            case WINDOW_SPAN: return span;
            case RISE_FROM_FLOOR: return floor;
            case DUAL_STRIDE_NEWEST: return newest;
            default: return Double.NaN;
        }
    }

    private static double value(LiveSample sample, ChannelRole role) {
        return sample == null ? Double.NaN : sample.get(role);
    }

    private static boolean finite(double... values) {
        if (values == null) return false;
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
