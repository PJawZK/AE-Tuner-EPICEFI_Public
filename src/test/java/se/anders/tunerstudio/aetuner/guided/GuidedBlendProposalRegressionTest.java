package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;
import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import java.util.Arrays;
import java.util.List;

public final class GuidedBlendProposalRegressionTest {
    private GuidedBlendProposalRegressionTest() { }

    public static void main(String[] args) {
        exposesActualProjectAxisAsCaptureTargets();
        correctedMeasurementsAreSummarizedButNeverConvertedDirectly();
        writePlanIsWithheldRegardlessOfRepeatability();
        outlierStatisticsRemainVisibleForModelReview();
        typedTrackerGroupsAndDiscardsControlledEvidence();
        System.out.println("GuidedBlendProposalRegressionTest passed");
    }

    private static void exposesActualProjectAxisAsCaptureTargets() {
        List<GuidedBlendProposal.PointChoice> points = GuidedBlendProposal.points(snapshot());
        require(points.size() == 4, "Actual project axis was not exposed");
        GuidedBlendProposal.PointChoice point = points.get(1);
        require(point.index == 1 && Math.abs(point.rpm - 2600.0) < 0.001,
                "2600 RPM point identity was not preserved");
        require(point.toString().contains("2600 RPM") && point.toString().contains("actual-bin capture target"),
                "table-point selector does not identify the actual capture target");
        String guidance = point.startGuidance(2000.0);
        require(guidance.contains("locked to this actual table bin: 2600 RPM"),
                "Guided capture guidance still permits a midpoint-region RPM assignment");
        require(guidance.contains("±200 RPM") && guidance.contains("±300 RPM"),
                "actual-bin READY/capture windows are not exposed in review guidance");
    }

    private static void correctedMeasurementsAreSummarizedButNeverConvertedDirectly() {
        GuidedBlendProposal review = GuidedBlendProposal.build(
                snapshot(), 1, Arrays.asList(0.20, 0.22, 0.24));
        require(!review.isAvailable(),
                "corrected final-target measurements must not be presented as an apply-ready proposal yet");
        require(!review.hasWritePlan(),
                "corrected physical catch-up measurements must not create a write plan before conversion validation");
        require(review.getCopyPasteBlock().length() == 0,
                "withheld correction-stage evidence must not expose a tune copy/paste block");
        require(review.getDisplayText().contains("Physical final-target catch-up: median 0.220 s"),
                "corrected physical final-target duration summary is missing");
        require(review.getDisplayText().contains("Measurement repeatability: MEDIUM"),
                "tight three-event measurement group should still report repeatability");
        require(review.getDisplayText().contains("Numerical proposal eligibility: WITHHELD BY DESIGN"),
                "correction stage does not visibly withhold numerical proposal conversion");
        require(review.getDisplayText().contains("previous largest-gap / 90%-catch-up conversion is retired"),
                "retired measurement formula is not explicitly disclosed");
    }

    private static void writePlanIsWithheldRegardlessOfRepeatability() {
        GuidedBlendProposal high = GuidedBlendProposal.build(
                snapshot(), 1, Arrays.asList(0.20, 0.21, 0.22, 0.23, 0.24));
        require(high.getDisplayText().contains("Measurement repeatability: HIGH"),
                "five tight corrected measurements should report high repeatability");
        require(!high.isAvailable() && !high.hasWritePlan(),
                "high repeatability alone re-enabled numerical Blend Duration application");
        require(high.getDisplayText().contains("working curve remains unchanged"),
                "model-review text does not preserve the current curve explicitly");
    }

    private static void outlierStatisticsRemainVisibleForModelReview() {
        GuidedBlendProposal review = GuidedBlendProposal.build(
                snapshot(), 1, Arrays.asList(0.20, 0.21, 0.22, 1.20));
        require(review.getDisplayText().contains("3 retained")
                        && review.getDisplayText().contains("statistical outliers: 1"),
                "IQR outlier evidence was not retained for model review");
        require(!review.hasWritePlan(),
                "outlier-filtered repeatability accidentally produced a write plan");
    }

    private static void typedTrackerGroupsAndDiscardsControlledEvidence() {
        GuidedBlendProposal.Tracker tracker = new GuidedBlendProposal.Tracker();
        tracker.start(snapshot(), 1);
        tracker.observe(outcome(0.20, "A", 1));
        tracker.observe(outcome(0.40, "B", 1));
        tracker.observe(outcome(0.22, "A", 2));
        require(tracker.durationCount() == 2,
                "typed tracker mixed different controlled comparability groups");
        tracker.observe(outcome(0.24, "A", 3));
        require(tracker.durationCount() == 3,
                "third comparable corrected outcome was not retained");
        GuidedBlendProposal review = tracker.evaluate();
        require(!review.isAvailable() && !review.hasWritePlan(),
                "typed corrected measurement group re-enabled an unvalidated numerical proposal");
        require(review.getDisplayText().contains("Measurement group: A | 3 valid event(s)"),
                "model review did not identify the selected controlled group");
        tracker.discardLastAccepted();
        require(tracker.durationCount() == 2,
                "discard did not remove only the latest typed outcome");
        require(!tracker.evaluate().hasWritePlan(),
                "discarded evidence retained a stale write plan");
    }

    private static GuidedOutcome outcome(double duration, String group, int count) {
        return new GuidedOutcome(GuidedOutcome.Decision.VALID,
                10.0 + count, duration, count, group, count, "typed outcome", "trace");
    }

    private static AeProjectSnapshot snapshot() {
        return new AeProjectSnapshot(
                "test",
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0], 0.0, 0.0,
                new double[0], new double[0], false, false, "off", false, true,
                false, false, new double[0][0], new double[0][0],
                new double[]{1000.0}, new double[]{10.0}, new double[][]{{50.0}},
                new double[]{1500.0, 2600.0, 3800.0, 5000.0},
                new double[]{0.30, 0.26, 0.24, 0.18});
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
