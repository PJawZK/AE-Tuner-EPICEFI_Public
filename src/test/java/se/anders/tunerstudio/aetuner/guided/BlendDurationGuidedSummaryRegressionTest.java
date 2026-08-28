package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;
import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

public final class BlendDurationGuidedSummaryRegressionTest {
    private BlendDurationGuidedSummaryRegressionTest() { }

    public static void main(String[] args) {
        controlledCapturingStateNamesAreVisible();
        repeatabilityTextDoesNotMasqueradeAsProposalQuality();
        idleSummaryDoesNotPresentPlaceholderAsSelectedTarget();
        activeSummaryUsesActualCaptureContractWording();
        numericalApplyWithholdingAndReadOnlyStatementRemainVisible();
        System.out.println("BlendDurationGuidedSummaryRegressionTest passed");
    }

    private static void controlledCapturingStateNamesAreVisible() {
        GuidedSessionSnapshot opening = snapshot(GuidedCaptureState.CAPTURING, false,
                new BlendDurationComparabilityGroups());
        require("OPENING — SETTLE INSIDE TARGET STEP".equals(opening.headline),
                "pre-plateau CAPTURING headline does not describe controlled TPS-step targeting");
        GuidedSessionSnapshot held = snapshot(GuidedCaptureState.CAPTURING, true,
                new BlendDurationComparabilityGroups());
        require("TARGET TPS HOLD ACQUIRED — HOLD".equals(held.headline),
                "post-plateau CAPTURING headline does not describe the controlled hold");
    }

    private static void repeatabilityTextDoesNotMasqueradeAsProposalQuality() {
        BlendDurationComparabilityGroups empty = new BlendDurationComparabilityGroups();
        GuidedSessionSnapshot incomplete = snapshot(GuidedCaptureState.READY, false, empty);
        require(incomplete.result.contains("Measurement-group repeatability: INCOMPLETE"),
                "incomplete corrected measurement-group wording changed");
        require(!incomplete.result.contains("Proposal-group quality"),
                "retired proposal-quality wording is still visible");

        BlendDurationComparabilityGroups groups = new BlendDurationComparabilityGroups();
        groups.assign(attempt(1, 0.20));
        groups.assign(attempt(2, 0.22));
        groups.assign(attempt(3, 0.24));
        GuidedSessionSnapshot medium = snapshot(GuidedCaptureState.ACCEPTED, true, groups);
        require(medium.result.contains("Measurement-group repeatability: MEDIUM"),
                "three tight comparable final-target events no longer report MEDIUM repeatability");
        require(medium.result.contains("Measurement group: A 3/5 comparable valid events"),
                "measurement-group count presentation changed");
        require(medium.result.contains("Best-group final-target catch-up median"),
                "corrected physical measurement name disappeared from summary");
    }

    private static void idleSummaryDoesNotPresentPlaceholderAsSelectedTarget() {
        GuidedSessionSnapshot idle = snapshot(GuidedCaptureState.IDLE, false,
                new BlendDurationComparabilityGroups());
        require(idle.result.contains("Tuning task: Predictive MAP Blend Duration"),
                "Guided summary does not use the user-facing tuning-task name");
        require(!idle.result.contains("Adaptive Predictive MAP Blend Duration"),
                "internal adaptive capture wording is still presented as the tuning method");
        require(!idle.result.contains("Capture RPM target: 2000"),
                "idle placeholder RPM is still presented as an armed/selected capture target");
        require(idle.result.contains("Session setup: not armed"),
                "idle summary does not explain that Start Capture arms the current controls");
    }

    private static void activeSummaryUsesActualCaptureContractWording() {
        GuidedSessionSnapshot active = BlendDurationGuidedSummary.snapshot(
                GuidedCaptureState.READY, false, "instruction", "checks", "latest",
                new BlendDurationCaptureConfig(1500.0, 20.0, 5, 0, false),
                0, 0, 0, 0, new BlendDurationComparabilityGroups(), "trace");
        require(active.result.contains("Capture RPM target: 1500 RPM"),
                "active summary does not identify the actual selected table-bin target");
        require(active.result.contains("READY/acquire ±200 RPM"),
                "active summary does not show the authoritative READY/acquisition window");
        require(active.result.contains("active capture ±300 RPM"),
                "active summary does not show the authoritative active-capture window");
        require(active.result.contains("target TPS step: +20.0 | accepted +10.0 to +30.0"),
                "active summary does not expose the dev5 controlled TPS-step window");
    }

    private static void numericalApplyWithholdingAndReadOnlyStatementRemainVisible() {
        GuidedSessionSnapshot state = snapshot(GuidedCaptureState.COMPLETE, true,
                new BlendDurationComparabilityGroups());
        require(state.result.contains("Numerical Blend Duration proposal/apply is intentionally withheld"),
                "correction-stage numerical apply withholding disappeared from summary");
        require(state.result.contains("never writes ECU RAM, burns settings, or removes passive/raw events"),
                "read-only Guided measurement statement disappeared from snapshot summary");
    }

    private static GuidedSessionSnapshot snapshot(GuidedCaptureState state,
                                                   boolean plateau,
                                                   BlendDurationComparabilityGroups groups) {
        return BlendDurationGuidedSummary.snapshot(state, plateau,
                "instruction", "checks", "latest",
                new BlendDurationCaptureConfig(2000.0, 22.0, 5, 2, false),
                groups.bestGroupCount(), 1, 2, 3, groups, "trace");
    }

    private static BlendDurationAttempt attempt(int number, double duration) {
        return new BlendDurationAttempt(number, duration,
                2000.0 + number * 10.0, 50.0 + number * 0.2,
                8.0, 30.0, 20.0, "RISING",
                new BlendDurationCaptureConfig(2000.0, 22.0, 5, 2, false),
                2, 2, false, false);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
