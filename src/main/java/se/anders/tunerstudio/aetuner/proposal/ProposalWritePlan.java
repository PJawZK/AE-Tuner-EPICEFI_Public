package se.anders.tunerstudio.aetuner.proposal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable, explicit description of the exact working-tune values a reviewed
 * proposal is allowed to change. Evaluators create plans; only the central
 * proposal-apply coordinator may execute them.
 */
public final class ProposalWritePlan {
    public enum Kind {
        SCALAR,
        ARRAY_CELL
    }

    public static final class Change {
        public final Kind kind;
        public final String parameterName;
        public final int flatIndex;
        public final double expectedValue;
        public final double proposedValue;
        public final String displayLabel;
        public final String unit;

        private Change(Kind kind, String parameterName, int flatIndex,
                       double expectedValue, double proposedValue,
                       String displayLabel, String unit) {
            this.kind = kind;
            this.parameterName = requireText(parameterName, "parameterName");
            this.flatIndex = flatIndex;
            this.expectedValue = expectedValue;
            this.proposedValue = proposedValue;
            this.displayLabel = displayLabel == null ? parameterName : displayLabel;
            this.unit = unit == null ? "" : unit;
            if (kind == null) {
                throw new IllegalArgumentException("proposal target kind is required");
            }
            if (!Double.isFinite(expectedValue) || !Double.isFinite(proposedValue)) {
                throw new IllegalArgumentException("proposal values must be finite");
            }
            if (expectedValue == proposedValue) {
                throw new IllegalArgumentException(
                        "proposal target must describe an actual value change: "
                                + parameterName);
            }
            if (kind == Kind.ARRAY_CELL && flatIndex < 0) {
                throw new IllegalArgumentException("array-cell target requires a non-negative index");
            }
            if (kind == Kind.SCALAR && flatIndex != -1) {
                throw new IllegalArgumentException("scalar target must use index -1");
            }
        }

        public static Change scalar(String parameterName,
                                    double expectedValue, double proposedValue,
                                    String displayLabel, String unit) {
            return new Change(Kind.SCALAR, parameterName, -1,
                    expectedValue, proposedValue, displayLabel, unit);
        }

        public static Change arrayCell(String parameterName, int flatIndex,
                                       double expectedValue, double proposedValue,
                                       String displayLabel, String unit) {
            return new Change(Kind.ARRAY_CELL, parameterName, flatIndex,
                    expectedValue, proposedValue, displayLabel, unit);
        }

        public String targetText() {
            return kind == Kind.SCALAR
                    ? parameterName
                    : parameterName + "[" + flatIndex + "]";
        }
    }

    private final String recipeId;
    private final String displayName;
    private final String configurationName;
    private final String context;
    private final List<Change> changes;

    public ProposalWritePlan(String recipeId, String displayName,
                             String configurationName, String context,
                             List<Change> changes) {
        this.recipeId = requireText(recipeId, "recipeId");
        this.displayName = requireText(displayName, "displayName");
        this.configurationName = requireText(configurationName, "configurationName");
        this.context = context == null ? "" : context;
        if (changes == null || changes.isEmpty()) {
            throw new IllegalArgumentException("proposal write plan requires at least one change");
        }
        validateUniqueTargets(changes);
        this.changes = Collections.unmodifiableList(
                new ArrayList<Change>(changes));
    }

    private static void validateUniqueTargets(List<Change> changes) {
        for (int i = 0; i < changes.size(); i++) {
            Change current = changes.get(i);
            if (current == null) {
                throw new IllegalArgumentException(
                        "proposal write plan cannot contain a null change");
            }
            for (int j = 0; j < i; j++) {
                Change prior = changes.get(j);
                if (!current.parameterName.equals(prior.parameterName)) {
                    continue;
                }
                if (current.kind != prior.kind) {
                    throw new IllegalArgumentException(
                            "one controller parameter cannot be both scalar and array in one plan: "
                                    + current.parameterName);
                }
                boolean sameTarget = current.kind == Kind.SCALAR
                        || current.flatIndex == prior.flatIndex;
                if (sameTarget) {
                    throw new IllegalArgumentException(
                            "proposal write plan declares the same target more than once: "
                                    + current.targetText());
                }
            }
        }
    }

    public String getRecipeId() { return recipeId; }
    public String getDisplayName() { return displayName; }
    public String getConfigurationName() { return configurationName; }
    public String getContext() { return context; }
    public List<Change> getChanges() { return changes; }
    public int changeCount() { return changes.size(); }

    public ProposalWritePlan reversed(String nextContext) {
        List<Change> reversed = new ArrayList<Change>();
        for (Change change : changes) {
            if (change.kind == Kind.SCALAR) {
                reversed.add(Change.scalar(change.parameterName,
                        change.proposedValue, change.expectedValue,
                        change.displayLabel, change.unit));
            } else {
                reversed.add(Change.arrayCell(change.parameterName,
                        change.flatIndex, change.proposedValue,
                        change.expectedValue, change.displayLabel, change.unit));
            }
        }
        return new ProposalWritePlan(recipeId, displayName, configurationName,
                nextContext == null ? context : nextContext, reversed);
    }

    public String reviewText() {
        StringBuilder text = new StringBuilder();
        text.append("CURRENT PROPOSAL\n")
                .append(displayName).append("\n");
        if (context.length() > 0) {
            text.append(context).append("\n");
        }
        text.append(changes.size()).append(changes.size() == 1
                ? " value will change\n" : " values will change\n");
        for (Change change : changes) {
            text.append("  ").append(change.displayLabel).append(": ")
                    .append(format(change.expectedValue)).append(unitSuffix(change.unit))
                    .append(" -> ")
                    .append(format(change.proposedValue)).append(unitSuffix(change.unit))
                    .append("\n");
        }
        text.append("Everything not declared above remains outside this write plan.");
        return text.toString();
    }

    /**
     * Machine-readable allowlist consumed by scripts/verify-msq-apply.py after
     * a real TunerStudio before/after MSQ save pair is captured.
     */
    public String verificationManifestJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\n")
                .append("  \"recipe_id\": \"").append(jsonEscape(recipeId)).append("\",\n")
                .append("  \"display_name\": \"").append(jsonEscape(displayName)).append("\",\n")
                .append("  \"configuration\": \"").append(jsonEscape(configurationName)).append("\",\n")
                .append("  \"context\": \"").append(jsonEscape(context)).append("\",\n")
                .append("  \"changes\": [\n");
        for (int i = 0; i < changes.size(); i++) {
            Change change = changes.get(i);
            json.append("    {\"parameter\": \"")
                    .append(jsonEscape(change.parameterName)).append("\"");
            if (change.kind == Kind.ARRAY_CELL) {
                json.append(", \"index\": ").append(change.flatIndex);
            }
            json.append(", \"before\": ").append(jsonNumber(change.expectedValue))
                    .append(", \"after\": ").append(jsonNumber(change.proposedValue))
                    .append("}");
            if (i + 1 < changes.size()) {
                json.append(',');
            }
            json.append('\n');
        }
        json.append("  ]\n}\n");
        return json.toString();
    }

    private static String jsonEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String jsonNumber(double value) {
        return String.format(java.util.Locale.ROOT, "%.10f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    private static String unitSuffix(String unit) {
        return unit == null || unit.length() == 0 ? "" : " " + unit;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    private static String requireText(String value, String label) {
        if (value == null || value.trim().length() == 0) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
