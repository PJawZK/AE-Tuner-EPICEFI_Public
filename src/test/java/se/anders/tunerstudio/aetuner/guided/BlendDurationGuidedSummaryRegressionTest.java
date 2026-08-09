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
        preservesCapturingStateNames();
        incompleteAndMediumQualityTextRemainStable();
        readOnlyStatementRemainsVisible();
        System.out.println("BlendDurationGuidedSummaryRegressionTest passed");
    }

    private static void preservesCapturingStateNames() {
        GuidedSessionSnapshot opening = snapshot(
                GuidedCaptureState.CAPTURING, false,
                new BlendDurationComparabilityGroups());
        require("OPENING — LET PEDAL SETTLE".equals(opening.headline),
                "pre-plateau CAPTURING headline changed");
        GuidedSessionSnapshot held = snapshot(
                GuidedCaptureState.CAPTURING, true,
                new BlendDurationComparabilityGroups());
        require("PEDAL HOLD ACQUIRED — HOLD".equals(held.headline),
                "post-plateau CAPTURING headline changed");
    }

    private static void incompleteAndMediumQualityTextRemainStable() {
        BlendDurationComparabilityGroups empty =
                new BlendDurationComparabilityGroups();
        GuidedSessionSnapshot incomplete = snapshot(
                GuidedCaptureState.READY, false, empty);
        require(incomplete.result.contains("Proposal-group quality: INCOMPLETE"),
                "incomplete proposal-group quality text changed");

        BlendDurationComparabilityGroups groups =
                new BlendDurationComparabilityGroups();
        groups.assign(attempt(1, 0.50));
        groups.assign(attempt(2, 0.52));
        groups.assign(attempt(3, 0.54));
        GuidedSessionSnapshot medium = snapshot(
                GuidedCaptureState.ACCEPTED, true, groups);
        require(medium.result.contains("Proposal-group quality: MEDIUM"),
                "three tight comparable events no longer report MEDIUM quality");
        require(medium.result.contains("Proposal group: A 3/5 comparable valid events"),
                "proposal-group count presentation changed");
    }

    private static void readOnlyStatementRemainsVisible() {
        GuidedSessionSnapshot snapshot = snapshot(
                GuidedCaptureState.COMPLETE, true,
                new BlendDurationComparabilityGroups());
        require(snapshot.result.contains(
                        "never writes ECU RAM, burns settings, or removes passive/raw events"),
                "read-only Guided statement disappeared from snapshot summary");
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
