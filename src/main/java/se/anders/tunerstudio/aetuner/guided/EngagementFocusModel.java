package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.EngagementModelOption;
import se.anders.tunerstudio.aetuner.model.LiveSample;

/**
 * Read-only live presentation model for AE Foundation TPS Movement / Timing.
 *
 * The normal tuning question is TPS movement -> production detected TPS change
 * -> AccelThreshold. Dual Stride / Newest is expected controller context, not a
 * user-selectable AE Tuner algorithm. Sample Length and Fast Callback are also
 * read-only context here. Delta Window is the one current A/B setting.
 */
public final class EngagementFocusModel {
    public final GuidedCaptureState captureState;
    public final EngagementModelOption workingModel;
    public final double rpm;
    public final double tps;
    public final double productionDeltaTps;
    public final double threshold;
    public final double newestPair;
    /** Compatibility alias: the production detector output being coached. */
    public final double selectedOutput;
    public final double windowMs;
    public final double windowSamples;
    public final double stride;
    public final double sampleLengthSeconds;
    public final boolean fastCallback;
    public final boolean fastCallbackAvailable;
    public final boolean expectedModel;
    public final boolean liveReady;
    public final boolean selectedAboveThreshold;
    public final int activityEvents;
    public final int targetEvents;
    public final int observedSamples;
    public final int completeRequiredSamples;

    private EngagementFocusModel(GuidedCaptureState captureState,
                                 EngagementModelOption workingModel,
                                 double rpm, double tps,
                                 double productionDeltaTps, double threshold,
                                 double newestPair,
                                 double windowMs, double windowSamples, double stride,
                                 double sampleLengthSeconds,
                                 boolean fastCallback, boolean fastCallbackAvailable,
                                 boolean expectedModel, boolean liveReady,
                                 boolean selectedAboveThreshold,
                                 int activityEvents, int targetEvents,
                                 int observedSamples, int completeRequiredSamples) {
        this.captureState = captureState == null ? GuidedCaptureState.IDLE : captureState;
        this.workingModel = workingModel;
        this.rpm = rpm;
        this.tps = tps;
        this.productionDeltaTps = productionDeltaTps;
        this.threshold = threshold;
        this.newestPair = newestPair;
        this.selectedOutput = productionDeltaTps;
        this.windowMs = windowMs;
        this.windowSamples = windowSamples;
        this.stride = stride;
        this.sampleLengthSeconds = sampleLengthSeconds;
        this.fastCallback = fastCallback;
        this.fastCallbackAvailable = fastCallbackAvailable;
        this.expectedModel = expectedModel;
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
        EngagementModelOption model = settings.modelBaselineAvailable
                ? settings.baselineEngagementModel : null;
        return new EngagementFocusModel(state, model,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN,
                settings.baselineSampleLengthSeconds,
                settings.baselineFastCallback, settings.fastCallbackBaselineAvailable,
                model == EngagementModelOption.DUAL_STRIDE_NEWEST,
                false, false, 0, 5, 0, 0);
    }

    public static EngagementFocusModel build(AeProjectSnapshot snapshot,
                                             LiveSample sample,
                                             GuidedCaptureState state,
                                             int activityEvents,
                                             int targetEvents,
                                             int observedSamples,
                                             int completeRequiredSamples) {
        EngagementModelOption model = snapshot == null ? null
                : EngagementModelOption.fromControllerText(snapshot.getEngagementModel());
        double rpm = value(sample, ChannelRole.RPM);
        double tps = value(sample, ChannelRole.TPS);
        double delta = value(sample, ChannelRole.DELTA_TPS);
        double threshold = value(sample, ChannelRole.ACCEL_THRESHOLD);
        double newest = value(sample, ChannelRole.AE_DELTA_NEWEST_PAIR);
        double windowMs = value(sample, ChannelRole.AE_WINDOW_MS);
        double windowSamples = value(sample, ChannelRole.AE_WINDOW_SAMPLES);
        double stride = value(sample, ChannelRole.AE_DELTA_STRIDE);
        double sampleLength = snapshot == null
                ? Double.NaN : snapshot.getEngagementSampleLengthSeconds();
        boolean fastAvailable = snapshot != null && snapshot.hasEngagementFastCallback();
        boolean fast = snapshot != null && snapshot.isEngagementFastCallback();
        boolean expected = model == EngagementModelOption.DUAL_STRIDE_NEWEST;
        boolean ready = expected && finite(rpm, tps, delta, threshold, newest, windowMs, stride);
        boolean active = ready && delta > threshold;
        return new EngagementFocusModel(state, model,
                rpm, tps, delta, threshold, newest,
                windowMs, windowSamples, stride,
                sampleLength, fast, fastAvailable,
                expected, ready, active,
                activityEvents, targetEvents, observedSamples, completeRequiredSamples);
    }

    /** Production TPS movement signal used by the Guided cue state machine. */
    public static double selectedDetectorOutput(AeProjectSnapshot snapshot,
                                                LiveSample sample) {
        return value(sample, ChannelRole.DELTA_TPS);
    }

    public double selectedThresholdRatio() {
        return Double.isFinite(productionDeltaTps) && Double.isFinite(threshold)
                && threshold > 0.000001 ? productionDeltaTps / threshold : Double.NaN;
    }

    public String detectorStatusText() {
        if (!expectedModel) return "SETUP — Dual Stride / Newest is required for this Guided workflow";
        if (!liveReady) return "WAIT — TPS movement / threshold data incomplete";
        if (selectedAboveThreshold) return "TRIGGERED — detected TPS change is above AccelThreshold";
        if (productionDeltaTps > 0.0) {
            return "OPENING / BELOW THRESHOLD — TPS is moving but the event threshold has not been crossed";
        }
        return "READY / BELOW THRESHOLD — make the next deliberate opening when safe";
    }

    public String nextActionText() {
        if (workingModel == null) {
            return "READ WORKING TUNE\nLoad the current TPS Movement / Timing baseline before testing.";
        }
        if (!expectedModel) {
            return "CHECK ECU SETUP\nThis Guided workflow expects Dual Stride / Newest. AE Tuner does not change Engagement Model.";
        }
        if (captureState == GuidedCaptureState.COMPLETE) {
            return "REVIEW THIS SET\nCompare TPS movement, detected change and threshold timing. If testing Delta Window, change only that value, Apply/verify, Read Working Tune, then repeat the same maneuvers.";
        }
        if (captureState == GuidedCaptureState.PAUSED) {
            return "PAUSED\nResume only when you are ready to continue the same comparison set.";
        }
        if (captureState == GuidedCaptureState.IDLE) {
            return "START A BASELINE CAPTURE\nDrive normally first. When safe, follow the maneuver sequence below.";
        }
        if (!liveReady) {
            return "WAIT FOR LIVE DATA\nKeep the ECU connected until TPS, RPM, Fuel: TPS AE change, AccelThreshold and timing diagnostics are present.";
        }
        if (selectedAboveThreshold) {
            return "HOLD / OBSERVE\nThe detected TPS change crossed AccelThreshold. Stop adding pedal briefly and watch that it falls back below threshold when movement stops.";
        }
        return maneuverInstruction(activityEvents);
    }

    public String maneuverPlanText() {
        return "BASELINE / DELTA WINDOW A-B SET\n"
                + "1. Normal moderate opening — observe TPS movement and threshold crossing.\n"
                + "2. Quick stab -> brief hold — detected change should cross promptly, then clear when movement stops.\n"
                + "3. Partial lift -> reapply — the reapply should become a fresh event.\n"
                + "4. Two or three stacked short stabs — genuine reapplications should remain separable.\n\n"
                + "After Review, change only Delta Window if the evidence justifies an A/B test, Apply/verify, Read Working Tune, then repeat the same maneuver set at similar RPM/load.";
    }

    public String audioPlanText() {
        return "AUDIO DURING TPS MOVEMENT CAPTURE\n"
                + "READY: required data is present and detected TPS change is below threshold.\n"
                + "TARGET: detected TPS change crosses AccelThreshold.\n"
                + "RETURN: detected TPS change has cleared below threshold long enough to separate the event.\n"
                + "COMPLETE: Finish/Review completed the current set.\n"
                + "Audio is guidance only; recorded channels remain the evidence.";
    }

    public String prerequisiteText() {
        StringBuilder text = new StringBuilder();
        text.append("Detector: ")
                .append(workingModel == null ? "unknown" : workingModel.displayName())
                .append(" (read-only)");
        text.append(" | Sample Length: ")
                .append(Double.isFinite(sampleLengthSeconds)
                        ? String.format(java.util.Locale.ROOT, "%.3f s", sampleLengthSeconds)
                        : "unknown")
                .append(" (read-only)");
        text.append(" | Fast Callback: ");
        if (!fastCallbackAvailable) text.append("unknown");
        else text.append(fastCallback ? "ON (~200 Hz)" : "OFF — ~200 Hz recommended");
        text.append(" (read-only)");
        return text.toString();
    }

    private static String maneuverInstruction(int events) {
        if (events <= 0) return "DO THIS NOW — NORMAL OPENING\nMake one ordinary moderate throttle opening when safe.";
        if (events == 1) return "DO THIS NOW — QUICK STAB -> HOLD\nGive one quick opening, then hold the pedal briefly.";
        if (events == 2) return "DO THIS NOW — PARTIAL LIFT -> REAPPLY\nOpen, lift part-way, then reapply.";
        if (events == 3) return "DO THIS NOW — STACKED SHORT STABS\nUse two or three short genuine reapplications.";
        return "CONTINUE COMPARABLE EVENTS\nRepeat the same maneuver types at similar RPM/load, then Finish/Review.";
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
