package se.anders.tunerstudio.aetuner.host;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical catalog of settings that AE Tuner may analyze and tune as AE settings.
 *
 * Catalog membership means a setting is eligible for the common guarded
 * working-tune Apply/Restore path whenever its method produces an explicit
 * reviewed ProposalWritePlan. Recommendation/evidence logic decides whether a
 * changed value exists; there is no separate read-only product-maturity gate.
 *
 * The first real use of a new controller setting/representation gets one quick
 * TunerStudio working-tune Apply -> readback -> Restore confirmation. That test
 * may be performed engine-off; it validates the setting mapping/write contract,
 * not combustion behavior. Once confirmed, normal guarded Apply remains
 * available. Burn is outside AE Tuner's current write contract.
 */
public final class AeTuningParameterCatalog {
    public enum Subsystem {
        ENGAGEMENT_DETECTION,
        TPS_AE,
        MAP_PREDICT,
        WALL_WETTING,
        INSTANT_FUEL
    }

    public enum Shape {
        BOOLEAN,
        ENUM,
        SCALAR,
        CURVE_AXIS,
        CURVE_VALUES,
        TABLE
    }

    /**
     * Dependency order for evidence. A change at an earlier tier can change the
     * meaning or population of evidence collected for later tiers.
     */
    public enum DependencyTier {
        DETECTOR_MODEL,
        DETECTOR_TIMING,
        DETECTOR_THRESHOLD,
        METHOD_ACTIVATION,
        TRANSIENT_RESPONSE,
        FINE_CORRECTION
    }

    public static final class Parameter {
        private final String controllerName;
        private final String displayName;
        private final Subsystem subsystem;
        private final Shape shape;
        private final String unit;
        private final DependencyTier dependencyTier;
        private final boolean evidenceBreaking;

        private Parameter(String controllerName, String displayName,
                          Subsystem subsystem, Shape shape, String unit,
                          DependencyTier dependencyTier, boolean evidenceBreaking) {
            this.controllerName = controllerName;
            this.displayName = displayName;
            this.subsystem = subsystem;
            this.shape = shape;
            this.unit = unit == null ? "" : unit;
            this.dependencyTier = dependencyTier;
            this.evidenceBreaking = evidenceBreaking;
        }

        public String getControllerName() { return controllerName; }
        public String getDisplayName() { return displayName; }
        public Subsystem getSubsystem() { return subsystem; }
        public Shape getShape() { return shape; }
        public String getUnit() { return unit; }
        public DependencyTier getDependencyTier() { return dependencyTier; }
        public boolean isEvidenceBreaking() { return evidenceBreaking; }

        public boolean isUpstreamOf(Parameter other) {
            return other != null
                    && dependencyTier.ordinal() < other.dependencyTier.ordinal();
        }

        @Override public String toString() {
            return displayName + " [" + controllerName + "]";
        }
    }

    private static final List<Parameter> ALL;
    private static final Map<String, Parameter> BY_CONTROLLER_NAME;

    static {
        List<Parameter> all = new ArrayList<Parameter>();

        // Engagement / detection: deliberately upstream of all fuel recipes.
        add(all, AeParameterNames.TPS_AE_DETECT_MODE, "Engagement model",
                Subsystem.ENGAGEMENT_DETECTION, Shape.ENUM, "",
                DependencyTier.DETECTOR_MODEL, true);
        add(all, AeParameterNames.TPS_AE_DELTA_WINDOW_MS, "Delta window",
                Subsystem.ENGAGEMENT_DETECTION, Shape.SCALAR, "ms",
                DependencyTier.DETECTOR_TIMING, true);
        add(all, AeParameterNames.TPS_ACCEL_LOOKBACK, "Sample Length",
                Subsystem.ENGAGEMENT_DETECTION, Shape.SCALAR, "s",
                DependencyTier.DETECTOR_TIMING, true);
        add(all, AeParameterNames.TPS_AE_FAST_CALLBACK, "TPS AE callback rate",
                Subsystem.ENGAGEMENT_DETECTION, Shape.BOOLEAN, "",
                DependencyTier.DETECTOR_TIMING, true);
        add(all, AeParameterNames.DELTA_TPS_AVERAGE_ALPHA,
                "Delta TPS Average Smoothing Factor",
                Subsystem.ENGAGEMENT_DETECTION, Shape.SCALAR, "",
                DependencyTier.DETECTOR_THRESHOLD, true);
        add(all, AeParameterNames.TPS_AE_USE_DYNAMIC_THRESHOLD,
                "Use calculated threshold from averaged delta TPS",
                Subsystem.ENGAGEMENT_DETECTION, Shape.BOOLEAN, "",
                DependencyTier.DETECTOR_THRESHOLD, true);
        add(all, AeParameterNames.TPS_AE_DYNAMIC_THRESHOLD_AVERAGE_STATIC_CURVE,
                "Average static and dynamic TPS thresholds",
                Subsystem.ENGAGEMENT_DETECTION, Shape.BOOLEAN, "",
                DependencyTier.DETECTOR_THRESHOLD, true);
        add(all, AeParameterNames.TPS_AE_THRESHOLD_RPM_BINS,
                "TPS AE Rate of change vs RPM — RPM bins",
                Subsystem.ENGAGEMENT_DETECTION, Shape.CURVE_AXIS, "RPM",
                DependencyTier.DETECTOR_THRESHOLD, true);
        add(all, AeParameterNames.TPS_AE_THRESHOLD_VALUES,
                "TPS AE Rate of change vs RPM — threshold",
                Subsystem.ENGAGEMENT_DETECTION, Shape.CURVE_VALUES, "delta TPS",
                DependencyTier.DETECTOR_THRESHOLD, true);

        // TPS AE fuel amount/duration.
        add(all, AeParameterNames.TPS_ACCEL_AE_ENABLED,
                "Enable TPS Acceleration Enrichment",
                Subsystem.TPS_AE, Shape.BOOLEAN, "",
                DependencyTier.METHOD_ACTIVATION, true);
        add(all, AeParameterNames.TPS_AE_CYCLE_CYCLE_BINS,
                "TPS AE fuel multiplier — engine cycle bins",
                Subsystem.TPS_AE, Shape.CURVE_AXIS, "cycle",
                DependencyTier.TRANSIENT_RESPONSE, false);
        add(all, AeParameterNames.TPS_AE_CYCLE_TPS_TO_BINS,
                "TPS AE fuel multiplier — TPS-to bins",
                Subsystem.TPS_AE, Shape.CURVE_AXIS, "%",
                DependencyTier.TRANSIENT_RESPONSE, false);
        add(all, AeParameterNames.TPS_AE_CYCLE_VALUES,
                "TPS AE: Fuel multiplier by engine cycle",
                Subsystem.TPS_AE, Shape.TABLE, "%",
                DependencyTier.TRANSIENT_RESPONSE, false);
        add(all, AeParameterNames.TPS_AE_BURN_SKIP_INITIAL,
                "TPS AE Burn Skip count",
                Subsystem.TPS_AE, Shape.SCALAR, "count",
                DependencyTier.FINE_CORRECTION, false);
        add(all, AeParameterNames.TPS_AE_RESETS_EGO,
                "TPS Accel resets EGO to 0%",
                Subsystem.TPS_AE, Shape.BOOLEAN, "",
                DependencyTier.FINE_CORRECTION, false);
        add(all, AeParameterNames.NO_FUEL_TRIM_AFTER_ACCEL_TIME,
                "Inhibit closed loop fuel after accel",
                Subsystem.TPS_AE, Shape.SCALAR, "s",
                DependencyTier.FINE_CORRECTION, false);

        // MAP Predict.
        add(all, AeParameterNames.USE_MAP_ESTIMATE_DURING_TRANSIENT,
                "Use MAP estimate during transient",
                Subsystem.MAP_PREDICT, Shape.BOOLEAN, "",
                DependencyTier.METHOD_ACTIVATION, true);
        add(all, AeParameterNames.MAP_ESTIMATE_RPM_BINS,
                "MAP Estimate — RPM bins",
                Subsystem.MAP_PREDICT, Shape.CURVE_AXIS, "RPM",
                DependencyTier.TRANSIENT_RESPONSE, false);
        add(all, AeParameterNames.MAP_ESTIMATE_TPS_BINS,
                "MAP Estimate — TPS bins",
                Subsystem.MAP_PREDICT, Shape.CURVE_AXIS, "%",
                DependencyTier.TRANSIENT_RESPONSE, false);
        add(all, AeParameterNames.MAP_ESTIMATE_TABLE,
                "MAP estimate table",
                Subsystem.MAP_PREDICT, Shape.TABLE, "kPa",
                DependencyTier.TRANSIENT_RESPONSE, false);
        add(all, AeParameterNames.PREDICTIVE_MAP_BLEND_DURATION_BINS,
                "Predictive MAP Blend Duration — RPM bins",
                Subsystem.MAP_PREDICT, Shape.CURVE_AXIS, "RPM",
                DependencyTier.TRANSIENT_RESPONSE, false);
        add(all, AeParameterNames.PREDICTIVE_MAP_BLEND_DURATION_VALUES,
                "Predictive MAP Blend Duration",
                Subsystem.MAP_PREDICT, Shape.CURVE_VALUES, "s",
                DependencyTier.TRANSIENT_RESPONSE, false);

        // Wall Wetting.
        add(all, AeParameterNames.WALL_WETTING_AE_ENABLED,
                "Enable wall wetting Acceleration Enrichment",
                Subsystem.WALL_WETTING, Shape.BOOLEAN, "",
                DependencyTier.METHOD_ACTIVATION, true);
        add(all, AeParameterNames.WALL_MODEL_TYPE,
                "Wall fueling model type",
                Subsystem.WALL_WETTING, Shape.ENUM, "",
                DependencyTier.METHOD_ACTIVATION, true);
        add(all, AeParameterNames.WALL_TAU,
                "Evaporation time constant / tau",
                Subsystem.WALL_WETTING, Shape.SCALAR, "",
                DependencyTier.TRANSIENT_RESPONSE, false);
        add(all, AeParameterNames.WALL_BETA,
                "Added to wall coefficient / beta",
                Subsystem.WALL_WETTING, Shape.SCALAR, "",
                DependencyTier.TRANSIENT_RESPONSE, false);
        add(all, AeParameterNames.WALL_TAU_TABLE,
                "Evap from wall table",
                Subsystem.WALL_WETTING, Shape.TABLE, "",
                DependencyTier.TRANSIENT_RESPONSE, false);
        add(all, AeParameterNames.WALL_BETA_TABLE,
                "Stick to wall table",
                Subsystem.WALL_WETTING, Shape.TABLE, "",
                DependencyTier.TRANSIENT_RESPONSE, false);

        // Instant Fuel.
        add(all, AeParameterNames.TPS_ACCEL_EXTRA_SHOT,
                "Instant Fuel Pulse",
                Subsystem.INSTANT_FUEL, Shape.BOOLEAN, "",
                DependencyTier.METHOD_ACTIVATION, true);
        add(all, AeParameterNames.TPS_EXTRA_SHOT_MULT,
                "Instant Fuel Pulse Multiplier (global)",
                Subsystem.INSTANT_FUEL, Shape.SCALAR, "mult",
                DependencyTier.TRANSIENT_RESPONSE, false);
        add(all, AeParameterNames.TPS_EXTRA_SHOT_TIMER,
                "Instant Fuel Pulse Inhibit Cycles",
                Subsystem.INSTANT_FUEL, Shape.SCALAR, "cycle",
                DependencyTier.FINE_CORRECTION, false);

        Map<String, Parameter> byName = new LinkedHashMap<String, Parameter>();
        for (Parameter parameter : all) {
            Parameter previous = byName.put(parameter.controllerName, parameter);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate AE parameter catalog entry: " + parameter.controllerName);
            }
        }
        ALL = Collections.unmodifiableList(all);
        BY_CONTROLLER_NAME = Collections.unmodifiableMap(byName);
    }

    private AeTuningParameterCatalog() { }

    private static void add(List<Parameter> target, String controllerName,
                            String displayName, Subsystem subsystem, Shape shape,
                            String unit, DependencyTier tier, boolean evidenceBreaking) {
        target.add(new Parameter(controllerName, displayName, subsystem, shape,
                unit, tier, evidenceBreaking));
    }

    public static List<Parameter> all() {
        return ALL;
    }

    public static Parameter find(String controllerName) {
        if (controllerName == null) return null;
        return BY_CONTROLLER_NAME.get(controllerName);
    }

    public static List<Parameter> forSubsystem(Subsystem subsystem) {
        if (subsystem == null) return Collections.emptyList();
        List<Parameter> result = new ArrayList<Parameter>();
        for (Parameter parameter : ALL) {
            if (parameter.subsystem == subsystem) result.add(parameter);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * True when changing {@code changed} can invalidate evidence gathered for
     * {@code dependent} because the changed setting is earlier in the AE chain.
     */
    public static boolean invalidatesEvidence(Parameter changed, Parameter dependent) {
        if (changed == null || dependent == null || !changed.evidenceBreaking) return false;
        return changed == dependent || changed.isUpstreamOf(dependent);
    }
}
