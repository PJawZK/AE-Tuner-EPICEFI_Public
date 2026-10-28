package se.anders.tunerstudio.aetuner.guided.method;

/** Cooperative stop boundary; interruption never becomes partial proposal evidence. */
final class GuidedAnalysisCancellation {
    private GuidedAnalysisCancellation() { }
    static void check() {
        if (Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException("Historical analysis cancelled");
        }
    }
}
