package se.anders.tunerstudio.aetuner.guided;

public final class BlendDurationGuidedSummaryRegressionTest {
    private BlendDurationGuidedSummaryRegressionTest() { }

    public static void main(String[] args) {
        controlledCapturingStateNamesAreVisible();
        repeatabilityTextDoesNotMasqueradeAsProposalQuality();
        completedTargetGroupUsesMedianInsteadOfLastEvent();
        idleSummaryDoesNotPresentPlaceholderAsSelectedTarget();
        activeSummaryUsesActualCaptureContractWording();
        numericalApplyWithholdingAndReadOnlyStatementRemainVisible();
        System.out.println("BlendDurationGuidedSummaryRegressionTest passed");
    }

    private static void controlledCapturingStateNamesAreVisible() {
        GuidedSessionSnapshot opening = snapshot(GuidedCaptureState.CAPTURING, false,
                new BlendDurationComparabilityGroups());
        require("OPENING — FORM STABLE PEDAL PLATEAU".equals(opening.headline),
                "pre-plateau CAPTURING headline reintroduced a fixed TPS-step target");
        GuidedSessionSnapshot held = snapshot(GuidedCaptureState.CAPTURING, true,
                new BlendDurationComparabilityGroups());
        require("STABLE TPS HOLD — MEASURING PHYSICAL MAP RESPONSE".equals(held.headline),
                "post-plateau CAPTURING headline does not describe physical response timing");
    }

    private static void repeatabilityTextDoesNotMasqueradeAsProposalQuality() {
        BlendDurationComparabilityGroups empty = new BlendDurationComparabilityGroups();
        GuidedSessionSnapshot incomplete = snapshot(GuidedCaptureState.READY, false, empty);
        require(incomplete.result.contains("Measurement-group repeatability: INCOMPLETE"),
                "incomplete physical measurement-group wording changed");
        require(!incomplete.result.contains("Proposal-group quality"),
                "retired proposal-quality wording is still visible");

        BlendDurationComparabilityGroups groups = new BlendDurationComparabilityGroups();
        groups.assign(attempt(1, 0.20));
        groups.assign(attempt(2, 0.22));
        groups.assign(attempt(3, 0.24));
        GuidedSessionSnapshot medium = snapshot(GuidedCaptureState.ACCEPTED, true, groups);
        require(medium.result.contains("Measurement-group repeatability: MEDIUM"),
                "three tight comparable physical-response events no longer report MEDIUM repeatability");
        require(medium.result.contains("Measurement group: A 3/5 comparable valid events"),
                "measurement-group count presentation changed");
        require(medium.result.contains("Best-group physical MAP 20→80 response median"),
                "physical measurement name disappeared from summary");
        require(medium.result.contains("Coarse physical timing reference: ~0.22 s")
                        && medium.result.contains("not an ECU proposal"),
                "coarse good-enough timing reference is missing or overstates proposal authority");
    }

    private static void completedTargetGroupUsesMedianInsteadOfLastEvent() {
        BlendDurationComparabilityGroups groups = new BlendDurationComparabilityGroups();
        groups.assign(attempt(1, 0.653));
        groups.assign(attempt(2, 0.627));
        groups.assign(attempt(3, 0.650));
        groups.assign(attempt(4, 0.406));
        groups.assign(attempt(5, 0.163));
        GuidedSessionSnapshot complete = BlendDurationGuidedSummary.snapshot(
                GuidedCaptureState.COMPLETE, true, "instruction", "checks",
                "VALID ROAD EVENT\nPhysical MAP 20->80 response duration: 0.163 s",
                new BlendDurationCaptureConfig(2000.0, 22.0, 5, 2, false),
                5, 0, 0, 5, groups, "trace");
        require(complete.result.startsWith("SERIES RESULT — GROUP A MEDIAN\nPhysical MAP 20→80 response: 0.627 s"),
                "completed five-event series did not promote the group median to the session result");
        require(complete.result.contains("Comparable valid events: 5 | range: 0.163-0.653 s"),
                "completed series result does not retain the group spread around its median");
        require(!complete.result.startsWith("VALID ROAD EVENT"),
                "the last individual event is still promoted as the completed-series result");
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
        require(active.result.contains("Capture RPM entry target: 1500 RPM")
                        && active.result.contains("entry/READY ±300 RPM")
                        && active.result.contains("no post-start RPM ceiling"),
                "active summary does not expose actual RPM entry contract");
        require(active.result.contains("suggested TPS step: +20.0 (coaching only)")
                        && active.result.contains("physically usable +10.0 to +40.0")
                        && active.result.contains("comparable-group TPS spread ≤5.0"),
                "active summary does not separate coaching, physical usability and comparability");
        require(active.result.contains("Primary timing is the event-relative physical MAP 20→80 response")
                        && active.result.contains("Prediction/fallback target evidence is diagnostic only"),
                "summary does not expose physical-versus-prediction authority boundary");
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
