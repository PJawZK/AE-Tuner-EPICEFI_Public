package se.anders.tunerstudio.aetuner.guided;

/** Regression contract for Foundation-first area/task navigation and immediate Guided Focus routing. */
public final class GuidedTuningNavigationRegressionTest {
    private GuidedTuningNavigationRegressionTest() { }

    public static void main(String[] args) {
        foundationIsTheStartingAreaWithoutImplyingMandatoryStrategies();
        allKnownCurrentAeFamiliesHaveTaskScaffolding();
        mapPredictHasTheCorrectLocalDependencyOrder();
        decelIsAFirstClassTransientArea();
        changingTaskImmediatelyChangesGuidedFocusWithoutCapture();
        plannedTasksAreExplicitlyNonFunctionalScaffolds();
        System.out.println("GuidedTuningNavigationRegressionTest passed");
    }

    private static void foundationIsTheStartingAreaWithoutImplyingMandatoryStrategies() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            require(panel.tuningAreaCountForTest() == 7,
                    "Guided navigation must expose seven product-level tuning areas including Decel / Tip-out");
            require(panel.selectedTuningAreaForTest().equals("AE Foundation"),
                    "AE Foundation must be the initial Guided tuning area");
            require(panel.tuningTaskCountForTest() == 3,
                    "Foundation must expose TPS movement/timing, threshold/sensitivity and validation tasks");
            require(panel.selectedTuningTaskForTest().contains("1. TPS Movement / Timing"),
                    "TPS Movement / Timing must be the first AE tuning task");
        } finally {
            panel.disposePanel();
        }
    }

    private static void allKnownCurrentAeFamiliesHaveTaskScaffolding() {
        require(GuidedTuningArea.FOUNDATION.tasks().length == 3,
                "Foundation task map is incomplete");
        require(GuidedTuningArea.TPS_AE.tasks().length == 4,
                "TPS AE task map is incomplete");
        require(GuidedTuningArea.MAP_PREDICT.tasks().length == 3,
                "MAP Predict task map is incomplete");
        require(GuidedTuningArea.WALL_WETTING.tasks().length == 3,
                "Wall Wetting task map is incomplete");
        require(GuidedTuningArea.DECEL_TIPOUT.tasks().length == 4,
                "Decel / Tip-out task map is incomplete");
        require(GuidedTuningArea.OPTIONAL_RESIDUAL.tasks().length == 4,
                "Instant Fuel task map is incomplete");
        require(GuidedTuningArea.REVIEW_SIMPLIFICATION.tasks().length == 3,
                "Review / Simplification task map is incomplete");

        GuidedTuningRecipe[] tps = GuidedTuningArea.TPS_AE.tasks();
        require(tps[0] == GuidedTuningRecipe.TPS_AE
                        && tps[1] == GuidedTuningRecipe.TPS_AE_COMPENSATION
                        && tps[2] == GuidedTuningRecipe.TPS_AE_COMPLETION
                        && tps[3] == GuidedTuningRecipe.TPS_AE_VALIDATION,
                "TPS AE local order is incorrect");

        GuidedTuningRecipe[] wall = GuidedTuningArea.WALL_WETTING.tasks();
        require(wall[0] == GuidedTuningRecipe.WALL_WETTING
                        && wall[1] == GuidedTuningRecipe.WALL_WETTING_ADVANCED
                        && wall[2] == GuidedTuningRecipe.WALL_WETTING_VALIDATION,
                "Wall Wetting local order is incorrect");

        GuidedTuningRecipe[] instant = GuidedTuningArea.OPTIONAL_RESIDUAL.tasks();
        require(instant[0] == GuidedTuningRecipe.INSTANT_FUEL_SETUP
                        && instant[1] == GuidedTuningRecipe.INSTANT_FUEL_EVENT_STRENGTH
                        && instant[2] == GuidedTuningRecipe.INSTANT_FUEL_CONDITIONS
                        && instant[3] == GuidedTuningRecipe.INSTANT_FUEL,
                "Instant Fuel local order is incorrect");
    }

    private static void mapPredictHasTheCorrectLocalDependencyOrder() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            GuidedTuningRecipe[] tasks = GuidedTuningArea.MAP_PREDICT.tasks();
            require(tasks.length == 3,
                    "MAP Predict must expose exactly three current Guided tasks");
            require(tasks[0] == GuidedTuningRecipe.MAP_ESTIMATE,
                    "MAP Estimate Table must be first inside MAP Predict");
            require(tasks[1] == GuidedTuningRecipe.BLEND_DURATION,
                    "Blend Duration must follow MAP Estimate Table");
            require(tasks[2] == GuidedTuningRecipe.MAP_PREDICT,
                    "Transient Validation must follow table and duration tuning");

            panel.selectTuningAreaForTest(GuidedTuningArea.MAP_PREDICT);
            require(panel.selectedTuningAreaForTest().equals("MAP Predict"),
                    "MAP Predict area was not selected");
            require(panel.tuningTaskCountForTest() == 3,
                    "MAP Predict area did not expose its three local tasks");
            require(panel.selectedTuningTaskForTest().contains("1. MAP Estimate Table"),
                    "MAP Predict area did not default to its first dependency");
        } finally {
            panel.disposePanel();
        }
    }

    private static void decelIsAFirstClassTransientArea() {
        GuidedTuningRecipe[] tasks = GuidedTuningArea.DECEL_TIPOUT.tasks();
        require(tasks[0] == GuidedTuningRecipe.DECEL_DETECTION
                        && tasks[1] == GuidedTuningRecipe.DECEL_FUEL
                        && tasks[2] == GuidedTuningRecipe.DECEL_MAP_PREDICT
                        && tasks[3] == GuidedTuningRecipe.DECEL_VALIDATION,
                "Decel / Tip-out must expose detection, fuel shape, MAP prediction and validation in that local order");
        require(GuidedTuningArea.DECEL_TIPOUT.guidance.contains("DFCO")
              && GuidedTuningArea.DECEL_TIPOUT.guidance.contains("without treating DFCO as an AE Tuner target"),
      "Decel area must preserve the boundary that DFCO is context rather than an AE Tuner target");
    }

    private static void changingTaskImmediatelyChangesGuidedFocusWithoutCapture() {
        GuidedCapturePanel panel = new GuidedCapturePanel();
        try {
            require(GuidedFocusHub.snapshot().recipe == GuidedTuningRecipe.ENGAGEMENT_DETECTION,
                    "initial Guided Focus did not follow Foundation selection");
            require(GuidedFocusHub.snapshot().captureState == GuidedCaptureState.IDLE,
                    "initial focus unexpectedly required/started capture");

            panel.selectTuningTaskForTest(GuidedTuningRecipe.BLEND_DURATION);
            require(GuidedFocusHub.snapshot().recipe == GuidedTuningRecipe.BLEND_DURATION,
                    "Guided Focus did not change immediately when Blend Duration was selected");
            require(GuidedFocusHub.snapshot().captureState == GuidedCaptureState.IDLE,
                    "changing task incorrectly started a capture");

            panel.selectTuningTaskForTest(GuidedTuningRecipe.DECEL_MAP_PREDICT);
            require(GuidedFocusHub.snapshot().recipe == GuidedTuningRecipe.DECEL_MAP_PREDICT,
                    "Guided Focus did not immediately switch to a planned Decel task");
            require(panel.selectedTuningAreaForTest().equals("Decel / Tip-out")
                            && panel.selectedTuningTaskForTest().contains("3. Decel MAP Prediction"),
                    "planned area/task navigation did not update immediately");
        } finally {
            panel.disposePanel();
        }
    }

    private static void plannedTasksAreExplicitlyNonFunctionalScaffolds() {
        GuidedTuningRecipe[] planned = new GuidedTuningRecipe[]{
                GuidedTuningRecipe.FOUNDATION_THRESHOLD,
                GuidedTuningRecipe.FOUNDATION_VALIDATION,
                GuidedTuningRecipe.TPS_AE_COMPENSATION,
                GuidedTuningRecipe.TPS_AE_COMPLETION,
                GuidedTuningRecipe.TPS_AE_VALIDATION,
                GuidedTuningRecipe.WALL_WETTING_ADVANCED,
                GuidedTuningRecipe.WALL_WETTING_VALIDATION,
                GuidedTuningRecipe.DECEL_DETECTION,
                GuidedTuningRecipe.DECEL_FUEL,
                GuidedTuningRecipe.DECEL_MAP_PREDICT,
                GuidedTuningRecipe.DECEL_VALIDATION,
                GuidedTuningRecipe.INSTANT_FUEL_SETUP,
                GuidedTuningRecipe.INSTANT_FUEL_EVENT_STRENGTH,
                GuidedTuningRecipe.INSTANT_FUEL_CONDITIONS,
                GuidedTuningRecipe.RESIDUAL_ERROR_REVIEW,
                GuidedTuningRecipe.FINAL_SIMPLIFICATION
        };
        for (GuidedTuningRecipe recipe : planned) {
            require(!recipe.implemented && recipe.status.toLowerCase().contains("scaffold")
                            || (!recipe.implemented && recipe.status.toLowerCase().contains("coach")),
                    "planned task is not visibly classified as scaffold/coach: " + recipe);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
