package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

public final class DecelHistoricalThresholdDecisionRegressionTest {
    private static final double[] RPM_BINS =
            new double[]{1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500};
    private static final double EXPECTED_BOUNDARY_TARGET = 0.400;

    private DecelHistoricalThresholdDecisionRegressionTest() { }

    public static void main(String[] args) {
        validatedWindowsProduceClassificationBoundaryTargets();
        boundaryTargetIsIndependentOfCurrentAndRangeCenter();
        manualLogShapedEvidenceReproduces450_300_250_200();
        matchingBoundaryTargetKeepsCurrent();
        zeroThresholdNeverAutoEnables();
        detectorModeMismatchWithholdsDecisionAuthority();
        detectorTimingMismatchWithholdsDecisionAuthority();
        System.out.println("DecelHistoricalThresholdDecisionRegressionTest passed");
    }

    private static void validatedWindowsProduceClassificationBoundaryTargets() {
        AeProjectSnapshot snapshot = snapshot(0.30, RPM_BINS, "Dual stride, newest", 30.0, 0.05);
        DecelHistoricalEvidenceAnalyzer.Result analysis = analyze(snapshot);
        DecelHistoricalThresholdDecision.Result result =
                DecelHistoricalThresholdDecision.evaluate(snapshot, analysis, metadata(30.0, 0.05));

        require(result.compatibility.actionable,
                "matching captured/current detector settings were not accepted as compatible");
        require(result.keepCount == 0 && result.proposeCount == 2 && result.withholdCount == 6,
                "validated bins did not resolve to two classification-boundary proposals");
        requireBoundaryProposal(result, 1500.0, EXPECTED_BOUNDARY_TARGET);
        requireBoundaryProposal(result, 3000.0, EXPECTED_BOUNDARY_TARGET);
        require(binAt(result, 2000.0).outcome == DecelHistoricalThresholdDecision.Outcome.WITHHOLD,
                "unsupported RPM bin did not WITHHOLD");
        require(result.reportText.contains("largest observed Normal Correction")
                        && result.reportText.contains("lowest credible Decel Release")
                        && result.reportText.contains("quantized to 0.05")
                        && !result.reportText.contains("pair candidates"),
                "historical threshold report lost the independent-event classification-boundary semantics");
    }

    private static void boundaryTargetIsIndependentOfCurrentAndRangeCenter() {
        AeProjectSnapshot snapshot = snapshot(0.05, RPM_BINS, "Dual stride, newest", 30.0, 0.05);
        DecelHistoricalEvidenceAnalyzer.Result analysis = analyze(snapshot);
        DecelHistoricalThresholdDecision.Result result =
                DecelHistoricalThresholdDecision.evaluate(snapshot, analysis, metadata(30.0, 0.05));
        DecelHistoricalThresholdDecision.BinDecision decision = binAt(result, 1500.0);

        require(decision.outcome == DecelHistoricalThresholdDecision.Outcome.PROPOSE,
                "validated historical events did not produce a boundary proposal");
        require(near(decision.preview, EXPECTED_BOUNDARY_TARGET),
                "proposal was not the expected classification-boundary target");
        double intervalCenter = Math.round(((decision.safeLow + decision.safeHigh) * 0.5) / 0.001) * 0.001;
        require(!near(decision.preview, intervalCenter),
                "safe-range-center result survived instead of using independent event boundaries");
        require(near(decision.ecuResultValue(), EXPECTED_BOUNDARY_TARGET),
                "single ECU result value does not equal the classification-boundary target");
    }

    private static void manualLogShapedEvidenceReproduces450_300_250_200() {
        double[] rpm = new double[]{800, 1600, 2400, 3200, 4100, 4900, 5700, 6500};
        AeProjectSnapshot snapshot = snapshot(0.90, rpm, "Dual stride, newest", 40.0, 0.06);
        List<DecelHistoricalEvidenceAnalyzer.BinSummary> bins = new ArrayList<DecelHistoricalEvidenceAnalyzer.BinSummary>();
        bins.add(bin(800, 29, 5, 0.084,
                new double[]{0.725, 0.888, 0.940, 0.959, 1.096}, 0.071, 0.711));
        bins.add(bin(1600, 42, 68, 0.167,
                releases(68, 0.368, 0.368, 1.000), 0.132, 0.832));
        bins.add(bin(2400, 33, 45, 0.089,
                releases(45, 0.397, 0.468, 1.000), 0.095, 1.150));
        bins.add(bin(3200, 6, 13, 0.050,
                releases(13, 0.103, 0.234, 1.000), 0.061, 1.525));
        for (int i = 4; i < rpm.length; i++) {
            bins.add(new DecelHistoricalEvidenceAnalyzer.BinSummary(
                    rpm[i], 0, 0, 0, 0, 0,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    false, Collections.<DecelHistoricalEvidenceAnalyzer.Event>emptyList()));
        }
        DecelHistoricalEvidenceAnalyzer.Result analysis =
                new DecelHistoricalEvidenceAnalyzer.Result(
                        "manual-shaped fixture", 1000, true, 80, 2.0,
                        0.5, 0.7, 2.0, 6.0,
                        241, 110, 131, 0, 4, bins);
        DecelHistoricalThresholdDecision.Result result =
                DecelHistoricalThresholdDecision.evaluate(snapshot, analysis, metadata(40.0, 0.06));

        requireBoundaryProposal(result, 800.0, 0.450);
        requireBoundaryProposal(result, 1600.0, 0.300);
        requireBoundaryProposal(result, 2400.0, 0.250);
        requireBoundaryProposal(result, 3200.0, 0.200);
        require(result.reportText.contains("ignored isolated weak releases 1")
                        && result.reportText.contains("gap position 75%"),
                "3200-like isolated weak-release handling was not reported transparently");
    }

    private static DecelHistoricalEvidenceAnalyzer.BinSummary bin(
            double rpm, int normalCount, int releaseCount, double normalMax,
            double[] releasePeaks, double safeLow, double safeHigh) {
        List<DecelHistoricalEvidenceAnalyzer.Event> events =
                new ArrayList<DecelHistoricalEvidenceAnalyzer.Event>();
        for (int i = 0; i < normalCount; i++) {
            double peak = i == normalCount - 1 ? normalMax : Math.max(0.001, normalMax * 0.50);
            events.add(event(DecelHistoricalEvidenceAnalyzer.CLASS_NORMAL_CORRECTION, rpm, peak));
        }
        for (int i = 0; i < releaseCount; i++) {
            events.add(event(DecelHistoricalEvidenceAnalyzer.CLASS_DECEL_RELEASE,
                    rpm, releasePeaks[Math.min(i, releasePeaks.length - 1)]));
        }
        return new DecelHistoricalEvidenceAnalyzer.BinSummary(
                rpm, normalCount, releaseCount, 0, 0, 0,
                normalMax, releasePeaks[0], safeLow, safeHigh, true, events);
    }

    private static double[] releases(int count, double first, double second, double rest) {
        double[] values = new double[count];
        if (count > 0) values[0] = first;
        if (count > 1) values[1] = second;
        for (int i = 2; i < count; i++) values[i] = rest;
        return values;
    }

    private static DecelHistoricalEvidenceAnalyzer.Event event(String eventClass, double rpm, double peak) {
        return new DecelHistoricalEvidenceAnalyzer.Event(
                eventClass, 0.0, 0.0, rpm, peak, 10.0,
                1.0, 1.0, false, false);
    }

    private static void matchingBoundaryTargetKeepsCurrent() {
        AeProjectSnapshot snapshot = snapshot(EXPECTED_BOUNDARY_TARGET,
                RPM_BINS, "Dual stride, newest", 30.0, 0.05);
        DecelHistoricalEvidenceAnalyzer.Result analysis = analyze(snapshot);
        DecelHistoricalThresholdDecision.Result result =
                DecelHistoricalThresholdDecision.evaluate(snapshot, analysis, metadata(30.0, 0.05));
        DecelHistoricalThresholdDecision.BinDecision decision = binAt(result, 1500.0);
        require(decision.outcome == DecelHistoricalThresholdDecision.Outcome.KEEP
                        && near(decision.ecuResultValue(), EXPECTED_BOUNDARY_TARGET),
                "Working Tune equal to classification-boundary target did not KEEP");
    }

    private static void zeroThresholdNeverAutoEnables() {
        AeProjectSnapshot snapshot = snapshot(0.0, RPM_BINS, "Dual stride, newest", 30.0, 0.05);
        DecelHistoricalEvidenceAnalyzer.Result analysis = analyze(snapshot);
        DecelHistoricalThresholdDecision.Result result =
                DecelHistoricalThresholdDecision.evaluate(snapshot, analysis, metadata(30.0, 0.05));
        DecelHistoricalThresholdDecision.BinDecision decision = binAt(result, 1500.0);
        require(decision.outcome == DecelHistoricalThresholdDecision.Outcome.WITHHOLD,
                "firmware-disabled zero threshold was automatically enabled by historical evidence");
        require(Double.isNaN(decision.ecuResultValue()), "WITHHOLD fabricated an ECU result value");
        require(decision.reason.contains("never auto-enables"),
                "zero-threshold withholding did not explain the firmware-disable safety boundary");
    }

    private static void detectorModeMismatchWithholdsDecisionAuthority() {
        AeProjectSnapshot snapshot = snapshot(0.30, RPM_BINS, "Max step, timed", 30.0, 0.05);
        DecelHistoricalEvidenceAnalyzer.Result analysis = analyze(snapshot);
        DecelHistoricalThresholdDecision.Result result =
                DecelHistoricalThresholdDecision.evaluate(snapshot, analysis, metadata(30.0, 0.05));
        require(!result.compatibility.actionable,
                "captured/current detector-mode mismatch retained historical decision authority");
        require(result.keepCount == 0 && result.proposeCount == 0
                        && result.withholdCount == RPM_BINS.length,
                "mode mismatch did not WITHHOLD every historical threshold decision");
    }

    private static void detectorTimingMismatchWithholdsDecisionAuthority() {
        AeProjectSnapshot snapshot = snapshot(0.30, RPM_BINS, "Dual stride, newest", 40.0, 0.05);
        DecelHistoricalEvidenceAnalyzer.Result analysis = analyze(snapshot);
        DecelHistoricalThresholdDecision.Result result =
                DecelHistoricalThresholdDecision.evaluate(snapshot, analysis, metadata(30.0, 0.05));
        require(!result.compatibility.actionable
                        && result.compatibility.status.contains("Delta Window differs"),
                "captured/current detector-timing mismatch did not withhold historical decision authority");
    }

    private static void requireBoundaryProposal(DecelHistoricalThresholdDecision.Result result,
                                                double rpm, double expected) {
        DecelHistoricalThresholdDecision.BinDecision decision = binAt(result, rpm);
        require(decision.outcome == DecelHistoricalThresholdDecision.Outcome.PROPOSE,
                "validated bin did not produce PROPOSE at " + rpm + " RPM");
        require(near(decision.preview, expected),
                "classification-boundary target changed at " + rpm + " RPM: expected "
                        + expected + " but was " + decision.preview);
    }

    private static DecelHistoricalEvidenceAnalyzer.Result analyze(AeProjectSnapshot snapshot) {
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
                DecelHistoricalEvidenceAnalyzer.analyze(snapshot, samples);
        require(result.quietCalibrationLocked && result.separatedBins == 2,
                "decision fixture did not produce two validated historical intervals");
        return result;
    }

    private static MlgEmbeddedTuneMetadata metadata(double windowMs, double lookbackSeconds) {
        String xml = "<msq><page>"
                + "<constant name=\"tpsAeDetectMode\">\"Dual stride, newest\"</constant>"
                + "<constant name=\"tpsAeDeltaWindowMs\">" + windowMs + "</constant>"
                + "<constant name=\"tpsAccelLookback\">" + lookbackSeconds + "</constant>"
                + "</page></msq>";
        return MlgEmbeddedTuneMetadata.parse(xml);
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

    private static AeProjectSnapshot snapshot(double threshold, double[] rpmBins, String mode,
                                              double windowMs, double lookbackSeconds) {
        double[] thresholds = new double[rpmBins.length];
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
                mode, windowMs, lookbackSeconds,
                false, true, 0.35,
                rpmBins, thresholds, 12.0);
    }

    private static DecelHistoricalThresholdDecision.BinDecision binAt(
            DecelHistoricalThresholdDecision.Result result, double rpm) {
        for (DecelHistoricalThresholdDecision.BinDecision bin : result.bins) {
            if (Math.abs(bin.rpm - rpm) < 0.001) return bin;
        }
        throw new AssertionError("Missing decision bin " + rpm);
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 0.000001;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
