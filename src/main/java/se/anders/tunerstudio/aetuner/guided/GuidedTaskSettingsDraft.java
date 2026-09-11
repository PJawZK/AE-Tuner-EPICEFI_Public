package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.host.AeApplyRestoreValidationTargets;
import se.anders.tunerstudio.aetuner.host.AeControllerDefinitionCatalog;
import se.anders.tunerstudio.aetuner.host.AeControllerTargetReader;
import se.anders.tunerstudio.aetuner.host.AeTuningParameterCatalog;
import se.anders.tunerstudio.aetuner.host.AeValidationValuePolicy;
import se.anders.tunerstudio.aetuner.host.GuidedControllerSettingInventory;
import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;

import com.efiAnalytics.plugin.ecu.ControllerAccess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read/review model for a selected Guided task's physically validated settings.
 *
 * This class has no controller writer. It captures live values through the
 * read-only canonical target reader and can only build a ProposalWritePlan.
 * The existing Guided ProposalApplyCoordinator remains the sole mutation path.
 */
public final class GuidedTaskSettingsDraft {
    interface ValueReader {
        double read(String configurationName,
                    AeApplyRestoreValidationTargets.Target target) throws Exception;
    }

    public static final class Entry {
        private final AeApplyRestoreValidationTargets.Target target;
        private final double originalValue;
        private double proposedValue;

        private Entry(AeApplyRestoreValidationTargets.Target target,
                      double originalValue) {
            this.target = target;
            this.originalValue = originalValue;
            this.proposedValue = originalValue;
        }

        public AeApplyRestoreValidationTargets.Target getTarget() { return target; }
        public double getOriginalValue() { return originalValue; }
        public double getProposedValue() { return proposedValue; }
        public boolean isChanged() { return !same(originalValue, proposedValue); }
    }

    private final GuidedTuningRecipe task;
    private final String configurationName;
    private final List<Entry> entries;
    private final Map<String, Entry> byIdentity;

    public static GuidedTaskSettingsDraft capture(ControllerAccess access,
                                                   String configurationName,
                                                   GuidedTuningRecipe task)
            throws Exception {
        final AeControllerTargetReader reader = new AeControllerTargetReader(access);
        return capture(new ValueReader() {
            @Override public double read(String config,
                                         AeApplyRestoreValidationTargets.Target target)
                    throws Exception {
                return reader.read(config, target);
            }
        }, configurationName, task);
    }

    static GuidedTaskSettingsDraft capture(ValueReader reader,
                                           String configurationName,
                                           GuidedTuningRecipe task)
            throws Exception {
        if (reader == null) throw new IllegalArgumentException("task settings reader is required");
        if (configurationName == null || configurationName.trim().length() == 0) {
            throw new IllegalArgumentException("configuration name is required");
        }
        GuidedControllerSettingInventory.TaskInventory inventory =
                GuidedControllerSettingInventory.find(task);
        if (inventory == null) throw new IllegalArgumentException("unknown Guided task " + task);
        if (!inventory.hasWriteTargets()) {
            throw new IllegalArgumentException(task + " has no direct controller write targets");
        }
        if (inventory.getProductionWriteSupport()
                != GuidedControllerSettingInventory.ProductionWriteSupport.CURRENT) {
            throw new IllegalStateException(task
                    + " does not have current production Apply/Restore support");
        }

        List<Entry> entries = new ArrayList<Entry>();
        for (AeApplyRestoreValidationTargets.Target target
                : AeApplyRestoreValidationTargets.all()) {
            if (!target.getTasks().contains(task)) continue;
            double current = reader.read(configurationName, target);
            if (!Double.isFinite(current)) {
                throw new IllegalStateException(target.identity()
                        + " returned a non-finite current working-tune value");
            }
            entries.add(new Entry(target, current));
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException(task
                    + " declares write targets but no physical targets were captured");
        }
        return new GuidedTaskSettingsDraft(task, configurationName, entries);
    }

    private GuidedTaskSettingsDraft(GuidedTuningRecipe task,
                                    String configurationName,
                                    List<Entry> entries) {
        this.task = task;
        this.configurationName = configurationName;
        this.entries = Collections.unmodifiableList(new ArrayList<Entry>(entries));
        LinkedHashMap<String, Entry> map = new LinkedHashMap<String, Entry>();
        for (Entry entry : entries) map.put(entry.target.identity(), entry);
        this.byIdentity = map;
    }

    public GuidedTuningRecipe getTask() { return task; }
    public String getConfigurationName() { return configurationName; }
    public List<Entry> getEntries() { return entries; }

    /**
     * Return an independent mutable proposal draft with the exact same frozen
     * original values. Evidence engines use this so repeated Review calls never
     * mutate the cached Read Working Tune baseline.
     */
    public GuidedTaskSettingsDraft copyBaseline() {
        List<Entry> copy = new ArrayList<Entry>();
        for (Entry entry : entries) {
            copy.add(new Entry(entry.target, entry.originalValue));
        }
        return new GuidedTaskSettingsDraft(task, configurationName, copy);
    }

    public List<String> controllerNames() {
        Set<String> names = new LinkedHashSet<String>();
        for (Entry entry : entries) names.add(entry.target.getControllerName());
        return Collections.unmodifiableList(new ArrayList<String>(names));
    }

    public List<Entry> entriesForController(String controllerName) {
        if (controllerName == null) return Collections.emptyList();
        List<Entry> result = new ArrayList<Entry>();
        for (Entry entry : entries) {
            if (controllerName.equals(entry.target.getControllerName())) result.add(entry);
        }
        return Collections.unmodifiableList(result);
    }

    public void setProposedValue(String targetIdentity, double value) {
        Entry entry = byIdentity.get(targetIdentity);
        if (entry == null) {
            throw new IllegalArgumentException("unknown task settings target " + targetIdentity);
        }
        if (same(entry.originalValue, value)) {
            entry.proposedValue = entry.originalValue;
            return;
        }
        entry.proposedValue = AeValidationValuePolicy.requireValidTemporaryValue(
                entry.target, entry.originalValue, value);
    }

    public int changedCount() {
        int count = 0;
        for (Entry entry : entries) if (entry.isChanged()) count++;
        return count;
    }

    public ProposalWritePlan buildPlan() {
        if (changedCount() == 0) return null;
        validateCurveAxisOrdering();

        List<ProposalWritePlan.Change> changes =
                new ArrayList<ProposalWritePlan.Change>();
        Set<String> parameters = new LinkedHashSet<String>();
        for (Entry entry : entries) {
            if (!entry.isChanged()) continue;
            AeApplyRestoreValidationTargets.Target target = entry.target;
            parameters.add(target.getControllerName());
            String label = target.getParameter().getDisplayName();
            if (target.isIndexed()) label += " [" + target.coordinateText() + "]";
            String unit = target.getDefinition().getUnit();
            if (target.isIndexed()) {
                changes.add(ProposalWritePlan.Change.arrayCell(
                        target.getControllerName(), target.getFlatIndex(),
                        entry.originalValue, entry.proposedValue, label, unit));
            } else {
                changes.add(ProposalWritePlan.Change.scalar(
                        target.getControllerName(), entry.originalValue,
                        entry.proposedValue, label, unit));
            }
        }
        if (changes.isEmpty()) return null;
        return new ProposalWritePlan(
                "task-settings-" + task.name().toLowerCase(java.util.Locale.ROOT),
                task.displayName + " task settings",
                configurationName,
                "Operator-reviewed physically validated task settings; "
                        + changes.size() + " changed value(s) across "
                        + parameters.size() + " controller parameter(s).",
                changes);
    }

    /**
     * Preserve the live monotonic direction of every editable curve axis when
     * several bins are changed in one plan. Non-axis arrays/tables are not
     * subjected to this ordering rule.
     */
    private void validateCurveAxisOrdering() {
        for (String controllerName : controllerNames()) {
            List<Entry> parameterEntries = entriesForController(controllerName);
            if (parameterEntries.size() < 2) continue;
            AeTuningParameterCatalog.Parameter parameter =
                    parameterEntries.get(0).target.getParameter();
            if (parameter.getShape() != AeTuningParameterCatalog.Shape.CURVE_AXIS) continue;

            int direction = monotonicDirection(parameterEntries, false);
            if (direction == 0) {
                throw new IllegalStateException(controllerName
                        + " live axis is not strictly monotonic; task settings edit is blocked");
            }
            int proposedDirection = monotonicDirection(parameterEntries, true);
            if (proposedDirection != direction) {
                throw new IllegalArgumentException(controllerName
                        + " proposed axis must preserve strict "
                        + (direction > 0 ? "ascending" : "descending")
                        + " ordering of the working tune");
            }
        }
    }

    private static int monotonicDirection(List<Entry> entries, boolean proposed) {
        int direction = 0;
        for (int i = 1; i < entries.size(); i++) {
            double previous = proposed
                    ? entries.get(i - 1).proposedValue : entries.get(i - 1).originalValue;
            double current = proposed
                    ? entries.get(i).proposedValue : entries.get(i).originalValue;
            double difference = current - previous;
            if (Math.abs(difference) <= 0.000001) return 0;
            int next = difference > 0.0 ? 1 : -1;
            if (direction == 0) direction = next;
            else if (direction != next) return 0;
        }
        return direction;
    }

    static double editorStep(AeControllerDefinitionCatalog.Definition definition) {
        switch (definition.getValueType()) {
            case U08:
            case S08:
            case U16:
            case S16:
            case U32:
                return definition.getScale();
            default:
                int decimals = Math.max(0, definition.getDecimals());
                return Math.pow(10.0, -decimals);
        }
    }

    private static boolean same(double left, double right) {
        return Double.isFinite(left) && Double.isFinite(right)
                && Math.abs(left - right) <= 0.000001;
    }
}
