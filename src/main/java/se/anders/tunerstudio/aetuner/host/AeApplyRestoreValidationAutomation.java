package se.anders.tunerstudio.aetuner.host;

import java.util.List;

/**
 * Guarded sequential automation over the already-validated one-setting
 * Apply/Restore engine. This class owns no controller writer and never burns.
 */
public final class AeApplyRestoreValidationAutomation {
    public enum StepStatus {
        PASS,
        FAIL,
        SKIP,
        HALT
    }

    public static final class StepResult {
        public final AeApplyRestoreValidationTargets.Target target;
        public final StepStatus status;
        public final boolean safeToContinue;
        public final String message;

        private StepResult(AeApplyRestoreValidationTargets.Target target,
                           StepStatus status, boolean safeToContinue,
                           String message) {
            this.target = target;
            this.status = status;
            this.safeToContinue = safeToContinue;
            this.message = message == null ? "" : message;
        }
    }

    private final AeApplyRestoreValidationLabModel model;

    public AeApplyRestoreValidationAutomation(AeApplyRestoreValidationLabModel model) {
        if (model == null) throw new IllegalArgumentException("validation Lab model is required");
        this.model = model;
    }

    public List<AeApplyRestoreValidationTargets.Target> remainingTargets() {
        return model.untestedTargets();
    }

    public AeApplyRestoreValidationTargets.Target nextUntestedTarget() {
        List<AeApplyRestoreValidationTargets.Target> remaining = remainingTargets();
        return remaining.isEmpty() ? null : remaining.get(0);
    }

    /**
     * Validate one physical target. PASS and ordinary FAIL always finish with a
     * verified restored working tune before safeToContinue can be true.
     * Pre-write conditions that prevent a meaningful test are SKIP. Any
     * mutation-phase exception, unverified rollback or Restore/readback failure
     * is HALT.
     */
    public StepResult validateOne(AeApplyRestoreValidationTargets.Target target) {
        if (target == null) {
            return result(null, StepStatus.HALT, false,
                    "Automation halted: no validation target was supplied.");
        }
        AeApplyRestoreValidationLabModel.Record existing = model.recordFor(target);
        if (existing == null) {
            return result(target, StepStatus.HALT, false,
                    "Automation halted: target is outside the audited physical inventory: "
                            + target.identity());
        }
        if (existing.getStatus()
                != AeApplyRestoreValidationLabModel.ValidationStatus.NOT_TESTED) {
            return result(target, StepStatus.SKIP, true,
                    "Existing " + existing.getStatus().label
                            + " evidence retained; automation did not re-test "
                            + target.identity() + ".");
        }

        double original;
        double temporary;
        try {
            model.select(target);
            original = model.getCurrentCapturedOriginalValue();
            temporary = chooseSafeAutomaticValue(target, original);
            model.setTemporaryValue(temporary);
        } catch (Exception ex) {
            if (model.isTemporaryApplied()) {
                return result(target, StepStatus.HALT, false,
                        "Automation halted during pre-write setup while a temporary value is reported applied: "
                                + safeMessage(ex));
            }
            recordSkip(target, "AUTO pre-write SKIP: " + safeMessage(ex));
            return result(target, StepStatus.SKIP, true,
                    target.identity() + " skipped before any write: " + safeMessage(ex));
        }

        AeApplyRestoreValidationEngine.OperationResult apply;
        try {
            apply = model.applyTemporary();
        } catch (Exception ex) {
            return result(target, StepStatus.HALT, false,
                    "Automation halted on unexpected Apply exception for "
                            + target.identity() + ": " + safeMessage(ex));
        }

        if (!apply.success) {
            if (model.isTemporaryApplied() || hasUnverifiedRollback(apply.message)) {
                return result(target, StepStatus.HALT, false,
                        "Automation halted: Apply failed and the working tune could not be proven restored for "
                                + target.identity() + ". " + apply.message);
            }
            if (isPreWriteApplyBlock(apply.message)) {
                safeMarkSelected(
                        AeApplyRestoreValidationLabModel.ValidationStatus.SKIP,
                        "AUTO pre-write SKIP: " + apply.message);
                return result(target, StepStatus.SKIP, true,
                        target.identity() + " SKIP: " + apply.message);
            }
            safeMarkSelected(
                    AeApplyRestoreValidationLabModel.ValidationStatus.FAIL,
                    "AUTO Apply/readback FAIL; safety Restore completed or no mutation remained. "
                            + apply.message);
            return result(target, StepStatus.FAIL, true,
                    target.identity() + " FAIL: " + apply.message);
        }

        AeApplyRestoreValidationEngine.OperationResult restore;
        try {
            restore = model.restoreOriginal();
        } catch (Exception ex) {
            return result(target, StepStatus.HALT, false,
                    "Automation halted on unexpected Restore exception for "
                            + target.identity() + ": " + safeMessage(ex));
        }

        if (!restore.success || model.isTemporaryApplied()
                || hasUnverifiedRollback(restore.message)) {
            return result(target, StepStatus.HALT, false,
                    "Automation halted: exact original Restore was not independently verified for "
                            + target.identity() + ". " + restore.message);
        }

        try {
            model.markSelected(
                    AeApplyRestoreValidationLabModel.ValidationStatus.PASS,
                    "AUTO round trip: original " + format(original)
                            + " -> temporary " + format(temporary)
                            + " -> exact original Restore verified.");
        } catch (Exception ex) {
            return result(target, StepStatus.HALT, false,
                    "Automation halted after successful Restore because PASS evidence could not be recorded for "
                            + target.identity() + ": " + safeMessage(ex));
        }
        return result(target, StepStatus.PASS, true,
                target.identity() + " PASS: temporary " + format(temporary)
                        + " applied/read back and original " + format(original)
                        + " restored/read back exactly.");
    }

    private double chooseSafeAutomaticValue(
            AeApplyRestoreValidationTargets.Target target,
            double original) throws Exception {
        if (target.getParameter().getShape()
                != AeTuningParameterCatalog.Shape.CURVE_AXIS || !target.isIndexed()) {
            return AeValidationValuePolicy.chooseAutomaticTemporaryValue(target, original);
        }

        int index = target.getFlatIndex();
        int count = target.getDefinition().elementCount();
        double lower = Double.NEGATIVE_INFINITY;
        double upper = Double.POSITIVE_INFINITY;
        if (index > 0) {
            AeApplyRestoreValidationTargets.Target previous =
                    AeApplyRestoreValidationTargets.find(
                            target.getControllerName(), index - 1);
            if (previous == null) {
                throw new IllegalStateException(
                        "previous curve-axis target is absent from physical inventory");
            }
            lower = model.readLiveValue(previous);
        }
        if (index + 1 < count) {
            AeApplyRestoreValidationTargets.Target next =
                    AeApplyRestoreValidationTargets.find(
                            target.getControllerName(), index + 1);
            if (next == null) {
                throw new IllegalStateException(
                        "next curve-axis target is absent from physical inventory");
            }
            upper = model.readLiveValue(next);
        }
        return AeValidationValuePolicy.chooseAutomaticTemporaryValueWithin(
                target, original, lower, upper);
    }

    static boolean hasUnverifiedRollback(String message) {
        return message != null && message.contains("ROLLBACK COULD NOT BE VERIFIED");
    }

    private static boolean isPreWriteApplyBlock(String message) {
        if (message == null) return false;
        return message.startsWith("Apply blocked before writing:")
                || message.startsWith("Apply failed: Apply blocked by stale working tune");
    }

    private void recordSkip(AeApplyRestoreValidationTargets.Target target, String note) {
        try {
            AeApplyRestoreValidationLabModel.Record selected = model.getSelected();
            if (selected != null
                    && selected.getTarget().identity().equals(target.identity())) {
                model.markSelected(
                        AeApplyRestoreValidationLabModel.ValidationStatus.SKIP, note);
            } else {
                model.markSkipped(target, note);
            }
        } catch (Exception ignored) {
            // The StepResult still reports the pre-write skip. Failure to record
            // evidence must not manufacture a controller mutation.
        }
    }

    private void safeMarkSelected(
            AeApplyRestoreValidationLabModel.ValidationStatus status,
            String note) {
        try {
            model.markSelected(status, note);
        } catch (Exception ignored) {
            // The model already marks failed Apply/Restore operations FAIL.
        }
    }

    private static StepResult result(AeApplyRestoreValidationTargets.Target target,
                                     StepStatus status, boolean safeToContinue,
                                     String message) {
        return new StepResult(target, status, safeToContinue, message);
    }

    private static String safeMessage(Exception ex) {
        if (ex == null) return "unknown error";
        String value = ex.getMessage();
        return value == null || value.length() == 0
                ? ex.getClass().getSimpleName() : value;
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) return "n/a";
        return String.format(java.util.Locale.ROOT, "%.6f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }
}
