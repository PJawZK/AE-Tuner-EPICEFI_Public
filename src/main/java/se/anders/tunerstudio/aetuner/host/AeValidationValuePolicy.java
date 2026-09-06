package se.anders.tunerstudio.aetuner.host;

/**
 * Validates temporary values against the frozen controller definition and can
 * derive one deterministic nearby value for automated physical validation.
 * Values are expressed in TunerStudio engineering units, matching
 * ProposalWritePlan and controller readback values.
 */
public final class AeValidationValuePolicy {
    private static final double EPSILON = 0.000001;

    private AeValidationValuePolicy() { }

    /**
     * Choose the smallest practical in-range alternative to the captured live
     * value. Bits/enums select another valid option; integer-backed values move
     * one exact controller storage step; floating values move one displayed
     * decimal step. The result is always re-validated by the same policy used
     * for manual values.
     */
    public static double chooseAutomaticTemporaryValue(
            AeApplyRestoreValidationTargets.Target target,
            double originalValue) {
        return chooseAutomaticTemporaryValueWithin(
                target, originalValue,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    /**
     * As above, but require the temporary value to remain strictly inside a
     * caller-supplied live-neighbor interval. Used for curve axes so an
     * automated validation write cannot equal or cross an adjacent bin.
     */
    public static double chooseAutomaticTemporaryValueWithin(
            AeApplyRestoreValidationTargets.Target target,
            double originalValue,
            double lowerExclusive, double upperExclusive) {
        if (target == null) throw new IllegalArgumentException("validation target is required");
        if (!Double.isFinite(originalValue)) {
            throw new IllegalArgumentException("original working-tune value must be finite");
        }
        if (!(lowerExclusive < originalValue && originalValue < upperExclusive)) {
            throw new IllegalStateException(target.identity()
                    + " live axis value " + originalValue
                    + " is not strictly between neighbors "
                    + lowerExclusive + " and " + upperExclusive);
        }

        AeControllerDefinitionCatalog.Definition definition = target.getDefinition();
        if (definition.getKind() == AeControllerDefinitionCatalog.Kind.BITS) {
            String[] labels = definition.getOptionLabels();
            int originalOption = (int) Math.rint(originalValue);
            for (int distance = 1; distance <= labels.length; distance++) {
                int lower = originalOption - distance;
                if (isValidOption(labels, lower)
                        && inside(lower, lowerExclusive, upperExclusive)) {
                    return requireValidTemporaryValue(target, originalValue, lower);
                }
                int upper = originalOption + distance;
                if (isValidOption(labels, upper)
                        && inside(upper, lowerExclusive, upperExclusive)) {
                    return requireValidTemporaryValue(target, originalValue, upper);
                }
            }
            throw new IllegalStateException(target.identity()
                    + " has no second valid controller option for an automated round trip");
        }

        double step = automaticNumericStep(definition);
        Double candidate = validatedCandidateWithin(
                target, originalValue, originalValue + step,
                lowerExclusive, upperExclusive);
        if (candidate != null) return candidate.doubleValue();
        candidate = validatedCandidateWithin(
                target, originalValue, originalValue - step,
                lowerExclusive, upperExclusive);
        if (candidate != null) return candidate.doubleValue();

        // Boundary/floating-point fallback: exact advertised limits are only
        // acceptable when they also remain strictly inside live axis neighbors.
        candidate = validatedCandidateWithin(
                target, originalValue, definition.getMaximum(),
                lowerExclusive, upperExclusive);
        if (candidate != null) return candidate.doubleValue();
        candidate = validatedCandidateWithin(
                target, originalValue, definition.getMinimum(),
                lowerExclusive, upperExclusive);
        if (candidate != null) return candidate.doubleValue();

        throw new IllegalStateException(target.identity()
                + " has no distinct representable in-range value inside live neighbors "
                + lowerExclusive + ".." + upperExclusive);
    }

    public static double requireValidTemporaryValue(
            AeApplyRestoreValidationTargets.Target target,
            double originalValue, double requestedValue) {
        if (target == null) throw new IllegalArgumentException("validation target is required");
        if (!Double.isFinite(originalValue)) {
            throw new IllegalArgumentException("original working-tune value must be finite");
        }
        if (!Double.isFinite(requestedValue)) {
            throw new IllegalArgumentException("temporary value must be finite");
        }

        AeControllerDefinitionCatalog.Definition definition = target.getDefinition();
        if (requestedValue < definition.getMinimum() - EPSILON
                || requestedValue > definition.getMaximum() + EPSILON) {
            throw new IllegalArgumentException(target.identity() + " temporary value "
                    + requestedValue + " is outside controller range "
                    + definition.getMinimum() + ".." + definition.getMaximum());
        }

        if (definition.getKind() == AeControllerDefinitionCatalog.Kind.BITS) {
            int option = requireIntegral(requestedValue, target.identity() + " option index");
            String[] labels = definition.getOptionLabels();
            if (!isValidOption(labels, option)) {
                throw new IllegalArgumentException(target.identity()
                        + " option " + option + " is not a valid controller state");
            }
        } else if (isIntegerStorage(definition.getValueType())) {
            double scale = definition.getScale();
            if (!(scale > 0.0) || !Double.isFinite(scale)) {
                throw new IllegalStateException(target.identity()
                        + " has invalid controller scale " + scale);
            }
            double raw = requestedValue / scale;
            double nearest = Math.rint(raw);
            if (Math.abs(raw - nearest) > EPSILON) {
                throw new IllegalArgumentException(target.identity() + " temporary value "
                        + requestedValue + " is not representable on controller step " + scale);
            }
        }

        if (Math.abs(requestedValue - originalValue) <= EPSILON) {
            throw new IllegalArgumentException(target.identity()
                    + " temporary value must differ from the captured original value");
        }
        return requestedValue;
    }

    private static Double validatedCandidateWithin(
            AeApplyRestoreValidationTargets.Target target,
            double originalValue, double requestedValue,
            double lowerExclusive, double upperExclusive) {
        if (!inside(requestedValue, lowerExclusive, upperExclusive)) return null;
        try {
            return Double.valueOf(requireValidTemporaryValue(
                    target, originalValue, requestedValue));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean inside(double value, double lowerExclusive, double upperExclusive) {
        return value > lowerExclusive + EPSILON && value < upperExclusive - EPSILON;
    }

    private static boolean isValidOption(String[] labels, int option) {
        if (labels == null || option < 0 || option >= labels.length) return false;
        String label = labels[option] == null ? "" : labels[option].trim();
        return label.length() > 0 && !"INVALID".equalsIgnoreCase(label);
    }

    private static double automaticNumericStep(
            AeControllerDefinitionCatalog.Definition definition) {
        if (isIntegerStorage(definition.getValueType())) {
            double scale = definition.getScale();
            if (!(scale > 0.0) || !Double.isFinite(scale)) {
                throw new IllegalStateException("invalid controller scale " + scale);
            }
            return scale;
        }
        int decimals = Math.max(0, definition.getDecimals());
        double displayedStep = Math.pow(10.0, -decimals);
        return Math.max(displayedStep, EPSILON * 10.0);
    }

    private static int requireIntegral(double value, String label) {
        int result = (int) Math.rint(value);
        if (Math.abs(value - result) > EPSILON) {
            throw new IllegalArgumentException(label + " must be integral, not " + value);
        }
        return result;
    }

    private static boolean isIntegerStorage(AeControllerDefinitionCatalog.ValueType type) {
        return type == AeControllerDefinitionCatalog.ValueType.U08
                || type == AeControllerDefinitionCatalog.ValueType.S08
                || type == AeControllerDefinitionCatalog.ValueType.U16
                || type == AeControllerDefinitionCatalog.ValueType.S16
                || type == AeControllerDefinitionCatalog.ValueType.U32;
    }
}
