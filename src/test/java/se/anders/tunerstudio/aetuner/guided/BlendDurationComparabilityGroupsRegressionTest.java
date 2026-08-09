package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

import java.util.List;

public final class BlendDurationComparabilityGroupsRegressionTest {
    private BlendDurationComparabilityGroupsRegressionTest() { }

    public static void main(String[] args) {
        nearbyAttemptsJoinSameGroup();
        differentRoadLoadCreatesAnotherGroup();
        nearBoundaryWarnsWithoutDiscarding();
        rebuildPreservesBestGroupEvidence();
        System.out.println("BlendDurationComparabilityGroupsRegressionTest passed");
    }

    private static void nearbyAttemptsJoinSameGroup() {
        BlendDurationComparabilityGroups groups =
                new BlendDurationComparabilityGroups();
        BlendDurationComparabilityGroups.Assignment first =
                groups.assign(attempt(1, 2000.0, 50.0, 8.0, 30.0, 20.0, "RISING"));
        BlendDurationComparabilityGroups.Assignment second =
                groups.assign(attempt(2, 2100.0, 54.0, 8.0, 32.0, 22.0, "RISING"));
        require("A".equals(first.groupId) && "A".equals(second.groupId),
                "nearby road events were split into different groups");
        require(second.groupCount == 2 && groups.bestGroupCount() == 2,
                "same-group count changed");
    }

    private static void differentRoadLoadCreatesAnotherGroup() {
        BlendDurationComparabilityGroups groups =
                new BlendDurationComparabilityGroups();
        groups.assign(attempt(1, 2000.0, 50.0, 8.0, 30.0, 20.0, "RISING"));
        BlendDurationComparabilityGroups.Assignment different =
                groups.assign(attempt(2, 2000.0, 62.0, 10.0, 33.0, 22.0, "RISING"));
        require("B".equals(different.groupId),
                "materially different baseline MAP did not create another valid group");
        require(groups.groupCount() == 2,
                "different-load valid event was lost instead of retained separately");
    }

    private static void nearBoundaryWarnsWithoutDiscarding() {
        BlendDurationComparabilityGroups groups =
                new BlendDurationComparabilityGroups();
        groups.assign(attempt(1, 2000.0, 50.0, 8.0, 30.0, 20.0, "RISING"));
        BlendDurationComparabilityGroups.Assignment near =
                groups.assign(attempt(2, 2200.0, 50.0, 8.0, 34.0, 20.0, "RISING"));
        require("A".equals(near.groupId) && near.groupCount == 2,
                "near-boundary event was not retained in its compatible group");
        require(near.nearBoundary,
                "near-boundary advisory flag changed");
        require(near.description.contains("retained but not discarded"),
                "near-boundary warning text no longer preserves valid evidence");
    }

    private static void rebuildPreservesBestGroupEvidence() {
        BlendDurationComparabilityGroups groups =
                new BlendDurationComparabilityGroups();
        BlendDurationAttempt a1 = attempt(1, 2000.0, 50.0, 8.0, 30.0, 20.0, "RISING");
        BlendDurationAttempt b1 = attempt(2, 2000.0, 62.0, 10.0, 33.0, 22.0, "RISING");
        BlendDurationAttempt a2 = attempt(3, 2050.0, 52.0, 8.5, 31.0, 21.0, "RISING");
        java.util.ArrayList<BlendDurationAttempt> retained =
                new java.util.ArrayList<BlendDurationAttempt>();
        retained.add(a1);
        retained.add(b1);
        retained.add(a2);
        groups.rebuild(retained);
        require("A".equals(groups.bestGroupId()) && groups.bestGroupCount() == 2,
                "rebuild changed best-group selection");
        List<BlendDurationAttempt> best = groups.bestAttempts();
        require(best.size() == 2 && best.contains(a1) && best.contains(a2),
                "best-group retained attempts changed after rebuild");
    }

    private static BlendDurationAttempt attempt(int number,
                                                double rpm, double map,
                                                double baseTps, double heldTps,
                                                double gap, String trend) {
        return new BlendDurationAttempt(number, 0.50,
                rpm, map, baseTps, heldTps, gap, trend,
                new BlendDurationCaptureConfig(2000.0, 22.0, 5, 2, false),
                2, 2, false, false);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
