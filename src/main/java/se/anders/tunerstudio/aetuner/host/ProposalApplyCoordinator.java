package se.anders.tunerstudio.aetuner.host;

import se.anders.tunerstudio.aetuner.proposal.ProposalWritePlan;
import se.anders.tunerstudio.aetuner.model.EngagementModelOption;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import com.efiAnalytics.plugin.ecu.ControllerException;
import com.efiAnalytics.plugin.ecu.ControllerParameter;
import com.efiAnalytics.plugin.ecu.servers.ControllerParameterServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single gateway for explicit AE Tuner working-tune writes.
 *
 * Safety properties:
 * - every write must be declared by an immutable ProposalWritePlan;
 * - all targets are pre-read and stale-checked before the first write;
 * - array shape is preserved and only declared cells are changed;
 * - canonical TunerStudio bit/enum settings are read and written through their
 *   option-selection representation rather than the shared-word scalar value;
 * - all written parameters are read back and compared against the exact
 *   expected post-write state;
 * - partial failures trigger best-effort rollback;
 * - Restore Previous Apply is itself stale-checked;
 * - successful explicit applies are retained as a LIFO restore stack so
 *   sequential tuning of multiple points/settings does not discard rollback
 *   history;
 * - there is deliberately no burn API or automatic application path here.
 */
public final class ProposalApplyCoordinator {
    private static final double VALUE_EPSILON = 0.000001;

    interface Backend {
        double readScalar(String configurationName, String parameterName)
                throws Exception;
        default String readOption(String configurationName, String parameterName)
                throws Exception {
            throw new UnsupportedOperationException(
                    "bit-selection read is not implemented for " + parameterName);
        }
        default List<String> readOptionDescriptions(String configurationName,
                                                    String parameterName)
                throws Exception {
            throw new UnsupportedOperationException(
                    "bit-selection option list is not implemented for " + parameterName);
        }
        double[][] readArray(String configurationName, String parameterName)
                throws Exception;
        void writeScalar(String configurationName, String parameterName, double value)
                throws Exception;
        default void writeOptionIndex(String configurationName, String parameterName,
                                      int optionIndex) throws Exception {
            throw new UnsupportedOperationException(
                    "bit-selection indexed write is not implemented for " + parameterName);
        }
        void writeArray(String configurationName, String parameterName, double[][] values)
                throws Exception;
    }

    private static final class TunerStudioBackend implements Backend {
        private final ControllerParameterServer server;

        TunerStudioBackend(ControllerAccess access) {
            if (access == null || access.getControllerParameterServer() == null) {
                throw new IllegalArgumentException(
                        "TunerStudio controller-parameter server is required");
            }
            this.server = access.getControllerParameterServer();
        }

        @Override
        public double readScalar(String configurationName, String parameterName)
                throws ControllerException {
            ControllerParameter parameter =
                    server.getControllerParameter(configurationName, parameterName);
            if (parameter == null) {
                throw new ControllerException("Missing controller parameter " + parameterName);
            }
            return parameter.getScalarValue();
        }

        @Override
        public String readOption(String configurationName, String parameterName)
                throws ControllerException {
            ControllerParameter parameter = requireBitParameter(configurationName, parameterName);
            if (parameter.getStringValue() == null) {
                throw new ControllerException(
                        "Missing bit-selection value for " + parameterName);
            }
            return parameter.getStringValue();
        }

        @Override
        public List<String> readOptionDescriptions(String configurationName,
                                                   String parameterName)
                throws ControllerException {
            return bitOptions(requireBitParameter(configurationName, parameterName), parameterName);
        }

        @Override
        public double[][] readArray(String configurationName, String parameterName)
                throws ControllerException {
            ControllerParameter parameter =
                    server.getControllerParameter(configurationName, parameterName);
            if (parameter == null || parameter.getArrayValues() == null) {
                throw new ControllerException("Missing array controller parameter " + parameterName);
            }
            return cloneTable(parameter.getArrayValues());
        }

        @Override
        public void writeScalar(String configurationName, String parameterName, double value)
                throws ControllerException {
            server.updateParameter(configurationName, parameterName, value);
        }

        @Override
        public void writeOptionIndex(String configurationName, String parameterName,
                                     int optionIndex) throws ControllerException {
            ControllerParameter parameter = requireBitParameter(configurationName, parameterName);
            List<String> options = bitOptions(parameter, parameterName);
            if (optionIndex < 0 || optionIndex >= options.size()) {
                throw new ControllerException(parameterName + " option index " + optionIndex
                        + " is outside live TunerStudio options " + options);
            }
            String exactOption = options.get(optionIndex);
            server.updateParameter(configurationName, parameterName, exactOption);
        }

        private ControllerParameter requireBitParameter(String configurationName,
                                                        String parameterName)
                throws ControllerException {
            ControllerParameter parameter =
                    server.getControllerParameter(configurationName, parameterName);
            if (parameter == null) {
                throw new ControllerException("Missing controller parameter " + parameterName);
            }
            if (!ControllerParameter.PARAM_CLASS_BITS.equals(parameter.getParamClass())) {
                throw new ControllerException(parameterName + " is " + parameter.getParamClass()
                        + ", expected TunerStudio bit selection");
            }
            return parameter;
        }

        private static List<String> bitOptions(ControllerParameter parameter, String parameterName)
                throws ControllerException {
            java.util.ArrayList raw = parameter.getOptionDescriptions();
            if (raw == null || raw.isEmpty()) {
                throw new ControllerException(
                        "TunerStudio returned no valid bit options for " + parameterName);
            }
            List<String> result = new ArrayList<String>();
            for (Object option : raw) {
                result.add(option == null ? "" : String.valueOf(option));
            }
            return result;
        }

        @Override
        public void writeArray(String configurationName, String parameterName, double[][] values)
                throws ControllerException {
            server.updateParameter(configurationName, parameterName, cloneTable(values));
        }
    }

    public static final class ApplyResult {
        public final boolean success;
        public final boolean restore;
        public final int changeCount;
        public final String message;

        private ApplyResult(boolean success, boolean restore,
                            int changeCount, String message) {
            this.success = success;
            this.restore = restore;
            this.changeCount = changeCount;
            this.message = message == null ? "" : message;
        }

        static ApplyResult success(boolean restore, int count, String message) {
            return new ApplyResult(true, restore, count, message);
        }

        static ApplyResult failure(boolean restore, String message) {
            return new ApplyResult(false, restore, 0, message);
        }
    }

    private static final class ParameterState {
        final String parameterName;
        final ProposalWritePlan.Kind kind;
        final double scalar;
        final double[][] array;

        ParameterState(String parameterName, double scalar) {
            this.parameterName = parameterName;
            this.kind = ProposalWritePlan.Kind.SCALAR;
            this.scalar = scalar;
            this.array = null;
        }

        ParameterState(String parameterName, double[][] array) {
            this.parameterName = parameterName;
            this.kind = ProposalWritePlan.Kind.ARRAY_CELL;
            this.scalar = Double.NaN;
            this.array = cloneTable(array);
        }
    }

    private static final class ApplyRecord {
        final ProposalWritePlan plan;
        final LinkedHashMap<String, ParameterState> before;
        final LinkedHashMap<String, ParameterState> after;

        ApplyRecord(ProposalWritePlan plan,
                    LinkedHashMap<String, ParameterState> before,
                    LinkedHashMap<String, ParameterState> after) {
            this.plan = plan;
            this.before = before;
            this.after = after;
        }
    }

    private final Backend backend;
    private final List<ApplyRecord> applyHistory = new ArrayList<ApplyRecord>();

    public ProposalApplyCoordinator(ControllerAccess access) {
        this(new TunerStudioBackend(access));
    }

    ProposalApplyCoordinator(Backend backend) {
        if (backend == null) {
            throw new IllegalArgumentException("proposal apply backend is required");
        }
        this.backend = backend;
    }

    public synchronized boolean canRestorePreviousApply() {
        return !applyHistory.isEmpty();
    }

    public synchronized int applyDepth() {
        return applyHistory.size();
    }

    public synchronized ProposalWritePlan previousApplyPlan() {
        ApplyRecord latest = latestRecord();
        return latest == null ? null : latest.plan;
    }

    public synchronized String previousApplyText() {
        ApplyRecord latest = latestRecord();
        if (latest == null) {
            return "No proposal has been applied during this plugin session.";
        }
        return latest.plan.getDisplayName() + " | "
                + latest.plan.getContext() + " | "
                + latest.plan.changeCount() + " value(s) | restore depth "
                + applyHistory.size();
    }

    public synchronized ApplyResult apply(ProposalWritePlan plan) {
        if (plan == null) {
            return ApplyResult.failure(false, "No reviewed proposal is available.");
        }
        try {
            Prepared prepared = prepare(plan);
            ApplyResult result = execute(plan, prepared.before, prepared.after, false);
            if (result.success) {
                applyHistory.add(new ApplyRecord(plan, prepared.before, prepared.after));
            }
            return result;
        } catch (Exception ex) {
            return ApplyResult.failure(false,
                    "Apply blocked before writing: " + safeMessage(ex));
        }
    }

    public synchronized ApplyResult restorePreviousApply() {
        ApplyRecord record = latestRecord();
        if (record == null) {
            return ApplyResult.failure(true,
                    "Nothing is available to restore in this plugin session.");
        }

        try {
            verifyCurrentMatches(record.plan.getConfigurationName(), record.after,
                    "Restore blocked: working tune changed after AE Tuner applied the proposal");
            ApplyResult result = execute(record.plan, record.after, record.before, true);
            if (result.success) {
                applyHistory.remove(applyHistory.size() - 1);
            }
            return result;
        } catch (Exception ex) {
            return ApplyResult.failure(true, safeMessage(ex));
        }
    }

    private ApplyRecord latestRecord() {
        return applyHistory.isEmpty()
                ? null : applyHistory.get(applyHistory.size() - 1);
    }

    private Prepared prepare(ProposalWritePlan plan) throws Exception {
        String configurationName = plan.getConfigurationName();
        LinkedHashMap<String, List<ProposalWritePlan.Change>> grouped =
                groupChanges(plan.getChanges());
        LinkedHashMap<String, ParameterState> before =
                new LinkedHashMap<String, ParameterState>();
        LinkedHashMap<String, ParameterState> after =
                new LinkedHashMap<String, ParameterState>();

        for (Map.Entry<String, List<ProposalWritePlan.Change>> entry
                : grouped.entrySet()) {
            String parameterName = entry.getKey();
            List<ProposalWritePlan.Change> changes = entry.getValue();
            ProposalWritePlan.Kind kind = consistentKind(changes);
            if (kind == ProposalWritePlan.Kind.SCALAR) {
                if (changes.size() != 1) {
                    throw new IllegalArgumentException(
                            "multiple scalar writes declared for " + parameterName);
                }
                ProposalWritePlan.Change change = changes.get(0);
                double current = readScalarForComparison(configurationName, parameterName);
                requireMatch(change.expectedValue, current,
                        change.targetText() + " is stale");
                before.put(parameterName, new ParameterState(parameterName, current));
                after.put(parameterName,
                        new ParameterState(parameterName, change.proposedValue));
            } else {
                double[][] current = backend.readArray(configurationName, parameterName);
                double[][] proposed = cloneTable(current);
                for (ProposalWritePlan.Change change : changes) {
                    double actual = getFlat(current, change.flatIndex);
                    requireMatch(change.expectedValue, actual,
                            change.targetText() + " is stale");
                    setFlat(proposed, change.flatIndex, change.proposedValue);
                }
                before.put(parameterName, new ParameterState(parameterName, current));
                after.put(parameterName, new ParameterState(parameterName, proposed));
            }
        }
        return new Prepared(before, after);
    }

    private ApplyResult execute(ProposalWritePlan plan,
                                LinkedHashMap<String, ParameterState> expectedCurrent,
                                LinkedHashMap<String, ParameterState> desired,
                                boolean restore) {
        String configurationName = plan.getConfigurationName();
        List<String> written = new ArrayList<String>();
        try {
            verifyCurrentMatches(configurationName, expectedCurrent,
                    restore ? "Restore blocked by stale working tune"
                            : "Apply blocked by stale working tune");

            for (ParameterState state : desired.values()) {
                writeState(configurationName, state);
                written.add(state.parameterName);
            }

            verifyCurrentMatches(configurationName, desired,
                    restore ? "Restore read-back verification failed"
                            : "Apply read-back verification failed");

            String action = restore ? "Restored" : "Applied";
            return ApplyResult.success(restore, plan.changeCount(),
                    action + " " + plan.changeCount() + " declared value(s) for "
                            + plan.getDisplayName()
                            + "; read-back PASS; no burn was requested.");
        } catch (Exception failure) {
            String rollback = rollback(configurationName, expectedCurrent, written);
            return ApplyResult.failure(restore,
                    (restore ? "Restore failed: " : "Apply failed: ")
                            + safeMessage(failure) + ". " + rollback);
        }
    }

    private String rollback(String configurationName,
                            LinkedHashMap<String, ParameterState> original,
                            List<String> written) {
        if (written.isEmpty()) {
            return "No value had been written.";
        }
        List<String> reversed = new ArrayList<String>(written);
        Collections.reverse(reversed);
        try {
            for (String parameterName : reversed) {
                ParameterState state = original.get(parameterName);
                if (state != null) {
                    writeState(configurationName, state);
                }
            }
            verifyCurrentMatches(configurationName, original,
                    "rollback read-back mismatch");
            return "Best-effort rollback PASS.";
        } catch (Exception rollbackFailure) {
            return "ROLLBACK COULD NOT BE VERIFIED: " + safeMessage(rollbackFailure);
        }
    }

    private void verifyCurrentMatches(String configurationName,
                                      LinkedHashMap<String, ParameterState> expected,
                                      String prefix) throws Exception {
        for (ParameterState state : expected.values()) {
            if (state.kind == ProposalWritePlan.Kind.SCALAR) {
                double current = readScalarForComparison(configurationName, state.parameterName);
                requireMatch(state.scalar, current,
                        prefix + " at " + state.parameterName);
            } else {
                double[][] current = backend.readArray(configurationName, state.parameterName);
                if (!sameShape(state.array, current)) {
                    throw new IllegalStateException(prefix + " at " + state.parameterName
                            + ": array shape changed");
                }
                int count = flatCount(state.array);
                for (int i = 0; i < count; i++) {
                    requireMatch(getFlat(state.array, i), getFlat(current, i),
                            prefix + " at " + state.parameterName + "[" + i + "]");
                }
            }
        }
    }

    /** Normalize controller representations before stale/readback comparison. */
    private double readScalarForComparison(String configurationName,
                                           String parameterName) throws Exception {
        if (isCanonicalBitSelection(parameterName)) {
            return readBitSelectionForComparison(configurationName, parameterName);
        }
        return backend.readScalar(configurationName, parameterName);
    }

    private double readBitSelectionForComparison(String configurationName,
                                                  String parameterName) throws Exception {
        AeControllerDefinitionCatalog.Definition definition =
                requireBitDefinition(parameterName);
        String rawOption = backend.readOption(configurationName, parameterName);
        List<String> liveOptions = backend.readOptionDescriptions(configurationName, parameterName);
        int liveIndex = matchingOptionIndex(rawOption, liveOptions);
        if (liveIndex >= 0) {
            requireValidBitOptionIndex(definition, liveIndex, parameterName);
            return liveIndex;
        }

        // Preserve the established Engagement Model compatibility with API
        // builds that report either the enum text or an integral controller code.
        if (AeParameterNames.TPS_AE_DETECT_MODE.equals(parameterName)) {
            EngagementModelOption option = EngagementModelOption.fromControllerText(rawOption);
            if (option != null) {
                requireValidBitOptionIndex(definition, option.controllerValue(), parameterName);
                return option.controllerValue();
            }
        }

        // Some API builds expose one-bit settings as generic boolean text rather
        // than the exact option-description object. Preserve that compatibility
        // for every canonical two-state bit field, not just Fast Callback.
        if (definition.getOptionLabels().length == 2) {
            Boolean parsed = booleanOptionText(rawOption);
            if (parsed != null) return parsed.booleanValue() ? 1.0 : 0.0;
        }

        Integer numeric = integralOptionText(rawOption);
        if (numeric != null) {
            requireValidBitOptionIndex(definition, numeric.intValue(), parameterName);
            return numeric.intValue();
        }

        throw new IllegalStateException(parameterName
                + " returned current option '" + rawOption
                + "' which is absent from live TunerStudio options " + liveOptions);
    }

    private void writeState(String configurationName, ParameterState state)
            throws Exception {
        if (state.kind == ProposalWritePlan.Kind.SCALAR) {
            if (isCanonicalBitSelection(state.parameterName)) {
                AeControllerDefinitionCatalog.Definition definition =
                        requireBitDefinition(state.parameterName);
                int optionIndex = integralCode(state.scalar,
                        state.parameterName + " option index");
                requireValidBitOptionIndex(definition, optionIndex, state.parameterName);
                backend.writeOptionIndex(configurationName, state.parameterName, optionIndex);
            } else {
                backend.writeScalar(configurationName, state.parameterName, state.scalar);
            }
        } else {
            backend.writeArray(configurationName, state.parameterName, state.array);
        }
    }

    private static boolean isCanonicalBitSelection(String parameterName) {
        AeControllerDefinitionCatalog.Definition definition =
                AeControllerDefinitionCatalog.find(parameterName);
        return definition != null
                && definition.getKind() == AeControllerDefinitionCatalog.Kind.BITS;
    }

    private static AeControllerDefinitionCatalog.Definition requireBitDefinition(
            String parameterName) {
        AeControllerDefinitionCatalog.Definition definition =
                AeControllerDefinitionCatalog.find(parameterName);
        if (definition == null
                || definition.getKind() != AeControllerDefinitionCatalog.Kind.BITS) {
            throw new IllegalArgumentException(
                    parameterName + " is not a canonical TunerStudio bit selection");
        }
        return definition;
    }

    private static void requireValidBitOptionIndex(
            AeControllerDefinitionCatalog.Definition definition,
            int optionIndex, String parameterName) {
        String[] labels = definition.getOptionLabels();
        if (optionIndex < 0 || optionIndex >= labels.length) {
            throw new IllegalArgumentException(parameterName + " option index " + optionIndex
                    + " is outside frozen controller options 0.." + (labels.length - 1));
        }
        String label = labels[optionIndex] == null ? "" : labels[optionIndex].trim();
        if (label.length() == 0 || "INVALID".equalsIgnoreCase(label)) {
            throw new IllegalArgumentException(parameterName + " option index " + optionIndex
                    + " is not a valid frozen controller state");
        }
    }

    private static int matchingOptionIndex(String current, List<String> options) {
        if (current == null || options == null) return -1;
        String normalizedCurrent = normalizeOptionText(current);
        for (int i = 0; i < options.size(); i++) {
            if (normalizedCurrent.equals(normalizeOptionText(options.get(i)))) return i;
        }
        return -1;
    }

    private static String normalizeOptionText(String value) {
        if (value == null) return "";
        String normalized = value.trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"")
                && normalized.length() >= 2) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private static Boolean booleanOptionText(String value) {
        if (value == null) return null;
        String normalized = normalizeOptionText(value).toLowerCase(java.util.Locale.ROOT);
        if ("0".equals(normalized) || "false".equals(normalized)
                || "off".equals(normalized) || "disabled".equals(normalized)
                || "no".equals(normalized)) return Boolean.FALSE;
        if ("1".equals(normalized) || "true".equals(normalized)
                || "on".equals(normalized) || "enabled".equals(normalized)
                || "yes".equals(normalized)) return Boolean.TRUE;
        return null;
    }

    private static Integer integralOptionText(String value) {
        if (value == null) return null;
        try {
            double numeric = Double.parseDouble(normalizeOptionText(value));
            return Integer.valueOf(integralCode(numeric, "option index"));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static int integralCode(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite: " + value);
        }
        int code = (int) Math.rint(value);
        if (Math.abs(value - code) > VALUE_EPSILON) {
            throw new IllegalArgumentException(label + " must be integral: " + value);
        }
        return code;
    }

    private static LinkedHashMap<String, List<ProposalWritePlan.Change>> groupChanges(
            List<ProposalWritePlan.Change> changes) {
        LinkedHashMap<String, List<ProposalWritePlan.Change>> grouped =
                new LinkedHashMap<String, List<ProposalWritePlan.Change>>();
        for (ProposalWritePlan.Change change : changes) {
            List<ProposalWritePlan.Change> bucket = grouped.get(change.parameterName);
            if (bucket == null) {
                bucket = new ArrayList<ProposalWritePlan.Change>();
                grouped.put(change.parameterName, bucket);
            }
            bucket.add(change);
        }
        return grouped;
    }

    private static ProposalWritePlan.Kind consistentKind(
            List<ProposalWritePlan.Change> changes) {
        ProposalWritePlan.Kind kind = changes.get(0).kind;
        for (ProposalWritePlan.Change change : changes) {
            if (change.kind != kind) {
                throw new IllegalArgumentException(
                        "one controller parameter cannot be both scalar and array in one plan");
            }
        }
        return kind;
    }

    private static void requireMatch(double expected, double actual, String message) {
        if (!Double.isFinite(actual)
                || Math.abs(expected - actual) > VALUE_EPSILON) {
            throw new IllegalStateException(message + ": expected " + expected
                    + " but current value is " + actual);
        }
    }

    private static int flatCount(double[][] values) {
        int count = 0;
        if (values != null) {
            for (double[] row : values) {
                if (row != null) count += row.length;
            }
        }
        return count;
    }

    private static double getFlat(double[][] values, int targetIndex) {
        if (targetIndex < 0) {
            throw new IndexOutOfBoundsException("negative array index " + targetIndex);
        }
        int index = 0;
        if (values != null) {
            for (double[] row : values) {
                if (row == null) continue;
                for (double value : row) {
                    if (index == targetIndex) return value;
                    index++;
                }
            }
        }
        throw new IndexOutOfBoundsException("array index " + targetIndex
                + " outside " + index + " value(s)");
    }

    private static void setFlat(double[][] values, int targetIndex, double value) {
        if (targetIndex < 0) {
            throw new IndexOutOfBoundsException("negative array index " + targetIndex);
        }
        int index = 0;
        if (values != null) {
            for (int row = 0; row < values.length; row++) {
                if (values[row] == null) continue;
                for (int column = 0; column < values[row].length; column++) {
                    if (index == targetIndex) {
                        values[row][column] = value;
                        return;
                    }
                    index++;
                }
            }
        }
        throw new IndexOutOfBoundsException("array index " + targetIndex
                + " outside " + index + " value(s)");
    }

    private static boolean sameShape(double[][] a, double[][] b) {
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            int ac = a[i] == null ? 0 : a[i].length;
            int bc = b[i] == null ? 0 : b[i].length;
            if (ac != bc) return false;
        }
        return true;
    }

    private static double[][] cloneTable(double[][] values) {
        if (values == null) return new double[0][0];
        double[][] copy = new double[values.length][];
        for (int i = 0; i < values.length; i++) {
            copy[i] = values[i] == null ? new double[0] : values[i].clone();
        }
        return copy;
    }

    private static String safeMessage(Exception ex) {
        if (ex == null) return "unknown error";
        String message = ex.getMessage();
        return message == null || message.length() == 0
                ? ex.getClass().getSimpleName() : message;
    }

    private static final class Prepared {
        final LinkedHashMap<String, ParameterState> before;
        final LinkedHashMap<String, ParameterState> after;

        Prepared(LinkedHashMap<String, ParameterState> before,
                 LinkedHashMap<String, ParameterState> after) {
            this.before = before;
            this.after = after;
        }
    }
}
