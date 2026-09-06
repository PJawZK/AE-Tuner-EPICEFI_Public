package se.anders.tunerstudio.aetuner.host;

import se.anders.tunerstudio.aetuner.guided.GuidedTuningArea;
import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;

import com.efiAnalytics.plugin.ecu.ControllerAccess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Session-local model for the temporary physical Apply/Restore Validation Lab.
 *
 * Results are keyed by unique physical target identity. A target may be shown
 * under more than one Guided task, but it is validated and recorded once.
 */
public final class AeApplyRestoreValidationLabModel {
    public enum ValidationStatus {
        NOT_TESTED("NOT TESTED"),
        PASS("PASS"),
        FAIL("FAIL"),
        SKIP("SKIP");

        public final String label;

        ValidationStatus(String label) { this.label = label; }

        @Override public String toString() { return label; }
    }

    public static final class Record {
        private final AeApplyRestoreValidationTargets.Target target;
        private ValidationStatus status = ValidationStatus.NOT_TESTED;
        private String note = "";
        private double originalValue = Double.NaN;
        private double temporaryValue = Double.NaN;
        private double applyReadback = Double.NaN;
        private double restoreReadback = Double.NaN;
        private String message = "Not tested in this plugin session.";

        private Record(AeApplyRestoreValidationTargets.Target target) {
            this.target = target;
        }

        public AeApplyRestoreValidationTargets.Target getTarget() { return target; }
        public ValidationStatus getStatus() { return status; }
        public String getNote() { return note; }
        public double getOriginalValue() { return originalValue; }
        public double getTemporaryValue() { return temporaryValue; }
        public double getApplyReadback() { return applyReadback; }
        public double getRestoreReadback() { return restoreReadback; }
        public String getMessage() { return message; }
    }

    private final AeApplyRestoreValidationEngine engine;
    private final Map<String, Record> records = new LinkedHashMap<String, Record>();
    private Record selected;

    public AeApplyRestoreValidationLabModel(ControllerAccess access,
                                            String configurationName) {
        this(new AeApplyRestoreValidationEngine(access, configurationName));
    }

    AeApplyRestoreValidationLabModel(AeApplyRestoreValidationEngine engine) {
        if (engine == null) throw new IllegalArgumentException("validation engine is required");
        this.engine = engine;
        for (AeApplyRestoreValidationTargets.Target target
                : AeApplyRestoreValidationTargets.all()) {
            records.put(target.identity(), new Record(target));
        }
    }

    public synchronized List<AeApplyRestoreValidationTargets.Target> targetsForTask(
            GuidedTuningRecipe task) {
        if (task == null) return Collections.emptyList();
        List<AeApplyRestoreValidationTargets.Target> result =
                new ArrayList<AeApplyRestoreValidationTargets.Target>();
        for (Record record : records.values()) {
            if (record.target.getTasks().contains(task)) result.add(record.target);
        }
        return Collections.unmodifiableList(result);
    }

    /** Unique physical targets that still have no final result in this session. */
    public synchronized List<AeApplyRestoreValidationTargets.Target> untestedTargets() {
        List<AeApplyRestoreValidationTargets.Target> result =
                new ArrayList<AeApplyRestoreValidationTargets.Target>();
        for (Record record : records.values()) {
            if (record.status == ValidationStatus.NOT_TESTED) result.add(record.target);
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized Record select(
            AeApplyRestoreValidationTargets.Target target) throws Exception {
        if (target == null) throw new IllegalArgumentException("validation target is required");
        Record record = records.get(target.identity());
        if (record == null) {
            throw new IllegalArgumentException(
                    "target is outside the audited physical validation inventory: "
                            + target.identity());
        }
        double original = engine.selectTarget(target);

        // Browsing must never erase completed physical evidence. The current
        // live value is captured by the engine for stale checking, but recorded
        // round-trip evidence remains intact until the operator deliberately
        // starts a new test by entering a new temporary value.
        if (record.status == ValidationStatus.NOT_TESTED
                && !Double.isFinite(record.applyReadback)
                && !Double.isFinite(record.restoreReadback)) {
            record.originalValue = original;
            record.message = "Original working-tune value captured. Choose one valid temporary value.";
        } else {
            record.message = "Previous " + record.status.label
                    + " evidence retained while browsing. Current working-tune value captured for an optional re-test.";
        }
        selected = record;
        return record;
    }

    /** Read another audited target while the working tune is in restored state. */
    public synchronized double readLiveValue(
            AeApplyRestoreValidationTargets.Target target) throws Exception {
        return engine.readLiveValue(target);
    }

    public synchronized double setTemporaryValue(double value) {
        Record record = requireSelected();
        double validated = engine.setTemporaryValue(value);

        // A new temporary value is the explicit start of a re-test. Only here
        // do we replace prior evidence/status with the newly captured baseline.
        record.originalValue = engine.getOriginalValue();
        record.temporaryValue = validated;
        record.applyReadback = Double.NaN;
        record.restoreReadback = Double.NaN;
        record.status = ValidationStatus.NOT_TESTED;
        record.note = "";
        record.message = "Temporary value accepted. Apply writes working tune only; no burn.";
        return validated;
    }

    public synchronized AeApplyRestoreValidationEngine.OperationResult applyTemporary() {
        Record record = requireSelected();
        AeApplyRestoreValidationEngine.OperationResult result = engine.applyTemporary();
        record.originalValue = result.originalValue;
        record.temporaryValue = result.temporaryValue;
        record.applyReadback = result.readbackValue;
        record.message = result.message;
        if (!result.success) record.status = ValidationStatus.FAIL;
        return result;
    }

    public synchronized AeApplyRestoreValidationEngine.OperationResult restoreOriginal() {
        Record record = requireSelected();
        AeApplyRestoreValidationEngine.OperationResult result = engine.restoreOriginal();
        record.originalValue = result.originalValue;
        record.temporaryValue = result.temporaryValue;
        record.restoreReadback = result.readbackValue;
        record.message = result.message;
        if (!result.success) record.status = ValidationStatus.FAIL;
        return result;
    }

    public synchronized void markSelected(ValidationStatus status, String note) {
        Record record = requireSelected();
        if (engine.isTemporaryApplied()) {
            throw new IllegalStateException(
                    "Restore the temporary value before recording a final validation status");
        }
        if (status == null) throw new IllegalArgumentException("validation status is required");
        if (status == ValidationStatus.PASS) {
            if (!Double.isFinite(record.applyReadback)
                    || !Double.isFinite(record.restoreReadback)) {
                throw new IllegalStateException(
                        "PASS requires verified Apply and Restore readback values");
            }
            if (!same(record.temporaryValue, record.applyReadback)
                    || !same(record.originalValue, record.restoreReadback)) {
                throw new IllegalStateException(
                        "PASS requires exact temporary Apply and exact original Restore readbacks");
            }
        }
        record.status = status;
        record.note = note == null ? "" : note.trim();
        record.message = status.label + (record.note.length() == 0
                ? " recorded." : " recorded: " + record.note);
    }

    /**
     * Record an untested target as SKIP when automation cannot even establish a
     * safe pre-write test (for example an unreadable target or no alternative
     * representable value). This method cannot be used while any temporary
     * value is applied and never overwrites an existing final result.
     */
    public synchronized void markSkipped(
            AeApplyRestoreValidationTargets.Target target, String note) {
        if (engine.isTemporaryApplied()) {
            throw new IllegalStateException(
                    "cannot mark SKIP while a temporary validation value is applied");
        }
        if (target == null) throw new IllegalArgumentException("validation target is required");
        Record record = records.get(target.identity());
        if (record == null) {
            throw new IllegalArgumentException(
                    "target is outside the audited physical validation inventory: "
                            + target.identity());
        }
        if (record.status != ValidationStatus.NOT_TESTED) {
            throw new IllegalStateException(
                    "SKIP cannot overwrite existing " + record.status.label
                            + " evidence for " + target.identity());
        }
        record.status = ValidationStatus.SKIP;
        record.note = note == null ? "" : note.trim();
        record.message = "SKIP recorded"
                + (record.note.length() == 0 ? "." : ": " + record.note);
    }

    public synchronized Record getSelected() { return selected; }

    /** Current live baseline captured by the engine for the selected target. */
    public synchronized double getCurrentCapturedOriginalValue() {
        return selected == null ? Double.NaN : engine.getOriginalValue();
    }

    public synchronized Record recordFor(
            AeApplyRestoreValidationTargets.Target target) {
        return target == null ? null : records.get(target.identity());
    }

    public synchronized boolean isTemporaryApplied() {
        return engine.isTemporaryApplied();
    }

    public synchronized String engineStatusText() { return engine.statusText(); }

    public synchronized int totalCount() { return records.size(); }

    public synchronized int count(ValidationStatus status) {
        int count = 0;
        for (Record record : records.values()) {
            if (record.status == status) count++;
        }
        return count;
    }

    public synchronized String summaryText() {
        return "Physical targets " + totalCount()
                + " | PASS " + count(ValidationStatus.PASS)
                + " | FAIL " + count(ValidationStatus.FAIL)
                + " | SKIP " + count(ValidationStatus.SKIP)
                + " | NOT TESTED " + count(ValidationStatus.NOT_TESTED);
    }

    public synchronized List<GuidedTuningArea> areasWithTargets() {
        List<GuidedTuningArea> result = new ArrayList<GuidedTuningArea>();
        for (GuidedTuningArea area : GuidedTuningArea.values()) {
            boolean found = false;
            for (GuidedTuningRecipe task : area.tasks()) {
                if (!targetsForTask(task).isEmpty()) {
                    found = true;
                    break;
                }
            }
            if (found) result.add(area);
        }
        return Collections.unmodifiableList(result);
    }

    private Record requireSelected() {
        if (selected == null) throw new IllegalStateException("select a validation target first");
        return selected;
    }

    private static boolean same(double expected, double actual) {
        return Double.isFinite(expected) && Double.isFinite(actual)
                && Math.abs(expected - actual) <= 0.000001;
    }
}
