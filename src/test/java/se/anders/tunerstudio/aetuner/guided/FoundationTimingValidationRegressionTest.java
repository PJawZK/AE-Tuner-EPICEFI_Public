package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.AeParameterNames;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import java.util.ArrayList;
import java.util.List;

public final class FoundationTimingValidationRegressionTest {
    private FoundationTimingValidationRegressionTest() { }

    public static void main(String[] args) {
        freshMethodRetainingAppliedPairKeeps();
        freshMethodResolvingBackRestores();
        unresolvedFreshMethodIsInconclusive();
        System.out.println("FoundationTimingValidationRegressionTest passed");
    }

    private static void freshMethodRetainingAppliedPairKeeps() {
        ProposalWritePlan plan = timingPlan();
        FoundationTimingValidation.Baseline baseline =
                new FoundationTimingValidation.Baseline(plan, status(true, 40.0, 25.0, true, 60.0, 50.0, true));
        FoundationTimingValidation.Result result =
                FoundationTimingValidation.compareStatus(
                        baseline, status(true, 40.0, 40.0, false, 60.0, 60.0, false),
                        40.0, 0.060);
        require(result.verdict == GuidedValidationVerdict.KEEP,
                "fresh timing evidence retaining applied pair did not KEEP");
    }

    private static void freshMethodResolvingBackRestores() {
        ProposalWritePlan plan = timingPlan();
        FoundationTimingValidation.Baseline baseline =
                new FoundationTimingValidation.Baseline(plan, status(true, 40.0, 25.0, true, 60.0, 50.0, true));
        FoundationTimingValidation.Result result =
                FoundationTimingValidation.compareStatus(
                        baseline, status(true, 25.0, 40.0, true, 60.0, 60.0, false),
                        40.0, 0.060);
        require(result.verdict == GuidedValidationVerdict.RESTORE,
                "fresh timing evidence resolving back to prior Delta did not RESTORE");
    }

    private static void unresolvedFreshMethodIsInconclusive() {
        ProposalWritePlan plan = timingPlan();
        FoundationTimingValidation.Baseline baseline =
                new FoundationTimingValidation.Baseline(plan, status(true, 40.0, 25.0, true, 60.0, 50.0, true));
        FoundationTimingValidation.Result result =
                FoundationTimingValidation.compareStatus(
                        baseline, status(false, 40.0, 40.0, false, 60.0, 60.0, false),
                        40.0, 0.060);
        require(result.verdict == GuidedValidationVerdict.INCONCLUSIVE,
                "unresolved fresh timing evidence did not remain INCONCLUSIVE");
    }

    private static ProposalWritePlan timingPlan() {
        List<ProposalWritePlan.Change> changes = new ArrayList<ProposalWritePlan.Change>();
        changes.add(ProposalWritePlan.Change.scalar(
                AeParameterNames.TPS_AE_DELTA_WINDOW_MS, 25.0, 40.0,
                "Delta Window", "ms"));
        changes.add(ProposalWritePlan.Change.scalar(
                AeParameterNames.TPS_ACCEL_LOOKBACK, 0.050, 0.060,
                "Sample Length", "s"));
        return new ProposalWritePlan(
                "engagement-detection-timing-pair",
                "AE Foundation — TPS Movement / Timing",
                "Main Controller", "validation fixture", changes);
    }

    private static EngagementPassiveCapture.TimingStatus status(
            boolean applyReady, double selectedDelta, double currentDelta,
            boolean deltaChange, double selectedSampleMs, double currentSampleMs,
            boolean sampleChange) {
        return new EngagementPassiveCapture.TimingStatus(
                true, true, applyReady,
                selectedDelta, currentDelta, deltaChange,
                selectedSampleMs, currentSampleMs, sampleChange,
                applyReady ? "resolved" : "need more evidence",
                selectedDelta, 0.10,
                selectedDelta + 5.0, 0.20,
                0.10, 0.04, 0.15,
                selectedDelta, selectedDelta + 5.0,
                2, 10, 5.0, 120.0,
                false, "GOOD", "repeat comparable openings");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
