package se.anders.tunerstudio.aetuner.host;

import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;

public final class AeApplyRestoreValidationTargetsRegressionTest {
    private AeApplyRestoreValidationTargetsRegressionTest() { }

    public static void main(String[] args) {
        physicalSurfaceCountIsPinnedAfterInventoryAudit();
        tablesExpandToEveryCellWithCoordinates();
        sharedControllerTargetsArePhysicallyDeduplicated();
        noReadOnlyContextLeaksIntoRequiredPhysicalTargets();
        valuePolicyEnforcesRangeStepEnumAndActualChange();
        System.out.println("AeApplyRestoreValidationTargetsRegressionTest passed");
    }

    private static void physicalSurfaceCountIsPinnedAfterInventoryAudit() {
        require(AeApplyRestoreValidationTargets.physicalTargetCount() == 816,
                "audited unique physical validation surface changed: expected 816 but was "
                        + AeApplyRestoreValidationTargets.physicalTargetCount());
    }

    private static void tablesExpandToEveryCellWithCoordinates() {
        AeApplyRestoreValidationTargets.Target first =
                AeApplyRestoreValidationTargets.find(AeParameterNames.MAP_ESTIMATE_TABLE, 0);
        AeApplyRestoreValidationTargets.Target last =
                AeApplyRestoreValidationTargets.find(AeParameterNames.MAP_ESTIMATE_TABLE, 255);
        require(first != null && last != null,
                "16x16 MAP Estimate table did not expand to all 256 physical cells");
        requireCoordinates(first, 0, 0);
        requireCoordinates(AeApplyRestoreValidationTargets.find(
                AeParameterNames.MAP_ESTIMATE_TABLE, 17), 1, 1);
        requireCoordinates(last, 15, 15);
        require(AeApplyRestoreValidationTargets.find(
                AeParameterNames.MAP_ESTIMATE_TABLE, 256) == null,
                "MAP Estimate table exposed an out-of-range physical cell");
    }

    private static void sharedControllerTargetsArePhysicallyDeduplicated() {
        AeApplyRestoreValidationTargets.Target cycle0 =
                AeApplyRestoreValidationTargets.find(AeParameterNames.TPS_AE_CYCLE_CYCLE_BINS, 0);
        require(cycle0 != null, "shared TPS AE cycle-axis target is missing");
        require(cycle0.getTasks().contains(GuidedTuningRecipe.TPS_AE),
                "shared cycle-axis target lost Fuel-by-Engine-Cycle ownership");
        require(cycle0.getTasks().contains(GuidedTuningRecipe.TPS_AE_COMPLETION),
                "shared cycle-axis target lost Completion ownership");
        require(cycle0.getTasks().size() == 2,
                "shared cycle-axis physical target was duplicated instead of retaining task associations");
    }

    private static void noReadOnlyContextLeaksIntoRequiredPhysicalTargets() {
        require(AeApplyRestoreValidationTargets.find(AeParameterNames.TPS_AE_DETECT_MODE, -1) == null,
                "read-only Guided engagement-model context became a required physical write target");
        require(AeApplyRestoreValidationTargets.find(AeParameterNames.TPS_ACCEL_LOOKBACK, -1) == null,
                "read-only Guided sample-length context became a required physical write target");
        require(AeApplyRestoreValidationTargets.find(AeParameterNames.TPS_AE_FAST_CALLBACK, -1) == null,
                "read-only Guided callback context became a required physical write target");
        require(AeApplyRestoreValidationTargets.find(AeParameterNames.TPS_AE_DELTA_WINDOW_MS, -1) != null,
                "qualified Delta Window physical target is missing");
    }

    private static void valuePolicyEnforcesRangeStepEnumAndActualChange() {
        AeApplyRestoreValidationTargets.Target deltaWindow =
                AeApplyRestoreValidationTargets.find(AeParameterNames.TPS_AE_DELTA_WINDOW_MS, -1);
        requireClose(60.0, AeValidationValuePolicy.requireValidTemporaryValue(
                deltaWindow, 50.0, 60.0), "valid Delta Window rejected");
        requireThrows(() -> AeValidationValuePolicy.requireValidTemporaryValue(
                deltaWindow, 50.0, 4.0), "below-range Delta Window must be rejected");
        requireThrows(() -> AeValidationValuePolicy.requireValidTemporaryValue(
                deltaWindow, 50.0, 50.0), "no-op temporary value must be rejected");

        AeApplyRestoreValidationTargets.Target blend =
                AeApplyRestoreValidationTargets.find(
                        AeParameterNames.PREDICTIVE_MAP_BLEND_DURATION_VALUES, 0);
        requireClose(0.20, AeValidationValuePolicy.requireValidTemporaryValue(
                blend, 0.18, 0.20), "representable blend duration rejected");
        requireThrows(() -> AeValidationValuePolicy.requireValidTemporaryValue(
                blend, 0.18, 0.19), "off-grid U08 engineering value must be rejected");

        AeApplyRestoreValidationTargets.Target wallModel =
                AeApplyRestoreValidationTargets.find(AeParameterNames.WALL_MODEL_TYPE, -1);
        requireClose(1.0, AeValidationValuePolicy.requireValidTemporaryValue(
                wallModel, 0.0, 1.0), "valid Wall Model enum state rejected");
    }

    private static void requireCoordinates(AeApplyRestoreValidationTargets.Target target,
                                           int... expected) {
        require(target != null, "expected physical target is missing");
        int[] actual = target.getCoordinates();
        require(actual.length == expected.length,
                "coordinate rank changed for " + target.identity());
        for (int i = 0; i < expected.length; i++) {
            require(actual[i] == expected[i],
                    "coordinate changed for " + target.identity() + " at dimension " + i);
        }
    }

    private static void requireThrows(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.000001) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
