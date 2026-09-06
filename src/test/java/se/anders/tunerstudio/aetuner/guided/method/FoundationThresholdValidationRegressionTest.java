package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.GuidedValidationVerdict;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class FoundationThresholdValidationRegressionTest {
    private static final double[] RPM_BINS =
            new double[]{1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500};

    private FoundationThresholdValidationRegressionTest() { }

    public static void main(String[] args) {
        localChangedRegionUsesSameEventCounterfactualKeep();
        newNormalCorrectionCrossingRecommendsRestore();
        missingFreshTargetEvidenceIsInconclusive();
        nonTargetContextDriftBlocksValidation();
        sameEventComparisonDoesNotRequireBToMatchAAmplitude();
        System.out.println("FoundationThresholdValidationRegressionTest passed");
    }

    private static void localChangedRegionUsesSameEventCounterfactualKeep() {
        AeProjectSnapshot before = snapshot(new double[]{0.30, 1.00, 0.30, 0.30, 0.30, 0.30, 0.30, 0.30});
        List<LiveSample> a = evidence(1500.0, 1.00, 0.10, 0.42, 3, 3);
        FoundationThresholdRecommendation.Result recommendation =
                FoundationThresholdRecommendation.evaluate(before, a);
        require(recommendation.plan != null && recommendation.plan.changeCount() == 1,
                "baseline fixture did not create one local threshold proposal");
        ProposalWritePlan.Change change = recommendation.plan.getChanges().get(0);
        require(change.flatIndex == 1 && close(change.proposedValue, 0.75),
                "baseline fixture lost expected bounded local proposal");

        FoundationThresholdValidation.Baseline baseline =
                FoundationThresholdValidation.arm(before, a, recommendation.plan);
        require(baseline != null
                        && baseline.instructions().contains("3 Normal Corrections + 3 Acceleration Openings")
                        && baseline.instructions().contains("80 qualifying quiet samples")
                        && baseline.instructions().contains("1.5 continuous seconds"),
                "A/B baseline did not expose physical and time-qualified Validation B instructions");

        AeProjectSnapshot after = snapshot(new double[]{0.30, 0.75, 0.30, 0.30, 0.30, 0.30, 0.30, 0.30});
        List<LiveSample> b = evidence(1500.0, 0.75, 0.10, 0.90, 3, 3);
        FoundationThresholdValidation.Result result =
                FoundationThresholdValidation.compare(baseline, after, b);
        require(result.verdict == GuidedValidationVerdict.KEEP,
                "same-event local threshold improvement did not produce KEEP");
        require(result.text.contains("SAME-EVENT B COUNTERFACTUAL")
                        && result.text.contains("VERDICT: KEEP")
                        && result.text.contains("Normal Corrections / Acceleration Openings 3/3")
                        && result.text.contains("B classification: Normal crossings")
                        && result.text.contains("Acceleration hits")
                        && result.text.contains("Acceleration misses")
                        && result.text.contains("REGION VERDICT UNDER APPLIED CURVE: KEEP")
                        && result.text.contains("does not declare any region fully converged")
                        && result.text.contains("NEXT INCREMENTAL PROPOSAL — NOT YET APPLIED"),
                "KEEP report omitted Archive46-driven classification, region-verdict or non-convergence semantics");
        require(!result.text.contains("4500"),
                "local validation incorrectly required unrelated high-RPM coverage");
    }

    private static void newNormalCorrectionCrossingRecommendsRestore() {
        AeProjectSnapshot before = snapshot(new double[]{0.30, 1.00, 0.30, 0.30, 0.30, 0.30, 0.30, 0.30});
        List<LiveSample> a = evidence(1500.0, 1.00, 0.10, 0.42, 3, 3);
        ProposalWritePlan plan = FoundationThresholdRecommendation.evaluate(before, a).plan;
        FoundationThresholdValidation.Baseline baseline = FoundationThresholdValidation.arm(before, a, plan);
        AeProjectSnapshot after = snapshot(new double[]{0.30, 0.75, 0.30, 0.30, 0.30, 0.30, 0.30, 0.30});
        List<LiveSample> b = evidence(1500.0, 0.75, 0.80, 1.50, 3, 3);
        FoundationThresholdValidation.Result result = FoundationThresholdValidation.compare(baseline, after, b);
        require(result.verdict == GuidedValidationVerdict.RESTORE,
                "new same-event Normal Correction threshold crossing did not recommend RESTORE");
        require(result.text.contains("newly triggered Normal Corrections")
                        && result.text.contains("REGION VERDICT UNDER APPLIED CURVE: RESTORE"),
                "RESTORE report did not identify the exact crossing regression and region verdict");
    }

    private static void missingFreshTargetEvidenceIsInconclusive() {
        AeProjectSnapshot before = snapshot(new double[]{0.30, 1.00, 0.30, 0.30, 0.30, 0.30, 0.30, 0.30});
        List<LiveSample> a = evidence(1500.0, 1.00, 0.10, 0.42, 3, 3);
        ProposalWritePlan plan = FoundationThresholdRecommendation.evaluate(before, a).plan;
        FoundationThresholdValidation.Baseline baseline = FoundationThresholdValidation.arm(before, a, plan);
        AeProjectSnapshot after = snapshot(new double[]{0.30, 0.75, 0.30, 0.30, 0.30, 0.30, 0.30, 0.30});
        List<LiveSample> b = evidence(1500.0, 0.75, 0.10, 0.90, 2, 0);
        FoundationThresholdValidation.Result result = FoundationThresholdValidation.compare(baseline, after, b);
        require(result.verdict == GuidedValidationVerdict.INCONCLUSIVE,
                "insufficient local post-Apply evidence did not remain INCONCLUSIVE");
    }

    private static void nonTargetContextDriftBlocksValidation() {
        AeProjectSnapshot before = snapshot(new double[]{0.30, 1.00, 0.60, 0.30, 0.30, 0.30, 0.30, 0.30});
        List<LiveSample> a = evidence(1500.0, 1.00, 0.10, 0.42, 3, 3);
        ProposalWritePlan plan = FoundationThresholdRecommendation.evaluate(before, a).plan;
        FoundationThresholdValidation.Baseline baseline = FoundationThresholdValidation.arm(before, a, plan);
        AeProjectSnapshot after = snapshot(new double[]{0.30, 0.75, 0.90, 0.30, 0.30, 0.30, 0.30, 0.30});
        String mismatch = FoundationThresholdValidation.contextMismatch(baseline, after);
        require(mismatch.contains("non-target Foundation context changed")
                        && mismatch.contains("2000 RPM"),
                "non-target threshold drift was not identified before B validation");
        FoundationThresholdValidation.Result result = FoundationThresholdValidation.compare(
                baseline, after, evidence(1500.0, 0.75, 0.10, 0.90, 3, 3));
        require(result.verdict == GuidedValidationVerdict.INCONCLUSIVE
                        && result.text.contains("VALIDATION BLOCKED — CONTROLLER CONTEXT CHANGED"),
                "contaminated A/B controller state was allowed to produce a tuning verdict");
    }

    private static void sameEventComparisonDoesNotRequireBToMatchAAmplitude() {
        AeProjectSnapshot before = snapshot(new double[]{0.30, 1.00, 0.30, 0.30, 0.30, 0.30, 0.30, 0.30});
        List<LiveSample> a = evidence(1500.0, 1.00, 0.10, 0.70, 3, 3);
        ProposalWritePlan plan = FoundationThresholdRecommendation.evaluate(before, a).plan;
        FoundationThresholdValidation.Baseline baseline = FoundationThresholdValidation.arm(before, a, plan);
        AeProjectSnapshot after = snapshot(new double[]{0.30, 0.75, 0.30, 0.30, 0.30, 0.30, 0.30, 0.30});
        List<LiveSample> b = evidence(1500.0, 0.75, 0.10, 0.55, 3, 3);
        FoundationThresholdValidation.Result result = FoundationThresholdValidation.compare(baseline, after, b);
        require(result.verdict == GuidedValidationVerdict.KEEP,
                "validator still depended on matching aggregate A/B acceleration amplitude");
        require(result.text.contains("A/B driver amplitude matching is not required"),
                "same-event rationale is not explicit in the validation report");
    }

    private static List<LiveSample> evidence(double rpm, double threshold,
                                             double normalDelta, double accelerationDelta,
                                             int normalCount, int accelerationCount) {
        List<LiveSample> samples = new ArrayList<LiveSample>();
        double seconds = 0.0;
        for (int i = 0; i < 90; i++) {
            samples.add(sample(seconds, rpm, 12.0, 0.020, threshold, 0.20));
            seconds += 0.020;
        }
        seconds += 0.25;
        for (int i = 0; i < normalCount; i++) {
            samples.add(sample(seconds, rpm, 12.1 + i * 0.02,
                    normalDelta + i * 0.002, threshold, 3.0));
            seconds += 0.30;
        }
        for (int i = 0; i < accelerationCount; i++) {
            double tps = 15.0 + i * 0.10;
            samples.add(sample(seconds, rpm, tps,
                    accelerationDelta + i * 0.01, threshold, 18.0));
            samples.add(sample(seconds + 0.05, rpm, tps + 0.7,
                    accelerationDelta + 0.08 + i * 0.01, threshold, 4.0));
            seconds += 0.30;
        }
        return samples;
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

    private static AeProjectSnapshot snapshot(double[] thresholdValues) {
        return new AeProjectSnapshot(
                "Main Controller",
                new double[0], new double[0], new double[0][0],
                RPM_BINS, thresholdValues,
                0.0, 0.0,
                new double[0], new double[0],
                true, false, "off", false, false,
                false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0]);
    }

    private static boolean close(double a, double b) { return Math.abs(a - b) < 0.000001; }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
