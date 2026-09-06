package se.anders.tunerstudio.aetuner.host;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exact TunerStudio/firmware representation metadata for the AE controller
 * surface. This is permanent production metadata owned by the canonical AE
 * setting layer, not a Validation-Lab-specific map.
 *
 * Authority: mainController.ini signature
 * rusEFI master.2026.08.26.MEGA144H7.2273317132.
 */
public final class AeControllerDefinitionCatalog {
    public enum Kind { SCALAR, ARRAY, BITS }
    public enum ValueType { U08, S08, U16, S16, U32, F32 }
    public enum Access { WRITABLE }

    public static final String AUTHORITY_SIGNATURE =
            "rusEFI master.2026.08.26.MEGA144H7.2273317132";

    public static final class Definition {
        private final String controllerName;
        private final Kind kind;
        private final ValueType valueType;
        private final int[] dimensions;
        private final String unit;
        private final double scale;
        private final double minimum;
        private final double maximum;
        private final int decimals;
        private final String bitRange;
        private final String[] optionLabels;
        private final Access access;

        private Definition(String controllerName, Kind kind, ValueType valueType,
                           int[] dimensions, String unit, double scale,
                           double minimum, double maximum, int decimals,
                           String bitRange, String[] optionLabels, Access access) {
            this.controllerName = controllerName;
            this.kind = kind;
            this.valueType = valueType;
            this.dimensions = dimensions == null ? new int[0] : dimensions.clone();
            this.unit = unit == null ? "" : unit;
            this.scale = scale;
            this.minimum = minimum;
            this.maximum = maximum;
            this.decimals = decimals;
            this.bitRange = bitRange == null ? "" : bitRange;
            this.optionLabels = optionLabels == null ? new String[0] : optionLabels.clone();
            this.access = access;
        }

        public String getControllerName() { return controllerName; }
        public Kind getKind() { return kind; }
        public ValueType getValueType() { return valueType; }
        public int[] getDimensions() { return dimensions.clone(); }
        public String getUnit() { return unit; }
        public double getScale() { return scale; }
        public double getMinimum() { return minimum; }
        public double getMaximum() { return maximum; }
        public int getDecimals() { return decimals; }
        public String getBitRange() { return bitRange; }
        public String[] getOptionLabels() { return optionLabels.clone(); }
        public Access getAccess() { return access; }
        public boolean isWritable() { return access == Access.WRITABLE; }
        public boolean isIndexed() { return dimensions.length > 0; }
        public int elementCount() {
            int count = 1;
            for (int dimension : dimensions) count *= dimension;
            return count;
        }

        @Override public String toString() {
            return controllerName + " " + kind + " " + valueType
                    + (dimensions.length == 0 ? "" : Arrays.toString(dimensions));
        }
    }

    private static final Map<String, Definition> BY_NAME;

    static {
        Map<String, Definition> definitions = new LinkedHashMap<String, Definition>();

        // Engagement / threshold.
        bits(definitions, AeParameterNames.TPS_AE_DETECT_MODE, ValueType.U08,
                "[0:2]", "Max step (legacy)", "Max step, timed", "Window span",
                "Rise from floor", "Dual stride, newest", "INVALID", "INVALID", "INVALID");
        scalar(definitions, AeParameterNames.TPS_AE_DELTA_WINDOW_MS, ValueType.U08,
                "ms", 1, 5, 250, 0);
        scalar(definitions, AeParameterNames.TPS_ACCEL_LOOKBACK, ValueType.U16,
                "sec", 0.0001, 0, 6, 4);
        bits(definitions, AeParameterNames.TPS_AE_FAST_CALLBACK, ValueType.U32,
                "[19:19]", "false", "true");
        scalar(definitions, AeParameterNames.DELTA_TPS_AVERAGE_ALPHA, ValueType.F32,
                "", 1, 0, 1, 3);
        array(definitions, AeParameterNames.DELTA_TPS_AVERAGE_CURVE_RPM_BINS,
                ValueType.U08, new int[]{8}, "RPM", 100, 0, 25000, 0);
        array(definitions, AeParameterNames.DELTA_TPS_AVERAGE_CURVE_MULTIPLIER,
                ValueType.U08, new int[]{8}, "mult", 0.1, 0, 25, 1);
        bits(definitions, AeParameterNames.TPS_AE_USE_DYNAMIC_THRESHOLD, ValueType.U32,
                "[8:8]", "false", "true");
        bits(definitions, AeParameterNames.TPS_AE_DYNAMIC_THRESHOLD_AVERAGE_STATIC_CURVE,
                ValueType.U32, "[9:9]", "false", "true");
        array(definitions, AeParameterNames.TPS_AE_THRESHOLD_RPM_BINS, ValueType.U08,
                new int[]{8}, "RPM", 100, 0, 25000, 0);
        array(definitions, AeParameterNames.TPS_AE_THRESHOLD_VALUES, ValueType.U16,
                new int[]{8}, "#", 0.001, 0, 60, 3);

        // TPS AE fuel and completion.
        bits(definitions, AeParameterNames.TPS_ACCEL_AE_ENABLED, ValueType.U32,
                "[1:1]", "false", "true");
        array(definitions, AeParameterNames.TPS_AE_CYCLE_CYCLE_BINS, ValueType.U08,
                new int[]{8}, "cycle", 1, 0, 250, 0);
        array(definitions, AeParameterNames.TPS_AE_CYCLE_TPS_TO_BINS, ValueType.U08,
                new int[]{8}, "%", 0.5, 0, 100, 1);
        array(definitions, AeParameterNames.TPS_AE_CYCLE_VALUES, ValueType.U08,
                new int[]{8, 8}, "%", 0.02, 0, 5, 2);
        scalar(definitions, AeParameterNames.TPS_AE_BURN_SKIP_INITIAL, ValueType.U08,
                "", 1, 0, 100, 0);
        bits(definitions, AeParameterNames.TPS_AE_RESETS_EGO, ValueType.U32,
                "[7:7]", "false", "true");
        scalar(definitions, AeParameterNames.NO_FUEL_TRIM_AFTER_ACCEL_TIME, ValueType.U08,
                "sec", 0.1, 0, 10, 1);

        // TPS AE compensation.
        array(definitions, AeParameterNames.TPS_AE_RPM_CORRECTION_BINS, ValueType.U08,
                new int[]{4}, "RPM", 50, 0, 12500, 0);
        array(definitions, AeParameterNames.TPS_AE_RPM_CORRECTION_VALUES, ValueType.U08,
                new int[]{4}, "multiplier", 0.02, 0, 5, 2);
        array(definitions, AeParameterNames.TPS_AE_SCALE_TPS_BINS, ValueType.U08,
                new int[]{5}, "TPS", 0.5, 0, 100, 1);
        // The authority INI itself labels this CLT axis as "TPS"; preserve it verbatim.
        array(definitions, AeParameterNames.TPS_AE_SCALE_CLT_BINS, ValueType.U08,
                new int[]{5}, "TPS", 1, 0, 250, 0);
        array(definitions, AeParameterNames.TPS_AE_SCALE_TABLE, ValueType.U08,
                new int[]{5, 5}, "mult", 0.01, 0, 2.5, 2);
        array(definitions, AeParameterNames.AE_CLT_CORR_BINS, ValueType.S08,
                new int[]{6}, "C", 2, -100, 250, 0);
        array(definitions, AeParameterNames.AE_CLT_CORR_VALUES, ValueType.U08,
                new int[]{6}, "mult", 0.05, 0, 10, 2);

        // MAP Predict.
        bits(definitions, AeParameterNames.USE_MAP_ESTIMATE_DURING_TRANSIENT,
                ValueType.U32, "[3:3]", "false", "true");
        array(definitions, AeParameterNames.MAP_ESTIMATE_RPM_BINS, ValueType.U08,
                new int[]{16}, "RPM", 100, 0, 25000, 0);
        array(definitions, AeParameterNames.MAP_ESTIMATE_TPS_BINS, ValueType.U08,
                new int[]{16}, "% TPS", 0.5, 0, 100, 1);
        array(definitions, AeParameterNames.MAP_ESTIMATE_TABLE, ValueType.U16,
                new int[]{16, 16}, "kPa", 0.01, 0, 650, 2);
        array(definitions, AeParameterNames.PREDICTIVE_MAP_BLEND_DURATION_BINS,
                ValueType.U08, new int[]{4}, "RPM", 50, 0, 12500, 0);
        array(definitions, AeParameterNames.PREDICTIVE_MAP_BLEND_DURATION_VALUES,
                ValueType.U08, new int[]{4}, "second", 0.02, 0, 5, 2);

        // Wall Wetting.
        bits(definitions, AeParameterNames.WALL_WETTING_AE_ENABLED, ValueType.U32,
                "[2:2]", "false", "true");
        bits(definitions, AeParameterNames.WALL_MODEL_TYPE, ValueType.U32,
                "[3:3]", "Basic (constants)", "Advanced (tables)");
        scalar(definitions, AeParameterNames.WALL_TAU, ValueType.F32,
                "Seconds", 1, 0, 3, 4);
        scalar(definitions, AeParameterNames.WALL_BETA, ValueType.F32,
                "Fraction", 1, 0, 1, 4);
        array(definitions, AeParameterNames.WALL_CLT_BINS, ValueType.S08,
                new int[]{8}, "deg C", 1, -40, 120, 0);
        array(definitions, AeParameterNames.WALL_TAU_CLT_VALUES, ValueType.U08,
                new int[]{8}, "", 0.01, 0, 2.5, 2);
        array(definitions, AeParameterNames.WALL_BETA_CLT_VALUES, ValueType.U08,
                new int[]{8}, "", 0.005, 0, 1, 3);
        array(definitions, AeParameterNames.WALL_RPM_BINS, ValueType.U16,
                new int[]{8}, "RPM", 1, 0, 25000, 0);
        array(definitions, AeParameterNames.WALL_MAP_BINS, ValueType.U16,
                new int[]{8}, "kPa", 1, 0, 250, 0);
        array(definitions, AeParameterNames.WALL_TAU_TABLE, ValueType.U08,
                new int[]{8, 8}, "", 0.01, 0, 2.5, 3);
        array(definitions, AeParameterNames.WALL_BETA_TABLE, ValueType.U08,
                new int[]{8, 8}, "", 0.01, 0, 2.5, 3);

        // Decel / tip-out.
        bits(definitions, AeParameterNames.TPS_DECEL_ENLEANMENT_ENABLED, ValueType.U32,
                "[31:31]", "false", "true");
        array(definitions, AeParameterNames.TPS_DECEL_THRESHOLD_RPM_BINS, ValueType.U08,
                new int[]{8}, "RPM", 100, 0, 25000, 0);
        array(definitions, AeParameterNames.TPS_DECEL_THRESHOLD_VALUES, ValueType.U16,
                new int[]{8}, "#", 0.001, 0, 60, 3);
        scalar(definitions, AeParameterNames.TPS_DECEL_HOLD_CYCLES, ValueType.U08,
                "cycles", 1, 0, 250, 0);
        array(definitions, AeParameterNames.TPS_DECEL_CYCLE_CYCLE_BINS, ValueType.U08,
                new int[]{8}, "cycle", 1, 0, 250, 0);
        array(definitions, AeParameterNames.TPS_DECEL_CYCLE_TPS_TO_BINS, ValueType.U08,
                new int[]{8}, "%", 0.5, 0, 100, 1);
        array(definitions, AeParameterNames.TPS_DECEL_CYCLE_VALUES, ValueType.U08,
                new int[]{8, 8}, "mult", 0.01, 0, 2.5, 2);
        array(definitions, AeParameterNames.TPS_DECEL_CLT_BINS, ValueType.S08,
                new int[]{6}, "C", 2, -100, 250, 0);
        array(definitions, AeParameterNames.TPS_DECEL_CLT_MULT, ValueType.U08,
                new int[]{6}, "mult", 0.01, 0, 1, 2);
        bits(definitions, AeParameterNames.USE_MAP_ESTIMATE_DURING_DECEL, ValueType.U32,
                "[28:28]", "false", "true");
        array(definitions, AeParameterNames.DECEL_MAP_BLEND_DURATION_BINS, ValueType.U08,
                new int[]{4}, "RPM", 50, 0, 12500, 0);
        array(definitions, AeParameterNames.DECEL_MAP_BLEND_DURATION_VALUES, ValueType.U08,
                new int[]{4}, "second", 0.02, 0, 5, 2);

        // Instant Fuel.
        bits(definitions, AeParameterNames.TPS_ACCEL_EXTRA_SHOT, ValueType.U32,
                "[26:26]", "false", "true");
        scalar(definitions, AeParameterNames.TPS_EXTRA_SHOT_MULT, ValueType.U08,
                "*", 0.01, 0, 2.5, 2);
        scalar(definitions, AeParameterNames.TPS_EXTRA_SHOT_TIMER, ValueType.U08,
                "#", 1, 0, 15, 0);
        array(definitions, AeParameterNames.TPS_AE_INSTANT_RPM_BINS, ValueType.U08,
                new int[]{5}, "RPM", 100, 0, 25000, 0);
        array(definitions, AeParameterNames.TPS_AE_INSTANT_RPM_MULTIPLIER, ValueType.U08,
                new int[]{5}, "mult", 0.01, 0, 2.5, 2);
        array(definitions, AeParameterNames.TPS_AE_INSTANT_TPS_BINS, ValueType.U08,
                new int[]{5}, "%TPS", 0.5, 0, 100, 1);
        array(definitions, AeParameterNames.TPS_AE_INSTANT_TPS_MULTIPLIER, ValueType.U08,
                new int[]{5}, "mult", 0.01, 0, 2.5, 2);
        array(definitions, AeParameterNames.TPS_AE_INSTANT_MAP_BINS, ValueType.U08,
                new int[]{5}, "kPa", 1, 0, 255, 0);
        array(definitions, AeParameterNames.TPS_AE_INSTANT_MAP_MULTIPLIER, ValueType.U08,
                new int[]{5}, "mult", 0.01, 0, 2.5, 2);
        array(definitions, AeParameterNames.TPS_AE_INSTANT_CLT_BINS, ValueType.U08,
                new int[]{5}, "C", 1, 0, 220, 1);
        array(definitions, AeParameterNames.TPS_AE_INSTANT_CLT_MULTIPLIER, ValueType.U08,
                new int[]{5}, "mult", 0.01, 0, 2.5, 2);
        array(definitions, AeParameterNames.TPS_AE_INSTANT_DELTA_TPS_BINS, ValueType.U16,
                new int[]{5}, "%", 0.01, 0, 100, 2);
        array(definitions, AeParameterNames.TPS_AE_INSTANT_DELTA_TPS_MULTIPLIER, ValueType.U08,
                new int[]{5}, "mult", 0.01, 0, 2.5, 2);

        BY_NAME = Collections.unmodifiableMap(definitions);
    }

    private AeControllerDefinitionCatalog() { }

    private static void scalar(Map<String, Definition> target, String name,
                               ValueType type, String unit, double scale,
                               double minimum, double maximum, int decimals) {
        put(target, new Definition(name, Kind.SCALAR, type, new int[0], unit,
                scale, minimum, maximum, decimals, "", null, Access.WRITABLE));
    }

    private static void array(Map<String, Definition> target, String name,
                              ValueType type, int[] dimensions, String unit,
                              double scale, double minimum, double maximum,
                              int decimals) {
        put(target, new Definition(name, Kind.ARRAY, type, dimensions, unit,
                scale, minimum, maximum, decimals, "", null, Access.WRITABLE));
    }

    private static void bits(Map<String, Definition> target, String name,
                             ValueType type, String bitRange, String... labels) {
        put(target, new Definition(name, Kind.BITS, type, new int[0], "",
                1, 0, labels.length - 1, 0, bitRange, labels, Access.WRITABLE));
    }

    private static void put(Map<String, Definition> target, Definition definition) {
        Definition previous = target.put(definition.controllerName, definition);
        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate AE controller definition: " + definition.controllerName);
        }
    }

    public static Definition find(String controllerName) {
        if (controllerName == null) return null;
        return BY_NAME.get(controllerName);
    }

    public static Map<String, Definition> allByName() { return BY_NAME; }
}
