package se.anders.tunerstudio.aetuner.host;

import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import com.efiAnalytics.plugin.ecu.ControllerAccess;

import java.util.Arrays;

/**
 * Generic one-setting-at-a-time physical Apply/Restore validation engine.
 *
 * The engine may read any physical target derived from the permanent inventory,
 * but it has no controller write API. Every mutation is expressed as an exact
 * one-change ProposalWritePlan and executed by ProposalApplyCoordinator.
 */
public final class AeApplyRestoreValidationEngine {
    private static final double EPSILON = 0.000001;

    interface ValueReader {
        double read(String configurationName,
                    AeApplyRestoreValidationTargets.Target target) throws Exception;
    }

    public static final class OperationResult {
        public final boolean success;
        public final boolean restore;
        public final double originalValue;
        public final double temporaryValue;
        public final double readbackValue;
        public final String message;

        private OperationResult(boolean success, boolean restore,
                                double originalValue, double temporaryValue,
                                double readbackValue, String message) {
            this.success = success;
            this.restore = restore;
            this.originalValue = originalValue;
            this.temporaryValue = temporaryValue;
            this.readbackValue = readbackValue;
            this.message = message == null ? "" : message;
        }

        static OperationResult success(boolean restore,
                                       double originalValue, double temporaryValue,
                                       double readbackValue, String message) {
            return new OperationResult(true, restore, originalValue,
                    temporaryValue, readbackValue, message);
        }

        static OperationResult failure(boolean restore,
                                       double originalValue, double temporaryValue,
                                       double readbackValue, String message) {
            return new OperationResult(false, restore, originalValue,
                    temporaryValue, readbackValue, message);
        }
    }

    private final String configurationName;
    private final ValueReader reader;
    private final ProposalApplyCoordinator coordinator;

    private AeApplyRestoreValidationTargets.Target selectedTarget;
    private double originalValue = Double.NaN;
    private double temporaryValue = Double.NaN;
    private ProposalWritePlan activePlan;
    private boolean temporaryApplied;

    public AeApplyRestoreValidationEngine(ControllerAccess access,
                                          String configurationName) {
        this(new TunerStudioReader(access),
                new ProposalApplyCoordinator(access), configurationName);
    }

    AeApplyRestoreValidationEngine(ValueReader reader,
                                   ProposalApplyCoordinator coordinator,
                                   String configurationName) {
        if (reader == null) throw new IllegalArgumentException("validation reader is required");
        if (coordinator == null) throw new IllegalArgumentException("apply coordinator is required");
        if (configurationName == null || configurationName.trim().length() == 0) {
            throw new IllegalArgumentException("configuration name is required");
        }
        this.reader = reader;
        this.coordinator = coordinator;
        this.configurationName = configurationName.trim();
    }

    public synchronized double selectTarget(
            AeApplyRestoreValidationTargets.Target target) throws Exception {
        if (temporaryApplied) {
            throw new IllegalStateException(
                    "Restore the currently applied temporary value before selecting another target");
        }
        if (target == null) throw new IllegalArgumentException("validation target is required");
        double live = readLiveValue(target);
        selectedTarget = target;
        originalValue = live;
        temporaryValue = Double.NaN;
        activePlan = null;
        return originalValue;
    }

    /**
     * Read an audited physical target without creating a write plan. Used by
     * validation automation to inspect adjacent curve-axis bins before choosing
     * a temporary value. Reads are blocked while any temporary value is active
     * so neighbor checks always describe the restored working tune.
     */
    public synchronized double readLiveValue(
            AeApplyRestoreValidationTargets.Target target) throws Exception {
        if (temporaryApplied) {
            throw new IllegalStateException(
                    "live validation reads are blocked until the temporary value is restored");
        }
        if (target == null) throw new IllegalArgumentException("validation target is required");
        double live = reader.read(configurationName, target);
        if (!Double.isFinite(live)) {
            throw new IllegalStateException(target.identity()
                    + " returned a non-finite working-tune value");
        }
        return live;
    }

    public synchronized double setTemporaryValue(double requestedValue) {
        requireSelectedTarget();
        if (temporaryApplied) {
            throw new IllegalStateException(
                    "The temporary value is already applied; restore it before changing the test value");
        }
        temporaryValue = AeValidationValuePolicy.requireValidTemporaryValue(
                selectedTarget, originalValue, requestedValue);
        return temporaryValue;
    }

    public synchronized OperationResult applyTemporary() {
        requireSelectedTarget();
        if (!Double.isFinite(temporaryValue)) {
            return OperationResult.failure(false, originalValue, temporaryValue,
                    Double.NaN, "Choose a valid temporary value before Apply.");
        }
        if (temporaryApplied) {
            return OperationResult.failure(false, originalValue, temporaryValue,
                    Double.NaN, "The selected temporary value is already applied.");
        }

        activePlan = oneChangePlan(selectedTarget, originalValue, temporaryValue);
        ProposalApplyCoordinator.ApplyResult apply = coordinator.apply(activePlan);
        if (!apply.success) {
            activePlan = null;
            return OperationResult.failure(false, originalValue, temporaryValue,
                    Double.NaN, apply.message);
        }

        temporaryApplied = true;
        try {
            double readback = reader.read(configurationName, selectedTarget);
            if (!matches(temporaryValue, readback)) {
                return recoverAfterExternalApplyReadbackMismatch(readback);
            }
            return OperationResult.success(false, originalValue, temporaryValue,
                    readback, apply.message + " Independent target readback PASS.");
        } catch (Exception ex) {
            return recoverAfterExternalApplyReadbackFailure(ex);
        }
    }

    public synchronized OperationResult restoreOriginal() {
        requireSelectedTarget();
        if (!temporaryApplied || activePlan == null) {
            return OperationResult.failure(true, originalValue, temporaryValue,
                    Double.NaN, "No temporary validation value is currently applied.");
        }
        if (coordinator.previousApplyPlan() != activePlan) {
            return OperationResult.failure(true, originalValue, temporaryValue,
                    Double.NaN,
                    "Restore blocked: another AE Tuner apply is above this validation target on the restore stack.");
        }

        ProposalApplyCoordinator.ApplyResult restore = coordinator.restorePreviousApply();
        if (!restore.success) {
            return OperationResult.failure(true, originalValue, temporaryValue,
                    Double.NaN, restore.message);
        }

        temporaryApplied = false;
        activePlan = null;
        try {
            double readback = reader.read(configurationName, selectedTarget);
            if (!matches(originalValue, readback)) {
                return OperationResult.failure(true, originalValue, temporaryValue,
                        readback,
                        "Restore coordinator reported PASS but independent target readback did not recover the exact original value.");
            }
            return OperationResult.success(true, originalValue, temporaryValue,
                    readback, restore.message + " Independent original-value readback PASS.");
        } catch (Exception ex) {
            return OperationResult.failure(true, originalValue, temporaryValue,
                    Double.NaN,
                    "Restore coordinator reported PASS but independent target readback failed: "
                            + safeMessage(ex));
        }
    }

    public synchronized AeApplyRestoreValidationTargets.Target getSelectedTarget() {
        return selectedTarget;
    }

    public synchronized double getOriginalValue() { return originalValue; }
    public synchronized double getTemporaryValue() { return temporaryValue; }
    public synchronized boolean isTemporaryApplied() { return temporaryApplied; }
    public synchronized boolean canSelectAnotherTarget() { return !temporaryApplied; }

    public synchronized String statusText() {
        if (selectedTarget == null) return "No physical validation target selected.";
        StringBuilder text = new StringBuilder(selectedTarget.identity())
                .append(" | original ").append(format(originalValue));
        if (Double.isFinite(temporaryValue)) {
            text.append(" | temporary ").append(format(temporaryValue));
        }
        text.append(temporaryApplied ? " | TEMPORARY VALUE APPLIED — RESTORE REQUIRED"
                : " | working tune at captured original state");
        return text.toString();
    }

    private OperationResult recoverAfterExternalApplyReadbackMismatch(double readback) {
        ProposalApplyCoordinator.ApplyResult recovery = coordinator.restorePreviousApply();
        if (recovery.success) {
            temporaryApplied = false;
            activePlan = null;
            return OperationResult.failure(false, originalValue, temporaryValue,
                    readback,
                    "Independent Apply readback mismatch: expected " + temporaryValue
                            + " but read " + readback
                            + ". Automatic safety restore PASS; original value recovered by the production coordinator.");
        }
        return OperationResult.failure(false, originalValue, temporaryValue,
                readback,
                "Independent Apply readback mismatch: expected " + temporaryValue
                        + " but read " + readback
                        + ". AUTOMATIC SAFETY RESTORE FAILED: " + recovery.message);
    }

    private OperationResult recoverAfterExternalApplyReadbackFailure(Exception failure) {
        ProposalApplyCoordinator.ApplyResult recovery = coordinator.restorePreviousApply();
        if (recovery.success) {
            temporaryApplied = false;
            activePlan = null;
            return OperationResult.failure(false, originalValue, temporaryValue,
                    Double.NaN,
                    "Independent Apply readback failed: " + safeMessage(failure)
                            + ". Automatic safety restore PASS.");
        }
        return OperationResult.failure(false, originalValue, temporaryValue,
                Double.NaN,
                "Independent Apply readback failed: " + safeMessage(failure)
                        + ". AUTOMATIC SAFETY RESTORE FAILED: " + recovery.message);
    }

    private ProposalWritePlan oneChangePlan(
            AeApplyRestoreValidationTargets.Target target,
            double before, double after) {
        String label = target.getParameter().getDisplayName();
        if (target.isIndexed()) label += " [" + target.coordinateText() + "]";
        ProposalWritePlan.Change change = target.isIndexed()
                ? ProposalWritePlan.Change.arrayCell(
                        target.getControllerName(), target.getFlatIndex(), before, after,
                        label, target.getDefinition().getUnit())
                : ProposalWritePlan.Change.scalar(
                        target.getControllerName(), before, after,
                        label, target.getDefinition().getUnit());
        return new ProposalWritePlan(
                "apply-restore-validation",
                "Apply/Restore Validation — " + target.getParameter().getDisplayName(),
                configurationName,
                "Physical target " + target.identity()
                        + " | frozen authority "
                        + AeControllerDefinitionCatalog.AUTHORITY_SIGNATURE,
                Arrays.asList(change));
    }

    private void requireSelectedTarget() {
        if (selectedTarget == null) {
            throw new IllegalStateException("Select a physical validation target first");
        }
    }

    private static boolean matches(double expected, double actual) {
        return Double.isFinite(actual) && Math.abs(expected - actual) <= EPSILON;
    }

    private static String safeMessage(Exception ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().length() == 0) {
            return ex == null ? "unknown error" : ex.getClass().getSimpleName();
        }
        return ex.getMessage();
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) return "n/a";
        return String.format(java.util.Locale.ROOT, "%.6f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    private static final class TunerStudioReader implements ValueReader {
        private final AeControllerTargetReader reader;

        TunerStudioReader(ControllerAccess access) {
            this.reader = new AeControllerTargetReader(access);
        }

        @Override
        public double read(String configurationName,
                           AeApplyRestoreValidationTargets.Target target) throws Exception {
            return reader.read(configurationName, target);
        }
    }
}
