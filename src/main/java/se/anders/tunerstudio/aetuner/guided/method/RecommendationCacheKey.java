package se.anders.tunerstudio.aetuner.guided.method;

import se.anders.tunerstudio.aetuner.model.AeProjectSnapshot;
import se.anders.tunerstudio.aetuner.model.LiveSample;
import java.lang.ref.WeakReference;
import java.util.List;

/** Exact immutable input identity without retaining a retired baseline/evidence list. */
final class RecommendationCacheKey {
    private final WeakReference<AeProjectSnapshot> snapshot;
    private final WeakReference<List<LiveSample>> evidence;
    private final int size;
    private final long lastNano;

    RecommendationCacheKey(AeProjectSnapshot snapshot, List<LiveSample> evidence) {
        this.snapshot = snapshot == null ? null : new WeakReference<AeProjectSnapshot>(snapshot);
        this.evidence = evidence == null ? null : new WeakReference<List<LiveSample>>(evidence);
        size = evidence == null ? 0 : evidence.size();
        lastNano = lastNano(evidence, size);
    }

    boolean matches(AeProjectSnapshot snapshot, List<LiveSample> evidence) {
        return same(this.snapshot, snapshot) && same(this.evidence, evidence)
                && size == (evidence == null ? 0 : evidence.size())
                && lastNano == lastNano(evidence, size);
    }

    private static <T> boolean same(WeakReference<T> reference, T value) {
        return reference == null ? value == null : value != null && reference.get() == value;
    }
    private static long lastNano(List<LiveSample> evidence, int size) {
        return size == 0 || evidence.get(size - 1) == null
                ? Long.MIN_VALUE : evidence.get(size - 1).getNanoTime();
    }
}
