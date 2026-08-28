package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.EngagementModelOption;

/** UI-only requested setting state; this class never writes the ECU. */
public final class EngagementDetectionWriteSelection {
    public static final class Snapshot {
        public final String configurationName;
        public final String engagementModel;
        public final EngagementModelOption baselineEngagementModel;
        public final EngagementModelOption requestedEngagementModel;
        public final double baselineDeltaWindowMs;
        public final double requestedDeltaWindowMs;
        public final double baselineSampleLengthSeconds;
        public final double requestedSampleLengthSeconds;
        public final boolean baselineFastCallback;
        public final boolean requestedFastCallback;
        public final boolean baselineAvailable;
        public final boolean modelBaselineAvailable;
        public final boolean sampleLengthBaselineAvailable;
        public final boolean fastCallbackBaselineAvailable;

        private Snapshot(String configurationName, String engagementModel,
                         EngagementModelOption baselineEngagementModel,
                         EngagementModelOption requestedEngagementModel,
                         double baselineDeltaWindowMs, double requestedDeltaWindowMs,
                         double baselineSampleLengthSeconds,
                         double requestedSampleLengthSeconds,
                         boolean baselineFastCallback, boolean requestedFastCallback,
                         boolean baselineAvailable, boolean modelBaselineAvailable,
                         boolean sampleLengthBaselineAvailable,
                         boolean fastCallbackBaselineAvailable) {
            this.configurationName = configurationName;
            this.engagementModel = engagementModel;
            this.baselineEngagementModel = baselineEngagementModel;
            this.requestedEngagementModel = requestedEngagementModel;
            this.baselineDeltaWindowMs = baselineDeltaWindowMs;
            this.requestedDeltaWindowMs = requestedDeltaWindowMs;
            this.baselineSampleLengthSeconds = baselineSampleLengthSeconds;
            this.requestedSampleLengthSeconds = requestedSampleLengthSeconds;
            this.baselineFastCallback = baselineFastCallback;
            this.requestedFastCallback = requestedFastCallback;
            this.baselineAvailable = baselineAvailable;
            this.modelBaselineAvailable = modelBaselineAvailable;
            this.sampleLengthBaselineAvailable = sampleLengthBaselineAvailable;
            this.fastCallbackBaselineAvailable = fastCallbackBaselineAvailable;
        }

        public boolean hasRequestedModelChange() {
            return modelBaselineAvailable && requestedEngagementModel != null
                    && requestedEngagementModel != baselineEngagementModel;
        }

        public boolean hasRequestedDeltaWindowChange() {
            return baselineAvailable && Double.isFinite(requestedDeltaWindowMs)
                    && Math.abs(requestedDeltaWindowMs - baselineDeltaWindowMs) > 0.000001;
        }

        public boolean hasRequestedSampleLengthChange() {
            return sampleLengthBaselineAvailable && Double.isFinite(requestedSampleLengthSeconds)
                    && Math.abs(requestedSampleLengthSeconds
                    - baselineSampleLengthSeconds) > 0.000001;
        }

        public boolean hasRequestedFastCallbackChange() {
            return fastCallbackBaselineAvailable
                    && requestedFastCallback != baselineFastCallback;
        }

        public boolean hasRequestedChange() {
            return hasRequestedModelChange()
                    || hasRequestedDeltaWindowChange()
                    || hasRequestedSampleLengthChange()
                    || hasRequestedFastCallbackChange();
        }
    }

    private static String configurationName = "";
    private static String engagementModel = "unknown";
    private static EngagementModelOption baselineEngagementModel;
    private static EngagementModelOption requestedEngagementModel;
    private static double baselineDeltaWindowMs = Double.NaN;
    private static double requestedDeltaWindowMs = Double.NaN;
    private static double baselineSampleLengthSeconds = Double.NaN;
    private static double requestedSampleLengthSeconds = Double.NaN;
    private static boolean baselineFastCallback;
    private static Boolean requestedFastCallback;
    private static boolean fastCallbackBaselineAvailable;
    private static AeProjectSnapshot observedSnapshot;

    private EngagementDetectionWriteSelection() { }

    /** A fresh Read Working Tune snapshot resets every temporary operator choice. */
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
        boolean modelChanged = nextModel != baselineEngagementModel;
        boolean deltaChanged = finiteChanged(nextDelta, baselineDeltaWindowMs);
        boolean sampleLengthChanged = finiteChanged(
                nextSampleLength, baselineSampleLengthSeconds);
        boolean fastCallbackChanged = nextFastAvailable != fastCallbackBaselineAvailable
                || (nextFastAvailable && nextFastCallback != baselineFastCallback);

        observedSnapshot = snapshot;
        configurationName = nextConfiguration;
        engagementModel = nextModelText;
        baselineEngagementModel = nextModel;
        baselineDeltaWindowMs = nextDelta;
        baselineSampleLengthSeconds = nextSampleLength;
        baselineFastCallback = nextFastCallback;
        fastCallbackBaselineAvailable = nextFastAvailable;

        if (freshSnapshot || configurationChanged || modelChanged
                || requestedEngagementModel == null) {
            requestedEngagementModel = nextModel;
        }
        if (freshSnapshot || configurationChanged || deltaChanged
                || !Double.isFinite(requestedDeltaWindowMs)) {
            requestedDeltaWindowMs = Double.isFinite(nextDelta) ? nextDelta : Double.NaN;
        }
        if (freshSnapshot || configurationChanged || sampleLengthChanged
                || !Double.isFinite(requestedSampleLengthSeconds)) {
            requestedSampleLengthSeconds = Double.isFinite(nextSampleLength)
                    ? nextSampleLength : Double.NaN;
        }
        if (freshSnapshot || configurationChanged || fastCallbackChanged
                || requestedFastCallback == null) {
            requestedFastCallback = Boolean.valueOf(nextFastCallback);
        }
    }

    public static synchronized void requestEngagementModel(EngagementModelOption value) {
        if (value == null) {
            throw new IllegalArgumentException("Engagement Model request is required");
        }
        requestedEngagementModel = value;
    }

    public static synchronized void requestDeltaWindowMs(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException("Delta Window request must be finite and positive");
        }
        requestedDeltaWindowMs = value;
    }

    public static synchronized void requestSampleLengthSeconds(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException("Sample Length request must be finite and positive");
        }
        requestedSampleLengthSeconds = value;
    }

    public static synchronized void requestFastCallback(boolean value) {
        requestedFastCallback = Boolean.valueOf(value);
    }

    public static synchronized Snapshot snapshot() {
        return new Snapshot(configurationName, engagementModel,
                baselineEngagementModel, requestedEngagementModel,
                baselineDeltaWindowMs, requestedDeltaWindowMs,
                baselineSampleLengthSeconds, requestedSampleLengthSeconds,
                baselineFastCallback,
                requestedFastCallback == null
                        ? baselineFastCallback : requestedFastCallback.booleanValue(),
                Double.isFinite(baselineDeltaWindowMs),
                baselineEngagementModel != null,
                Double.isFinite(baselineSampleLengthSeconds),
                fastCallbackBaselineAvailable);
    }

    static synchronized void resetForTest() {
        configurationName = "";
        engagementModel = "unknown";
        baselineEngagementModel = null;
        requestedEngagementModel = null;
        baselineDeltaWindowMs = Double.NaN;
        requestedDeltaWindowMs = Double.NaN;
        baselineSampleLengthSeconds = Double.NaN;
        requestedSampleLengthSeconds = Double.NaN;
        baselineFastCallback = false;
        requestedFastCallback = null;
        fastCallbackBaselineAvailable = false;
        observedSnapshot = null;
    }

    private static boolean finiteChanged(double a, double b) {
        if (Double.isFinite(a) != Double.isFinite(b)) return true;
        return Double.isFinite(a) && Math.abs(a - b) > 0.000001;
    }
}
