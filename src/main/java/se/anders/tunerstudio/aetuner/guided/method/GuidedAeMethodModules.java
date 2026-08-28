package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;

import java.util.EnumMap;
import java.util.Map;

/** One explicit module route per selector entry; no method-specific switch sprawl in the UI. */
public final class GuidedAeMethodModules {
    private static final Map<GuidedTuningRecipe, GuidedAeMethodModule> MODULES =
            new EnumMap<GuidedTuningRecipe, GuidedAeMethodModule>(GuidedTuningRecipe.class);

    static {
        register(new BlendDurationMethodModule());
        register(new EngagementDetectionMethodModule());
        register(new MapPredictMethodModule());
        register(new MapEstimateMethodModule());
        register(new WallWettingMethodModule());
        register(new TpsAeMethodModule());
        register(new InstantFuelMethodModule());
        register(new OptimizationMethodModule());

        for (GuidedTuningRecipe recipe : GuidedTuningRecipe.values()) {
            if (!recipe.implemented && !MODULES.containsKey(recipe)) {
                register(new PlannedGuidedTaskModule(recipe));
            }
        }

        if (MODULES.size() != GuidedTuningRecipe.values().length) {
            throw new IllegalStateException("Every Guided tuning selector entry must have exactly one isolated method module");
        }
    }

    private GuidedAeMethodModules() { }

    private static void register(GuidedAeMethodModule module) {
        GuidedAeMethodModule previous = MODULES.put(module.recipe(), module);
        if (previous != null) {
            throw new IllegalStateException("Duplicate Guided AE module for " + module.recipe());
        }
    }

    public static GuidedAeMethodModule forRecipe(GuidedTuningRecipe recipe) {
        GuidedAeMethodModule module = MODULES.get(recipe);
        if (module == null) {
            throw new IllegalArgumentException("No Guided AE method module registered for " + recipe);
        }
        return module;
    }
}
