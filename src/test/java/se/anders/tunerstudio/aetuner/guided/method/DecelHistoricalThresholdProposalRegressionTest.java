package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.ChannelRole;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class DecelHistoricalThresholdProposalRegressionTest {
    private static final double[] RPM =
            new double[]{1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500};
    private static final double BOUNDARY_TARGET = 0.400;

    private DecelHistoricalThresholdProposalRegressionTest() { }

    public static void main(String[] args) {
        eligibilityIsPlanFreeUntilExplicitReview();
        explicitReviewStagesBoundaryCells();
        signalCompatibilitySafetyStillBlocksAuthority();
        axisAndZeroSafetyStillBlockAuthority();
        appliedBoundaryTargetConvergesInOneFreshRead();
        moduleRoutingAndLifecycleClear();
        System.out.println("DecelHistoricalThresholdProposalRegressionTest passed");
    }

    private static void eligibilityIsPlanFreeUntilExplicitReview() {
        DecelHistoricalThresholdProposalContext.clear();
        AeProjectSnapshot tune = snapshot(0.30, RPM, "Dual stride, newest", 30.0);
        Fixture f = fixture(tune, Signal.MATCHING);
        DecelHistoricalThresholdProposal.Eligibility eligibility =
                DecelHistoricalThresholdProposal.reviewEligibility(null, tune, f.replay);

        require(eligibility.promotable
                        && eligibility.state == DecelHistoricalThresholdProposal.State.PLAN_READY,
                "classification-boundary candidates were not exposed as reviewable eligibility");
        require(f.replay.thresholdDecision.proposeCount == 2,
                "fixture did not produce two boundary candidates");
        require(!DecelHistoricalThresholdProposalContext.active()
                        && DecelHistoricalThresholdProposalContext.planFor(tune) == null,
                "eligibility created staged write authority before explicit review");
        require(eligibility.reviewText.contains("No ProposalWritePlan has been constructed or staged")
                        && !eligibility.reviewText.contains("values will change")
                        && !eligibility.reviewText.contains("THRESHOLD PROPOSAL — NOT YET APPLIED"),
                "pre-Review eligibility leaked a constructed write plan");
    }

    private static void explicitReviewStagesBoundaryCells() {
        DecelHistoricalThresholdProposalContext.clear();
        AeProjectSnapshot tune = snapshot(0.05, RPM, "Dual stride, newest", 30.0);
        Fixture f = fixture(tune, Signal.MATCHING);
        DecelHistoricalThresholdProposal.Eligibility eligibility =
                DecelHistoricalThresholdProposal.reviewEligibility(null, tune, f.replay);

        String review = DecelHistoricalThresholdProposal.reviewChangesText(tune, eligibility.evidence);
        require(review.contains("CLASSIFICATION-BOUNDARY TARGETS READY")
                        && review.contains("2 values will change"),
                "explicit Review did not expose exact boundary would-write changes");

        DecelHistoricalThresholdProposal.Result staged =
                DecelHistoricalThresholdProposalContext.stage(eligibility, tune);
        require(staged.state == DecelHistoricalThresholdProposal.State.PLAN_READY
                        && staged.plan != null && staged.plan.changeCount() == 2,
                "explicit review did not stage exactly two boundary cells");
        require(staged.plan.getRecipeId().equals("decel-historical-threshold-curve"),
                "historical proposal lost its audit identity");
        for (ProposalWritePlan.Change c : staged.plan.getChanges()) {
            require(c.kind == ProposalWritePlan.Kind.ARRAY_CELL
                            && c.parameterName.equals(AeParameterNames.TPS_DECEL_THRESHOLD_VALUES),
                    "historical proposal escaped the Decel threshold curve");
            require(c.flatIndex == 1 || c.flatIndex == 4,
                    "unsupported cell entered historical write plan");
            require(near(c.expectedValue, 0.05) && near(c.proposedValue, BOUNDARY_TARGET),
                    "historical plan did not use the classification-boundary target");
        }
        require(staged.plan.getContext().contains("independent Normal/Release physical events")
                        && staged.plan.getContext().contains("Hold and fuel multiplier remain unchanged"),
                "plan lost independent-event provenance or broadened authority");
    }

    private static void signalCompatibilitySafetyStillBlocksAuthority() {
        AeProjectSnapshot tune = snapshot(0.05, RPM, "Dual stride, newest", 30.0);
        Fixture directOnly = fixture(tune, Signal.DIRECT_ONLY);
        DecelHistoricalThresholdProposal.Eligibility missingTwin =
                DecelHistoricalThresholdProposal.reviewEligibility(null, tune, directOnly.replay);
        require(!missingTwin.promotable && missingTwin.reviewText.contains("Mode-matched falling twin"),
                "direct fallback gained write authority without configured twin");

        Fixture contradictory = fixture(tune, Signal.CONTRADICTORY_DIRECT);
        DecelHistoricalThresholdProposal.Eligibility mismatch =
                DecelHistoricalThresholdProposal.reviewEligibility(null, tune, contradictory.replay);
        require(!mismatch.promotable && mismatch.reviewText.contains("materially disagrees"),
                "contradictory direct channel did not block promotion");

        AeProjectSnapshot modeMismatch = snapshot(0.05, RPM, "Max step, timed", 30.0);
        Fixture modeFixture = fixture(modeMismatch, Signal.MATCHING);
        DecelHistoricalThresholdProposal.Eligibility modeEligibility =
                DecelHistoricalThresholdProposal.reviewEligibility(null, modeMismatch, modeFixture.replay);
        require(!modeEligibility.promotable
                        && modeEligibility.reviewText.contains("different falling twins"),
                "detector mode mismatch retained proposal authority");

        AeProjectSnapshot timingMismatch = snapshot(0.05, RPM, "Dual stride, newest", 40.0);
        Fixture timingFixture = fixture(timingMismatch, Signal.MATCHING);
        DecelHistoricalThresholdProposal.Eligibility timingEligibility =
                DecelHistoricalThresholdProposal.reviewEligibility(null, timingMismatch, timingFixture.replay);
        require(!timingEligibility.promotable
                        && timingEligibility.reviewText.contains("Delta Window differs"),
                "detector timing mismatch retained proposal authority");
    }

    private static void axisAndZeroSafetyStillBlockAuthority() {
        DecelHistoricalThresholdProposalContext.clear();
        AeProjectSnapshot zero = snapshot(0.0, RPM, "Dual stride, newest", 30.0);
        Fixture zeroFixture = fixture(zero, Signal.MATCHING);
        DecelHistoricalThresholdProposal.Eligibility zeroEligibility =
                DecelHistoricalThresholdProposal.reviewEligibility(null, zero, zeroFixture.replay);
        require(!zeroEligibility.promotable && zeroFixture.replay.thresholdDecision.proposeCount == 0,
                "firmware-disabled zero threshold was promoted or auto-enabled");

        AeProjectSnapshot baseline = snapshot(0.05, RPM, "Dual stride, newest", 30.0);
        Fixture f = fixture(baseline, Signal.MATCHING);
        DecelHistoricalThresholdProposal.Eligibility eligibility =
                DecelHistoricalThresholdProposal.reviewEligibility(null, baseline, f.replay);
        require(DecelHistoricalThresholdProposalContext.stage(eligibility, baseline).plan != null,
                "axis fixture did not stage baseline proposal");

        double[] changedRpm = RPM.clone();
        changedRpm[1] = 1550.0;
        DecelHistoricalThresholdProposal.Result changed =
                DecelHistoricalThresholdProposalContext.evaluate(
                        snapshot(0.05, changedRpm, "Dual stride, newest", 30.0));
        require(changed.state == DecelHistoricalThresholdProposal.State.WITHHELD
                        && changed.plan == null && changed.reviewText.contains("RPM axis changed"),
                "changed Working Tune RPM axis did not invalidate staged evidence");
    }

    private static void appliedBoundaryTargetConvergesInOneFreshRead() {
        DecelHistoricalThresholdProposalContext.clear();
        AeProjectSnapshot current = snapshot(0.01, RPM, "Dual stride, newest", 30.0);
        Fixture f = fixture(current, Signal.MATCHING);
        DecelHistoricalThresholdProposal.Eligibility eligibility =
                DecelHistoricalThresholdProposal.reviewEligibility(null, current, f.replay);
        DecelHistoricalThresholdProposal.Result result =
                DecelHistoricalThresholdProposalContext.stage(eligibility, current);
        require(result.state == DecelHistoricalThresholdProposal.State.PLAN_READY
                        && result.plan != null,
                "boundary result did not produce reviewed plan");

        double[] before = current.getDecelThresholdValues();
        double[] next = before.clone();
        for (ProposalWritePlan.Change c : result.plan.getChanges()) {
            require(near(c.proposedValue, BOUNDARY_TARGET),
                    "plan did not target classification boundary");
            require(near(before[c.flatIndex], c.expectedValue),
                    "proposal expected value was not tied to fresh Working Tune");
            next[c.flatIndex] = c.proposedValue;
        }
        require(near(current.getDecelThresholdValues()[1], before[1]),
                "creating proposal mutated snapshot automatically");

        current = snapshot(next, RPM, "Dual stride, newest", 30.0);
        result = DecelHistoricalThresholdProposalContext.evaluate(current);
        require(result.state == DecelHistoricalThresholdProposal.State.CONVERGED
                        && result.plan == null
                        && result.reviewText.contains("CONVERGED FOR AVAILABLE EVIDENCE"),
                "fresh Working Tune did not converge after exact boundary apply");
    }

    private static void moduleRoutingAndLifecycleClear() {
        DecelHistoricalThresholdProposalContext.clear();
        DecelDetectionMethodModule module = new DecelDetectionMethodModule();
        AeProjectSnapshot tune = snapshot(0.05, RPM, "Dual stride, newest", 30.0);
        Fixture f = fixture(tune, Signal.MATCHING);
        require(module.explicitSettingWritePlan(tune) == null,
                "module exposed historical plan before explicit staging");
        DecelHistoricalThresholdProposal.Eligibility eligibility =
                DecelHistoricalThresholdProposal.reviewEligibility(null, tune, f.replay);
        DecelHistoricalThresholdProposalContext.stage(eligibility, tune);
        ProposalWritePlan plan = module.explicitSettingWritePlan(tune);
        require(plan != null && plan.getRecipeId().equals("decel-historical-threshold-curve"),
                "staged plan did not route through existing explicit-setting contract");
        DecelHistoricalThresholdProposalContext.clear();
        require(!DecelHistoricalThresholdProposalContext.active()
                        && module.explicitSettingWritePlan(tune) == null,
                "lifecycle clear left proposal authority active");
    }

    private enum Signal { MATCHING, DIRECT_ONLY, CONTRADICTORY_DIRECT }

    private static final class Fixture {
        final DecelHistoricalReplayService.Result replay;
        Fixture(DecelHistoricalReplayService.Result replay) { this.replay = replay; }
    }

    private static Fixture fixture(AeProjectSnapshot tune, Signal signal) {
        List<LiveSample> raw = new ArrayList<LiveSample>();
        double t = quiet(raw, 0.0, 1500.0, signal);
        for (int i = 0; i < 3; i++) {
            t = correction(raw, t, 1500.0, i, signal);
            t = correction(raw, t, 3000.0, i, signal);
        }
        for (int i = 0; i < 3; i++) {
            t = release(raw, t, 1500.0, i, signal);
            t = release(raw, t, 3000.0, i, signal);
        }

        MlgEmbeddedTuneMetadata embedded = metadata();
        List<LiveSample> primary = DecelSignalSamples.asPrimaryDelta(raw, embedded.detectorModeRaw);
        DecelHistoricalEvidenceAnalyzer.Result analysis =
                DecelHistoricalEvidenceAnalyzer.analyze(tune, primary);
        require(analysis.quietCalibrationLocked && analysis.separatedBins == 2,
                "proposal fixture did not produce two separated bins");
        DecelHistoricalThresholdDecision.Result decision =
                DecelHistoricalThresholdDecision.evaluate(tune, analysis, embedded);
        DecelHistoricalLogImporter.Result imported = new DecelHistoricalLogImporter.Result(
                raw, "synthetic historical MLG", "fixture",
                raw.size(), raw.size(), 0,
                true, false, false, false, false, true,
                signal != Signal.DIRECT_ONLY);
        return new Fixture(new DecelHistoricalReplayService.Result(
                imported, embedded, analysis, null, decision, "fixture"));
    }

    private static double quiet(List<LiveSample> out, double t, double rpm, Signal signal) {
        for (int i = 0; i < 90; i++) {
            double d = i % 2 == 0 ? 0.020 : -0.020;
            out.add(sample(t, rpm, 20.0, d, i % 2 == 0 ? 0.20 : -0.20, false, signal));
            t += 0.020;
        }
        return t + 0.25;
    }

    private static double correction(List<LiveSample> out, double t, double rpm,
                                     int i, Signal signal) {
        double base = 20.0 + i * 0.3;
        out.add(sample(t, rpm, base, 0.01, 0.2, false, signal));
        t += 0.25;
        out.add(sample(t, rpm, base - 0.18,
                -(0.10 + i * 0.004), -3.0, false, signal));
        return t + 0.30;
    }

    private static double release(List<LiveSample> out, double t, double rpm,
                                  int i, Signal signal) {
        double base = 24.0 + i * 0.4;
        out.add(sample(t, rpm, base, 0.01, 0.2, false, signal));
        t += 0.25;
        out.add(sample(t, rpm, base - 0.65,
                -(0.42 + i * 0.01), -18.0, true, signal));
        out.add(sample(t + 0.05, rpm, base - 1.20,
                -(0.58 + i * 0.01), -8.0, true, signal));
        return t + 0.40;
    }

    private static LiveSample sample(double seconds, double rpm, double tps,
                                     double delta, double tpsRate, boolean active,
                                     Signal signal) {
        EnumMap<ChannelRole, Double> v = new EnumMap<ChannelRole, Double>(ChannelRole.class);
        v.put(ChannelRole.RPM, rpm);
        v.put(ChannelRole.TPS, tps);
        v.put(ChannelRole.ENGINE_RUNNING, 1.0);
        v.put(ChannelRole.ENGINE_CRANKING, 0.0);
        v.put(ChannelRole.DECEL_DELTA_TPS,
                signal == Signal.CONTRADICTORY_DIRECT ? delta + 1.0 : delta);
        if (signal != Signal.DIRECT_ONLY) v.put(ChannelRole.AE_DELTA_NEWEST_FALL, delta);
        v.put(ChannelRole.TPS_DECEL_ACTIVE, active ? 1.0 : 0.0);
        return new LiveSample((long)(seconds * 1.0e9), seconds, v, tpsRate, 0.0);
    }

    private static MlgEmbeddedTuneMetadata metadata() {
        return MlgEmbeddedTuneMetadata.parse(
                "<msq><page>"
                + "<constant name=\"tpsAeDetectMode\">\"Dual stride, newest\"</constant>"
                + "<constant name=\"tpsAeDeltaWindowMs\">30.0</constant>"
                + "<constant name=\"tpsAccelLookback\">0.05</constant>"
                + "</page></msq>");
    }

    private static AeProjectSnapshot snapshot(double threshold, double[] rpm,
                                              String mode, double windowMs) {
        double[] thresholds = new double[rpm.length];
        for (int i = 0; i < thresholds.length; i++) thresholds[i] = threshold;
        return snapshot(thresholds, rpm, mode, windowMs);
    }

    private static AeProjectSnapshot snapshot(double[] thresholds, double[] rpm,
                                              String mode, double windowMs) {
        return new AeProjectSnapshot(
                "Main Controller",
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                0.0, 0.0, new double[0], new double[0],
                false, false, "fixed", false, false, false, false,
                new double[0][0], new double[0][0],
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                mode, windowMs, 0.05,
                false, true, 0.35,
                rpm, thresholds, 12.0);
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 0.000001;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
