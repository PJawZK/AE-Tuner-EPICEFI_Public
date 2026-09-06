package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.FoundationThresholdFocusModel;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class GuidedProposalValidationSessionRegressionTest {
    private static final double[] RPM_BINS =
            new double[]{1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500};

    private GuidedProposalValidationSessionRegressionTest() { }

    public static void main(String[] args) {
        idleLifecycleIsExplicit();
        operatorRestoreBeforeEvaluationIsNotAlgorithmVerdict();
        postApplyReadBlocksNonTargetContextDrift();
        algorithmRestoreRetainsEvaluatorVerdict();
        System.out.println("GuidedProposalValidationSessionRegressionTest passed");
    }

    private static void idleLifecycleIsExplicit() {
        GuidedProposalValidationSession session = new GuidedProposalValidationSession();
        require(!session.unresolved(), "new validation session unexpectedly unresolved");
        require(!session.canAcceptKeep(), "new validation session exposed KEEP");
        require(session.recipe() == null, "new validation session has a recipe");
        require(session.statusText().contains("No method-owned A/B validation"),
                "idle validation status is not explicit");
        session.onGuidedReset();
        require(!session.unresolved(), "idle reset armed validation");
        session.reset();
        require(!session.hasArtifactFor(GuidedTuningRecipe.FOUNDATION_THRESHOLD),
                "reset validation retained a stale artifact");
    }

    private static void operatorRestoreBeforeEvaluationIsNotAlgorithmVerdict() {
        Fixture f = fixture();
        GuidedProposalValidationSession session = new GuidedProposalValidationSession();
        session.armAfterVerifiedApply(GuidedTuningRecipe.FOUNDATION_THRESHOLD,
                f.before, f.a, f.plan, "A report", "A csv");
        require(session.unresolved(), "verified Apply did not arm A/B validation");
        require("Abort A/B Validation & Restore".equals(session.restoreActionLabel()),
                "pre-evaluation Restore is not labelled as an A/B abort");
        session.markRestored(f.plan);
        require(!session.unresolved(), "operator restore did not resolve lifecycle");
        String report = session.reportFor(GuidedTuningRecipe.FOUNDATION_THRESHOLD);
        require(report.contains("State: RESOLVED_OPERATOR_RESTORE"),
                "operator restore was mislabeled as algorithmic RESTORE");
        require(report.contains("Evaluation performed: NO")
                        && report.contains("Method verdict: NOT_EVALUATED")
                        && report.contains("Resolution: OPERATOR_ABORT_OR_OVERRIDE_RESTORE"),
                "pre-evaluation Restore manufactured a method verdict");
        require(report.contains("APPLIED PLAN\nThreshold / Sensitivity regression fixture")
                        && !report.contains("APPLIED PLAN\nNEXT INCREMENTAL PROPOSAL"),
                "validation export confused the already-applied plan with a next incremental proposal");
    }

    private static void postApplyReadBlocksNonTargetContextDrift() {
        Fixture f = fixture();
        GuidedProposalValidationSession session = new GuidedProposalValidationSession();
        session.armAfterVerifiedApply(GuidedTuningRecipe.FOUNDATION_THRESHOLD,
                f.before, f.a, f.plan, "A report", "A csv");
        double[] contaminated = f.after.getThresholdValues();
        contaminated[2] = 0.90;
        AeProjectSnapshot drift = snapshot(contaminated);
        require(!session.noteWorkingTuneRead(drift),
                "post-Apply Read Working Tune accepted non-target Foundation context drift");
        require(session.statusText().contains("non-target Foundation context changed")
                        && session.statusText().contains("2000 RPM"),
                "context-drift block did not identify the changed non-target cell");
    }

    private static void algorithmRestoreRetainsEvaluatorVerdict() {
        Fixture f = fixture();
        GuidedProposalValidationSession session = new GuidedProposalValidationSession();
        session.armAfterVerifiedApply(GuidedTuningRecipe.FOUNDATION_THRESHOLD,
                f.before, f.a, f.plan, "A report", "A csv");
        require(session.noteWorkingTuneRead(f.after),
                "verified applied Working Tune did not release Validation B");
        List<LiveSample> b = evidence(1500.0, 0.75, 0.80, 1.50, 3, 3);
        session.evaluateAfter(f.after, b);
        require(session.statusText().contains("RESTORE_RECOMMENDED"),
                "same-event regression did not reach algorithm RESTORE recommendation");
        require("Restore Previous Apply".equals(session.restoreActionLabel()),
                "algorithmic RESTORE recommendation did not use the normal Restore label");
        session.markRestored(f.plan);
        String report = session.reportFor(GuidedTuningRecipe.FOUNDATION_THRESHOLD);
        require(report.contains("State: RESOLVED_RESTORE")
                        && report.contains("Evaluation performed: YES")
                        && report.contains("Method verdict: RESTORE")
                        && report.contains("Resolution: ALGORITHM_RESTORE"),
                "algorithmic Restore lost evaluator provenance");
    }

    private static Fixture fixture() {
        AeProjectSnapshot before = snapshot(
                new double[]{0.30, 1.00, 0.30, 0.30, 0.30, 0.30, 0.30, 0.30});
        List<LiveSample> a = evidence(1500.0, 1.00, 0.10, 0.42, 3, 3);
        FoundationThresholdFocusModel model = FoundationThresholdFocusModel.build(
                before, a, a.get(a.size() - 1), GuidedCaptureState.COMPLETE);
        require(model.recommendationPlanAvailable,
                "Foundation threshold fixture did not reach proposal-ready state");
        ProposalWritePlan plan = new ProposalWritePlan(
                "foundation-threshold-curve",
                "Threshold / Sensitivity regression fixture",
                before.getConfigurationName(),
                "same-event validation lifecycle fixture",
                java.util.Collections.singletonList(
                        ProposalWritePlan.Change.arrayCell(
                                "tpsAeThresholdValue", 1, 1.00, 0.75,
                                "TPS AE threshold @ 1500 RPM", "#")));
        AeProjectSnapshot after = snapshot(
                new double[]{0.30, 0.75, 0.30, 0.30, 0.30, 0.30, 0.30, 0.30});
        return new Fixture(before, after, a, plan);
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
        return new LiveSample(Math.round(seconds * 1000000000.0), seconds,
                values, tpsRate, 0.0);
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

    private static final class Fixture {
        final AeProjectSnapshot before;
        final AeProjectSnapshot after;
        final List<LiveSample> a;
        final ProposalWritePlan plan;
        Fixture(AeProjectSnapshot before, AeProjectSnapshot after,
                List<LiveSample> a, ProposalWritePlan plan) {
            this.before = before;
            this.after = after;
            this.a = a;
            this.plan = plan;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
