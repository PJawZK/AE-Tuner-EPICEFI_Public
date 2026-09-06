package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class FoundationThresholdRecommendationRegressionTest {
    private static final double[] RPM_BINS =
            new double[]{1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500};

    private FoundationThresholdRecommendationRegressionTest() { }

    public static void main(String[] args) {
        twoSidedEvidenceChangesOnlyObservedBins();
        intentionalOpeningTailStaysInsideWholeEvent();
        frozenCalibrationCannotRetroactivelyRelabelEvents();
        semanticMismatchIsRejectedWithoutRelabeling();
        archive45PhysicalQualityGatesRejectBadManeuvers();
        quietCalibrationNeedsCountAndContinuousTime();
        dynamicAveragedThresholdUsesExactFirmwareEquation();
        dynamicOnlyThresholdKeepsStaticCurveBlocked();
        System.out.println("FoundationThresholdRecommendationRegressionTest passed");
    }

    private static void twoSidedEvidenceChangesOnlyObservedBins() {
        AeProjectSnapshot snapshot = snapshot(false, false,
                new double[]{0.30, 1.00, 0.30, 0.30, 1.00, 0.30, 0.30, 0.30});
        List<LiveSample> evidence = evidenceForBins(1500.0, 3000.0, 1.00);
        FoundationThresholdRecommendation.Result result =
                FoundationThresholdRecommendation.evaluate(snapshot, evidence);
        ProposalWritePlan plan = result.plan;
        require(plan != null && plan.changeCount() == 2,
                "two observed out-of-window bins should create exactly two changes");
        require(result.eligibleBins == 2 && result.changedBins == 2,
                "recommendation did not report exact evidence-backed bin counts");
        ProposalWritePlan.Change first = plan.getChanges().get(0);
        ProposalWritePlan.Change second = plan.getChanges().get(1);
        require(AeParameterNames.TPS_AE_THRESHOLD_VALUES.equals(first.parameterName)
                        && AeParameterNames.TPS_AE_THRESHOLD_VALUES.equals(second.parameterName),
                "threshold recommendation targeted a setting outside tpsAeThresholdValue");
        require(first.flatIndex == 1 && second.flatIndex == 4,
                "recommendation extrapolated into an unobserved RPM bin");
        requireClose(0.75, first.proposedValue,
                "first recommendation did not obey conservative 25% movement cap");
        require(result.reviewText.contains("Normal Corrections 3 event(s), Acceleration Openings 3 event(s)")
                        && result.reviewText.contains("Proposed threshold changes: 2")
                        && result.reviewText.contains("quiet p99 is diagnostic only"),
                "review did not explain the physical event classes and calibration boundary");
    }

    private static void intentionalOpeningTailStaysInsideWholeEvent() {
        FoundationThresholdRecommendation.Result result =
                FoundationThresholdRecommendation.evaluate(
                        snapshot(false, false,
                                new double[]{0.30, 1.00, 0.30, 0.30, 1.00, 0.30, 0.30, 0.30}),
                        evidenceForBins(1500.0, 3000.0, 1.00));
        FoundationThresholdRecommendation.BinDecision first = decisionAt(result, 1500.0);
        FoundationThresholdRecommendation.BinDecision second = decisionAt(result, 3000.0);
        require(first != null && second != null, "covered bins disappeared");
        require(first.incidentalEvents == 3 && first.intentEvents == 3
                        && second.incidentalEvents == 3 && second.intentEvents == 3,
                "slow tails leaked into the Normal Correction population");
        require(first.effectiveValidated && second.effectiveValidated,
                "clean 3+3 effective evidence did not lock the covered regions");
    }

    private static void frozenCalibrationCannotRetroactivelyRelabelEvents() {
        AeProjectSnapshot snapshot = snapshot(false, false,
                new double[]{2.449, 2.449, 2.449, 2.449, 2.449, 2.449, 2.449, 2.449});
        List<LiveSample> prefix = new ArrayList<LiveSample>();
        double seconds = appendQuiet(prefix, 0.0, 2400.0, 2.449, 90);
        for (int i = 0; i < 3; i++) {
            prefix.add(sample(seconds, 2400.0, 12.0 + i * 0.1,
                    0.40 + i * 0.01, 2.449, 3.0));
            seconds += 0.30;
        }
        for (int i = 0; i < 3; i++) {
            prefix.add(sample(seconds, 2400.0, 15.2 + i * 0.1,
                    4.073 + i * 0.02, 2.449, 16.11));
            seconds += 0.30;
        }
        FoundationThresholdRecommendation.Result before =
                FoundationThresholdRecommendation.evaluate(snapshot, prefix);
        FoundationThresholdRecommendation.BinDecision beforeBin = decisionAt(before, 2500.0);
        require(before.summary.calibrationFrozen && beforeBin.effectiveValidated,
                "baseline did not lock before appended driving");

        List<LiveSample> appended = new ArrayList<LiveSample>(prefix);
        for (int i = 0; i < 400; i++) {
            appended.add(sample(seconds, 2400.0, 15.5, 0.020, 2.449, 4.0));
            seconds += 0.020;
        }
        FoundationThresholdRecommendation.Result after =
                FoundationThresholdRecommendation.evaluate(snapshot, appended);
        FoundationThresholdRecommendation.BinDecision afterBin = decisionAt(after, 2500.0);
        requireClose(before.summary.quietP95, after.summary.quietP95,
                "later driving moved frozen quiet p95");
        require(beforeBin.incidentalEvents == afterBin.incidentalEvents
                        && beforeBin.intentEvents == afterBin.intentEvents,
                "later samples retroactively relabelled an accepted event");
        require(afterBin.effectiveValidated, "locked PASS regressed after appended driving");
    }

    private static void semanticMismatchIsRejectedWithoutRelabeling() {
        AeProjectSnapshot snapshot = snapshot(false, false,
                new double[]{1.00, 1.00, 1.00, 1.00, 1.00, 1.00, 1.00, 1.00});
        List<LiveSample> evidence = new ArrayList<LiveSample>();
        double seconds = appendQuiet(evidence, 0.0, 1600.0, 1.00, 90);
        seconds = appendOrdinary(evidence, seconds, 1600.0, 1.00, 0);
        seconds = appendOrdinary(evidence, seconds, 1600.0, 1.00, 1);
        evidence.add(sample(seconds, 1600.0, 15.2, 1.80, 1.00, 18.0));
        seconds += 0.30;
        seconds = appendOrdinary(evidence, seconds, 1600.0, 1.00, 2);
        for (int i = 0; i < 3; i++) seconds = appendIntent(evidence, seconds, 1600.0, 1.00, i);
        FoundationThresholdRecommendation.Result result =
                FoundationThresholdRecommendation.evaluate(snapshot, evidence);
        FoundationThresholdRecommendation.BinDecision bin = decisionAt(result, 1500.0);
        require(bin.rejectedEvents == 1 && result.summary.semanticRejectedEvents == 1,
                "obvious wrong-phase opening was not rejected");
        require(bin.incidentalEvents == 3 && bin.intentEvents == 3,
                "mismatch was relabelled into the wrong population");
    }

    private static void archive45PhysicalQualityGatesRejectBadManeuvers() {
        AeProjectSnapshot snapshot = snapshot(false, false,
                new double[]{1.00, 1.00, 1.00, 1.00, 1.00, 1.00, 1.00, 1.00});
        List<LiveSample> evidence = new ArrayList<LiveSample>();
        double seconds = appendQuiet(evidence, 0.0, 1600.0, 1.00, 90);
        seconds = appendOrdinary(evidence, seconds, 1600.0, 1.00, 0);
        seconds = appendOrdinary(evidence, seconds, 1600.0, 1.00, 1);
        evidence.add(sample(seconds, 1600.0, 13.7, 0.65, 1.00, 26.6));
        seconds += 0.30;
        seconds = appendOrdinaryAtTps(evidence, seconds, 1600.0, 1.00, 13.75, 2);
        evidence.add(sample(seconds, 1600.0, 15.2, 0.16, 1.00, 4.6));
        seconds += 0.30;
        for (int i = 0; i < 3; i++) seconds = appendIntent(evidence, seconds, 1600.0, 1.00, i);

        FoundationThresholdRecommendation.BinDecision bin = decisionAt(
                FoundationThresholdRecommendation.evaluate(snapshot, evidence), 1500.0);
        require(bin.rejectedEvents >= 2, "Archive45 bad maneuvers were accepted into percentile evidence");
        require(bin.incidentalEvents == 3 && bin.intentEvents == 3,
                "replacement maneuvers did not produce clean 3+3 evidence");
        require(bin.lastRejectedReason.contains("too slow")
                        || bin.lastRejectedReason.contains("too large/fast"),
                "Driver UI has no physical reason for the rejected attempt");
    }

    private static void quietCalibrationNeedsCountAndContinuousTime() {
        AeProjectSnapshot snapshot = snapshot(false, false,
                new double[]{1,1,1,1,1,1,1,1});
        List<LiveSample> tooShort = new ArrayList<LiveSample>();
        appendQuietWithStep(tooShort, 0.0, 1600.0, 1.0, 100, 0.010);
        FoundationThresholdRecommendation.Result shortResult =
                FoundationThresholdRecommendation.evaluate(snapshot, tooShort);
        require(!shortResult.summary.calibrationFrozen,
                "sample count alone locked calibration before minimum continuous time");

        List<LiveSample> longEnough = new ArrayList<LiveSample>();
        appendQuietWithStep(longEnough, 0.0, 1600.0, 1.0, 170, 0.010);
        FoundationThresholdRecommendation.Result longResult =
                FoundationThresholdRecommendation.evaluate(snapshot, longEnough);
        require(longResult.summary.calibrationFrozen
                        && longResult.summary.quietDurationSeconds >= FoundationThresholdRecommendation.QUIET_CALIBRATION_MIN_SECONDS,
                "continuous quiet duration did not participate in calibration lock");
    }

    private static void dynamicAveragedThresholdUsesExactFirmwareEquation() {
        AeProjectSnapshot snapshot = snapshot(true, true,
                new double[]{0.30, 1.00, 0.30, 0.30, 1.00, 0.30, 0.30, 0.30});
        FoundationThresholdRecommendation.Result result =
                FoundationThresholdRecommendation.evaluate(snapshot, evidenceForBins(1500.0, 3000.0, 1.00));
        require(result.eligibleBins == 2 && result.plan != null,
                "averaged dynamic threshold lost evidence-backed static changes");
        require(result.reviewText.contains("Effective=(Static+Dynamic)/2"),
                "averaged dynamic-threshold firmware equation is not explicit");
    }

    private static void dynamicOnlyThresholdKeepsStaticCurveBlocked() {
        AeProjectSnapshot snapshot = snapshot(true, false,
                new double[]{0.30, 1.00, 0.30, 0.30, 1.00, 0.30, 0.30, 0.30});
        FoundationThresholdRecommendation.Result result =
                FoundationThresholdRecommendation.evaluate(snapshot, evidenceForBins(1500.0, 3000.0, 1.00));
        require(result.summary.effectiveValidatedBins == 2 && result.eligibleBins == 0,
                "dynamic-only fixture lost measured-effective/static-authority separation");
        require(result.plan == null && result.reviewText.contains("zero runtime authority"),
                "dynamic-only mode attempted a static curve write");
    }

    private static List<LiveSample> evidenceForBins(double firstRpm, double secondRpm, double threshold) {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        double seconds = appendQuiet(samples, 0.0, firstRpm, threshold, 90);
        for (int event = 0; event < 4; event++) {
            seconds = appendOrdinary(samples, seconds, firstRpm, threshold, event);
            seconds = appendOrdinary(samples, seconds, secondRpm, threshold, event);
        }
        for (int event = 0; event < 4; event++) {
            seconds = appendIntent(samples, seconds, firstRpm, threshold, event);
            seconds = appendIntent(samples, seconds, secondRpm, threshold, event);
        }
        return samples;
    }

    private static double appendQuiet(List<LiveSample> samples, double seconds,
                                      double rpm, double threshold, int count) {
        return appendQuietWithStep(samples, seconds, rpm, threshold, count, 0.020) + 0.25;
    }

    private static double appendQuietWithStep(List<LiveSample> samples, double seconds,
                                              double rpm, double threshold, int count, double step) {
        for (int i = 0; i < count; i++) {
            samples.add(sample(seconds, rpm, 12.0,
                    0.020 + (i % 3) * 0.002, threshold, 0.20));
            seconds += step;
        }
        return seconds;
    }

    private static double appendOrdinary(List<LiveSample> samples, double seconds,
                                         double rpm, double threshold, int event) {
        return appendOrdinaryAtTps(samples, seconds, rpm, threshold, 12.0 + event * 0.03, event);
    }

    private static double appendOrdinaryAtTps(List<LiveSample> samples, double seconds,
                                              double rpm, double threshold, double tps, int event) {
        samples.add(sample(seconds, rpm, tps,
                0.100 + (event % 4) * 0.004, threshold, 3.0));
        return seconds + 0.25;
    }

    private static double appendIntent(List<LiveSample> samples, double seconds,
                                       double rpm, double threshold, int event) {
        double baseTps = 15.0 + event * 0.10;
        samples.add(sample(seconds, rpm, baseTps,
                0.42 + event * 0.01, threshold, 18.0));
        samples.add(sample(seconds + 0.05, rpm, baseTps + 0.6,
                0.58 + event * 0.01, threshold, 4.0));
        samples.add(sample(seconds + 0.10, rpm, baseTps + 0.65,
                0.54 + event * 0.01, threshold, 1.0));
        return seconds + 0.40;
    }

    private static FoundationThresholdRecommendation.BinDecision decisionAt(
            FoundationThresholdRecommendation.Result result, double rpm) {
        for (FoundationThresholdRecommendation.BinDecision bin : result.summary.bins) {
            if (Math.abs(bin.rpm - rpm) < 0.001) return bin;
        }
        return null;
    }

    private static LiveSample sample(double seconds, double rpm, double tps,
                                     double delta, double threshold, double tpsRate) {
        EnumMap<ChannelRole, Double> values = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        values.put(ChannelRole.RPM, rpm);
        values.put(ChannelRole.TPS, tps);
        values.put(ChannelRole.DELTA_TPS, delta);
        values.put(ChannelRole.ACCEL_THRESHOLD, threshold);
        return new LiveSample(Math.round(seconds * 1000000000.0), seconds, values, tpsRate, 0.0);
    }

    private static AeProjectSnapshot snapshot(boolean dynamic, boolean averageStatic, double[] thresholdValues) {
        return new AeProjectSnapshot(
                "Main Controller",
                new double[0], new double[0], new double[0][0],
                RPM_BINS, thresholdValues,
                0.0, 0.0,
                new double[0], new double[0],
                true, false, "off", false, false,
                dynamic, averageStatic,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0]);
    }

    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.000001) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
