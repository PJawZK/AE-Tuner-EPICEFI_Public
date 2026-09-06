package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Immutable presentation snapshot for AE Foundation Threshold / Sensitivity. */
public final class FoundationThresholdFocusModel {
    public static final int QUIET_CALIBRATION_TARGET =
            FoundationThresholdRecommendation.QUIET_CALIBRATION_TARGET;
    public static final double QUIET_CALIBRATION_MIN_SECONDS =
            FoundationThresholdRecommendation.QUIET_CALIBRATION_MIN_SECONDS;

    public static final class Bin {
        public final double rpm;
        public final String regionLabel;
        public final double currentThreshold;
        public final int normalCorrectionEvents;
        public final int accelerationOpeningEvents;
        // Compatibility aliases for older tests/internal callers; user-facing UI uses the physical names above.
        public final int ordinaryEvents;
        public final int intentionalEvents;
        public final int rejectedEvents;
        public final double normalCorrectionP95;
        public final double accelerationOpeningP25;
        public final double ordinaryP95;
        public final double intentionalP25;
        public final double effectiveSeparationLow;
        public final double effectiveSeparationHigh;
        public final double separationGap;
        public final double requiredSeparation;
        public final double staticSeparationLow;
        public final double staticSeparationHigh;
        public final boolean effectiveValidated;
        public final boolean eligible;
        public final double proposedThreshold;
        public final boolean changed;
        public final String requestedClass;
        public final String status;
        public final String lastRejectedReason;
        public final double lastRejectedRate;
        public final double lastRejectedExcursion;
        public final double lastRejectedDelta;
        public final double lastRejectedRatio;

        private Bin(FoundationThresholdRecommendation.BinDecision source) {
            rpm = source.rpm;
            regionLabel = source.regionLabel;
            currentThreshold = source.current;
            normalCorrectionEvents = source.incidentalEvents;
            accelerationOpeningEvents = source.intentEvents;
            ordinaryEvents = normalCorrectionEvents;
            intentionalEvents = accelerationOpeningEvents;
            rejectedEvents = source.rejectedEvents;
            normalCorrectionP95 = source.incidentalP95;
            accelerationOpeningP25 = source.intentP25;
            ordinaryP95 = normalCorrectionP95;
            intentionalP25 = accelerationOpeningP25;
            effectiveSeparationLow = source.effectiveLow;
            effectiveSeparationHigh = source.effectiveHigh;
            separationGap = source.separationGap;
            requiredSeparation = source.requiredSeparation;
            staticSeparationLow = source.low;
            staticSeparationHigh = source.high;
            effectiveValidated = source.effectiveValidated;
            eligible = source.eligible;
            proposedThreshold = source.proposed;
            changed = source.changed;
            requestedClass = source.requestedClass;
            status = source.status;
            lastRejectedReason = source.lastRejectedReason;
            lastRejectedRate = source.lastRejectedRate;
            lastRejectedExcursion = source.lastRejectedExcursion;
            lastRejectedDelta = source.lastRejectedDelta;
            lastRejectedRatio = source.lastRejectedRatio;
        }

        public String requestedClassLabel() {
            if (FoundationThresholdRecommendation.CLASS_NORMAL_CORRECTION.equals(requestedClass)) {
                return "NORMAL CORRECTION";
            }
            if (FoundationThresholdRecommendation.CLASS_ACCELERATION_OPENING.equals(requestedClass)) {
                return "ACCELERATION OPENING";
            }
            return requestedClass == null ? "" : requestedClass.replace('_', ' ');
        }

        public boolean eventCountsComplete() {
            return normalCorrectionEvents >= FoundationThresholdRecommendation.MIN_INCIDENTAL_EVENTS_PER_BIN
                    && accelerationOpeningEvents >= FoundationThresholdRecommendation.MIN_INTENT_EVENTS_PER_BIN;
        }
    }

    public final String captureState;
    public final boolean workingTuneAvailable;
    public final boolean dynamicThresholdEnabled;
    public final boolean averageStaticEnabled;
    public final double smoothingAlpha;
    public final double liveRpm;
    public final double liveTps;
    public final double liveDelta;
    public final double liveThreshold;
    public final double liveRatio;
    public final double liveTpsRate;
    public final int validSamples;
    public final boolean calibrationFrozen;
    public final int quietTarget;
    public final int quietSamples;
    public final double quietDurationSeconds;
    public final double quietRateP95;
    public final double quietRateP99;
    public final double quietRatioP99;
    public final double movementRateFloor;
    public final double intentRateFloor;
    public final int rawCrossingSamples;
    public final int falseTriggerCandidateEvents;
    public final int missedIntentCandidateEvents;
    public final int nearThresholdIntentEvents;
    public final int movementEvents;
    public final int semanticRejectedEvents;
    public final int effectiveValidatedBins;
    public final int eligibleBins;
    public final int changedBins;
    public final boolean recommendationPlanAvailable;
    public final String reviewText;
    public final List<Bin> bins;

    private FoundationThresholdFocusModel(Object state,
            AeProjectSnapshot snapshot, LiveSample latest,
            FoundationThresholdRecommendation.Result result) {
        captureState = stateName(state, "IDLE");
        workingTuneAvailable = snapshot != null;
        dynamicThresholdEnabled = snapshot != null && snapshot.isDynamicThresholdEnabled();
        averageStaticEnabled = snapshot != null && snapshot.isDynamicThresholdAverageStatic();
        smoothingAlpha = snapshot == null ? Double.NaN : snapshot.getDeltaTpsAverageAlpha();
        liveRpm = value(latest, ChannelRole.RPM);
        liveTps = value(latest, ChannelRole.TPS);
        liveDelta = value(latest, ChannelRole.DELTA_TPS);
        liveThreshold = value(latest, ChannelRole.ACCEL_THRESHOLD);
        liveRatio = ratio(liveDelta, liveThreshold);
        liveTpsRate = latest == null ? Double.NaN : latest.getTpsDot();
        FoundationThresholdRecommendation.EvidenceSummary s = result.summary;
        validSamples = s.validSamples;
        calibrationFrozen = s.calibrationFrozen;
        quietTarget = s.calibrationQuietTarget;
        quietSamples = s.quietSamples;
        quietDurationSeconds = s.quietDurationSeconds;
        quietRateP95 = s.quietP95;
        quietRateP99 = s.quietP99;
        quietRatioP99 = s.quietRatioP99;
        movementRateFloor = s.movementRateFloor;
        intentRateFloor = s.intentRateFloor;
        rawCrossingSamples = s.rawCrossingSamples;
        falseTriggerCandidateEvents = s.falseTriggerCandidateEvents;
        missedIntentCandidateEvents = s.missedIntentCandidateEvents;
        nearThresholdIntentEvents = s.nearThresholdIntentEvents;
        movementEvents = s.movementEvents;
        semanticRejectedEvents = s.semanticRejectedEvents;
        effectiveValidatedBins = s.effectiveValidatedBins;
        eligibleBins = result.eligibleBins;
        changedBins = result.changedBins;
        recommendationPlanAvailable = result.plan != null;
        reviewText = result.reviewText;
        List<Bin> copy = new ArrayList<Bin>();
        for (FoundationThresholdRecommendation.BinDecision decision : s.bins) {
            copy.add(new Bin(decision));
        }
        bins = Collections.unmodifiableList(copy);
    }

    private FoundationThresholdFocusModel(FoundationThresholdFocusModel base,
            LiveSample latest, Object state) {
        captureState = stateName(state, base.captureState);
        workingTuneAvailable = base.workingTuneAvailable;
        dynamicThresholdEnabled = base.dynamicThresholdEnabled;
        averageStaticEnabled = base.averageStaticEnabled;
        smoothingAlpha = base.smoothingAlpha;
        liveRpm = latest == null ? base.liveRpm : value(latest, ChannelRole.RPM);
        liveTps = latest == null ? base.liveTps : value(latest, ChannelRole.TPS);
        liveDelta = latest == null ? base.liveDelta : value(latest, ChannelRole.DELTA_TPS);
        liveThreshold = latest == null ? base.liveThreshold : value(latest, ChannelRole.ACCEL_THRESHOLD);
        liveRatio = ratio(liveDelta, liveThreshold);
        liveTpsRate = latest == null ? base.liveTpsRate : latest.getTpsDot();
        validSamples = base.validSamples;
        calibrationFrozen = base.calibrationFrozen;
        quietTarget = base.quietTarget;
        quietSamples = base.quietSamples;
        quietDurationSeconds = base.quietDurationSeconds;
        quietRateP95 = base.quietRateP95;
        quietRateP99 = base.quietRateP99;
        quietRatioP99 = base.quietRatioP99;
        movementRateFloor = base.movementRateFloor;
        intentRateFloor = base.intentRateFloor;
        rawCrossingSamples = base.rawCrossingSamples;
        falseTriggerCandidateEvents = base.falseTriggerCandidateEvents;
        missedIntentCandidateEvents = base.missedIntentCandidateEvents;
        nearThresholdIntentEvents = base.nearThresholdIntentEvents;
        movementEvents = base.movementEvents;
        semanticRejectedEvents = base.semanticRejectedEvents;
        effectiveValidatedBins = base.effectiveValidatedBins;
        eligibleBins = base.eligibleBins;
        changedBins = base.changedBins;
        recommendationPlanAvailable = base.recommendationPlanAvailable;
        reviewText = base.reviewText;
        bins = base.bins;
    }

    public static FoundationThresholdFocusModel build(AeProjectSnapshot snapshot,
            List<LiveSample> evidence, LiveSample latest, Object state) {
        List<LiveSample> safe = evidence == null
                ? Collections.<LiveSample>emptyList() : evidence;
        return new FoundationThresholdFocusModel(state, snapshot, latest,
                FoundationThresholdRecommendation.evaluate(snapshot, safe));
    }

    public FoundationThresholdFocusModel withLive(LiveSample latest, Object state) {
        return new FoundationThresholdFocusModel(this, latest, state);
    }

    public Bin liveBin() {
        if (bins.isEmpty() || !Double.isFinite(liveRpm)) return null;
        for (int i = 0; i < bins.size() - 1; i++) {
            double boundary = bins.get(i).rpm
                    + (bins.get(i + 1).rpm - bins.get(i).rpm) / 2.0;
            if (liveRpm <= boundary) return bins.get(i);
        }
        return bins.get(bins.size() - 1);
    }

    public String driverInstruction() {
        if (!workingTuneAvailable) return "READ WORKING TUNE";
        if ("IDLE".equals(captureState)) return "START CAPTURE — BEGIN WITH A QUIET / STEADY PEDAL";
        if ("PAUSED".equals(captureState)) return "PAUSED — RESUME WHEN THE ROAD / ENGINE STATE IS READY";
        if ("COMPLETE".equals(captureState)) return "REVIEW — CHECK EVENT EVIDENCE, SEPARATION, AND STATIC AUTHORITY BEFORE APPLY";
        if (!calibrationFrozen) {
            return "CALIBRATION — HOLD THE PEDAL STEADY UNTIL 80 SAMPLES + 1.5 CONTINUOUS SECONDS LOCK";
        }
        Bin bin = liveBin();
        if (bin == null) return "DRIVE NORMALLY — BUILD EVIDENCE ACROSS BROAD RPM REGIONS";
        if (bin.effectiveValidated) {
            if (bin.status.contains("static-cell authority blocked")) {
                return "EVENT EVIDENCE VALID — EFFECTIVE SEPARATION LOCKED; STATIC-CELL AUTHORITY BLOCKED";
            }
            return "EVENT EVIDENCE VALID — EFFECTIVE SEPARATION LOCKED; MOVE NATURALLY TO ANOTHER REGION IF WANTED";
        }
        if (FoundationThresholdRecommendation.CLASS_NORMAL_CORRECTION.equals(bin.requestedClass)) {
            return "NORMAL CORRECTION — MAKE A SMALL NATURAL PEDAL ADJUSTMENT WITHOUT TRYING TO ACCELERATE";
        }
        if (FoundationThresholdRecommendation.CLASS_ACCELERATION_OPENING.equals(bin.requestedClass)) {
            return "ACCELERATION OPENING — MAKE A CLEAR NORMAL QUICK OPENING TO ACCELERATE; DO NOT SLOWLY ROLL IN";
        }
        return "FOLLOW THE CURRENT BROAD-RPM GUIDED PHASE";
    }

    public String recommendationStatus() {
        if (!workingTuneAvailable) return "WITHHOLD — working tune not read";
        if (!calibrationFrozen) {
            return "CALIBRATING — quiet baseline needs both 80 samples and 1.5 continuous seconds before maneuvers count";
        }
        if (dynamicThresholdEnabled && !averageStaticEnabled) {
            return effectiveValidatedBins > 0
                    ? "EVENT EVIDENCE VALID — " + effectiveValidatedBins + " effective region(s) locked; static curve has zero runtime authority in Dynamic-only mode"
                    : "WITHHOLD — Dynamic Threshold is ON with averaging OFF; firmware gives the static curve zero runtime authority";
        }
        if (recommendationPlanAvailable) {
            return "READY FOR REVIEW — " + changedBins + " evidence-backed static threshold bin change(s)"
                    + (dynamicThresholdEnabled && averageStaticEnabled ? " using firmware averaged-threshold inversion" : "");
        }
        if (eligibleBins > 0) return "NO CHANGE NEEDED — eligible static bins already sit inside their locked event-backed separation window";
        if (effectiveValidatedBins > 0) return "EVENT EVIDENCE VALID — " + effectiveValidatedBins + " effective region(s) locked; static-only proposal withheld";
        return "WITHHOLD — no broad RPM region has locked two-sided Normal Correction / Acceleration Opening separation yet";
    }

    public String currentTuneText() {
        if (!workingTuneAvailable) return "Working tune not read";
        return "Dynamic threshold " + (dynamicThresholdEnabled ? "ON" : "OFF")
                + " | static/dynamic averaging " + (averageStaticEnabled ? "ON" : "OFF")
                + " | Delta TPS smoothing alpha " + fmt(smoothingAlpha)
                + " | automatic recommendation authority: static tpsAeThresholdValue bins only";
    }

    private static String stateName(Object state, String fallback) {
        if (state == null) return fallback == null ? "IDLE" : fallback;
        String text = String.valueOf(state).trim();
        return text.length() == 0 ? (fallback == null ? "IDLE" : fallback) : text;
    }

    private static double value(LiveSample sample, ChannelRole role) {
        return sample == null ? Double.NaN : sample.get(role);
    }

    private static double ratio(double delta, double threshold) {
        return Double.isFinite(delta) && Double.isFinite(threshold) && threshold > 0.0
                ? delta / threshold : Double.NaN;
    }

    private static String fmt(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%.3f", value) : "n/a";
    }
}
