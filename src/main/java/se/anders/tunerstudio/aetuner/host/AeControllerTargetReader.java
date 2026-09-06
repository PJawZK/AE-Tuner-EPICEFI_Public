package se.anders.tunerstudio.aetuner.host;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import com.efiAnalytics.plugin.ecu.ControllerException;
import com.efiAnalytics.plugin.ecu.ControllerParameter;
import com.efiAnalytics.plugin.ecu.servers.ControllerParameterServer;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only accessor for one canonical physical validation target.
 *
 * This class deliberately has no updateParameter/write API. Mutation remains
 * exclusively in ProposalApplyCoordinator.
 */
public final class AeControllerTargetReader {
    private static final double EPSILON = 0.000001;
    private final ControllerParameterServer server;

    public AeControllerTargetReader(ControllerAccess access) {
        if (access == null || access.getControllerParameterServer() == null) {
            throw new IllegalArgumentException(
                    "TunerStudio controller-parameter server is required");
        }
        this.server = access.getControllerParameterServer();
    }

    public double read(String configurationName,
                       AeApplyRestoreValidationTargets.Target target)
            throws ControllerException {
        if (configurationName == null || configurationName.trim().length() == 0) {
            throw new IllegalArgumentException("configuration name is required");
        }
        if (target == null) throw new IllegalArgumentException("validation target is required");

        AeControllerDefinitionCatalog.Definition definition = target.getDefinition();
        ControllerParameter parameter = server.getControllerParameter(
                configurationName, target.getControllerName());
        if (parameter == null) {
            throw new ControllerException(
                    "Missing controller parameter " + target.getControllerName());
        }

        if (definition.getKind() == AeControllerDefinitionCatalog.Kind.BITS) {
            return readBitSelection(parameter, target.getControllerName(), definition);
        }
        if (definition.isIndexed()) {
            double[][] values = parameter.getArrayValues();
            if (values == null) {
                throw new ControllerException(
                        "Missing array controller parameter " + target.getControllerName());
            }
            int liveCount = flatCount(values);
            if (liveCount != definition.elementCount()) {
                throw new ControllerException(target.getControllerName()
                        + " live array has " + liveCount + " element(s), frozen authority expects "
                        + definition.elementCount());
            }
            return getFlat(values, target.getFlatIndex());
        }
        return parameter.getScalarValue();
    }

    private static double readBitSelection(
            ControllerParameter parameter, String parameterName,
            AeControllerDefinitionCatalog.Definition definition)
            throws ControllerException {
        if (!ControllerParameter.PARAM_CLASS_BITS.equals(parameter.getParamClass())) {
            throw new ControllerException(parameterName + " is " + parameter.getParamClass()
                    + ", expected TunerStudio bit selection");
        }
        String raw = parameter.getStringValue();
        if (raw == null) {
            throw new ControllerException("Missing bit-selection value for " + parameterName);
        }

        List<String> liveOptions = optionDescriptions(parameter, parameterName);
        int liveIndex = matchingOptionIndex(raw, liveOptions);
        if (liveIndex >= 0) {
            requireValidFrozenOption(definition, liveIndex, parameterName);
            return liveIndex;
        }

        if (definition.getOptionLabels().length == 2) {
            Boolean parsed = booleanOptionText(raw);
            if (parsed != null) return parsed.booleanValue() ? 1.0 : 0.0;
        }

        Integer numeric = integralOptionText(raw);
        if (numeric != null) {
            requireValidFrozenOption(definition, numeric.intValue(), parameterName);
            return numeric.intValue();
        }

        throw new ControllerException(parameterName + " current option '" + raw
                + "' is absent from live TunerStudio options " + liveOptions);
    }

    private static List<String> optionDescriptions(
            ControllerParameter parameter, String parameterName)
            throws ControllerException {
        java.util.ArrayList raw = parameter.getOptionDescriptions();
        if (raw == null || raw.isEmpty()) {
            throw new ControllerException(
                    "TunerStudio returned no bit options for " + parameterName);
        }
        List<String> result = new ArrayList<String>();
        for (Object option : raw) {
            result.add(option == null ? "" : String.valueOf(option));
        }
        return result;
    }

    private static void requireValidFrozenOption(
            AeControllerDefinitionCatalog.Definition definition,
            int optionIndex, String parameterName) throws ControllerException {
        String[] labels = definition.getOptionLabels();
        if (optionIndex < 0 || optionIndex >= labels.length) {
            throw new ControllerException(parameterName + " option index " + optionIndex
                    + " is outside frozen controller options");
        }
        String label = labels[optionIndex] == null ? "" : labels[optionIndex].trim();
        if (label.length() == 0 || "INVALID".equalsIgnoreCase(label)) {
            throw new ControllerException(parameterName + " current option index " + optionIndex
                    + " is not a valid frozen controller state");
        }
    }

    private static int matchingOptionIndex(String current, List<String> options) {
        String normalizedCurrent = normalize(current);
        for (int i = 0; i < options.size(); i++) {
            if (normalizedCurrent.equals(normalize(options.get(i)))) return i;
        }
        return -1;
    }

    private static Boolean booleanOptionText(String value) {
        String normalized = normalize(value).toLowerCase(java.util.Locale.ROOT);
        if ("0".equals(normalized) || "false".equals(normalized)
                || "off".equals(normalized) || "disabled".equals(normalized)
                || "no".equals(normalized)) return Boolean.FALSE;
        if ("1".equals(normalized) || "true".equals(normalized)
                || "on".equals(normalized) || "enabled".equals(normalized)
                || "yes".equals(normalized)) return Boolean.TRUE;
        return null;
    }

    private static Integer integralOptionText(String value) {
        try {
            double numeric = Double.parseDouble(normalize(value));
            int integer = (int) Math.rint(numeric);
            if (Math.abs(numeric - integer) <= EPSILON) return Integer.valueOf(integer);
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String normalized = value.trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"")
                && normalized.length() >= 2) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private static int flatCount(double[][] values) {
        int count = 0;
        for (double[] row : values) {
            if (row != null) count += row.length;
        }
        return count;
    }

    private static double getFlat(double[][] values, int targetIndex)
            throws ControllerException {
        int index = 0;
        for (double[] row : values) {
            if (row == null) continue;
            for (double value : row) {
                if (index == targetIndex) return value;
                index++;
            }
        }
        throw new ControllerException(
                "array index " + targetIndex + " outside " + index + " element(s)");
    }
}
