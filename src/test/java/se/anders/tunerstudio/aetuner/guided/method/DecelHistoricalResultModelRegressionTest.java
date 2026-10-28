package se.anders.tunerstudio.aetuner.guided.method;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DecelHistoricalResultModelRegressionTest {
    private DecelHistoricalResultModelRegressionTest() { }

    public static void main(String[] args) {
        test9LikeEvidenceBecomesUsefulBoundaryChangeResult();
        evidenceStrengthUsesIndependentEventCountsOnly();
        completeMatchingBoundaryEvidenceBecomesValidatedResult();
        proposedCellBecomesBoundaryChangeRecommendation();
        incompatibleEvidenceIsWithheldNotMislabelledMissing();
        freshWorkingTuneRefreshCanConvergeAtBoundary();
        resultContextRetainsMissingTargetsWithoutWriteAuthority();
        System.out.println("DecelHistoricalResultModelRegressionTest passed");
    }

    private static void test9LikeEvidenceBecomesUsefulBoundaryChangeResult() {
        DecelHistoricalThresholdDecision.Result decision = test9Decision();
        DecelHistoricalResultModel model = DecelHistoricalResultModel.build(
                null, null, replay(decision), null);

        require(model.state == DecelHistoricalResultModel.OverallState.CHANGE_RECOMMENDED,
                "Test-9-like evidence did not become CHANGE_RECOMMENDED after boundary targeting");
        require(model.validatedBins == 4 && model.proposedBins == 4
                        && model.missingBins == 4 && model.totalBins == 8,
                "boundary-target result lost supported/change/missing counts");
        require(model.headline.contains("LOG-DERIVED")
                        && model.recommendation.contains("classification-boundary threshold values"),
                "operator result does not directly state the boundary-derived conclusion");
        require(model.targetedCaptureUseful && !model.proposalAvailable,
                "result should retain missing-region capture but no write authority before review");
        require(model.missingBinsText().equals("4100 / 4900 / 5700 / 6500"),
                "missing RPM result no longer identifies exact unresolved regions");
        require(model.releaseMisses == 15,
                "result lost the diagnostic clear-release miss count");
        require(model.strongBins == 2 && model.goodBins == 2 && model.minimumBins == 0
                        && model.evidenceStrengthSummary().equals("2 strong / 2 good / 0 minimum"),
                "Test-13-shaped independent-event coverage did not expose 2 strong / 2 good bins");
        double[] expected = new double[]{0.450, 0.300, 0.250, 0.200};
        DecelHistoricalEvidenceStrength.Level[] expectedStrength = new DecelHistoricalEvidenceStrength.Level[]{
                DecelHistoricalEvidenceStrength.Level.GOOD,
                DecelHistoricalEvidenceStrength.Level.STRONG,
                DecelHistoricalEvidenceStrength.Level.STRONG,
                DecelHistoricalEvidenceStrength.Level.GOOD,
                DecelHistoricalEvidenceStrength.Level.NONE,
                DecelHistoricalEvidenceStrength.Level.NONE,
                DecelHistoricalEvidenceStrength.Level.NONE,
                DecelHistoricalEvidenceStrength.Level.NONE
        };
        for (int i = 0; i < model.bins.size(); i++) {
            String state = i < 4 ? "CHANGE" : "NEED EVIDENCE";
            require(state.equals(model.bins.get(i).operatorState),
                    "per-bin operator state changed at index " + i);
            require(model.bins.get(i).evidenceStrength == expectedStrength[i],
                    "per-bin independent-event evidence strength changed at index " + i);
            if (i < 4) {
                require(near(model.bins.get(i).proposed, expected[i]),
                        "classification-boundary ECU result changed at index " + i);
            } else {
                require(Double.isNaN(model.bins.get(i).proposed),
                        "missing-evidence bin fabricated an ECU result at index " + i);
            }
        }
    }

    private static void evidenceStrengthUsesIndependentEventCountsOnly() {
        DecelHistoricalThresholdDecision.BinDecision strong = bin(1600, 0.4, 0.1, 0.8,
                10, 10, 200, DecelHistoricalThresholdDecision.Outcome.PROPOSE, 0.3, "boundary");
        DecelHistoricalThresholdDecision.BinDecision good = bin(2400, 0.4, 0.1, 0.8,
                5, 5, 0, DecelHistoricalThresholdDecision.Outcome.PROPOSE, 0.3, "boundary");
        DecelHistoricalThresholdDecision.BinDecision minimum = bin(3200, 0.4, 0.1, 0.8,
                3, 3, 0, DecelHistoricalThresholdDecision.Outcome.PROPOSE, 0.3, "boundary");
        DecelHistoricalThresholdDecision.BinDecision withheld = bin(4100, 0.4,
                Double.NaN, Double.NaN, 100, 100, 0,
                DecelHistoricalThresholdDecision.Outcome.WITHHOLD, Double.NaN, "missing");

        require(DecelHistoricalEvidenceStrength.assess(strong).level
                        == DecelHistoricalEvidenceStrength.Level.STRONG,
                "10+10 independent events did not become STRONG");
        require(DecelHistoricalEvidenceStrength.assess(good).level
                        == DecelHistoricalEvidenceStrength.Level.GOOD,
                "5+5 independent events did not become GOOD");
        require(DecelHistoricalEvidenceStrength.assess(minimum).level
                        == DecelHistoricalEvidenceStrength.Level.MINIMUM,
                "3+3 independent events did not become MINIMUM");
        require(DecelHistoricalEvidenceStrength.assess(withheld).level
                        == DecelHistoricalEvidenceStrength.Level.NONE,
                "WITHHOLD gained evidence strength from unrelated event counts");
        require(DecelHistoricalEvidenceStrength.assess(strong).reason.contains("10 independent Normal")
                        && !DecelHistoricalEvidenceStrength.assess(strong).reason.contains("candidate"),
                "strength labeling stopped being based on independent physical-event counts");
    }

    private static void completeMatchingBoundaryEvidenceBecomesValidatedResult() {
        List<DecelHistoricalThresholdDecision.BinDecision> bins = new ArrayList<DecelHistoricalThresholdDecision.BinDecision>();
        for (int i = 0; i < 8; i++) bins.add(bin(800 + i * 800, 0.45, 0.1, 0.8,
                5, 5, 0, DecelHistoricalThresholdDecision.Outcome.KEEP, 0.45, "already boundary"));
        DecelHistoricalThresholdDecision.Result decision = decision(true, bins, 8, 0, 0);
        DecelHistoricalResultModel model = DecelHistoricalResultModel.build(null, null, replay(decision), null);
        require(model.state == DecelHistoricalResultModel.OverallState.VALIDATED_KEEP,
                "complete boundary-matching evidence did not become VALIDATED_KEEP");
        require(model.validatedBins == 8 && !model.targetedCaptureUseful
                        && model.missingRpmBins.length == 0,
                "complete boundary validation still requested missing-region capture");
        require(model.goodBins == 8 && model.strongBins == 0 && model.minimumBins == 0,
                "5+5 complete evidence did not remain GOOD coverage without changing tuning authority");
    }

    private static void proposedCellBecomesBoundaryChangeRecommendation() {
        List<DecelHistoricalThresholdDecision.BinDecision> bins = new ArrayList<DecelHistoricalThresholdDecision.BinDecision>();
        bins.add(bin(800, 0.9, 0.1, 0.7, 6, 8, 0,
                DecelHistoricalThresholdDecision.Outcome.PROPOSE, 0.4, "boundary target"));
        bins.add(bin(1600, 0.45, 0.1, 0.8, 6, 8, 0,
                DecelHistoricalThresholdDecision.Outcome.KEEP, 0.45, "already boundary"));
        DecelHistoricalThresholdDecision.Result decision = decision(true, bins, 1, 1, 0);
        DecelHistoricalResultModel model = DecelHistoricalResultModel.build(null, null, replay(decision), null);
        require(model.state == DecelHistoricalResultModel.OverallState.CHANGE_RECOMMENDED,
                "PROPOSE evidence did not become CHANGE_RECOMMENDED");
        require(model.validatedBins == 2 && model.bins.get(0).operatorState.equals("CHANGE")
                        && near(model.bins.get(0).proposed, 0.4),
                "proposed cell is not presented as the concrete boundary ECU result");
        require(model.goodBins == 2,
                "6/8-event supported bins did not expose GOOD coverage");
        require(!model.proposalAvailable,
                "presentation layer fabricated write authority without reviewed eligibility");
    }

    private static void incompatibleEvidenceIsWithheldNotMislabelledMissing() {
        List<DecelHistoricalThresholdDecision.BinDecision> bins = new ArrayList<DecelHistoricalThresholdDecision.BinDecision>();
        bins.add(bin(800, 0.5, Double.NaN, Double.NaN, 0, 0, 0,
                DecelHistoricalThresholdDecision.Outcome.WITHHOLD, Double.NaN, "compatibility"));
        DecelHistoricalThresholdDecision.Result decision = decision(false, bins, 0, 0, 1);
        DecelHistoricalResultModel model = DecelHistoricalResultModel.build(null, null, replay(decision), null);
        require(model.state == DecelHistoricalResultModel.OverallState.RESULT_WITHHELD,
                "incompatible evidence did not become RESULT_WITHHELD");
        require(model.bins.get(0).operatorState.equals("WITHHELD")
                        && model.bins.get(0).evidenceStrength == DecelHistoricalEvidenceStrength.Level.NONE
                        && Double.isNaN(model.bins.get(0).proposed)
                        && !model.targetedCaptureUseful,
                "calibration incompatibility was incorrectly presented as an ECU result, coverage strength or driving-data target");
    }

    private static void freshWorkingTuneRefreshCanConvergeAtBoundary() {
        List<DecelHistoricalThresholdDecision.BinDecision> initialBins = new ArrayList<DecelHistoricalThresholdDecision.BinDecision>();
        initialBins.add(bin(1600, 1.0, 0.2, 0.8, 8, 10, 0,
                DecelHistoricalThresholdDecision.Outcome.PROPOSE, 0.5, "boundary target"));
        DecelHistoricalResultModel initial = DecelHistoricalResultModel.build(
                null, null, replay(decision(true, initialBins, 0, 1, 0)), null);

        List<DecelHistoricalThresholdDecision.BinDecision> finalBins = new ArrayList<DecelHistoricalThresholdDecision.BinDecision>();
        finalBins.add(bin(1600, 0.5, 0.2, 0.8, 8, 10, 0,
                DecelHistoricalThresholdDecision.Outcome.KEEP, 0.5, "already boundary"));
        DecelHistoricalThresholdDecision.Result finalDecision = decision(true, finalBins, 1, 0, 0);
        DecelHistoricalThresholdProposal.Result proposalResult = proposalResult(
                DecelHistoricalThresholdProposal.State.CONVERGED, finalDecision);
        DecelHistoricalResultModel refreshed =
                DecelHistoricalResultModel.refreshAfterWorkingTune(initial, proposalResult);

        require(refreshed.state == DecelHistoricalResultModel.OverallState.VALIDATED_KEEP,
                "fresh Working Tune reevaluation did not converge at the historical boundary target");
        require(refreshed.proposedBins == 0 && refreshed.validatedBins == 1
                        && near(refreshed.bins.get(0).current, 0.5)
                        && near(refreshed.bins.get(0).proposed, 0.5),
                "refreshed result did not expose the boundary-matching Working Tune value");
        require(refreshed.bins.get(0).evidenceStrength == DecelHistoricalEvidenceStrength.Level.GOOD,
                "fresh Working Tune refresh lost retained independent-event coverage strength");
    }

    private static void resultContextRetainsMissingTargetsWithoutWriteAuthority() {
        DecelHistoricalResultContext.clear();
        DecelHistoricalThresholdProposalContext.clear();
        DecelHistoricalResultModel model = DecelHistoricalResultModel.build(
                null, null, replay(test9Decision()), null);
        DecelHistoricalResultContext.publish(model);
        require(DecelHistoricalResultContext.reviewReady(),
                "useful historical result did not gain read-only Review authority");
        require(DecelHistoricalResultContext.targetedCaptureUseful()
                        && DecelHistoricalResultContext.targetedRpmText().equals("4100 / 4900 / 5700 / 6500"),
                "result context lost exact targeted missing RPM regions");
        require(!DecelHistoricalResultContext.targetedRpmText().contains("3200"),
                "GOOD but thinner supported 3200 evidence was incorrectly turned into a missing-region capture target");
        require(!DecelHistoricalThresholdProposalContext.active(),
                "publishing a result silently created proposal/write authority");
        DecelHistoricalResultContext.clear();
    }

    private static DecelHistoricalThresholdDecision.Result test9Decision() {
        List<DecelHistoricalThresholdDecision.BinDecision> bins = new ArrayList<DecelHistoricalThresholdDecision.BinDecision>();
        bins.add(bin(800, 0.570, 0.071, 0.711, 29, 5, 0,
                DecelHistoricalThresholdDecision.Outcome.PROPOSE, 0.450, "boundary target"));
        bins.add(bin(1600, 0.597, 0.132, 0.832, 42, 68, 9,
                DecelHistoricalThresholdDecision.Outcome.PROPOSE, 0.300, "boundary target"));
        bins.add(bin(2400, 0.831, 0.095, 1.150, 33, 45, 5,
                DecelHistoricalThresholdDecision.Outcome.PROPOSE, 0.250, "boundary target"));
        bins.add(bin(3200, 0.867, 0.061, 1.525, 6, 13, 0,
                DecelHistoricalThresholdDecision.Outcome.PROPOSE, 0.200, "boundary target"));
        bins.add(bin(4100, 0.903, Double.NaN, Double.NaN, 0, 0, 0,
                DecelHistoricalThresholdDecision.Outcome.WITHHOLD, Double.NaN, "insufficient"));
        bins.add(bin(4900, 0.940, Double.NaN, Double.NaN, 0, 0, 0,
                DecelHistoricalThresholdDecision.Outcome.WITHHOLD, Double.NaN, "insufficient"));
        bins.add(bin(5700, 0.974, Double.NaN, Double.NaN, 0, 0, 0,
                DecelHistoricalThresholdDecision.Outcome.WITHHOLD, Double.NaN, "insufficient"));
        bins.add(bin(6500, 1.010, Double.NaN, Double.NaN, 0, 0, 0,
                DecelHistoricalThresholdDecision.Outcome.WITHHOLD, Double.NaN, "insufficient"));
        return decision(true, bins, 0, 4, 4);
    }

    private static DecelHistoricalReplayService.Result replay(
            DecelHistoricalThresholdDecision.Result decision) {
        List<DecelHistoricalEvidenceAnalyzer.BinSummary> analysisBins =
                new ArrayList<DecelHistoricalEvidenceAnalyzer.BinSummary>();
        for (int i = 0; i < decision.bins.size(); i++) {
            DecelHistoricalThresholdDecision.BinDecision b = decision.bins.get(i);
            int misses = i == 1 ? 7 : i == 2 ? 5 : i == 3 ? 3 : 0;
            analysisBins.add(new DecelHistoricalEvidenceAnalyzer.BinSummary(
                    b.rpm, b.normalEvents, b.releaseEvents, b.ambiguousEvents,
                    0, misses, b.safeLow, b.safeHigh,
                    b.safeLow, b.safeHigh,
                    b.outcome != DecelHistoricalThresholdDecision.Outcome.WITHHOLD,
                    Collections.<DecelHistoricalEvidenceAnalyzer.Event>emptyList()));
        }
        DecelHistoricalEvidenceAnalyzer.Result analysis =
                new DecelHistoricalEvidenceAnalyzer.Result(
                        "fixture", 1000, true, 80, 2.0, 0.5, 0.7,
                        2.0, 6.0, 255, 110, 131, 14, 4, analysisBins);
        return new DecelHistoricalReplayService.Result(
                null, null, analysis, null, decision, "fixture technical report");
    }

    private static DecelHistoricalThresholdDecision.Result decision(
            boolean compatible,
            List<DecelHistoricalThresholdDecision.BinDecision> bins,
            int keep, int propose, int withhold) {
        DecelHistoricalThresholdDecision.Compatibility compatibility =
                new DecelHistoricalThresholdDecision.Compatibility(compatible,
                        compatible ? "COMPATIBLE" : "WITHHELD — mismatch");
        return new DecelHistoricalThresholdDecision.Result(
                compatibility, bins, keep, propose, withhold, "fixture decision");
    }

    private static DecelHistoricalThresholdDecision.BinDecision bin(
            double rpm, double current, double low, double high,
            int normal, int release, int ambiguous,
            DecelHistoricalThresholdDecision.Outcome outcome,
            double preview, String reason) {
        return new DecelHistoricalThresholdDecision.BinDecision(
                rpm, current, low, high, normal, release, ambiguous,
                outcome, preview, reason);
    }

    private static DecelHistoricalThresholdProposal.Result proposalResult(
            DecelHistoricalThresholdProposal.State state,
            DecelHistoricalThresholdDecision.Result decision) {
        try {
            Constructor<DecelHistoricalThresholdProposal.Result> constructor =
                    DecelHistoricalThresholdProposal.Result.class.getDeclaredConstructor(
                            DecelHistoricalThresholdProposal.State.class,
                            se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan.class,
                            DecelHistoricalThresholdDecision.Result.class,
                            String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(state, null, decision, "fixture refreshed result");
        } catch (Exception ex) {
            throw new AssertionError("could not create proposal result fixture", ex);
        }
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 0.000001;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
