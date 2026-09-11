package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.guided.method.GuidedAeMethodModules;
import se.anders.tunerstudio.aetuner.host.AeControllerBridge;
import se.anders.tunerstudio.aetuner.host.GuidedControllerSettingInventory;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;

import com.efiAnalytics.plugin.ecu.ControllerAccess;

import java.util.EnumMap;
import java.util.Map;

/**
 * Frozen read-only controller-surface cache for evidence-based Guided tuning.
 *
 * The cache is populated from the controller handle exposed by the normal Read
 * Working Tune bridge. Each writable TPS AE, Wall Wetting and Instant Fuel
 * subtask is captured using GuidedTaskSettingsDraft, which itself uses the
 * canonical read-only physical target reader. Recommendation engines receive
 * independent copies so Review cannot mutate the frozen baseline. This class
 * never writes the controller.
 */
public final class GuidedWorkingTuneSurfaceCache {
    private static final Map<GuidedTuningRecipe, GuidedTaskSettingsDraft> BASELINES =
            new EnumMap<GuidedTuningRecipe, GuidedTaskSettingsDraft>(GuidedTuningRecipe.class);
    private static final Map<GuidedTuningRecipe, String> ERRORS =
            new EnumMap<GuidedTuningRecipe, String>(GuidedTuningRecipe.class);
    private static String configurationName = "";

    private GuidedWorkingTuneSurfaceCache() { }

    public static synchronized void refresh(ControllerAccess access,
                                            String configuration) {
        BASELINES.clear();
        ERRORS.clear();
        configurationName = configuration == null ? "" : configuration;
        if (access == null || configurationName.length() == 0) return;

        for (GuidedTuningRecipe task : GuidedTuningRecipe.values()) {
            if (!isFullAeBaseTask(task)) continue;
            GuidedControllerSettingInventory.TaskInventory inventory =
                    GuidedControllerSettingInventory.find(task);
            if (inventory == null || !inventory.hasWriteTargets()
                    || inventory.getProductionWriteSupport()
                    != GuidedControllerSettingInventory.ProductionWriteSupport.CURRENT) {
                continue;
            }
            try {
                BASELINES.put(task,
                        GuidedTaskSettingsDraft.capture(access, configurationName, task));
            } catch (Exception ex) {
                ERRORS.put(task, ex.getMessage() == null
                        ? ex.getClass().getSimpleName() : ex.getMessage());
            }
        }
    }

    /**
     * Ensure the latest Read Working Tune has a complete method-surface cache.
     * The first TPS/Wall/Instant task selected after a working-tune read freezes
     * all related surfaces together, so later task switching does not silently
     * change the evidence baseline.
     */
    public static synchronized GuidedTaskSettingsDraft ensureBaseline(
            GuidedTuningRecipe task, AeProjectSnapshot snapshot) {
        if (snapshot == null) return null;
        String requestedConfiguration = snapshot.getConfigurationName();
        boolean sameConfiguration = requestedConfiguration != null
                && requestedConfiguration.equals(configurationName);
        if (!sameConfiguration || (BASELINES.isEmpty() && ERRORS.isEmpty())) {
            refresh(AeControllerBridge.latestControllerAccess(), requestedConfiguration);
        }
        return baselineFor(task);
    }

    public static synchronized GuidedTaskSettingsDraft baselineFor(
            GuidedTuningRecipe task) {
        GuidedTaskSettingsDraft draft = BASELINES.get(task);
        return draft == null ? null : draft.copyBaseline();
    }

    public static synchronized boolean hasBaseline(GuidedTuningRecipe task) {
        return BASELINES.containsKey(task);
    }

    public static synchronized String statusFor(GuidedTuningRecipe task) {
        if (BASELINES.containsKey(task)) {
            GuidedTaskSettingsDraft draft = BASELINES.get(task);
            return "Working Tune surface captured: " + draft.getEntries().size()
                    + " validated value(s) across "
                    + draft.controllerNames().size() + " controller parameter(s).";
        }
        String error = ERRORS.get(task);
        return error == null
                ? "No frozen Working Tune surface is cached for this task."
                : "Working Tune surface unavailable: " + error;
    }

    public static synchronized String configurationName() {
        return configurationName;
    }

    /** Final/static cache retirement boundary used by plugin close and explicit cache resets. */
    public static synchronized void clear() {
        BASELINES.clear();
        ERRORS.clear();
        configurationName = "";
        GuidedAeMethodModules.clearLifecycleCaches();
    }

    private static boolean isFullAeBaseTask(GuidedTuningRecipe task) {
        return task == GuidedTuningRecipe.TPS_AE
                || task == GuidedTuningRecipe.TPS_AE_COMPENSATION
                || task == GuidedTuningRecipe.TPS_AE_COMPLETION
                || task == GuidedTuningRecipe.WALL_WETTING
                || task == GuidedTuningRecipe.WALL_WETTING_ADVANCED
                || task == GuidedTuningRecipe.INSTANT_FUEL_SETUP
                || task == GuidedTuningRecipe.INSTANT_FUEL_EVENT_STRENGTH
                || task == GuidedTuningRecipe.INSTANT_FUEL_CONDITIONS;
    }
}
