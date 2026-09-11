package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.guided.GuidedTuningRecipe;
import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Strengthens the older baseline-result cache without adding another retention
 * root. The recommendation cache itself is cleared whenever the exact immutable
 * evidence snapshot or Working Tune object changes; repeated UI reads of the
 * same revision still reuse the expensive result.
 */
final class BaselineRecommendationCacheGuard {
    private static GuidedTuningRecipe recipe;
    private static WeakReference<AeProjectSnapshot> snapshot = weak(null);
    private static WeakReference<List<LiveSample>> evidence = weak(null);

    private BaselineRecommendationCacheGuard() { }

    static synchronized void prepare(GuidedTuningRecipe nextRecipe,
                                     AeProjectSnapshot nextSnapshot,
                                     List<LiveSample> nextEvidence) {
        if (recipe == nextRecipe
                && snapshot.get() == nextSnapshot
                && evidence.get() == nextEvidence) return;
        BaselineSurfaceRecommendation.clearCache();
        recipe = nextRecipe;
        snapshot = weak(nextSnapshot);
        evidence = weak(nextEvidence);
    }

    static synchronized void clear() {
        recipe = null;
        snapshot.clear();
        evidence.clear();
        snapshot = weak(null);
        evidence = weak(null);
        BaselineSurfaceRecommendation.clearCache();
    }

    private static <T> WeakReference<T> weak(T value) {
        return new WeakReference<T>(value);
    }
}
