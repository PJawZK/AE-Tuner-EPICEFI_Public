package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class DecelHistoricalEvidenceAnalyzerRegressionTest {
    private static final double[] RPM_BINS =
            new double[]{1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500};

    private DecelHistoricalEvidenceAnalyzerRegressionTest() { }

    public static void main(String[] args) {
        historicalReplayAutomaticallySeparatesObviousEventClasses();
        ambiguousMiddleIsNeverForcedIntoEitherClass();
        currentThresholdIsReplayOnlyAndDoesNotOwnClassification();
        insufficientQuietEvidenceWithholdsEventAnalysis();
        // These are part of the same historical-Decel evidence contract and are
        // invoked here so the permanent Guided method regression runner executes
        // them even though they remain focused standalone tests as well.
        DecelSignalSamplesRegressionTest.main(new String[0]);
        MlgEmbeddedTuneMetadataRegressionTest.main(new String[0]);
        DecelHistoricalStateReplayRegressionTest.main(new String[0]);
        DecelHistoricalThresholdDecisionRegressionTest.main(new String[0]);
        System.out.println("DecelHistoricalEvidenceAnalyzerRegressionTest passed");
    }

    private static void historicalReplayAutomaticallySeparatesObviousEventClasses() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        double seconds = appendQuiet(samples, 0.0, 1500.0, 90);
        for (int i = 0; i < 3; i++) {
            seconds = appendCorrection(samples, seconds, 1500.0, i);
            seconds = appendCorrection(samples, seconds, 3000.0, i);
        }
        for (int i = 0; i < 3; i++) {
            seconds = appendRelease(samples, seconds, 1500.0, i);
            seconds = appendRelease(samples, seconds, 3000.0, i);
        }

        DecelHistoricalEvidenceAnalyzer.Result result =
                DecelHistoricalEvidenceAnalyzer.analyze(snapshot(0.30), samples);
        require(result.quietCalibrationLocked, "quiet baseline did not lock");
        require(result.normalCorrections == 6, "normal corrections were not classified automatically");
        require(result.decelReleases == 6, "clear releases were not classified automatically");
        require(result.ambiguousEvents == 0, "obvious historical events became ambiguous");
        require(result.separatedBins == 2, "two populated RPM bins did not validate separation");
        require(result.reportText.contains("READ ONLY")
                        && result.reportText.contains("not on the current Decel threshold")
                        && result.reportText.contains("creates no ProposalWritePlan"),
                "historical replay report lost its read-only/non-circular boundary");
    }

    private static void ambiguousMiddleIsNeverForcedIntoEitherClass() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        double seconds = appendQuiet(samples, 0.0, 2000.0, 90);
        samples.add(sample(seconds, 2000.0, 20.0, 0.01, 0.2, false));
        seconds += 0.25;
        // A meaningful falling episode, but neither clearly slow/small nor clearly fast release.
        samples.add(sample(seconds, 2000.0, 19.5, -0.22, -5.2, false));
        samples.add(sample(seconds + 0.05, 2000.0, 19.1, -0.30, -5.0, false));

        DecelHistoricalEvidenceAnalyzer.Result result =
                DecelHistoricalEvidenceAnalyzer.analyze(snapshot(0.30), samples);
        require(result.movementEvents == 1, "ambiguous fixture did not form one falling event");
        require(result.normalCorrections == 0 && result.decelReleases == 0,
                "ambiguous event was forced into a labelled evidence class");
        require(result.ambiguousEvents == 1, "ambiguous event was not retained explicitly");
    }

    private static void currentThresholdIsReplayOnlyAndDoesNotOwnClassification() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        double seconds = appendQuiet(samples, 0.0, 1500.0, 90);
        for (int i = 0; i < 3; i++) seconds = appendCorrection(samples, seconds, 1500.0, i);
        for (int i = 0; i < 3; i++) seconds = appendRelease(samples, seconds, 1500.0, i);

        DecelHistoricalEvidenceAnalyzer.Result low =
                DecelHistoricalEvidenceAnalyzer.analyze(snapshot(0.05), samples);
        DecelHistoricalEvidenceAnalyzer.Result high =
                DecelHistoricalEvidenceAnalyzer.analyze(snapshot(2.00), samples);
        require(low.normalCorrections == high.normalCorrections
                        && low.decelReleases == high.decelReleases
                        && low.ambiguousEvents == high.ambiguousEvents,
                "changing current threshold changed historical event classification");
        DecelHistoricalEvidenceAnalyzer.BinSummary lowBin = binAt(low, 1500.0);
        DecelHistoricalEvidenceAnalyzer.BinSummary highBin = binAt(high, 1500.0);
        require(lowBin.normalCrossings > highBin.normalCrossings,
                "threshold replay did not reflect different current threshold values");
        require(highBin.releaseMisses > lowBin.releaseMisses,
                "threshold replay did not expose missed clear releases at a high current threshold");
    }

    private static void insufficientQuietEvidenceWithholdsEventAnalysis() {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        double seconds = appendQuiet(samples, 0.0, 1500.0, 20);
        seconds = appendRelease(samples, seconds, 1500.0, 0);
        DecelHistoricalEvidenceAnalyzer.Result result =
                DecelHistoricalEvidenceAnalyzer.analyze(snapshot(0.30), samples);
        require(!result.quietCalibrationLocked, "short quiet fixture unexpectedly locked baseline");
        require(result.movementEvents == 0 && result.separatedBins == 0,
                "historical analyzer accepted movement before quiet baseline was credible");
    }

    private static double appendQuiet(List<LiveSample> samples, double seconds,
                                      double rpm, int count) {
        for (int i = 0; i < count; i++) {
            samples.add(sample(seconds, rpm, 20.0,
                    i % 2 == 0 ? 0.020 : -0.020,
                    i % 2 == 0 ? 0.20 : -0.20, false));
            seconds += 0.020;
        }
        return seconds + 0.25;
    }

    private static double appendCorrection(List<LiveSample> samples, double seconds,
                                           double rpm, int index) {
        double base = 20.0 + index * 0.3;
        samples.add(sample(seconds, rpm, base, 0.01, 0.2, false));
        seconds += 0.25;
        samples.add(sample(seconds, rpm, base - 0.18,
                -(0.10 + index * 0.004), -3.0, false));
        return seconds + 0.30;
    }

    private static double appendRelease(List<LiveSample> samples, double seconds,
                                        double rpm, int index) {
        double base = 24.0 + index * 0.4;
        samples.add(sample(seconds, rpm, base, 0.01, 0.2, false));
        seconds += 0.25;
        samples.add(sample(seconds, rpm, base - 0.65,
                -(0.42 + index * 0.01), -18.0, true));
        samples.add(sample(seconds + 0.05, rpm, base - 1.20,
                -(0.58 + index * 0.01), -8.0, true));
        return seconds + 0.40;
    }

    private static LiveSample sample(double seconds, double rpm, double tps,
                                     double delta, double tpsRate,
                                     boolean decelActive) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.DELTA_TPS, delta);
        values.put(ChannelRole.TPS_DECEL_ACTIVE, decelActive ? 1.0 : 0.0);
        return new LiveSample((long) (seconds * 1.0e9), seconds, values, tpsRate, 0.0);
    }

    private static AeProjectSnapshot snapshot(double threshold) {
        double[] thresholds = new double[RPM_BINS.length];
        for (int i = 0; i < thresholds.length; i++) thresholds[i] = threshold;
        return new AeProjectSnapshot(
                "Main Controller",
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                0.0, 0.0, new double[0], new double[0],
                false, false, "fixed", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                "Dual stride, newest", 30.0, 0.05,
                false, true, 0.35,
                RPM_BINS, thresholds, 12.0);
    }

    private static DecelHistoricalEvidenceAnalyzer.BinSummary binAt(
            DecelHistoricalEvidenceAnalyzer.Result result, double rpm) {
        for (DecelHistoricalEvidenceAnalyzer.BinSummary bin : result.bins) {
            if (Math.abs(bin.rpm - rpm) < 0.001) return bin;
        }
        throw new AssertionError("Missing bin " + rpm);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
