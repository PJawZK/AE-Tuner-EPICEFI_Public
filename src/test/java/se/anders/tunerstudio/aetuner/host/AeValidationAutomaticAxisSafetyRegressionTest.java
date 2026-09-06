package se.anders.tunerstudio.aetuner.host;

public final class AeValidationAutomaticAxisSafetyRegressionTest {
    private AeValidationAutomaticAxisSafetyRegressionTest() { }

    public static void main(String[] args) {
        oneStepAxisChangeStaysStrictlyInsideNeighbors();
        tightAxisGapHasNoAutomatedTemporaryValue();
        lastAxisPointReversesDirectionAtControllerMaximum();
        unverifiedRollbackIsNeverSafeToContinue();
        System.out.println("AeValidationAutomaticAxisSafetyRegressionTest passed");
    }

    private static void oneStepAxisChangeStaysStrictlyInsideNeighbors() {
        AeApplyRestoreValidationTargets.Target target = target(
                AeParameterNames.TPS_AE_CYCLE_CYCLE_BINS, 1);
        double chosen = AeValidationValuePolicy.chooseAutomaticTemporaryValueWithin(
                target, 2.0, 0.0, 4.0);
        requireClose(3.0, chosen,
                "axis automation did not choose one representable step inside neighbors");
    }

    private static void tightAxisGapHasNoAutomatedTemporaryValue() {
        AeApplyRestoreValidationTargets.Target target = target(
                AeParameterNames.TPS_AE_CYCLE_CYCLE_BINS, 0);
        requireThrows(() -> AeValidationValuePolicy.chooseAutomaticTemporaryValueWithin(
                        target, 0.0, Double.NEGATIVE_INFINITY, 1.0),
                "axis automation allowed a temporary value equal to/crossing the next bin");
    }

    private static void lastAxisPointReversesDirectionAtControllerMaximum() {
        AeApplyRestoreValidationTargets.Target target = target(
                AeParameterNames.TPS_AE_CYCLE_TPS_TO_BINS, 7);
        double chosen = AeValidationValuePolicy.chooseAutomaticTemporaryValueWithin(
                target, 100.0, 86.0, Double.POSITIVE_INFINITY);
        requireClose(99.5, chosen,
                "last TPS axis point did not reverse one storage step at maximum");
    }

    private static void unverifiedRollbackIsNeverSafeToContinue() {
        require(AeApplyRestoreValidationAutomation.hasUnverifiedRollback(
                        "Apply failed: readback mismatch. ROLLBACK COULD NOT BE VERIFIED: synthetic"),
                "automation did not detect unverified rollback as a hard halt condition");
        require(!AeApplyRestoreValidationAutomation.hasUnverifiedRollback(
                        "Apply failed: readback mismatch. Best-effort rollback PASS."),
                "verified rollback was incorrectly classified as an unverified rollback");
    }

    private static AeApplyRestoreValidationTargets.Target target(
            String controllerName, int flatIndex) {
        AeApplyRestoreValidationTargets.Target target =
                AeApplyRestoreValidationTargets.find(controllerName, flatIndex);
        if (target == null) throw new AssertionError("missing target " + controllerName);
        return target;
    }

    private static void requireThrows(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalStateException expected) {
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
