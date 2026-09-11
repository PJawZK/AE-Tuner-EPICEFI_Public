package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.EngagementModelOption;
import se.anders.tunerstudio.aetuner.model.LiveSample;

/** Lightweight presentation model for passive TPS Movement / Timing capture. */
public final class EngagementFocusModel {
    private static final long PRESENTATION_INTERVAL_NS = 100000000L;
    private static AeProjectSnapshot cachedSnapshot;
    private static GuidedCaptureState cachedState;
    private static long cachedSampleNano = Long.MIN_VALUE;
    private static long cachedPassiveRevision = Long.MIN_VALUE;
    private static EngagementFocusModel cachedLiveModel;
    private static long presentationBuildCount;

    public final GuidedCaptureState captureState;
    public final EngagementModelOption workingModel;
    public final double rpm;
    public final double tps;
    public final double productionDeltaTps;
    public final double threshold;
    public final double newestPair;
    public final double selectedOutput;
    public final double windowMs;
    public final double windowSamples;
    public final double stride;
    public final double sampleLengthSeconds;
    public final EngagementPassiveCapture.TimingStatus timingStatus;
    public final boolean fastCallback;
    public final boolean fastCallbackAvailable;
    public final boolean expectedModel;
    public final boolean channelsReady;
    public final boolean liveReady;
    public final boolean selectedAboveThreshold;
    public final boolean noiseCalibrationReady;
    public final boolean incidentalThresholdCrossing;
    public final int quietCalibrationSamples;
    public final double quietTpsRateP99;
    public final double intentTpsRateFloor;
    public final double rawTpsRate;
    public final int activityEvents;
    public final int targetEvents;
    public final int observedSamples;
    public final int completeRequiredSamples;

    private EngagementFocusModel(AeProjectSnapshot timingSnapshot,
                             GuidedCaptureState captureState,
                                 EngagementModelOption workingModel,
                                 double rpm, double tps,
                                 double productionDeltaTps, double threshold,
                                 double newestPair, double selectedOutput,
                                 double windowMs, double windowSamples,
                                 double stride, double sampleLengthSeconds,
                                 boolean fastCallback,
                                 boolean fastCallbackAvailable,
                                 boolean expectedModel,
                                 boolean channelsReady,
                                 boolean liveReady,
                                 boolean selectedAboveThreshold,
                                 double rawTpsRate,
                                 int activityEvents, int targetEvents,
                                 int observedSamples, int completeRequiredSamples) {
        this.captureState = captureState == null ? GuidedCaptureState.IDLE : captureState;
        this.workingModel = workingModel;
        this.rpm = rpm;
        this.tps = tps;
        this.productionDeltaTps = productionDeltaTps;
        this.threshold = threshold;
        this.newestPair = newestPair;
        this.selectedOutput = selectedOutput;
        this.windowMs = windowMs;
        this.windowSamples = windowSamples;
        this.stride = stride;
        this.sampleLengthSeconds = sampleLengthSeconds;
        this.timingStatus = EngagementPassiveCapture.timingStatus(timingSnapshot);
        this.fastCallback = fastCallback;
        this.fastCallbackAvailable = fastCallbackAvailable;
        this.expectedModel = expectedModel;
        this.channelsReady = channelsReady;
        this.liveReady = liveReady;
        this.selectedAboveThreshold = selectedAboveThreshold;
        this.noiseCalibrationReady = true;
        this.incidentalThresholdCrossing = false;
        this.quietCalibrationSamples = 0;
        this.quietTpsRateP99 = Double.NaN;
        this.intentTpsRateFloor = EngagementPassiveCapture.snapshot().onsetRateFloor;
        this.rawTpsRate = rawTpsRate;
        this.activityEvents = Math.max(0, activityEvents);
        this.targetEvents = Math.max(1, targetEvents);
        this.observedSamples = Math.max(0, observedSamples);
        this.completeRequiredSamples = Math.max(0, completeRequiredSamples);
    }

    public static EngagementFocusModel setupFromWorkingTune(GuidedCaptureState state) {
        EngagementDetectionWriteSelection.Snapshot settings = EngagementDetectionWriteSelection.snapshot();
        EngagementModelOption model = settings.modelBaselineAvailable
                ? settings.baselineEngagementModel : null;
        return new EngagementFocusModel(null, state, model,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, settings.baselineSampleLengthSeconds,
                settings.baselineFastCallback, settings.fastCallbackBaselineAvailable,
                model == EngagementModelOption.DUAL_STRIDE_NEWEST,
                false, false, false, Double.NaN,
                0, 6, 0, 0);
    }

    public static synchronized EngagementFocusModel build(
            AeProjectSnapshot snapshot, LiveSample sample,
            GuidedCaptureState state, int activityEvents,
            int targetEvents, int observedSamples,
            int completeRequiredSamples) {
        long sampleNano = sample == null ? Long.MIN_VALUE : sample.getNanoTime();
        long passiveRevision = EngagementPassiveCapture.snapshot().revision;
        if (sample != null && cachedLiveModel != null
                && cachedSnapshot == snapshot
                && cachedState == state
                && cachedSampleNano != Long.MIN_VALUE
                && sampleNano > cachedSampleNano
                && sampleNano - cachedSampleNano < PRESENTATION_INTERVAL_NS
                && cachedPassiveRevision == passiveRevision) {
            return cachedLiveModel;
        }

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
        double sampleLength = snapshot == null ? Double.NaN : snapshot.getEngagementSampleLengthSeconds();
        boolean fastAvailable = snapshot != null && snapshot.hasEngagementFastCallback();
        boolean fast = snapshot != null && snapshot.isEngagementFastCallback();
        boolean expected = model == EngagementModelOption.DUAL_STRIDE_NEWEST;
        boolean channels = expected && finite(rpm, tps, threshold);
        double selected = selectedDetectorOutput(snapshot, sample);
        boolean active = channels && sample != null && sample.bool(ChannelRole.AE_ABOVE_THRESHOLD);

        EngagementFocusModel built = new EngagementFocusModel(
                snapshot, state, model, rpm, tps, delta, threshold, newest, selected,
                windowMs, windowSamples, stride, sampleLength,
                fast, fastAvailable, expected, channels, channels, active,
                sample == null ? Double.NaN : sample.getTpsDot(),
                activityEvents, targetEvents, observedSamples,
                completeRequiredSamples);

        cachedSnapshot = snapshot;
        cachedState = state;
        cachedSampleNano = sampleNano;
        cachedPassiveRevision = passiveRevision;
        cachedLiveModel = built;
        presentationBuildCount++;
        return built;
    }

    public static double selectedDetectorOutput(AeProjectSnapshot snapshot, LiveSample sample) {
        if (sample == null) return Double.NaN;
        double production = sample.get(ChannelRole.DELTA_TPS);
        if (Double.isFinite(production)) return production;
        return sample.get(ChannelRole.AE_DELTA_NEWEST_PAIR);
    }

    public double selectedThresholdRatio() {
        return Double.isFinite(productionDeltaTps)
                && Double.isFinite(threshold) && threshold > 0.000001
                ? productionDeltaTps / threshold : Double.NaN;
    }

    public String detectorStatusText() {
        EngagementPassiveCapture.Snapshot passive = EngagementPassiveCapture.snapshot();
        if (!expectedModel) return "SETUP — Dual Stride / Newest is required for passive timing estimation";
        if (!channelsReady) return "WAIT — RPM/TPS/AccelThreshold data incomplete";
        if (passive.complete()) {
            if (timingStatus.deltaResolved) {
                return "TIMING PAIR COMPLETE — Delta Window and Sample Length resolved";
            }
            return timingStatus.sampleResolved
                    ? "DELTA WINDOW AMBIGUOUS — Sample Length capacity already assessed"
                    : "SET COMPLETE — timing evidence still incomplete";
        }
        if (passive.moving) return "CAPTURING — let this pedal opening peak naturally";
        if (passive.settling) return "SETTLING — TPS must return and stabilize before re-arm";
        return "READY — " + passive.comparableEvents + "/"
                + passive.targetComparable + " comparable movements";
    }

    public String nextActionText() {
        EngagementPassiveCapture.Snapshot passive = EngagementPassiveCapture.snapshot();
        if (workingModel == null) return "READ WORKING TUNE\nLoad the current TPS Movement / Timing baseline before capture.";
        if (!expectedModel) return "CHECK ECU SETUP\nPassive timing estimation expects Dual Stride / Newest.";
        if (captureState == GuidedCaptureState.COMPLETE || passive.complete()) {
            if (!timingStatus.deltaResolved) {
                String sample = timingStatus.sampleResolved
                        ? "\n2/2 SAMPLE LENGTH — " + fmt1(timingStatus.sampleLengthMs)
                            + " ms — " + (timingStatus.sampleChangeNeeded ? "INCREASE" : "RETAIN")
                        : "\n2/2 SAMPLE LENGTH — WAITING FOR CAPACITY BOUND";
                return "1/2 DELTA WINDOW — AMBIGUOUS\n" + timingStatus.reason
                        + sample + "\nNEXT — " + timingStatus.coaching;
            }
            return "TIMING PAIR COMPLETE\n1/2 Delta Window and 2/2 Sample Length are both resolved from accumulated evidence.";
        }
        if (captureState == GuidedCaptureState.PAUSED) return "PAUSED\nResume when safe.";
        if (captureState == GuidedCaptureState.IDLE) return "START PASSIVE CAPTURE\nThe first usable opening will set a visual TPS reference marker.";
        if (passive.moving) return "LET IT PEAK NATURALLY\nNo exact TPS target and no hold are required.";
        if (passive.settling) return "LET TPS SETTLE\nRelease the pedal and wait for the physical event to re-arm before the next opening.";
        if (!Double.isFinite(passive.referencePeakTps)) {
            return "MAKE ONE COMFORTABLE PEDAL OPENING\nIts peak becomes a visual reference only, not a hard acceptance target.";
        }
        return "REPEAT APPROXIMATELY THE REFERENCE MOVEMENT\nAE Tuner decides comparability from the measured event data.";
    }

    public String maneuverPlanText() {
        return "PASSIVE FOUNDATION 1\n"
                + "1. The first usable opening sets a full-height visual TPS reference marker; later peaks use shorter lower-half markers.\n"
                + "2. The marker is presentation-only. AE Tuner captures each physical opening and learns the repeatable TPS-step cluster from measured data.\n"
                + "3. SETTLING/READY only separates physical events: TPS must return and become quiet before another opening can be accepted. It is not target choreography.\n"
                + "4. No controller writes are performed while driving. Idle/no-load evidence is provisional; representative pre-event operating RPM coverage is required before Apply; VSS is context, not a prerequisite.\n"
                + "5. Sample Length is retained unless it is too short to contain the selected Delta Window history.";
    }

    public String audioPlanText() {
        return "FOUNDATION 1 AUDIO\n"
                + "One optional accepted-event cue confirms that a comparable pedal movement was stored.\n"
                + "SETTLING/READY is visual event-separation status only; there are no exact-target, hold or candidate-transition cues.";
    }

    public String prerequisiteText() {
        EngagementPassiveCapture.Snapshot passive = EngagementPassiveCapture.snapshot();
        StringBuilder text = new StringBuilder();
        text.append("Detector: ").append(workingModel == null ? "unknown" : workingModel.displayName()).append(" (read-only)");
        text.append(" | Sample Length: ").append(Double.isFinite(sampleLengthSeconds)
                ? String.format(java.util.Locale.ROOT, "%.0f ms", sampleLengthSeconds * 1000.0) : "unknown")
                .append(" (history capacity)");
        text.append(" | Fast Callback: ");
        if (!fastCallbackAvailable) text.append("unknown"); else text.append(fastCallback ? "ON" : "OFF");
        text.append(" | passive onset floor: ").append(fmt1(passive.onsetRateFloor)).append(" %TPS/s");
        text.append(" | VSS-road context: ").append(passive.roadComparableEvents)
                .append(" | pre-event RPM span: ").append(fmt1(passive.roadRpmSpan)).append(" RPM");
        text.append(" | capture writes: NONE | Burn: unavailable");
        return text.toString();
    }

    static synchronized void resetPresentationCacheForTest() {
        cachedSnapshot = null;
        cachedState = null;
        cachedSampleNano = Long.MIN_VALUE;
        cachedPassiveRevision = Long.MIN_VALUE;
        cachedLiveModel = null;
        presentationBuildCount = 0L;
    }

    static synchronized long presentationBuildCountForTest() { return presentationBuildCount; }
    private static double value(LiveSample sample, ChannelRole role) { return sample == null ? Double.NaN : sample.get(role); }
    private static boolean finite(double... values) { if (values == null) return false; for (double value : values) if (!Double.isFinite(value)) return false; return true; }
    private static String fmt1(double value) { return Double.isFinite(value) ? String.format(java.util.Locale.ROOT, "%.1f", value) : "n/a"; }
}
