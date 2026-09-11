package se.anders.tunerstudio.aetuner.host;

import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;

/**
 * The 816/816 physical campaign proved the common write layer. Product write
 * availability must therefore no longer be held back by recommendation maturity.
 * PARTIAL means a real baseline evidence algorithm exists and still requires
 * vehicle confirmation/refinement; it does not mean the controller surface is
 * absent or unwritable.
 */
public final class GuidedWriteCapabilityMaturityRegressionTest {
    private GuidedWriteCapabilityMaturityRegressionTest() { }

    public static void main(String[] args) {
        everyDeclaredGuidedSurfaceHasCurrentProductionWriteSupport();
        everyDeclaredTargetRemainsInsidePhysicalValidationInventory();
        recommendationMaturityStaysIndependentAndTruthful();
        System.out.println("GuidedWriteCapabilityMaturityRegressionTest passed");
    }

    private static void everyDeclaredGuidedSurfaceHasCurrentProductionWriteSupport() {
        for (GuidedControllerSettingInventory.TaskInventory item
                : GuidedControllerSettingInventory.all()) {
            if (item.hasWriteTargets()) {
                require(item.getProductionWriteSupport()
                                == GuidedControllerSettingInventory.ProductionWriteSupport.CURRENT,
                        "validated Guided write surface was artificially staged: "
                                + item.getTask() + " -> " + item.getProductionWriteSupport());
            } else {
                require(item.getProductionWriteSupport()
                                == GuidedControllerSettingInventory.ProductionWriteSupport.NONE,
                        "no-target Guided task claimed production write support: "
                                + item.getTask());
            }
        }
    }

    private static void everyDeclaredTargetRemainsInsidePhysicalValidationInventory() {
        for (GuidedControllerSettingInventory.TaskInventory item
                : GuidedControllerSettingInventory.all()) {
            for (String controllerName : item.getControllerTargets()) {
                AeControllerDefinitionCatalog.Definition definition =
                        AeControllerDefinitionCatalog.find(controllerName);
                require(definition != null && definition.isWritable(),
                        "declared production write target lacks writable canonical metadata: "
                                + controllerName);
                int index = definition.isIndexed() ? 0 : -1;
                AeApplyRestoreValidationTargets.Target target =
                        AeApplyRestoreValidationTargets.find(controllerName, index);
                require(target != null,
                        "declared Guided write target escaped the physically validated inventory: "
                                + item.getTask() + " -> " + controllerName);
            }
        }
        require(AeApplyRestoreValidationTargets.physicalTargetCount() == 816,
                "physical write authority changed while promoting production availability");
    }

    private static void recommendationMaturityStaysIndependentAndTruthful() {
        requireRecommendation(GuidedTuningRecipe.ENGAGEMENT_DETECTION,
                GuidedControllerSettingInventory.RecommendationSupport.CURRENT);
        requireRecommendation(GuidedTuningRecipe.FOUNDATION_THRESHOLD,
                GuidedControllerSettingInventory.RecommendationSupport.PARTIAL);

        requireRecommendation(GuidedTuningRecipe.TPS_AE,
                GuidedControllerSettingInventory.RecommendationSupport.PARTIAL);
        requireRecommendation(GuidedTuningRecipe.TPS_AE_COMPENSATION,
                GuidedControllerSettingInventory.RecommendationSupport.PARTIAL);
        requireRecommendation(GuidedTuningRecipe.TPS_AE_COMPLETION,
                GuidedControllerSettingInventory.RecommendationSupport.PARTIAL);

        requireRecommendation(GuidedTuningRecipe.MAP_ESTIMATE,
                GuidedControllerSettingInventory.RecommendationSupport.CURRENT);
        requireRecommendation(GuidedTuningRecipe.BLEND_DURATION,
                GuidedControllerSettingInventory.RecommendationSupport.CURRENT);

        requireRecommendation(GuidedTuningRecipe.WALL_WETTING,
                GuidedControllerSettingInventory.RecommendationSupport.PARTIAL);
        requireRecommendation(GuidedTuningRecipe.WALL_WETTING_ADVANCED,
                GuidedControllerSettingInventory.RecommendationSupport.PARTIAL);

        requireRecommendation(GuidedTuningRecipe.DECEL_DETECTION,
                GuidedControllerSettingInventory.RecommendationSupport.PARTIAL);
        requireRecommendation(GuidedTuningRecipe.DECEL_FUEL,
                GuidedControllerSettingInventory.RecommendationSupport.PLANNED);
        requireRecommendation(GuidedTuningRecipe.DECEL_MAP_PREDICT,
                GuidedControllerSettingInventory.RecommendationSupport.PLANNED);

        requireRecommendation(GuidedTuningRecipe.INSTANT_FUEL_SETUP,
                GuidedControllerSettingInventory.RecommendationSupport.PARTIAL);
        requireRecommendation(GuidedTuningRecipe.INSTANT_FUEL_EVENT_STRENGTH,
                GuidedControllerSettingInventory.RecommendationSupport.PARTIAL);
        requireRecommendation(GuidedTuningRecipe.INSTANT_FUEL_CONDITIONS,
                GuidedControllerSettingInventory.RecommendationSupport.PARTIAL);

        for (GuidedControllerSettingInventory.TaskInventory item
                : GuidedControllerSettingInventory.all()) {
            require(item.getRecommendationSupport() != null,
                    "Guided recommendation maturity missing: " + item.getTask());
            if (!item.hasWriteTargets()) {
                require(item.getRecommendationSupport()
                                == GuidedControllerSettingInventory.RecommendationSupport.NONE,
                        "no-target/diagnostic task claimed recommendation-to-write maturity: "
                                + item.getTask());
            }
        }
    }

    private static void requireRecommendation(
            GuidedTuningRecipe task,
            GuidedControllerSettingInventory.RecommendationSupport expected) {
        GuidedControllerSettingInventory.TaskInventory item =
                GuidedControllerSettingInventory.find(task);
        require(item != null, "missing Guided inventory task " + task);
        require(item.getProductionWriteSupport()
                        == GuidedControllerSettingInventory.ProductionWriteSupport.CURRENT,
                "recommendation maturity accidentally controls write availability for " + task);
        require(item.getRecommendationSupport() == expected,
                "recommendation maturity changed for " + task + ": expected "
                        + expected + " but was " + item.getRecommendationSupport());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
