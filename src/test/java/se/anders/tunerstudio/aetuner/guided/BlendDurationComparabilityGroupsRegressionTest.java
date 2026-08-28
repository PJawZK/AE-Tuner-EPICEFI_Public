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
        automaticDetectedGearIsAComparabilityDimension();
        manualGearDoesNotRequireDetectedGearAgreement();
        System.out.println("BlendDurationComparabilityGroupsRegressionTest passed");
    }

    private static void nearbyAttemptsJoinSameGroup() {
        BlendDurationComparabilityGroups groups = new BlendDurationComparabilityGroups();
        BlendDurationComparabilityGroups.Assignment first =
                groups.assign(attempt(1, 2000.0, 50.0, 8.0, 30.0, 20.0, "RISING"));
        BlendDurationComparabilityGroups.Assignment second =
                groups.assign(attempt(2, 2100.0, 54.0, 8.0, 32.0, 22.0, "RISING"));
        require("A".equals(first.groupId) && "A".equals(second.groupId),
                "nearby road events were split into different groups");
        require(second.groupCount == 2 && groups.bestGroupCount() == 2,
                "same-group count changed");
        require(second.description.contains("VALID — ADVANCES LEADING GROUP A")
                        && second.description.contains("2 comparable events"),
                "driver feedback does not identify a valid event that advanced the leading group");
    }

    private static void differentRoadLoadCreatesAnotherGroup() {
        BlendDurationComparabilityGroups groups = new BlendDurationComparabilityGroups();
        groups.assign(attempt(1, 2000.0, 50.0, 8.0, 30.0, 20.0, "RISING"));
        groups.assign(attempt(2, 2050.0, 52.0, 8.5, 31.0, 21.0, "RISING"));
        BlendDurationComparabilityGroups.Assignment different =
                groups.assign(attempt(3, 2000.0, 62.0, 10.0, 33.0, 22.0, "RISING"));
        require("B".equals(different.groupId),
                "materially different baseline MAP did not create another valid group");
        require(groups.groupCount() == 2,
                "different-load valid event was lost instead of retained separately");
        require(different.description.contains("VALID — RETAINED IN DIFFERENT GROUP B")
                        && different.description.contains("leading group A remains at 2 comparable events"),
                "valid alternate-group event no longer tells the driver why the leading series did not advance");
    }

    private static void nearBoundaryWarnsWithoutDiscarding() {
        BlendDurationComparabilityGroups groups = new BlendDurationComparabilityGroups();
        groups.assign(attempt(1, 2000.0, 50.0, 8.0, 30.0, 20.0, "RISING"));
        BlendDurationComparabilityGroups.Assignment near =
                groups.assign(attempt(2, 2200.0, 50.0, 8.0, 34.0, 20.0, "RISING"));
        require("A".equals(near.groupId) && near.groupCount == 2,
                "near-boundary event was not retained in its compatible group");
        require(near.nearBoundary, "near-boundary advisory flag changed");
        require(near.description.contains("retained but not discarded"),
                "near-boundary warning text no longer preserves valid evidence");
    }

    private static void rebuildPreservesBestGroupEvidence() {
        BlendDurationComparabilityGroups groups = new BlendDurationComparabilityGroups();
        BlendDurationAttempt a1 = attempt(1, 2000.0, 50.0, 8.0, 30.0, 20.0, "RISING");
        BlendDurationAttempt b1 = attempt(2, 2000.0, 62.0, 10.0, 33.0, 22.0, "RISING");
        BlendDurationAttempt a2 = attempt(3, 2050.0, 52.0, 8.5, 31.0, 21.0, "RISING");
        java.util.ArrayList<BlendDurationAttempt> retained = new java.util.ArrayList<BlendDurationAttempt>();
        retained.add(a1); retained.add(b1); retained.add(a2);
        groups.rebuild(retained);
        require("A".equals(groups.bestGroupId()) && groups.bestGroupCount() == 2,
                "rebuild changed best-group selection");
        List<BlendDurationAttempt> best = groups.bestAttempts();
        require(best.size() == 2 && best.contains(a1) && best.contains(a2),
                "best-group retained attempts changed after rebuild");
    }

    private static void automaticDetectedGearIsAComparabilityDimension() {
        BlendDurationComparabilityGroups groups = new BlendDurationComparabilityGroups();
        BlendDurationAttempt gear2 = autoAttempt(1, 2);
        BlendDurationAttempt gear3 = autoAttempt(2, 3);
        BlendDurationComparabilityGroups.Assignment a = groups.assign(gear2);
        BlendDurationComparabilityGroups.Assignment b = groups.assign(gear3);
        require("A".equals(a.groupId) && "B".equals(b.groupId),
                "Automatic mode mixed otherwise-identical events from different latched detected gears");
        require(groups.summary().contains("detected gear 2") && groups.summary().contains("detected gear 3"),
                "automatic detected gear is not visible in group summaries");
    }

    private static void manualGearDoesNotRequireDetectedGearAgreement() {
        BlendDurationComparabilityGroups groups = new BlendDurationComparabilityGroups();
        BlendDurationCaptureConfig manual2 = new BlendDurationCaptureConfig(2000.0, 22.0, 5, 2, false);
        BlendDurationAttempt ecu2 = new BlendDurationAttempt(1, 0.20,
                2000.0, 50.0, 8.0, 30.0, 20.0, "RISING",
                manual2, 2, 2, false, false);
        BlendDurationAttempt ecu3 = new BlendDurationAttempt(2, 0.22,
                2010.0, 50.5, 8.0, 30.5, 20.5, "RISING",
                manual2, 3, 3, false, false);
        BlendDurationComparabilityGroups.Assignment first = groups.assign(ecu2);
        BlendDurationComparabilityGroups.Assignment second = groups.assign(ecu3);
        require("A".equals(first.groupId) && "A".equals(second.groupId),
                "manual operator-selected gear was incorrectly rejected by ECU detected-gear mismatch");
        require(second.description.contains("manual 2")
                        && second.description.contains("ECU detected 3")
                        && second.description.contains("informational mismatch"),
                "manual mode does not preserve detected gear as informational export evidence");
    }

    private static BlendDurationAttempt attempt(int number, double rpm, double map,
                                                double baseTps, double heldTps,
                                                double gap, String trend) {
        return new BlendDurationAttempt(number, 0.50, rpm, map, baseTps, heldTps, gap, trend,
                new BlendDurationCaptureConfig(2000.0, 22.0, 5, 2, false),
                2, 2, false, false);
    }

    private static BlendDurationAttempt autoAttempt(int number, int detectedGear) {
        return new BlendDurationAttempt(number, 0.20,
                2000.0, 50.0, 8.0, 30.0, 20.0, "RISING",
                new BlendDurationCaptureConfig(2000.0, 22.0, 5, 0, true),
                detectedGear, detectedGear, false, false);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
