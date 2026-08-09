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
        exposesActualProjectAxisAndRegions();
        tightGuidedSeriesChangesOnlySelectedPoint();
        insufficientAndWideEvidenceRemainWithheld();
        statisticalOutlierHandlingMatchesPassiveRules();
        typedTrackerGroupsAndDiscardsAdaptiveEvidence();
        System.out.println("GuidedBlendProposalRegressionTest passed");
    }

    private static void exposesActualProjectAxisAndRegions() {
        List<GuidedBlendProposal.PointChoice> points =
                GuidedBlendProposal.points(snapshot());
        require(points.size() == 4, "Actual project axis was not exposed");
        GuidedBlendProposal.PointChoice point = points.get(1);
        require(point.index == 1 && Math.abs(point.rpm - 2450.0) < 0.001,
                "2450 RPM point identity was not preserved");
        require(point.contains(2000.0),
                "2000 RPM should belong to the 2450 RPM assignment region");
        require(!point.contains(3500.0),
                "3500 RPM should not belong to the 2450 RPM assignment region");
        require(point.regionText().contains("1525")
                        && point.regionText().contains("3400"),
                "Actual midpoint assignment region was not displayed");
    }

    private static void tightGuidedSeriesChangesOnlySelectedPoint() {
        GuidedBlendProposal proposal = GuidedBlendProposal.build(
                snapshot(), 1, Arrays.asList(0.50, 0.52, 0.54));
        require(proposal.isAvailable(),
                "Three tight comparable events should produce a proposal");
        require(proposal.getDisplayText().contains("Confidence: MEDIUM"),
                "Three-event tight series should be medium confidence");
        require(proposal.getDisplayText().contains("0.26 -> 0.54 s"),
                "Median plus final-only margin was not applied");
        String[] values = proposal.getCopyPasteBlock().split("\\n");
        require(values.length == 4, "Full curve was not preserved in copy block");
        require("0.18".equals(values[0]), "Unsupported 600 RPM point changed");
        require("0.54".equals(values[1]), "Selected 2450 RPM point was not proposed");
        require("0.22".equals(values[2]), "Unsupported 4350 RPM point changed");
        require("0.18".equals(values[3]), "Unsupported 6200 RPM point changed");
    }

    private static void insufficientAndWideEvidenceRemainWithheld() {
        GuidedBlendProposal insufficient = GuidedBlendProposal.build(
                snapshot(), 1, Arrays.asList(0.50, 0.52));
        require(!insufficient.isAvailable(),
                "Two events must not produce a proposal");
        require(insufficient.getDisplayText().contains("need at least 3"),
                "Minimum-event reason was not displayed");

        GuidedBlendProposal wide = GuidedBlendProposal.build(
                snapshot(), 1, Arrays.asList(0.30, 0.50, 0.70));
        require(!wide.isAvailable(),
                "Wide measured duration spread must remain withheld");
        require(wide.getDisplayText().contains("range exceeds 0.18 s"),
                "Wide-range rejection reason was not displayed");
    }

    private static void statisticalOutlierHandlingMatchesPassiveRules() {
        GuidedBlendProposal proposal = GuidedBlendProposal.build(
                snapshot(), 1, Arrays.asList(0.50, 0.51, 0.52, 1.20));
        require(proposal.isAvailable(),
                "One IQR outlier should not invalidate three tight retained events");
        require(proposal.getDisplayText().contains("3 retained")
                        && proposal.getDisplayText().contains("statistical outliers: 1"),
                "IQR outlier evidence was not reported");
        require(proposal.getDisplayText().contains("0.26 -> 0.53 s"),
                "Retained median plus 0.02 s margin was not used");
    }

    private static void typedTrackerGroupsAndDiscardsAdaptiveEvidence() {
        GuidedBlendProposal.Tracker tracker = new GuidedBlendProposal.Tracker();
        tracker.start(snapshot(), 1);
        tracker.observe(outcome(0.50, "A", 1));
        tracker.observe(outcome(0.70, "B", 1));
        tracker.observe(outcome(0.52, "A", 2));
        require(tracker.durationCount() == 2,
                "typed tracker mixed different adaptive groups");
        require(!tracker.evaluate().isAvailable(),
                "two-event best group must remain withheld");

        tracker.observe(outcome(0.54, "A", 3));
        require(tracker.durationCount() == 3,
                "third comparable typed outcome was not retained");
        require(tracker.evaluate().isAvailable(),
                "three tight typed outcomes should produce a proposal");
        require(tracker.evaluate().getDisplayText().contains(
                        "Proposal group: A | 3 valid event(s)"),
                "proposal did not identify the typed adaptive group");

        tracker.discardLastAccepted();
        require(tracker.durationCount() == 2,
                "discard did not remove only the latest typed outcome");
        require(!tracker.evaluate().isAvailable(),
                "discarded typed evidence still influenced eligibility");
    }

    private static GuidedOutcome outcome(double duration, String group, int count) {
        return new GuidedOutcome(GuidedOutcome.Decision.VALID,
                10.0 + count, duration, count, group, count,
                "typed outcome", "trace");
    }

    private static AeProjectSnapshot snapshot() {
        return new AeProjectSnapshot(
                "test",
                new double[0], new double[0], new double[0][0],
                new double[0], new double[0],
                0.0, 0.0,
                new double[0], new double[0],
                false, false, "off", false, true,
                false, false,
                new double[0][0], new double[0][0],
                new double[]{1000.0}, new double[]{10.0},
                new double[][]{{50.0}},
                new double[]{600.0, 2450.0, 4350.0, 6200.0},
                new double[]{0.18, 0.26, 0.22, 0.18});
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
