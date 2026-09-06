package se.anders.tunerstudio.aetuner.host;

import se.anders.tunerstudio.aetuner.guided.GuidedTuningArea;
import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Derived physical Apply/Restore validation targets.
 *
 * This class owns no controller mapping of its own. It expands the permanent
 * GuidedControllerSettingInventory through AeTuningParameterCatalog and
 * AeControllerDefinitionCatalog so every writable scalar/bit/enum is one target
 * and every writable array/table element is one target. Multiple Guided tasks
 * may intentionally reference the same physical controller target; those task
 * associations are retained while the physical target itself is deduplicated.
 */
public final class AeApplyRestoreValidationTargets {
    public static final class Target {
        private final String controllerName;
        private final int flatIndex;
        private final int[] coordinates;
        private final AeTuningParameterCatalog.Parameter parameter;
        private final AeControllerDefinitionCatalog.Definition definition;
        private final List<GuidedTuningRecipe> tasks;
        private final List<GuidedTuningArea> areas;

        private Target(String controllerName, int flatIndex, int[] coordinates,
                       AeTuningParameterCatalog.Parameter parameter,
                       AeControllerDefinitionCatalog.Definition definition,
                       List<GuidedTuningRecipe> tasks,
                       List<GuidedTuningArea> areas) {
            this.controllerName = controllerName;
            this.flatIndex = flatIndex;
            this.coordinates = coordinates == null ? new int[0] : coordinates.clone();
            this.parameter = parameter;
            this.definition = definition;
            this.tasks = Collections.unmodifiableList(new ArrayList<GuidedTuningRecipe>(tasks));
            this.areas = Collections.unmodifiableList(new ArrayList<GuidedTuningArea>(areas));
        }

        public String getControllerName() { return controllerName; }
        public int getFlatIndex() { return flatIndex; }
        public int[] getCoordinates() { return coordinates.clone(); }
        public AeTuningParameterCatalog.Parameter getParameter() { return parameter; }
        public AeControllerDefinitionCatalog.Definition getDefinition() { return definition; }
        public List<GuidedTuningRecipe> getTasks() { return tasks; }
        public List<GuidedTuningArea> getAreas() { return areas; }
        public boolean isIndexed() { return flatIndex >= 0; }

        public String identity() {
            return isIndexed() ? controllerName + "[" + flatIndex + "]" : controllerName;
        }

        public String coordinateText() {
            if (coordinates.length == 0) return "";
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < coordinates.length; i++) {
                if (i > 0) text.append(',');
                text.append(coordinates[i]);
            }
            return text.toString();
        }
    }

    private static final List<Target> ALL = build();

    private AeApplyRestoreValidationTargets() { }

    public static List<Target> all() { return ALL; }

    public static Target find(String controllerName, int flatIndex) {
        if (controllerName == null) return null;
        for (Target target : ALL) {
            if (controllerName.equals(target.controllerName)
                    && flatIndex == target.flatIndex) return target;
        }
        return null;
    }

    public static int physicalTargetCount() { return ALL.size(); }

    private static List<Target> build() {
        LinkedHashMap<String, MutableTarget> targets =
                new LinkedHashMap<String, MutableTarget>();

        for (GuidedControllerSettingInventory.TaskInventory taskInventory
                : GuidedControllerSettingInventory.all()) {
            if (!requiresWriteValidation(taskInventory.getClassification())) continue;

            for (String controllerName : taskInventory.getControllerTargets()) {
                AeTuningParameterCatalog.Parameter parameter =
                        AeTuningParameterCatalog.find(controllerName);
                if (parameter == null) {
                    throw new IllegalStateException(
                            "Guided validation target is absent from canonical catalog: "
                                    + controllerName);
                }
                AeControllerDefinitionCatalog.Definition definition =
                        parameter.getControllerDefinition();
                if (definition == null || !definition.isWritable()) {
                    throw new IllegalStateException(
                            "Guided validation target lacks writable controller metadata: "
                                    + controllerName);
                }

                int count = definition.elementCount();
                if (count <= 0) {
                    throw new IllegalStateException(
                            "Controller target has no physical elements: " + controllerName);
                }
                for (int element = 0; element < count; element++) {
                    int flatIndex = definition.isIndexed() ? element : -1;
                    String identity = flatIndex < 0
                            ? controllerName : controllerName + "[" + flatIndex + "]";
                    MutableTarget mutable = targets.get(identity);
                    if (mutable == null) {
                        mutable = new MutableTarget(controllerName, flatIndex,
                                coordinates(definition.getDimensions(), element),
                                parameter, definition);
                        targets.put(identity, mutable);
                    }
                    mutable.addAssociation(taskInventory.getArea(), taskInventory.getTask());
                }
            }
        }

        List<Target> result = new ArrayList<Target>();
        for (MutableTarget mutable : targets.values()) result.add(mutable.freeze());
        return Collections.unmodifiableList(result);
    }

    private static boolean requiresWriteValidation(
            GuidedControllerSettingInventory.Classification classification) {
        return classification
                == GuidedControllerSettingInventory.Classification.WRITABLE_VALIDATION_REQUIRED
                || classification
                == GuidedControllerSettingInventory.Classification.PLANNED_WRITABLE_MAPPING;
    }

    /** Row-major coordinates matching ProposalApplyCoordinator's flat array order. */
    static int[] coordinates(int[] dimensions, int flatIndex) {
        if (dimensions == null || dimensions.length == 0) return new int[0];
        int count = 1;
        for (int dimension : dimensions) {
            if (dimension <= 0) throw new IllegalArgumentException("invalid dimension " + dimension);
            count *= dimension;
        }
        if (flatIndex < 0 || flatIndex >= count) {
            throw new IndexOutOfBoundsException(
                    "flat index " + flatIndex + " outside " + count + " elements");
        }
        int[] coordinates = new int[dimensions.length];
        int remaining = flatIndex;
        for (int i = dimensions.length - 1; i >= 0; i--) {
            coordinates[i] = remaining % dimensions[i];
            remaining /= dimensions[i];
        }
        return coordinates;
    }

    private static final class MutableTarget {
        final String controllerName;
        final int flatIndex;
        final int[] coordinates;
        final AeTuningParameterCatalog.Parameter parameter;
        final AeControllerDefinitionCatalog.Definition definition;
        final List<GuidedTuningRecipe> tasks = new ArrayList<GuidedTuningRecipe>();
        final List<GuidedTuningArea> areas = new ArrayList<GuidedTuningArea>();

        MutableTarget(String controllerName, int flatIndex, int[] coordinates,
                      AeTuningParameterCatalog.Parameter parameter,
                      AeControllerDefinitionCatalog.Definition definition) {
            this.controllerName = controllerName;
            this.flatIndex = flatIndex;
            this.coordinates = coordinates;
            this.parameter = parameter;
            this.definition = definition;
        }

        void addAssociation(GuidedTuningArea area, GuidedTuningRecipe task) {
            if (!tasks.contains(task)) tasks.add(task);
            if (!areas.contains(area)) areas.add(area);
        }

        Target freeze() {
            return new Target(controllerName, flatIndex, coordinates,
                    parameter, definition, tasks, areas);
        }
    }
}
