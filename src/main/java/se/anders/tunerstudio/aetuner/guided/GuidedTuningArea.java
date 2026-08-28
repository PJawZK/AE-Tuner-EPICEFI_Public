package se.anders.tunerstudio.aetuner.guided;

/**
 * User-facing Guided Tuning navigation groups.
 *
 * The order communicates how to start without implying that every AE method is
 * mandatory. Foundation is upstream; after that the operator chooses only the
 * AE strategy/strategies actually used by the tune. MAP Predict has a strict
 * local task order. Other areas use local order to express dependencies inside
 * that method, not a requirement that every product area must be completed.
 * Numbering shown in task names is therefore local to the selected area and must
 * never be presented as one global 1..N checklist across all AE strategies.
 *
 * The current task map is intentionally a product/UX baseline rather than a
 * frozen controller abstraction. Individual tasks may be merged, split, renamed
 * or reordered later when their real tuning logic and vehicle evidence justify
 * a better grouping; planned scaffolds must never block that refinement.
 *
 * Area/task selection is navigation state and must take effect independently of
 * starting an evidence capture. Retained evidence may block a new capture, but
 * it must not block browsing another area/task.
 */
public enum GuidedTuningArea {
    FOUNDATION(
            "AE Foundation",
            "Start here. Establish shared throttle-opening event detection first; downstream AE strategies depend on what the detector calls an event.",
            new GuidedTuningRecipe[]{
                    GuidedTuningRecipe.ENGAGEMENT_DETECTION,
                    GuidedTuningRecipe.FOUNDATION_THRESHOLD,
                    GuidedTuningRecipe.FOUNDATION_VALIDATION
            }),
    TPS_AE(
            "TPS AE",
            "Use when cycle-based TPS acceleration fuel is part of the tune. Tune its fuel shape and corrections only after the shared detector is credible.",
            new GuidedTuningRecipe[]{
                    GuidedTuningRecipe.TPS_AE,
                    GuidedTuningRecipe.TPS_AE_COMPENSATION,
                    GuidedTuningRecipe.TPS_AE_COMPLETION,
                    GuidedTuningRecipe.TPS_AE_VALIDATION
            }),
    MAP_PREDICT(
            "MAP Predict",
            "Tune in this local order: MAP Estimate Table, Blend Duration, then Transient Validation of the combined behavior.",
            new GuidedTuningRecipe[]{
                    GuidedTuningRecipe.MAP_ESTIMATE,
                    GuidedTuningRecipe.BLEND_DURATION,
                    GuidedTuningRecipe.MAP_PREDICT
            }),
    WALL_WETTING(
            "Wall Wetting",
            "Use when wall-film compensation is part of the tune. Establish the model first, add temperature/load detail only when needed, then validate both tip-in and tip-out behavior.",
            new GuidedTuningRecipe[]{
                    GuidedTuningRecipe.WALL_WETTING,
                    GuidedTuningRecipe.WALL_WETTING_ADVANCED,
                    GuidedTuningRecipe.WALL_WETTING_VALIDATION
            }),
    DECEL_TIPOUT(
            "Decel / Tip-out",
            "Current EpicEFI has a dedicated closing-throttle transient path. Tune its own detection, optional fuel enleanment and Decel MAP Prediction without treating DFCO as an AE Tuner target.",
            new GuidedTuningRecipe[]{
                    GuidedTuningRecipe.DECEL_DETECTION,
                    GuidedTuningRecipe.DECEL_FUEL,
                    GuidedTuningRecipe.DECEL_MAP_PREDICT,
                    GuidedTuningRecipe.DECEL_VALIDATION
            }),
    OPTIONAL_RESIDUAL(
            "Optional / Residual Correction",
            "Instant Fuel belongs here only when the primary AE strategy still leaves a demonstrated early transient error. Configure the pulse, shape it, then prove it is still needed.",
            new GuidedTuningRecipe[]{
                    GuidedTuningRecipe.INSTANT_FUEL_SETUP,
                    GuidedTuningRecipe.INSTANT_FUEL_EVENT_STRENGTH,
                    GuidedTuningRecipe.INSTANT_FUEL_CONDITIONS,
                    GuidedTuningRecipe.INSTANT_FUEL
            }),
    REVIEW_SIMPLIFICATION(
            "Review / Simplification",
            "Review the completed transient stack, classify remaining errors and remove unnecessary overlap. These are product-review tasks, not additional enrichment methods.",
            new GuidedTuningRecipe[]{
                    GuidedTuningRecipe.OPTIMIZATION,
                    GuidedTuningRecipe.RESIDUAL_ERROR_REVIEW,
                    GuidedTuningRecipe.FINAL_SIMPLIFICATION
            });

    public final String displayName;
    public final String guidance;
    private final GuidedTuningRecipe[] tasks;

    GuidedTuningArea(String displayName, String guidance,
                     GuidedTuningRecipe[] tasks) {
        this.displayName = displayName;
        this.guidance = guidance;
        this.tasks = tasks.clone();
    }

    public GuidedTuningRecipe[] tasks() {
        return tasks.clone();
    }

    public boolean contains(GuidedTuningRecipe recipe) {
        if (recipe == null) return false;
        for (GuidedTuningRecipe task : tasks) {
            if (task == recipe) return true;
        }
        return false;
    }

    public static GuidedTuningArea forRecipe(GuidedTuningRecipe recipe) {
        for (GuidedTuningArea area : values()) {
            if (area.contains(recipe)) return area;
        }
        return FOUNDATION;
    }

    @Override public String toString() {
        return displayName;
    }
}
