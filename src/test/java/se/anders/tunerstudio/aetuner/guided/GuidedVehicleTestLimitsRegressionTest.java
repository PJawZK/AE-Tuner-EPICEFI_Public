package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.AeTunerPlugin;

import se.anders.tunerstudio.aetuner.host.*;
import se.anders.tunerstudio.aetuner.passive.*;
import se.anders.tunerstudio.aetuner.guided.*;
import se.anders.tunerstudio.aetuner.model.*;
import se.anders.tunerstudio.aetuner.proposal.*;
import se.anders.tunerstudio.aetuner.recovery.*;
import se.anders.tunerstudio.aetuner.ui.*;

public final class GuidedVehicleTestLimitsRegressionTest {
    private GuidedVehicleTestLimitsRegressionTest() { }

    public static void main(String[] args) {
        defaultsAreCandidateValuesAndOverridesAreOff();
        overridePanelIsNotUserVisible();
        localOnlyConfirmationCannotBeLoweredBelowUsableStep();
        activeSessionSnapshotCannotChangeMidRun();
        restoreReturnsToCandidateDefaults();
        System.out.println("GuidedVehicleTestLimitsRegressionTest passed");
    }

    private static void defaultsAreCandidateValuesAndOverridesAreOff() {
        GuidedVehicleTestLimits.restoreCandidateDefaults();
        GuidedVehicleTestLimits.Snapshot defaults =
                GuidedVehicleTestLimits.current();
        require(!defaults.enabled, "test overrides must default OFF");
        close(defaults.detectorConfirmSeconds, 0.55, "detector default");
        close(defaults.targetAcquisitionSeconds, 1.00, "target-acquisition default");
        close(defaults.mapCatchupSeconds, 1.20, "MAP-catch-up default");
        close(defaults.tpsTolerance, 3.00, "TPS-tolerance default");
        close(defaults.tpsBoundaryEpsilon, 0.05, "boundary-epsilon default");
        close(defaults.localTpsOnsetRise, PedalPlateauDetector.MIN_USABLE_STEP,
                "local-only confirmation must match the recipe's minimum usable TPS step");
        require(defaults.summary().contains("local confirmation +10.00 TPS"),
                "candidate-default summary does not expose the local-only confirmation threshold");
    }

    private static void overridePanelIsNotUserVisible() {
        GuidedVehicleTestOverridePanel panel = new GuidedVehicleTestOverridePanel();
        require(!panel.isVisible(),
                "vehicle-test override controls must not be exposed in the normal user-facing workspace");
        require(!panel.isEnabledForTest(),
                "hidden vehicle-test override controls must remain disabled by default");
    }

    private static void localOnlyConfirmationCannotBeLoweredBelowUsableStep() {
        GuidedVehicleTestLimits.configurePending(true,
                0.80, 1.40, 1.70, 4.00, 0.08, 2.50);
        GuidedVehicleTestLimits.Snapshot pending = GuidedVehicleTestLimits.pending();
        close(pending.localTpsOnsetRise, PedalPlateauDetector.MIN_USABLE_STEP,
                "vehicle-test override allowed local-only confirmation below usable TPS step");
        GuidedVehicleTestLimits.restoreCandidateDefaults();
    }

    private static void activeSessionSnapshotCannotChangeMidRun() {
        GuidedVehicleTestLimits.configurePending(true,
                0.80, 1.40, 1.70, 4.00, 0.08, 10.50);
        GuidedVehicleTestLimits.Snapshot active =
                GuidedVehicleTestLimits.beginSession();
        require(active.enabled, "enabled test overrides did not activate");

        GuidedVehicleTestLimits.configurePending(true,
                1.10, 1.90, 2.20, 5.00, 0.12, 12.00);
        GuidedVehicleTestLimits.Snapshot stillActive =
                GuidedVehicleTestLimits.current();
        close(stillActive.detectorConfirmSeconds, 0.80,
                "mid-session pending change altered active detector limit");
        close(stillActive.tpsTolerance, 4.00,
                "mid-session pending change altered active TPS tolerance");
        close(stillActive.localTpsOnsetRise, 10.50,
                "mid-session pending change altered active local confirmation threshold");
        require(stillActive.summary().contains("TEST OVERRIDES ACTIVE"),
                "active override identity was not visible");

        GuidedVehicleTestLimits.endSession();
        GuidedVehicleTestLimits.Snapshot next =
                GuidedVehicleTestLimits.current();
        close(next.detectorConfirmSeconds, 1.10,
                "next-session pending detector value was lost");
        close(next.tpsTolerance, 5.00,
                "next-session pending TPS tolerance was lost");
        close(next.localTpsOnsetRise, 12.00,
                "next-session pending local confirmation value was lost");
    }

    private static void restoreReturnsToCandidateDefaults() {
        GuidedVehicleTestLimits.restoreCandidateDefaults();
        GuidedVehicleTestLimits.Snapshot restored =
                GuidedVehicleTestLimits.current();
        require(!restored.enabled, "restore did not disable overrides");
        require(!restored.hasNonDefaultValues(),
                "restore left non-default values behind");
    }

    private static void close(double actual, double expected, String message) {
        if (Math.abs(actual - expected) > 0.000001) {
            throw new AssertionError(message + ": expected " + expected
                    + " but was " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
