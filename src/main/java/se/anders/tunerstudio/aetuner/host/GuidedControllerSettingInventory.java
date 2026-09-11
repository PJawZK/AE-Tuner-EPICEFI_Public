package se.anders.tunerstudio.aetuner.host;

import se.anders.tunerstudio.aetuner.guided.GuidedTuningArea;
import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Permanent task-to-controller inventory for Guided Tuning.
 *
 * This is the safety contract between product navigation and the canonical AE
 * controller catalog. Every Guided task must appear exactly once, including
 * diagnostic/review tasks with no write target.
 *
 * Production write support and recommendation maturity are deliberately
 * independent. Every declared write target is available only through the
 * common guarded production Apply/Restore path. PARTIAL recommendation support
 * means a usable baseline evidence algorithm exists and is intentionally
 * expected to be refined by subsequent vehicle confirmation; it never limits
 * access to the already physically validated controller surface.
 */
public final class GuidedControllerSettingInventory {
    public enum Classification {
        WRITABLE_VALIDATION_REQUIRED,
        PLANNED_WRITABLE_MAPPING,
        READ_ONLY_EVIDENCE_DIAGNOSTIC,
        NO_DIRECT_CONTROLLER_WRITE_TARGETS
    }

    public enum ProductionWriteSupport {
        CURRENT,
        PARTIAL,
        PLANNED,
        NONE
    }

    public enum RecommendationSupport {
        CURRENT,
        PARTIAL,
        PLANNED,
        NONE
    }

    public static final class TaskInventory {
        private final GuidedTuningArea area;
        private final GuidedTuningRecipe task;
        private final Classification classification;
        private final ProductionWriteSupport productionWriteSupport;
        private final RecommendationSupport recommendationSupport;
        private final List<String> controllerTargets;
        private final String note;

        private TaskInventory(GuidedTuningArea area, GuidedTuningRecipe task,
                              Classification classification,
                              ProductionWriteSupport productionWriteSupport,
                              RecommendationSupport recommendationSupport,
                              String note, String... controllerTargets) {
            this.area = area;
            this.task = task;
            this.classification = classification;
            this.productionWriteSupport = productionWriteSupport;
            this.recommendationSupport = recommendationSupport;
            this.note = note == null ? "" : note;
            this.controllerTargets = Collections.unmodifiableList(
                    Arrays.asList(controllerTargets == null
                            ? new String[0] : controllerTargets.clone()));
        }

        public GuidedTuningArea getArea() { return area; }
        public GuidedTuningRecipe getTask() { return task; }
        public Classification getClassification() { return classification; }
        public ProductionWriteSupport getProductionWriteSupport() { return productionWriteSupport; }
        public RecommendationSupport getRecommendationSupport() { return recommendationSupport; }
        public List<String> getControllerTargets() { return controllerTargets; }
        public String getNote() { return note; }
        public boolean hasWriteTargets() { return !controllerTargets.isEmpty(); }
    }

    private static final List<TaskInventory> ALL;
    private static final Map<GuidedTuningRecipe, TaskInventory> BY_TASK;

    static {
        List<TaskInventory> all = new ArrayList<TaskInventory>();

        // AE Foundation.
        add(all, GuidedTuningArea.FOUNDATION, GuidedTuningRecipe.ENGAGEMENT_DETECTION,
                Classification.WRITABLE_VALIDATION_REQUIRED,
                RecommendationSupport.CURRENT,
                "Delta Window has current recommendation/apply logic. Engagement model, sample length and callback settings remain read-only context for this Guided task.",
                AeParameterNames.TPS_AE_DELTA_WINDOW_MS);
        add(all, GuidedTuningArea.FOUNDATION, GuidedTuningRecipe.FOUNDATION_THRESHOLD,
                Classification.PLANNED_WRITABLE_MAPPING,
                RecommendationSupport.PARTIAL,
                "The complete threshold/smoothing controller surface is physically validated. Current evidence logic may emit a conservative multi-bin tpsAeThresholdValue proposal when the static RPM threshold curve is directly authoritative and each changed bin has sufficient two-sided noise-versus-intent evidence. Dynamic-threshold and smoothing recommendations remain incomplete; all validated settings remain operator-reviewable.",
                AeParameterNames.DELTA_TPS_AVERAGE_ALPHA,
                AeParameterNames.DELTA_TPS_AVERAGE_CURVE_RPM_BINS,
                AeParameterNames.DELTA_TPS_AVERAGE_CURVE_MULTIPLIER,
                AeParameterNames.TPS_AE_USE_DYNAMIC_THRESHOLD,
                AeParameterNames.TPS_AE_DYNAMIC_THRESHOLD_AVERAGE_STATIC_CURVE,
                AeParameterNames.TPS_AE_THRESHOLD_RPM_BINS,
                AeParameterNames.TPS_AE_THRESHOLD_VALUES);
        addNoTargets(all, GuidedTuningArea.FOUNDATION, GuidedTuningRecipe.FOUNDATION_VALIDATION,
                Classification.NO_DIRECT_CONTROLLER_WRITE_TARGETS,
                "Validation observes the completed detector configuration and does not introduce another controller setting.");

        // TPS AE.
        add(all, GuidedTuningArea.TPS_AE, GuidedTuningRecipe.TPS_AE,
                Classification.WRITABLE_VALIDATION_REQUIRED,
                RecommendationSupport.PARTIAL,
                "The full declared TPS AE cycle surface is physically validated for guarded Apply/Restore. Current recommendation logic populates the evidence-derived cycle fuel table as one coherent multi-cell plan; activation and axes remain operator-reviewable settings.",
                AeParameterNames.TPS_ACCEL_AE_ENABLED,
                AeParameterNames.TPS_AE_CYCLE_CYCLE_BINS,
                AeParameterNames.TPS_AE_CYCLE_TPS_TO_BINS,
                AeParameterNames.TPS_AE_CYCLE_VALUES);
        add(all, GuidedTuningArea.TPS_AE, GuidedTuningRecipe.TPS_AE_COMPENSATION,
                Classification.WRITABLE_VALIDATION_REQUIRED,
                RecommendationSupport.PARTIAL,
                "RPM correction, TPS-vs-CLT scale table and AE-vs-CLT correction are complete physically validated write surfaces. Baseline recommendation logic uses condition-binned residual lambda evidence and is expected to be refined by new vehicle confirmation.",
                AeParameterNames.TPS_AE_RPM_CORRECTION_BINS,
                AeParameterNames.TPS_AE_RPM_CORRECTION_VALUES,
                AeParameterNames.TPS_AE_SCALE_TPS_BINS,
                AeParameterNames.TPS_AE_SCALE_CLT_BINS,
                AeParameterNames.TPS_AE_SCALE_TABLE,
                AeParameterNames.AE_CLT_CORR_BINS,
                AeParameterNames.AE_CLT_CORR_VALUES);
        add(all, GuidedTuningArea.TPS_AE, GuidedTuningRecipe.TPS_AE_COMPLETION,
                Classification.WRITABLE_VALIDATION_REQUIRED,
                RecommendationSupport.PARTIAL,
                "Cycle-tail length plus burn-skip, EGO-reset and post-accel closed-loop inhibit settings are complete physically validated write surfaces. Baseline review uses the measured AE tail and closed-loop handoff timeline; vehicle confirmation will refine its thresholds.",
                AeParameterNames.TPS_AE_CYCLE_CYCLE_BINS,
                AeParameterNames.TPS_AE_BURN_SKIP_INITIAL,
                AeParameterNames.TPS_AE_RESETS_EGO,
                AeParameterNames.NO_FUEL_TRIM_AFTER_ACCEL_TIME);
        addNoTargets(all, GuidedTuningArea.TPS_AE, GuidedTuningRecipe.TPS_AE_VALIDATION,
                Classification.READ_ONLY_EVIDENCE_DIAGNOSTIC,
                "Outcome validation captures the completed TPS AE stack and routes residual early/mid/tail/reapply errors back to the owning subtask without adding another controller target.");

        // MAP Predict.
        add(all, GuidedTuningArea.MAP_PREDICT, GuidedTuningRecipe.MAP_ESTIMATE,
                Classification.WRITABLE_VALIDATION_REQUIRED,
                RecommendationSupport.CURRENT,
                "MAP Estimate axes and all table cells are physically validated; current evidence logic may emit coherent multi-cell table proposals.",
                AeParameterNames.MAP_ESTIMATE_RPM_BINS,
                AeParameterNames.MAP_ESTIMATE_TPS_BINS,
                AeParameterNames.MAP_ESTIMATE_TABLE);
        add(all, GuidedTuningArea.MAP_PREDICT, GuidedTuningRecipe.BLEND_DURATION,
                Classification.WRITABLE_VALIDATION_REQUIRED,
                RecommendationSupport.CURRENT,
                "Predictive enable, RPM axis and duration values are physically validated. Current Guided recommendation logic populates the justified duration point(s); the write layer is not limited to one value.",
                AeParameterNames.USE_MAP_ESTIMATE_DURING_TRANSIENT,
                AeParameterNames.PREDICTIVE_MAP_BLEND_DURATION_BINS,
                AeParameterNames.PREDICTIVE_MAP_BLEND_DURATION_VALUES);
        addNoTargets(all, GuidedTuningArea.MAP_PREDICT, GuidedTuningRecipe.MAP_PREDICT,
                Classification.NO_DIRECT_CONTROLLER_WRITE_TARGETS,
                "Transient Validation evaluates the combined MAP Predict behavior after the writable tasks are complete.");

        // Wall Wetting.
        add(all, GuidedTuningArea.WALL_WETTING, GuidedTuningRecipe.WALL_WETTING,
                Classification.WRITABLE_VALIDATION_REQUIRED,
                RecommendationSupport.PARTIAL,
                "Base model activation/type and fixed tau/beta are complete physically validated write surfaces. Baseline evidence logic tunes beta from immediate film-amplitude error and tau from paired tip-in/tip-out persistence/decay; both require subsequent vehicle confirmation rather than artificial exclusion.",
                AeParameterNames.WALL_WETTING_AE_ENABLED,
                AeParameterNames.WALL_MODEL_TYPE,
                AeParameterNames.WALL_TAU,
                AeParameterNames.WALL_BETA);
        add(all, GuidedTuningArea.WALL_WETTING, GuidedTuningRecipe.WALL_WETTING_ADVANCED,
                Classification.WRITABLE_VALIDATION_REQUIRED,
                RecommendationSupport.PARTIAL,
                "Shared CLT/RPM/MAP axes, tau/beta CLT curves and RPM-vs-MAP tables are complete physically validated write surfaces. Baseline mapping uses residual wall-film behavior versus condition after the base model is established; new captures refine cell authority.",
                AeParameterNames.WALL_CLT_BINS,
                AeParameterNames.WALL_TAU_CLT_VALUES,
                AeParameterNames.WALL_BETA_CLT_VALUES,
                AeParameterNames.WALL_RPM_BINS,
                AeParameterNames.WALL_MAP_BINS,
                AeParameterNames.WALL_TAU_TABLE,
                AeParameterNames.WALL_BETA_TABLE);
        addNoTargets(all, GuidedTuningArea.WALL_WETTING, GuidedTuningRecipe.WALL_WETTING_VALIDATION,
                Classification.READ_ONLY_EVIDENCE_DIAGNOSTIC,
                "Film Validation captures paired bidirectional response after Base and Advanced tuning and attributes residual errors without adding another controller setting.");

        // Decel / Tip-out.
        add(all, GuidedTuningArea.DECEL_TIPOUT, GuidedTuningRecipe.DECEL_DETECTION,
                Classification.WRITABLE_VALIDATION_REQUIRED,
                RecommendationSupport.PARTIAL,
                "The falling-TPS threshold axis/value curve and hold-cycle setting are physically validated write surfaces. Current evidence logic may emit conservative multi-bin tpsDecelThresholdValue proposals from locked Normal Correction versus Decel Release separation. A threshold of 0 is a firmware disable command and is never auto-enabled. tpsDecelHoldCycles is captured and remains operator-reviewable, but automatic hold-cycle recommendation is intentionally withheld until threshold behavior is validated independently.",
                AeParameterNames.TPS_DECEL_THRESHOLD_RPM_BINS,
                AeParameterNames.TPS_DECEL_THRESHOLD_VALUES,
                AeParameterNames.TPS_DECEL_HOLD_CYCLES);
        add(all, GuidedTuningArea.DECEL_TIPOUT, GuidedTuningRecipe.DECEL_FUEL,
                Classification.PLANNED_WRITABLE_MAPPING,
                RecommendationSupport.PLANNED,
                "Enleanment enable, cycle/ending-TPS table and CLT authority curve are physically validated write surfaces; recommendation logic remains planned.",
                AeParameterNames.TPS_DECEL_ENLEANMENT_ENABLED,
                AeParameterNames.TPS_DECEL_CYCLE_CYCLE_BINS,
                AeParameterNames.TPS_DECEL_CYCLE_TPS_TO_BINS,
                AeParameterNames.TPS_DECEL_CYCLE_VALUES,
                AeParameterNames.TPS_DECEL_CLT_BINS,
                AeParameterNames.TPS_DECEL_CLT_MULT);
        add(all, GuidedTuningArea.DECEL_TIPOUT, GuidedTuningRecipe.DECEL_MAP_PREDICT,
                Classification.PLANNED_WRITABLE_MAPPING,
                RecommendationSupport.PLANNED,
                "Closing-throttle MAP prediction enable and blend-duration curve are physically validated write surfaces; recommendation logic remains planned.",
                AeParameterNames.USE_MAP_ESTIMATE_DURING_DECEL,
                AeParameterNames.DECEL_MAP_BLEND_DURATION_BINS,
                AeParameterNames.DECEL_MAP_BLEND_DURATION_VALUES);
        addNoTargets(all, GuidedTuningArea.DECEL_TIPOUT, GuidedTuningRecipe.DECEL_VALIDATION,
                Classification.NO_DIRECT_CONTROLLER_WRITE_TARGETS,
                "Decel validation observes the completed closing-throttle stack.");

        // Optional / Residual Correction.
        add(all, GuidedTuningArea.OPTIONAL_RESIDUAL, GuidedTuningRecipe.INSTANT_FUEL_SETUP,
                Classification.WRITABLE_VALIDATION_REQUIRED,
                RecommendationSupport.PARTIAL,
                "Instant-pulse enable, global multiplier and inhibit cycles form one complete physically validated setup surface. Baseline recommendation logic uses repeatable residual first-moment error and repeated-stab spacing; new vehicle evidence confirms/refines the result.",
                AeParameterNames.TPS_ACCEL_EXTRA_SHOT,
                AeParameterNames.TPS_EXTRA_SHOT_MULT,
                AeParameterNames.TPS_EXTRA_SHOT_TIMER);
        add(all, GuidedTuningArea.OPTIONAL_RESIDUAL, GuidedTuningRecipe.INSTANT_FUEL_EVENT_STRENGTH,
                Classification.WRITABLE_VALIDATION_REQUIRED,
                RecommendationSupport.PARTIAL,
                "Delta-TPS axis and multiplier shape are a complete physically validated surface. Baseline logic bins clean first-moment residual error by latched TPS change while preserving the live axis.",
                AeParameterNames.TPS_AE_INSTANT_DELTA_TPS_BINS,
                AeParameterNames.TPS_AE_INSTANT_DELTA_TPS_MULTIPLIER);
        add(all, GuidedTuningArea.OPTIONAL_RESIDUAL, GuidedTuningRecipe.INSTANT_FUEL_CONDITIONS,
                Classification.WRITABLE_VALIDATION_REQUIRED,
                RecommendationSupport.PARTIAL,
                "RPM, ending-TPS, MAP and CLT condition curves are complete physically validated surfaces. Baseline logic evaluates one condition axis at a time against repeatable clean pulse events; new captures refine the binning and interaction rules.",
                AeParameterNames.TPS_AE_INSTANT_RPM_BINS,
                AeParameterNames.TPS_AE_INSTANT_RPM_MULTIPLIER,
                AeParameterNames.TPS_AE_INSTANT_TPS_BINS,
                AeParameterNames.TPS_AE_INSTANT_TPS_MULTIPLIER,
                AeParameterNames.TPS_AE_INSTANT_MAP_BINS,
                AeParameterNames.TPS_AE_INSTANT_MAP_MULTIPLIER,
                AeParameterNames.TPS_AE_INSTANT_CLT_BINS,
                AeParameterNames.TPS_AE_INSTANT_CLT_MULTIPLIER);
        addNoTargets(all, GuidedTuningArea.OPTIONAL_RESIDUAL, GuidedTuningRecipe.INSTANT_FUEL,
                Classification.READ_ONLY_EVIDENCE_DIAGNOSTIC,
                "Residual Lean-Hole Validation is the final evidence-only confirmation after Setup, Event Strength and Operating Conditions. It does not own a duplicate multiplier write target.");

        // Review / Simplification.
        addNoTargets(all, GuidedTuningArea.REVIEW_SIMPLIFICATION, GuidedTuningRecipe.OPTIMIZATION,
                Classification.NO_DIRECT_CONTROLLER_WRITE_TARGETS,
                "Optimization reviews overlap and evidence; it does not invent a new controller setting.");
        addNoTargets(all, GuidedTuningArea.REVIEW_SIMPLIFICATION, GuidedTuningRecipe.RESIDUAL_ERROR_REVIEW,
                Classification.READ_ONLY_EVIDENCE_DIAGNOSTIC,
                "Residual error review is diagnostic evidence only.");
        addNoTargets(all, GuidedTuningArea.REVIEW_SIMPLIFICATION, GuidedTuningRecipe.FINAL_SIMPLIFICATION,
                Classification.NO_DIRECT_CONTROLLER_WRITE_TARGETS,
                "Final simplification is a product review decision over already-mapped settings.");

        EnumMap<GuidedTuningRecipe, TaskInventory> byTask =
                new EnumMap<GuidedTuningRecipe, TaskInventory>(GuidedTuningRecipe.class);
        for (TaskInventory item : all) {
            TaskInventory previous = byTask.put(item.task, item);
            if (previous != null) {
                throw new IllegalStateException("Duplicate Guided controller inventory for " + item.task);
            }
        }
        ALL = Collections.unmodifiableList(all);
        BY_TASK = Collections.unmodifiableMap(byTask);
    }

    private GuidedControllerSettingInventory() { }

    private static void add(List<TaskInventory> all, GuidedTuningArea area,
                            GuidedTuningRecipe task, Classification classification,
                            RecommendationSupport recommendationSupport,
                            String note, String... controllerTargets) {
        all.add(new TaskInventory(area, task, classification,
                ProductionWriteSupport.CURRENT, recommendationSupport, note,
                controllerTargets));
    }

    private static void addNoTargets(List<TaskInventory> all, GuidedTuningArea area,
                                     GuidedTuningRecipe task,
                                     Classification classification, String note) {
        all.add(new TaskInventory(area, task, classification,
                ProductionWriteSupport.NONE, RecommendationSupport.NONE, note));
    }

    public static List<TaskInventory> all() { return ALL; }

    public static TaskInventory find(GuidedTuningRecipe task) {
        return task == null ? null : BY_TASK.get(task);
    }
}
