package se.anders.tunerstudio.aetuner.host;

import se.anders.tunerstudio.aetuner.guided.GuidedTuningArea;
import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AeTuningParameterCatalogRegressionTest {
    private AeTuningParameterCatalogRegressionTest() { }

    public static void main(String[] args) {
        currentEngagementParametersUseExactControllerNames();
        engagementCatalogPreservesRepresentations();
        completePlannedSurfacesUseExactControllerNamesAndShapes();
        everyAeSubsystemHasCataloguedParameters();
        everyGuidedTaskHasExplicitInventoryClassification();
        everyGuidedWriteTargetExistsInCanonicalCatalog();
        everyCanonicalTargetHasExactControllerMetadata();
        criticalMetadataMatchesFrozenIniAuthority();
        noTargetClassificationsCannotHideControllerTargets();
        detectorSettingsAreUpstreamEvidenceDependencies();
        nonAeParametersStayOutsideTheCatalog();
        controllerNamesAreUnique();
        System.out.println("AeTuningParameterCatalogRegressionTest passed");
    }

    private static void currentEngagementParametersUseExactControllerNames() {
        requireParameter(AeParameterNames.TPS_AE_DETECT_MODE,
                "Engagement model", AeTuningParameterCatalog.Subsystem.ENGAGEMENT_DETECTION);
        requireParameter(AeParameterNames.TPS_AE_DELTA_WINDOW_MS,
                "Delta window", AeTuningParameterCatalog.Subsystem.ENGAGEMENT_DETECTION);
        requireParameter(AeParameterNames.TPS_ACCEL_LOOKBACK,
                "Sample Length", AeTuningParameterCatalog.Subsystem.ENGAGEMENT_DETECTION);
        requireParameter(AeParameterNames.TPS_AE_THRESHOLD_VALUES,
                "TPS AE Rate of change vs RPM — threshold",
                AeTuningParameterCatalog.Subsystem.ENGAGEMENT_DETECTION);
        require("tpsAeDynamicTresholdAverageStaticCurve".equals(
                        AeParameterNames.TPS_AE_DYNAMIC_THRESHOLD_AVERAGE_STATIC_CURVE),
                "controller's authoritative Treshold spelling must not be silently corrected");
    }

    private static void engagementCatalogPreservesRepresentations() {
        requireShape(AeParameterNames.TPS_AE_DELTA_WINDOW_MS,
                AeTuningParameterCatalog.Shape.SCALAR);
        requireShape(AeParameterNames.TPS_ACCEL_LOOKBACK,
                AeTuningParameterCatalog.Shape.SCALAR);
        requireShape(AeParameterNames.TPS_AE_DETECT_MODE,
                AeTuningParameterCatalog.Shape.ENUM);
        requireShape(AeParameterNames.TPS_AE_FAST_CALLBACK,
                AeTuningParameterCatalog.Shape.BOOLEAN);
        requireShape(AeParameterNames.DELTA_TPS_AVERAGE_CURVE_RPM_BINS,
                AeTuningParameterCatalog.Shape.CURVE_AXIS);
        requireShape(AeParameterNames.DELTA_TPS_AVERAGE_CURVE_MULTIPLIER,
                AeTuningParameterCatalog.Shape.CURVE_VALUES);
        requireShape(AeParameterNames.TPS_AE_THRESHOLD_VALUES,
                AeTuningParameterCatalog.Shape.CURVE_VALUES);
    }

    private static void completePlannedSurfacesUseExactControllerNamesAndShapes() {
        require("tps_ae_scale_multiplier".equals(AeParameterNames.TPS_AE_SCALE_TABLE),
                "TPS-vs-CLT AE scale table must retain the INI's exact snake-case name");
        requireShape(AeParameterNames.TPS_AE_SCALE_TABLE, AeTuningParameterCatalog.Shape.TABLE);
        requireShape(AeParameterNames.WALL_TAU_CLT_VALUES,
                AeTuningParameterCatalog.Shape.CURVE_VALUES);
        requireShape(AeParameterNames.WALL_TAU_TABLE, AeTuningParameterCatalog.Shape.TABLE);
        requireShape(AeParameterNames.TPS_DECEL_THRESHOLD_VALUES,
                AeTuningParameterCatalog.Shape.CURVE_VALUES);
        requireShape(AeParameterNames.TPS_DECEL_CYCLE_VALUES,
                AeTuningParameterCatalog.Shape.TABLE);
        requireShape(AeParameterNames.USE_MAP_ESTIMATE_DURING_DECEL,
                AeTuningParameterCatalog.Shape.BOOLEAN);
        requireShape(AeParameterNames.TPS_AE_INSTANT_DELTA_TPS_BINS,
                AeTuningParameterCatalog.Shape.CURVE_AXIS);
        requireShape(AeParameterNames.TPS_AE_INSTANT_DELTA_TPS_MULTIPLIER,
                AeTuningParameterCatalog.Shape.CURVE_VALUES);
        requireShape(AeParameterNames.TPS_AE_INSTANT_CLT_MULTIPLIER,
                AeTuningParameterCatalog.Shape.CURVE_VALUES);
    }

    private static void everyAeSubsystemHasCataloguedParameters() {
        for (AeTuningParameterCatalog.Subsystem subsystem
                : AeTuningParameterCatalog.Subsystem.values()) {
            List<AeTuningParameterCatalog.Parameter> parameters =
                    AeTuningParameterCatalog.forSubsystem(subsystem);
            require(!parameters.isEmpty(),
                    "general AE catalog omitted subsystem " + subsystem);
        }
    }

    private static void everyGuidedTaskHasExplicitInventoryClassification() {
        Set<GuidedTuningRecipe> seen = new HashSet<GuidedTuningRecipe>();
        for (GuidedControllerSettingInventory.TaskInventory item
                : GuidedControllerSettingInventory.all()) {
            require(item.getArea() != null, "inventory area is required for " + item.getTask());
            require(item.getTask() != null, "inventory task is required");
            require(item.getClassification() != null,
                    "inventory classification is required for " + item.getTask());
            require(item.getProductionWriteSupport() != null,
                    "inventory production-write support is required for " + item.getTask());
            require(item.getArea().contains(item.getTask()),
                    "inventory area/task mismatch for " + item.getTask());
            require(seen.add(item.getTask()),
                    "Guided task appears more than once in controller inventory: " + item.getTask());
        }

        int recipeTaskCount = 0;
        for (GuidedTuningArea area : GuidedTuningArea.values()) {
            for (GuidedTuningRecipe task : area.tasks()) {
                recipeTaskCount++;
                GuidedControllerSettingInventory.TaskInventory item =
                        GuidedControllerSettingInventory.find(task);
                require(item != null,
                        "Guided task disappeared from controller inventory: " + area + " / " + task);
                require(item.getArea() == area,
                        "Guided inventory moved task to wrong area: " + task);
            }
        }
        require(seen.size() == recipeTaskCount,
                "Guided inventory count no longer matches navigation task count");
    }

    private static void everyGuidedWriteTargetExistsInCanonicalCatalog() {
        for (GuidedControllerSettingInventory.TaskInventory item
                : GuidedControllerSettingInventory.all()) {
            boolean writeClassification =
                    item.getClassification()
                            == GuidedControllerSettingInventory.Classification.WRITABLE_VALIDATION_REQUIRED
                    || item.getClassification()
                            == GuidedControllerSettingInventory.Classification.PLANNED_WRITABLE_MAPPING;
            if (writeClassification) {
                require(item.hasWriteTargets(),
                        "writable Guided task has no declared controller targets: " + item.getTask());
            }
            Set<String> taskTargets = new HashSet<String>();
            for (String controllerName : item.getControllerTargets()) {
                require(taskTargets.add(controllerName),
                        "Guided task declares duplicate target " + controllerName
                                + ": " + item.getTask());
                require(AeTuningParameterCatalog.find(controllerName) != null,
                        "Guided task target is absent from canonical AE catalog: "
                                + item.getTask() + " -> " + controllerName);
            }
        }
    }

    private static void everyCanonicalTargetHasExactControllerMetadata() {
        require(AeControllerDefinitionCatalog.AUTHORITY_SIGNATURE.equals(
                        "rusEFI master.2026.08.26.MEGA144H7.2273317132"),
                "controller metadata authority signature changed unexpectedly");
        for (AeTuningParameterCatalog.Parameter parameter : AeTuningParameterCatalog.all()) {
            AeControllerDefinitionCatalog.Definition definition =
                    parameter.getControllerDefinition();
            require(definition != null,
                    "canonical AE setting lacks exact controller metadata: "
                            + parameter.getControllerName());
            require(definition.isWritable(),
                    "canonical AE setting is unexpectedly not writable: "
                            + parameter.getControllerName());
        }
        for (GuidedControllerSettingInventory.TaskInventory item
                : GuidedControllerSettingInventory.all()) {
            for (String controllerName : item.getControllerTargets()) {
                require(AeControllerDefinitionCatalog.find(controllerName) != null,
                        "Guided target lacks exact controller metadata: " + controllerName);
            }
        }
    }

    private static void criticalMetadataMatchesFrozenIniAuthority() {
        requireDefinition(AeParameterNames.TPS_AE_DELTA_WINDOW_MS,
                AeControllerDefinitionCatalog.Kind.SCALAR,
                AeControllerDefinitionCatalog.ValueType.U08, 1, 5, 250, 1);
        requireDefinition(AeParameterNames.DELTA_TPS_AVERAGE_CURVE_RPM_BINS,
                AeControllerDefinitionCatalog.Kind.ARRAY,
                AeControllerDefinitionCatalog.ValueType.U08, 100, 0, 25000, 8);
        requireDefinition(AeParameterNames.DELTA_TPS_AVERAGE_CURVE_MULTIPLIER,
                AeControllerDefinitionCatalog.Kind.ARRAY,
                AeControllerDefinitionCatalog.ValueType.U08, 0.1, 0, 25, 8);
        requireDefinition(AeParameterNames.TPS_AE_THRESHOLD_VALUES,
                AeControllerDefinitionCatalog.Kind.ARRAY,
                AeControllerDefinitionCatalog.ValueType.U16, 0.001, 0, 60, 8);
        requireDefinition(AeParameterNames.TPS_AE_CYCLE_VALUES,
                AeControllerDefinitionCatalog.Kind.ARRAY,
                AeControllerDefinitionCatalog.ValueType.U08, 0.02, 0, 5, 64);
        requireDefinition(AeParameterNames.TPS_AE_BURN_SKIP_INITIAL,
                AeControllerDefinitionCatalog.Kind.SCALAR,
                AeControllerDefinitionCatalog.ValueType.U08, 1, 0, 100, 1);
        requireDefinition(AeParameterNames.MAP_ESTIMATE_TABLE,
                AeControllerDefinitionCatalog.Kind.ARRAY,
                AeControllerDefinitionCatalog.ValueType.U16, 0.01, 0, 650, 256);
        requireDefinition(AeParameterNames.TPS_AE_SCALE_TABLE,
                AeControllerDefinitionCatalog.Kind.ARRAY,
                AeControllerDefinitionCatalog.ValueType.U08, 0.01, 0, 2.5, 25);
        requireDefinition(AeParameterNames.WALL_TAU_TABLE,
                AeControllerDefinitionCatalog.Kind.ARRAY,
                AeControllerDefinitionCatalog.ValueType.U08, 0.01, 0, 2.5, 64);
        requireDefinition(AeParameterNames.TPS_DECEL_CYCLE_VALUES,
                AeControllerDefinitionCatalog.Kind.ARRAY,
                AeControllerDefinitionCatalog.ValueType.U08, 0.01, 0, 2.5, 64);
        requireDefinition(AeParameterNames.TPS_AE_INSTANT_DELTA_TPS_BINS,
                AeControllerDefinitionCatalog.Kind.ARRAY,
                AeControllerDefinitionCatalog.ValueType.U16, 0.01, 0, 100, 5);

        AeControllerDefinitionCatalog.Definition model =
                AeControllerDefinitionCatalog.find(AeParameterNames.TPS_AE_DETECT_MODE);
        require(model != null && "[0:2]".equals(model.getBitRange()),
                "engagement-model bit range changed from INI authority");
        require(model.getOptionLabels().length == 8,
                "engagement-model enum option count changed from INI authority");
        requireBit(AeParameterNames.TPS_AE_FAST_CALLBACK, "[19:19]");
        requireBit(AeParameterNames.TPS_AE_RESETS_EGO, "[7:7]");
        requireBit(AeParameterNames.USE_MAP_ESTIMATE_DURING_TRANSIENT, "[3:3]");
        requireBit(AeParameterNames.USE_MAP_ESTIMATE_DURING_DECEL, "[28:28]");
    }

    private static void noTargetClassificationsCannotHideControllerTargets() {
        for (GuidedControllerSettingInventory.TaskInventory item
                : GuidedControllerSettingInventory.all()) {
            boolean mustBeEmpty =
                    item.getClassification()
                            == GuidedControllerSettingInventory.Classification.READ_ONLY_EVIDENCE_DIAGNOSTIC
                    || item.getClassification()
                            == GuidedControllerSettingInventory.Classification.NO_DIRECT_CONTROLLER_WRITE_TARGETS;
            if (mustBeEmpty) {
                require(!item.hasWriteTargets(),
                        "read-only/no-target Guided task unexpectedly declares a write target: "
                                + item.getTask());
                require(item.getProductionWriteSupport()
                                == GuidedControllerSettingInventory.ProductionWriteSupport.NONE,
                        "read-only/no-target task cannot claim production write support: "
                                + item.getTask());
            }
        }
    }

    private static void detectorSettingsAreUpstreamEvidenceDependencies() {
        AeTuningParameterCatalog.Parameter model =
                AeTuningParameterCatalog.find(AeParameterNames.TPS_AE_DETECT_MODE);
        AeTuningParameterCatalog.Parameter window =
                AeTuningParameterCatalog.find(AeParameterNames.TPS_AE_DELTA_WINDOW_MS);
        AeTuningParameterCatalog.Parameter threshold =
                AeTuningParameterCatalog.find(AeParameterNames.TPS_AE_THRESHOLD_VALUES);
        AeTuningParameterCatalog.Parameter blend =
                AeTuningParameterCatalog.find(AeParameterNames.PREDICTIVE_MAP_BLEND_DURATION_VALUES);
        AeTuningParameterCatalog.Parameter tpsFuel =
                AeTuningParameterCatalog.find(AeParameterNames.TPS_AE_CYCLE_VALUES);

        require(AeTuningParameterCatalog.invalidatesEvidence(model, window),
                "changing engagement model must invalidate detector-timing evidence");
        require(AeTuningParameterCatalog.invalidatesEvidence(window, threshold),
                "changing detector timing must invalidate threshold evidence");
        require(AeTuningParameterCatalog.invalidatesEvidence(model, blend),
                "changing engagement model must invalidate downstream MAP Predict evidence");
        require(AeTuningParameterCatalog.invalidatesEvidence(window, tpsFuel),
                "changing Delta Window must invalidate downstream TPS AE fuel evidence");
        require(!AeTuningParameterCatalog.invalidatesEvidence(tpsFuel, model),
                "downstream TPS fuel shape must not invalidate upstream detector evidence");
    }

    private static void nonAeParametersStayOutsideTheCatalog() {
        require(AeTuningParameterCatalog.find("veTable") == null,
                "VE must remain outside AE Tuner tuning scope");
        require(AeTuningParameterCatalog.find("ignitionTable") == null,
                "ignition must remain outside AE Tuner tuning scope");
    }

    private static void controllerNamesAreUnique() {
        Set<String> names = new HashSet<String>();
        for (AeTuningParameterCatalog.Parameter parameter : AeTuningParameterCatalog.all()) {
            require(names.add(parameter.getControllerName()),
                    "duplicate controller parameter in AE catalog: "
                            + parameter.getControllerName());
        }
    }

    private static void requireParameter(String controllerName, String displayName,
                                         AeTuningParameterCatalog.Subsystem subsystem) {
        AeTuningParameterCatalog.Parameter parameter = AeTuningParameterCatalog.find(controllerName);
        require(parameter != null, "catalog omitted " + controllerName);
        require(displayName.equals(parameter.getDisplayName()),
                "catalog display name changed for " + controllerName);
        require(parameter.getSubsystem() == subsystem,
                "catalog subsystem changed for " + controllerName);
    }

    private static void requireShape(String controllerName,
                                     AeTuningParameterCatalog.Shape shape) {
        AeTuningParameterCatalog.Parameter parameter = AeTuningParameterCatalog.find(controllerName);
        require(parameter != null, "catalog omitted " + controllerName);
        require(parameter.getShape() == shape,
                "catalog representation changed for " + controllerName
                        + ": expected " + shape + " but was " + parameter.getShape());
    }

    private static void requireDefinition(String controllerName,
                                          AeControllerDefinitionCatalog.Kind kind,
                                          AeControllerDefinitionCatalog.ValueType type,
                                          double scale, double minimum,
                                          double maximum, int elementCount) {
        AeControllerDefinitionCatalog.Definition definition =
                AeControllerDefinitionCatalog.find(controllerName);
        require(definition != null, "controller definition omitted " + controllerName);
        require(definition.getKind() == kind, "controller kind changed for " + controllerName);
        require(definition.getValueType() == type,
                "controller value type changed for " + controllerName);
        require(Math.abs(definition.getScale() - scale) < 1e-12,
                "controller scale changed for " + controllerName);
        require(Math.abs(definition.getMinimum() - minimum) < 1e-12,
                "controller minimum changed for " + controllerName);
        require(Math.abs(definition.getMaximum() - maximum) < 1e-12,
                "controller maximum changed for " + controllerName);
        require(definition.elementCount() == elementCount,
                "controller element count changed for " + controllerName);
    }

    private static void requireBit(String controllerName, String bitRange) {
        AeControllerDefinitionCatalog.Definition definition =
                AeControllerDefinitionCatalog.find(controllerName);
        require(definition != null, "controller definition omitted " + controllerName);
        require(definition.getKind() == AeControllerDefinitionCatalog.Kind.BITS,
                "controller is no longer a bit field: " + controllerName);
        require(bitRange.equals(definition.getBitRange()),
                "controller bit range changed for " + controllerName);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
