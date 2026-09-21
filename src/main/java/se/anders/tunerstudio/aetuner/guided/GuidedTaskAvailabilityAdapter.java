package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;

/**
 * Converts the real Working Tune snapshot plus production binding maturity into
 * sidebar state. Unsupported/pending tasks stay visible and fail closed.
 *
 * Method enable state is authoritative for the whole method group: when the
 * method is OFF, none of its tasks may be opened for setup, capture, review, or
 * validation. When the method is ON, each implemented/bound task is available
 * according to its normal production maturity.
 *
 * A live Guided capture owns navigation until it is explicitly finished. This
 * includes Blend Duration's settling/ready/outcome/re-arm states, not only the
 * literal CAPTURING/PAUSED states. It prevents the v0.19 selector from visually
 * switching to another task while the hidden production session still owns the
 * capture lifecycle.
 */
public final class GuidedTaskAvailabilityAdapter {
    private GuidedTaskAvailabilityAdapter() { }

    public static GuidedTaskAvailability evaluate(GuidedProductionTask task,
                                                   AeProjectSnapshot snapshot) {
        if (task == null) return unavailable("UNAVAILABLE", "No task selected.");

        GuidedCaptureState liveState = GuidedFocusHub.activeCaptureState();
        GuidedTuningRecipe liveRecipe = GuidedFocusHub.activeCaptureRecipe();
        if (liveState.isCaptureInProgress()
                && task.productionRecipe != liveRecipe) {
            return unavailable("CAPTURE ACTIVE",
                    (liveRecipe == null ? "Another Guided task" : liveRecipe.displayName)
                            + " is currently collecting Guided evidence. Finish that capture before switching tasks so one session cannot be hidden underneath another task.");
        }

        if (snapshot == null) {
            return unavailable("READ TUNE",
                    "Read Working Tune before this task can be opened from the production sidebar.");
        }

        Boolean groupEnabled = groupEnabled(task.group, snapshot);
        if (Boolean.FALSE.equals(groupEnabled)) {
            return unavailable("OFF IN TUNE",
                    task.group.displayName + " is OFF in the current working tune. All tasks in this method group are unavailable and collect no Guided evidence until the method is enabled and Working Tune is read again.");
        }
        if (groupEnabled == null) {
            return unavailable("STATE UNRESOLVED",
                    "The current production snapshot does not yet expose an authoritative enable state for "
                            + task.group.displayName + ". The task is visible but cannot be opened from this port.");
        }

        if (task == GuidedProductionTask.DECEL_DETECTION
                && !snapshot.hasDecelDetectionSettings()) {
            return unavailable("SETTING UNAVAILABLE",
                    "The Working Tune did not expose the complete tpsDecelThresholdRpmBins / tpsDecelThresholdValue / tpsDecelHoldCycles baseline. Decel Detection fails closed rather than inventing a zero threshold or hold value.");
        }

        if (!task.firstSliceBound) {
            if (task.productionRecipe == null) {
                return unavailable("BINDING PENDING",
                        "No existing production Guided recipe currently owns this presentation task. No tuning authority is invented by the UI port.");
            }
            if (!task.productionRecipe.implemented) {
                return unavailable("PLANNED",
                        "The firmware/controller surface is catalogued, but the existing production Guided recipe is still planned. The UI port does not create tuning logic.");
            }
            return unavailable("PORT PENDING",
                    "The existing production engine is retained, but its v0.19 presentation binding is deferred until the preceding production presentation slice is parity-proven.");
        }

        return new GuidedTaskAvailability(true, "AVAILABLE",
                "Bound to the existing production " + task.productionRecipe.displayName
                        + " evidence/session path. Capture remains read-only; Apply/Restore remains unchanged.");
    }

    public static String groupState(GuidedProductionTask.Group group,
                                    AeProjectSnapshot snapshot) {
        if (snapshot == null) return "READ TUNE";
        Boolean enabled = groupEnabled(group, snapshot);
        if (enabled == null) return "UNRESOLVED";
        if (group == GuidedProductionTask.Group.AE_FOUNDATION) return "SHARED";
        return enabled.booleanValue() ? "ON" : "OFF";
    }

    private static Boolean groupEnabled(GuidedProductionTask.Group group,
                                        AeProjectSnapshot snapshot) {
        switch (group) {
            case AE_FOUNDATION:
                return Boolean.TRUE;
            case TPS_AE:
                return Boolean.valueOf(snapshot.isTpsAeEnabled());
            case MAP_PREDICT:
                return Boolean.valueOf(snapshot.isMapEstimateEnabled());
            case WALL_WETTING:
                return Boolean.valueOf(snapshot.isWallWettingEnabled());
            case INSTANT_FUEL:
                return Boolean.valueOf(snapshot.isExtraShotEnabled());
            case OPTIONAL_TRANSIENT:
            default:
                return null;
        }
    }

    private static GuidedTaskAvailability unavailable(String status, String reason) {
        return new GuidedTaskAvailability(false, status, reason);
    }
}
