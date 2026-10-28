package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

/** Permanent regressions for the Test-15 Decel corrections. */
public final class DecelHistoricalTest15RegressionTest {
    private static final double[] RPM =
            new double[]{1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500};

    private DecelHistoricalTest15RegressionTest() { }

    public static void main(String[] args) {
        strongBelowCeilingWeakReleaseProduces300();
        oneNonMaterialRawMismatchRemainsReviewable();
        crossingChangingMismatchStillBlocksReview();
        resultReportIdentityIsNotDuplicated();
        System.out.println("DecelHistoricalTest15RegressionTest passed");
    }

    private static void strongBelowCeilingWeakReleaseProduces300() {
        double[] rpm = new double[]{800, 1600, 2400, 3200, 4100, 4900, 5700, 6500};
        AeProjectSnapshot snapshot = snapshot(0.477, rpm, "Dual stride, newest", 40.0, 0.06);
        List<DecelHistoricalEvidenceAnalyzer.BinSummary> bins =
                new ArrayList<DecelHistoricalEvidenceAnalyzer.BinSummary>();
        bins.add(emptyBin(800));
        bins.add(bin(1600, 228, 63, 0.104,
                releases(63, 0.060, 0.450, 0.532), 0.057, 0.768));
        for (int i = 2; i < rpm.length; i++) bins.add(emptyBin(rpm[i]));

        DecelHistoricalEvidenceAnalyzer.Result analysis =
                new DecelHistoricalEvidenceAnalyzer.Result(
                        "test15 strong below-ceiling weak release", 1000,
                        true, 80, 1.5, 1.218, 1.764, 2.0, 6.089,
                        291, 228, 63, 0, 1, bins);
        DecelHistoricalThresholdDecision.Result result =
                DecelHistoricalThresholdDecision.evaluate(snapshot, analysis,
                        metadata(40.0, 0.06));
        DecelHistoricalThresholdDecision.BinDecision decision = binAt(result, 1600.0);

        require(decision.outcome == DecelHistoricalThresholdDecision.Outcome.PROPOSE,
                "isolated below-ceiling weak Release still WITHHELD the strong 1600-RPM bin");
        require(near(decision.preview, 0.300),
                "strong 1600-RPM evidence did not reproduce the independently assessed 0.300 target");
        require(result.reportText.contains("ignored isolated weak releases 1")
                        && result.reportText.contains("gap position 60%"),
                "strong-evidence weak-release rejection did not retain the normal 60% placement");
    }

    private static void oneNonMaterialRawMismatchRemainsReviewable() {
        AeProjectSnapshot tune = snapshot(0.30, RPM, "Dual stride, newest", 30.0, 0.05);
        Fixture fixture = fixture(tune, Mismatch.NON_MATERIAL);
        DecelHistoricalThresholdProposal.Eligibility eligibility =
                DecelHistoricalThresholdProposal.reviewEligibility(null, tune, fixture.replay);

        require(eligibility.promotable,
                "one low-magnitude raw direct/twin mismatch incorrectly blocked Review");
        require(eligibility.evidence != null
                        && eligibility.evidence.directRawMismatchedSamples == 1
                        && eligibility.evidence.directMaterialMismatchedSamples == 0,
                "raw/material mismatch accounting did not preserve the one diagnostic-only mismatch");
        require(eligibility.reviewText.contains("1 raw mismatch(es)")
                        && eligibility.reviewText.contains("0 proposal-relevant material mismatch(es)")
                        && eligibility.reviewText.contains("diagnostic only"),
                "eligibility report did not explain why the raw mismatch is non-material");
    }

    private static void crossingChangingMismatchStillBlocksReview() {
        AeProjectSnapshot tune = snapshot(0.30, RPM, "Dual stride, newest", 30.0, 0.05);
        Fixture fixture = fixture(tune, Mismatch.MATERIAL_CROSSING);
        DecelHistoricalThresholdProposal.Eligibility eligibility =
                DecelHistoricalThresholdProposal.reviewEligibility(null, tune, fixture.replay);

        require(!eligibility.promotable
                        && eligibility.reviewText.contains("proposal-relevant sample"),
                "a direct/twin mismatch that changes threshold crossing did not block Review");
    }

    private static void resultReportIdentityIsNotDuplicated() {
        AeProjectSnapshot tune = snapshot(0.30, RPM, "Dual stride, newest", 30.0, 0.05);
        Fixture fixture = fixture(tune, Mismatch.NONE);
        DecelHistoricalThresholdProposal.Eligibility eligibility =
                DecelHistoricalThresholdProposal.reviewEligibility(null, tune, fixture.replay);
        DecelHistoricalReplayService.Result replay = new DecelHistoricalReplayService.Result(
                fixture.replay.importResult, fixture.replay.embeddedTune,
                fixture.replay.analysis, fixture.replay.stateReplay,
                fixture.replay.thresholdDecision,
                "AE TUNER REPORT IDENTITY\nPlugin build: test\nDecel decision algorithm: test\n\nfixture");
        DecelHistoricalResultModel model = DecelHistoricalResultModel.build(
                null, tune, replay, eligibility);
        require(count(model.technicalReport, "AE TUNER REPORT IDENTITY") == 1,
                "result layer duplicated a replay-source report identity stamp");
    }

    private enum Mismatch { NONE, NON_MATERIAL, MATERIAL_CROSSING }

    private static final class Fixture {
        final DecelHistoricalReplayService.Result replay;
        Fixture(DecelHistoricalReplayService.Result replay) { this.replay = replay; }
    }

    private static Fixture fixture(AeProjectSnapshot tune, Mismatch mismatch) {
        List<LiveSample> raw = new ArrayList<LiveSample>();
        double t = quiet(raw, 0.0, 1500.0, mismatch);
        for (int i = 0; i < 3; i++) {
            t = correction(raw, t, 1500.0, i, mismatch);
            t = correction(raw, t, 3000.0, i, mismatch);
        }
        for (int i = 0; i < 3; i++) {
            t = release(raw, t, 1500.0, i, mismatch);
            t = release(raw, t, 3000.0, i, mismatch);
        }

        MlgEmbeddedTuneMetadata embedded = metadata(30.0, 0.05);
        List<LiveSample> primary = DecelSignalSamples.asPrimaryDelta(raw, embedded.detectorModeRaw);
        DecelHistoricalEvidenceAnalyzer.Result analysis =
                DecelHistoricalEvidenceAnalyzer.analyze(tune, primary);
        require(analysis.quietCalibrationLocked && analysis.separatedBins == 2,
                "Test-15 proposal fixture did not produce two separated bins");
        DecelHistoricalThresholdDecision.Result decision =
                DecelHistoricalThresholdDecision.evaluate(tune, analysis, embedded);
        DecelHistoricalLogImporter.Result imported = new DecelHistoricalLogImporter.Result(
                raw, "synthetic historical MLG", "fixture",
                raw.size(), raw.size(), 0,
                true, false, false, false, false, true, true);
        return new Fixture(new DecelHistoricalReplayService.Result(
                imported, embedded, analysis, null, decision, "fixture"));
    }

    private static double quiet(List<LiveSample> out, double t, double rpm, Mismatch mismatch) {
        for (int i = 0; i < 90; i++) {
            double twin = i % 2 == 0 ? 0.020 : -0.020;
            double direct = twin;
            if (mismatch == Mismatch.NON_MATERIAL && i == 10) {
                twin = -0.029;
                direct = -0.016;
            }
            out.add(sample(t, rpm, 20.0, twin, direct,
                    i % 2 == 0 ? 0.20 : -0.20, false));
            t += 0.020;
        }
        return t + 0.25;
    }

    private static double correction(List<LiveSample> out, double t, double rpm,
                                     int i, Mismatch mismatch) {
        double base = 20.0 + i * 0.3;
        out.add(sample(t, rpm, base, 0.01, 0.01, 0.2, false));
        t += 0.25;
        double delta = -(0.10 + i * 0.004);
        out.add(sample(t, rpm, base - 0.18, delta, delta, -3.0, false));
        return t + 0.30;
    }

    private static double release(List<LiveSample> out, double t, double rpm,
                                  int i, Mismatch mismatch) {
        double base = 24.0 + i * 0.4;
        out.add(sample(t, rpm, base, 0.01, 0.01, 0.2, false));
        t += 0.25;
        double twin = -(0.42 + i * 0.01);
        double direct = twin;
        if (mismatch == Mismatch.MATERIAL_CROSSING && rpm == 1500.0 && i == 0) {
            direct = -0.01;
        }
        out.add(sample(t, rpm, base - 0.65, twin, direct, -18.0, true));
        double tail = -(0.58 + i * 0.01);
        out.add(sample(t + 0.05, rpm, base - 1.20, tail, tail, -8.0, true));
        return t + 0.40;
    }

    private static LiveSample sample(double seconds, double rpm, double tps,
                                     double twinDelta, double directDelta,
                                     double tpsRate, boolean active) {
        EnumMap<ChannelRole, Double> v = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        v.put(ChannelRole.RPM, rpm);
        v.put(ChannelRole.TPS, tps);
        v.put(ChannelRole.ENGINE_RUNNING, 1.0);
        v.put(ChannelRole.ENGINE_CRANKING, 0.0);
        v.put(ChannelRole.DECEL_DELTA_TPS, directDelta);
        v.put(ChannelRole.AE_DELTA_NEWEST_FALL, twinDelta);
        v.put(ChannelRole.TPS_DECEL_ACTIVE, active ? 1.0 : 0.0);
        return new LiveSample((long)(seconds * 1.0e9), seconds, v, tpsRate, 0.0);
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

    private static DecelHistoricalEvidenceAnalyzer.BinSummary emptyBin(double rpm) {
        return new DecelHistoricalEvidenceAnalyzer.BinSummary(
                rpm, 0, 0, 0, 0, 0,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                false, Collections.<DecelHistoricalEvidenceAnalyzer.Event>emptyList());
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

    private static DecelHistoricalThresholdDecision.BinDecision binAt(
            DecelHistoricalThresholdDecision.Result result, double rpm) {
        for (DecelHistoricalThresholdDecision.BinDecision bin : result.bins) {
            if (near(bin.rpm, rpm)) return bin;
        }
        throw new AssertionError("missing bin " + rpm);
    }

    private static MlgEmbeddedTuneMetadata metadata(double windowMs, double lookbackSeconds) {
        return MlgEmbeddedTuneMetadata.parse(
                "<msq><page>"
                + "<constant name=\"tpsAeDetectMode\">\"Dual stride, newest\"</constant>"
                + "<constant name=\"tpsAeDeltaWindowMs\">" + windowMs + "</constant>"
                + "<constant name=\"tpsAccelLookback\">" + lookbackSeconds + "</constant>"
                + "</page></msq>");
    }

    private static AeProjectSnapshot snapshot(double threshold, double[] rpm,
                                              String mode, double windowMs,
                                              double lookbackSeconds) {
        double[] thresholds = new double[rpm.length];
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
                rpm, thresholds, 12.0);
    }

    private static int count(String value, String needle) {
        if (value == null || needle == null || needle.length() == 0) return 0;
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 0.000001;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
