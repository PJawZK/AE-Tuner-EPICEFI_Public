package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Prevents false cache hits and stale recommendation retention across lifecycle close. */
public final class BaselineRecommendationCacheLifecycleRegressionTest {
    private BaselineRecommendationCacheLifecycleRegressionTest() { }

    public static void main(String[] args) {
        exactEvidenceIdentityControlsReuse();
        lifecycleClearInvalidatesReuse();
        System.out.println("BaselineRecommendationCacheLifecycleRegressionTest passed");
    }

    private static void exactEvidenceIdentityControlsReuse() {
        GuidedTuningRecipe recipe = GuidedTuningRecipe.TPS_AE_COMPENSATION;
        List<LiveSample> first = Collections.emptyList();
        List<LiveSample> replacement = new ArrayList<LiveSample>();
        BaselineRecommendationCacheGuard.clear();
        long before = BaselineSurfaceRecommendation.evaluationCountForTest();

        BaselineRecommendationCacheGuard.prepare(recipe, null, first);
        BaselineSurfaceRecommendation.evaluate(recipe, null, first);
        long afterFirst = BaselineSurfaceRecommendation.evaluationCountForTest();
        require(afterFirst == before + 1,
                "first recommendation evaluation was not recorded");

        BaselineRecommendationCacheGuard.prepare(recipe, null, first);
        BaselineSurfaceRecommendation.evaluate(recipe, null, first);
        require(BaselineSurfaceRecommendation.evaluationCountForTest() == afterFirst,
                "same immutable evidence identity did not reuse cached result");

        BaselineRecommendationCacheGuard.prepare(recipe, null, replacement);
        BaselineSurfaceRecommendation.evaluate(recipe, null, replacement);
        require(BaselineSurfaceRecommendation.evaluationCountForTest() == afterFirst + 1,
                "distinct evidence object with same size was incorrectly treated as same revision");
    }

    private static void lifecycleClearInvalidatesReuse() {
        GuidedTuningRecipe recipe = GuidedTuningRecipe.TPS_AE_COMPENSATION;
        List<LiveSample> evidence = Collections.emptyList();
        BaselineRecommendationCacheGuard.prepare(recipe, null, evidence);
        BaselineSurfaceRecommendation.evaluate(recipe, null, evidence);
        long beforeClear = BaselineSurfaceRecommendation.evaluationCountForTest();

        GuidedAeMethodModules.clearLifecycleCaches();
        BaselineRecommendationCacheGuard.prepare(recipe, null, evidence);
        BaselineSurfaceRecommendation.evaluate(recipe, null, evidence);
        require(BaselineSurfaceRecommendation.evaluationCountForTest() == beforeClear + 1,
                "final lifecycle clear left a reusable baseline recommendation cached");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
