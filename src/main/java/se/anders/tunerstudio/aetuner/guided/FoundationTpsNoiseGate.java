package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Explicit user-assisted TPS quiet-baseline calibration and intent gate for
 * AE Foundation.
 *
 * The operator starts calibration while the pedal is untouched. Natural
 * vehicle-specific idle/DBW motion is treated as background evidence. The
 * resulting statistics freeze for the full comparable sweep until the user
 * explicitly recalibrates or a new working-tune baseline is loaded.
 */
public final class FoundationTpsNoiseGate {
    public enum CalibrationState { UNCALIBRATED, CALIBRATING, FROZEN }

    private static final int MIN_QUIET_SAMPLES = 40;
    private static final int MAX_QUIET_SAMPLES = 800;
    private static final double MIN_CALIBRATION_SECONDS = 2.0;
    private static final double P95_MULTIPLIER = 3.0;
    private static final double P99_MULTIPLIER = 1.5;
    private static final double TREND_COHERENCE_LIMIT = 0.85;

    private static final List<Double> QUIET_ABS_TPS_RATE = new ArrayList<Double>();
    private static final List<Double> QUIET_TPS = new ArrayList<Double>();
    private static long lastSampleNano = Long.MIN_VALUE;
    private static Evaluation lastEvaluation = Evaluation.unavailable();
    private static double cachedP95 = Double.NaN;
    private static double cachedP99 = Double.NaN;
    private static double cachedIntentFloor = Double.NaN;
    private static CalibrationState calibrationState = CalibrationState.UNCALIBRATED;
    private static double calibrationStartSeconds = Double.NaN;
    private static double calibrationLastSeconds = Double.NaN;
    private static int calibrationRetries;
    private static int trimmedOutliers;
    private static long calibrationRevision;
    private static String calibrationStatus =
            "TPS quiet baseline not calibrated — Start Capture to begin";

    private FoundationTpsNoiseGate() { }

    public static synchronized void reset() {
        clearSamplesLocked();
        lastSampleNano = Long.MIN_VALUE;
        lastEvaluation = Evaluation.unavailable();
        calibrationState = CalibrationState.UNCALIBRATED;
        calibrationRetries = 0;
        trimmedOutliers = 0;
        calibrationRevision = 0L;
        calibrationStatus = "TPS quiet baseline not calibrated — Start Capture to begin";
    }

    /** Begin a fresh operator-declared pedal-untouched calibration window. */
    public static synchronized void beginCalibration() {
        clearSamplesLocked();
        lastSampleNano = Long.MIN_VALUE;
        calibrationState = CalibrationState.CALIBRATING;
        calibrationRetries = 0;
        trimmedOutliers = 0;
        calibrationStatus =
                "CALIBRATING TPS QUIET BASELINE — engine idling, pedal untouched";
        lastEvaluation = Evaluation.unavailable();
    }

    /** Used by capture start: never silently re-trains an already frozen basis. */
    public static synchronized void beginCalibrationIfNeeded() {
        if (calibrationState == CalibrationState.UNCALIBRATED) beginCalibration();
    }

    public static synchronized CalibrationState calibrationState() {
        return calibrationState;
    }

    public static synchronized boolean calibrationFrozen() {
        return calibrationState == CalibrationState.FROZEN;
    }

    public static synchronized Evaluation evaluate(LiveSample sample) {
        if (sample == null) return evaluationForSample(null);
        if (sample.getNanoTime() == lastSampleNano) return lastEvaluation;

        double delta = sample.get(ChannelRole.DELTA_TPS);
        double threshold = sample.get(ChannelRole.ACCEL_THRESHOLD);
        double tpsRate = sample.getTpsDot();
        double tps = sample.get(ChannelRole.TPS);

        if (calibrationState == CalibrationState.CALIBRATING
                && Double.isFinite(tpsRate) && Double.isFinite(tps)
                && Double.isFinite(sample.getSeconds())) {
            collectCalibrationSampleLocked(sample.getSeconds(), tps, tpsRate);
        }

        boolean calibrated = calibrationState == CalibrationState.FROZEN;
        boolean crossing = Double.isFinite(delta) && Double.isFinite(threshold)
                && threshold > 0.000001 && delta > threshold;
        boolean intentQualified = calibrated && crossing && Double.isFinite(tpsRate)
                && Double.isFinite(cachedIntentFloor)
                && tpsRate > cachedIntentFloor;
        boolean incidentalCrossing = calibrated && crossing && !intentQualified;

        lastSampleNano = sample.getNanoTime();
        lastEvaluation = new Evaluation(
                calibrationState,
                calibrated,
                QUIET_ABS_TPS_RATE.size(),
                calibrationDurationLocked(),
                cachedP95,
                cachedP99,
                calibrated ? cachedIntentFloor : Double.NaN,
                tpsRate,
                delta,
                threshold,
                crossing,
                intentQualified,
                incidentalCrossing,
                calibrationRetries,
                trimmedOutliers,
                calibrationRevision,
                calibrationStatus);
        return lastEvaluation;
    }

    public static int minimumQuietSamples() {
        return MIN_QUIET_SAMPLES;
    }

    public static double minimumCalibrationSeconds() {
        return MIN_CALIBRATION_SECONDS;
    }

    static synchronized boolean calibrationFrozenForTest() {
        return calibrationState == CalibrationState.FROZEN;
    }

    private static void collectCalibrationSampleLocked(double seconds,
                                                       double tps,
                                                       double tpsRate) {
        if (!Double.isFinite(calibrationStartSeconds)) {
            calibrationStartSeconds = seconds;
        } else if (seconds < calibrationLastSeconds) {
            restartCalibrationLocked(seconds, tps, tpsRate,
                    "sample time moved backward");
            return;
        }
        calibrationLastSeconds = seconds;
        QUIET_ABS_TPS_RATE.add(Double.valueOf(Math.abs(tpsRate)));
        QUIET_TPS.add(Double.valueOf(tps));
        while (QUIET_ABS_TPS_RATE.size() > MAX_QUIET_SAMPLES) {
            QUIET_ABS_TPS_RATE.remove(0);
            QUIET_TPS.remove(0);
        }

        recomputeQuietStatisticsLocked();
        double elapsed = calibrationDurationLocked();
        calibrationStatus = "CALIBRATING TPS QUIET BASELINE — "
                + formatSeconds(elapsed) + " s, "
                + QUIET_ABS_TPS_RATE.size() + " samples; pedal untouched";

        if (elapsed < MIN_CALIBRATION_SECONDS
                || QUIET_ABS_TPS_RATE.size() < MIN_QUIET_SAMPLES) {
            return;
        }

        if (sustainedDirectionalTrendLocked()) {
            restartCalibrationLocked(seconds, tps, tpsRate,
                    "sustained one-way TPS movement looked like pedal input");
            return;
        }

        calibrationState = CalibrationState.FROZEN;
        calibrationRevision++;
        calibrationStatus = "TPS QUIET BASELINE FROZEN — "
                + formatSeconds(elapsed) + " s, "
                + QUIET_ABS_TPS_RATE.size() + " samples, p95 "
                + formatRate(cachedP95) + " %/s, p99 "
                + formatRate(cachedP99) + " %/s, intent floor "
                + formatRate(cachedIntentFloor) + " %/s"
                + (trimmedOutliers > 0
                ? ", " + trimmedOutliers + " transient outlier(s) trimmed" : "");
    }

    private static void restartCalibrationLocked(double seconds,
                                                 double tps,
                                                 double tpsRate,
                                                 String reason) {
        calibrationRetries++;
        clearSamplesLocked();
        calibrationState = CalibrationState.CALIBRATING;
        calibrationStartSeconds = seconds;
        calibrationLastSeconds = seconds;
        QUIET_ABS_TPS_RATE.add(Double.valueOf(Math.abs(tpsRate)));
        QUIET_TPS.add(Double.valueOf(tps));
        recomputeQuietStatisticsLocked();
        calibrationStatus = "CALIBRATION RETRY " + calibrationRetries
                + " — " + reason + "; keep pedal untouched";
    }

    private static boolean sustainedDirectionalTrendLocked() {
        if (QUIET_TPS.size() < MIN_QUIET_SAMPLES) return false;
        double first = QUIET_TPS.get(0).doubleValue();
        double last = QUIET_TPS.get(QUIET_TPS.size() - 1).doubleValue();
        double net = Math.abs(last - first);
        double travel = 0.0;
        int changed = 0;
        for (int i = 1; i < QUIET_TPS.size(); i++) {
            double step = Math.abs(QUIET_TPS.get(i).doubleValue()
                    - QUIET_TPS.get(i - 1).doubleValue());
            travel += step;
            if (step > 0.000001) changed++;
        }
        if (travel <= 0.000001 || net <= 0.000001) return false;
        int requiredChanged = Math.max(6, QUIET_TPS.size() / 10);
        double coherence = net / travel;
        return changed >= requiredChanged && coherence >= TREND_COHERENCE_LIMIT;
    }

    private static void recomputeQuietStatisticsLocked() {
        if (QUIET_ABS_TPS_RATE.isEmpty()) {
            cachedP95 = Double.NaN;
            cachedP99 = Double.NaN;
            cachedIntentFloor = Double.NaN;
            trimmedOutliers = 0;
            return;
        }

        List<Double> sorted = new ArrayList<Double>(QUIET_ABS_TPS_RATE);
        Collections.sort(sorted);

        double median = percentileSorted(sorted, 0.50);
        double preliminaryP95 = percentileSorted(sorted, 0.95);
        List<Double> deviations = new ArrayList<Double>(sorted.size());
        for (Double value : sorted) {
            deviations.add(Double.valueOf(Math.abs(value.doubleValue() - median)));
        }
        Collections.sort(deviations);
        double mad = percentileSorted(deviations, 0.50);
        double cutoff = Math.max(preliminaryP95 * 3.0, median + mad * 8.0);

        List<Double> robust = new ArrayList<Double>(sorted.size());
        for (Double value : sorted) {
            if (value.doubleValue() <= cutoff || !Double.isFinite(cutoff)) {
                robust.add(value);
            }
        }
        if (robust.isEmpty()) robust.addAll(sorted);
        trimmedOutliers = Math.max(0, sorted.size() - robust.size());

        cachedP95 = percentileSorted(robust, 0.95);
        cachedP99 = percentileSorted(robust, 0.99);
        cachedIntentFloor = Math.max(cachedP95 * P95_MULTIPLIER,
                cachedP99 * P99_MULTIPLIER);
    }

    private static double calibrationDurationLocked() {
        return Double.isFinite(calibrationStartSeconds)
                && Double.isFinite(calibrationLastSeconds)
                ? Math.max(0.0, calibrationLastSeconds - calibrationStartSeconds)
                : 0.0;
    }

    private static void clearSamplesLocked() {
        QUIET_ABS_TPS_RATE.clear();
        QUIET_TPS.clear();
        calibrationStartSeconds = Double.NaN;
        calibrationLastSeconds = Double.NaN;
        cachedP95 = Double.NaN;
        cachedP99 = Double.NaN;
        cachedIntentFloor = Double.NaN;
        trimmedOutliers = 0;
    }

    private static Evaluation evaluationForSample(LiveSample sample) {
        double tpsRate = sample == null ? Double.NaN : sample.getTpsDot();
        double delta = sample == null ? Double.NaN : sample.get(ChannelRole.DELTA_TPS);
        double threshold = sample == null ? Double.NaN : sample.get(ChannelRole.ACCEL_THRESHOLD);
        return new Evaluation(
                calibrationState,
                calibrationState == CalibrationState.FROZEN,
                QUIET_ABS_TPS_RATE.size(),
                calibrationDurationLocked(),
                cachedP95,
                cachedP99,
                calibrationState == CalibrationState.FROZEN
                        ? cachedIntentFloor : Double.NaN,
                tpsRate,
                delta,
                threshold,
                false,
                false,
                false,
                calibrationRetries,
                trimmedOutliers,
                calibrationRevision,
                calibrationStatus);
    }

    private static double percentileSorted(List<Double> sorted, double fraction) {
        if (sorted == null || sorted.isEmpty()) return Double.NaN;
        if (sorted.size() == 1) return sorted.get(0).doubleValue();
        double position = fraction * (sorted.size() - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) return sorted.get(lower).doubleValue();
        double weight = position - lower;
        return sorted.get(lower).doubleValue() * (1.0 - weight)
                + sorted.get(upper).doubleValue() * weight;
    }

    private static String formatSeconds(double value) {
        return Double.isFinite(value)
                ? String.format(java.util.Locale.ROOT, "%.1f", value) : "0.0";
    }

    private static String formatRate(double value) {
        return Double.isFinite(value)
                ? String.format(java.util.Locale.ROOT, "%.2f", value) : "n/a";
    }

    public static final class Evaluation {
        public final CalibrationState calibrationState;
        public final boolean calibrated;
        public final int quietSamples;
        public final double calibrationDurationSeconds;
        public final double quietRateP95;
        public final double quietRateP99;
        public final double intentRateFloor;
        public final double rawTpsRate;
        public final double productionDeltaTps;
        public final double accelThreshold;
        public final boolean ecuThresholdCrossing;
        public final boolean intentQualified;
        public final boolean incidentalCrossing;
        public final int calibrationRetries;
        public final int trimmedOutliers;
        public final long calibrationRevision;
        public final String calibrationStatus;

        private Evaluation(CalibrationState calibrationState,
                           boolean calibrated,
                           int quietSamples,
                           double calibrationDurationSeconds,
                           double quietRateP95,
                           double quietRateP99,
                           double intentRateFloor,
                           double rawTpsRate,
                           double productionDeltaTps,
                           double accelThreshold,
                           boolean ecuThresholdCrossing,
                           boolean intentQualified,
                           boolean incidentalCrossing,
                           int calibrationRetries,
                           int trimmedOutliers,
                           long calibrationRevision,
                           String calibrationStatus) {
            this.calibrationState = calibrationState == null
                    ? CalibrationState.UNCALIBRATED : calibrationState;
            this.calibrated = calibrated;
            this.quietSamples = Math.max(0, quietSamples);
            this.calibrationDurationSeconds = Math.max(0.0, calibrationDurationSeconds);
            this.quietRateP95 = quietRateP95;
            this.quietRateP99 = quietRateP99;
            this.intentRateFloor = intentRateFloor;
            this.rawTpsRate = rawTpsRate;
            this.productionDeltaTps = productionDeltaTps;
            this.accelThreshold = accelThreshold;
            this.ecuThresholdCrossing = ecuThresholdCrossing;
            this.intentQualified = intentQualified;
            this.incidentalCrossing = incidentalCrossing;
            this.calibrationRetries = Math.max(0, calibrationRetries);
            this.trimmedOutliers = Math.max(0, trimmedOutliers);
            this.calibrationRevision = Math.max(0L, calibrationRevision);
            this.calibrationStatus = calibrationStatus == null ? "" : calibrationStatus;
        }

        static Evaluation unavailable() {
            return new Evaluation(
                    CalibrationState.UNCALIBRATED,
                    false, 0, 0.0,
                    Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN,
                    false, false, false,
                    0, 0, 0L,
                    "TPS quiet baseline unavailable");
        }

        public double coachedDetectorOutput() {
            if (!calibrated || !Double.isFinite(productionDeltaTps)
                    || !Double.isFinite(accelThreshold)) return Double.NaN;
            if (incidentalCrossing) return accelThreshold;
            return productionDeltaTps;
        }
    }
}
