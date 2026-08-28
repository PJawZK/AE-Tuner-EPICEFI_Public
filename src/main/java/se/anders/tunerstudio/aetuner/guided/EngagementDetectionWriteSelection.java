package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.EngagementModelOption;

/**
 * UI-only state for the one supported TPS Movement / Timing A/B setting:
 * Delta Window. Engagement Model, Sample Length and Fast Callback are retained
 * as read-only working-tune context and are not AE Tuner edit targets.
 */
public final class EngagementDetectionWriteSelection {
    public static final class Snapshot {
        public final String configurationName;
        public final String engagementModel;
        public final EngagementModelOption baselineEngagementModel;
        public final double baselineDeltaWindowMs;
        public final double requestedDeltaWindowMs;
        public final double baselineSampleLengthSeconds;
        public final boolean baselineFastCallback;
        public final boolean baselineAvailable;
        public final boolean modelBaselineAvailable;
        public final boolean sampleLengthBaselineAvailable;
        public final boolean fastCallbackBaselineAvailable;

        private Snapshot(String configurationName, String engagementModel,
                         EngagementModelOption baselineEngagementModel,
                         double baselineDeltaWindowMs, double requestedDeltaWindowMs,
                         double baselineSampleLengthSeconds,
                         boolean baselineFastCallback,
                         boolean baselineAvailable, boolean modelBaselineAvailable,
                         boolean sampleLengthBaselineAvailable,
                         boolean fastCallbackBaselineAvailable) {
            this.configurationName = configurationName;
            this.engagementModel = engagementModel;
            this.baselineEngagementModel = baselineEngagementModel;
            this.baselineDeltaWindowMs = baselineDeltaWindowMs;
            this.requestedDeltaWindowMs = requestedDeltaWindowMs;
            this.baselineSampleLengthSeconds = baselineSampleLengthSeconds;
            this.baselineFastCallback = baselineFastCallback;
            this.baselineAvailable = baselineAvailable;
            this.modelBaselineAvailable = modelBaselineAvailable;
            this.sampleLengthBaselineAvailable = sampleLengthBaselineAvailable;
            this.fastCallbackBaselineAvailable = fastCallbackBaselineAvailable;
        }

        public boolean hasRequestedDeltaWindowChange() {
            return baselineAvailable && Double.isFinite(requestedDeltaWindowMs)
                    && Math.abs(requestedDeltaWindowMs - baselineDeltaWindowMs) > 0.000001;
        }

        public boolean hasRequestedChange() {
            return hasRequestedDeltaWindowChange();
        }
    }

    private static String configurationName = "";
    private static String engagementModel = "unknown";
    private static EngagementModelOption baselineEngagementModel;
    private static double baselineDeltaWindowMs = Double.NaN;
    private static double requestedDeltaWindowMs = Double.NaN;
    private static double baselineSampleLengthSeconds = Double.NaN;
    private static boolean baselineFastCallback;
    private static boolean fastCallbackBaselineAvailable;
    private static AeProjectSnapshot observedSnapshot;

    private EngagementDetectionWriteSelection() { }

    /** A fresh Read Working Tune snapshot resets the temporary Delta Window choice. */
    public static synchronized void observeWorkingTune(AeProjectSnapshot snapshot) {
        if (snapshot == null) return;
        String nextConfiguration = snapshot.getConfigurationName() == null
                ? "" : snapshot.getConfigurationName();
        String nextModelText = snapshot.getEngagementModel() == null
                ? "unknown" : snapshot.getEngagementModel();
        EngagementModelOption nextModel = EngagementModelOption.fromControllerText(nextModelText);
        double nextDelta = snapshot.getEngagementDeltaWindowMs();
        double nextSampleLength = snapshot.getEngagementSampleLengthSeconds();
        boolean nextFastCallback = snapshot.isEngagementFastCallback();
        boolean nextFastAvailable = snapshot.hasEngagementFastCallback();
        boolean freshSnapshot = snapshot != observedSnapshot;
        boolean configurationChanged = !nextConfiguration.equals(configurationName);
        boolean deltaChanged = finiteChanged(nextDelta, baselineDeltaWindowMs);

        observedSnapshot = snapshot;
        configurationName = nextConfiguration;
        engagementModel = nextModelText;
        baselineEngagementModel = nextModel;
        baselineDeltaWindowMs = nextDelta;
        baselineSampleLengthSeconds = nextSampleLength;
        baselineFastCallback = nextFastCallback;
        fastCallbackBaselineAvailable = nextFastAvailable;

        if (freshSnapshot || configurationChanged || deltaChanged
                || !Double.isFinite(requestedDeltaWindowMs)) {
            requestedDeltaWindowMs = Double.isFinite(nextDelta) ? nextDelta : Double.NaN;
        }
    }

    public static synchronized void requestDeltaWindowMs(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException("Delta Window request must be finite and positive");
        }
        requestedDeltaWindowMs = value;
    }

    public static synchronized Snapshot snapshot() {
        return new Snapshot(configurationName, engagementModel,
                baselineEngagementModel,
                baselineDeltaWindowMs, requestedDeltaWindowMs,
                baselineSampleLengthSeconds,
                baselineFastCallback,
                Double.isFinite(baselineDeltaWindowMs),
                baselineEngagementModel != null,
                Double.isFinite(baselineSampleLengthSeconds),
                fastCallbackBaselineAvailable);
    }

    static synchronized void resetForTest() {
        configurationName = "";
        engagementModel = "unknown";
        baselineEngagementModel = null;
        baselineDeltaWindowMs = Double.NaN;
        requestedDeltaWindowMs = Double.NaN;
        baselineSampleLengthSeconds = Double.NaN;
        baselineFastCallback = false;
        fastCallbackBaselineAvailable = false;
        observedSnapshot = null;
    }

    private static boolean finiteChanged(double a, double b) {
        if (Double.isFinite(a) != Double.isFinite(b)) return true;
        return Double.isFinite(a) && Math.abs(a - b) > 0.000001;
    }
}
